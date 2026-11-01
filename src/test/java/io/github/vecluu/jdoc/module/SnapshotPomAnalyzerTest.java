package io.github.vecluu.jdoc.module;

import static io.github.vecluu.jdoc.module.PomFixtures.artifact;
import static io.github.vecluu.jdoc.module.PomFixtures.gav;
import static io.github.vecluu.jdoc.module.PomFixtures.modules;
import static io.github.vecluu.jdoc.module.PomFixtures.parent;
import static io.github.vecluu.jdoc.module.PomFixtures.pom;
import static io.github.vecluu.jdoc.module.PomFixtures.writeFile;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.github.vecluu.jdoc.config.CachePaths;
import io.github.vecluu.jdoc.error.ResolutionException;
import io.github.vecluu.jdoc.model.Gav;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The snapshot fast path must notice every change the parsing analysis would: each test compares with a fresh
 * {@link ParsingPomAnalyzer} (the oracle) and counts how often the snapshot analyzer had to parse.
 */
class SnapshotPomAnalyzerTest {

    @TempDir
    Path tmp;

    private Path root;
    private Path repo;
    private CachePaths cache;
    private int parses;

    @BeforeEach
    void setUp() throws IOException {
        root = tmp.toRealPath().resolve("project");
        repo = tmp.toRealPath().resolve("repo");
        cache = new CachePaths(tmp.toRealPath().resolve("cache"));
    }

    @Test
    void unchangedPomsAreNotParsedAgain() {
        Path app = standaloneApp();
        SnapshotPomAnalyzer analyzer = analyzer(settledClock());

        PomAnalysis first = analyzer.analyze(app);
        PomAnalysis second = analyzer(settledClock()).analyze(app);

        assertThat(parses).isEqualTo(1);
        assertThat(second).isEqualTo(first).isEqualTo(oracle(app));
        assertThat(cache.moduleSnapshotsDirectory()).isDirectoryContaining(path -> path.toString().endsWith(".txt"));
    }

    @Test
    void moduleEditIsDetected() {
        Path app = standaloneApp();
        analyzer(settledClock()).analyze(app);

        pom(app, gav("com.acme", "app", "1.0.1"));

        assertReanalyzed(app);
    }

    @Test
    void sameSizeEditWithRestoredMtimeIsDetected() throws Exception {
        assumeTrue(Files.getFileStore(tmp).supportsFileAttributeView("unix"), "needs ctime");
        Path app = standaloneApp();
        Path file = app.resolve("pom.xml");
        FileTime mtime = Files.getLastModifiedTime(file);
        analyzer(settledClock()).analyze(app);
        Thread.sleep(30);

        String edited = Files.readString(file).replace("<version>1.0</version>", "<version>2.0</version>");
        Files.writeString(file, edited, StandardCharsets.UTF_8);
        Files.setLastModifiedTime(file, mtime);

        assertReanalyzed(app);
    }

    @Test
    void replacedFileWithSameSizeAndMtimeIsDetected() throws Exception {
        Path app = standaloneApp();
        Path file = app.resolve("pom.xml");
        FileTime mtime = Files.getLastModifiedTime(file);
        analyzer(settledClock()).analyze(app);

        Path replacement = writeFile(tmp.resolve("replacement.xml"),
                Files.readString(file).replace("<version>1.0</version>", "<version>2.0</version>"));
        Files.setLastModifiedTime(replacement, mtime);
        Files.move(replacement, file, StandardCopyOption.REPLACE_EXISTING);

        assertReanalyzed(app);
    }

    @Test
    void localParentEditIsDetected() {
        pom(root, gav("com.acme", "root", "1.0"), "<packaging>pom</packaging>");
        Path app = root.resolve("app");
        pom(app, parent("com.acme", "root", "1.0"), artifact("app"));
        analyzer(settledClock()).analyze(app);

        pom(root, gav("com.acme", "root", "1.0"), "<packaging>pom</packaging>", "<properties><x>y</x></properties>");

        assertReanalyzed(app);
    }

    @Test
    void localParentAppearingAtItsRelativePathIsDetected() {
        Path app = root.resolve("app");
        pom(app, parent("com.acme", "root", "1.0"), artifact("app"));
        PomAnalysis before = analyzer(settledClock()).analyze(app);

        pom(root, gav("com.acme", "root", "1.0"), "<packaging>pom</packaging>");

        assertThat(assertReanalyzed(app).key()).isNotEqualTo(before.key());
    }

    @Test
    void touchedSnapshotParentInTheRepositoryIsDetected() throws Exception {
        Gav parentGav = Gav.of("org.acme", "acme-parent", "5-SNAPSHOT");
        Path repoPom = writeFile(repo.resolve(parentGav.pomPath()), "<project/>");
        Files.setLastModifiedTime(repoPom, FileTime.from(Instant.parse("2024-01-01T00:00:00Z")));
        Path app = root.resolve("app");
        pom(app, parent("org.acme", "acme-parent", "5-SNAPSHOT"), artifact("app"));
        PomAnalysis before = analyzer(settledClock()).analyze(app);

        Files.setLastModifiedTime(repoPom, FileTime.from(Instant.parse("2024-02-01T00:00:00Z")));

        assertThat(assertReanalyzed(app).key()).isNotEqualTo(before.key());
    }

