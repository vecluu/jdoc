package io.github.vecluu.jdoc.module;

import java.nio.file.Path;
import java.util.Optional;

/** Stores one {@link ModuleSnapshot} per module directory. Storage problems never fail a call: they cost a parse. */
public interface ModuleSnapshotStore {

    /** The stored snapshot of a module directory, if there is a readable one. */
    Optional<ModuleSnapshot> read(Path moduleDirectory);

    /** Stores (replaces) the snapshot of its module directory. */
    void write(ModuleSnapshot snapshot);
}
