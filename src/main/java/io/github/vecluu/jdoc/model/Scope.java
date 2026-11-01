package io.github.vecluu.jdoc.model;

import java.util.Locale;
import java.util.Optional;

/** Maven dependency scope of a resolved dependency. */
public enum Scope {
    COMPILE, PROVIDED, RUNTIME, TEST, SYSTEM;

    /**
     * Parses the scope as Maven prints it ({@code compile}, {@code test}, ...), ignoring case and surrounding
     * whitespace.
     *
     * @return the scope, or empty for unknown or blank text
     */
    public static Optional<Scope> parse(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(text.strip().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** The scope as Maven writes it, e.g. {@code compile}. */
    public String mavenName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
