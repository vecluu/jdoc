package io.github.vecluu.jdoc.module;

/** Runs Maven. Implementations must never let Maven's output reach stdout. */
public interface MavenInvoker {

    /** Runs Maven with the given arguments and returns its outcome; never throws for a failed build. */
    MavenResult run(MavenCommand command);
}
