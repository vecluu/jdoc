package io.github.vecluu.jdoc.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.vecluu.jdoc.error.ConfigurationException;
import io.github.vecluu.jdoc.model.ArtifactKey;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JdkInfoReaderTest {

    @TempDir
    Path jdk;

    private final JdkInfoReader reader = new JdkInfoReader();

    @Test
    void readsFeatureVersionAndSrcZip() throws IOException {
        release("IMPLEMENTOR=\"Eclipse Adoptium\"\nJAVA_VERSION=\"25.0.1\"\nOS_NAME=\"Linux\"\n");
        Files.createDirectories(jdk.resolve("lib"));
        Files.writeString(jdk.resolve("lib/src.zip"), "zip");

        JdkInfo info = reader.read(jdk);

        assertThat(info.featureVersion()).isEqualTo(25);
        assertThat(info.srcZip()).contains(jdk.resolve("lib/src.zip"));
        assertThat(info.javaHome()).isEqualTo(jdk);
        assertThat(info.artifactKey()).isEqualTo(ArtifactKey.jdk(25));
    }

    @Test
    void missingSrcZipIsEmpty() throws IOException {
        release("JAVA_VERSION=\"21\"\n");
        JdkInfo info = reader.read(jdk);
        assertThat(info.featureVersion()).isEqualTo(21);
        assertThat(info.srcZip()).isEmpty();
    }

    @Test
    void legacyAndVendorVersionFormats() {
        assertThat(JdkInfoReader.featureVersion("1.8.0_392", jdk)).isEqualTo(8);
        assertThat(JdkInfoReader.featureVersion("25.0.5.0.2", jdk)).isEqualTo(25);
        assertThat(JdkInfoReader.featureVersion("26-ea", jdk)).isEqualTo(26);
        assertThat(JdkInfoReader.featureVersion("17+35", jdk)).isEqualTo(17);
    }

    @Test
    void failsForNonJdkDirectories() throws IOException {
        assertThatThrownBy(() -> reader.read(jdk)).isInstanceOf(ConfigurationException.class)
                .hasMessageContaining("not a JDK");
        release("OS_NAME=\"Linux\"\n");
        assertThatThrownBy(() -> reader.read(jdk)).isInstanceOf(ConfigurationException.class)
                .hasMessageContaining("JAVA_VERSION");
        release("JAVA_VERSION=\"abc\"\n");
        assertThatThrownBy(() -> reader.read(jdk)).isInstanceOf(ConfigurationException.class)
                .hasMessageContaining("abc");
    }

    @Test
    void readsTheRunningJdk() {
        JdkInfo info = reader.read(Path.of(System.getProperty("java.home")));
        assertThat(info.featureVersion()).isEqualTo(Runtime.version().feature());
    }

    private void release(String content) throws IOException {
        Files.writeString(jdk.resolve("release"), content);
    }
}
