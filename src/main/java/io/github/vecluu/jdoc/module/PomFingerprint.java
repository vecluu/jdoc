package io.github.vecluu.jdoc.module;

import java.nio.file.Path;
import java.util.Objects;

/**
 * One input of a POM key: a file and what was hashed from it.
 *
 * @param file        the POM file
 * @param fingerprint lowercase hex SHA-256 of the file's bytes, or {@code mtime:<millis>} / {@code mtime:absent}
 *                    for a SNAPSHOT parent taken from the repository
 */
public record PomFingerprint(Path file, String fingerprint) {
    public PomFingerprint {
        Objects.requireNonNull(file, "file must not be null");
        Objects.requireNonNull(fingerprint, "fingerprint must not be null");
    }
}
