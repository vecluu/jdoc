package io.github.vecluu.jdoc.module;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * {@link PathStamper} on the default file system: one {@code stat} per path. Where the {@code unix} attribute view
 * exists (Linux, macOS), a file's stamp includes its status-change time ({@code ctime}, which no tool can set back),
 * device and inode; elsewhere it falls back to size, modification time and the file key.
 */
public final class NioPathStamper implements PathStamper {

    private static final String UNIX_ATTRIBUTES = "unix:isRegularFile,isDirectory,size,lastModifiedTime,ctime,dev,ino";

    private boolean unixView = true;

    @Override
    public PathStamp stamp(Path path) {
        try {
            if (unixView) {
                try {
                    return unixStamp(Files.readAttributes(path, UNIX_ATTRIBUTES));
                } catch (UnsupportedOperationException | IllegalArgumentException e) {
                    unixView = false;
                }
            }
            return basicStamp(Files.readAttributes(path, BasicFileAttributes.class));
        } catch (NoSuchFileException e) {
            return PathStamp.ABSENT;
        } catch (IOException e) {
            return new PathStamp("error:" + e.getClass().getSimpleName(), Optional.empty());
        }
    }

    private static PathStamp unixStamp(Map<String, Object> attributes) {
        if (Boolean.TRUE.equals(attributes.get("isDirectory"))) {
            return PathStamp.DIRECTORY;
        }
        if (!Boolean.TRUE.equals(attributes.get("isRegularFile"))) {
            return new PathStamp("other", Optional.empty());
        }
        FileTime modified = (FileTime) attributes.get("lastModifiedTime");
        FileTime changed = (FileTime) attributes.get("ctime");
        String value = "file:" + attributes.get("size") + ":" + nanos(modified) + ":" + nanos(changed) + ":"
                + attributes.get("dev") + ":" + attributes.get("ino");
        Instant latest = modified.toInstant().isAfter(changed.toInstant()) ? modified.toInstant() : changed.toInstant();
        return new PathStamp(value, Optional.of(latest));
    }

    private static PathStamp basicStamp(BasicFileAttributes attributes) {
        if (attributes.isDirectory()) {
            return PathStamp.DIRECTORY;
        }
        if (!attributes.isRegularFile()) {
            return new PathStamp("other", Optional.empty());
        }
        Object key = attributes.fileKey();
        String value = "file:" + attributes.size() + ":" + nanos(attributes.lastModifiedTime()) + ":-:"
                + (key == null ? "-" : key.toString().replaceAll("\\s", "_"));
        return new PathStamp(value, Optional.of(attributes.lastModifiedTime().toInstant()));
    }

    private static long nanos(FileTime time) {
        return time.to(TimeUnit.NANOSECONDS);
    }
}
