package io.github.vecluu.jdoc.module;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MavenDependencyListerTest {

    @TempDir
    Path tmp;

    @Test
    void runsTheExactCommandInTheModuleDirectory() {
        FakeMavenInvoker invoker = new FakeMavenInvoker().thenWrites("deps");
        Path output = tmp.resolve("deps.txt");

        MavenResult result = new MavenDependencyLister(invoker).list(tmp.resolve("app"), Optional.empty(), output);

        assertThat(result.success()).isTrue();
        assertThat(invoker.commands()).singleElement().satisfies(command -> {
            assertThat(command.workingDirectory()).isEqualTo(tmp.resolve("app"));
            assertThat(command.arguments()).containsExactly("-o", "-q", "-B", "dependency:list",
                    "-DincludeScope=test", "-DoutputAbsoluteArtifactFilename=true", "-DoutputFile=" + output);
        });
    }

    @Test
    void appendsExtraArgumentsToEveryRun() {
        FakeMavenInvoker invoker = new FakeMavenInvoker().thenFails("sibling not found").thenWrites("deps");
        Path output = tmp.resolve("deps.txt");

        MavenDependencyLister.withExtraArguments(invoker, List.of("-Dmaven.repo.local=/repo"))
                .list(tmp.resolve("m"), Optional.of(tmp), output);

        assertThat(invoker.commands()).hasSize(2).allSatisfy(command ->
                assertThat(command.arguments()).contains("-Dmaven.repo.local=/repo", "-DoutputFile=" + output));
    }

    @Test
    void retriesFromReactorRootWithProjectList() {
        FakeMavenInvoker invoker = new FakeMavenInvoker().thenFails("sibling not found").thenWrites("deps");
        Path module = tmp.resolve("services/impl");
        Path output = tmp.resolve("deps.txt");

        MavenResult result = new MavenDependencyLister(invoker).list(module, Optional.of(tmp), output);

        assertThat(result.success()).isTrue();
        assertThat(invoker.commands()).hasSize(2);
        assertThat(invoker.commands().get(1).workingDirectory()).isEqualTo(tmp);
        assertThat(invoker.commands().get(1).arguments()).endsWith("-pl", "services/impl", "-am")
                .contains("-DoutputFile=" + output, "dependency:list", "-o");
    }

    @Test
    void doesNotRetryWhenModuleIsTheRoot() {
        FakeMavenInvoker invoker = new FakeMavenInvoker().thenFails("boom");

        MavenResult result = new MavenDependencyLister(invoker).list(tmp, Optional.of(tmp), tmp.resolve("o"));

        assertThat(result.success()).isFalse();
        assertThat(result.summary()).isEqualTo("boom");
        assertThat(invoker.commands()).hasSize(1);
    }

    @Test
    void doesNotRetryWithoutReactorRootOrOutsideIt() {
        FakeMavenInvoker invoker = new FakeMavenInvoker().thenFails("a").thenFails("b");
        MavenDependencyLister lister = new MavenDependencyLister(invoker);

        lister.list(tmp.resolve("app"), Optional.empty(), tmp.resolve("o"));
        lister.list(tmp.resolve("app"), Optional.of(tmp.resolve("elsewhere")), tmp.resolve("o"));

        assertThat(invoker.commands()).hasSize(2);
    }

    @Test
    void bothRunsFailingReportsTheRetryError() {
        FakeMavenInvoker invoker = new FakeMavenInvoker().thenFails("first").thenFails("second");

        MavenResult result = new MavenDependencyLister(invoker).list(tmp.resolve("m"), Optional.of(tmp),
                tmp.resolve("o"));

        assertThat(result.success()).isFalse();
        assertThat(result.summary()).isEqualTo("second");
    }

    @Test
    void successWithoutOutputFileIsAFailure() {
        FakeMavenInvoker invoker = new FakeMavenInvoker().thenSucceedsWithoutOutput();

        MavenResult result = new MavenDependencyLister(invoker).list(tmp, Optional.empty(), tmp.resolve("o"));

        assertThat(result.success()).isFalse();
        assertThat(result.output()).contains("did not write");
    }

    @Test
    void partialOutputOfFailedFirstRunIsRemovedBeforeRetry() throws Exception {
        Path output = tmp.resolve("deps.txt");
        FakeMavenInvoker invoker = new FakeMavenInvoker().thenWrites("partial").thenSucceedsWithoutOutput();
        MavenInvoker failingAfterWrite = command -> {
            MavenResult result = invoker.run(command);
            return command.arguments().contains("-am") ? result : new MavenResult(false, 1, "[ERROR] x");
        };

        MavenResult result = new MavenDependencyLister(failingAfterWrite).list(tmp.resolve("m"), Optional.of(tmp),
                output);

        assertThat(result.success()).isFalse();
        assertThat(Files.exists(output)).isFalse();
    }

    @Test
    void reactorCommandRunsTestCompileWithCompilationSkipped() {
        FakeMavenInvoker invoker = new FakeMavenInvoker().thenFails("sibling not found").thenWrites("deps");
        Path output = tmp.resolve("deps.txt");

        new MavenDependencyLister(invoker).list(tmp.resolve("b"), Optional.of(tmp), output);

        assertThat(invoker.commands().get(1).arguments()).containsExactly("-o", "-q", "-B", "test-compile",
                "dependency:list", "-DincludeScope=test", "-DoutputAbsoluteArtifactFilename=true",
                "-DoutputFile=" + output, "-Dmaven.main.skip=true", "-Dmaven.test.skip=true",
                "-Dmaven.resources.skip=true", "-pl", "b", "-am");
    }

    @Test
    void reactorFirstSkipsTheModuleRun() {
        FakeMavenInvoker invoker = new FakeMavenInvoker().thenWrites("deps").otherwiseFails();

        MavenResult result = new MavenDependencyLister(invoker).list(tmp.resolve("b"), Optional.of(tmp), true,
                tmp.resolve("o"));

        assertThat(result.success()).isTrue();
        assertThat(invoker.commands()).singleElement()
                .satisfies(command -> assertThat(command.workingDirectory()).isEqualTo(tmp));
    }

    @Test
    void reactorFirstWithoutReactorRunsInTheModule() {
        FakeMavenInvoker invoker = new FakeMavenInvoker().thenWrites("deps");

        new MavenDependencyLister(invoker).list(tmp, Optional.of(tmp), true, tmp.resolve("o"));

        assertThat(invoker.commands()).singleElement()
                .satisfies(command -> assertThat(command.arguments()).doesNotContain("-am", "test-compile"));
    }

    @Test
    void missingPluginPrefixRetriesWithQualifiedGoalAndKeepsIt() {
        String qualified = "org.apache.maven.plugins:maven-dependency-plugin:3.11.0:list";
        FakeMavenInvoker invoker = new FakeMavenInvoker()
                .thenFails("No plugin found for prefix 'dependency' in the current project")
                .thenFails("sibling not found")
                .thenWrites("deps");

        MavenResult result = new MavenDependencyLister(invoker).withFallbackGoal(() -> Optional.of(qualified))
                .list(tmp.resolve("b"), Optional.of(tmp), tmp.resolve("o"));

        assertThat(result.success()).isTrue();
        assertThat(invoker.commands()).extracting(command -> command.arguments().contains(qualified))
                .containsExactly(false, true, true);
        assertThat(invoker.commands().get(2).arguments()).doesNotContain("dependency:list").endsWith("-am");
    }

    @Test
    void missingPluginPrefixWithoutLocalPluginKeepsTheFailure() {
        FakeMavenInvoker invoker = new FakeMavenInvoker().thenFails("No plugin found for prefix 'dependency'");

        MavenResult result = new MavenDependencyLister(invoker).withFallbackGoal(Optional::empty)
                .list(tmp, Optional.empty(), tmp.resolve("o"));

        assertThat(result.success()).isFalse();
        assertThat(invoker.commands()).hasSize(1);
    }

    @Test
    void otherFailuresDoNotAskForTheQualifiedGoal() {
        FakeMavenInvoker invoker = new FakeMavenInvoker().thenFails("Could not resolve dependencies");

        new MavenDependencyLister(invoker).withFallbackGoal(() -> {
            throw new AssertionError("not expected");
        }).list(tmp, Optional.empty(), tmp.resolve("o"));

        assertThat(invoker.commands()).hasSize(1);
    }
}
