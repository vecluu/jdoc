package io.github.vecluu.jdoc.indexer;

import io.github.vecluu.jdoc.model.DocSource;
import io.github.vecluu.jdoc.model.TypeName;

/**
 * An API type whose docs do not come from source.
 *
 * @param name      the type
 * @param docSource {@link DocSource#JAVADOC} or {@link DocSource#DECOMPILED}
 */
public record TypeWithoutSources(TypeName name, DocSource docSource) {
}
