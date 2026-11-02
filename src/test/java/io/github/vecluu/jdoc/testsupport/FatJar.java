package io.github.vecluu.jdoc.testsupport;

import java.net.URISyntaxException;
import java.nio.file.Path;

/**
 * The shaded {@code jdoc.jar} that {@code package} builds before the ITs run. ITs that start it as a real JVM (or
 * install it) take its path from here and check that it exists with {@link Prerequisites} or an assumption.
 */
public final class FatJar {

    private FatJar() {
    }

    /**
     * The failsafe system property {@code jdoc.shadedJar} when the Maven build runs the test, else {@code jdoc.jar}
     * next to the test classes directory (the build directory, also under {@code -Djdoc.buildDir}). The file may not
     * exist.
     */
    public static Path path() {
        String property = System.getProperty("jdoc.shadedJar");
        if (property != null) {
            return Path.of(property);
        }
        try {
            Path testClasses = Path.of(FatJar.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return testClasses.getParent().resolve("jdoc.jar");
        } catch (URISyntaxException e) {
            throw new IllegalStateException("test classes location is not a path", e);
        }
    }
}
