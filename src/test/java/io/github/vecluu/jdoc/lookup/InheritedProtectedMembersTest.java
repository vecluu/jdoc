package io.github.vecluu.jdoc.lookup;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.vecluu.jdoc.model.ArtifactKey;
import io.github.vecluu.jdoc.model.DocSource;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Inherited protected members are hidden on types that users cannot subclass. */
class InheritedProtectedMembersTest {

    private static final String OBJECT = """
            package java.lang;

            /** The root. */
            public class Object {
                public Object() {
                }

                public final native Class<?> getClass();

                public native int hashCode();

                protected native Object clone() throws CloneNotSupportedException;

                @Deprecated(since = "9", forRemoval = true)
                protected void finalize() throws Throwable {
                }
            }
            """;

    private static final String ENUM = """
            package java.lang;

            /** Base of enums. */
            public abstract class Enum<E extends Enum<E>> {
                protected Enum(String name, int ordinal) {
                }

                public final String name() {
                    return null;
                }

                protected final Object clone() throws CloneNotSupportedException {
                    throw new CloneNotSupportedException();
                }
            }
            """;

    private static final String RECORD = """
            package java.lang;

            /** Base of records. */
            public abstract class Record {
                protected Record() {
                }
            }
            """;

    @TempDir
    Path tmp;

    private LookupIndex index;
    private LookupServices services;

    @BeforeEach
    void setUp() {
        index = LookupIndex.open(tmp);
        Map<String, String> jdk = new LinkedHashMap<>();
        jdk.put("java/lang/Object.java", OBJECT);
        jdk.put("java/lang/Enum.java", ENUM);
        jdk.put("java/lang/Record.java", RECORD);
        index.addSources(ArtifactKey.jdk(25), DocSource.JDK, "java.base/", jdk);
        Map<String, String> library = new LinkedHashMap<>();
        library.put("p/Open.java", """
                package p;

                public class Open {
                    protected void hook() {
                    }

                    public void run() {
                    }
                }
                """);
        library.put("p/Closed.java", """
                package p;

                public final class Closed extends Open {
                    protected int ownProtected;
                }
                """);
        library.put("p/Copyable.java", """
                package p;

                public final class Copyable {
                    @Override
                    public Object clone() {
                        return this;
                    }
                }
                """);
        library.put("p/Color.java", """
                package p;

                public enum Color {
                    RED
                }
                """);
        library.put("p/Pair.java", """
                package p;

                public record Pair(int left) {
                }
                """);
        library.put("p/Shape.java", """
                package p;

                public sealed class Shape permits Square {
                }
                """);
        library.put("p/Square.java", """
                package p;

                public non-sealed class Square extends Shape {
                }
                """);
        library.put("p/Sized.java", """
                package p;

                public interface Sized {
                    int size();
                }
                """);
        index.addSources("com.acme:p:1.0", library);
        services = index.services();
    }

    @AfterEach
    void tearDown() {
        services.close();
        index.close();
    }

    @Test
    void finalClassHidesInheritedProtectedMembersButKeepsItsOwn() {
        ClassView view = view("p.Closed");

        assertThat(inheritedNames(view)).containsExactly("Open: run", "Object: getClass, hashCode");
        assertThat(view.members()).extracting(member -> member.descriptor().name()).contains("ownProtected");
        assertThat(services.typeMembers().inheritedForSubclassesOnly(type("p.Closed"), index.scope()))
                .extracting(MemberMatch::reference)
                .containsExactly("p.Open#hook()", "java.lang.Object#clone()", "java.lang.Object#finalize()");
    }

    @Test
    void publicOverrideOfAProtectedMemberStaysVisibleAndHidesTheProtectedOne() {
        assertThat(inheritedNames(view("p.Copyable"))).containsExactly("Object: getClass, hashCode");
        assertThat(found("p.Copyable#clone").members()).extracting(MemberMatch::inherited).containsExactly(false);
        assertThat(services.typeMembers().inheritedForSubclassesOnly(type("p.Copyable"), index.scope()))
                .extracting(MemberMatch::reference).containsExactly("java.lang.Object#finalize()");
    }

    @Test
    void enumsAndRecordsHideInheritedProtectedMembers() {
        assertThat(inheritedNames(view("p.Color"))).containsExactly("Enum: name", "Object: getClass, hashCode");
        assertThat(inheritedNames(view("p.Pair"))).containsExactly("Object: getClass");
    }

    @Test
    void sealedClassHidesThemAndItsNonSealedSubclassShowsThem() {
        assertThat(inheritedNames(view("p.Shape"))).containsExactly("Object: getClass, hashCode");
        assertThat(inheritedNames(view("p.Square")))
                .containsExactly("Object: getClass, hashCode, clone, finalize");
    }

    @Test
    void nonFinalClassKeepsInheritedProtectedMembersIncludingDeprecatedFinalize() {
        assertThat(inheritedNames(view("p.Open"))).containsExactly("Object: getClass, hashCode, clone, finalize");
        assertThat(services.typeMembers().inheritedForSubclassesOnly(type("p.Open"), index.scope())).isEmpty();
        assertThat(found("p.Open#finalize").members()).singleElement()
                .satisfies(match -> assertThat(match.descriptor().deprecated()).isTrue());
    }

    @Test
    void interfacesInheritNothingProtected() {
        assertThat(view("p.Sized").inherited()).isEmpty();
        assertThat(TypeMembers.canBeSubclassed(type("p.Sized"))).isTrue();
    }

    @Test
    void methodLookupOfAHiddenProtectedMemberIsNotFoundAndNamesIt() {
        MemberLookup lookup = services.memberLookup().lookup("p.Closed#clone", index.scope());

        assertThat(lookup).isInstanceOfSatisfying(MemberLookup.MemberNotFound.class, notFound -> {
            assertThat(notFound.protectedInherited()).extracting(MemberMatch::reference)
                    .containsExactly("java.lang.Object#clone()");
            assertThat(notFound.suggestions()).isEmpty();
        });
        assertThat(services.memberLookup().lookup("p.Closed#hook()", index.scope()))
                .isInstanceOfSatisfying(MemberLookup.MemberNotFound.class, notFound ->
                        assertThat(notFound.protectedInherited()).extracting(MemberMatch::reference)
                                .containsExactly("p.Open#hook()"));
        assertThat(found("p.Closed#run").members()).extracting(MemberMatch::reference).containsExactly("p.Open#run()");
    }

    @Test
    void unrelatedMissingMemberCarriesNoProtectedMatches() {
        assertThat(services.memberLookup().lookup("p.Closed#nothing", index.scope()))
                .isInstanceOfSatisfying(MemberLookup.MemberNotFound.class, notFound ->
                        assertThat(notFound.protectedInherited()).isEmpty());
    }

    private List<String> inheritedNames(ClassView view) {
        return view.inherited().stream()
                .map(group -> group.declaringType().name().simpleName() + ": " + String.join(", ", group.names()))
                .toList();
    }

    private LocatedType type(String binaryName) {
        return ((TypeLookup.Found) services.typeResolver().resolve(binaryName, index.scope())).type();
    }

    private ClassView view(String binaryName) {
        return services.classView().view(type(binaryName), index.scope());
    }

    private MemberLookup.Found found(String reference) {
        return (MemberLookup.Found) services.memberLookup().lookup(reference, index.scope());
    }
}
