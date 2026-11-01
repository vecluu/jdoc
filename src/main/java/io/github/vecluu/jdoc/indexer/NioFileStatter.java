package io.github.vecluu.jdoc.indexer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/** {@link FileStatter} on the real filesystem (follows symbolic links). */
@Slf4j
public final class NioFileStatter implements FileStatter {

    @Override
    public Optional<FileStamp> stat(Path file) {
        try {
            BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
            if (!attributes.isRegularFile()) {
                return Optional.empty();
            }
            return Optional.of(new FileStamp(attributes.lastModifiedTime().toMillis(), attributes.size()));
        } catch (IOException e) {
            log.debug("cannot stat {}: {}", file, e.toString());
            return Optional.empty();
        }
    }
}
