package io.github.vecluu.jdoc.source;

import static io.github.vecluu.jdoc.source.ParsedSources.descriptor;
import static io.github.vecluu.jdoc.source.ParsedSources.member;
import static io.github.vecluu.jdoc.source.ParsedSources.signature;
import static io.github.vecluu.jdoc.source.ParsedSources.type;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.vecluu.jdoc.model.DocSource;
import io.github.vecluu.jdoc.model.MemberDescriptor;
import io.github.vecluu.jdoc.model.MemberKind;
import io.github.vecluu.jdoc.model.ParameterInfo;
import io.github.vecluu.jdoc.model.TypeDescriptor;
import io.github.vecluu.jdoc.model.TypeKind;
import io.github.vecluu.jdoc.model.TypeName;
import io.github.vecluu.jdoc.model.Visibility;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class JavaSourceParserTest {

    private final JavaSourceParser parser = new JavaSourceParser();

    @AfterEach
    void close() {
        parser.close();
    }

    private ParsedSourceFile parse(String text) {
        return ParsedSources.parse(parser, "p/T.java", text);
    }

    private ParsedType parseType(String text) {
        return type(parse(text), "p.T");
    }

    @Test
    void methodSignaturesWithModifiers() {
        ParsedType type = parseType("""
                package p;
                public abstract class T {
                    public void a() {}
                    protected static int b(int x, long y) { return 0; }
                    public abstract String c();
                    public final synchronized native void d();
                    protected abstract void e() throws java.io.IOException, IllegalStateException;
                    private void f() {}
                    void g() {}
                }
                """);
        assertThat(signature(type, "a()")).isEqualTo("void a()");
        assertThat(signature(type, "b(int,long)")).isEqualTo("protected static int b(int x, long y)");
        assertThat(signature(type, "c()")).isEqualTo("abstract String c()");
        assertThat(signature(type, "d()")).isEqualTo("void d()");
        assertThat(signature(type, "e()")).isEqualTo("protected abstract void e() throws IOException, IllegalStateException");
        assertThat(descriptor(type, "f()").visibility()).isEqualTo(Visibility.PRIVATE);
        assertThat(descriptor(type, "g()").visibility()).isEqualTo(Visibility.PACKAGE);
        assertThat(descriptor(type, "b(int,long)").isStatic()).isTrue();
        assertThat(descriptor(type, "a()").isStatic()).isFalse();
    }

    @Test
    void genericsArraysAndVarargs() {
        ParsedType type = parseType("""
                package p;
                import java.util.*;
                public class T<E extends Number> {
                    public static <T extends Comparable<? super T>> T max(Collection<? extends T> c) { return null; }
                    public <K, V extends List<K>> Map<K, V[]> m(Map.Entry<K, V> e, int[][] grid, String... rest) { return null; }
                    public E first(E[] values, List<?> any) { return null; }
                    public int legacy(int values[])[] { return null; }
                    public void arrays(String[] notVarargs) {}
                    public <X extends Runnable & AutoCloseable> void both(X x) {}
                }
                """);
        MemberDescriptor max = descriptor(type, "max(Collection)");
        assertThat(max.signature()).isEqualTo("static <T extends Comparable<? super T>> T max(Collection<? extends T> c)");
        MemberDescriptor m = descriptor(type, "m(Map.Entry,int[][],String[])");
        assertThat(m.signature()).isEqualTo(
                "<K, V extends List<K>> Map<K, V[]> m(Map.Entry<K, V> e, int[][] grid, String... rest)");
        assertThat(m.parameters()).containsExactly(
                new ParameterInfo("e", "Map.Entry<K, V>", "Map.Entry"),
                new ParameterInfo("grid", "int[][]", "int[][]"),
                new ParameterInfo("rest", "String...", "String[]"));
        assertThat(descriptor(type, "first(Number[],List)").signature()).isEqualTo("E first(E[] values, List<?> any)");
        assertThat(descriptor(type, "legacy(int[])").signature()).isEqualTo("int[] legacy(int[] values)");
        assertThat(descriptor(type, "arrays(String[])").parameters().getFirst().isVarargs()).isFalse();
        assertThat(descriptor(type, "both(Runnable)").signature())
                .isEqualTo("<X extends Runnable & AutoCloseable> void both(X x)");
        assertThat(type.descriptor().typeParameters()).isEqualTo("<E extends Number>");
    }

    @Test
    void erasureOfTypeVariables() {
        ParsedType type = parseType("""
                package p;
                public class T<A, B extends CharSequence, C extends B> {
                    public <M extends java.util.List<A>> void m(A a, B b, C c, M m, A[] as, A... more) {}
                }
                """);
        assertThat(descriptor(type, "m(Object,CharSequence,CharSequence,List,Object[],Object[])").parameters())
                .extracting(ParameterInfo::erasedType)
                .containsExactly("Object", "CharSequence", "CharSequence", "List", "Object[]", "Object[]");
    }

    @Test
    void annotationsAreDroppedAndQualifiedNamesSimplified() {
        ParsedType type = parseType("""
                package p;
                public class T {
                    @Deprecated
                    public java.util.List<java.lang.@SuppressWarnings("x") String> names(final @SuppressWarnings("y") java.util.Map.Entry<String, ?> e) { return null; }
                }
                """);
        MemberDescriptor names = descriptor(type, "names(Map.Entry)");
        assertThat(names.signature()).isEqualTo("List<String> names(Map.Entry<String, ?> e)");
        assertThat(names.deprecated()).isTrue();
    }

    @Test
    void constructorsAndFields() {
        ParsedType type = parseType("""
                package p;
                public class T {
                    public static final int MAX = 1;
                    protected volatile String name;
                    int a, b[];
                    public T() {}
                    protected <X> T(X x) throws Exception {}
                    private T(int i) {}
                }
                """);
        assertThat(signature(type, "MAX")).isEqualTo("static final int MAX");
        assertThat(descriptor(type, "MAX").kind()).isEqualTo(MemberKind.FIELD);
        assertThat(descriptor(type, "MAX").isStatic()).isTrue();
        assertThat(signature(type, "name")).isEqualTo("protected String name");
        assertThat(signature(type, "a")).isEqualTo("int a");
        assertThat(signature(type, "b")).isEqualTo("int[] b");
        assertThat(signature(type, "T()")).isEqualTo("T()");
        assertThat(signature(type, "T(Object)")).isEqualTo("protected <X> T(X x) throws Exception");
        assertThat(descriptor(type, "T(int)").visibility()).isEqualTo(Visibility.PRIVATE);
        assertThat(descriptor(type, "T()").kind()).isEqualTo(MemberKind.CONSTRUCTOR);
        assertThat(type.members()).noneMatch(ParsedMember::implicit);
    }

    @Test
    void defaultConstructorHasTheClassAccess() {
        ParsedSourceFile file = parse("""
                package p;
                public class T {
                    protected static class P {}
                    private class Q {}
                }
                """);
        ParsedMember implicit = member(type(file, "p.T"), "T()");
        assertThat(implicit.implicit()).isTrue();
        assertThat(implicit.descriptor().visibility()).isEqualTo(Visibility.PUBLIC);
        assertThat(implicit.descriptor().range()).isEmpty();
        assertThat(implicit.doc().inheritsDoc()).isFalse();
        assertThat(signature(type(file, "p.T$P"), "P()")).isEqualTo("protected P()");
        assertThat(descriptor(type(file, "p.T$Q"), "Q()").visibility()).isEqualTo(Visibility.PRIVATE);
    }

    @Test
    void interfaceMembersAreImplicitlyPublic() {
        ParsedSourceFile file = parse("""
                package p;
                public interface T<E> extends Comparable<T<E>>, java.io.Serializable {
                    int LIMIT = 3;
                    E get();
                    default E orElse(E other) { return other; }
                    static <E> T<E> of(E e) { return null; }
                    private void helper() {}
                    class Impl {}
                    interface Nested {}
                }
                """);
        ParsedType type = type(file, "p.T");
        assertThat(type.descriptor().kind()).isEqualTo(TypeKind.INTERFACE);
        assertThat(type.descriptor().modifiers()).isEqualTo("public");
        assertThat(type.descriptor().supertypes())
                .containsExactly(TypeName.of("java.lang.Comparable"), TypeName.of("java.io.Serializable"));
        assertThat(signature(type, "LIMIT")).isEqualTo("static final int LIMIT");
        assertThat(descriptor(type, "LIMIT").visibility()).isEqualTo(Visibility.PUBLIC);
        assertThat(descriptor(type, "LIMIT").isStatic()).isTrue();
        assertThat(signature(type, "get()")).isEqualTo("abstract E get()");
        assertThat(signature(type, "orElse(Object)")).isEqualTo("default E orElse(E other)");
        assertThat(signature(type, "of(Object)")).isEqualTo("static <E> T<E> of(E e)");
        assertThat(descriptor(type, "get()").visibility()).isEqualTo(Visibility.PUBLIC);
        assertThat(descriptor(type, "helper()").visibility()).isEqualTo(Visibility.PRIVATE);
        assertThat(signature(type, "helper()")).isEqualTo("void helper()");
        TypeDescriptor impl = type(file, "p.T$Impl").descriptor();
        assertThat(impl.visibility()).isEqualTo(Visibility.PUBLIC);
        assertThat(impl.modifiers()).isEqualTo("public static");
        assertThat(type(file, "p.T$Nested").descriptor().modifiers()).isEqualTo("public static");
        assertThat(type.members()).noneMatch(ParsedMember::implicit);
    }

    @Test
    void annotationElements() {
        ParsedType type = parseType("""
                package p;
                public @interface T {
                    String value() default "";
                    int[] codes();
                    String NAME = "n";
                }
                """);
        assertThat(type.descriptor().kind()).isEqualTo(TypeKind.ANNOTATION);
        assertThat(type.descriptor().supertypes()).containsExactly(TypeName.of("java.lang.annotation.Annotation"));
        assertThat(signature(type, "value()")).isEqualTo("abstract String value()");
        assertThat(signature(type, "codes()")).isEqualTo("abstract int[] codes()");
        assertThat(descriptor(type, "codes()").visibility()).isEqualTo(Visibility.PUBLIC);
        assertThat(signature(type, "NAME")).isEqualTo("static final String NAME");
    }

    @Test
    void enumConstantsAndImplicitMethods() {
        ParsedSourceFile file = parse("""
                package p;
                /** Colors. */
                public enum T implements Runnable {
                    /** Red. */
                    RED,
                    @Deprecated GREEN(2),
                    BLUE { public void run() {} };
                    public static final T DEFAULT = RED;
                    T() {}
                    T(int x) {}
                    public void run() {}
                }
                """);
        ParsedType type = type(file, "p.T");
        assertThat(type.descriptor().supertypes())
                .containsExactly(TypeName.of("java.lang.Enum"), TypeName.of("java.lang.Runnable"));
        MemberDescriptor red = descriptor(type, "RED");
        assertThat(red.kind()).isEqualTo(MemberKind.ENUM_CONSTANT);
        assertThat(red.signature()).isEqualTo("RED");
        assertThat(red.visibility()).isEqualTo(Visibility.PUBLIC);
        assertThat(red.isStatic()).isTrue();
        assertThat(red.firstSentence()).isEqualTo("Red.");
        assertThat(descriptor(type, "GREEN").deprecated()).isTrue();
        assertThat(descriptor(type, "BLUE").kind()).isEqualTo(MemberKind.ENUM_CONSTANT);
        assertThat(descriptor(type, "DEFAULT").kind()).isEqualTo(MemberKind.FIELD);
        assertThat(descriptor(type, "T()").visibility()).isEqualTo(Visibility.PRIVATE);
        ParsedMember values = member(type, "values()");
        assertThat(values.implicit()).isTrue();
        assertThat(values.descriptor().signature()).isEqualTo("static T[] values()");
        assertThat(values.descriptor().firstSentence()).startsWith("Returns an array containing the constants");
        ParsedMember valueOf = member(type, "valueOf(String)");
        assertThat(valueOf.descriptor().signature()).isEqualTo("static T valueOf(String name)");
        assertThat(valueOf.descriptor().isStatic()).isTrue();
        assertThat(valueOf.doc().blockTags()).hasSize(4);
        assertThat(file.types()).extracting(ParsedType::name).containsExactly(TypeName.of("p.T"));
    }

    @Test
    void recordImplicitMembers() {
        ParsedSourceFile file = parse("""
                package p;
                /**
                 * A pair.
                 *
                 * @param left the left value
                 * @param right the right values
                 */
                public record T<L>(L left, int... right) implements Comparable<T<L>> {
                    static final int ZERO = 0;
                    public int compareTo(T<L> o) { return 0; }
                }
                """);
        ParsedType type = type(file, "p.T");
        assertThat(type.descriptor().kind()).isEqualTo(TypeKind.RECORD);
        assertThat(type.descriptor().supertypes())
                .containsExactly(TypeName.of("java.lang.Record"), TypeName.of("java.lang.Comparable"));
        ParsedMember canonical = member(type, "T(Object,int[])");
        assertThat(canonical.implicit()).isTrue();
        assertThat(canonical.descriptor().signature()).isEqualTo("T(L left, int... right)");
        assertThat(canonical.doc().blockTags()).containsExactly("@param left the left value",
                "@param right the right values");
        ParsedMember left = member(type, "left()");
        assertThat(left.descriptor().signature()).isEqualTo("L left()");
        assertThat(left.descriptor().visibility()).isEqualTo(Visibility.PUBLIC);
        assertThat(left.doc().firstSentence()).isEqualTo("Returns the value of the `left` record component.");
        assertThat(left.doc().blockTags()).containsExactly("@return the left value");
        assertThat(signature(type, "right()")).isEqualTo("int[] right()");
        assertThat(type.members()).filteredOn(ParsedMember::implicit).extracting(m -> m.descriptor().signature())
                .containsExactly("T(L left, int... right)", "String toString()", "int hashCode()",
                        "boolean equals(Object o)", "L left()", "int[] right()");
        ParsedMember equals = member(type, "equals(Object)");
        assertThat(equals.descriptor().visibility()).isEqualTo(Visibility.PUBLIC);
        assertThat(equals.descriptor().isStatic()).isFalse();
        assertThat(equals.doc().blockTags()).first().isEqualTo("@param o the object with which to compare");
        assertThat(type.members()).noneMatch(m -> m.descriptor().kind() == MemberKind.FIELD
                && !m.descriptor().isStatic());
        assertThat(signature(type, "ZERO")).isEqualTo("static final int ZERO");
    }

    @Test
    void recordDeclaredMembersWin() {
        ParsedSourceFile file = parse("""
                package p;
                record T(int x, String y) {
                    /** Compact. */
                    T {
                        if (x < 0) throw new IllegalArgumentException();
                    }
                    /** The x. @return x */
                    public int x() { return x; }
                    @Override public String toString() { return "T"; }
                }
                """);
        ParsedType type = type(file, "p.T");
        ParsedMember canonical = member(type, "T(int,String)");
        assertThat(canonical.implicit()).isFalse();
        assertThat(canonical.descriptor().signature()).isEqualTo("T(int x, String y)");
        assertThat(canonical.descriptor().visibility()).isEqualTo(Visibility.PACKAGE);
        assertThat(canonical.descriptor().firstSentence()).isEqualTo("Compact.");
        assertThat(member(type, "x()").implicit()).isFalse();
        assertThat(member(type, "y()").implicit()).isTrue();
        assertThat(member(type, "toString()").implicit()).isFalse();
        assertThat(type.members()).filteredOn(m -> m.descriptor().name().equals("toString")).hasSize(1);
        assertThat(type.descriptor().visibility()).isEqualTo(Visibility.PACKAGE);
        assertThat(type.descriptor().modifiers()).isEmpty();
    }

    @Test
    void typeModifiersAndKinds() {
        ParsedSourceFile file = parse("""
                package p;
                public abstract sealed class T permits T.A, T.B {
                    public static final class A extends T {}
                    protected static non-sealed class B extends T {}
                    public class Inner {}
                    enum E { X }
                    record R() {}
                    private interface I {}
                }
                """);
        assertThat(type(file, "p.T").descriptor().modifiers()).isEqualTo("public abstract sealed");
        assertThat(type(file, "p.T$A").descriptor().modifiers()).isEqualTo("public static final");
        assertThat(type(file, "p.T$B").descriptor().modifiers()).isEqualTo("protected static non-sealed");
        assertThat(type(file, "p.T$B").descriptor().visibility()).isEqualTo(Visibility.PROTECTED);
        assertThat(type(file, "p.T$Inner").descriptor().modifiers()).isEqualTo("public");
        assertThat(type(file, "p.T$E").descriptor().modifiers()).isEqualTo("static");
        assertThat(type(file, "p.T$E").descriptor().kind()).isEqualTo(TypeKind.ENUM);
        assertThat(type(file, "p.T$R").descriptor().modifiers()).isEqualTo("static");
        assertThat(type(file, "p.T$I").descriptor().modifiers()).isEqualTo("private static");
        assertThat(type(file, "p.T$A").descriptor().supertypes()).containsExactly(TypeName.of("p.T"));
        assertThat(file.types()).extracting(ParsedType::name).extracting(TypeName::binaryName)
                .containsExactly("p.T", "p.T$A", "p.T$B", "p.T$Inner", "p.T$E", "p.T$R", "p.T$I");
    }

    @Test
    void localAndAnonymousClassesAreSkipped() {
        ParsedSourceFile file = parse("""
                package p;
                public class T {
                    Runnable r = new Runnable() { public void run() {} };
                    void m() {
                        class Local {}
                        new Object() { };
                    }
                    static { class InInit {} }
                }
                """);
        assertThat(file.types()).extracting(ParsedType::name).containsExactly(TypeName.of("p.T"));
    }

    @Test
    void nestedTypesAndTheirMembers() {
        ParsedSourceFile file = parse("""
                package p;
                import java.util.Map;
                public class T {
                    public static class A {
                        public static class B {
                            public A owner() { return null; }
                            public B self() { return null; }
                            public Map.Entry<A, B> entry() { return null; }
                        }
                    }
                }
                """);
        ParsedType b = type(file, "p.T$A$B");
        assertThat(b.descriptor().entryPath()).isEqualTo("p/T.java");
        assertThat(signature(b, "owner()")).isEqualTo("T.A owner()");
        assertThat(signature(b, "self()")).isEqualTo("T.A.B self()");
        assertThat(signature(b, "entry()")).isEqualTo("Map.Entry<T.A, T.A.B> entry()");
        assertThat(descriptor(b, "self()").declaringType()).isEqualTo(TypeName.of("p.T$A$B"));
    }

    @Test
    void supertypeResolution() {
        ParsedSourceFile file = parse("""
                package p;
                import java.util.AbstractList;
                import java.util.Map.Entry;
                import java.util.concurrent.*;
                public class T extends AbstractList<String> implements Entry<String, String>, Runnable, Callable<String>, Other, java.io.Closeable, T.Inner {
                    public interface Inner {}
                    static class Sub extends T implements Inner {}
                }
                """);
        assertThat(type(file, "p.T").descriptor().supertypes()).extracting(TypeName::binaryName).containsExactly(
                "java.util.AbstractList", "java.util.Map$Entry", "java.lang.Runnable",
                "java.util.concurrent.Callable", "java.util.concurrent.Other", "java.io.Closeable", "p.T$Inner");
        assertThat(type(file, "p.T$Sub").descriptor().supertypes()).extracting(TypeName::binaryName)
                .containsExactly("p.T", "p.T$Inner");
    }

    @Test
    void knownTypesRefineResolution() {
        String text = """
                package p;
                import java.util.*;
                import java.util.concurrent.*;
                public class T extends Base implements Callable<String> {}
                """;
        ParsedSourceFile guessed = parse(text);
        assertThat(type(guessed, "p.T").descriptor().supertypes()).extracting(TypeName::binaryName)
                .containsExactly("java.util.Base", "java.util.Callable");
        ParsedSourceFile known = parser.parse(new JavaSource("p/T.java", text, DocSource.SOURCES),
                KnownTypes.of(List.of(TypeName.of("p.Base"), TypeName.of("java.util.concurrent.Callable"))));
        assertThat(type(known, "p.T").descriptor().supertypes()).extracting(TypeName::binaryName)
                .containsExactly("p.Base", "java.util.concurrent.Callable");
    }

    @Test
    void deprecatedFromAnnotationOrTag() {
        ParsedSourceFile file = parse("""
                package p;
                /**
                 * Old.
                 * @deprecated gone
                 */
                public class T {
                    @java.lang.Deprecated(since = "1") public void a() {}
                    /** @deprecated use b2 */ public void b() {}
                    public void c() {}
                }
                @Deprecated class U {}
                """);
        ParsedType type = type(file, "p.T");
        assertThat(type.descriptor().deprecated()).isTrue();
        assertThat(descriptor(type, "a()").deprecated()).isTrue();
        assertThat(descriptor(type, "b()").deprecated()).isTrue();
        assertThat(descriptor(type, "c()").deprecated()).isFalse();
        assertThat(type(file, "p.U").descriptor().deprecated()).isTrue();
    }

    @Test
    void docsAndFirstSentences() {
        ParsedType type = parseType("""
                package p;
                /**
                 * The type, e.g. a thing. More.
                 * @since 2
                 */
                public class T {
                    /** {@inheritDoc} */
                    public String toString() { return ""; }
                    public int hashCode() { return 0; }
                    /** Returns {@code 1}. */
                    public int one() { return 1; }
                }
                """);
        assertThat(type.descriptor().firstSentence()).isEqualTo("The type, e.g. a thing.");
        assertThat(type.doc().blockTags()).containsExactly("@since 2");
        assertThat(member(type, "toString()").doc().inheritsDoc()).isTrue();
        assertThat(member(type, "hashCode()").doc().inheritsDoc()).isTrue();
        assertThat(member(type, "hashCode()").descriptor().firstSentence()).isEmpty();
        assertThat(member(type, "one()").descriptor().firstSentence()).isEqualTo("Returns `1`.");
    }

    @Test
    void sourceRanges() {
        String text = """
                package p;

                /** Type doc. */
                @SuppressWarnings("x")
                public class T {
                    /**
                     * Doc of m.
                     */
                    @Override
                    public String toString() {
                        return "";
                    }

                    // a line comment
                    public int f = 1;
                }
                """;
        ParsedType type = ParsedSources.type(parse(text), "p.T");
        assertThat(type.descriptor().declarationRange().orElseThrow().slice(text))
                .startsWith("/** Type doc. */\n@SuppressWarnings").endsWith("public int f = 1;\n}");
        MemberDescriptor method = descriptor(type, "toString()");
        assertThat(method.docRange().orElseThrow().slice(text)).isEqualTo("/**\n     * Doc of m.\n     */");
        assertThat(method.range().orElseThrow().slice(text))
                .startsWith("/**\n     * Doc of m.").contains("@Override").endsWith("return \"\";\n    }");
        MemberDescriptor field = descriptor(type, "f");
        assertThat(field.docRange()).isEmpty();
        assertThat(field.range().orElseThrow().slice(text)).isEqualTo("public int f = 1;");
    }

    @Test
    void packageInfo() {
        String text = """
                /**
                 * The package.
                 *
                 * @since 3
                 */
                @Deprecated
                package a.b;
                """;
        ParsedSourceFile file = ParsedSources.parse(parser, "a/b/package-info.java", text);
        assertThat(file.packageName()).isEqualTo("a.b");
        assertThat(file.types()).isEmpty();
        assertThat(file.packageDoc().orElseThrow().firstSentence()).isEqualTo("The package.");
        assertThat(file.packageDoc().orElseThrow().blockTags()).containsExactly("@since 3");
        assertThat(file.packageDocRange().orElseThrow().slice(text)).startsWith("/**").endsWith("*/");
        assertThat(ParsedSources.parse(parser, "a/b/package-info.java", "package a.b;").packageDoc()).isEmpty();
    }

    @Test
    void moduleInfoHasNoTypes() {
        ParsedSourceFile file = ParsedSources.parse(parser, "module-info.java", """
                /** The module. */
                module my.mod {
                    requires java.base;
                    exports a.b;
                }
                """);
        assertThat(file.types()).isEmpty();
        assertThat(file.problems()).isEmpty();
        assertThat(file.packageName()).isEmpty();
    }

    @Test
    void defaultPackage() {
        ParsedSourceFile file = ParsedSources.parse(parser, "T.java", "public class T { public void m() {} }");
        assertThat(type(file, "T").descriptor().name().packageName()).isEmpty();
    }

    @Test
    void syntaxErrorsAreReportedNotThrown() {
        ParsedSourceFile file = parse("""
                package p;
                public class T {
                    public void ok() {}
                    public void broken( { }
                }
                """);
        assertThat(file.hasProblems()).isTrue();
        assertThat(file.problems().getFirst()).matches("\\d+:\\d+: .*");
        assertThat(file.types()).isNotEmpty();
    }

    @Test
    void decompiledEntryPathsAreClassEntries() {
        ParsedSourceFile file = parser.parse(new JavaSource("a/T.java", """
                package a;
                public class T { public static class N {} }
                """, DocSource.DECOMPILED));
        assertThat(type(file, "a.T").descriptor().entryPath()).isEqualTo("a/T.class");
        assertThat(type(file, "a.T$N").descriptor().entryPath()).isEqualTo("a/T$N.class");
        assertThat(type(file, "a.T").descriptor().docSource()).isEqualTo(DocSource.DECOMPILED);
    }

    @Test
    void sourceValidation() {
        assertThatThrownBy(() -> new JavaSource("a/T.kt", "", DocSource.SOURCES))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JavaSource("a/T.java", "", DocSource.JAVADOC))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new JavaSource("a/b/T.java", "", DocSource.JDK).fileName()).isEqualTo("T.java");
    }

    @Test
    void innerClassOfGenericOuter() {
        ParsedType type = parseType("""
                package p;
                public class T<E> {
                    public class Node {}
                    public T<E>.Node head() { return null; }
                }
                """);
        assertThat(signature(type, "head()")).isEqualTo("T<E>.Node head()");
    }
}
