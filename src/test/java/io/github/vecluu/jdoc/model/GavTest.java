package io.github.vecluu.jdoc.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GavTest {

    private final Gav lang3 = Gav.of("org.apache.commons", "commons-lang3", "3.17.0");

    @Test
    void formatsAsGroupArtifactVersion() {
        assertThat(lang3).hasToString("org.apache.commons:commons-lang3:3.17.0");
        assertThat(lang3.coordinates()).isEqualTo("org.apache.commons:commons-lang3");
        assertThat(lang3.type()).isEqualTo("jar");
        assertThat(lang3.classifier()).isEmpty();
        assertThat(lang3.canonical()).isEqualTo("org.apache.commons:commons-lang3:3.17.0");
    }

    @Test
    void parsesThreeFourAndFivePartForms() {
        assertThat(Gav.parse("g:a:1.0")).isEqualTo(Gav.of("g", "a", "1.0"));
        assertThat(Gav.parse("g:a:pom:1.0")).isEqualTo(new Gav("g", "a", "1.0", Optional.empty(), "pom"));
        assertThat(Gav.parse(" g:a:jar:linux-x86_64:1.0 "))
                .isEqualTo(new Gav("g", "a", "1.0", Optional.of("linux-x86_64"), "jar"));
    }

    @Test
    void canonicalRoundTripsThroughParse() {
        Gav classified = new Gav("io.netty", "netty-epoll", "4.1.0", Optional.of("linux"), "jar");
        Gav typed = new Gav("g", "a", "1", Optional.empty(), "test-jar");
        for (Gav gav : new Gav[] {lang3, classified, typed}) {
            assertThat(Gav.parse(gav.canonical())).isEqualTo(gav);
        }
        assertThat(classified.canonical()).isEqualTo("io.netty:netty-epoll:jar:linux:4.1.0");
        assertThat(typed.canonical()).isEqualTo("g:a:test-jar:1");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "g:a", "g:a:b:c:d:e", "g::1", "g:a: "})
    void rejectsMalformedCoordinates(String text) {
        assertThatThrownBy(() -> Gav.parse(text)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsIllegalCharactersAndNulls() {
        assertThatThrownBy(() -> Gav.of("g/x", "a", "1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Gav.of("g", "a b", "1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Gav.of(null, "a", "1")).isInstanceOf(NullPointerException.class);
    }

    @Test
    void normalizesNullClassifierAndType() {
        Gav gav = new Gav("g", "a", "1", null, null);
        assertThat(gav.classifier()).isEmpty();
        assertThat(gav.type()).isEqualTo("jar");
        assertThat(new Gav("g", "a", "1", Optional.of(" "), "jar").classifier()).isEmpty();
    }

    @Test
    void detectsSnapshots() {
        assertThat(lang3.isSnapshot()).isFalse();
        assertThat(Gav.of("g", "a", "1.0-SNAPSHOT").isSnapshot()).isTrue();
        Gav timestamped = Gav.of("g", "a", "1.0-20240101.120000-3");
        assertThat(timestamped.isSnapshot()).isTrue();
        assertThat(timestamped.baseVersion()).isEqualTo("1.0-SNAPSHOT");
        assertThat(lang3.baseVersion()).isEqualTo("3.17.0");
    }

    @Test
    void repositoryPaths() {
        Path dir = Path.of("org/apache/commons/commons-lang3/3.17.0");
        assertThat(lang3.groupPath()).isEqualTo("org/apache/commons");
        assertThat(lang3.repositoryDirectory()).isEqualTo(dir);
        assertThat(lang3.repositoryPath()).isEqualTo(dir.resolve("commons-lang3-3.17.0.jar"));
        assertThat(lang3.pomPath()).isEqualTo(dir.resolve("commons-lang3-3.17.0.pom"));
    }

    @Test
    void classifierAndTypeAffectFileName() {
        assertThat(lang3.withClassifier("tests").fileName()).isEqualTo("commons-lang3-3.17.0-tests.jar");
        assertThat(new Gav("g", "a", "1", Optional.empty(), "test-jar").fileName()).isEqualTo("a-1.jar");
        assertThat(new Gav("g", "a", "1", Optional.empty(), "pom").fileName()).isEqualTo("a-1.pom");
        Gav snapshot = Gav.of("g", "a", "1.0-20240101.120000-3");
        assertThat(snapshot.repositoryPath()).isEqualTo(Path.of("g/a/1.0-SNAPSHOT/a-1.0-20240101.120000-3.jar"));
    }
}
