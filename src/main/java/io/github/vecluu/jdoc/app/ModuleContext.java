package io.github.vecluu.jdoc.app;

import io.github.vecluu.jdoc.indexer.IndexOptions;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Function;

/**
 * The shared first step of every command: find the module, resolve its classpath, make sure the index is current and
 * build the lookup scope.
 */
public interface ModuleContext {

    /**
     * Resolves and indexes the module.
     *
     * @param from    the {@code --from} path, or empty for the working directory
     * @param options how to index
     * @throws io.github.vecluu.jdoc.error.JdocException if the classpath cannot be resolved at all
     */
    ModuleSession open(Optional<Path> from, IndexOptions options);

    /**
     * Opens a session, runs the work in it, closes it, and adds the session's warnings to the result's stderr.
     */
    default CommandResult run(Optional<Path> from, IndexOptions options, Function<ModuleSession, CommandResult> work) {
        try (ModuleSession session = open(from, options)) {
            return work.apply(session).withWarnings(session.warnings());
        }
    }
}
