package io.github.vecluu.jdoc.module;

import java.nio.file.Path;
import lombok.RequiredArgsConstructor;

/** The {@link PomAnalyzer} that parses the POMs: {@link PomKeyCalculator} plus {@link ReactorDetector}. */
@RequiredArgsConstructor
public final class ParsingPomAnalyzer implements PomAnalyzer {

    private final PomKeyCalculator keyCalculator;
    private final ReactorDetector reactorDetector;

    @Override
    public PomAnalysis analyze(Path moduleDirectory) {
        PomKey key = keyCalculator.calculate(moduleDirectory.resolve(ModuleLocator.POM_FILE));
        return new PomAnalysis(key, reactorDetector.detect(moduleDirectory));
    }
}
