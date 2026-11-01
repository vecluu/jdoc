package io.github.vecluu.jdoc.model;

import java.nio.file.Path;
import java.util.List;

/**
 * The resolved classpath of one Maven module, in Maven's order.
 *
 * @param moduleDirectory absolute directory containing the module's {@code pom.xml}
 * @param pomKey          lowercase hex SHA-256 POM key (see {@code module} package)
 * @param dependencies    resolved dependencies (reactor modules already removed)
 * @param status          how the list was obtained
 * @param warnings        user-facing warning lines, e.g. a failed Maven run
 */
public record ModuleClasspath(
        Path moduleDirectory,
        String pomKey,
        List<ResolvedDependency> dependencies,
        ResolutionStatus status,
        List<String> warnings) {

    public ModuleClasspath {
        Checks.notNull(moduleDirectory, "module directory");
        Checks.notBlank(pomKey, "pom key");
        Checks.notNull(status, "status");
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
