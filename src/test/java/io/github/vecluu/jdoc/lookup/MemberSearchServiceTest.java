package io.github.vecluu.jdoc.lookup;

import static io.github.vecluu.jdoc.lookup.LookupIndex.field;
import static io.github.vecluu.jdoc.lookup.LookupIndex.method;
import static io.github.vecluu.jdoc.lookup.LookupIndex.publicClass;
import static io.github.vecluu.jdoc.lookup.LookupIndex.type;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.vecluu.jdoc.model.TypeKind;
import io.github.vecluu.jdoc.model.Visibility;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MemberSearchServiceTest {

    @TempDir
    Path tmp;

    private LookupIndex index;
    private MemberSearchService search;

    @BeforeEach
    void setUp() {
        index = LookupIndex.open(tmp);
        index.add("com.acme:a:1.0", List.of(
                        publicClass("x.Beta"),
                        publicClass("x.Alpha"),
                        type("x.Secret", TypeKind.CLASS, Visibility.PACKAGE)),
                method("x.Beta", "of", "int value"),
                method("x.Alpha", "of"),
                method("x.Alpha", "of", "String... values"),
                field("x.Alpha", "OF"),
                method("x.Secret", "of"),
                method("x.Beta", "Size"));
        index.add("com.acme:b:1.0", List.of(publicClass("x.Beta")), method("x.Beta", "of", "long shadowed"));
        search = new MemberSearchService(new ScopedTypes(index.types), index.members);
    }

    @AfterEach
    void tearDown() {
        index.close();
    }

    @Test
    void listsEveryApiTypeDeclaringTheMemberByTypeName() {
        assertThat(search.search("of", index.scope())).extracting(MemberMatch::reference)
                .containsExactly("x.Alpha#of()", "x.Alpha#of(String[])", "x.Beta#of(int)");
    }

    @Test
    void prefersExactCaseButFallsBackToIgnoringCase() {
        assertThat(search.search("OF", index.scope())).extracting(MemberMatch::reference)
                .containsExactly("x.Alpha#OF");
        assertThat(search.search("size", index.scope())).extracting(MemberMatch::reference)
                .containsExactly("x.Beta#Size()");
    }

    @Test
    void unknownOrBlankNamesGiveNothing() {
        assertThat(search.search("nothing", index.scope())).isEmpty();
        assertThat(search.search(" ", index.scope())).isEmpty();
    }
}
