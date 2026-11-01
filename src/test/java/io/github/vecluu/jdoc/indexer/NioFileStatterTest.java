package io.github.vecluu.jdoc.indexer;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NioFileStatterTest {

    @TempDir
    Path dir;

    @Test
    void readsMtimeAndSize() throws IOException {
        Path file = Files.writeString(dir.resolve("a.jar"), "12345");
        Files.setLastModifiedTime(file, FileTime.fromMillis(1_700_000_000_000L));

        assertThat(new NioFileStatter().stat(file)).contains(new FileStamp(1_700_000_000_000L, 5));
    }

    @Test
    void missingFilesAndDirectoriesAreEmpty() {
        assertThat(new NioFileStatter().stat(dir.resolve("missing.jar"))).isEmpty();
        assertThat(new NioFileStatter().stat(dir)).isEmpty();
    }
}
