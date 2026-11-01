package io.github.vecluu.jdoc.indexer;

import java.util.List;

/**
 * Produces the types (and package docs) of one artifact in batches, on the calling thread; lets the store write while
 * the producer is still parsing (the JDK) without holding every type in memory.
 */
@FunctionalInterface
public interface EntryProducer {

    /** Passes every type to {@code sink}, in batches of any size, and every package doc found. */
    void produce(EntrySink sink);

    /** A producer of a ready list of types, without package docs. */
    static EntryProducer of(List<TypeEntry> entries) {
        List<TypeEntry> copy = List.copyOf(entries);
        return sink -> sink.types(copy);
    }
}
