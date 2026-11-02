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

import io.github.vecluu.jdoc.config.JdocEnvironment;
import io.github.vecluu.jdoc.model.ModuleClasspath;
import io.github.vecluu.jdoc.model.ResolutionStatus;
import io.github.vecluu.jdoc.model.ResolvedDependency;
import io.github.vecluu.jdoc.model.Scope;
import io.github.vecluu.jdoc.testsupport.Prerequisites;
import io.github.vecluu.jdoc.testsupport.SampleLibrary;
import io.github.vecluu.jdoc.testsupport.TestEnvironments;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Resolution with a real {@code mvn} against a temp multi-module project: root + {@code a} (depends on the sample
 * library) + {@code b} (depends on {@code a}, which is not installed).
 *
 * <p>The Maven is the one running the build ({@code jdoc.mavenHome}, set by failsafe from {@code maven.home}, so it
 * works under {@code ./mvnw} without {@code mvn} on the PATH), else {@code JDOC_MVN}, else the {@code PATH}. The build's
 * local repository ({@code jdoc.localRepository}, default {@code ~/.m2/repository}) is only read: it is chained as
 * Maven's read-only {@code maven.repo.local.tail} (set in the project's {@code .mvn/maven.config}), while the writable
 * local repository is a temp one holding the sample library. Everything runs offline. The temp project pins the
 * plugins the build itself has just resolved ({@code jdoc.offlinePlugins}: compiler, resources, dependency), so the
 * offline run never needs a plugin the build did not download. A missing prerequisite skips the tests locally and
 * fails them in CI (see {@link Prerequisites}).
 */
@Tag("slow")
class RealMavenResolutionIT {

    static final String MAVEN_HOME_PROPERTY = "jdoc.mavenHome";
    static final String LOCAL_REPOSITORY_PROPERTY = "jdoc.localRepository";
    static final String OFFLINE_PLUGINS_PROPERTY = "jdoc.offlinePlugins";

    @TempDir
    Path tmp;

    private JdocEnvironment env;
    private Path project;
    private ResolvedDependency sampleLib;

    @BeforeEach
    void setUp() throws Exception {
        Prerequisites prerequisites = Prerequisites.fromEnvironment();
        Optional<Path> maven = findMaven();
        prerequisites.require(maven.isPresent(),
                "no Maven: neither -D" + MAVEN_HOME_PROPERTY + " (set by failsafe), JDOC_MVN nor mvn on the PATH");
        Path buildRepository = buildRepository();
        prerequisites.require(new DependencyPluginLocator(buildRepository, new MavenVersionComparator())
                .qualifiedListGoal().isPresent(), () -> "maven-dependency-plugin is not in " + buildRepository);

        env = TestEnvironments.inTempDir(tmp).toBuilder()
                .mavenExecutable(maven.get().toString())
                .localRepositoryExplicit(true)
                .build();
        sampleLib = TestEnvironments.repository(env).install(SampleLibrary.complete(), Scope.COMPILE);
        project = tmp.toRealPath().resolve("project");
        pom(project, gav("demo.t19", "parent", "1.0-SNAPSHOT"), "<packaging>pom</packaging>", modules("a", "b"),
                "<properties><project.build.sourceEncoding>UTF-8</project.build.sourceEncoding></properties>",
                pluginManagement(System.getProperty(OFFLINE_PLUGINS_PROPERTY, "")));
        pom(project.resolve("a"), parent("demo.t19", "parent", "1.0-SNAPSHOT"), artifact("a"),
                dependencies(dependency("com.example", "sample-lib", "<version>1.0.0</version>")));
        pom(project.resolve("b"), parent("demo.t19", "parent", "1.0-SNAPSHOT"), artifact("b"),
                dependencies(dependency("demo.t19", "a", "<version>${project.version}</version>")));
        writeFile(project.resolve(".mvn/maven.config"), "-Dmaven.repo.local.tail=" + buildRepository + "\n");

        MavenResult probe = new ProcessMavenInvoker(env.mavenExecutable(), env.javaHome(), Duration.ofMinutes(2))
                .run(new MavenCommand(project, List.of("-o", "-q", "-B", "test-compile",
                        "-Dmaven.repo.local=" + env.localRepository(), "-Dmaven.main.skip=true",
                        "-Dmaven.test.skip=true", "-Dmaven.resources.skip=true", "-pl", "a")));
        prerequisites.require(probe.success(), () -> "offline test-compile with " + maven.get() + " and "
                + buildRepository + " as the read-only repository failed: " + probe.summary());
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

    /** The build's Maven ({@code jdoc.mavenHome}), else {@code JDOC_MVN}, else the first {@code mvn} on the PATH. */
    private static Optional<Path> findMaven() {
        List<Path> candidates = new ArrayList<>();
        Optional.ofNullable(System.getProperty(MAVEN_HOME_PROPERTY)).filter(value -> !value.isBlank())
                .map(home -> Path.of(home, "bin", "mvn")).ifPresent(candidates::add);
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

    private static Path buildRepository() {
        return Optional.ofNullable(System.getProperty(LOCAL_REPOSITORY_PROPERTY)).filter(value -> !value.isBlank())
                .map(Path::of)
                .orElseGet(() -> Path.of(System.getProperty("user.home"), ".m2", "repository"));
    }

    /**
     * {@code <build><pluginManagement>} pinning {@code artifactId:version,...} of {@code org.apache.maven.plugins}
     * plugins; empty without plugins (IDE run without the failsafe properties: Maven's defaults apply).
     */
    private static String pluginManagement(String plugins) {
        String pinned = Arrays.stream(plugins.split(",")).map(String::strip).filter(plugin -> !plugin.isEmpty())
                .map(plugin -> plugin.split(":", 2))
                .map(parts -> "<plugin><groupId>org.apache.maven.plugins</groupId><artifactId>%s</artifactId>"
                        .formatted(parts[0]) + "<version>%s</version></plugin>".formatted(parts[1]))
                .collect(Collectors.joining());
        return pinned.isEmpty() ? "" : "<build><pluginManagement><plugins>" + pinned
                + "</plugins></pluginManagement></build>";
    }
}
