package io.github.vecluu.jdoc.module;

import static io.github.vecluu.jdoc.module.PomFixtures.artifact;
import static io.github.vecluu.jdoc.module.PomFixtures.dependencies;
import static io.github.vecluu.jdoc.module.PomFixtures.dependency;
import static io.github.vecluu.jdoc.module.PomFixtures.gav;
import static io.github.vecluu.jdoc.module.PomFixtures.modules;
import static io.github.vecluu.jdoc.module.PomFixtures.parent;
import static io.github.vecluu.jdoc.module.PomFixtures.pom;
import static io.github.vecluu.jdoc.module.PomFixtures.touch;
import static io.github.vecluu.jdoc.module.PomFixtures.writeFile;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.vecluu.jdoc.config.JdocEnvironment;
import io.github.vecluu.jdoc.model.Gav;
import io.github.vecluu.jdoc.model.ModuleClasspath;
import io.github.vecluu.jdoc.model.ResolutionStatus;
import io.github.vecluu.jdoc.model.ResolvedDependency;
import io.github.vecluu.jdoc.model.Scope;
import io.github.vecluu.jdoc.testsupport.FakeMavenRepo;
import io.github.vecluu.jdoc.testsupport.SampleLibrary;
import io.github.vecluu.jdoc.testsupport.TestEnvironments;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end resolution of a temp multi-module project (root + app + lib) with a fake {@code mvn} shell script that
 * copies a prepared {@code deps.txt} and logs its calls.
 */
class ClasspathResolverIT {

    @TempDir
    Path tmp;

    private JdocEnvironment env;
    private Path control;
    private Path project;
    private Path app;
    private ResolvedDependency sampleLib;

    @BeforeEach
    void setUp() throws Exception {
        env = TestEnvironments.inTempDir(tmp);
        control = Files.createDirectories(tmp.resolve("control"));
        project = tmp.toRealPath().resolve("project");
        app = project.resolve("app");
        pom(project, gav("com.acme", "root", "1.0-SNAPSHOT"), "<packaging>pom</packaging>", modules("app", "lib"));
        pom(project.resolve("lib"), parent("com.acme", "root", "1.0-SNAPSHOT"), artifact("lib"));
        pom(app, parent("com.acme", "root", "1.0-SNAPSHOT"), artifact("app"),
                dependencies(dependency("com.example", "sample-lib"), dependency("com.acme", "lib")));

        FakeMavenRepo repo = TestEnvironments.repository(env);
        sampleLib = repo.install(SampleLibrary.complete(), Scope.COMPILE);
        ResolvedDependency sibling = new ResolvedDependency(Gav.of("com.acme", "lib", "1.0-SNAPSHOT"),
                Scope.COMPILE, touch(project.resolve("lib/target/lib-1.0-SNAPSHOT.jar")));
        writeFile(control.resolve("deps.txt"), FakeMavenRepo.depsTxt(List.of(sampleLib, sibling))
                .replace("sample-lib-1.0.0.jar", "sample-lib-1.0.0.jar -- module sample.lib (auto)"));

        TestEnvironments.installFakeMaven(env, """
                ctl='%s'
                echo "$(pwd) $*" >> "$ctl/calls.log"
                echo "[INFO] noise on stdout"
                echo "noise on stderr" >&2
                if [ -f "$ctl/fail" ]; then echo "[ERROR] offline: artifact not available"; exit 1; fi
                if [ -f "$ctl/fail-in-module" ] && [ "$(pwd)" = '%s' ]; then exit 1; fi
                for a in "$@"; do case "$a" in -DoutputFile=*) out="${a#-DoutputFile=}";; esac; done
                cp "$ctl/deps.txt" "$out"
                """.formatted(control, app));
    }

