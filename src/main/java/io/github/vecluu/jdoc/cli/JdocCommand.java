package io.github.vecluu.jdoc.cli;

import io.github.vecluu.jdoc.app.Application;
import io.github.vecluu.jdoc.app.CommandResult;
import java.util.concurrent.Callable;
import java.util.function.Function;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ScopeType;
import picocli.CommandLine.Spec;

/**
 * The picocli model of {@code jdoc} and its eight subcommands. Each subcommand only hands its arguments to the
 * {@link Application} through the {@link CommandRunner} and returns the exit code.
 */
@Command(name = "jdoc",
        scope = ScopeType.INHERIT,
        mixinStandardHelpOptions = true,
        versionProvider = ManifestVersionProvider.class,
        usageHelpAutoWidth = true,
        sortOptions = false,
        description = {
                "Signatures and Javadoc of the Java libraries on the current Maven module's classpath and the JDK.",
                "Exit codes: 0 found, 1 not found, 2 ambiguous, 3 error."},
        commandListHeading = "%nCommands:%n")
final class JdocCommand implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    private final CommandRunner runner;

    JdocCommand(CommandRunner runner) {
        this.runner = runner;
    }

    /** {@code jdoc} without a subcommand is a usage error. */
    @Override
    public Integer call() {
        throw new ParameterException(spec.commandLine(), "Missing command, e.g. jdoc class <type> (see jdoc --help)");
    }

    @Command(name = "find", description = "Types and members whose names match: substring or camel humps (HSR).")
    int find(@Mixin CommonOptions options,
             @Parameters(paramLabel = "<pattern>", description = "Name pattern.") String pattern) {
        return run(options, app -> app.find(pattern, options.from()));
    }

    @Command(name = "class", description = "Kind, supertypes, first doc paragraph, members with first sentences, "
            + "inherited member names.")
    int classView(@Mixin CommonOptions options,
                  @Parameters(paramLabel = "<type>", description = "FQCN or simple name (Outer.Inner).")
                  String type) {
        return run(options, app -> app.classView(type, options.from()));
    }

    @Command(name = "method", description = "Full Javadoc of a method, or of every overload without (<params>).")
    int method(@Mixin CommonOptions options,
               @Parameters(paramLabel = "<type>#<name>[(<params>)]",
                       description = "e.g. java.util.Optional#orElseThrow or 'Map#of(Object,Object)'.")
               String reference) {
        return run(options, app -> app.method(reference, options.from()));
    }

    @Command(name = "source", description = "The member's implementation with its Javadoc.")
    int source(@Mixin CommonOptions options,
               @Parameters(paramLabel = "<type>#<name>[(<params>)]", description = "The member.")
               String reference) {
        return run(options, app -> app.source(reference, options.from()));
    }

    @Command(name = "impls", description = "Known subtypes and implementations on the classpath.")
    int impls(@Mixin CommonOptions options,
              @Parameters(paramLabel = "<type>", description = "FQCN or simple name.") String type) {
        return run(options, app -> app.impls(type, options.from()));
    }

    @Command(name = "member", description = "Every type declaring a member with this name, as Type#name(params).")
    int member(@Mixin CommonOptions options,
               @Parameters(paramLabel = "<name>", description = "Member name.") String name) {
        return run(options, app -> app.member(name, options.from()));
    }

    @Command(name = "package", description = "The package's doc, then its types with first sentences.")
    int packageView(@Mixin CommonOptions options,
                    @Parameters(paramLabel = "<pkg>", description = "Package name.") String packageName) {
        return run(options, app -> app.packageView(packageName, options.from()));
    }

    @Command(name = "index", description = "Resolve and index the current module; report types without sources.")
    int index(@Mixin CommonOptions options,
              @Option(names = "--force", description = "Rebuild the module's artifacts from scratch.")
              boolean force) {
        return run(options, app -> app.index(force, options.from()));
    }

    private int run(CommonOptions options, Function<Application, CommandResult> call) {
        return runner.run(options.page(), call);
    }
}
