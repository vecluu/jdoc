package io.github.vecluu.jdoc.indexer;

import io.github.vecluu.jdoc.javadoc.JavadocPageSource;

/** An opened javadoc jar: its pages, plus closing. */
public interface JavadocArchive extends JavadocPageSource, AutoCloseable {

    @Override
    void close();
}
