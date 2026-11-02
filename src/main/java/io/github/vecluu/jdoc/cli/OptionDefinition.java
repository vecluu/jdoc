package io.github.vecluu.jdoc.cli;

import java.util.Optional;

/**
 * One command-line option: a value option ({@code --page <n>}) or a flag ({@code --force}). A new option is also
 * accepted by its commands in {@link JdocCommand} and read into the {@link Invocation} by {@link CommandLineParser}.
 *
 * @param name        the option name including the dashes, e.g. {@code --from}
 * @param valueLabel  the placeholder of the value in help and errors, e.g. {@code <path>}; empty for a flag
 * @param description one line for the help text
 */
record OptionDefinition(String name, Optional<String> valueLabel, String description) {

    /** {@code --from <path>}, shared by every command. */
    static final OptionDefinition FROM = new OptionDefinition("--from", Optional.of("<path>"),
            "Pick the module from this file's location instead of the working directory.");

    /** {@code --page <n>}, shared by every command. */
    static final OptionDefinition PAGE = new OptionDefinition("--page", Optional.of("<n>"),
            "Show page <n> (1 or more) of a capped result.");

    /** {@code --force}, only for {@code index}. */
    static final OptionDefinition FORCE = new OptionDefinition("--force", Optional.empty(),
            "Rebuild the module's artifacts from scratch.");

    /** Whether the option needs a value ({@code --page 2} or {@code --page=2}). */
    boolean takesValue() {
        return valueLabel.isPresent();
    }

    /** The option as written in help: {@code --from <path>} or {@code --force}. */
    String synopsis() {
        return valueLabel.map(label -> name + " " + label).orElse(name);
    }
}
