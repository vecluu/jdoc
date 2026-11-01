package io.github.vecluu.jdoc.cli;

import java.nio.file.Path;
import java.util.Optional;

/**
 * The parsed arguments of one command.
 *
 * @param argument the command's argument (empty for {@code index})
 * @param from     the {@code --from} path, if given
 * @param page     the requested page, 1 by default
 * @param force    whether {@code --force} was given
 */
record Invocation(Optional<String> argument, Optional<Path> from, int page, boolean force) {
}
