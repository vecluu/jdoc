package io.github.vecluu.jdoc.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.github.vecluu.jdoc.config.JdocEnvironment;
import io.github.vecluu.jdoc.model.Gav;
import io.github.vecluu.jdoc.model.ResolvedDependency;
import io.github.vecluu.jdoc.model.Scope;
import io.github.vecluu.jdoc.testsupport.FakeMavenRepo;
import io.github.vecluu.jdoc.testsupport.JarWriter;
import io.github.vecluu.jdoc.testsupport.SampleLibrary;
import io.github.vecluu.jdoc.testsupport.TestEnvironments;
import io.github.vecluu.jdoc.testsupport.TestLibrary;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * jdoc end to end with real components and no network: a temp cache ({@code JDOC_CACHE}), a fake local Maven
 * repository with T01's fixture jars, temp Maven modules and a fake {@code mvn} script ({@code JDOC_MVN}) that copies a
 * prepared {@code deps.txt} (or fails when a marker file exists). The real JDK's {@code src.zip} is indexed once.
 */
class EndToEndIT {

    private static final Gav SHADOW = Gav.of("com.evil", "shadow", "1.0");
    private static final Gav MUTABLE = Gav.of("com.acme", "mutable", "1.0");

    @TempDir
    static Path root;
    static JdocEnvironment env;
    static FakeMavenRepo repo;
    static Path control;
    static Path app;
    static Path other;
    static Run firstIndex;

    record Run(int code, String out, String err) {
        List<String> lines() {
            return out.lines().toList();
        }
    }

    @BeforeAll
    static void setUp() throws IOException {
        env = TestEnvironments.inTempDir(root);
        repo = TestEnvironments.repository(env);
        control = Files.createDirectories(root.resolve("control"));
        ResolvedDependency sampleLib = repo.install(SampleLibrary.mixed(), Scope.COMPILE);
        ResolvedDependency shadow = installShadowJar();
        ResolvedDependency mutable = repo.install(mutableLibrary("first"), Scope.TEST);

        app = module("app", "com.example", "sample-lib", sampleLib, shadow, mutable);
        other = module("other", null, null);
        TestEnvironments.installFakeMaven(env, """
                ctl='%s'
                m=$(basename "$(pwd)")
                echo "$m $*" >> "$ctl/calls.log"
                if [ -f "$ctl/$m.fail" ]; then echo "[ERROR] offline: cannot resolve"; exit 1; fi
                for a in "$@"; do case "$a" in -DoutputFile=*) out="${a#-DoutputFile=}";; esac; done
                cp "$ctl/$m.deps" "$out"
                """.formatted(control));
        firstIndex = jdoc(app, "index");
    }

    /** A module directory with a POM (optionally declaring one dependency) and the deps.txt the fake mvn returns. */
    static Path module(String name, String declaredGroup, String declaredArtifact, ResolvedDependency... deps)
            throws IOException {
        Path dir = Files.createDirectories(root.resolve("projects").resolve(name));
        String dependencies = declaredGroup == null ? "" : "<dependencies><dependency><groupId>" + declaredGroup
                + "</groupId><artifactId>" + declaredArtifact + "</artifactId><version>1.0.0</version>"
                + "</dependency></dependencies>";
        Files.writeString(dir.resolve("pom.xml"), "<project><modelVersion>4.0.0</modelVersion><groupId>t</groupId>"
                + "<artifactId>" + name + "</artifactId><version>1</version>" + dependencies + "</project>\n");
        Files.writeString(control.resolve(name + ".deps"), FakeMavenRepo.depsTxt(List.of(deps)));
        return dir;
    }

    static TestLibrary mutableLibrary(String method) {
        return TestLibrary.builder(MUTABLE).source("com/acme/Mutable.java", """
                package com.acme;

                /** Changes between test runs. */
                public class Mutable {
                    /** The %s method. */
                    public void %s() {
                    }
                }
                """.formatted(method, method)).build();
    }

