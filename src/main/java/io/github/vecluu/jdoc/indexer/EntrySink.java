package io.github.vecluu.jdoc.indexer;

import io.github.vecluu.jdoc.model.RenderedDoc;
import java.util.List;

/** Receives what an {@link EntryProducer} produces for one artifact; called on the producer's calling thread. */
public interface EntrySink {

    /** A batch of types (any size, possibly empty). */
    void types(List<TypeEntry> batch);

    /** The doc of one package of the artifact ({@code package-info.java} or {@code package-summary.html}). */
    void packageDoc(String packageName, RenderedDoc doc);
}
