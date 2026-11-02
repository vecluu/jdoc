package io.github.vecluu.jdoc.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class TrainingLibrarySourcesTest {

    @Test
    void placesEveryFileInThePackageDirectoryAndDeclaresThatPackage() {
        Map<String, String> sources = TrainingLibrarySources.forPackage("a.b.c");

        assertThat(sources).containsKeys("a/b/c/package-info.java", "a/b/c/Gadget.java", "a/b/c/Widget.java",
                "a/b/c/Part.java", "a/b/c/Mode.java", "a/b/c/Marker.java");
        assertThat(sources).allSatisfy((path, text) -> {
            assertThat(path).startsWith("a/b/c/").endsWith(".java");
            assertThat(text).contains("package a.b.c;").doesNotContain("PKG");
        });
    }

    @Test
    void coversTheShapesTheTrainingWorkloadAsksFor() {
        Map<String, String> sources = TrainingLibrarySources.forPackage("p");

        assertThat(sources.get("p/Widget.java")).contains("public Widget resize(int size)", "{@inheritDoc}",
                "public int add(Part... more)", "public static final class Builder", "@Deprecated")
                .contains("<pre>{@code");
        assertThat(sources.get("p/Gadget.java")).contains("static <T> List<T> labels(", "default <R> R label(");
        assertThat(sources.get("p/Part.java")).contains("public record Part(", "public Part withWeight(");
        assertThat(sources.get("p/Mode.java")).contains("public enum Mode", "int round(double value)");
    }

    @Test
    void givesEachPackageItsOwnCopy() {
        assertThat(TrainingLibrarySources.forPackage("x").get("x/Widget.java"))
                .isEqualTo(TrainingLibrarySources.forPackage("y").get("y/Widget.java").replace("package y;",
                        "package x;"));
    }
}
