package io.github.vecluu.jdoc.source;

import static io.github.vecluu.jdoc.source.ParsedSources.descriptor;
import static io.github.vecluu.jdoc.source.ParsedSources.member;
import static io.github.vecluu.jdoc.source.ParsedSources.signature;
import static io.github.vecluu.jdoc.source.ParsedSources.type;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.vecluu.jdoc.model.DocSource;
import io.github.vecluu.jdoc.model.MemberKind;
import io.github.vecluu.jdoc.model.TypeKind;
import io.github.vecluu.jdoc.model.TypeName;
import io.github.vecluu.jdoc.model.Visibility;
import io.github.vecluu.jdoc.testsupport.Prerequisites;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * Parses real files from the running JDK's {@code lib/src.zip}; skipped if the JDK has none (fails in CI instead).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdkSourceParseIT {

    private static final String BASE = "java.base/";

    private final JavaSourceParser parser = new JavaSourceParser();
    private ZipFile srcZip;

    @BeforeAll
    void openSrcZip() throws IOException {
        Path zip = Path.of(System.getProperty("java.home"), "lib", "src.zip");
        Prerequisites.fromEnvironment().require(Files.isRegularFile(zip), "no src.zip in the running JDK");
        srcZip = new ZipFile(zip.toFile());
    }

    @AfterAll
    void close() throws IOException {
        parser.close();
        if (srcZip != null) {
            srcZip.close();
        }
    }

    private ParsedSourceFile parse(String path) {
        ZipEntry entry = srcZip.getEntry(BASE + path);
        assertThat(entry).as(path).isNotNull();
        ParsedSourceFile file = parser.parse(new JavaSource(path, read(entry), DocSource.JDK));
        assertThat(file.problems()).as(path).isEmpty();
        return file;
    }

    private String read(ZipEntry entry) {
        try {
            return new String(srcZip.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void optional() {
        ParsedType optional = type(parse("java/util/Optional.java"), "java.util.Optional");
        assertThat(optional.descriptor().modifiers()).isEqualTo("public final");
        assertThat(optional.descriptor().typeParameters()).isEqualTo("<T>");
        assertThat(optional.descriptor().docSource()).isEqualTo(DocSource.JDK);
        assertThat(optional.descriptor().entryPath()).isEqualTo("java/util/Optional.java");
        assertThat(optional.descriptor().firstSentence())
                .isEqualTo("A container object which may or may not contain a non-`null` value.");
        assertThat(signature(optional, "empty()")).isEqualTo("static <T> Optional<T> empty()");
        assertThat(signature(optional, "of(Object)")).isEqualTo("static <T> Optional<T> of(T value)");
        assertThat(signature(optional, "flatMap(Function)"))
                .isEqualTo("<U> Optional<U> flatMap(Function<? super T, ? extends Optional<? extends U>> mapper)");
        assertThat(signature(optional, "orElseThrow(Supplier)"))
                .isEqualTo("<X extends Throwable> T orElseThrow(Supplier<? extends X> exceptionSupplier) throws X");
        assertThat(descriptor(optional, "of(Object)").firstSentence())
                .isEqualTo("Returns an `Optional` describing the given non-`null` value.");
        assertThat(member(optional, "of(Object)").doc().blockTags()).contains(
                "@param value the value to describe, which must be non-`null`",
                "@param <T> the type of the value");
        assertThat(descriptor(optional, "EMPTY").visibility()).isEqualTo(Visibility.PRIVATE);
    }

    @Test
    void mapAndNestedEntry() {
        ParsedSourceFile file = parse("java/util/Map.java");
        ParsedType map = type(file, "java.util.Map");
        assertThat(map.descriptor().kind()).isEqualTo(TypeKind.INTERFACE);
        assertThat(map.descriptor().typeParameters()).isEqualTo("<K, V>");
        assertThat(signature(map, "entrySet()")).isEqualTo("Set<Map.Entry<K, V>> entrySet()");
        assertThat(signature(map, "getOrDefault(Object,Object)"))
                .isEqualTo("default V getOrDefault(Object key, V defaultValue)");
        assertThat(signature(map, "ofEntries(Map.Entry[])"))
                .isEqualTo("static <K, V> Map<K, V> ofEntries(Map.Entry<? extends K, ? extends V>... entries)");
        ParsedType entry = type(file, "java.util.Map$Entry");
        assertThat(entry.descriptor().modifiers()).isEqualTo("public static");
        assertThat(entry.descriptor().visibility()).isEqualTo(Visibility.PUBLIC);
        assertThat(entry.descriptor().firstSentence()).isEqualTo("A map entry (key-value pair).");
        assertThat(signature(entry, "getKey()")).isEqualTo("K getKey()");
        assertThat(signature(entry, "comparingByKey()"))
                .isEqualTo("static <K extends Comparable<? super K>, V> Comparator<Map.Entry<K, V>> comparingByKey()");
    }

    @Test
    void string() {
        ParsedType string = type(parse("java/lang/String.java"), "java.lang.String");
        assertThat(string.descriptor().supertypes()).extracting(TypeName::binaryName).containsExactly(
                "java.lang.Object", "java.io.Serializable", "java.lang.Comparable", "java.lang.CharSequence",
                "java.lang.constant.Constable", "java.lang.constant.ConstantDesc");
        assertThat(signature(string, "String(byte[],Charset)")).isEqualTo("String(byte[] bytes, Charset charset)");
        assertThat(signature(string, "format(String,Object[])"))
                .isEqualTo("static String format(String format, Object... args)");
        assertThat(signature(string, "CASE_INSENSITIVE_ORDER"))
                .isEqualTo("static final Comparator<String> CASE_INSENSITIVE_ORDER");
        assertThat(descriptor(string, "length()").firstSentence()).isEqualTo("Returns the length of this string.");
        assertThat(string.members().stream().filter(m -> m.descriptor().name().equals("valueOf")
                && m.descriptor().visibility().isApi())).hasSizeGreaterThanOrEqualTo(9);
    }

    @Test
    void record() {
        ParsedType record = type(parse("java/lang/Record.java"), "java.lang.Record");
        assertThat(record.descriptor().modifiers()).isEqualTo("public abstract");
        assertThat(signature(record, "Record()")).isEqualTo("protected Record()");
        assertThat(signature(record, "equals(Object)")).isEqualTo("abstract boolean equals(Object obj)");
        assertThat(record.doc().description()).contains("record class");
    }

    @Test
    void enumTimeUnit() {
        ParsedType unit = type(parse("java/util/concurrent/TimeUnit.java"), "java.util.concurrent.TimeUnit");
        assertThat(unit.descriptor().kind()).isEqualTo(TypeKind.ENUM);
        assertThat(unit.members()).filteredOn(m -> m.descriptor().kind() == MemberKind.ENUM_CONSTANT)
                .extracting(m -> m.descriptor().name())
                .containsExactly("NANOSECONDS", "MICROSECONDS", "MILLISECONDS", "SECONDS", "MINUTES", "HOURS",
                        "DAYS");
        assertThat(descriptor(unit, "SECONDS").firstSentence()).isEqualTo("Time unit representing one second.");
        assertThat(signature(unit, "values()")).isEqualTo("static TimeUnit[] values()");
        assertThat(signature(unit, "toChronoUnit()")).isEqualTo("ChronoUnit toChronoUnit()");
    }

    @Test
    void annotationDeprecated() {
        ParsedType deprecated = type(parse("java/lang/Deprecated.java"), "java.lang.Deprecated");
        assertThat(deprecated.descriptor().kind()).isEqualTo(TypeKind.ANNOTATION);
        assertThat(signature(deprecated, "since()")).isEqualTo("String since()");
        assertThat(signature(deprecated, "forRemoval()")).isEqualTo("boolean forRemoval()");
    }

    @Test
    void concurrentHashMap() {
        ParsedSourceFile file = parse("java/util/concurrent/ConcurrentHashMap.java");
        ParsedType chm = type(file, "java.util.concurrent.ConcurrentHashMap");
        assertThat(chm.descriptor().supertypes()).extracting(TypeName::binaryName).containsExactly(
                "java.util.AbstractMap", "java.util.concurrent.ConcurrentMap", "java.io.Serializable");
        assertThat(signature(chm, "newKeySet()"))
                .isEqualTo("static <K> ConcurrentHashMap.KeySetView<K, Boolean> newKeySet()");
        assertThat(signature(chm, "computeIfAbsent(Object,Function)"))
                .isEqualTo("V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction)");
        ParsedType keySetView = type(file, "java.util.concurrent.ConcurrentHashMap$KeySetView");
        assertThat(keySetView.descriptor().modifiers()).isEqualTo("public static final");
        assertThat(keySetView.descriptor().typeParameters()).isEqualTo("<K, V>");
        assertThat(file.types()).extracting(t -> t.name().binaryName())
                .contains("java.util.concurrent.ConcurrentHashMap$Node")
                .noneMatch(name -> name.matches(".*\\$\\d+.*"));
        assertThat(type(file, "java.util.concurrent.ConcurrentHashMap$Node").descriptor().visibility())
                .isEqualTo(Visibility.PACKAGE);
    }

    @Test
    void packageInfoAndModuleInfo() {
        ParsedSourceFile packageInfo = parse("java/util/package-info.java");
        assertThat(packageInfo.packageDoc()).isPresent();
        assertThat(packageInfo.packageDoc().orElseThrow().firstSentence()).isNotBlank();
        ParsedSourceFile moduleInfo = parse("module-info.java");
        assertThat(moduleInfo.types()).isEmpty();
    }

    @Test
    void parsesAllOfJavaBase() {
        long start = System.nanoTime();
        int files = 0;
        int types = 0;
        int members = 0;
        List<String> problems = new ArrayList<>();
        Enumeration<? extends ZipEntry> entries = srcZip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (!entry.getName().startsWith(BASE) || !entry.getName().endsWith(".java")) {
                continue;
            }
            String path = entry.getName().substring(BASE.length());
            ParsedSourceFile file = parser.parse(new JavaSource(path, read(entry), DocSource.JDK));
            files++;
            types += file.types().size();
            for (ParsedType type : file.types()) {
                members += type.members().size();
            }
            if (file.hasProblems()) {
                problems.add(path + ": " + file.problems());
            }
        }
        long millis = (System.nanoTime() - start) / 1_000_000;
        System.out.printf("JdkSourceParseIT: parsed %d java.base files (%d types, %d members) in %d ms%n",
                files, types, members, millis);
        assertThat(problems).isEmpty();
        assertThat(files).isGreaterThan(2000);
        assertThat(types).isGreaterThan(files);
    }
}
