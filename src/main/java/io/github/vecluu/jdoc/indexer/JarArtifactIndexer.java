package io.github.vecluu.jdoc.indexer;

import io.github.vecluu.jdoc.bytecode.BinaryJarScanner;
import io.github.vecluu.jdoc.bytecode.ScannedClass;
import io.github.vecluu.jdoc.javadoc.JavadocJarReader;
import io.github.vecluu.jdoc.javadoc.JavadocPageParser;
import io.github.vecluu.jdoc.model.ArtifactKey;
import io.github.vecluu.jdoc.model.DocSource;
import io.github.vecluu.jdoc.model.Gav;
import io.github.vecluu.jdoc.model.IndexedArtifact;
import io.github.vecluu.jdoc.model.TypeName;
import io.github.vecluu.jdoc.source.JavaSourceParser;
import io.github.vecluu.jdoc.source.KnownTypes;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link ArtifactIndexer} for jar artifacts: the binary jar lists what exists (every named type, package-private
 * ones too); each top-level type then takes the best doc source available ({@link TypeDocResolver}), and each
 * package its {@code package-info.java} or {@code package-summary.html} doc. Types, members and their rendered docs
 * are written in batches inside one transaction.
 */
@Slf4j
@RequiredArgsConstructor
public final class JarArtifactIndexer implements ArtifactIndexer {

    private static final int BATCH_SIZE = 500;

    private final BinaryJarScanner scanner;
    private final DocJarLocator docJars;
    private final ArchiveOpener archives;
    private final JavadocPageParser pageParser;
    private final Supplier<JavaSourceParser> parsers;
    private final IndexStore store;
    private final Clock clock;

    @Override
    public StoredArtifact index(Gav gav, Path jar, FileStamp stamp) {
        long start = System.nanoTime();
        Map<TypeName, List<ScannedClass>> groups = new LinkedHashMap<>();
        List<TypeName> names = new ArrayList<>();
        scanner.scan(jar, scanned -> {
            TypeName name = scanned.type().name();
            names.add(name);
            groups.computeIfAbsent(name.topLevel(), top -> new ArrayList<>()).add(scanned);
        });
        DocJars located = docJars.locate(gav, jar);
        IndexedArtifact artifact = IndexedArtifact.builder()
                .key(ArtifactKey.maven(gav))
                .jarPath(jar)
                .jarMtime(stamp.mtimeMillis())
                .jarSize(stamp.size())
                .sourcesJar(located.sourcesJar())
                .javadocJar(located.javadocJar())
                .indexedAt(clock.instant())
                .build();
        StoredArtifact stored = store.replace(artifact,
                sink -> produce(groups, KnownTypes.of(names), located, sink));
        log.debug("indexed {} ({} types, {} members) in {} ms", gav, stored.typeCount(), stored.memberCount(),
                (System.nanoTime() - start) / 1_000_000);
        return stored;
    }

    private void produce(Map<TypeName, List<ScannedClass>> groups, KnownTypes knownTypes, DocJars located,
                         EntrySink sink) {
        try (Resources resources = new Resources()) {
            Optional<SourceArchive> sources = located.sourcesJar().map(archives::openSources);
            sources.ifPresent(resources::register);
            Optional<JavadocArchive> javadoc = located.javadocJar().map(archives::openJavadoc);
            javadoc.ifPresent(resources::register);
            Optional<SourceFiles> sourceFiles = sources.map(archive -> new SourceFiles(archive,
                    resources.register(parsers.get()), knownTypes, DocSource.SOURCES));
            TypeDocResolver resolver = new TypeDocResolver(sourceFiles,
                    javadoc.map(pages -> new JavadocJarReader(pages, pageParser)));
            List<TypeEntry> batch = new ArrayList<>();
            Set<String> packages = new TreeSet<>();
            for (Map.Entry<TypeName, List<ScannedClass>> group : groups.entrySet()) {
                packages.add(group.getKey().packageName());
                batch.addAll(resolver.resolve(group.getKey(), group.getValue()));
                if (batch.size() >= BATCH_SIZE) {
                    sink.types(List.copyOf(batch));
                    batch.clear();
                }
            }
            sink.types(List.copyOf(batch));
            for (String packageName : packages) {
                resolver.packageDoc(packageName).ifPresent(doc -> sink.packageDoc(packageName, doc));
            }
        }
    }

    /** Closes opened archives and the parser in reverse order. */
    private static final class Resources implements AutoCloseable {
        private final List<AutoCloseable> opened = new ArrayList<>();

        <T extends AutoCloseable> T register(T resource) {
            opened.add(resource);
            return resource;
        }

        @Override
        public void close() {
            RuntimeException failure = null;
            for (int i = opened.size() - 1; i >= 0; i--) {
                try {
                    opened.get(i).close();
                } catch (Exception e) {
                    if (failure == null) {
                        failure = e instanceof RuntimeException runtime ? runtime : new IllegalStateException(e);
                    }
                }
            }
            if (failure != null) {
                throw failure;
            }
        }
    }
}
