package io.github.vecluu.jdoc.cli;

import java.util.ArrayList;
import java.util.List;

/**
 * The hand-written help: {@code jdoc --help} (every command with its argument syntax, the options, the exit codes)
 * and {@code jdoc <command> --help}. One line per entry, no wrapping: the main reader is an agent.
 */
final class HelpText {

    private static final String SUMMARY =
            "Signatures and Javadoc of the Java libraries on the current Maven module's classpath and the JDK.";
    private static final String EXIT_CODES = "Exit codes: 0 found, 1 not found, 2 ambiguous, 3 error.";
    private static final String OPTION_FORMS = "Options go before or after the argument, as --page 2 or --page=2; "
            + "-- ends the options.";

    private HelpText() {
    }

    /** {@code jdoc --help}. */
    static String overview() {
        List<String> lines = new ArrayList<>();
        lines.add("Usage: jdoc <command> [<argument>] [--from <path>] [--page <n>]");
        lines.add(SUMMARY);
        lines.add("");
        lines.add("Commands:");
        List<String[]> commands = new ArrayList<>();
        for (JdocCommand command : JdocCommand.values()) {
            commands.add(new String[] {command.synopsis(), command.description()});
        }
        addTable(lines, commands);
        lines.add("");
        lines.add("Options:");
        addTable(lines, List.of(
                row(OptionDefinition.FROM),
                row(OptionDefinition.PAGE),
                new String[] {OptionDefinition.FORCE.synopsis(),
                        "index only: rebuild the module's artifacts from scratch."},
                new String[] {"-h, --help", "Show this help; jdoc <command> --help for one command."},
                new String[] {"-V, --version", "Print the version."}));
        lines.add(OPTION_FORMS);
        lines.add("");
        lines.add(EXIT_CODES);
        return String.join("\n", lines);
    }

    /** {@code jdoc <command> --help}. */
    static String of(JdocCommand command) {
        StringBuilder usage = new StringBuilder("Usage: jdoc ").append(command.commandName());
        command.parameter().ifPresent(parameter -> usage.append(' ').append(parameter));
        for (OptionDefinition option : command.options()) {
            usage.append(" [").append(option.synopsis()).append(']');
        }
        List<String> lines = new ArrayList<>();
        lines.add(usage.toString());
        lines.add(command.description());
        lines.add("");
        List<String[]> rows = new ArrayList<>();
        command.parameter().ifPresent(parameter ->
                rows.add(new String[] {parameter, command.parameterDescription().orElse("")}));
        command.options().forEach(option -> rows.add(row(option)));
        rows.add(new String[] {"-h, --help", "Show this help."});
        addTable(lines, rows);
        lines.add(OPTION_FORMS);
        lines.add("");
        lines.add(EXIT_CODES);
        return String.join("\n", lines);
    }

    private static String[] row(OptionDefinition option) {
        return new String[] {option.synopsis(), option.description()};
    }

    private static void addTable(List<String> lines, List<String[]> rows) {
        int width = 0;
        for (String[] row : rows) {
            width = Math.max(width, row[0].length());
        }
        for (String[] row : rows) {
            lines.add("  " + row[0] + " ".repeat(width - row[0].length() + 2) + row[1]);
        }
    }
}
