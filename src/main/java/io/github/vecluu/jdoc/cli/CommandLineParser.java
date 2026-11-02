package io.github.vecluu.jdoc.cli;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Parses {@code jdoc <command> [<argument>] [options]} into a {@link CliRequest}.
 *
 * <p>Options may come before or after the argument, as {@code --page 2} or {@code --page=2}; flags take no value
 * ({@code --force}, or explicitly {@code --force=true}); {@code --} ends the options so that an argument may start
 * with a dash. {@code -h}/{@code --help} and {@code -V}/{@code --version} are accepted at the top level and after a
 * command. Anything else is a {@link UsageException} naming the offending argument; the messages keep the wording of
 * the picocli parser this one replaced ({@code Unmatched argument at index ...}, {@code Missing required parameter:
 * ...}), and tests assert it.
 */
final class CommandLineParser {

    private static final String END_OF_OPTIONS = "--";

    /**
     * @param args the command-line arguments
     * @return the request
     * @throws UsageException if the command line is malformed
     */
    CliRequest parse(List<String> args) {
        if (args.isEmpty()) {
            throw new UsageException("Missing command, e.g. jdoc class <type>", Optional.empty());
        }
        String first = args.getFirst();
        if (isHelp(first)) {
            return new CliRequest.Help(Optional.empty());
        }
        if (isVersion(first)) {
            return new CliRequest.Version();
        }
        if (isOptionLike(first)) {
            throw new UsageException("Unknown option: '" + first + "'", Optional.empty());
        }
        JdocCommand command = JdocCommand.named(first).orElseThrow(() -> new UsageException(
                "Unmatched argument at index 0: '" + first + "'", Optional.empty()));
        return parseCommand(command, args);
    }

    private CliRequest parseCommand(JdocCommand command, List<String> args) {
        Optional<JdocCommand> context = Optional.of(command);
        Map<OptionDefinition, String> values = new LinkedHashMap<>();
        String argument = null;
        boolean optionsEnded = false;
        for (int i = 1; i < args.size(); i++) {
            String token = args.get(i);
            if (!optionsEnded && token.equals(END_OF_OPTIONS)) {
                optionsEnded = true;
            } else if (!optionsEnded && isOptionLike(token)) {
                if (isHelp(token)) {
                    return new CliRequest.Help(context);
                }
                if (isVersion(token)) {
                    return new CliRequest.Version();
                }
                i = readOption(command, args, i, values);
            } else if (argument == null && command.parameter().isPresent()) {
                argument = token;
            } else {
                throw new UsageException("Unmatched argument at index " + i + ": '" + token + "'", context);
            }
        }
        if (argument == null && command.parameter().isPresent()) {
            throw new UsageException("Missing required parameter: '" + command.parameter().get() + "'", context);
        }
        return new CliRequest.Run(command, invocation(command, argument, values));
    }

    /** Reads the option at {@code index} into {@code values}; returns the index of the last token it consumed. */
    private int readOption(JdocCommand command, List<String> args, int index, Map<OptionDefinition, String> values) {
        Optional<JdocCommand> context = Optional.of(command);
        String token = args.get(index);
        String name = optionName(token);
        Optional<String> inline = name.equals(token) ? Optional.empty()
                : Optional.of(token.substring(name.length() + 1));
        OptionDefinition option = command.option(name)
                .orElseThrow(() -> new UsageException("Unknown option: '" + name + "'", context));
        if (values.containsKey(option)) {
            throw new UsageException("option '" + option.synopsis() + "' should be specified only once", context);
        }
        if (!option.takesValue()) {
            values.put(option, flagValue(option, inline, context));
            return index;
        }
        if (inline.isPresent()) {
            values.put(option, requireNonEmpty(option, inline.get(), context));
            return index;
        }
        if (index + 1 >= args.size() || isKnownOption(command, args.get(index + 1))) {
            throw missingValue(option, context);
        }
        values.put(option, requireNonEmpty(option, args.get(index + 1), context));
        return index + 1;
    }

    private static String flagValue(OptionDefinition option, Optional<String> inline, Optional<JdocCommand> context) {
        String value = inline.orElse("true");
        if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
            throw new UsageException("Invalid value for option '" + option.name() + "': '" + value
                    + "' is not a boolean", context);
        }
        return value.toLowerCase(Locale.ROOT);
    }

    private static String requireNonEmpty(OptionDefinition option, String value, Optional<JdocCommand> context) {
        if (value.isEmpty()) {
            throw missingValue(option, context);
        }
        return value;
    }

    private static UsageException missingValue(OptionDefinition option, Optional<JdocCommand> context) {
        return new UsageException("Missing required parameter for option '" + option.name() + "' ("
                + option.valueLabel().orElse("") + ")", context);
    }

    private static Invocation invocation(JdocCommand command, String argument,
                                         Map<OptionDefinition, String> values) {
        Optional<JdocCommand> context = Optional.of(command);
        Optional<Path> from = Optional.ofNullable(values.get(OptionDefinition.FROM)).map(value -> path(value, context));
        int page = Optional.ofNullable(values.get(OptionDefinition.PAGE)).map(value -> page(value, context)).orElse(1);
        boolean force = "true".equals(values.get(OptionDefinition.FORCE));
        return new Invocation(Optional.ofNullable(argument), from, page, force);
    }

    private static Path path(String value, Optional<JdocCommand> context) {
        try {
            return Path.of(value);
        } catch (InvalidPathException e) {
            throw new UsageException("Invalid value for option '--from': '" + value + "' is not a path", context);
        }
    }

    private static int page(String value, Optional<JdocCommand> context) {
        int page;
        try {
            page = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new UsageException("Invalid value for option '--page': '" + value + "' is not an int", context);
        }
        if (page < 1) {
            throw new UsageException("--page must be at least 1, was " + page, context);
        }
        return page;
    }

    /** {@code --page} for {@code --page=2}; the token itself otherwise. */
    private static String optionName(String token) {
        int equals = token.indexOf('=');
        return token.startsWith("--") && equals > 2 ? token.substring(0, equals) : token;
    }

    private static boolean isKnownOption(JdocCommand command, String token) {
        String name = optionName(token);
        return isHelp(name) || isVersion(name) || command.option(name).isPresent();
    }

    private static boolean isOptionLike(String token) {
        return token.length() > 1 && token.startsWith("-");
    }

    private static boolean isHelp(String token) {
        return token.equals("-h") || token.equals("--help");
    }

    private static boolean isVersion(String token) {
        return token.equals("-V") || token.equals("--version");
    }
}
