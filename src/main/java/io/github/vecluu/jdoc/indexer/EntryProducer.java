package io.github.vecluu.jdoc.indexer;

import java.util.List;
import java.util.function.Consumer;

/**
 * Produces the types of one artifact in batches, on the calling thread; lets the store write while the producer
 * is still parsing (the JDK) without holding every type in memory.
 */
@FunctionalInterface
public interface EntryProducer {

    /** Passes every type to {@code sink}, in batches of any size. */
    void produce(Consumer<List<TypeEntry>> sink);

    /** A producer of a ready list. */
    static EntryProducer of(List<TypeEntry> entries) {
        List<TypeEntry> copy = List.copyOf(entries);
        return sink -> sink.accept(copy);
    }
}
