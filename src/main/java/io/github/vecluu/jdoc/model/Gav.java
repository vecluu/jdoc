package io.github.vecluu.jdoc.model;

import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Maven coordinates of one artifact.
 *
 * <p>{@link #toString()} is {@code g:a:v}; {@link #canonical()} is the unique, parseable form that also carries
 * type and classifier when they differ from a plain jar. Repository paths follow the Maven 2 layout.
 *
 * @param groupId    group id, e.g. {@code org.apache.commons}
 * @param artifactId artifact id, e.g. {@code commons-lang3}
 * @param version    version, possibly a timestamped snapshot ({@code 1.0-20240101.120000-3})
 * @param classifier optional classifier, e.g. {@code linux-x86_64}; never {@code null}
 * @param type       packaging type, {@code jar} by default
 */
public record Gav(String groupId, String artifactId, String version, Optional<String> classifier, String type) {

    /** The default artifact type. */
    public static final String JAR = "jar";

    private static final String SNAPSHOT = "SNAPSHOT";
    private static final Pattern TIMESTAMPED = Pattern.compile("^(.*)-(\\d{8}\\.\\d{6})-(\\d+)$");
    private static final Set<String> JAR_TYPES = Set.of(JAR, "test-jar", "maven-plugin", "ejb", "bundle", "ejb-client");

    public Gav {
        Checks.notBlank(groupId, "groupId");
        Checks.notBlank(artifactId, "artifactId");
        Checks.notBlank(version, "version");
        classifier = classifier == null ? Optional.empty() : classifier.filter(c -> !c.isBlank());
        type = type == null || type.isBlank() ? JAR : type;
        checkNoColon(groupId, "groupId");
        checkNoColon(artifactId, "artifactId");
        checkNoColon(version, "version");
        classifier.ifPresent(c -> checkNoColon(c, "classifier"));
        checkNoColon(type, "type");
    }

    /** A plain jar artifact without classifier. */
    public static Gav of(String groupId, String artifactId, String version) {
        return new Gav(groupId, artifactId, version, Optional.empty(), JAR);
    }

    /**
     * Parses {@code g:a:v}, {@code g:a:type:v} or {@code g:a:type:classifier:v} (the inverse of {@link #canonical()}).
     *
     * @throws IllegalArgumentException for any other shape
     */
    public static Gav parse(String text) {
        Checks.notBlank(text, "coordinates");
        String[] parts = text.strip().split(":", -1);
        return switch (parts.length) {
            case 3 -> new Gav(parts[0], parts[1], parts[2], Optional.empty(), JAR);
            case 4 -> new Gav(parts[0], parts[1], parts[3], Optional.empty(), parts[2]);
            case 5 -> new Gav(parts[0], parts[1], parts[4], Optional.of(parts[3]), parts[2]);
            default -> throw new IllegalArgumentException("not Maven coordinates: " + text);
        };
    }

    /** True for {@code -SNAPSHOT} versions and timestamped snapshot versions. */
    public boolean isSnapshot() {
        return version.endsWith("-" + SNAPSHOT) || version.equals(SNAPSHOT) || TIMESTAMPED.matcher(version).matches();
    }

    /** The version directory name: a timestamped snapshot maps back to {@code X-SNAPSHOT}. */
    public String baseVersion() {
        Matcher matcher = TIMESTAMPED.matcher(version);
        return matcher.matches() ? matcher.group(1) + "-" + SNAPSHOT : version;
    }

    /** {@code groupId:artifactId}, used to match reactor modules regardless of version. */
    public String coordinates() {
        return groupId + ":" + artifactId;
    }

    /**
     * Unique parseable form: {@code g:a:v} for plain jars, otherwise {@code g:a:type:v} or
     * {@code g:a:type:classifier:v}.
     */
    public String canonical() {
        if (classifier.isPresent()) {
            return groupId + ":" + artifactId + ":" + type + ":" + classifier.get() + ":" + version;
        }
        if (!JAR.equals(type)) {
            return groupId + ":" + artifactId + ":" + type + ":" + version;
        }
        return toString();
    }

    /** Same coordinates with another classifier (e.g. {@code sources}). */
    public Gav withClassifier(String newClassifier) {
        return new Gav(groupId, artifactId, version, Optional.ofNullable(newClassifier), type);
    }

    /** The group id as a path, {@code org/apache/commons}. */
    public String groupPath() {
        return groupId.replace('.', '/');
    }

    /** Repository-relative version directory, {@code org/apache/commons/commons-lang3/3.17.0}. */
    public Path repositoryDirectory() {
        return Path.of(groupPath(), artifactId, baseVersion());
    }

    /** File extension for this artifact's type ({@code jar} for all jar-like types). */
    public String extension() {
        return JAR_TYPES.contains(type) ? JAR : type;
    }

    /** File name of this artifact, {@code commons-lang3-3.17.0[-classifier].jar}. */
    public String fileName() {
        return artifactId + "-" + version + classifier.map(c -> "-" + c).orElse("") + "." + extension();
    }

    /** Repository-relative path of this artifact's file. */
    public Path repositoryPath() {
        return repositoryDirectory().resolve(fileName());
    }

    /** Repository-relative path of the artifact's POM. */
    public Path pomPath() {
        return repositoryDirectory().resolve(artifactId + "-" + version + ".pom");
    }

    /** {@code g:a:v}. */
    @Override
    public String toString() {
        return groupId + ":" + artifactId + ":" + version;
    }

    private static void checkNoColon(String value, String name) {
        if (value.indexOf(':') >= 0 || value.indexOf('/') >= 0 || value.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException(name + " contains an illegal character: '" + value + "'");
        }
    }
}
