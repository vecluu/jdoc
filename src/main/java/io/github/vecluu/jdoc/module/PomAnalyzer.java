package io.github.vecluu.jdoc.module;

import java.nio.file.Path;

/** Computes the {@link PomAnalysis} of a module. */
public interface PomAnalyzer {

    /**
     * Analyzes the module whose {@code pom.xml} is in {@code moduleDirectory}.
     *
     * @throws io.github.vecluu.jdoc.error.ResolutionException if the module POM cannot be read at all
     */
    PomAnalysis analyze(Path moduleDirectory);
}
