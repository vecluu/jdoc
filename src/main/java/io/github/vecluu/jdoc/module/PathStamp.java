package io.github.vecluu.jdoc.module;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * What a path looked like at one moment, as one comparable string: absent, a directory, or a regular file with its
 * size, modification and status-change times and identity (device and inode where the platform has them).
 *
 * @param value     the comparable form, without tabs or line breaks
 * @param changedAt the latest of the file's modification and status-change times; empty for anything but a file
 */
public record PathStamp(String value, Optional<Instant> changedAt) {

    /** The stamp of a path that does not exist. */
    public static final PathStamp ABSENT = new PathStamp("absent", Optional.empty());

    /** The stamp of a directory (only its kind matters for POM analysis). */
    public static final PathStamp DIRECTORY = new PathStamp("dir", Optional.empty());

    public PathStamp {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(changedAt, "changedAt");
        if (value.isEmpty() || value.indexOf('\t') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("stamp must be one non-empty token: " + value);
        }
    }
}
