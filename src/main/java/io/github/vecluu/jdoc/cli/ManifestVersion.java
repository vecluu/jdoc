package io.github.vecluu.jdoc.cli;

import java.util.Optional;

/** {@code jdoc --version}: the {@code Implementation-Version} of the jar manifest. */
final class ManifestVersion {

    /** Shown when not running from the built jar (e.g. from classes in an IDE). */
    static final String UNKNOWN = "development build";

    private ManifestVersion() {
    }

    /** {@code jdoc 1.2.0}, or {@code jdoc development build} outside the jar. */
    static String text() {
        String version = Optional.ofNullable(ManifestVersion.class.getPackage())
                .map(Package::getImplementationVersion)
                .orElse(UNKNOWN);
        return "jdoc ".concat(version);
    }
}
