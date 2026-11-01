package io.github.vecluu.jdoc.app;

import java.nio.file.Path;
import java.util.Optional;

/** A command answering one argument ({@code <type>}, {@code <pattern>}, ...) from the current module. */
@FunctionalInterface
public interface LookupCommand {

    /**
     * Answers the argument.
     *
     * @param argument the command's argument as typed
     * @param from     the {@code --from} path, or empty for the working directory
     */
    CommandResult run(String argument, Optional<Path> from);
}
