package io.github.vecluu.jdoc.bytecode;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.vecluu.jdoc.model.MemberDescriptor;
import io.github.vecluu.jdoc.model.TypeKind;
import io.github.vecluu.jdoc.model.TypeName;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Reads real-world class files of the running JDK through the {@code jrt:/} file system. */
@Tag("slow")
class JdkJmodSmokeIT {

    private final ClassFileReader reader = new ClassFileReader();
    private final FileSystem jrt = FileSystems.getFileSystem(URI.create("jrt:/"));

    private ScannedClass read(String module, TypeName type) throws IOException {
        byte[] bytes = Files.readAllBytes(jrt.getPath("modules", module, type.classEntryPath()));
        return reader.read(bytes).orElseThrow();
    }

    private static List<String> signatures(ScannedClass scanned, String name) {
        return scanned.members().stream().filter(m -> m.name().equals(name)).map(MemberDescriptor::signature)
                .toList();
    }

    @Test
    void readsOptional() throws IOException {
        ScannedClass optional = read("java.base", TypeName.of("java.util.Optional"));

        assertThat(optional.type().modifiers()).isEqualTo("public final");
        assertThat(optional.type().typeParameters()).isEqualTo("<T>");
        assertThat(signatures(optional, "of")).singleElement().asString().matches("static <T> Optional<T> of\\(T \\w+\\)");
        assertThat(signatures(optional, "orElseThrow")).anySatisfy(s -> assertThat(s)
                .matches("<X extends Throwable> T orElseThrow\\(Supplier<\\? extends X> \\w+\\) throws X"));
        assertThat(signatures(optional, "Optional")).isEmpty();
    }

    @Test
    void readsNestedInterfaceAndEnum() throws IOException {
        ScannedClass entry = read("java.base", TypeName.of("java.util.Map$Entry"));
        assertThat(entry.type().kind()).isEqualTo(TypeKind.INTERFACE);
        assertThat(entry.type().modifiers()).isEqualTo("public static");
        assertThat(signatures(entry, "comparingByKey")).contains(
                "static <K extends Comparable<? super K>, V> Comparator<Map.Entry<K, V>> comparingByKey()");

        ScannedClass state = read("java.base", TypeName.of("java.lang.Thread$State"));
        assertThat(state.type().kind()).isEqualTo(TypeKind.ENUM);
        assertThat(signatures(state, "NEW")).containsExactly("NEW");
    }

    @Test
    void readsStringSupertypes() throws IOException {
        ScannedClass string = read("java.base", TypeName.of("java.lang.String"));

        assertThat(string.type().supertypes()).startsWith(TypeName.of("java.lang.Object"))
                .contains(TypeName.of("java.lang.CharSequence"), TypeName.of("java.lang.Comparable"));
        assertThat(signatures(string, "format")).hasSize(2);
    }

    @Test
    void readsAllOfJavaBaseWithoutErrors() throws IOException {
        AtomicInteger types = new AtomicInteger();
        AtomicInteger members = new AtomicInteger();
        Path root = jrt.getPath("modules", "java.base");
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".class")).toList()) {
                reader.read(Files.readAllBytes(file)).ifPresent(c -> {
                    types.incrementAndGet();
                    members.addAndGet(c.members().size());
                });
            }
        }
        assertThat(types.get()).isGreaterThan(3000);
        assertThat(members.get()).isGreaterThan(30000);
    }
}
