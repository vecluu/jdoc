package io.github.vecluu.jdoc.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class ArtifactKeyTest {

    @Test
    void mavenKey() {
        ArtifactKey key = ArtifactKey.maven(Gav.of("org.apache.commons", "commons-lang3", "3.17.0"));
        assertThat(key.keyString()).isEqualTo("org.apache.commons:commons-lang3:3.17.0");
        assertThat(key.label()).isEqualTo("commons-lang3 3.17.0");
        assertThat(key.isJdk()).isFalse();
        assertThat(ArtifactKey.parse(key.keyString())).isEqualTo(key);
    }

    @Test
    void classifiedMavenKeyIsDistinct() {
        Gav gav = new Gav("io.netty", "netty-epoll", "4.1.0", Optional.of("linux"), "jar");
        ArtifactKey key = ArtifactKey.maven(gav);
        assertThat(key.keyString()).isEqualTo("io.netty:netty-epoll:jar:linux:4.1.0");
        assertThat(key.label()).isEqualTo("netty-epoll:linux 4.1.0");
        assertThat(ArtifactKey.parse(key.keyString())).isEqualTo(key);
    }

    @Test
    void jdkKey() {
        ArtifactKey key = ArtifactKey.jdk(25);
        assertThat(key.keyString()).isEqualTo("jdk:25");
        assertThat(key.label()).isEqualTo("jdk 25");
        assertThat(key.isJdk()).isTrue();
        assertThat(ArtifactKey.parse("jdk:25")).isEqualTo(key);
    }

    @Test
    void rejectsInvalidKeys() {
        assertThatThrownBy(() -> ArtifactKey.parse("jdk:x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ArtifactKey.jdk(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ArtifactKey.parse("nonsense")).isInstanceOf(IllegalArgumentException.class);
    }
}
