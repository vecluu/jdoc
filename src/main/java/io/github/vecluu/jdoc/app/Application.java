package io.github.vecluu.jdoc.app;

import java.nio.file.Path;
import java.util.Optional;

/** The eight commands of jdoc, as the CLI calls them. Closing releases the index database. */
public interface Application extends AutoCloseable {

    /** {@code jdoc find <pattern>}. */
    CommandResult find(String pattern, Optional<Path> from);

    /** {@code jdoc class <type>}. */
    CommandResult classView(String type, Optional<Path> from);

    /** {@code jdoc method <type>#<name>[(<params>)]}. */
    CommandResult method(String reference, Optional<Path> from);

    /** {@code jdoc source <type>#<name>[(<params>)]}. */
    CommandResult source(String reference, Optional<Path> from);

    /** {@code jdoc impls <type>}. */
    CommandResult impls(String type, Optional<Path> from);

    /** {@code jdoc member <name>}. */
    CommandResult member(String name, Optional<Path> from);

    /** {@code jdoc package <pkg>}. */
    CommandResult packageView(String packageName, Optional<Path> from);

    /** {@code jdoc index [--force]}. */
    CommandResult index(boolean force, Optional<Path> from);

    @Override
    void close();
}
