package io.github.vecluu.jdoc.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.github.vecluu.jdoc.model.Gav;
import io.github.vecluu.jdoc.model.ResolvedDependency;
import io.github.vecluu.jdoc.testsupport.FakeMavenRepo;
import io.github.vecluu.jdoc.testsupport.Prerequisites;
import io.github.vecluu.jdoc.testsupport.SampleLibrary;
import io.github.vecluu.jdoc.testsupport.TestLibrary;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Installs the release bundle layout ({@code dist/install.sh}, {@code dist/bin/jdoc}, the fat jar) into a temp prefix
 * and runs the installed launcher: AOT cache used while its fingerprint matches (also for the first call after a
 * library is added, whose indexing and decompiling classes come from the cache because the training covered a
 * library), silent fallback to the per-user CDS archive after the jar changes, and no sqlite library extracted to the
 * temp dir per call. Linux only. Missing tools skip the tests locally and fail them in CI (see {@link Prerequisites}).
 */
@Tag("slow")
class InstallerIT {

    private static final String METHOD = "java.util.Optional#orElseThrow";
    private static final String CDS = "source: shared objects file";
    /** The repository's {@code dist/}: the release bundle's files. */
    private static final Path DIST = Path.of(System.getProperty("user.dir"), "dist");

    private final Prerequisites prerequisites = Prerequisites.fromEnvironment();

    @TempDir
    Path tmp;

    private Path prefix;
    private Path bindir;
    private Path module;
    private Path javaTmp;
    private Path cache;

    @BeforeEach
    void setUp() throws IOException {
        prefix = tmp.resolve("pre fix");
        bindir = tmp.resolve("bin");
        module = Files.createDirectories(tmp.resolve("module"));
        javaTmp = Files.createDirectories(tmp.resolve("java-tmp"));
        cache = tmp.resolve("cache");
        Files.writeString(module.resolve("pom.xml"), """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>it</groupId>
                  <artifactId>it</artifactId>
                  <version>1</version>
                </project>
                """);
        Path mvn = tmp.resolve("fake-mvn");
        Files.writeString(mvn, """
                #!/bin/sh
                for a in "$@"; do case "$a" in -DoutputFile=*) out="${a#-DoutputFile=}";; esac; done
                if [ -n "$out" ]; then
                  if [ -f deps.list ]; then cp deps.list "$out"; else : > "$out"; fi
                fi
                exit 0
                """);
        assertThat(mvn.toFile().setExecutable(true)).isTrue();
    }

