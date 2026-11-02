package io.github.vecluu.jdoc.cli;

import io.github.vecluu.jdoc.app.Application;
import io.github.vecluu.jdoc.app.CommandResult;
import java.util.List;
import java.util.Optional;

/**
 * The eight jdoc commands: name, argument, options and help lines, and the {@link Application} call each one makes.
 * Plain data on purpose: building the command table loads no framework and runs no reflection.
 *
 * <p>A new command also needs a line in the training {@code workload} of {@code dist/install.sh} (so the AOT cache
 * covers it), a row in {@code HotPathClassLoadingIT} if it is a lookup, a case in {@code EndToEndIT}, and the docs
 * that list the commands ({@code docs/SPEC.md}, {@code README.md}, {@code dist/skill/SKILL.md},
 * {@code dist/AGENTS-snippet.md}).
 */
enum JdocCommand {

    FIND("find", "<pattern>", "Case-insensitive substring or camel humps (HSR matches HttpServletRequest).",
            "Types and members whose names match: substring or camel humps (HSR)."),
    CLASS("class", "<type>", "FQCN or simple name; nested types as Outer.Inner or Outer$Inner.",
            "Kind, supertypes, first doc paragraph, members with first sentences, inherited member names."),
    METHOD("method", "<type>#<name>[(<params>)]",
            "e.g. java.util.Optional#orElseThrow or 'Map#of(Object,Object)' (simple parameter type names).",
            "Full Javadoc of a method, or of every overload when (<params>) is omitted."),
    SOURCE("source", "<type>#<name>[(<params>)]", "The member, written as for method.",
            "The member's implementation with its Javadoc."),
    IMPLS("impls", "<type>", "FQCN or simple name.", "Known subtypes and implementations on the classpath."),
    MEMBER("member", "<name>", "Member name.",
            "Every type declaring a member with this name, as Type#name(params)."),
    PACKAGE("package", "<pkg>", "Package name, e.g. java.util.", "The package's doc, then its types with first "
            + "sentences."),
    INDEX("index", null, null, "Resolve and index the current module; report types without sources.");

    private static final List<OptionDefinition> SHARED = List.of(OptionDefinition.FROM, OptionDefinition.PAGE);
    private static final List<OptionDefinition> WITH_FORCE =
            List.of(OptionDefinition.FORCE, OptionDefinition.FROM, OptionDefinition.PAGE);

    private final String commandName;
    private final String parameter;
    private final String parameterDescription;
    private final String description;

    JdocCommand(String commandName, String parameter, String parameterDescription, String description) {
        this.commandName = commandName;
        this.parameter = parameter;
        this.parameterDescription = parameterDescription;
        this.description = description;
    }

    /** The command typed on the command line, e.g. {@code class}. */
    static Optional<JdocCommand> named(String name) {
        for (JdocCommand command : values()) {
            if (command.commandName.equals(name)) {
                return Optional.of(command);
            }
        }
        return Optional.empty();
    }

    /** The name typed on the command line. */
    String commandName() {
        return commandName;
    }

    /** The label of the single required argument, e.g. {@code <type>}; empty for {@code index}. */
    Optional<String> parameter() {
        return Optional.ofNullable(parameter);
    }

    /** The help line of the argument; empty for {@code index}. */
    Optional<String> parameterDescription() {
        return Optional.ofNullable(parameterDescription);
    }

    /** One line describing the command. */
    String description() {
        return description;
    }

    /** The options the command accepts, in help order. */
    List<OptionDefinition> options() {
        return this == INDEX ? WITH_FORCE : SHARED;
    }

    /** The option with this name ({@code --from}), if the command accepts it. */
    Optional<OptionDefinition> option(String name) {
        for (OptionDefinition option : options()) {
            if (option.name().equals(name)) {
                return Optional.of(option);
            }
        }
        return Optional.empty();
    }

    /** The command and its argument as shown in the command list, e.g. {@code class <type>}, {@code index [--force]}. */
    String synopsis() {
        return this == INDEX ? commandName + " [" + OptionDefinition.FORCE.name() + "]"
                : commandName + " " + parameter;
    }

    /** Calls the application for a parsed invocation of this command. */
    CommandResult call(Application application, Invocation invocation) {
        String argument = invocation.argument().orElse("");
        return switch (this) {
            case FIND -> application.find(argument, invocation.from());
            case CLASS -> application.classView(argument, invocation.from());
            case METHOD -> application.method(argument, invocation.from());
            case SOURCE -> application.source(argument, invocation.from());
            case IMPLS -> application.impls(argument, invocation.from());
            case MEMBER -> application.member(argument, invocation.from());
            case PACKAGE -> application.packageView(argument, invocation.from());
            case INDEX -> application.index(invocation.force(), invocation.from());
        };
    }
}
