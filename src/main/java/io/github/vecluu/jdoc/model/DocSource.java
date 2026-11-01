package io.github.vecluu.jdoc.model;

import java.util.Optional;

/** Where a type's documentation and signatures come from (chosen per type). */
public enum DocSource {
    /** The artifact's {@code -sources.jar}. */
    SOURCES(null),
    /** The artifact's {@code -javadoc.jar} HTML; signatures from the binary jar. */
    JAVADOC("[from javadoc jar]"),
    /** Vineflower output of the binary class; no docs. */
    DECOMPILED("[decompiled, no docs]"),
    /** The JDK's {@code lib/src.zip}. */
    JDK(null);

    private final String headerTag;

    DocSource(String headerTag) {
        this.headerTag = headerTag;
    }

    /** The tag appended to a result header line, if this source has one. */
    public Optional<String> headerTag() {
        return Optional.ofNullable(headerTag);
    }

    /** True for sources parsed from {@code .java} text (sources jar or JDK {@code src.zip}). */
    public boolean isSourceText() {
        return this == SOURCES || this == JDK;
    }
}
