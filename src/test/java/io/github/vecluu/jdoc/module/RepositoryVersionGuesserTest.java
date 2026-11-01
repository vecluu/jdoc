package io.github.vecluu.jdoc.module;

import static io.github.vecluu.jdoc.module.PomFixtures.touch;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.vecluu.jdoc.model.ResolvedDependency;
import io.github.vecluu.jdoc.model.Scope;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RepositoryVersionGuesserTest {

    @TempDir
    Path repo;

    private RepositoryVersionGuesser guesser;

    @BeforeEach
    void setUp() {
        guesser = new RepositoryVersionGuesser(repo, new MavenVersionComparator());
    }

    @Test
    void picksNewestVersionThatHasTheJar() throws Exception {
        jar("org.lib", "lib", "1.9");
        jar("org.lib", "lib", "1.10");
        jar("org.lib", "lib", "2.0-SNAPSHOT");
        jar("org.lib", "lib", "2.0-RC1");
        Files.createDirectories(repo.resolve("org/lib/lib/3.0"));
        touch(repo.resolve("org/lib/lib/4.0/lib-4.0.pom"));

        List<ResolvedDependency> guessed = guesser.guess(List.of(declared("org.lib", "lib", Scope.TEST)));

        assertThat(guessed).singleElement().satisfies(dep -> {
            assertThat(dep.gav().version()).isEqualTo("2.0-SNAPSHOT");
            assertThat(dep.scope()).isEqualTo(Scope.TEST);
            assertThat(dep.jar()).isEqualTo(repo.resolve("org/lib/lib/2.0-SNAPSHOT/lib-2.0-SNAPSHOT.jar"));
        });
    }

    @Test
    void releaseBeatsSnapshotOfSameVersion() throws Exception {
        jar("g", "a", "1.0-SNAPSHOT");
        jar("g", "a", "1.0");

        assertThat(guesser.guess(List.of(declared("g", "a", Scope.COMPILE))))
                .extracting(dep -> dep.gav().version()).containsExactly("1.0");
    }

    @Test
    void honoursClassifier() throws Exception {
        touch(repo.resolve("g/n/1.0/n-1.0-linux.jar"));
        touch(repo.resolve("g/n/2.0/n-2.0.jar"));

        List<ResolvedDependency> guessed = guesser.guess(List.of(
                new DeclaredDependency("g", "n", Scope.RUNTIME, "jar", Optional.of("linux"))));

        assertThat(guessed).singleElement().satisfies(dep -> {
            assertThat(dep.gav().version()).isEqualTo("1.0");
            assertThat(dep.gav().classifier()).contains("linux");
        });
    }

    @Test
    void skipsMissingNonJarSystemUnresolvedAndDuplicates() throws Exception {
        jar("g", "a", "1");
        jar("g", "sys", "1");

        List<ResolvedDependency> guessed = guesser.guess(List.of(
                declared("g", "a", Scope.COMPILE),
                declared("g", "a", Scope.TEST),
                declared("g", "missing", Scope.COMPILE),
                declared("g", "sys", Scope.SYSTEM),
                declared("${x}", "a", Scope.COMPILE),
                new DeclaredDependency("g", "a", Scope.COMPILE, "pom", Optional.empty())));

        assertThat(guessed).singleElement().satisfies(dep -> assertThat(dep.scope()).isEqualTo(Scope.COMPILE));
    }

    private void jar(String group, String artifact, String version) {
        touch(repo.resolve(group.replace('.', '/')).resolve(artifact).resolve(version)
                .resolve(artifact + "-" + version + ".jar"));
    }

    private static DeclaredDependency declared(String group, String artifact, Scope scope) {
        return new DeclaredDependency(group, artifact, scope, "jar", Optional.empty());
    }
}
