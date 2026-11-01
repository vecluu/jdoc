package io.github.vecluu.jdoc.module;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Function;

/** A scripted {@link MavenInvoker}: records commands and answers each with the next queued behaviour. */
final class FakeMavenInvoker implements MavenInvoker {

    private final List<MavenCommand> commands = new ArrayList<>();
    private final Deque<Function<MavenCommand, MavenResult>> behaviours = new ArrayDeque<>();
    private Function<MavenCommand, MavenResult> fallback = command -> failure("no behaviour queued");

    /** Answers the next call by writing {@code content} to the {@code -DoutputFile} and succeeding. */
    FakeMavenInvoker thenWrites(String content) {
        behaviours.add(command -> {
            write(outputFile(command), content);
            return new MavenResult(true, 0, "");
        });
        return this;
    }

    /** Answers the next call with a failed build. */
    FakeMavenInvoker thenFails(String errorLine) {
        behaviours.add(command -> failure(errorLine));
        return this;
    }

    /** Answers the next call with exit code 0 but no output file. */
    FakeMavenInvoker thenSucceedsWithoutOutput() {
        behaviours.add(command -> new MavenResult(true, 0, ""));
        return this;
    }

    /** What to do once the queue is empty. */
    FakeMavenInvoker otherwiseFails() {
        fallback = command -> failure("unexpected mvn call");
        return this;
    }

    @Override
    public MavenResult run(MavenCommand command) {
        commands.add(command);
        Function<MavenCommand, MavenResult> behaviour = behaviours.isEmpty() ? fallback : behaviours.poll();
        return behaviour.apply(command);
    }

    List<MavenCommand> commands() {
        return commands;
    }

    static Path outputFile(MavenCommand command) {
        return command.arguments().stream().filter(arg -> arg.startsWith("-DoutputFile="))
                .map(arg -> Path.of(arg.substring("-DoutputFile=".length())))
                .findFirst().orElseThrow();
    }

    private static MavenResult failure(String errorLine) {
        return new MavenResult(false, 1, "[ERROR] " + errorLine + "\n[ERROR] -> [Help 1]\n");
    }

    private static void write(Path file, String content) {
        try {
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
