package io.github.vecluu.jdoc.cli;

import io.github.vecluu.jdoc.app.Application;
import io.github.vecluu.jdoc.app.CommandApplication;
import io.github.vecluu.jdoc.app.IndexingModuleContext;
import io.github.vecluu.jdoc.app.LookupServicesFactory;
import io.github.vecluu.jdoc.bytecode.ClassFileReader;
import io.github.vecluu.jdoc.bytecode.ZipBinaryJarScanner;
import io.github.vecluu.jdoc.config.CachePaths;
import io.github.vecluu.jdoc.config.JdkInfo;
import io.github.vecluu.jdoc.config.JdkInfoReader;
import io.github.vecluu.jdoc.config.JdocEnvironment;
import io.github.vecluu.jdoc.decompile.DecompiledSourceCache;
import io.github.vecluu.jdoc.decompile.Decompiler;
import io.github.vecluu.jdoc.decompile.VineflowerDecompiler;
import io.github.vecluu.jdoc.index.FileIndexLock;
import io.github.vecluu.jdoc.index.IndexDatabase;
import io.github.vecluu.jdoc.index.JdbcArtifactRepository;
import io.github.vecluu.jdoc.index.JdbcDocRepository;
import io.github.vecluu.jdoc.index.JdbcFailedArtifactRepository;
import io.github.vecluu.jdoc.index.JdbcMemberRepository;
import io.github.vecluu.jdoc.index.JdbcSupertypeRepository;
import io.github.vecluu.jdoc.index.JdbcTypeRepository;
import io.github.vecluu.jdoc.indexer.ArtifactIndexer;
import io.github.vecluu.jdoc.indexer.ClasspathIndexer;
import io.github.vecluu.jdoc.indexer.DecompiledDirectoryEvictor;
import io.github.vecluu.jdoc.indexer.DefaultJdkIndexer;
import io.github.vecluu.jdoc.indexer.JarArtifactIndexer;
import io.github.vecluu.jdoc.indexer.JdkIndexer;
import io.github.vecluu.jdoc.indexer.JdkPackageFilter;
import io.github.vecluu.jdoc.indexer.NioFileStatter;
import io.github.vecluu.jdoc.indexer.SiblingDocJarLocator;
import io.github.vecluu.jdoc.indexer.SqliteIndexStore;
import io.github.vecluu.jdoc.indexer.ZipArchiveOpener;
import io.github.vecluu.jdoc.javadoc.JavadocPageParser;
import io.github.vecluu.jdoc.lookup.LookupServices;
import io.github.vecluu.jdoc.lookup.ZipJarEntryReader;
import io.github.vecluu.jdoc.model.TypeName;
import io.github.vecluu.jdoc.module.MavenClasspathResolver;
import io.github.vecluu.jdoc.source.JavaSourceParser;
import java.nio.file.Path;
import java.time.Clock;
import java.util.function.Supplier;

/**
 * The composition root: the only place constructing the concrete components, from a {@link JdocEnvironment}.
 *
 * <p>Startup cost is kept off the hot path: constructing the graph is cheap (no I/O besides reading the
 * JDK's {@code release} file; {@code index.db} is opened on a background thread while the command resolves the
 * module's classpath). Lookups read rendered docs from the index, so the
 * indexing components (ASM scanner, javadoc-page parser on jsoup, javac parsers) are created only when an artifact or
 * the JDK actually has to be (re-)indexed; javac is otherwise only touched by {@code jdoc source}, and Vineflower
 * only when a type is actually decompiled. The returned {@link Application} closes the database.
 */
public final class CompositionRoot {

    private CompositionRoot() {
    }

    /**
     * Wires the application for an environment.
     *
     * @throws io.github.vecluu.jdoc.error.JdocException if the JDK or the index database cannot be opened
     */
    public static Application create(JdocEnvironment environment) {
        CachePaths paths = environment.cachePaths();
        // Opened first, on a background thread (sqlite-jdbc's native library and connection setup), while this
        // thread wires the graph and the command resolves the module's classpath; the first query waits for it.
        IndexDatabase database = IndexDatabase.openInBackground(paths);
        try {
            JdkInfo jdk = new JdkInfoReader().read(environment.javaHome());
            Clock clock = Clock.systemUTC();
            JdbcArtifactRepository artifacts = new JdbcArtifactRepository(database);
            JdbcTypeRepository types = new JdbcTypeRepository(database);
            JdbcSupertypeRepository supertypes = new JdbcSupertypeRepository(database);
            JdbcMemberRepository members = new JdbcMemberRepository(database);
            JdbcDocRepository docs = new JdbcDocRepository(database);
            SqliteIndexStore store = new SqliteIndexStore(database, artifacts, types, members,
                    new JdbcFailedArtifactRepository(database), docs);

            Supplier<ArtifactIndexer> jarIndexer = memoized(() -> new JarArtifactIndexer(new ZipBinaryJarScanner(),
                    new SiblingDocJarLocator(), new ZipArchiveOpener(), new JavadocPageParser(), JavaSourceParser::new,
                    store, clock));
            Supplier<JdkIndexer> jdkIndexer = memoized(() -> new DefaultJdkIndexer(store, JavaSourceParser::new,
                    new ClassFileReader(), new JdkPackageFilter(), DefaultJdkIndexer.defaultThreads(), clock));
            ClasspathIndexer indexer = new ClasspathIndexer(
                    store,
                    (gav, jar, stamp) -> jarIndexer.get().index(gav, jar, stamp),
                    // A lambda, so the indexer always uses JdkIndexer's default indexedFile; an override in
                    // DefaultJdkIndexer would have no effect here.
                    (jdkInfo, stamp) -> jdkIndexer.get().index(jdkInfo, stamp),
                    jdk,
                    new FileIndexLock(paths.lockFile(), FileIndexLock.DEFAULT_TIMEOUT),
                    new NioFileStatter(),
                    new DecompiledDirectoryEvictor(paths));

            DecompiledSourceCache decompiled = new DecompiledSourceCache(paths, lazyVineflower());
            LookupServicesFactory lookups = () -> LookupServices.create(artifacts, types, supertypes, members, docs,
                    new ZipJarEntryReader(), decompiled);

            IndexingModuleContext context = new IndexingModuleContext(
                    MavenClasspathResolver.create(environment), indexer, lookups, environment.workingDirectory());
            return new CommandApplication(context, database::close);
        } catch (RuntimeException e) {
            database.close();
            throw e;
        }
    }

    /** A decompiler that loads Vineflower on its first use only. */
    private static Decompiler lazyVineflower() {
        // A lambda, not VineflowerDecompiler::new: a constructor reference would resolve the class right away.
        Supplier<Decompiler> vineflower = memoized(() -> new VineflowerDecompiler());
        return (Path jar, TypeName type) -> vineflower.get().decompile(jar, type);
    }

    /** A supplier creating its value on the first call only (single-threaded use). */
    private static <T> Supplier<T> memoized(Supplier<T> factory) {
        return new Supplier<>() {
            private T value;

            @Override
            public T get() {
                if (value == null) {
                    value = factory.get();
                }
                return value;
            }
        };
    }
}
