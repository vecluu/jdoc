package io.github.vecluu.jdoc.module;

import static io.github.vecluu.jdoc.module.PomFixtures.artifact;
import static io.github.vecluu.jdoc.module.PomFixtures.dependencies;
import static io.github.vecluu.jdoc.module.PomFixtures.dependency;
import static io.github.vecluu.jdoc.module.PomFixtures.gav;
import static io.github.vecluu.jdoc.module.PomFixtures.modules;
import static io.github.vecluu.jdoc.module.PomFixtures.parent;
import static io.github.vecluu.jdoc.module.PomFixtures.pom;
import static io.github.vecluu.jdoc.module.PomFixtures.touch;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.vecluu.jdoc.config.JdocEnvironment;
import io.github.vecluu.jdoc.error.ResolutionException;
import io.github.vecluu.jdoc.model.ModuleClasspath;
import io.github.vecluu.jdoc.model.ResolutionStatus;
import io.github.vecluu.jdoc.model.ResolvedDependency;
import io.github.vecluu.jdoc.testsupport.TestEnvironments;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MavenClasspathResolverTest {

    @TempDir
    Path tmp;

    private JdocEnvironment env;
    private Path project;
    private Path app;
    private Path repo;
    private FakeMavenInvoker maven;
    private MutableClock clock;

    @BeforeEach
    void setUp() {
        env = TestEnvironments.inTempDir(tmp);
        repo = env.localRepository();
        project = realPath(tmp).resolve("project");
        app = project.resolve("app");
        pom(project, gav("com.acme", "root", "1.0"), modules("app", "lib"));
        pom(project.resolve("lib"), parent("com.acme", "root", "1.0"), artifact("lib"));
        pom(app, parent("com.acme", "root", "1.0"), artifact("app"),
                dependencies(dependency("org.ext", "ext"), dependency("com.acme", "lib")));
        maven = new FakeMavenInvoker().otherwiseFails();
        clock = new MutableClock(Instant.parse("2026-11-01T09:30:00Z"));
    }

    @Test
    void missRunsMavenAndHitDoesNot() {
        maven.thenWrites(deps("org.ext:ext:jar:2.0:compile", "ext-2.0.jar"));

        ModuleClasspath first = resolver().resolve(app.resolve("src/main/java/App.java"));
        ModuleClasspath second = resolver().resolve(app);

        assertThat(maven.commands()).hasSize(1);
        assertThat(maven.commands().getFirst().workingDirectory()).isEqualTo(app);
        assertThat(first.status()).isEqualTo(ResolutionStatus.RESOLVED);
        assertThat(first.warnings()).isEmpty();
        assertThat(first.moduleDirectory()).isEqualTo(app);
        assertThat(first.pomKey()).matches("[0-9a-f]{64}");
        assertThat(first.dependencies()).extracting(dep -> dep.gav().toString()).containsExactly("org.ext:ext:2.0");
        assertThat(second).isEqualTo(first);
        assertThat(env.cachePaths().metaFile(first.pomKey())).exists();
    }

    @Test
    void reactorModulesAreDropped() {
        maven.thenWrites(deps("org.ext:ext:jar:2.0:compile", "ext-2.0.jar")
                + deps("com.acme:lib:jar:1.0:compile", "lib-1.0.jar"));

        assertThat(resolver().resolve(app).dependencies()).extracting(dep -> dep.gav().artifactId())
                .containsExactly("ext");
    }

    @Test
    void pomChangeTriggersNewResolution() {
        maven.thenWrites(deps("org.ext:ext:jar:2.0:compile", "ext-2.0.jar"))
                .thenWrites(deps("org.ext:ext:jar:3.0:compile", "ext-3.0.jar"));
        ModuleClasspath first = resolver().resolve(app);

        pom(project, gav("com.acme", "root", "1.0"), modules("app", "lib"), "<properties><x>y</x></properties>");
        ModuleClasspath second = resolver().resolve(app);

        assertThat(maven.commands()).hasSize(2);
        assertThat(second.pomKey()).isNotEqualTo(first.pomKey());
        assertThat(second.dependencies()).extracting(dep -> dep.gav().version()).containsExactly("3.0");
    }

    @Test
    void failureFallsBackToLastGoodListWithOneWarning() {
        maven.thenWrites(deps("org.ext:ext:jar:2.0:compile", "ext-2.0.jar"));
        resolver().resolve(app);
        pom(app, parent("com.acme", "root", "1.0"), artifact("app"), "<description>broken</description>");
        clock.set(Instant.parse("2026-11-02T00:00:00Z"));

        ModuleClasspath stale = resolver().resolve(app);

        assertThat(stale.status()).isEqualTo(ResolutionStatus.STALE);
        assertThat(stale.warnings()).containsExactly("warning: mvn failed, using classpath resolved at 2026-11-01 09:30");
        assertThat(stale.dependencies()).extracting(dep -> dep.gav().version()).containsExactly("2.0");
        assertThat(maven.commands()).hasSize(3);
        assertThat(maven.commands().get(2).workingDirectory()).isEqualTo(project);
        assertThat(maven.commands().get(2).arguments()).endsWith("-pl", "app", "-am");
        assertThat(env.cachePaths().depsFile(stale.pomKey())).doesNotExist();
    }

    @Test
    void failedResolutionIsRetriedOnNextCall() {
        maven.thenFails("offline").thenFails("offline")
                .thenWrites(deps("org.ext:ext:jar:2.0:compile", "ext-2.0.jar"));
        touch(repo.resolve("org/ext/ext/1.0/ext-1.0.jar"));

        ModuleClasspath unresolved = resolver().resolve(app);
        ModuleClasspath resolved = resolver().resolve(app);

        assertThat(unresolved.status()).isEqualTo(ResolutionStatus.UNRESOLVED);
        assertThat(resolved.status()).isEqualTo(ResolutionStatus.RESOLVED);
        assertThat(maven.commands()).hasSize(3);
    }

    @Test
    void noListAtAllGuessesNewestVersionsAndSkipsReactorModules() {
        maven.thenFails("offline");
        touch(repo.resolve("org/ext/ext/1.0/ext-1.0.jar"));
        touch(repo.resolve("org/ext/ext/1.2/ext-1.2.jar"));
        touch(repo.resolve("com/acme/lib/1.0/lib-1.0.jar"));

        ModuleClasspath classpath = resolver().resolve(app);

        assertThat(classpath.status()).isEqualTo(ResolutionStatus.UNRESOLVED);
        assertThat(classpath.warnings()).singleElement().asString().startsWith("warning: mvn failed");
        assertThat(classpath.dependencies()).extracting(ResolvedDependency::jar)
                .containsExactly(repo.resolve("org/ext/ext/1.2/ext-1.2.jar"));
    }

    @Test
    void guessIncludesDependenciesDeclaredInLocalParents() {
        pom(project, gav("com.acme", "root", "1.0"), modules("app", "lib"),
                dependencies(dependency("org.common", "common")));
        maven.thenFails("offline");
        touch(repo.resolve("org/ext/ext/1.0/ext-1.0.jar"));
        touch(repo.resolve("org/common/common/4/common-4.jar"));

        assertThat(resolver().resolve(app).dependencies()).extracting(dep -> dep.gav().artifactId())
                .containsExactly("ext", "common");
    }

    @Test
    void nothingUsableThrows() {
        maven.thenFails("Could not resolve dependencies for project com.acme:app")
                .thenFails("Could not resolve dependencies for project com.acme:app");

        assertThatThrownBy(() -> resolver().resolve(app))
                .isInstanceOf(ResolutionException.class)
                .hasMessageContaining("mvn failed")
                .hasMessageContaining("Could not resolve dependencies");
    }

    @Test
    void moduleWithoutDependenciesIsUnresolvedButEmptyWhenMavenFails() {
        Path standalone = tmp.resolve("standalone");
        pom(standalone, gav("g", "s", "1"));
        maven.thenFails("offline");

        ModuleClasspath classpath = resolver().resolve(standalone);

        assertThat(classpath.status()).isEqualTo(ResolutionStatus.UNRESOLVED);
        assertThat(classpath.dependencies()).isEmpty();
    }

    @Test
    void noModuleThrows() {
        assertThatThrownBy(() -> resolver().resolve(tmp.resolve("work")))
                .isInstanceOf(ResolutionException.class);
    }

    private static Path realPath(Path path) {
        try {
            return path.toRealPath();
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private MavenClasspathResolver resolver() {
        return MavenClasspathResolver.create(env, maven, clock);
    }

    private String deps(String coordinatesAndScope, String jarName) {
        Path jar = touch(repo.resolve("jars").resolve(jarName));
        return "   " + coordinatesAndScope + ":" + jar + "\n";
    }

    /** A clock in UTC whose instant tests can move. */
    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void set(Instant instant) {
            now = instant;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
