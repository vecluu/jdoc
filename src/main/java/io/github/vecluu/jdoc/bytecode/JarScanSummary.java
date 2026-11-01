package io.github.vecluu.jdoc.bytecode;

import java.util.List;

/**
 * Outcome of {@link BinaryJarScanner#scan}.
 *
 * @param typeCount            named types passed to the consumer
 * @param skippedEntries       class entries skipped as unreadable (corrupt or unsupported class files)
 * @param packageInfoPackages  packages (dotted, {@code ""} for the default package) with a {@code package-info.class},
 *                             sorted
 */
public record JarScanSummary(int typeCount, int skippedEntries, List<String> packageInfoPackages) {
    public JarScanSummary {
        packageInfoPackages = packageInfoPackages == null ? List.of() : List.copyOf(packageInfoPackages);
    }
}
