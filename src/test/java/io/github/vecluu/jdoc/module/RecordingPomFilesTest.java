package io.github.vecluu.jdoc.module;

import static io.github.vecluu.jdoc.module.PomFixtures.writeFile;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RecordingPomFilesTest {

    @TempDir
    Path tmp;

    private final RecordingPomFiles files = new RecordingPomFiles(new NioPomFiles());

    @Test
    void recordsEveryTouchedPathInFirstUseOrder() throws Exception {
        Path pom = writeFile(tmp.resolve("a/pom.xml"), "<project/>");
        Path missing = tmp.resolve("b/pom.xml");

        RecordingPomFiles.Recorded<String> recorded = files.record(() -> {
            try {
                files.isRegularFile(missing);
                files.read(pom);
                files.isDirectory(tmp.resolve("a/../a"));
                files.lastModifiedTime(pom);
                return "done";
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        });

        assertThat(recorded.value()).isEqualTo("done");
        assertThat(recorded.paths()).containsExactly(missing, pom, tmp.resolve("a"));
    }

    @Test
    void delegatesWithoutRecordingOutsideARecording() throws Exception {
        Path pom = writeFile(tmp.resolve("pom.xml"), "<project/>");

        assertThat(files.read(pom)).asString().isEqualTo("<project/>");
        assertThat(files.isRegularFile(pom)).isTrue();
        assertThat(files.isDirectory(tmp)).isTrue();
        assertThat(files.record(() -> 1).paths()).isEmpty();
    }

    @Test
    void recordingsDoNotNestAndEndOnFailure() {
        assertThatThrownBy(() -> files.record(() -> files.record(() -> 1)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> files.record(() -> {
            throw new IllegalArgumentException("boom");
        })).hasMessage("boom");

        assertThat(files.record(() -> files.isRegularFile(tmp)).paths()).containsExactly(tmp);
    }
}
