package io.github.vecluu.jdoc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Licence compliance guard for the shaded {@code jdoc.jar}.
 *
 * <p>Checks that jdoc's own {@code LICENSE}, {@code NOTICE} and {@code THIRD-PARTY-NOTICES} sit under
 * {@code META-INF/} byte for byte, that no dependency licence or notice shadows them, that every jar entry (including
 * the contents of nested jars) belongs to an allow-listed component with a {@code THIRD-PARTY-NOTICES} section, and
 * that every resolved runtime {@code groupId:artifactId:version} is named there. A new dependency, a version bump or
 * new jar content therefore fails the build until the notices are reviewed.
 *
 * <p>The build passes the paths through the failsafe system properties {@code jdoc.projectRoot},
 * {@code jdoc.shadedJar} and {@code jdoc.runtimeDependencies}; without them (an IDE run) the test is skipped.
 */
class LicenseNoticesIT {

    /** Marks jdoc's own content: covered by {@code LICENSE}, no third-party section needed. */
    private static final String JDOC = "";

    private static final List<String> ROOT_FILES = List.of("LICENSE", "NOTICE", "THIRD-PARTY-NOTICES");

    /** Path prefix (or exact entry) in the shaded jar → section id in THIRD-PARTY-NOTICES. Longest prefix wins. */
    private static final Map<String, String> OWNERS = Map.ofEntries(
            Map.entry("io/github/vecluu/jdoc/", JDOC),
            Map.entry("META-INF/MANIFEST.MF", JDOC),
            Map.entry("META-INF/LICENSE", JDOC),
            Map.entry("META-INF/NOTICE", JDOC),
            Map.entry("META-INF/THIRD-PARTY-NOTICES", JDOC),
            Map.entry("META-INF/services/", JDOC),
            Map.entry("META-INF/maven/io.github.vecluu/jdoc/", JDOC),
            Map.entry("org/sqlite/", "sqlite-jdbc"),
            Map.entry("sqlite-jdbc.properties", "sqlite-jdbc"),
            Map.entry("META-INF/maven/org.xerial/sqlite-jdbc/", "sqlite-jdbc"),
            Map.entry("META-INF/native-image/org.xerial/sqlite-jdbc/", "sqlite-jdbc"),
            Map.entry("org/jsoup/", "jsoup"),
            Map.entry("META-INF/jsoup/", "jsoup"),
            Map.entry("META-INF/maven/org.jsoup/jsoup/", "jsoup"),
            Map.entry("META-INF/proguard/org.jsoup_jsoup.pro", "jsoup"),
            Map.entry("org/objectweb/asm/", "asm"),
            Map.entry("org/jetbrains/java/decompiler/", "vineflower"),
            Map.entry("net/fabricmc/fernflower/", "vineflower"),
            Map.entry("META-INF/plugins/vineflower-idea-not-null.jar", "vineflower"),
            Map.entry("META-INF/plugins/vineflower-variable-renaming.jar", "vineflower"),
            Map.entry("META-INF/plugins/vineflower-kotlin-0.1.0.jar", "vineflower"),
            Map.entry("org/slf4j/", "slf4j-api"),
            Map.entry("META-INF/maven/org.slf4j/slf4j-api/", "slf4j-api"),
            Map.entry("org/slf4j/simple/", "slf4j-simple"),
            Map.entry("META-INF/maven/org.slf4j/slf4j-simple/", "slf4j-simple"));

    /** Path prefix inside a nested Vineflower plugin jar → section id. Longest prefix wins. */
    private static final Map<String, String> NESTED_OWNERS = Map.ofEntries(
            Map.entry("META-INF/MANIFEST.MF", "vineflower"),
            Map.entry("META-INF/services/", "vineflower"),
            Map.entry("org/vineflower/ideanotnull/", "vineflower"),
            Map.entry("org/vineflower/variablerenaming/", "vineflower"),
            Map.entry("org/vineflower/kotlin/", "vineflower"),
            Map.entry("org/vineflower/kt/", "kotlin-metadata"),
            Map.entry("org/vineflower/kt/protobuf/", "kotlin-protobuf"),
            Map.entry("org/jetbrains/annotations/", "jetbrains-annotations"),
            Map.entry("org/intellij/lang/annotations/", "jetbrains-annotations"),
            Map.entry("META-INF/versions/9/module-info.class", "jetbrains-annotations"));

    private static final Pattern MULTI_RELEASE = Pattern.compile("^META-INF/versions/\\d+/");

    private static final Pattern LICENCE_LIKE = Pattern.compile("^META-INF/[^/]*(LICEN[CS]E|NOTICE|COPYING)[^/]*$");

    /** One {@code dependency:list} line: {@code g:a:type[:classifier]:version:scope}. */
    private static final Pattern DEPENDENCY = Pattern.compile(
            "^\\s*([^:\\s]+):([^:\\s]+):[^:\\s]+(?::[^:\\s]+)?:([^:\\s]+):(compile|runtime)\\b.*$");

    private static Path projectRoot;
    private static Path shadedJar;
    private static Path runtimeDependencies;
    private static String notices;
    private static Set<String> sections;

