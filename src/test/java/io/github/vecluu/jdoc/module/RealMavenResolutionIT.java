package io.github.vecluu.jdoc.module;

import static io.github.vecluu.jdoc.module.PomFixtures.artifact;
import static io.github.vecluu.jdoc.module.PomFixtures.dependencies;
import static io.github.vecluu.jdoc.module.PomFixtures.dependency;
import static io.github.vecluu.jdoc.module.PomFixtures.gav;
import static io.github.vecluu.jdoc.module.PomFixtures.modules;
import static io.github.vecluu.jdoc.module.PomFixtures.parent;
import static io.github.vecluu.jdoc.module.PomFixtures.pom;
import static io.github.vecluu.jdoc.module.PomFixtures.writeFile;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.github.vecluu.jdoc.config.JdocEnvironment;
import io.github.vecluu.jdoc.model.ModuleClasspath;
import io.github.vecluu.jdoc.model.ResolutionStatus;
import io.github.vecluu.jdoc.model.ResolvedDependency;
import io.github.vecluu.jdoc.model.Scope;
import io.github.vecluu.jdoc.testsupport.SampleLibrary;
import io.github.vecluu.jdoc.testsupport.TestEnvironments;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Resolution with the real {@code mvn} (from {@code JDOC_MVN} or {@code PATH}) against a temp multi-module project:
 * root + {@code a} (depends on the sample library) + {@code b} (depends on {@code a}, which is not installed).
 *
 * <p>The real {@code ~/.m2/repository} is only read: it is chained as Maven's read-only
 * {@code maven.repo.local.tail} (set in the project's {@code .mvn/maven.config}), while the writable local repository
 * is a temp one holding the sample library. Everything runs offline; the tests are skipped when there is no Maven or
 * when the real repository lacks the plugins of an offline {@code test-compile} + {@code dependency:list} run.
 */
class RealMavenResolutionIT {

    @TempDir
    Path tmp;

    private JdocEnvironment env;
    private Path project;
    private ResolvedDependency sampleLib;

    @BeforeEach
    void setUp() throws Exception {
        Optional<Path> maven = findMaven();
        assumeTrue(maven.isPresent(), "no mvn on PATH (or JDOC_MVN)");
        Path realRepository = Path.of(System.getProperty("user.home"), ".m2", "repository");
        assumeTrue(new DependencyPluginLocator(realRepository, new MavenVersionComparator()).qualifiedListGoal()
                .isPresent(), "maven-dependency-plugin is not in " + realRepository);

        env = TestEnvironments.inTempDir(tmp).toBuilder()
                .mavenExecutable(maven.get().toString())
                .localRepositoryExplicit(true)
                .build();
        sampleLib = TestEnvironments.repository(env).install(SampleLibrary.complete(), Scope.COMPILE);
        project = tmp.toRealPath().resolve("project");
        pom(project, gav("demo.t19", "parent", "1.0-SNAPSHOT"), "<packaging>pom</packaging>", modules("a", "b"),
                "<properties><project.build.sourceEncoding>UTF-8</project.build.sourceEncoding></properties>");
        pom(project.resolve("a"), parent("demo.t19", "parent", "1.0-SNAPSHOT"), artifact("a"),
                dependencies(dependency("com.example", "sample-lib", "<version>1.0.0</version>")));
        pom(project.resolve("b"), parent("demo.t19", "parent", "1.0-SNAPSHOT"), artifact("b"),
                dependencies(dependency("demo.t19", "a", "<version>${project.version}</version>")));
        writeFile(project.resolve(".mvn/maven.config"), "-Dmaven.repo.local.tail=" + realRepository + "\n");

        MavenResult probe = new ProcessMavenInvoker(env.mavenExecutable(), env.javaHome(), Duration.ofMinutes(2))
                .run(new MavenCommand(project, List.of("-o", "-q", "-B", "test-compile",
                        "-Dmaven.repo.local=" + env.localRepository(), "-Dmaven.main.skip=true",
                        "-Dmaven.test.skip=true", "-Dmaven.resources.skip=true", "-pl", "a")));
        assumeTrue(probe.success(), () -> "offline test-compile is not possible here: " + probe.summary());
    }

    @Test
    void moduleWithoutSiblingsResolvesInItsDirectory() {
        ModuleClasspath classpath = resolver().resolve(project.resolve("a"));

        assertThat(classpath.status()).isEqualTo(ResolutionStatus.RESOLVED);
        assertThat(classpath.warnings()).isEmpty();
        assertThat(classpath.dependencies()).containsExactly(sampleLib);
    }

    @Test
    void uninstalledSiblingIsResolvedThroughTheReactorAndLeftOut() throws Exception {
        ModuleClasspath classpath = resolver().resolve(project.resolve("b/src/main/java/B.java"));

        assertThat(classpath.status()).isEqualTo(ResolutionStatus.RESOLVED);
        assertThat(classpath.dependencies()).containsExactly(sampleLib);
        try (Stream<Path> targets = Files.find(project, 3, (path, attributes) ->
                path.getFileName().toString().equals("target"))) {
            assertThat(targets).as("nothing is compiled").isEmpty();
        }
        assertThat(Files.exists(env.localRepository().resolve("demo"))).as("nothing is installed").isFalse();
    }

    private MavenClasspathResolver resolver() {
        return MavenClasspathResolver.create(env, ProcessMavenInvoker.from(env), Clock.systemDefaultZone());
    }

    private static Optional<Path> findMaven() {
        List<Path> candidates = new ArrayList<>();
        Optional.ofNullable(System.getenv(JdocEnvironment.ENV_MVN)).filter(value -> !value.isBlank())
                .map(Path::of).ifPresent(candidates::add);
        String path = Optional.ofNullable(System.getenv("PATH")).orElse("");
        for (String directory : path.split(File.pathSeparator)) {
            if (!directory.isBlank()) {
                candidates.add(Path.of(directory, "mvn"));
            }
        }
        return candidates.stream().filter(Files::isExecutable).findFirst();
    }
}
