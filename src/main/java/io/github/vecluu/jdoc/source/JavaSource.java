package io.github.vecluu.jdoc.source;

import io.github.vecluu.jdoc.model.DocSource;

/**
 * One {@code .java} file to parse.
 *
 * @param path      entry path inside its container, e.g. {@code java/util/Map.java}; its file name matters for
 *                  {@code package-info.java} and {@code module-info.java}
 * @param text      the complete file text; all offsets refer to it
 * @param docSource where the text comes from ({@link DocSource#SOURCES}, {@link DocSource#JDK} or
 *                  {@link DocSource#DECOMPILED})
 */
public record JavaSource(String path, String text, DocSource docSource) {
    public JavaSource {
        if (path == null || path.isBlank() || !path.endsWith(".java")) {
            throw new IllegalArgumentException("source path must end with .java: " + path);
        }
        if (text == null) {
            throw new IllegalArgumentException("source text must not be null");
        }
        if (docSource == null || docSource == DocSource.JAVADOC) {
            throw new IllegalArgumentException("not a source-text doc source: " + docSource);
        }
    }

    /** The file name without directories: {@code Map.java}. */
    public String fileName() {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }
}
