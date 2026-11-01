package io.github.vecluu.jdoc.cli;

import java.util.Optional;

/**
 * The command line is malformed: unknown command or option, missing or extra argument, invalid option value. Printed
 * as one {@code error: } line pointing at the right {@code --help}, exit code 3.
 */
final class UsageException extends RuntimeException {

    private final transient Optional<JdocCommand> command;

    /**
     * @param message what is wrong, one line
     * @param command the command being parsed, if known (selects the {@code --help} hint)
     */
    UsageException(String message, Optional<JdocCommand> command) {
        super(message);
        this.command = command;
    }

    /** The help command to suggest: {@code jdoc class --help} or {@code jdoc --help}. */
    String helpHint() {
        return command.map(c -> "jdoc " + c.commandName() + " --help").orElse("jdoc --help");
    }
}
