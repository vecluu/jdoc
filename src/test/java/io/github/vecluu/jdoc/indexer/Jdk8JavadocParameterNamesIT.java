package io.github.vecluu.jdoc.indexer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.github.vecluu.jdoc.bytecode.ScannedClass;
import io.github.vecluu.jdoc.bytecode.ZipBinaryJarScanner;
import io.github.vecluu.jdoc.javadoc.JavadocJar;
import io.github.vecluu.jdoc.javadoc.JavadocJarReader;
import io.github.vecluu.jdoc.javadoc.JavadocPageParser;
import io.github.vecluu.jdoc.model.MemberDescriptor;
import io.github.vecluu.jdoc.model.TypeName;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Parameter names from a real JDK-8-built javadoc jar (commons-lang3 3.5, HTML 4.01, legacy anchors), whose class
 * files have no {@code MethodParameters}. Uses copies of the jars from the local Maven repository; skipped when
 * they are absent (the jars are not downloaded).
 */
class Jdk8JavadocParameterNamesIT {

    private static final Path REPOSITORY = Path.of(System.getProperty("user.home"), ".m2", "repository",
            "org", "apache", "commons", "commons-lang3", "3.5");

    @TempDir
    Path tmp;

    @Test
    void bytecodeNamesAreReplacedByTheNamesOnTheLegacyPages() throws IOException {
        Path binary = REPOSITORY.resolve("commons-lang3-3.5.jar");
        Path javadoc = REPOSITORY.resolve("commons-lang3-3.5-javadoc.jar");
        assumeTrue(Files.isRegularFile(binary) && Files.isRegularFile(javadoc), "commons-lang3 3.5 not in ~/.m2");
        Path binaryCopy = Files.copy(binary, tmp.resolve(binary.getFileName()));
        Path javadocCopy = Files.copy(javadoc, tmp.resolve(javadoc.getFileName()));

        ZipBinaryJarScanner scanner = new ZipBinaryJarScanner();
        try (JavadocJar pages = JavadocJar.open(javadocCopy)) {
            TypeDocResolver resolver = new TypeDocResolver(Optional.empty(),
                    Optional.of(new JavadocJarReader(pages, new JavadocPageParser())));
            Map<String, MemberDescriptor> stringUtils = members(resolver, scanner, binaryCopy,
                    "org.apache.commons.lang3.StringUtils");
            Map<String, MemberDescriptor> arrayUtils = members(resolver, scanner, binaryCopy,
                    "org.apache.commons.lang3.ArrayUtils");
            Map<String, MemberDescriptor> pair = members(resolver, scanner, binaryCopy,
                    "org.apache.commons.lang3.tuple.Pair");

            assertThat(stringUtils.get("abbreviate(String,int)").signature())
                    .isEqualTo("static String abbreviate(String str, int maxWidth)");
            assertThat(stringUtils.get("join(Object[])").signature())
                    .isEqualTo("static <T> String join(T... elements)");
            assertThat(stringUtils.get("join(Iterable,char)").signature())
                    .isEqualTo("static String join(Iterable<?> iterable, char separator)");
            assertThat(arrayUtils.get("subarray(Object[],int,int)").signature())
                    .isEqualTo("static <T> T[] subarray(T[] array, int startIndexInclusive, int endIndexExclusive)");
            assertThat(pair.get("of(Object,Object)").signature())
                    .isEqualTo("static <L, R> Pair<L, R> of(L left, R right)");
            assertThat(pair.get("compareTo(Pair)").parameters().getFirst().name()).isEqualTo("other");
            for (Map<String, MemberDescriptor> members : List.of(stringUtils, arrayUtils, pair)) {
                assertThat(members.values().stream().filter(member -> member.signature().matches(".*\\barg\\d+\\b.*"))
                        .map(MemberDescriptor::reference)).as("members still showing argN").isEmpty();
            }
        }
    }

    private static Map<String, MemberDescriptor> members(TypeDocResolver resolver, ZipBinaryJarScanner scanner,
                                                         Path jar, String type) {
        ScannedClass scanned = scanner.readClass(jar, TypeName.of(type)).orElseThrow();
        TypeEntry entry = resolver.resolve(scanned.type().name(), List.of(scanned)).getFirst();
        return entry.memberDescriptors().stream()
                .collect(Collectors.toMap(MemberDescriptor::erasedKey, member -> member, (a, b) -> a));
    }
}
