package io.github.vecluu.jdoc.module;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A module's {@link PomAnalysis} together with the stamps of every path the analysis touched. While all paths still
 * have these stamps, the analysis would come out the same, so it can be reused without parsing a POM.
 *
 * @param moduleDirectory the module directory (real path)
 * @param localRepository the local Maven repository the analysis used (SNAPSHOT parents are looked up there)
 * @param analysis        the POM key and reactor
 * @param stamps          stamp per touched path, in first-use order
 */
public record ModuleSnapshot(Path moduleDirectory, Path localRepository, PomAnalysis analysis,
                             Map<Path, PathStamp> stamps) {

    public ModuleSnapshot {
        Objects.requireNonNull(moduleDirectory, "moduleDirectory");
        Objects.requireNonNull(localRepository, "localRepository");
        Objects.requireNonNull(analysis, "analysis");
        stamps = Collections.unmodifiableMap(new LinkedHashMap<>(stamps));
    }
}
