package io.github.vecluu.jdoc.module;

import static io.github.vecluu.jdoc.module.PomFixtures.writeFile;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NioPathStamperTest {

    @TempDir
    Path tmp;

    private final NioPathStamper stamper = new NioPathStamper();

    @Test
    void absentAndDirectoryHaveFixedStamps() {
        assertThat(stamper.stamp(tmp.resolve("missing"))).isEqualTo(PathStamp.ABSENT);
        assertThat(stamper.stamp(tmp)).isEqualTo(PathStamp.DIRECTORY);
    }

    @Test
    void fileStampIsStableAndChangesWithContentOrTime() throws Exception {
        Path file = writeFile(tmp.resolve("pom.xml"), "<project/>");
        PathStamp first = stamper.stamp(file);

        assertThat(first.value()).startsWith("file:10:");
        assertThat(stamper.stamp(file)).isEqualTo(first);
        assertThat(first.changedAt()).isPresent();

        Files.setLastModifiedTime(file, FileTime.from(Instant.parse("2020-01-01T00:00:00Z")));
        assertThat(stamper.stamp(file).value()).isNotEqualTo(first.value());

        writeFile(file, "<project></project>");
        assertThat(stamper.stamp(file).value()).startsWith("file:19:");
    }

    @Test
    void changedAtIsTheLaterOfModificationAndStatusChange() throws Exception {
        Path file = writeFile(tmp.resolve("pom.xml"), "<project/>");
        Files.setLastModifiedTime(file, FileTime.from(Instant.parse("2020-01-01T00:00:00Z")));

        assertThat(stamper.stamp(file).changedAt().orElseThrow())
                .isAfter(Instant.parse("2020-01-01T00:00:00Z").plusSeconds(1));
    }
}
