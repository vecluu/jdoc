package io.github.vecluu.jdoc.indexer;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.vecluu.jdoc.model.DocSource;
import io.github.vecluu.jdoc.model.MemberDescriptor;
import io.github.vecluu.jdoc.model.TypeName;
import io.github.vecluu.jdoc.source.JavaSourceParser;
import io.github.vecluu.jdoc.testsupport.JarWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JdkSourceZipProducerTest {

    @TempDir
    Path dir;

    /** A miniature src.zip with two modules. */
    static Path miniSrcZip(Path dir) {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("java.base/module-info.java", """
                module java.base {
                    exports java.lang;
                    exports java.util;
                    exports jdk.internal.misc to jdk.unsupported;
                    exports java.hidden to jdk.unsupported;
                }
                """);
        files.put("java.base/java/lang/Object.java", """
                package java.lang;
                /** The root. */
                public class Object {
                    /** Hash. */
                    public native int hashCode();
                    private void secret() {}
                }
                """);
        files.put("java.base/java/util/Bag.java", """
                package java.util;
                import java.io.*;
                import sun.misc.*;
                /** A bag. */
                public abstract class Bag<E> extends Helper implements Collection<E> {
                    /** Adds. */
                    public abstract boolean add(E e);
                    /** An entry. */
                    public interface Entry {}
                }
                """);
        files.put("java.base/java/util/Collection.java", "package java.util; public interface Collection<E> {}");
        files.put("java.base/java/util/package-info.java", "/** Utilities. */ package java.util;");
        files.put("java.base/java/hidden/Secret.java", "package java.hidden; public class Secret {}");
        files.put("java.base/jdk/internal/misc/Unsafe.java", "package jdk.internal.misc; public class Unsafe {}");
        files.put("java.base/sun/misc/Helper.java", "package sun.misc; public class Helper {}");
        files.put("jdk.foo/module-info.java", "module jdk.foo { exports jdk.foo; }");
        files.put("jdk.foo/jdk/foo/Foo.java", "package jdk.foo; /** Foo. */ public record Foo(int x) {}");
        Map<String, byte[]> bytes = new LinkedHashMap<>();
        files.forEach((path, text) -> bytes.put(path, text.getBytes(StandardCharsets.UTF_8)));
        Path zip = dir.resolve("src.zip");
        try {
            JarWriter.writeEntries(bytes, zip);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        return zip;
    }

    private Map<TypeName, TypeEntry> produce(int threads) {
        List<TypeEntry> entries = new ArrayList<>();
        new JdkSourceZipProducer(miniSrcZip(dir), new JdkPackageFilter(), JavaSourceParser::new, threads)
                .produce(entries::addAll);
        return entries.stream().collect(Collectors.toMap(e -> e.type().name(), e -> e));
    }

    @Test
    void indexesExportedJdkPackagesOnly() {
        Map<TypeName, TypeEntry> types = produce(2);

        assertThat(types.keySet()).containsExactlyInAnyOrder(TypeName.of("java.lang.Object"),
                TypeName.of("java.util.Bag"), TypeName.of("java.util.Bag$Entry"), TypeName.of("java.util.Collection"),
                TypeName.of("jdk.foo.Foo"));
    }

    @Test
    void typesCarryJdkDocSourceModulePathsAndApiMembers() {
        TypeEntry object = produce(3).get(TypeName.of("java.lang.Object"));

        assertThat(object.type().docSource()).isEqualTo(DocSource.JDK);
        assertThat(object.type().entryPath()).isEqualTo("java.base/java/lang/Object.java");
        assertThat(object.type().firstSentence()).isEqualTo("The root.");
        assertThat(object.members()).extracting(MemberDescriptor::name).containsExactly("hashCode", "Object");
    }

    @Test
    void supertypesResolveAgainstEverySourceFile() {
        TypeEntry bag = produce(1).get(TypeName.of("java.util.Bag"));

        assertThat(bag.type().supertypes())
                .containsExactly(TypeName.of("sun.misc.Helper"), TypeName.of("java.util.Collection"));
    }
}
