package io.github.vecluu.jdoc.text;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.vecluu.jdoc.model.ArtifactKey;
import io.github.vecluu.jdoc.model.DocSource;
import io.github.vecluu.jdoc.model.Gav;
import io.github.vecluu.jdoc.model.TypeDescriptor;
import io.github.vecluu.jdoc.model.TypeKind;
import io.github.vecluu.jdoc.model.TypeName;
import io.github.vecluu.jdoc.model.Visibility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ResultHeaderTest {

    private static final ArtifactKey JDK = ArtifactKey.jdk(25);
    private static final ArtifactKey LIB = ArtifactKey.maven(Gav.of("org.apache.commons", "commons-lang3", "3.17.0"));

    @Test
    void matchesTheOutlineExample() {
        TypeDescriptor optional = type("java.util.Optional", TypeKind.CLASS, "public final", "<T>", DocSource.JDK);

        assertThat(ResultHeader.of(optional, JDK, false).line())
                .isEqualTo("java.util.Optional<T>  public final class  (jdk 25)");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "CLASS|public abstract|public abstract class",
        "INTERFACE|public|public interface",
        "ENUM|public|public enum",
        "RECORD|public final|public final record",
        "ANNOTATION|public|public @interface",
        "CLASS|''|class",
        "INTERFACE|'  protected   static '|protected static interface",
    })
    void showsModifiersAndKindKeyword(TypeKind kind, String modifiers, String expected) {
        TypeDescriptor type = type("a.b.Thing", kind, modifiers, "", DocSource.SOURCES);

        assertThat(ResultHeader.of(type, LIB, false).line())
                .isEqualTo("a.b.Thing  " + expected + "  (commons-lang3 3.17.0)");
    }

    @Test
    void nestedTypesUseTheSourceStyleName() {
        TypeDescriptor entry = type("java.util.Map$Entry", TypeKind.INTERFACE, "public static", "<K, V>",
                DocSource.JDK);

        assertThat(ResultHeader.of(entry, JDK, false).line())
                .isEqualTo("java.util.Map.Entry<K, V>  public static interface  (jdk 25)");
    }

    @Test
    void appendsDocSourceAndVersionTags() {
        TypeDescriptor javadoc = type("a.Model", TypeKind.CLASS, "public", "", DocSource.JAVADOC);
        TypeDescriptor decompiled = type("a.Util", TypeKind.CLASS, "public final", "", DocSource.DECOMPILED);
        TypeDescriptor sources = type("a.Plain", TypeKind.CLASS, "public", "", DocSource.SOURCES);

        assertThat(ResultHeader.of(javadoc, LIB, false).line())
                .isEqualTo("a.Model  public class  (commons-lang3 3.17.0)  [from javadoc jar]");
        assertThat(ResultHeader.of(decompiled, LIB, true).line())
                .isEqualTo("a.Util  public final class  (commons-lang3 3.17.0)  [decompiled, no docs]"
                        + "  [version unresolved]");
        assertThat(ResultHeader.of(javadoc, LIB, true).line())
                .isEqualTo("a.Model  public class  (commons-lang3 3.17.0)  [from javadoc jar]  [version unresolved]");
        assertThat(new ResultHeader(sources, "lib 1.0", true).toString())
                .isEqualTo("a.Plain  public class  (lib 1.0)  [version unresolved]");
    }

    private static TypeDescriptor type(String binary, TypeKind kind, String modifiers, String typeParams,
            DocSource source) {
        return TypeDescriptor.builder()
                .name(TypeName.of(binary))
                .kind(kind)
                .visibility(Visibility.PUBLIC)
                .modifiers(modifiers)
                .typeParameters(typeParams)
                .docSource(source)
                .entryPath("x/Y.java")
                .build();
    }
}
