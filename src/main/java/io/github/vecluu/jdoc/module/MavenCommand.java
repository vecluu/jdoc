package io.github.vecluu.jdoc.module;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * One Maven invocation: the arguments after the executable and the directory to run in.
 *
 * @param workingDirectory directory Maven runs in
 * @param arguments        command-line arguments, e.g. {@code -o -q -B dependency:list ...}
 */
public record MavenCommand(Path workingDirectory, List<String> arguments) {
    public MavenCommand {
        Objects.requireNonNull(workingDirectory, "working directory must not be null");
        arguments = List.copyOf(arguments);
    }
}
