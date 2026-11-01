package io.github.vecluu.jdoc.module;

import java.nio.file.Path;

/** Takes {@link PathStamp}s: the state of a path that a later call compares to tell whether it changed. */
public interface PathStamper {

    /** The current stamp of {@code path} (following links); never throws for a missing or unreadable path. */
    PathStamp stamp(Path path);
}