    @Test
    void resolvesOnceThenServesFromCacheWithoutReactorSiblings() {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        ModuleClasspath first;
        ModuleClasspath second;
        System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
        try {
            first = resolver().resolve(app.resolve("src/main/java/com/acme/App.java"));
            second = resolver().resolve(app);
        } finally {
            System.setOut(originalOut);
        }

        assertThat(calls()).singleElement().asString().startsWith(project + " -o -q -B test-compile dependency:list "
                + "-DincludeScope=test -DoutputAbsoluteArtifactFilename=true -DoutputFile="
                + env.cachePaths().moduleDirectory(first.pomKey()) + "/").endsWith(" -pl app -am");
        assertThat(captured.toString(StandardCharsets.UTF_8)).isEmpty();
        assertThat(first.status()).isEqualTo(ResolutionStatus.RESOLVED);
        assertThat(first.dependencies()).containsExactly(sampleLib);
        assertThat(second).isEqualTo(first);
        assertThat(env.cachePaths().depsFile(first.pomKey())).exists();
        assertThat(env.cachePaths().metaFile(first.pomKey())).content().contains("\"modulePath\": \"" + app + "\"");
    }

    @Test
    void editingAPomTriggersReResolution() {
        ModuleClasspath first = resolver().resolve(app);
        resolver().resolve(app);

        pom(project, gav("com.acme", "root", "1.0-SNAPSHOT"), "<packaging>pom</packaging>", modules("app", "lib"),
                "<properties><foo>bar</foo></properties>");
        ModuleClasspath second = resolver().resolve(app);

        assertThat(calls()).hasSize(2);
        assertThat(second.pomKey()).isNotEqualTo(first.pomKey());
        assertThat(second.status()).isEqualTo(ResolutionStatus.RESOLVED);
    }

    @Test
    void failingMavenFallsBackToLastGoodList() throws Exception {
        resolver().resolve(app);
        Files.createFile(control.resolve("fail"));
        pom(app, parent("com.acme", "root", "1.0-SNAPSHOT"), artifact("app"),
                dependencies(dependency("com.example", "sample-lib"), dependency("org.gone", "gone")));

        ModuleClasspath stale = resolver().resolve(app);

        assertThat(stale.status()).isEqualTo(ResolutionStatus.STALE);
        assertThat(stale.warnings()).singleElement().asString()
                .startsWith("warning: mvn failed, using classpath resolved at ");
        assertThat(stale.dependencies()).containsExactly(sampleLib);
        assertThat(calls()).hasSize(3);
        assertThat(calls().get(2)).startsWith(project + " ").endsWith(" -pl app -am");
        try (var files = Files.list(env.cachePaths().moduleDirectory(stale.pomKey()))) {
            assertThat(files).isEmpty();
        }
    }

    @Test
    void failureInModuleIsRetriedFromReactorRoot() throws Exception {
        pom(app, parent("com.acme", "root", "1.0-SNAPSHOT"), artifact("app"),
                dependencies(dependency("com.example", "sample-lib")));
        Files.createFile(control.resolve("fail-in-module"));

        ModuleClasspath classpath = resolver().resolve(app);

        assertThat(classpath.status()).isEqualTo(ResolutionStatus.RESOLVED);
        assertThat(classpath.dependencies()).containsExactly(sampleLib);
        assertThat(calls()).hasSize(2);
        assertThat(calls().get(0)).startsWith(app + " ");
        assertThat(calls().get(1)).startsWith(project + " ").endsWith(" -pl app -am");
    }

    @Test
    void noMavenAndNoListGuessesFromLocalRepository() throws Exception {
        Files.createFile(control.resolve("fail"));

        ModuleClasspath classpath = resolver().resolve(app);

        assertThat(classpath.status()).isEqualTo(ResolutionStatus.UNRESOLVED);
        assertThat(classpath.dependencies()).containsExactly(sampleLib);
    }

    private MavenClasspathResolver resolver() {
        return MavenClasspathResolver.create(env);
    }

    private List<String> calls() {
        try {
            Path log = control.resolve("calls.log");
            return Files.exists(log) ? Files.readAllLines(log) : List.of();
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