    @Test
    void installedLauncherUsesTheAotCacheAndFallsBackSilentlyWhenTheJarChanges() throws Exception {
        assumeTrue(System.getProperty("os.name").startsWith("Linux"), "Linux only");
        prerequisites.require(onPath("bash") && onPath("stat") && onPath("od"), "bash, stat and od needed");
        Path bundle = bundle();

        Run install = run(List.of("bash", bundle.resolve("install.sh").toString(), "--prefix", prefix.toString(),
                "--bindir", bindir.toString(), "--skills-dir", tmp.resolve("skills").toString()), Map.of());
        assertThat(install.code).as(install.toString()).isZero();
        assertThat(install.out).as(install.toString()).contains("trained on the JDK and the training library");
        assertThat(install.err).as("no installer warning").isEmpty();
        Path lib = prefix.resolve("lib");
        assertThat(lib.resolve("jdoc.jar")).isRegularFile();
        assertThat(lib.resolve("native/libsqlitejdbc.so")).isRegularFile();
        assertThat(lib.resolve("jdoc.aot")).isRegularFile();
        assertThat(Files.readString(lib.resolve("jdoc.aot.key"))).contains("aot_stat='").contains("aot_flags='");
        assertThat(tmp.resolve("skills/jdoc/SKILL.md")).isRegularFile();
        assertThat(prefix.resolve("VERSION")).hasContent("0.0.0-it");
        try (var leftovers = Files.list(tmp.resolve("install-tmp"))) {
            assertThat(leftovers).as("installer temp dir removed").isEmpty();
        }

        Run version = jdoc(Map.of(), "--version");
        assertThat(version.code).isZero();
        assertThat(version.out).startsWith("jdoc ");
        assertThat(version.err).isEmpty();

        // -XX:AOTMode=on makes the JVM fail unless it really maps the cache.
        Run aot = jdoc(Map.of("JDOC_JAVA_OPTS", "-XX:AOTMode=on -Djava.io.tmpdir=" + javaTmp,
                "JDOC_WRAPPER_DEBUG", "1"), "method", METHOD);
        assertThat(aot.code).as(aot.toString()).isZero();
        assertThat(aot.out).contains("orElseThrow");
        assertThat(aot.err).startsWith("jdoc wrapper: aot cache ").doesNotContain("warning").doesNotContain("error");
        Run quiet = jdoc(Map.of("JDOC_JAVA_OPTS", "-Djava.io.tmpdir=" + javaTmp), "method", METHOD);
        assertThat(quiet.code).isZero();
        assertThat(quiet.err).isEmpty();
        assertThat(quiet.out).isEqualTo(aot.out);
        try (var files = Files.list(javaTmp)) {
            assertThat(files).as("nothing extracted to java.io.tmpdir").isEmpty();
        }
        assertThat(jdoc(Map.of(), "class", "does.not.Exist").code).isEqualTo(1);
        assertThat(jdoc(Map.of(), "bogus").code).isEqualTo(3);

        firstCallsAfterAddingLibrariesUseTheCache();

        Path jar = lib.resolve("jdoc.jar");
        Files.setLastModifiedTime(jar, FileTime.from(Files.getLastModifiedTime(jar).toInstant()
                .plus(Duration.ofHours(1))));
        Run debug = jdoc(Map.of("JDOC_WRAPPER_DEBUG", "1"), "method", METHOD);
        assertThat(debug.err).startsWith("jdoc wrapper: fallback archive " + cache.resolve("cds/jdoc.jsa"));
        assertThat(cache.resolve("cds/jdoc.jsa")).isRegularFile();
        List<CompletableFuture<Run>> parallel = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            parallel.add(CompletableFuture.supplyAsync(() -> jdocUnchecked("method", METHOD)));
        }
        for (CompletableFuture<Run> future : parallel) {
            Run fallback = future.get(2, TimeUnit.MINUTES);
            assertThat(fallback.code).as(fallback.toString()).isZero();
            assertThat(fallback.err).as("no JVM cache warnings").isEmpty();
            assertThat(fallback.out).isEqualTo(aot.out);
        }