    @Test
    void aggregatorAppearingAboveChangesTheReactor() {
        Path app = root.resolve("app");
        pom(app, gav("com.acme", "app", "1.0"));
        PomAnalysis before = analyzer(settledClock()).analyze(app);

        pom(root, gav("com.acme", "root", "1.0"), modules("app", "lib"));
        pom(root.resolve("lib"), gav("com.acme", "lib", "1.0"));

        PomAnalysis after = assertReanalyzed(app);
        assertThat(after.reactor()).isNotEqualTo(before.reactor());
        assertThat(after.reactor().rootDirectory()).isEqualTo(root);
    }

    @Test
    void reactorModuleAddedOrEditedIsDetected() {
        pom(root, gav("com.acme", "root", "1.0"), modules("app", "lib"));
        Path app = root.resolve("app");
        pom(app, parent("com.acme", "root", "1.0"), artifact("app"));
        analyzer(settledClock()).analyze(app);

        pom(root.resolve("lib"), parent("com.acme", "root", "1.0"), artifact("lib"));
        PomAnalysis withLib = assertReanalyzed(app);
        assertThat(withLib.reactor().moduleCoordinates()).contains("com.acme:lib");

        pom(root.resolve("lib"), parent("com.acme", "root", "1.0"), artifact("library"));
        assertThat(assertReanalyzed(app).reactor().moduleCoordinates()).contains("com.acme:library")
                .doesNotContain("com.acme:lib");
    }

    @Test
    void recentlyChangedPomsAreNotStored() {
        Path app = standaloneApp();
        Clock now = Clock.systemUTC();

        analyzer(now).analyze(app);
        analyzer(now).analyze(app);

        assertThat(parses).isEqualTo(2);
        assertThat(cache.moduleSnapshotsDirectory()).doesNotExist();
    }

    @Test
    void snapshotOfAnotherLocalRepositoryIsIgnored() {
        Path app = standaloneApp();
        analyzer(settledClock()).analyze(app);

        Path otherRepo = tmp.resolve("other-repo");
        RecordingPomFiles files = new RecordingPomFiles(new NioPomFiles());
        PomAnalysis analysis = analyzer(files, otherRepo, settledClock()).analyze(app);

        assertThat(parses).isEqualTo(2);
        assertThat(analysis).isEqualTo(oracle(app));
    }

    @Test
    void unreadableModulePomStillFails() {
        Path app = Path.of(root.toString(), "missing");

        assertThatThrownBy(() -> analyzer(settledClock()).analyze(app)).isInstanceOf(ResolutionException.class);
    }

    private PomAnalysis assertReanalyzed(Path app) {
        int before = parses;
        PomAnalysis analysis = analyzer(settledClock()).analyze(app);
        assertThat(parses).as("parsed again").isEqualTo(before + 1);
        assertThat(analysis).isEqualTo(oracle(app));
        assertThat(analyzer(settledClock()).analyze(app)).isEqualTo(analysis);
        assertThat(parses).as("stored again").isEqualTo(before + 1);
        return analysis;
    }

    private Path standaloneApp() {
        Path app = root.resolve("app");
        pom(app, gav("com.acme", "app", "1.0"));
        return app;
    }

    /** A clock far enough ahead that every file written by the test counts as settled. */
    private static Clock settledClock() {
        return Clock.fixed(Instant.now().plus(Duration.ofHours(1)), ZoneOffset.UTC);
    }

    private SnapshotPomAnalyzer analyzer(Clock clock) {
        return analyzer(new RecordingPomFiles(new NioPomFiles()), repo, clock);
    }

    private SnapshotPomAnalyzer analyzer(RecordingPomFiles files, Path repository, Clock clock) {
        PomHierarchy hierarchy = new PomHierarchy(new PomReader(), files);
        PomAnalyzer parsing = new ParsingPomAnalyzer(new PomKeyCalculator(hierarchy, repository),
                new ReactorDetector(hierarchy));
        PomAnalyzer counting = directory -> {
            parses++;
            return parsing.analyze(directory);
        };
        return new SnapshotPomAnalyzer(counting, files, new FileModuleSnapshotStore(cache, new ModuleSnapshotCodec()),
                new NioPathStamper(), repository, clock);
    }

    private PomAnalysis oracle(Path app) {
        PomHierarchy hierarchy = new PomHierarchy(new PomReader());
        return new ParsingPomAnalyzer(new PomKeyCalculator(hierarchy, repo), new ReactorDetector(hierarchy))
                .analyze(app);
    }
}
