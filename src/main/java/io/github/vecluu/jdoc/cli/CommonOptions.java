package io.github.vecluu.jdoc.cli;

import java.nio.file.Path;
import java.util.Optional;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;

/** Options every lookup command shares: {@code --from <path>} and {@code --page <n>}. */
public final class CommonOptions {

    @Spec(Spec.Target.MIXEE)
    private CommandSpec spec;

    private Path from;
    private int page = 1;

    /** Picks the module from a file's location instead of the working directory. */
    @Option(names = "--from", paramLabel = "<path>",
            description = "Pick the module from this file's location instead of the working directory.")
    void setFrom(Path from) {
        this.from = from;
    }

    /** Continues a capped result. */
    @Option(names = "--page", paramLabel = "<n>", description = "Show page <n> of a capped result.")
    void setPage(int page) {
        if (page < 1) {
            throw new ParameterException(spec.commandLine(), "--page must be at least 1, was " + page);
        }
        this.page = page;
    }

    /** The {@code --from} path, if given. */
    public Optional<Path> from() {
        return Optional.ofNullable(from);
    }

    /** The requested page, 1 by default. */
    public int page() {
        return page;
    }
}