    /** A jar declaring {@code java.util.Optional}: the JDK must still win (javac semantics). */
    static ResolvedDependency installShadowJar() throws IOException {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER, "java/util/Optional",
                null, "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "shadowMarker", "()V",
                null, null);
        method.visitCode();
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();
        Path jar = repo.artifactDirectory(SHADOW).resolve("shadow-1.0.jar");
        Files.createDirectories(jar.getParent());
        JarWriter.writeEntries(Map.of("java/util/Optional.class", writer.toByteArray()), jar);
        repo.writePom(SHADOW);
        return new ResolvedDependency(SHADOW, Scope.COMPILE, jar);
    }

    static Run jdoc(Path workingDirectory, String... args) {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        JdocEnvironment environment = env.toBuilder().workingDirectory(workingDirectory).build();
        int code = JdocCli.forEnvironment(() -> environment, new PrintWriter(out), new PrintWriter(err))
                .execute(args);
        return new Run(code, out.toString(), err.toString());
    }

    @Test
    void indexReportsCountsAndTypesWithoutSourcesThenForceRebuildsTheModule() throws IOException {
        assertThat(firstIndex.code()).isZero();
        assertThat(firstIndex.err()).isEmpty();
        assertThat(firstIndex.lines()).first()
                .isEqualTo("module " + app.toRealPath() + "  (3 artifacts + jdk 25)");
        assertThat(firstIndex.lines().get(1)).matches("indexed 4 artifacts \\(\\d+ types, \\d+ members\\), 0 up to date");
        assertThat(firstIndex.lines()).containsSubsequence(
                "types without sources:",
                "  sample-lib 1.0.0: 2 types",
                "    com.example.sample.gen.GeneratedModel  javadoc",
                "    com.example.sample.kt.KotlinUtils      decompiled",
                "  shadow 1.0: 1 type",
                "    java.util.Optional  decompiled");

        Run unchanged = jdoc(app, "index");
        assertThat(unchanged.lines().get(1)).isEqualTo("indexed 0 artifacts (0 types, 0 members), 4 up to date");

        Run forced = jdoc(app, "index", "--force");
        assertThat(forced.code()).isZero();
        assertThat(forced.lines().get(1)).matches("indexed 3 artifacts \\(\\d+ types, \\d+ members\\), 1 up to date");
    }

    @Test
    void classViewOfOptionalComesFromTheJdkEvenWhenALibraryDeclaresTheSameName() {
        Run run = jdoc(app, "class", "java.util.Optional");

        assertThat(run.code()).isZero();
        assertThat(run.err()).isEmpty();
        List<String> lines = run.lines();
        assertThat(lines.getFirst()).isEqualTo("java.util.Optional<T>  public final class  (jdk 25)");
        assertThat(lines.get(1)).startsWith("A container object which may or may not contain a non-`null` value.");
        assertThat(lines.get(2)).isEmpty();
        assertThat(lines).anySatisfy(line -> assertThat(line)
                .matches("static <T> Optional<T> empty\\(\\) +Returns an empty `Optional` instance\\."));
        assertThat(lines).anySatisfy(line -> assertThat(line)
                .matches("static <T> Optional<T> of\\(T value\\) +Returns an `Optional` describing the given "
                        + "non-`null` value\\."));
        assertThat(lines).anySatisfy(line -> assertThat(line)
                .matches("T orElseThrow\\(\\) +If a value is present, returns the value, otherwise throws "
                        + "`NoSuchElementException`\\."));
        assertThat(lines.getLast()).startsWith("inherited from Object: getClass, ");
        assertThat(lines).noneMatch(line -> line.contains("shadowMarker"));

        assertThat(jdoc(app, "class", "Optional").lines().getFirst()).endsWith("(jdk 25)");
        assertThat(jdoc(app, "method", "java.util.Optional#shadowMarker").code()).isEqualTo(1);
    }

    @Test
    void ambiguousSimpleNameListsBothDurations() {
        Run run = jdoc(app, "class", "Duration");

        assertThat(run.code()).isEqualTo(2);
        assertThat(run.lines()).containsExactly(
                "2 types named Duration on this classpath:",
                "  java.time.Duration           jdk 25",
                "  javax.xml.datatype.Duration  jdk 25",
                "Re-run with a fully qualified name.");
    }

    @Test
    void methodAndSourceForEveryDocSource() {
        Run jdk = jdoc(app, "method", "java.util.Optional#orElseThrow()");
        assertThat(jdk.code()).isZero();
        assertThat(jdk.lines()).containsSubsequence("java.util.Optional<T>  public final class  (jdk 25)",
                "T orElseThrow()", "@since 10");

        Run sources = jdoc(app, "method", "com.example.sample.Circle#area");
        assertThat(sources.code()).isZero();
        assertThat(sources.lines().getFirst()).isEqualTo("com.example.sample.Circle  public final class  "
                + "(sample-lib 1.0.0)");

        Run javadoc = jdoc(app, "method", "GeneratedModel#withId(String)");
        assertThat(javadoc.lines().getFirst()).endsWith("(sample-lib 1.0.0)  [from javadoc jar]");
        assertThat(javadoc.lines()).contains("@param id the new id");

        Run decompiled = jdoc(app, "source", "com.example.sample.kt.KotlinUtils#twice");
        assertThat(decompiled.code()).isZero();
        assertThat(decompiled.lines().getFirst()).endsWith("[decompiled, no docs]");
        assertThat(decompiled.lines()).contains("// decompiled");
        assertThat(decompiled.out()).contains("* 2");

        Run jdkSource = jdoc(app, "source", "java.util.Optional#isPresent");
        assertThat(jdkSource.out()).contains("public boolean isPresent() {", "return value != null;");
    }

    @Test
    void findImplsMemberAndPackage() {
        Run find = jdoc(app, "find", "AbstractSha");
        assertThat(find.code()).isZero();
        assertThat(find.lines()).anySatisfy(line -> assertThat(line)
                .matches("com\\.example\\.sample\\.AbstractShape +sample-lib 1\\.0\\.0"));

        Run impls = jdoc(app, "impls", "com.example.sample.Shape");
        assertThat(impls.code()).isZero();
        assertThat(impls.lines()).anySatisfy(line -> assertThat(line)
                .matches("com\\.example\\.sample\\.AbstractShape +sample-lib 1\\.0\\.0"));
        assertThat(impls.lines()).anySatisfy(line -> assertThat(line)
                .matches("com\\.example\\.sample\\.Circle +sample-lib 1\\.0\\.0  indirect"));

        Run member = jdoc(app, "member", "twice");
        assertThat(member.lines()).containsExactly("1 member named twice",
                "com.example.sample.kt.KotlinUtils#twice(int)  sample-lib 1.0.0");

        Run pkg = jdoc(app, "package", "com.example.sample");
        assertThat(pkg.code()).isZero();
        assertThat(pkg.lines().getFirst()).isEqualTo("com.example.sample  package  (sample-lib 1.0.0)");
        assertThat(pkg.lines().get(1)).isEqualTo("Sample shapes library used by jdoc tests.");
        assertThat(pkg.lines()).anySatisfy(line -> assertThat(line).startsWith("interface Shape "));
    }

    @Test
    void notFoundExitsWithOne() {
        assertThat(jdoc(app, "class", "NoSuchType").code()).isEqualTo(1);
        assertThat(jdoc(app, "method", "java.util.Optional#noSuchMethod").code()).isEqualTo(1);
        assertThat(jdoc(app, "find", "zqzqzq").code()).isEqualTo(1);
        assertThat(jdoc(app, "package", "no.such.pkg").code()).isEqualTo(1);
        assertThat(jdoc(app, "impls", "com.example.sample.Circle").code()).isEqualTo(1);
        assertThat(jdoc(app, "member", "noSuchMember").code()).isEqualTo(1);
    }

    @Test
    void errorsExitWithThreeAndOneLineOnStderr() throws IOException {
        Path noPom = Files.createDirectories(root.resolve("nopom"));
        Run run = jdoc(noPom, "class", "java.util.Optional");
        assertThat(run.code()).isEqualTo(3);
        assertThat(run.out()).isEmpty();
        assertThat(run.err().lines()).singleElement().asString().startsWith("error: ").contains("pom.xml");

        Run usage = jdoc(app, "method");
        assertThat(usage.code()).isEqualTo(3);
        assertThat(usage.err()).startsWith("error: Missing required parameter");

        Run invalid = jdoc(app, "method", "Optional#");
        assertThat(invalid.code()).isEqualTo(3);
        assertThat(invalid.err()).startsWith("error: invalid member reference");
    }

    @Test
    void cappedResultsPageWithTheCommandLineInTheFooter() {
        Run first = jdoc(app, "class", "java.util.Collections");
        assertThat(first.code()).isZero();
        String footer = first.lines().getLast();
        assertThat(footer).matches("page 1/\\d+ · next: jdoc class java\\.util\\.Collections --page 2");
        assertThat(first.out().length()).isLessThanOrEqualTo(8_000 + 1);

        Run second = jdoc(app, "class", "java.util.Collections", "--page", "2");
        assertThat(second.code()).isZero();
        assertThat(second.lines().getFirst()).isEqualTo(first.lines().getFirst());
        assertThat(second.lines().getLast()).startsWith("page 2/");

        assertThat(jdoc(app, "class", "java.util.Collections", "--page", "99").code()).isEqualTo(1);
    }

    @Test
    void fromPicksTheModuleOfAnotherDirectory() throws IOException {
        Path file = Files.createDirectories(app.resolve("src/main/java/x")).resolve("Uses.java");
        Files.writeString(file, "class Uses {}\n");

        assertThat(jdoc(other, "class", "com.example.sample.Circle").code()).isEqualTo(1);
        assertThat(jdoc(other, "class", "com.example.sample.Circle", "--from", file.toString()).code()).isZero();
        assertThat(jdoc(app, "class", "com.example.sample.Circle", "--from", other.toString()).code())
                .isEqualTo(1);
        assertThat(jdoc(other, "class", "java.util.Optional").code()).isZero();
    }

    @Test
    void changedJarIsReindexedOnTheNextCall() throws IOException {
        Path refresh = module("refresh", null, null, repo.install(mutableLibrary("first"), Scope.COMPILE));
        assertThat(jdoc(refresh, "method", "com.acme.Mutable#first").code()).isZero();
        assertThat(jdoc(refresh, "method", "com.acme.Mutable#second").code()).isEqualTo(1);

        ResolvedDependency changed = repo.install(mutableLibrary("second"), Scope.COMPILE);
        Files.setLastModifiedTime(changed.jar(), FileTime.from(Instant.now().plusSeconds(60)));

        Run run = jdoc(refresh, "method", "com.acme.Mutable#second");
        assertThat(run.code()).isZero();
        assertThat(run.lines()).contains("The second method.");
        assertThat(jdoc(refresh, "method", "com.acme.Mutable#first").code()).isEqualTo(1);
    }

    @Test
    void failedMavenFallsBackToTheLastGoodClasspathWithOneWarning() throws IOException {
        Path stale = module("stale", null, null,
                new ResolvedDependency(SampleLibrary.GAV, Scope.COMPILE, repo.artifactDirectory(SampleLibrary.GAV)
                        .resolve("sample-lib-1.0.0.jar")));
        Run fresh = jdoc(stale, "class", "com.example.sample.Circle");
        assertThat(fresh.code()).isZero();
        assertThat(fresh.err()).isEmpty();

        Files.writeString(control.resolve("stale.fail"), "");
        Files.writeString(stale.resolve("pom.xml"), Files.readString(stale.resolve("pom.xml")) + "<!-- edited -->\n");

        Run run = jdoc(stale, "class", "com.example.sample.Circle");
        assertThat(run.code()).isZero();
        assertThat(run.err().lines()).singleElement().asString()
                .startsWith("warning: mvn failed, using classpath resolved at ");
        assertThat(run.lines().getFirst()).isEqualTo("com.example.sample.Circle  public final class  "
                + "(sample-lib 1.0.0)");
    }

    @Test
    void neverResolvedModuleUsesNewestRepositoryVersionsAndTagsHeaders() throws IOException {
        Path guess = module("guess", "com.example", "sample-lib");
        Files.writeString(control.resolve("guess.fail"), "");

        Run run = jdoc(guess, "class", "com.example.sample.Circle");
        assertThat(run.code()).isZero();
        assertThat(run.err()).contains("versions unresolved");
        assertThat(run.lines().getFirst()).isEqualTo("com.example.sample.Circle  public final class  "
                + "(sample-lib 1.0.0)  [version unresolved]");
        assertThat(jdoc(guess, "class", "java.util.Optional").lines().getFirst())
                .isEqualTo("java.util.Optional<T>  public final class  (jdk 25)");
    }

    @Test
    void builtFatJarRuns() throws Exception {
        Path jar = fatJar();
        assumeTrue(Files.isRegularFile(jar), "fat jar not built: " + jar);

        Run help = java(jar, app, "--help");
        assertThat(help.code()).isZero();
        assertThat(help.out()).contains("Usage: jdoc");

        Run method = java(jar, app, "method", "java.util.Optional#orElseThrow()");
        assertThat(method.code()).as(method.err()).isZero();
        assertThat(method.err()).isEmpty();
        assertThat(method.lines()).containsSubsequence("java.util.Optional<T>  public final class  (jdk 25)",
                "T orElseThrow()", "@since 10");

        Run decompiled = java(jar, app, "source", "com.example.sample.kt.KotlinUtils#joinNames");
        assertThat(decompiled.code()).as(decompiled.err()).isZero();
        assertThat(decompiled.lines()).contains("// decompiled");
    }

    @Test
    void twoFirstRunsAtOnceBothSucceed() throws Exception {
        Path jar = fatJar();
        assumeTrue(Files.isRegularFile(jar), "fat jar not built: " + jar);
        Path freshCache = Files.createDirectories(root.resolve("fresh-cache"));

        List<Process> processes = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            processes.add(javaProcess(jar, app, freshCache, "class", "java.util.Optional"));
        }
        for (Process process : processes) {
            Run run = finish(process);
            assertThat(run.code()).as(run.err()).isZero();
            assertThat(run.lines().getFirst()).isEqualTo("java.util.Optional<T>  public final class  (jdk 25)");
        }
    }

    private static Path fatJar() throws URISyntaxException {
        Path testClasses = Path.of(EndToEndIT.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        return testClasses.getParent().resolve("jdoc.jar");
    }

    private static Run java(Path jar, Path workingDirectory, String... args) throws Exception {
        return finish(javaProcess(jar, workingDirectory, env.cacheDirectory(), args));
    }

    private static Process javaProcess(Path jar, Path workingDirectory, Path cache, String... args)
            throws IOException {
        List<String> command = new ArrayList<>(List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-jar", jar.toString()));
        command.addAll(List.of(args));
        ProcessBuilder builder = new ProcessBuilder(command).directory(workingDirectory.toFile());
        builder.environment().put("JDOC_CACHE", cache.toString());
        builder.environment().put("JDOC_MVN", env.mavenExecutable());
        builder.environment().put("JDOC_M2_REPO", env.localRepository().toString());
        builder.environment().put("JAVA_HOME", System.getProperty("java.home"));
        return builder.start();
    }

    private static Run finish(Process process) throws Exception {
        process.getOutputStream().close();
        byte[] out = process.getInputStream().readAllBytes();
        byte[] err = process.getErrorStream().readAllBytes();
        if (!process.waitFor(5, TimeUnit.MINUTES)) {
            process.destroyForcibly();
            throw new AssertionError("jdoc did not finish");
        }
        return new Run(process.exitValue(), new String(out, StandardCharsets.UTF_8),
                new String(err, StandardCharsets.UTF_8));
    }
}
