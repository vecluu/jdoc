package io.github.vecluu.jdoc.config;

import io.github.vecluu.jdoc.error.ConfigurationException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import lombok.Builder;

/**
 * Immutable view of the environment jdoc runs in. All paths are absolute and normalized.
 *
 * <table>
 *   <caption>Sources and defaults</caption>
 *   <tr><th>Accessor</th><th>Source</th><th>Default</th></tr>
 *   <tr><td>{@link #cacheDirectory()}</td><td>{@code JDOC_CACHE}</td><td>{@code $HOME/.cache/jdoc}</td></tr>
 *   <tr><td>{@link #localRepository()}</td><td>{@code JDOC_M2_REPO}</td><td>{@code $HOME/.m2/repository}</td></tr>
 *   <tr><td>{@link #javaHome()}</td><td>{@code JAVA_HOME}</td><td>{@code java.home} property</td></tr>
 *   <tr><td>{@link #mavenExecutable()}</td><td>{@code JDOC_MVN}</td><td>{@code mvn} (looked up on {@code PATH})</td></tr>
 *   <tr><td>{@link #workingDirectory()}</td><td>{@code user.dir} property</td><td>—</td></tr>
 * </table>
 *
 * <p>Values starting with {@code ~} are expanded against the home directory; relative paths are resolved against
 * the working directory. Blank variables count as unset. Tests build instances with {@link #builder()} or
 * {@link #from(Map, Map)}.
 *
 * @param cacheDirectory   root of jdoc's cache
 * @param localRepository  local Maven repository
 * @param javaHome         JDK whose {@code src.zip} is indexed
 * @param mavenExecutable  Maven command: a bare command name resolved via {@code PATH}, or an absolute path
 * @param workingDirectory directory the process runs in
 * @param localRepositoryExplicit true if {@code JDOC_M2_REPO} was set (Maven is then told to use that repository)
 */
@Builder(toBuilder = true)
public record JdocEnvironment(
        Path cacheDirectory,
        Path localRepository,
        Path javaHome,
        String mavenExecutable,
        Path workingDirectory,
        boolean localRepositoryExplicit) {

    /** Environment variable overriding the cache directory. */
    public static final String ENV_CACHE = "JDOC_CACHE";
    /** Environment variable overriding the local Maven repository. */
    public static final String ENV_M2_REPO = "JDOC_M2_REPO";
    /** Environment variable naming the JDK. */
    public static final String ENV_JAVA_HOME = "JAVA_HOME";
    /** Environment variable overriding the Maven executable. */
    public static final String ENV_MVN = "JDOC_MVN";
    /** Environment variable naming the home directory. */
    public static final String ENV_HOME = "HOME";
    /** Default Maven command. */
    public static final String DEFAULT_MVN = "mvn";

    public JdocEnvironment {
        Objects.requireNonNull(workingDirectory, "working directory must not be null");
        if (!workingDirectory.isAbsolute()) {
            throw new ConfigurationException("working directory must be absolute: " + workingDirectory);
        }
        workingDirectory = workingDirectory.normalize();
        cacheDirectory = absolute(Objects.requireNonNull(cacheDirectory, "cache directory must not be null"),
                workingDirectory);
        localRepository = absolute(Objects.requireNonNull(localRepository, "local repository must not be null"),
                workingDirectory);
        javaHome = absolute(Objects.requireNonNull(javaHome, "java home must not be null"), workingDirectory);
        if (mavenExecutable == null || mavenExecutable.isBlank()) {
            throw new ConfigurationException("maven executable must not be blank");
        }
    }

    /** The environment of the running process ({@link System#getenv()} and system properties). */
    public static JdocEnvironment fromSystem() {
        return from(System.getenv(), toMap(System.getProperties()));
    }

    /**
     * Builds the environment from explicit variables and system properties.
     *
     * @param env        environment variables ({@code JDOC_CACHE}, {@code HOME}, ...)
     * @param properties system properties; {@code user.dir} is required, {@code user.home} and {@code java.home}
     *                   are fallbacks
     * @throws ConfigurationException if the working directory, home or Java home cannot be determined
     */
    public static JdocEnvironment from(Map<String, String> env, Map<String, String> properties) {
        Path workingDirectory = toPath(required(properties.get("user.dir"), "user.dir system property"), "user.dir");
        if (!workingDirectory.isAbsolute()) {
            throw new ConfigurationException("user.dir must be absolute: " + workingDirectory);
        }
        String homeText = firstNonBlank(env.get(ENV_HOME), properties.get("user.home"));
        PathExpander expander = new PathExpander(
                homeText == null ? null : toPath(homeText, ENV_HOME), workingDirectory);

        String cache = env.get(ENV_CACHE);
        String repo = env.get(ENV_M2_REPO);
        String javaHome = firstNonBlank(env.get(ENV_JAVA_HOME), properties.get("java.home"));
        String mvn = env.get(ENV_MVN);
        return new JdocEnvironment(
                isBlank(cache) ? expander.home().resolve(".cache/jdoc") : expander.expand(cache, ENV_CACHE),
                isBlank(repo) ? expander.home().resolve(".m2/repository") : expander.expand(repo, ENV_M2_REPO),
                expander.expand(required(javaHome, "JAVA_HOME or java.home"), ENV_JAVA_HOME),
                isBlank(mvn) ? DEFAULT_MVN : expander.expandCommand(mvn.strip()),
                workingDirectory,
                !isBlank(repo));
    }

    /** The cache path layout for this environment. */
    public CachePaths cachePaths() {
        return new CachePaths(cacheDirectory);
    }

    private static Path absolute(Path path, Path workingDirectory) {
        return workingDirectory.resolve(path).normalize();
    }

    private static Map<String, String> toMap(Properties properties) {
        Map<String, String> map = new HashMap<>();
        for (String name : properties.stringPropertyNames()) {
            map.put(name, properties.getProperty(name));
        }
        return map;
    }

    private static String required(String value, String what) {
        if (isBlank(value)) {
            throw new ConfigurationException("cannot determine " + what);
        }
        return value;
    }

    private static String firstNonBlank(String first, String second) {
        return isBlank(first) ? (isBlank(second) ? null : second) : first;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    static Path toPath(String text, String what) {
        try {
            return Path.of(text.strip());
        } catch (InvalidPathException e) {
            throw new ConfigurationException("invalid path in " + what + ": " + text, e);
        }
    }

    /** Expands {@code ~} and resolves relative paths against the working directory. */
    private record PathExpander(Path homeOrNull, Path workingDirectory) {

        Path home() {
            if (homeOrNull == null) {
                throw new ConfigurationException("cannot determine the home directory (HOME or user.home)");
            }
            return workingDirectory.resolve(homeOrNull).normalize();
        }

        Path expand(String text, String what) {
            String value = text.strip();
            if (value.equals("~")) {
                return home();
            }
            if (value.startsWith("~/")) {
                return home().resolve(value.substring(2)).normalize();
            }
            return workingDirectory.resolve(toPath(value, what)).normalize();
        }

        String expandCommand(String command) {
            if (command.equals("~") || command.startsWith("~/") || command.indexOf('/') >= 0) {
                return expand(command, ENV_MVN).toString();
            }
            return command;
        }
    }
}
