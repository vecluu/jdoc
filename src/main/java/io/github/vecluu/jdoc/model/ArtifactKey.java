package io.github.vecluu.jdoc.model;

/**
 * Identity of an indexed artifact: a Maven artifact or the JDK of a given feature version.
 *
 * <p>{@link #keyString()} is the unique key stored in the database ({@link Gav#canonical()} or {@code jdk:25});
 * {@link #parse(String)} is its inverse. {@link #label()} is the short text shown to users.
 */
public sealed interface ArtifactKey permits ArtifactKey.Maven, ArtifactKey.Jdk {

    /** Prefix of JDK key strings. */
    String JDK_PREFIX = "jdk:";

    /** Key of a Maven artifact. */
    static ArtifactKey maven(Gav gav) {
        return new Maven(gav);
    }

    /** Key of the JDK with the given feature version, e.g. {@code 25}. */
    static ArtifactKey jdk(int featureVersion) {
        return new Jdk(featureVersion);
    }

    /** Parses a {@link #keyString()}. */
    static ArtifactKey parse(String keyString) {
        Checks.notBlank(keyString, "artifact key");
        if (keyString.startsWith(JDK_PREFIX)) {
            try {
                return jdk(Integer.parseInt(keyString.substring(JDK_PREFIX.length())));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("invalid JDK artifact key: " + keyString, e);
            }
        }
        return maven(Gav.parse(keyString));
    }

    /** Unique key string, {@code g:a:v} (see {@link Gav#canonical()}) or {@code jdk:25}. */
    String keyString();

    /** User-facing label, {@code commons-lang3 3.17.0} or {@code jdk 25}. */
    String label();

    /** True for the JDK key. */
    default boolean isJdk() {
        return this instanceof Jdk;
    }

    /**
     * A Maven artifact.
     *
     * @param gav the artifact's coordinates
     */
    record Maven(Gav gav) implements ArtifactKey {
        public Maven {
            Checks.notNull(gav, "gav");
        }

        @Override
        public String keyString() {
            return gav.canonical();
        }

        @Override
        public String label() {
            return gav.artifactId() + gav.classifier().map(c -> ":" + c).orElse("") + " " + gav.version();
        }
    }

    /**
     * The JDK.
     *
     * @param featureVersion feature release number, e.g. {@code 25}
     */
    record Jdk(int featureVersion) implements ArtifactKey {
        public Jdk {
            if (featureVersion < 1) {
                throw new IllegalArgumentException("invalid JDK feature version: " + featureVersion);
            }
        }

        @Override
        public String keyString() {
            return JDK_PREFIX + featureVersion;
        }

        @Override
        public String label() {
            return "jdk " + featureVersion;
        }
    }
}