    @BeforeAll
    static void locateBuildOutputs() throws IOException {
        Optional<Path> root = property("jdoc.projectRoot");
        assumeTrue(root.isPresent(), "not run by the Maven build: jdoc.projectRoot is not set");
        projectRoot = root.get();
        shadedJar = property("jdoc.shadedJar").orElseThrow();
        runtimeDependencies = property("jdoc.runtimeDependencies").orElseThrow();
        assertThat(shadedJar).as("shaded jar (run the package phase first)").isRegularFile();
        assertThat(runtimeDependencies).as("runtime dependency list (run the package phase first)").isRegularFile();
        notices = Files.readString(projectRoot.resolve("THIRD-PARTY-NOTICES"), StandardCharsets.UTF_8);
        sections = new TreeSet<>();
        Matcher heading = Pattern.compile("(?m)^\\[([a-z0-9-]+)] ").matcher(notices);
        while (heading.find()) {
            sections.add(heading.group(1));
        }
    }

    @Test
    void jarCarriesTheRootLicenceFilesUnchanged() throws IOException {
        try (ZipFile jar = new ZipFile(shadedJar.toFile())) {
            for (String name : ROOT_FILES) {
                ZipEntry entry = jar.getEntry("META-INF/" + name);
                assertThat(entry).as("META-INF/%s in %s", name, shadedJar).isNotNull();
                try (InputStream in = jar.getInputStream(entry)) {
                    assertThat(in.readAllBytes()).as("META-INF/%s equals the root %s", name, name)
                            .isEqualTo(Files.readAllBytes(projectRoot.resolve(name)));
                }
            }
        }
    }

    @Test
    void noOtherLicenceOrNoticeAtTheMetaInfRoot() throws IOException {
        List<String> foreign = entryNames(shadedJar).stream()
                .filter(name -> LICENCE_LIKE.matcher(name.toUpperCase(Locale.ROOT)).matches())
                .filter(name -> !ROOT_FILES.contains(name.substring("META-INF/".length())))
                .toList();

        assertThat(foreign).as("dependency licence/notice files at the META-INF/ root; merge them into the root files")
                .isEmpty();
    }

    @Test
    void everyJarEntryBelongsToAComponentWithANoticeSection() throws IOException {
        Set<String> unowned = new TreeSet<>();
        Set<String> owners = new TreeSet<>();
        try (ZipFile jar = new ZipFile(shadedJar.toFile())) {
            for (ZipEntry entry : jar.stream().filter(entry -> !entry.isDirectory()).toList()) {
                String name = MULTI_RELEASE.matcher(entry.getName()).replaceFirst("");
                Optional<String> owner = owner(OWNERS, name);
                owner.ifPresentOrElse(owners::add, () -> unowned.add(summary(entry.getName())));
                if (name.endsWith(".jar")) {
                    try (InputStream in = jar.getInputStream(entry)) {
                        checkNestedJar(entry.getName(), in.readAllBytes(), owners, unowned);
                    }
                }
            }
        }

        assertThat(unowned).as("jar content without an allow-listed owner in LicenseNoticesIT; review "
                + "THIRD-PARTY-NOTICES and extend the allow-list").isEmpty();
        owners.remove(JDOC);
        assertThat(sections).as("THIRD-PARTY-NOTICES sections").containsAll(owners);
    }

    @Test
    void everyRuntimeDependencyIsNamedInTheNotices() throws IOException {
        List<String> coordinates = runtimeCoordinates();

        assertThat(coordinates).as("parsed from %s", runtimeDependencies).isNotEmpty();
        assertThat(coordinates).as("runtime groupId:artifactId:version missing from THIRD-PARTY-NOTICES; review the "
                + "entry after adding or bumping a dependency").allSatisfy(gav -> assertThat(notices).contains(gav));
    }

    private static void checkNestedJar(String jarName, byte[] bytes, Collection<String> owners,
            Collection<String> unowned) throws IOException {
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                Optional<String> owner = name.endsWith(".jar") ? Optional.empty() : owner(NESTED_OWNERS, name);
                owner.ifPresentOrElse(owners::add, () -> unowned.add(jarName + "!" + summary(name)));
            }
        }
    }

    private static Optional<String> owner(Map<String, String> owners, String name) {
        return owners.entrySet().stream()
                .filter(entry -> entry.getKey().endsWith("/") ? name.startsWith(entry.getKey())
                        : name.equals(entry.getKey()))
                .max((a, b) -> Integer.compare(a.getKey().length(), b.getKey().length()))
                .map(Map.Entry::getValue);
    }

    /** Shortens an entry to its first three path segments so a report lists packages, not thousands of classes. */
    private static String summary(String name) {
        String[] parts = name.split("/");
        return parts.length <= 3 ? name : String.join("/", parts[0], parts[1], parts[2]) + "/**";
    }

    private static List<String> runtimeCoordinates() throws IOException {
        List<String> coordinates = new ArrayList<>();
        for (String line : Files.readAllLines(runtimeDependencies, StandardCharsets.UTF_8)) {
            Matcher matcher = DEPENDENCY.matcher(line);
            if (matcher.matches()) {
                coordinates.add(matcher.group(1) + ":" + matcher.group(2) + ":" + matcher.group(3));
            }
        }
        return coordinates;
    }

    private static List<String> entryNames(Path jar) throws IOException {
        try (ZipFile zip = new ZipFile(jar.toFile()); Stream<? extends ZipEntry> entries = zip.stream()) {
            return entries.map(ZipEntry::getName).toList();
        }
    }

    private static Optional<Path> property(String key) {
        return Optional.ofNullable(System.getProperty(key)).filter(value -> !value.isBlank()).map(Path::of);
    }
}
