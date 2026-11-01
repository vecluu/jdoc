package io.github.vecluu.jdoc.indexer;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** In-memory {@link SourceArchive}. */
final class MapSourceArchive implements SourceArchive {

    private final Map<String, String> files = new HashMap<>();
    private boolean closed;

    MapSourceArchive with(String path, String text) {
        files.put(path, text);
        return this;
    }

    @Override
    public Optional<String> read(String entryPath) {
        return Optional.ofNullable(files.get(entryPath));
    }

    @Override
    public void close() {
        closed = true;
    }

    boolean closed() {
        return closed;
    }
}
