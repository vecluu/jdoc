package io.github.vecluu.jdoc.indexer;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.vecluu.jdoc.bytecode.BinaryJarScanner;
import io.github.vecluu.jdoc.bytecode.ScannedClass;
import io.github.vecluu.jdoc.javadoc.JavadocPageParser;
import io.github.vecluu.jdoc.model.ArtifactKey;
import io.github.vecluu.jdoc.model.DocSource;
import io.github.vecluu.jdoc.model.Gav;
import io.github.vecluu.jdoc.model.IndexedArtifact;
import io.github.vecluu.jdoc.model.TypeName;
import io.github.vecluu.jdoc.source.JavaSourceParser;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class JarArtifactIndexerTest {

    private static final Gav GAV = Gav.of("g", "a", "1.0");
    private static final Path JAR = Path.of("/m2/a-1.0.jar");
    private static final Path SOURCES = Path.of("/m2/a-1.0-sources.jar");
    private static final Path JAVADOC = Path.of("/m2/a-1.0-javadoc.jar");
    private static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");

    private final InMemoryIndexStore store = new InMemoryIndexStore();
    private final MapSourceArchive sources = new MapSourceArchive()
            .with("p/Documented.java", "package p; /** Documented. */ public class Documented extends Base {}")
            .with("p/Base.java", "package p; /** Base. */ public class Base {}");
    private final MapJavadocArchive javadoc = new MapJavadocArchive()
            .withTypePage("p/Generated.html", "Generated.", Map.of());

    /** Scanner fake streaming fixed classes, in an order where nested types come before their outer type. */
    private static final class FakeScanner implements BinaryJarScanner {
        private final List<ScannedClass> classes = List.of(
                Scanned.type("p.Documented$Inner"),
                Scanned.type("p.Documented", List.of(TypeName.of("p.Base"))),
                Scanned.type("p.Base"),
                Scanned.type("p.Generated"),
                Scanned.type("p.Plain"));

        @Override
        public void scan(Path jar, Consumer<ScannedClass> consumer) {
            classes.forEach(consumer);
        }
    }

    private ArchiveOpener opener() {
        return new ArchiveOpener() {
            @Override
            public SourceArchive openSources(Path jar) {
                assertThat(jar).isEqualTo(SOURCES);
                return sources;
            }

            @Override
            public JavadocArchive openJavadoc(Path jar) {
                assertThat(jar).isEqualTo(JAVADOC);
                return javadoc;
            }
        };
    }

    private JarArtifactIndexer indexer(DocJars docJars) {
        return new JarArtifactIndexer(new FakeScanner(), (gav, jar) -> docJars, opener(), new JavadocPageParser(),
                JavaSourceParser::new, store, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private Map<TypeName, DocSource> docSources() {
        return store.entries(ArtifactKey.maven(GAV)).stream()
                .collect(Collectors.toMap(e -> e.type().name(), e -> e.type().docSource()));
    }

    @Test
    void indexesEveryTypeWithItsOwnDocSourceAndClosesArchives() {
        StoredArtifact stored = indexer(new DocJars(Optional.of(SOURCES), Optional.of(JAVADOC)))
                .index(GAV, JAR, new FileStamp(123, 456));

        assertThat(docSources()).containsExactlyInAnyOrderEntriesOf(Map.of(
                TypeName.of("p.Documented"), DocSource.SOURCES,
                TypeName.of("p.Documented$Inner"), DocSource.DECOMPILED,
                TypeName.of("p.Base"), DocSource.SOURCES,
                TypeName.of("p.Generated"), DocSource.JAVADOC,
                TypeName.of("p.Plain"), DocSource.DECOMPILED));
        assertThat(stored.typeCount()).isEqualTo(5);
        IndexedArtifact artifact = stored.artifact();
        assertThat(artifact.key()).isEqualTo(ArtifactKey.maven(GAV));
        assertThat(artifact.jarPath()).isEqualTo(JAR);
        assertThat(artifact.jarMtime()).isEqualTo(123);
        assertThat(artifact.jarSize()).isEqualTo(456);
        assertThat(artifact.sourcesJar()).contains(SOURCES);
        assertThat(artifact.javadocJar()).contains(JAVADOC);
        assertThat(artifact.indexedAt()).isEqualTo(NOW);
        assertThat(sources.closed()).isTrue();
        assertThat(javadoc.closed()).isTrue();
    }

    @Test
    void withoutDocJarsEverythingIsDecompiled() {
        StoredArtifact stored = indexer(DocJars.none()).index(GAV, JAR, new FileStamp(1, 2));

        assertThat(docSources().values()).containsOnly(DocSource.DECOMPILED);
        assertThat(stored.artifact().sourcesJar()).isEmpty();
        assertThat(sources.closed()).isFalse();
    }

    @Test
    void outerAndNestedTypesAreGroupedByTopLevelType() {
        indexer(new DocJars(Optional.of(SOURCES), Optional.empty())).index(GAV, JAR, new FileStamp(1, 2));

        TypeEntry documented = store.entries(ArtifactKey.maven(GAV)).stream()
                .filter(e -> e.type().name().equals(TypeName.of("p.Documented"))).findFirst().orElseThrow();
        assertThat(documented.type().firstSentence()).isEqualTo("Documented.");
        assertThat(documented.type().supertypes()).containsExactly(TypeName.of("p.Base"));
    }
}
