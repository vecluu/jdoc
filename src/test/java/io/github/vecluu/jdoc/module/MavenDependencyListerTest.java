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
}
