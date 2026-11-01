package io.github.vecluu.jdoc.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.vecluu.jdoc.error.ConfigurationException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JdocEnvironmentTest {

    private final Map<String, String> properties = new HashMap<>(Map.of(
            "user.dir", "/work/project",
            "user.home", "/home/prop",
            "java.home", "/opt/jdk-prop"));

    @Test
    void defaultsFromHomeAndProperties() {
        JdocEnvironment env = JdocEnvironment.from(Map.of("HOME", "/home/env"), properties);

        assertThat(env.cacheDirectory()).isEqualTo(Path.of("/home/env/.cache/jdoc"));
        assertThat(env.localRepository()).isEqualTo(Path.of("/home/env/.m2/repository"));
        assertThat(env.localRepositoryExplicit()).isFalse();
        assertThat(env.javaHome()).isEqualTo(Path.of("/opt/jdk-prop"));
        assertThat(env.mavenExecutable()).isEqualTo("mvn");
        assertThat(env.workingDirectory()).isEqualTo(Path.of("/work/project"));
    }

    @Test
    void userHomePropertyIsFallbackForHome() {
        JdocEnvironment env = JdocEnvironment.from(Map.of(), properties);
        assertThat(env.cacheDirectory()).isEqualTo(Path.of("/home/prop/.cache/jdoc"));
    }

    @Test
    void environmentVariablesOverrideDefaults() {
        JdocEnvironment env = JdocEnvironment.from(Map.of(
                "HOME", "/home/env",
                "JDOC_CACHE", "/var/cache/jdoc/",
                "JDOC_M2_REPO", "/srv/m2/./repository",
                "JAVA_HOME", "/opt/jdk-25",
                "JDOC_MVN", "/opt/maven/bin/mvn"), properties);

        assertThat(env.cacheDirectory()).isEqualTo(Path.of("/var/cache/jdoc"));
        assertThat(env.localRepository()).isEqualTo(Path.of("/srv/m2/repository"));
        assertThat(env.localRepositoryExplicit()).isTrue();
        assertThat(env.javaHome()).isEqualTo(Path.of("/opt/jdk-25"));
        assertThat(env.mavenExecutable()).isEqualTo("/opt/maven/bin/mvn");
    }

    @Test
    void expandsTildeAndResolvesRelativePaths() {
        JdocEnvironment env = JdocEnvironment.from(Map.of(
                "HOME", "/home/env",
                "JDOC_CACHE", "~/c",
                "JDOC_M2_REPO", "../repo",
                "JAVA_HOME", "~",
                "JDOC_MVN", "tools/mvnw"), properties);

        assertThat(env.cacheDirectory()).isEqualTo(Path.of("/home/env/c"));
        assertThat(env.localRepository()).isEqualTo(Path.of("/work/repo"));
        assertThat(env.javaHome()).isEqualTo(Path.of("/home/env"));
        assertThat(env.mavenExecutable()).isEqualTo("/work/project/tools/mvnw");
    }

    @Test
    void bareMavenCommandStaysOnPath() {
        JdocEnvironment env = JdocEnvironment.from(Map.of("HOME", "/h", "JDOC_MVN", " mvnd "), properties);
        assertThat(env.mavenExecutable()).isEqualTo("mvnd");
    }

    @Test
    void blankVariablesCountAsUnset() {
        JdocEnvironment env = JdocEnvironment.from(
                Map.of("HOME", "/h", "JDOC_CACHE", " ", "JAVA_HOME", "", "JDOC_MVN", ""), properties);
        assertThat(env.cacheDirectory()).isEqualTo(Path.of("/h/.cache/jdoc"));
        assertThat(env.javaHome()).isEqualTo(Path.of("/opt/jdk-prop"));
        assertThat(env.mavenExecutable()).isEqualTo("mvn");
    }

    @Test
    void failsWithoutWorkingDirectoryOrHome() {
        properties.remove("user.dir");
        assertThatThrownBy(() -> JdocEnvironment.from(Map.of("HOME", "/h"), properties))
                .isInstanceOf(ConfigurationException.class).hasMessageContaining("user.dir");

        properties.put("user.dir", "/w");
        properties.remove("user.home");
        assertThatThrownBy(() -> JdocEnvironment.from(Map.of(), properties))
                .isInstanceOf(ConfigurationException.class).hasMessageContaining("home");
    }

    @Test
    void homeIsNotNeededWhenEverythingIsExplicit() {
        properties.remove("user.home");
        JdocEnvironment env = JdocEnvironment.from(
                Map.of("JDOC_CACHE", "/c", "JDOC_M2_REPO", "/r"), properties);
        assertThat(env.cacheDirectory()).isEqualTo(Path.of("/c"));
    }

    @Test
    void builderNormalizesAndValidates() {
        JdocEnvironment env = JdocEnvironment.builder()
                .cacheDirectory(Path.of("cache"))
                .localRepository(Path.of("/r/../repo"))
                .javaHome(Path.of("/jdk"))
                .mavenExecutable("mvn")
                .workingDirectory(Path.of("/w/./x"))
                .build();
        assertThat(env.cacheDirectory()).isEqualTo(Path.of("/w/x/cache"));
        assertThat(env.localRepository()).isEqualTo(Path.of("/repo"));
        assertThat(env.cachePaths().root()).isEqualTo(Path.of("/w/x/cache"));

        assertThatThrownBy(() -> env.toBuilder().workingDirectory(Path.of("rel")).build())
                .isInstanceOf(ConfigurationException.class);
        assertThatThrownBy(() -> env.toBuilder().mavenExecutable(" ").build())
                .isInstanceOf(ConfigurationException.class);
    }

    @Test
    void fromSystemReadsTheRealProcess() {
        JdocEnvironment env = JdocEnvironment.fromSystem();
        assertThat(env.workingDirectory()).isAbsolute();
        assertThat(env.javaHome()).isAbsolute();
    }
}
