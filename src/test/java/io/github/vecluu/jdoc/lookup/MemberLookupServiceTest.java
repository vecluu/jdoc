package io.github.vecluu.jdoc.lookup;

import static io.github.vecluu.jdoc.lookup.LookupIndex.constructor;
import static io.github.vecluu.jdoc.lookup.LookupIndex.field;
import static io.github.vecluu.jdoc.lookup.LookupIndex.method;
import static io.github.vecluu.jdoc.lookup.LookupIndex.publicClass;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MemberLookupServiceTest {

    @TempDir
    Path tmp;

    private LookupIndex index;
    private LookupServices services;

    @BeforeEach
    void setUp() {
        index = LookupIndex.open(tmp);
        SampleSources.addTo(index);
        index.add("com.acme:generic:1.0", List.of(publicClass("g.Box"), publicClass("g.Dup"), publicClass("h.Dup"),
                        publicClass("g.Odd"), publicClass("g.Plain")),
                constructor("g.Odd"),
                constructor("g.Odd", "int size"),
                method("g.Odd", "Odd", "String name"),
                constructor("g.Plain", "int size"),
                field("g.Plain", "Plain"),
                method("g.Box", "put", "T value:Object"),
                method("g.Box", "put", "T value:Object", "int times"),
                method("g.Box", "all", "List<? extends T> values:List"),
                method("g.Box", "each", "String... values"));
        services = index.services();
    }

    @AfterEach
    void tearDown() {
        services.close();
        index.close();
    }

    @Test
    void findsEveryOverloadOnTheTypeItselfWithoutParentheses() {
        MemberLookup.Found found = found("g.Box#put");

        assertThat(found.members()).extracting(MemberMatch::reference)
                .containsExactly("g.Box#put(Object)", "g.Box#put(Object,int)");
        assertThat(found.members()).noneMatch(MemberMatch::inherited);
    }

    @Test
    void selectsOverloadsByErasedOrDeclaredParameterTypes() {
        assertThat(found("g.Box#put(T)").members()).extracting(MemberMatch::reference)
                .containsExactly("g.Box#put(Object)");
        assertThat(found("g.Box#put(Object, int)").members()).extracting(MemberMatch::reference)
                .containsExactly("g.Box#put(Object,int)");
        assertThat(found("Box#all(List<String>)").members()).hasSize(1);
        assertThat(found("Box#each(String...)").members()).hasSize(1);
        assertThat(found("Box#each(String[])").members()).hasSize(1);
    }

    @Test
    void ownMembersWinOverInheritedOnes() {
        MemberLookup.Found found = found("a.Impl#size");

        assertThat(found.members()).extracting(MemberMatch::reference).containsExactly("a.Impl#size(int)");
    }

    @Test
    void inheritedMembersAreFoundWithTheirDeclaringType() {
        MemberLookup.Found sizeTwice = found("a.Impl#size(int,int)");
        assertThat(sizeTwice.type().name().simpleName()).isEqualTo("Impl");
        assertThat(sizeTwice.members()).singleElement().satisfies(match -> {
            assertThat(match.inherited()).isTrue();
            assertThat(match.declaringType().name().simpleName()).isEqualTo("Base");
        });

        assertThat(found("Impl#name").members()).extracting(MemberMatch::reference)
                .containsExactly("a.Base#name()");
        assertThat(found("Impl#compareTo").members()).extracting(MemberMatch::reference)
                .containsExactly("a.Named#compareTo(Named)");
        assertThat(found("Impl#hashCode").members()).extracting(MemberMatch::reference)
                .containsExactly("java.lang.Object#hashCode()");
        assertThat(found("Impl#helper").members()).extracting(MemberMatch::reference)
                .containsExactly("a.Base#helper()");
    }

    @Test
    void constructorsAreFoundByTypeNameOrInitAndNeverInherited() {
        assertThat(found("a.Impl#Impl").members()).extracting(MemberMatch::reference)
                .containsExactly("a.Impl#Impl()");
        assertThat(found("a.Impl#<init>()").members()).hasSize(1);
        assertThat(found("a.Other#Other").members()).extracting(MemberMatch::reference)
                .as("implicit default constructor").containsExactly("a.Other#Other()");
        assertThat(services.memberLookup().lookup("a.Named#Named", index.scope()))
                .isInstanceOf(MemberLookup.MemberNotFound.class);
    }

    @Test
    void methodNamedLikeItsTypeWinsOverTheConstructors() {
        assertThat(found("g.Odd#Odd").members()).extracting(MemberMatch::reference)
                .containsExactly("g.Odd#Odd(String)");
        assertThat(found("g.Odd#Odd(String)").members()).extracting(MemberMatch::reference)
                .containsExactly("g.Odd#Odd(String)");
    }

    @Test
    void typeNameWithArgumentsNoMethodMatchesFallsBackToTheConstructors() {
        assertThat(found("g.Odd#Odd(int)").members()).extracting(MemberMatch::reference)
                .containsExactly("g.Odd#Odd(int)");
        assertThat(found("g.Odd#Odd()").members()).extracting(MemberMatch::reference)
                .containsExactly("g.Odd#Odd()");
        assertThat(services.memberLookup().lookup("g.Odd#Odd(long)", index.scope()))
                .isInstanceOfSatisfying(MemberLookup.MemberNotFound.class, notFound -> assertThat(
                        notFound.suggestions()).containsExactly("g.Odd#Odd()", "g.Odd#Odd(int)"));
    }

    @Test
    void printedConstructorReferencesRoundTrip() {
        for (String printed : found("g.Odd#<init>").members().stream().map(MemberMatch::reference).toList()) {
            assertThat(found(printed).members()).extracting(MemberMatch::reference).containsExactly(printed);
        }
    }

    @Test
    void typeNameWithArgumentsFindsTheConstructorOfATypeWithoutSuchMethod() {
        assertThat(found("a.Impl#Impl()").members()).extracting(MemberMatch::reference)
                .containsExactly("a.Impl#Impl()");
        assertThat(found("g.Plain#Plain(int)").members()).extracting(MemberMatch::reference)
                .containsExactly("g.Plain#Plain(int)");
    }

    @Test
    void initAlwaysMeansTheConstructors() {
        assertThat(found("g.Odd#<init>").members()).extracting(MemberMatch::reference)
                .containsExactly("g.Odd#Odd()", "g.Odd#Odd(int)");
        assertThat(found("g.Odd#<init>(int)").members()).extracting(MemberMatch::reference)
                .containsExactly("g.Odd#Odd(int)");
    }

    @Test
    void onlyAMethodTurnsTheTypeNameIntoAMethodName() {
        assertThat(found("g.Plain#Plain").members()).extracting(MemberMatch::reference)
                .as("a field named like the type does not").containsExactly("g.Plain#Plain(int)");
    }

    @Test
    void staticInterfaceMethodsAreNotInherited() {
        assertThat(services.memberLookup().lookup("a.Impl#of", index.scope()))
                .isInstanceOf(MemberLookup.MemberNotFound.class);
        assertThat(found("a.Named#of").members()).hasSize(1);
    }

    @Test
    void notFoundSuggestsOverloadsOrSimilarNames() {
        MemberLookup wrongParams = services.memberLookup().lookup("a.Impl#size(String)", index.scope());
        assertThat(wrongParams).isInstanceOfSatisfying(MemberLookup.MemberNotFound.class, notFound -> {
            assertThat(notFound.type().name().simpleName()).isEqualTo("Impl");
            assertThat(notFound.suggestions()).containsExactly("a.Impl#size(int)", "a.Base#size(int,int)");
        });

        MemberLookup misspelled = services.memberLookup().lookup("a.Impl#siz", index.scope());
        assertThat(misspelled).isInstanceOfSatisfying(MemberLookup.MemberNotFound.class,
                notFound -> assertThat(notFound.suggestions()).contains("a.Impl#size(int)"));
    }

    @Test
    void unresolvedTypesAndBadInputAreValues() {
        assertThat(services.memberLookup().lookup("Dup#x", index.scope()))
                .isInstanceOfSatisfying(MemberLookup.TypeNotResolved.class,
                        result -> assertThat(result.typeLookup()).isInstanceOf(TypeLookup.Ambiguous.class));
        assertThat(services.memberLookup().lookup("Nope#x", index.scope()))
                .isInstanceOfSatisfying(MemberLookup.TypeNotResolved.class,
                        result -> assertThat(result.typeLookup()).isInstanceOf(TypeLookup.NotFound.class));
        assertThat(services.memberLookup().lookup("Box", index.scope())).isInstanceOf(MemberLookup.Invalid.class);
        assertThat(services.memberLookup().lookup("Box#put(", index.scope()))
                .isInstanceOf(MemberLookup.Invalid.class);
    }

    private MemberLookup.Found found(String input) {
        MemberLookup lookup = services.memberLookup().lookup(input, index.scope());
        assertThat(lookup).as(input).isInstanceOf(MemberLookup.Found.class);
        return (MemberLookup.Found) lookup;
    }
}
