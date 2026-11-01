package io.github.vecluu.jdoc.app;

import io.github.vecluu.jdoc.lookup.LocatedType;
import io.github.vecluu.jdoc.lookup.LookupScope;
import io.github.vecluu.jdoc.text.ResultHeader;

/** The first line of every result. */
final class Headers {

    private static final String SEPARATOR = "  ";

    private Headers() {
    }

    /**
     * The type header ({@code java.util.Optional<T>  public final class  (jdk 25)}). {@code [version unresolved]} is
     * added for library types when the module's versions were guessed; the JDK's version is never a guess.
     */
    static String type(LocatedType type, LookupScope scope) {
        boolean unresolved = scope.versionUnresolved() && !type.artifact().key().isJdk();
        return new ResultHeader(type.descriptor(), type.label(), unresolved).line();
    }

    /** A header that is a sentence (lists, not-found messages), tagged when the module's versions were guessed. */
    static String message(String text, LookupScope scope) {
        return scope.versionUnresolved() ? text + SEPARATOR + ResultHeader.VERSION_UNRESOLVED_TAG : text;
    }
}
