package io.github.vecluu.jdoc.bytecode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.vecluu.jdoc.model.DocSource;
import io.github.vecluu.jdoc.model.MemberDescriptor;
import io.github.vecluu.jdoc.model.MemberKind;
import io.github.vecluu.jdoc.model.ParameterInfo;
import io.github.vecluu.jdoc.model.TypeKind;
import io.github.vecluu.jdoc.model.TypeName;
import io.github.vecluu.jdoc.model.Visibility;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

class ClassFileReaderTest {

    private final ClassFileReader reader = new ClassFileReader();

    private Map<String, ScannedClass> read(String path, String source, String... options) {
        Map<String, ScannedClass> result = new TreeMap<>();
        InMemoryCompiler.compile(path, source, options).forEach((name, bytes) ->
                reader.read(bytes).ifPresent(scanned -> result.put(scanned.type().name().binaryName(), scanned)));
        return result;
    }

    private static List<String> signatures(ScannedClass scanned) {
        return scanned.members().stream().map(MemberDescriptor::signature).toList();
    }

    private static MemberDescriptor member(ScannedClass scanned, String name) {
        return scanned.members().stream().filter(m -> m.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void readsTopLevelClassDescriptor() {
        ScannedClass scanned = read("p/Foo.java", """
                package p;
                public abstract class Foo<K, V extends Comparable<? super V>> extends java.util.AbstractMap<K, V>
                        implements java.io.Serializable, Cloneable {
                }
                """).get("p.Foo");

        assertThat(scanned.type().name()).isEqualTo(TypeName.of("p.Foo"));
        assertThat(scanned.type().kind()).isEqualTo(TypeKind.CLASS);
        assertThat(scanned.type().visibility()).isEqualTo(Visibility.PUBLIC);
        assertThat(scanned.type().modifiers()).isEqualTo("public abstract");
        assertThat(scanned.type().typeParameters()).isEqualTo("<K, V extends Comparable<? super V>>");
        assertThat(scanned.type().supertypes()).containsExactly(TypeName.of("java.util.AbstractMap"),
                TypeName.of("java.io.Serializable"), TypeName.of("java.lang.Cloneable"));
        assertThat(scanned.type().docSource()).isEqualTo(DocSource.DECOMPILED);
        assertThat(scanned.type().entryPath()).isEqualTo("p/Foo.class");
        assertThat(scanned.type().firstSentence()).isEmpty();
        assertThat(scanned.type().declarationRange()).isEmpty();
    }

    @Test
    void mapsKindsAndImplicitSupertypes() {
        Map<String, ScannedClass> types = read("p/Kinds.java", """
                package p;
                public class Kinds {
                    public interface I extends Runnable {}
                    public enum E { A }
                    public record R(int x) {}
                    public @interface A {}
                    class Plain {}
                }
                """);

        assertThat(types.get("p.Kinds$I").type().kind()).isEqualTo(TypeKind.INTERFACE);
        assertThat(types.get("p.Kinds$I").type().supertypes()).containsExactly(TypeName.of("java.lang.Runnable"));
        assertThat(types.get("p.Kinds$E").type().kind()).isEqualTo(TypeKind.ENUM);
        assertThat(types.get("p.Kinds$E").type().supertypes()).containsExactly(TypeName.of("java.lang.Enum"));
        assertThat(types.get("p.Kinds$R").type().kind()).isEqualTo(TypeKind.RECORD);
        assertThat(types.get("p.Kinds$R").type().supertypes()).containsExactly(TypeName.of("java.lang.Record"));
        assertThat(types.get("p.Kinds$A").type().kind()).isEqualTo(TypeKind.ANNOTATION);
        assertThat(types.get("p.Kinds$A").type().supertypes())
                .containsExactly(TypeName.of("java.lang.annotation.Annotation"));
        assertThat(types.get("p.Kinds$Plain").type().supertypes()).containsExactly(TypeName.of("java.lang.Object"));
    }

    @Test
    void nestedVisibilityAndModifiersComeFromInnerClasses() {
        Map<String, ScannedClass> types = read("p/Outer.java", """
                package p;
                public class Outer {
                    protected static final class Prot {}
                    private class Priv {}
                    static abstract class Pkg {}
                    public sealed interface S permits Impl {}
                    public static final class Impl implements S {}
                }
                class Hidden {}
                """);

        assertThat(types.get("p.Outer$Prot").type().visibility()).isEqualTo(Visibility.PROTECTED);
        assertThat(types.get("p.Outer$Prot").type().modifiers()).isEqualTo("protected static final");
        assertThat(types.get("p.Outer$Priv").type().visibility()).isEqualTo(Visibility.PRIVATE);
        assertThat(types.get("p.Outer$Priv").type().modifiers()).isEqualTo("private");
        assertThat(types.get("p.Outer$Pkg").type().visibility()).isEqualTo(Visibility.PACKAGE);
        assertThat(types.get("p.Outer$Pkg").type().modifiers()).isEqualTo("abstract static");
        assertThat(types.get("p.Outer$S").type().modifiers()).isEqualTo("public static sealed");
        assertThat(types.get("p.Hidden").type().visibility()).isEqualTo(Visibility.PACKAGE);
        assertThat(types.get("p.Hidden").type().modifiers()).isEmpty();
    }

    @Test
    void skipsAnonymousLocalAndTheirNestedClasses() {
        Map<String, ScannedClass> types = read("p/Outer.java", """
                package p;
                public class Outer {
                    public Runnable anonymous() {
                        return new Runnable() { public void run() {} };
                    }
                    public Object local() {
                        class Local { class InLocal {} }
                        return new Local();
                    }
                    public enum E { A { }, B }
                }
                """);

        assertThat(types).containsOnlyKeys("p.Outer", "p.Outer$E");
    }

    @Test
    void skipsSyntheticBridgeAndNonApiMembers() {
        ScannedClass scanned = read("p/Sub.java", """
                package p;
                public class Sub implements Comparable<Sub> {
                    private int hidden;
                    int packageField;
                    static { System.out.println(); }
                    private Sub(int x) {}
                    public Sub() {}
                    public int compareTo(Sub other) { return 0; }
                    public Sub copy() { return this; }
                    public Runnable lambda() { return () -> System.out.println(hidden); }
                    void packageMethod() {}
                }
                """).get("p.Sub");

        assertThat(signatures(scanned)).containsExactly("Sub()", "int compareTo(Sub arg0)", "Sub copy()",
                "Runnable lambda()");
    }

    @Test
    void covariantOverrideKeepsOnlyTheRealMethod() {
        ScannedClass scanned = read("p/Base.java", """
                package p;
                public class Base implements Cloneable {
                    @Override public Base clone() { return this; }
                }
                """).get("p.Base");

        assertThat(signatures(scanned)).containsExactly("Base()", "Base clone()");
    }

    @Test
    void rendersMethodsWithGenericsVarargsAndThrows() {
        ScannedClass scanned = read("p/Util.java", """
                package p;
                import java.util.*;
                public abstract class Util<T> {
                    public static <T extends Comparable<? super T>> T max(T a, T b) { return a; }
                    public static String join(String sep, Object... parts) { return ""; }
                    public Map<String, List<Map.Entry<T, int[]>>> nested(List<? extends T>[] lists) { return null; }
                    protected abstract void hook(T value) throws java.io.IOException, IllegalStateException;
                    public <X extends Throwable> void sneaky() throws X {}
                    public Optional<?> wildcard(Class<?> type) { return null; }
                    public <A, B extends A> B cast(A a, int[][] grid) { return null; }
                }
                """, "-parameters").get("p.Util");

        assertThat(signatures(scanned)).containsExactly(
                "Util()",
                "static <T extends Comparable<? super T>> T max(T a, T b)",
                "static String join(String sep, Object... parts)",
                "Map<String, List<Map.Entry<T, int[]>>> nested(List<? extends T>[] lists)",
                "protected abstract void hook(T value) throws IOException, IllegalStateException",
                "<X extends Throwable> void sneaky() throws X",
                "Optional<?> wildcard(Class<?> type)",
                "<A, B extends A> B cast(A a, int[][] grid)");

        MemberDescriptor max = member(scanned, "max");
        assertThat(max.isStatic()).isTrue();
        assertThat(max.parameters()).containsExactly(new ParameterInfo("a", "T", "Comparable"),
                new ParameterInfo("b", "T", "Comparable"));
        assertThat(member(scanned, "join").parameters().get(1))
                .isEqualTo(new ParameterInfo("parts", "Object...", "Object[]"));
        assertThat(member(scanned, "nested").erasedKey()).isEqualTo("nested(List[])");
        assertThat(member(scanned, "hook").visibility()).isEqualTo(Visibility.PROTECTED);
        assertThat(member(scanned, "cast").erasedKey()).isEqualTo("cast(Object,int[][])");
    }

    @Test
    void parameterNamesFallBackToArgN() {
        ScannedClass scanned = read("p/Names.java", """
                package p;
                public class Names {
                    public Names(int first, String second) {}
                }
                """).get("p.Names");

        assertThat(signatures(scanned)).containsExactly("Names(int arg0, String arg1)");
    }

    @Test
    void innerClassConstructorDropsOuterInstance() {
        Map<String, ScannedClass> types = read("p/Outer.java", """
                package p;
                public class Outer<T> {
                    public class Inner {
                        public Inner(String name) {}
                        public Outer<T> outer() { return Outer.this; }
                    }
                    public class GenericInner<U> {
                        public GenericInner(U value, T other) {}
                    }
                    public Outer<T>.GenericInner<String> make() { return null; }
                    public static class Nested { public Nested(int x) {} }
                }
                """, "-parameters");

        assertThat(signatures(types.get("p.Outer$Inner"))).containsExactly("Inner(String name)", "Outer<T> outer()");
        assertThat(signatures(types.get("p.Outer$GenericInner"))).containsExactly("GenericInner(U value, T other)");
        assertThat(member(types.get("p.Outer$Inner"), "Inner").parameters())
                .containsExactly(new ParameterInfo("name", "String", "String"));
        assertThat(signatures(types.get("p.Outer"))).contains("Outer<T>.GenericInner<String> make()");
        assertThat(signatures(types.get("p.Outer$Nested"))).containsExactly("Nested(int x)");
    }

    @Test
    void innerClassConstructorWithoutParametersAttribute() {
        ScannedClass inner = read("p/Outer.java", """
                package p;
                public class Outer {
                    public class Inner {
                        public Inner(String name, int count) {}
                    }
                }
                """).get("p.Outer$Inner");

        assertThat(signatures(inner)).containsExactly("Inner(String arg0, int arg1)");
    }

    @Test
    void readsEnumConstantsFieldsAndImplicitMethods() {
        ScannedClass scanned = read("p/Level.java", """
                package p;
                public enum Level {
                    LOW, HIGH { @Override public int weight() { return 2; } };
                    public static final int MAX = 2;
                    public final String label = "";
                    private Level() {}
                    public int weight() { return 1; }
                }
                """, "-parameters").get("p.Level");

        assertThat(signatures(scanned)).containsExactly("LOW", "HIGH", "static final int MAX", "final String label",
                "static Level[] values()", "static Level valueOf(String name)", "int weight()");
        assertThat(member(scanned, "LOW").kind()).isEqualTo(MemberKind.ENUM_CONSTANT);
        assertThat(member(scanned, "LOW").isStatic()).isTrue();
        assertThat(member(scanned, "MAX").kind()).isEqualTo(MemberKind.FIELD);
        assertThat(member(scanned, "label").isStatic()).isFalse();
    }

    @Test
    void interfaceMembersShowImplicitModifiers() {
        ScannedClass scanned = read("p/Api.java", """
                package p;
                public interface Api {
                    String NAME = "x";
                    void run();
                    default int size() { return 0; }
                    static Api create() { return null; }
                    private void helper() {}
                }
                """).get("p.Api");

        assertThat(signatures(scanned)).containsExactly("static final String NAME", "abstract void run()",
                "default int size()", "static Api create()");
    }

    @Test
    void annotationElementsAndRecordMembers() {
        Map<String, ScannedClass> types = read("p/Things.java", """
                package p;
                public class Things {
                    public @interface Tag { String value() default ""; int[] codes(); }
                    public record Pair<A, B>(A first, B second) {
                        public static final Pair<String, String> EMPTY = new Pair<>("", "");
                    }
                }
                """, "-parameters");

        assertThat(signatures(types.get("p.Things$Tag"))).containsExactly("abstract String value()",
                "abstract int[] codes()");
        assertThat(types.get("p.Things$Pair").type().typeParameters()).isEqualTo("<A, B>");
        assertThat(signatures(types.get("p.Things$Pair"))).contains(
                "static final Things.Pair<String, String> EMPTY",
                "Pair(A first, B second)",
                "A first()",
                "B second()");
    }

    @Test
    void deprecatedFromAnnotationOnTypesAndMembers() {
        ScannedClass scanned = read("p/Old.java", """
                package p;
                @Deprecated
                public class Old {
                    @Deprecated public int field;
                    @Deprecated(since = "2") public void method() {}
                    public void fresh() {}
                }
                """).get("p.Old");

        assertThat(scanned.type().deprecated()).isTrue();
        assertThat(member(scanned, "field").deprecated()).isTrue();
        assertThat(member(scanned, "method").deprecated()).isTrue();
        assertThat(member(scanned, "fresh").deprecated()).isFalse();
    }

    @Test
    void nestedTypeReferencesUseRelativeNames() {
        Map<String, ScannedClass> types = read("p/Shapes.java", """
                package p;
                import java.util.Map;
                public class Shapes {
                    public static class Registry {
                        public Entry register(Entry entry, Map.Entry<String, Entry> pair) { return entry; }
                        public record Entry(String name) {}
                    }
                }
                """, "-parameters");

        MemberDescriptor register = member(types.get("p.Shapes$Registry"), "register");
        assertThat(register.signature()).isEqualTo(
                "Shapes.Registry.Entry register(Shapes.Registry.Entry entry, Map.Entry<String, Shapes.Registry.Entry> pair)");
        assertThat(register.erasedParameterTypes()).containsExactly("Shapes.Registry.Entry", "Map.Entry");
        assertThat(signatures(types.get("p.Shapes$Registry$Entry"))).contains("Entry(String name)");
    }

    @Test
    void skipsModuleAndPackageInfo() {
        Map<String, byte[]> classes = InMemoryCompiler.compile(Map.of(
                "p/package-info.java", "@Deprecated package p;",
                "module-info.java", "module m { exports p; }",
                "p/A.java", "package p; public class A {}"));

        assertThat(reader.read(classes.get("p/package-info"))).isEmpty();
        assertThat(reader.read(classes.get("module-info"))).isEmpty();
        assertThat(reader.read(classes.get("p/A"))).isPresent();
    }

    @Test
    void rejectsInvalidClassFiles() {
        assertThatThrownBy(() -> reader.read(new byte[] {1, 2, 3}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid class file");
    }

    @Test
    void defaultPackageTypes() {
        Optional<ScannedClass> scanned = Optional.ofNullable(read("Top.java", """
                public class Top { public Top self() { return this; } }
                """).get("Top"));

        assertThat(scanned).isPresent();
        assertThat(signatures(scanned.get())).containsExactly("Top()", "Top self()");
        assertThat(scanned.get().type().entryPath()).isEqualTo("Top.class");
    }
}
