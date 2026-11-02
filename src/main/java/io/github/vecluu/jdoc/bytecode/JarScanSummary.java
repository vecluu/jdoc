package io.github.vecluu.jdoc.bytecode;

/**
 * Outcome of {@link BinaryJarScanner#scan}.
 *
 * @param typeCount named types passed to the consumer
 */
public record JarScanSummary(int typeCount) {
}
