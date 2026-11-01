package io.github.vecluu.jdoc.cli;

import java.util.Optional;

/** What a valid command line asks for: help, the version, or running a command. */
sealed interface CliRequest {

    /** {@code --help}: the overview, or one command's help. */
    record Help(Optional<JdocCommand> command) implements CliRequest {
    }

    /** {@code --version}. */
    record Version() implements CliRequest {
    }

    /** Run a command with its parsed arguments. */
    record Run(JdocCommand command, Invocation invocation) implements CliRequest {
    }
}
