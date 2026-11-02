package io.github.vecluu.jdoc.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.github.vecluu.jdoc.config.JdocEnvironment;
import io.github.vecluu.jdoc.model.ResolvedDependency;
import io.github.vecluu.jdoc.model.Scope;
import io.github.vecluu.jdoc.testsupport.FakeMavenRepo;
import io.github.vecluu.jdoc.testsupport.SampleLibrary;
import io.github.vecluu.jdoc.testsupport.TestEnvironments;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The lookup hot path answers from SQLite alone: each command runs in a child JVM on the fat jar with
 * {@code -Xlog:class+load}, against an index built once (real JDK {@code src.zip} plus the mixed sample library with
 * sources, javadoc-only and decompiled types), and must not load javac ({@code com.sun.source},
 * {@code com.sun.tools.javac}), jsoup, ASM or Vineflower. {@code jdoc source} of a member with source text cuts it by
 * the stored offsets, so it loads none of them either. The control is {@code jdoc source} of a decompiled type: it
 * decompiles and parses, and therefore does load javac.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("slow")
class HotPathClassLoadingIT {

    private static final List<String> FORBIDDEN = List.of("com.sun.source.", "com.sun.tools.javac.", "org.jsoup.",
            "org.objectweb.asm.", "org.jetbrains.java.decompiler.");

    private Path root;
    private JdocEnvironment env;
    private Path module;
    private Path jar;

    @BeforeAll
    void indexOnce(@TempDir Path tmp) throws Exception {
        root = tmp;
        jar = fatJar();
        assumeTrue(Files.isRegularFile(jar), "fat jar not built: " + jar);
        env = TestEnvironments.inTempDir(root);
        FakeMavenRepo repo = TestEnvironments.repository(env);
        ResolvedDependency library = repo.install(SampleLibrary.mixed(), Scope.COMPILE);
        module = Files.createDirectories(root.resolve("projects/app"));
        Files.writeString(module.resolve("pom.xml"), "<project><modelVersion>4.0.0</modelVersion><groupId>t</groupId>"
                + "<artifactId>app</artifactId><version>1</version></project>\n");
        Path deps = Files.writeString(root.resolve("deps.txt"), FakeMavenRepo.depsTxt(List.of(library)));
        TestEnvironments.installFakeMaven(env, """
                for a in "$@"; do case "$a" in -DoutputFile=*) out="${a#-DoutputFile=}";; esac; done
                cp '%s' "$out"
                """.formatted(deps));

        Run index = run("index", "index");
        assertThat(index.code()).as(index.err()).isZero();
        assertThat(index.out()).contains("jdk 25");
    }

    static Stream<Arguments> lookups() {
        return Stream.of(
                Arguments.of("method", "java.util.Optional#orElseThrow"),
                Arguments.of("method", "java.util.ArrayList#add"),
                Arguments.of("method", SampleLibrary.CIRCLE.displayName() + "#area"),
                Arguments.of("method", SampleLibrary.GENERATED_MODEL.displayName() + "#withId"),
                Arguments.of("method", SampleLibrary.KOTLIN_UTILS.displayName() + "#twice"),
                Arguments.of("class", "java.util.Optional"),
                Arguments.of("class", SampleLibrary.GENERATED_MODEL.displayName()),
                Arguments.of("find", "oET"),
                Arguments.of("member", "orElseThrow"),
                Arguments.of("impls", SampleLibrary.SHAPE.displayName()),
                Arguments.of("package", "java.util"),
                Arguments.of("package", SampleLibrary.PACKAGE),
                Arguments.of("source", "java.util.Optional#orElseThrow"),
                Arguments.of("source", SampleLibrary.CIRCLE.displayName() + "#area"));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("lookups")
    void lookupsLoadNoParserJsoupAsmOrDecompilerClasses(String command, String argument) throws Exception {
        Run run = run(command + "-" + argument.replaceAll("[^A-Za-z0-9]", "_"), command, argument);

        assertThat(run.code()).as(run.out() + run.err()).isIn(0, 1);
        assertThat(run.out()).isNotBlank();
        assertThat(run.loadedClasses()).as("classes loaded by jdoc %s %s", command, argument)
                .noneMatch(HotPathClassLoadingIT::forbidden)
                .anyMatch(name -> name.startsWith("io.github.vecluu.jdoc.lookup."));
    }

    @Test
    void sourceOfDecompiledTypeStillParsesWithJavac() throws Exception {
        Run run = run("source-decompiled", "source", SampleLibrary.KOTLIN_UTILS.displayName() + "#twice");

        assertThat(run.code()).as(run.err()).isZero();
        assertThat(run.out()).contains("twice");
        assertThat(run.loadedClasses()).anyMatch(name -> name.startsWith("com.sun.source."));
    }

    private static boolean forbidden(String className) {
        return FORBIDDEN.stream().anyMatch(className::startsWith);
    }

    private record Run(int code, String out, String err, List<String> loadedClasses) {
    }

    private Run run(String label, String... args) throws IOException, InterruptedException {
        Path log = root.resolve("class-load-" + label + ".log");
        List<String> command = new ArrayList<>(List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Xlog:class+load=info:file=" + log, "-jar", jar.toString()));
        command.addAll(List.of(args));
        ProcessBuilder builder = new ProcessBuilder(command).directory(module.toFile());
        builder.environment().put("JDOC_CACHE", env.cacheDirectory().toString());
        builder.environment().put("JDOC_MVN", env.mavenExecutable());
        builder.environment().put("JDOC_M2_REPO", env.localRepository().toString());
        builder.environment().put("JAVA_HOME", System.getProperty("java.home"));
        Process process = builder.start();
        process.getOutputStream().close();
        byte[] out = process.getInputStream().readAllBytes();
        byte[] err = process.getErrorStream().readAllBytes();
        if (!process.waitFor(5, TimeUnit.MINUTES)) {
            process.destroyForcibly();
            throw new AssertionError("jdoc did not finish");
        }
        return new Run(process.exitValue(), new String(out, StandardCharsets.UTF_8),
                new String(err, StandardCharsets.UTF_8), loadedClasses(log));
    }

    /** Class names from {@code -Xlog:class+load} lines: {@code [...][info][class,load] java.lang.Object source: ...}. */
    private static List<String> loadedClasses(Path log) throws IOException {
        return Files.readAllLines(log).stream()
                .filter(line -> line.contains("[class,load]"))
                .map(line -> line.substring(line.indexOf("[class,load]") + "[class,load]".length()).strip())
                .map(rest -> rest.split(" ", 2)[0])
                .toList();
    }

    private static Path fatJar() throws URISyntaxException {
        Path testClasses = Path.of(HotPathClassLoadingIT.class.getProtectionDomain().getCodeSource().getLocation()
                .toURI());
        return testClasses.getParent().resolve("jdoc.jar");
    }
}
