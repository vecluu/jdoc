package io.github.vecluu.jdoc.testsupport;

import io.github.vecluu.jdoc.config.JdocEnvironment;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

/**
 * {@link JdocEnvironment}s that keep everything inside a temp directory:
 *
 * <pre>
 * &lt;tmp&gt;/cache            JDOC_CACHE
 * &lt;tmp&gt;/m2/repository    JDOC_M2_REPO
 * &lt;tmp&gt;/bin/mvn          JDOC_MVN (not created; see {@link #installFakeMaven})
 * &lt;tmp&gt;/work             working directory (created)
 * </pre>
 *
 * <p>The Java home is the running JDK, so tests that need {@code src.zip} use the real one (read-only).
 */
public final class TestEnvironments {

    private TestEnvironments() {
    }

    /** An environment rooted in {@code tempDir}; creates the work directory and the repository root. */
    public static JdocEnvironment inTempDir(Path tempDir) {
        Path root = tempDir.toAbsolutePath().normalize();
        try {
            Files.createDirectories(root.resolve("work"));
            Files.createDirectories(root.resolve("m2/repository"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return JdocEnvironment.builder()
                .cacheDirectory(root.resolve("cache"))
                .localRepository(root.resolve("m2/repository"))
                .javaHome(Path.of(System.getProperty("java.home")))
                .mavenExecutable(root.resolve("bin/mvn").toString())
                .workingDirectory(root.resolve("work"))
                .build();
    }

    /** A {@link FakeMavenRepo} on the environment's local repository. */
    public static FakeMavenRepo repository(JdocEnvironment environment) {
        return new FakeMavenRepo(environment.localRepository());
    }

    /**
     * Writes an executable {@code /bin/sh} script at the environment's Maven executable path.
     *
     * @param scriptBody shell commands after the {@code #!/bin/sh} line; Maven's arguments are in {@code "$@"}
     */
    public static Path installFakeMaven(JdocEnvironment environment, String scriptBody) {
        Path script = Path.of(environment.mavenExecutable());
        try {
            Files.createDirectories(script.getParent());
            Files.writeString(script, "#!/bin/sh\n" + scriptBody + "\n", StandardCharsets.UTF_8);
            Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwxr-xr-x"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return script;
    }
}
