package io.github.vecluu.jdoc.cli;

import java.util.Optional;
import picocli.CommandLine.IVersionProvider;

/** {@code jdoc --version}: the {@code Implementation-Version} of the jar manifest. */
public final class ManifestVersionProvider implements IVersionProvider {

    /** Shown when not running from the built jar (e.g. from classes in an IDE). */
    static final String UNKNOWN = "development build";

    @Override
    public String[] getVersion() {
        String version = Optional.ofNullable(ManifestVersionProvider.class.getPackage())
                .map(Package::getImplementationVersion)
                .orElse(UNKNOWN);
        return new String[] {"jdoc " + version};
    }
}
