package io.github.vecluu.jdoc.module;

import java.util.Objects;

/**
 * A previously resolved {@code deps.txt} together with its meta.
 *
 * @param meta    where and when it was resolved
 * @param content the raw {@code deps.txt} text
 */
public record CachedDependencyList(ModuleMeta meta, String content) {
    public CachedDependencyList {
        Objects.requireNonNull(meta, "meta must not be null");
        Objects.requireNonNull(content, "content must not be null");
    }
}
