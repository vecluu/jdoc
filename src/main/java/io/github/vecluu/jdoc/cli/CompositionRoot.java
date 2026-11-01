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
import io.github.vecluu.jdoc.index.JdbcFailedArtifactRepository;
import io.github.vecluu.jdoc.index.JdbcMemberRepository;
import io.github.vecluu.jdoc.index.JdbcSupertypeRepository;
import io.github.vecluu.jdoc.index.JdbcTypeRepository;
import io.github.vecluu.jdoc.indexer.ClasspathIndexer;
import io.github.vecluu.jdoc.indexer.DecompiledDirectoryEvictor;
import io.github.vecluu.jdoc.indexer.DefaultJdkIndexer;
import io.github.vecluu.jdoc.indexer.JarArtifactIndexer;
import io.github.vecluu.jdoc.indexer.JdkPackageFilter;
import io.github.vecluu.jdoc.indexer.NioFileStatter;
import io.github.vecluu.jdoc.indexer.SiblingDocJarLocator;
import io.github.vecluu.jdoc.indexer.SqliteIndexStore;
import io.github.vecluu.jdoc.indexer.ZipArchiveOpener;
import io.github.vecluu.jdoc.javadoc.JavadocPageParser;
import io.github.vecluu.jdoc.lookup.JarJavadocDocs;
import io.github.vecluu.jdoc.lookup.LookupServices;
import io.github.vecluu.jdoc.lookup.ZipJarEntryReader;
import io.github.vecluu.jdoc.model.TypeName;
import io.github.vecluu.jdoc.module.MavenClasspathResolver;
import io.github.vecluu.jdoc.source.JavaSourceParser;
import java.nio.file.Path;
import java.time.Clock;

/**
 * The composition root: the only place constructing the concrete components, from a {@link JdocEnvironment}.
 *
 * <p>Startup cost is kept off the hot path: constructing the graph is cheap (no I/O besides opening
 * {@code index.db} and reading the JDK's {@code release} file); javac parsers are created per indexing run or on the
 * first source parse, jsoup is first touched when a javadoc page is read, and Vineflower is created only when a type
 * is actually decompiled. The returned {@link Application} closes the database.
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
        JdkInfo jdk = new JdkInfoReader().read(environment.javaHome());
        IndexDatabase database = IndexDatabase.open(paths);
        try {
            Clock clock = Clock.systemUTC();
            JdbcArtifactRepository artifacts = new JdbcArtifactRepository(database);
            JdbcTypeRepository types = new JdbcTypeRepository(database);
            JdbcSupertypeRepository supertypes = new JdbcSupertypeRepository(database);
            JdbcMemberRepository members = new JdbcMemberRepository(database);
            SqliteIndexStore store = new SqliteIndexStore(database, artifacts, types, members,
                    new JdbcFailedArtifactRepository(database));

            ClasspathIndexer indexer = new ClasspathIndexer(
                    store,
                    new JarArtifactIndexer(new ZipBinaryJarScanner(), new SiblingDocJarLocator(),
                            new ZipArchiveOpener(), new JavadocPageParser(), JavaSourceParser::new, store, clock),
                    new DefaultJdkIndexer(store, JavaSourceParser::new, new ClassFileReader(), new JdkPackageFilter(),
                            DefaultJdkIndexer.defaultThreads(), clock),
                    jdk,
                    new FileIndexLock(paths.lockFile(), FileIndexLock.DEFAULT_TIMEOUT),
                    new NioFileStatter(),
                    new DecompiledDirectoryEvictor(paths));

            DecompiledSourceCache decompiled = new DecompiledSourceCache(paths, lazyVineflower());
            LookupServicesFactory lookups = () -> LookupServices.create(artifacts, types, supertypes, members,
                    new ZipJarEntryReader(), new JarJavadocDocs(), decompiled);

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
        return new Decompiler() {
            private Decompiler delegate;

            @Override
            public String decompile(Path jar, TypeName type) {
                if (delegate == null) {
                    delegate = new VineflowerDecompiler();
                }
                return delegate.decompile(jar, type);
            }
        };
    }
}
