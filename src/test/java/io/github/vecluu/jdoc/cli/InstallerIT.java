package io.github.vecluu.jdoc.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.github.vecluu.jdoc.model.Gav;
import io.github.vecluu.jdoc.model.ResolvedDependency;
import io.github.vecluu.jdoc.testsupport.FakeMavenRepo;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Installs the release bundle layout ({@code dist/install.sh}, {@code dist/bin/jdoc}, the fat jar) into a temp prefix
 * and runs the installed launcher: AOT cache used while its fingerprint matches (also for the first call after a
 * library is added, whose indexing and decompiling classes come from the cache because the training covered a
 * library), silent fallback to the per-user CDS archive after the jar changes, and no sqlite library extracted to the
 * temp dir per call. Linux only.
 */
class InstallerIT {

    private static final String METHOD = "java.util.Optional#orElseThrow";
    private static final String CDS = "source: shared objects file";

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
        assumeTrue(onPath("bash") && onPath("stat") && onPath("od"), "bash, stat and od needed");
        Path bundle = bundle();

        Run install = run(List.of("bash", bundle.resolve("install.sh").toString(), "--prefix", prefix.toString(),
                "--bindir", bindir.toString(), "--skills-dir", tmp.resolve("skills").toString()), Map.of());
        assertThat(install.code).as(install.toString()).isZero();
        assertThat(install.out).contains("trained on the JDK and the training library");
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

    @Test
    void scriptsAreShellcheckClean() throws Exception {
        assumeTrue(onPath("shellcheck"), "shellcheck not installed");
        Path dist = Path.of(System.getProperty("user.dir"), "dist");
        Run check = run(List.of("shellcheck", dist.resolve("install.sh").toString(),
                dist.resolve("bin/jdoc").toString()), Map.of());
        assertThat(check.code).as(check.toString()).isZero();
    }

    private Path bundle() throws IOException, URISyntaxException {
        Path dist = Path.of(System.getProperty("user.dir"), "dist");
        Path jar = fatJar();
        assumeTrue(Files.isRegularFile(jar), "fat jar not built: " + jar);
        assumeTrue(Files.isRegularFile(dist.resolve("install.sh")), "dist/install.sh not found");
        Path bundle = Files.createDirectories(tmp.resolve("bundle"));
        Files.createDirectories(bundle.resolve("bin"));
        Files.createDirectories(bundle.resolve("lib"));
        Files.createDirectories(bundle.resolve("skill"));
        Files.copy(dist.resolve("install.sh"), bundle.resolve("install.sh"));
        Files.copy(dist.resolve("bin/jdoc"), bundle.resolve("bin/jdoc"));
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
