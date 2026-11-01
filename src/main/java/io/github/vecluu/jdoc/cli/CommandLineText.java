package io.github.vecluu.jdoc.cli;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Rebuilds the command line the user typed, for the pagination footer ({@code next: jdoc class X --page 2}), quoting
 * arguments so the line can be pasted into a shell ({@code 'Optional#of(T)'}).
 */
final class CommandLineText {

    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9_./:=@%+,#-]+");

    private CommandLineText() {
    }

    /** {@code jdoc} followed by the arguments, each quoted if the shell would otherwise change it. */
    static String of(List<String> arguments) {
        return arguments.stream().map(CommandLineText::quote)
                .collect(Collectors.joining(" ", arguments.isEmpty() ? "jdoc" : "jdoc ", ""));
    }

    static String quote(String argument) {
        if (!argument.isEmpty() && SAFE.matcher(argument).matches() && !argument.startsWith("#")) {
            return argument;
        }
        return "'" + argument.replace("'", "'\\''") + "'";
    }
}
