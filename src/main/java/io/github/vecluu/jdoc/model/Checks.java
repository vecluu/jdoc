package io.github.vecluu.jdoc.model;

import java.util.Objects;

/** Argument validation helpers for the compact constructors of model values. */
final class Checks {
    private Checks() {
    }

    static String notBlank(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    static <T> T notNull(T value, String name) {
        return Objects.requireNonNull(value, name + " must not be null");
    }
}
