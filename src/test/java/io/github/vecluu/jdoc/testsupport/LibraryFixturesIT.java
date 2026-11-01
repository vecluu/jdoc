package io.github.vecluu.jdoc.testsupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.vecluu.jdoc.config.JdocEnvironment;
import io.github.vecluu.jdoc.model.Gav;
import io.github.vecluu.jdoc.model.ResolvedDependency;
import io.github.vecluu.jdoc.model.Scope;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LibraryFixturesIT {

    @TempDir
    Path tempDir;

    @Test
    void completeLibraryProducesAllThreeJars() throws IOException {
        BuiltLibrary built = SampleLibrary.complete().writeTo(tempDir);

        assertThat(built.binaryJar()).isEqualTo(tempDir.resolve("sample-lib-1.0.0.jar"));
        assertThat(entries(built.binaryJar())).contains(
                "META-INF/MANIFEST.MF",
                "com/example/sample/",
                "com/example/sample/Circle.class",
                "com/example/sample/Shapes$Registry$Entry.class",
                "com/example/sample/Square$Corner.class",
                "com/example/sample/Color$1.class",
                "com/example/sample/gen/GeneratedModel.class",
                "com/example/sample/kt/KotlinUtils.class");

        assertThat(built.sourcesJar()).hasValue(tempDir.resolve("sample-lib-1.0.0-sources.jar"));
        assertThat(entries(built.sourcesJar().orElseThrow()))
                .contains("com/example/sample/Circle.java", "com/example/sample/package-info.java",
                        SampleLibrary.GENERATED_MODEL_SOURCE)
                .noneMatch(name -> name.endsWith(".class"));

        Path javadoc = built.javadocJar().orElseThrow();
        assertThat(javadoc).isEqualTo(tempDir.resolve("sample-lib-1.0.0-javadoc.jar"));
        assertThat(entries(javadoc)).contains(
                "com/example/sample/Circle.html",
                "com/example/sample/Shapes.Registry.Entry.html",
                "com/example/sample/Square.Corner.html",
                "com/example/sample/package-summary.html",
                "com/example/sample/kt/KotlinUtils.html",
                "index.html");
        // the work directory is cleaned up
        try (var files = Files.list(tempDir)) {
            assertThat(files.map(p -> p.getFileName().toString())).containsExactlyInAnyOrder(
                    "sample-lib-1.0.0.jar", "sample-lib-1.0.0-sources.jar", "sample-lib-1.0.0-javadoc.jar");
        }
    }

    @Test
    void javadocJarContainsRealJdkHtmlLayout() throws IOException {
        BuiltLibrary built = SampleLibrary.complete().writeTo(tempDir);

        String circle = entryText(built.javadocJar().orElseThrow(), "com/example/sample/Circle.html");
        assertThat(circle)
                .contains("class-description")
                .contains("A circle defined by its radius.")
                .contains("id=\"&lt;init&gt;(double,com.example.sample.Color)\"")
                .contains("id=\"diameter()\"");
        String shapes = entryText(built.javadocJar().orElseThrow(), "com/example/sample/Shapes.html");
        assertThat(shapes).contains("id=\"max(T,T)\"", "id=\"max(int,int)\"", "id=\"max(double...)\"");
    }

    @Test
    void mixedLibraryOmitsSourcesAndPages() throws IOException {
        BuiltLibrary built = SampleLibrary.mixed().writeTo(tempDir);

        assertThat(entries(built.binaryJar())).contains(
                SampleLibrary.GENERATED_MODEL.classEntryPath(), SampleLibrary.KOTLIN_UTILS.classEntryPath());
        assertThat(entries(built.sourcesJar().orElseThrow()))
                .contains(SampleLibrary.KOTLIN_UTILS_KT, "com/example/sample/Shape.java")
                .doesNotContain(SampleLibrary.GENERATED_MODEL_SOURCE, SampleLibrary.KOTLIN_UTILS_SOURCE);
        assertThat(entries(built.javadocJar().orElseThrow()))
                .contains(SampleLibrary.GENERATED_MODEL.javadocPagePath())
                .doesNotContain(SampleLibrary.KOTLIN_UTILS.javadocPagePath());
    }

    @Test
    void binaryOnlyLibraryHasNoSiblings() {
        BuiltLibrary built = SampleLibrary.binaryOnly().writeTo(tempDir);

        assertThat(built.sourcesJar()).isEmpty();
        assertThat(built.javadocJar()).isEmpty();
        assertThat(built.binaryJar()).isRegularFile();
    }

    @Test
    void parametersOptionControlsMethodParameterNames() throws Exception {
        TestLibrary.Builder base = TestLibrary.builder(Gav.of("t", "p", "1"))
                .source("t/P.java", "package t; public class P { public void m(int count) {} }")
                .withSources(false).withJavadoc(false);
        Path with = base.parameters(true).build().writeTo(tempDir.resolve("with")).binaryJar();
        Path without = base.parameters(false).debugInfo(false).build().writeTo(tempDir.resolve("without"))
                .binaryJar();

        assertThat(classBytesContain(with, "t/P.class", "MethodParameters")).isTrue();
        assertThat(classBytesContain(without, "t/P.class", "MethodParameters")).isFalse();
        assertThat(classBytesContain(without, "t/P.class", "count")).isFalse();
    }

    @Test
    void compileErrorsFailFast() {
        TestLibrary broken = TestLibrary.builder(Gav.of("t", "broken", "1"))
                .source("t/B.java", "package t; public class B { int x = ; }")
                .build();

        assertThatThrownBy(() -> broken.writeTo(tempDir)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not compile");
    }

    @Test
    void builderRejectsUnknownOmissionsAndBadPaths() {
        TestLibrary.Builder builder = TestLibrary.builder(Gav.of("t", "x", "1"))
                .source("t/X.java", "package t; class X {}");
        assertThatThrownBy(() -> builder.omitFromSources("t/Y.java").build())
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> builder.source("/abs/X.java", ""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> builder.source("t/X.kt", ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fakeMavenRepoLaysOutRepositoryAndDepsTxt() throws IOException {
        JdocEnvironment env = TestEnvironments.inTempDir(tempDir);
        FakeMavenRepo repo = TestEnvironments.repository(env);

        ResolvedDependency dependency = repo.install(SampleLibrary.complete(), Scope.TEST);

        Path versionDir = env.localRepository().resolve("com/example/sample-lib/1.0.0");
        assertThat(dependency.jar()).isEqualTo(versionDir.resolve("sample-lib-1.0.0.jar"));
        assertThat(dependency.scope()).isEqualTo(Scope.TEST);
        assertThat(versionDir.resolve("sample-lib-1.0.0-sources.jar")).isRegularFile();
        assertThat(versionDir.resolve("sample-lib-1.0.0-javadoc.jar")).isRegularFile();
        assertThat(Files.readString(versionDir.resolve("sample-lib-1.0.0.pom")))
                .contains("<artifactId>sample-lib</artifactId>");
        assertThat(FakeMavenRepo.depsTxt(List.of(dependency))).isEqualTo(
                "\nThe following files have been resolved:\n   com.example:sample-lib:jar:1.0.0:test:"
                        + dependency.jar() + "\n");
    }

    @Test
    void fakeMavenScriptIsExecutable() throws Exception {
        JdocEnvironment env = TestEnvironments.inTempDir(tempDir);
        Path script = TestEnvironments.installFakeMaven(env, "echo \"args: $@\"");

        Process process = new ProcessBuilder(script.toString(), "-o", "-q").redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertThat(process.waitFor()).isZero();
        assertThat(output).isEqualTo("args: -o -q\n");
        assertThat(env.workingDirectory()).isDirectory();
        assertThat(env.cacheDirectory()).doesNotExist();
    }

    private static List<String> entries(Path jar) throws IOException {
        try (JarFile file = new JarFile(jar.toFile())) {
            return file.stream().map(ZipEntry::getName).toList();
        }
    }

    private static String entryText(Path jar, String entry) throws IOException {
        try (JarFile file = new JarFile(jar.toFile())) {
            ZipEntry zipEntry = file.getEntry(entry);
            assertThat(zipEntry).as(entry).isNotNull();
            return new String(file.getInputStream(zipEntry).readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static boolean classBytesContain(Path jar, String entry, String text) throws IOException {
        try (JarFile file = new JarFile(jar.toFile())) {
            byte[] bytes = file.getInputStream(file.getEntry(entry)).readAllBytes();
            return new String(bytes, StandardCharsets.ISO_8859_1).contains(text);
        }
    }
}
