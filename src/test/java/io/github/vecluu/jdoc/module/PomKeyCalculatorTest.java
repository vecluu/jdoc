package io.github.vecluu.jdoc.module;

import static io.github.vecluu.jdoc.module.PomFixtures.artifact;
import static io.github.vecluu.jdoc.module.PomFixtures.gav;
import static io.github.vecluu.jdoc.module.PomFixtures.parent;
import static io.github.vecluu.jdoc.module.PomFixtures.pom;
import static io.github.vecluu.jdoc.module.PomFixtures.writeFile;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.vecluu.jdoc.model.Gav;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PomKeyCalculatorTest {

    @TempDir
    Path tmp;

    private Path repo;
    private PomKeyCalculator calculator;

    @BeforeEach
    void setUp() {
        repo = tmp.resolve("repo");
        calculator = new PomKeyCalculator(new PomHierarchy(new PomReader()), repo);
    }

    @Test
    void keyIsLowercaseHexSha256AndStable() {
        Path module = pom(tmp.resolve("app"), gav("g", "app", "1"));

        PomKey first = calculator.calculate(module);
        PomKey second = calculator.calculate(module);

        assertThat(first.value()).matches("[0-9a-f]{64}");
        assertThat(second).isEqualTo(first);
        assertThat(first.fingerprints()).singleElement()
                .satisfies(f -> assertThat(f.file()).isEqualTo(module));
    }

    @Test
    void changesWhenModulePomChanges() {
        Path module = pom(tmp.resolve("app"), gav("g", "app", "1"));
        String before = calculator.calculate(module).value();

        pom(tmp.resolve("app"), gav("g", "app", "2"));

        assertThat(calculator.calculate(module).value()).isNotEqualTo(before);
    }

    @Test
    void includesMatchingLocalParentAndChangesWithIt() {
        pom(tmp, gav("g", "parent", "1"), "<packaging>pom</packaging>");
        Path module = pom(tmp.resolve("app"), parent("g", "parent", "1"), artifact("app"));
        PomKey before = calculator.calculate(module);

        assertThat(before.fingerprints()).extracting(PomFingerprint::file)
                .containsExactly(module, tmp.resolve("pom.xml"));

        pom(tmp, gav("g", "parent", "1"), "<packaging>pom</packaging>", "<properties><x>1</x></properties>");

        assertThat(calculator.calculate(module).value()).isNotEqualTo(before.value());
    }

    @Test
    void walksGrandparentChain() {
        pom(tmp, gav("g", "root", "1"));
        pom(tmp.resolve("mid"), parent("g", "root", "1"), artifact("mid"));
        Path module = pom(tmp.resolve("mid/app"), parent("g", "mid", "1"), artifact("app"));

        assertThat(calculator.calculate(module).fingerprints()).hasSize(3);
    }

    @Test
    void ignoresLocalFileWhoseCoordinatesDoNotMatch() {
        pom(tmp, gav("g", "unrelated", "1"));
        Path module = pom(tmp.resolve("app"), parent("org.springframework.boot", "spring-boot-starter-parent", "3.0.0"),
                artifact("app"));
        String before = calculator.calculate(module).value();

        pom(tmp, gav("g", "unrelated", "2"));

        assertThat(calculator.calculate(module).value()).isEqualTo(before);
        assertThat(calculator.calculate(module).fingerprints()).hasSize(1);
    }

    @Test
    void emptyRelativePathSkipsLocalFile() {
        pom(tmp, gav("g", "parent", "1"));
        Path module = pom(tmp.resolve("app"), parent("g", "parent", "1", "<relativePath/>"), artifact("app"));
        String before = calculator.calculate(module).value();

        pom(tmp, gav("g", "parent", "1"), "<description>changed</description>");

        assertThat(calculator.calculate(module).value()).isEqualTo(before);
    }

    @Test
    void releaseParentFromRepositoryDoesNotContribute() throws Exception {
        Gav parentGav = Gav.of("org.acme", "acme-parent", "5");
        Path repoPom = writeFile(repo.resolve(parentGav.pomPath()), "<project/>");
        Path module = pom(tmp.resolve("app"), parent("org.acme", "acme-parent", "5"), artifact("app"));
        String before = calculator.calculate(module).value();

        Files.setLastModifiedTime(repoPom, FileTime.from(Instant.parse("2020-01-01T00:00:00Z")));

        assertThat(calculator.calculate(module).value()).isEqualTo(before);
    }

    @Test
    void snapshotParentFromRepositoryAddsItsMtime() throws Exception {
        Gav parentGav = Gav.of("org.acme", "acme-parent", "5-SNAPSHOT");
        Path repoPom = writeFile(repo.resolve(parentGav.pomPath()), "<project/>");
        Files.setLastModifiedTime(repoPom, FileTime.from(Instant.parse("2024-01-01T00:00:00Z")));
        Path module = pom(tmp.resolve("app"), parent("org.acme", "acme-parent", "5-SNAPSHOT"), artifact("app"));
        PomKey before = calculator.calculate(module);

        assertThat(before.fingerprints()).last().satisfies(f -> {
            assertThat(f.file()).isEqualTo(repoPom);
            assertThat(f.fingerprint()).isEqualTo("mtime:" + Instant.parse("2024-01-01T00:00:00Z").toEpochMilli());
        });

        Files.setLastModifiedTime(repoPom, FileTime.from(Instant.parse("2024-02-01T00:00:00Z")));

        assertThat(calculator.calculate(module).value()).isNotEqualTo(before.value());
    }

    @Test
    void missingSnapshotParentIsMarkedAbsent() {
        Path module = pom(tmp.resolve("app"), parent("org.acme", "acme-parent", "5-SNAPSHOT"), artifact("app"));

        assertThat(calculator.calculate(module).fingerprints()).last()
                .satisfies(f -> assertThat(f.fingerprint()).isEqualTo("mtime:absent"));
    }

    @Test
    void sameContentInDifferentModuleDirectoriesGivesDifferentKeys() {
        Path a = pom(tmp.resolve("a"), gav("g", "app", "1"));
        Path b = pom(tmp.resolve("b"), gav("g", "app", "1"));

        assertThat(calculator.calculate(a).value()).isNotEqualTo(calculator.calculate(b).value());
    }

    @Test
    void malformedModulePomIsHashedOnItsOwn() {
        Path module = writeFile(tmp.resolve("app/pom.xml"), "<project><broken>");

        PomKey key = calculator.calculate(module);

        assertThat(key.value()).matches("[0-9a-f]{64}");
        assertThat(key.fingerprints()).hasSize(1);
    }
}