        Files.write(cache.resolve("cds/jdoc.jsa"), new byte[0]);
        Run healed = jdoc(Map.of(), "--version");
        assertThat(healed.err).isEmpty();
        assertThat(Files.size(cache.resolve("cds/jdoc.jsa"))).as("broken archive rebuilt").isPositive();
    }

    /**
     * Without {@code --prefix} and {@code --bindir} the installer needs no root: it installs under {@code $HOME}
     * ({@code ~/.local/share/jdoc}, {@code ~/.local/bin}) and says so when the bindir is not on PATH.
     */
    @Test
    void installerDefaultsToTheUsersHomeAndHintsWhenTheBindirIsNotOnPath() throws Exception {
        assumeTrue(System.getProperty("os.name").startsWith("Linux"), "Linux only");
        prerequisites.require(onPath("bash"), "bash needed");
        Path bundle = bundle();
        Path home = Files.createDirectories(tmp.resolve("ho me"));

        Run install = run(List.of("bash", bundle.resolve("install.sh").toString(), "--no-aot"),
                Map.of("HOME", home.toString()));

        assertThat(install.code).as(install.toString()).isZero();
        assertThat(install.err).as(install.toString()).isEmpty();
        Path bin = home.resolve(".local/bin");
        assertThat(home.resolve(".local/share/jdoc/lib/jdoc.jar")).isRegularFile();
        assertThat(bin.resolve("jdoc")).isExecutable();
        assertThat(install.out).as(install.toString())
                .contains("note: " + bin + " is not on PATH; add it there to call jdoc by name");
        Run version = run(List.of(bin.resolve("jdoc").toString(), "--version"), Map.of());
        assertThat(version.code).as(version.toString()).isZero();
        assertThat(version.out).startsWith("jdoc ");

        String path = String.join(File.pathSeparator, bin.toString(),
                Path.of(System.getProperty("java.home"), "bin").toString(), System.getenv("PATH"));
        Run onPath = run(List.of("bash", bundle.resolve("install.sh").toString(), "--no-aot"),
                Map.of("HOME", home.toString(), "PATH", path));
        assertThat(onPath.code).as(onPath.toString()).isZero();
        assertThat(onPath.out).as(onPath.toString()).doesNotContain("not on PATH");
    }

    /** {@code --help} prints the usage with every option and exits 0; an unknown option prints it on stderr. */
    @Test
    void helpListsEveryOption() throws Exception {
        prerequisites.require(onPath("bash"), "bash needed");
        String script = DIST.resolve("install.sh").toString();

        Run help = run(List.of("bash", script, "--help"), Map.of());
        assertThat(help.code).as(help.toString()).isZero();
        assertThat(help.err).as(help.toString()).isEmpty();
        assertThat(help.out).as(help.toString()).startsWith("Usage: install.sh ")
                .contains("--prefix DIR", "--bindir DIR", "--skills-dir DIR", "--no-aot");

        Run unknown = run(List.of("bash", script, "--bogus"), Map.of());
        assertThat(unknown.code).isEqualTo(1);
        assertThat(unknown.err).startsWith("Usage: install.sh ").contains("--no-aot")
                .endsWith("install.sh: unknown option: --bogus\n");
    }

    /**
     * A training-library generator that fails costs only the library part of the training: the installer prints
     * exactly one warning line with the generator's error line, trains on the JDK and exits 0.
     */
    @Test
    void installerWarnsWithTheReasonAndTrainsOnTheJdkWhenTheTrainingLibraryCannotBeGenerated() throws Exception {
        assumeTrue(System.getProperty("os.name").startsWith("Linux"), "Linux only");
        prerequisites.require(onPath("bash") && onPath("stat"), "bash and stat needed");
        Run install = installWithFakeJava("""
                if [ "$a" = %s ]; then
                  echo 'error: simulated generator failure' >&2
                  echo 'a second line that the warning leaves out' >&2
                  exit 3
                fi""".formatted(TrainingLibrary.class.getName()));

        assertThat(install.code).as(install.toString()).isZero();
        assertThat(install.out).as(install.toString()).contains("trained on the JDK)")
                .doesNotContain("training library").doesNotContain("retrying");
        assertThat(install.err.lines().toList()).as(install.toString()).containsExactly(
                "install.sh: warning: could not generate the training library (error: simulated generator failure);"
                        + " the AOT cache covers JDK lookups only");
        assertThat(prefix.resolve("lib/jdoc.aot")).isRegularFile();
        assertThat(prefix.resolve("lib/jdoc.aot.key")).isRegularFile();
    }

    /**
     * A JVM crash while creating the cache (a rare HotSpot SIGSEGV in JDK 25, similar to JDK-8366948; see the
     * comment at the retry in {@code dist/install.sh}) is retried once with a fresh recording without training data;
     * the installer says so on stdout, and the cache is complete.
     */
    @Test
    void installerRetriesTheTrainingOnceWhenTheJvmCrashesCreatingTheCache() throws Exception {
        assumeTrue(System.getProperty("os.name").startsWith("Linux"), "Linux only");
        prerequisites.require(onPath("bash") && onPath("stat"), "bash and stat needed");
        Path crashed = tmp.resolve("crashed-once");
        Run install = installWithFakeJava("""
                if [ "$a" = -XX:AOTMode=create ] && [ ! -e '%s' ]; then
                  : > '%s'
                  printf '#\\n# A fatal error has been detected by the Java Runtime Environment:\\n#\\n'
                  printf '#  SIGSEGV (0xb) at pc=0x0000000000000001, pid=1, tid=1\\n#\\n'
                  exit 134
                fi""".formatted(crashed, crashed));

        assertThat(install.code).as(install.toString()).isZero();
        assertThat(crashed).as("the simulated crash happened").exists();
        assertThat(install.out).as(install.toString())
                .contains("install.sh: AOT training failed (SIGSEGV (0xb) at pc=0x0000000000000001, pid=1, tid=1);"
                        + " retrying once without method profiles")
                .contains("trained on the JDK and the training library");
        assertThat(install.err).as("a successful retry is no warning").isEmpty();
        assertThat(prefix.resolve("lib/jdoc.aot")).isRegularFile();
        // The fingerprint names the fake JDK, so the launcher would fall back; the real java maps the cache directly.
        // The launcher's log flags keep harmless AOT code cache warnings (seen on CI runners) off stdout.
        Path lib = prefix.resolve("lib");
        Run aot = run(List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Xlog:disable", "-Xlog:all=warning:stderr", "-Xlog:aot*=off,cds*=off:stderr",
                "-XX:TieredStopAtLevel=1", "-XX:+UseSerialGC", "-XX:-UsePerfData", "-XX:AOTMode=on",
                "-XX:AOTCache=" + lib.resolve("jdoc.aot"), "-jar", lib.resolve("jdoc.jar").toString(), "--version"),
                Map.of());
        assertThat(aot.code).as(aot.toString()).isZero();
        assertThat(aot.out).startsWith("jdoc ");
    }

    /**
     * Installs the bundle with a fake JDK first on PATH (see {@link #fakeJdk}) whose {@code java} runs
     * {@code failure} for every argument {@code $a} before it runs the real {@code java}.
     */
    private Run installWithFakeJava(String failure) throws Exception {
        Path bundle = bundle();
        Path fakeHome = tmp.resolve("fake jdk");
        try {
            Path fakeBin = fakeJdk(fakeHome, failure);
            return run(List.of("bash", bundle.resolve("install.sh").toString(), "--prefix", prefix.toString(),
                    "--bindir", bindir.toString()), Map.of("PATH", fakeBin + File.pathSeparator + System.getenv("PATH")));
        } finally {
            deleteLinks(fakeHome);
        }
    }

    /**
     * A JDK home whose {@code bin/java} is a script that can fail on purpose and otherwise runs the real {@code java};
     * the other top-level entries and {@code bin/jar} link to the real JDK, so the installer's JDK checks and
     * training work.
     *
     * @param failure sh code run for every argument {@code $a}, may exit
     * @return the fake home's {@code bin} directory
     */
    private static Path fakeJdk(Path home, String failure) throws IOException {
        Path realHome = Path.of(System.getProperty("java.home")).toRealPath();
        Path bin = Files.createDirectories(home.resolve("bin"));
        try (var entries = Files.list(realHome)) {
            for (Path entry : entries.filter(entry -> !entry.getFileName().toString().equals("bin")).toList()) {
                Files.createSymbolicLink(home.resolve(entry.getFileName().toString()), entry);
            }
        }
        Files.createSymbolicLink(bin.resolve("jar"), realHome.resolve("bin/jar"));
        Path java = bin.resolve("java");
        Files.writeString(java, "#!/bin/sh\nfor a in \"$@\"; do\n" + failure + "\ndone\nexec '"
                + realHome.resolve("bin/java") + "' \"$@\"\n");
        assertThat(java.toFile().setExecutable(true)).isTrue();
        return bin;
    }

    /** Removes the fake JDK's links before {@code @TempDir} cleanup, which warns about links leaving the temp dir. */
    private static void deleteLinks(Path home) throws IOException {
        if (!Files.isDirectory(home)) {
            return;
        }
        try (var paths = Files.walk(home)) {
            for (Path link : paths.filter(Files::isSymbolicLink).toList()) {
                Files.delete(link);
            }
        }
    }

    /**
     * Adds two libraries the training never saw (javadoc jar only, binary only) to the module. The first call indexes
     * them; its javadoc-page and jar-scanning classes and, for {@code source}, the decompiler come from the cache.
     */
    private void firstCallsAfterAddingLibrariesUseTheCache() throws IOException, InterruptedException {
        FakeMavenRepo repo = new FakeMavenRepo(tmp.resolve("m2"));
        ResolvedDependency javadocOnly = repo.install(SampleLibrary.builder().withSources(false).build());
        TestLibrary tool = TestLibrary.builder(Gav.of("com.example.fresh", "fresh-tool", "2.0"))
                .source("com/example/fresh/Tool.java", """
                        package com.example.fresh;

                        public final class Tool {
                            public int twice(int value) {
                                return value * 2;
                            }
                        }
                        """)
                .withSources(false).withJavadoc(false).build();
        ResolvedDependency binaryOnly = repo.install(tool);
        Files.writeString(module.resolve("deps.list"), FakeMavenRepo.depsTxt(List.of(javadocOnly, binaryOnly)));
        Files.writeString(module.resolve("pom.xml"), """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>it</groupId>
                  <artifactId>it</artifactId>
                  <version>1</version>
                  <dependencies>
                    <dependency><groupId>com.example</groupId><artifactId>sample-lib</artifactId><version>1.0.0</version></dependency>
                    <dependency><groupId>com.example.fresh</groupId><artifactId>fresh-tool</artifactId><version>2.0</version></dependency>
                  </dependencies>
                </project>
                """);

        Path indexLog = tmp.resolve("class-load-index.log");
        Run added = jdoc(Map.of("JDOC_JAVA_OPTS", "-XX:AOTMode=on -Xlog:class+load:file=" + indexLog,
                "JDOC_WRAPPER_DEBUG", "1"), "class", SampleLibrary.CIRCLE.binaryName());
        assertThat(added.code).as(added.toString()).isZero();
        assertThat(added.out).contains("(sample-lib 1.0.0)  [from javadoc jar]");
        assertThat(added.err).startsWith("jdoc wrapper: aot cache ").doesNotContain("warning").doesNotContain("error");
        assertThat(Files.readString(indexLog)).as("library indexing classes from the AOT cache")
                .contains("io.github.vecluu.jdoc.bytecode.ZipBinaryJarScanner " + CDS)
                .contains("io.github.vecluu.jdoc.javadoc.JavadocPageParser " + CDS);

        Path decompileLog = tmp.resolve("class-load-decompile.log");
        Run decompiled = jdoc(Map.of("JDOC_JAVA_OPTS", "-Xlog:class+load:file=" + decompileLog),
                "source", "com.example.fresh.Tool#twice");
        assertThat(decompiled.code).as(decompiled.toString()).isZero();
        assertThat(decompiled.out).contains("[decompiled, no docs]").contains("return value * 2;");
        assertThat(decompiled.err).isEmpty();
        assertThat(Files.readString(decompileLog)).as("decompiler classes from the AOT cache")
                .contains("org.jetbrains.java.decompiler.main.Fernflower " + CDS);
    }

    /** Every shipped or CI shell script: {@code dist/install.sh}, {@code dist/bin/jdoc} and {@code scripts/*.sh}. */
    @Test
    void scriptsAreShellcheckClean() throws Exception {
        prerequisites.require(onPath("shellcheck"), "shellcheck not installed");
        Path root = Path.of(System.getProperty("user.dir"));
        List<String> command = new ArrayList<>(List.of("shellcheck", root.resolve("dist/install.sh").toString(),
                root.resolve("dist/bin/jdoc").toString()));
        try (var scripts = Files.list(root.resolve("scripts"))) {
            scripts.filter(script -> script.getFileName().toString().endsWith(".sh")).sorted()
                    .map(Path::toString).forEach(command::add);
        }
        assertThat(command).as("scripts/*.sh found").hasSizeGreaterThan(3);
        Run check = run(command, Map.of());
        assertThat(check.code).as(check.toString()).isZero();
    }

    private Path bundle() throws IOException, URISyntaxException {
        Path jar = fatJar();
        prerequisites.require(Files.isRegularFile(jar), "fat jar not built: " + jar);
        prerequisites.require(Files.isRegularFile(DIST.resolve("install.sh")), "dist/install.sh not found");
        Path bundle = Files.createDirectories(tmp.resolve("bundle"));
        Files.createDirectories(bundle.resolve("bin"));
        Files.createDirectories(bundle.resolve("lib"));
        Files.createDirectories(bundle.resolve("skill"));
        Files.copy(DIST.resolve("install.sh"), bundle.resolve("install.sh"));
        Files.copy(DIST.resolve("bin/jdoc"), bundle.resolve("bin/jdoc"));
        Files.copy(jar, bundle.resolve("lib/jdoc.jar"));
        Files.writeString(bundle.resolve("skill/SKILL.md"), "skill\n");
        Files.writeString(bundle.resolve("VERSION"), "0.0.0-it\n");
        return bundle;
    }

    private Run jdocUnchecked(String... args) {
        try {
            return jdoc(Map.of(), args);
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    private Run jdoc(Map<String, String> env, String... args) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(bindir.resolve("jdoc").toString());
        command.addAll(Arrays.asList(args));
        return run(command, env);
    }

    private Run run(List<String> command, Map<String, String> env) throws IOException, InterruptedException {
        Path stdout = Files.createTempFile(tmp, "out", ".txt");
        Path stderr = Files.createTempFile(tmp, "err", ".txt");
        ProcessBuilder builder = new ProcessBuilder(command).directory(module.toFile())
                .redirectOutput(stdout.toFile()).redirectError(stderr.toFile());
        Map<String, String> environment = builder.environment();
        Path javaBin = Path.of(System.getProperty("java.home"), "bin");
        environment.put("PATH", javaBin + File.pathSeparator + environment.getOrDefault("PATH", ""));
        environment.put("JAVA_HOME", System.getProperty("java.home"));
        environment.put("JDOC_CACHE", cache.toString());
        environment.put("JDOC_M2_REPO", tmp.resolve("m2").toString());
        environment.put("JDOC_MVN", tmp.resolve("fake-mvn").toString());
        environment.put("TMPDIR", Files.createDirectories(tmp.resolve("install-tmp")).toString());
        List.of("JDOC_JAVA_OPTS", "JDOC_WRAPPER_DEBUG", "JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS")
                .forEach(environment::remove);
        environment.putAll(env);
        Process process = builder.start();
        process.getOutputStream().close();
        assertThat(process.waitFor(5, TimeUnit.MINUTES)).as("finished: " + command).isTrue();
        return new Run(process.exitValue(), Files.readString(stdout, StandardCharsets.UTF_8),
                Files.readString(stderr, StandardCharsets.UTF_8));
    }

    private static boolean onPath(String command) {
        String path = System.getenv("PATH");
        return path != null && Arrays.stream(path.split(File.pathSeparator))
                .anyMatch(directory -> Files.isExecutable(Path.of(directory, command)));
    }

    private static Path fatJar() throws URISyntaxException {
        Path testClasses = Path.of(InstallerIT.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        return testClasses.getParent().resolve("jdoc.jar");
    }

    private record Run(int code, String out, String err) {
        @Override
        public String toString() {
            return "exit " + code + "\nstdout:\n" + out + "\nstderr:\n" + err;
        }
    }
}
