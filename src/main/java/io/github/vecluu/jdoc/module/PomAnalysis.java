package io.github.vecluu.jdoc.module;

import java.util.Objects;

/**
 * What jdoc derives from a module's POMs before it looks at the cached classpath.
 *
 * @param key     the POM key (cache key of the resolved classpath)
 * @param reactor the project the module belongs to
 */
public record PomAnalysis(PomKey key, Reactor reactor) {

    public PomAnalysis {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(reactor, "reactor");
    }
}
