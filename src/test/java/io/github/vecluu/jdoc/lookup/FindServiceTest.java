package io.github.vecluu.jdoc.lookup;

import static io.github.vecluu.jdoc.lookup.LookupIndex.field;
import static io.github.vecluu.jdoc.lookup.LookupIndex.method;
import static io.github.vecluu.jdoc.lookup.LookupIndex.publicClass;
import static io.github.vecluu.jdoc.lookup.LookupIndex.type;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.vecluu.jdoc.model.ArtifactKey;
import io.github.vecluu.jdoc.model.Gav;
import io.github.vecluu.jdoc.model.MemberDescriptor;
import io.github.vecluu.jdoc.model.TypeDescriptor;
import io.github.vecluu.jdoc.model.TypeKind;
import io.github.vecluu.jdoc.model.Visibility;
import io.github.vecluu.jdoc.query.MatchKind;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FindServiceTest {

    @TempDir
    Path tmp;

    private LookupIndex index;
    private FindService find;

    @BeforeEach
    void setUp() {
        index = LookupIndex.open(tmp);
        index.add("com.acme:web:1.0", List.of(
                        publicClass("web.HttpServletRequest"),
                        publicClass("web.HttpSession"),
                        publicClass("util.HashMap"),
                        publicClass("util.Map"),
                        publicClass("util.Map$Entry"),
                        publicClass("util.MutableAnyPair"),
                        type("util.MapSecret", TypeKind.CLASS, Visibility.PACKAGE),
                        type("util.Hidden", TypeKind.CLASS, Visibility.PACKAGE),
                        publicClass("util.Hidden$MapInside")),
                method("util.HashMap", "put", "Object key"),
                method("util.Map", "getOrDefault", "Object key", "V value:Object"),
                method("util.Map", "mapping"),
                field("util.Map", "MAP_SIZE"),
                method("util.Hidden$MapInside", "map"));
        index.add("com.other:dup:1.0", List.of(publicClass("util.Map")), method("util.Map", "mapping"));
        find = new FindService(new ScopedTypes(index.types), index.members);
    }

    @AfterEach
    void tearDown() {
        index.close();
    }

    @Test
    void camelHumpPatternsMatchTypes() {
        FindResult result = find.find("HSR", index.scope());

        assertThat(result.hits()).extracting(FindHit::reference).containsExactly("web.HttpServletRequest");
        assertThat(result.hits().getFirst().score().kind()).isEqualTo(MatchKind.CAMEL_HUMPS);
        assertThat(result.truncated()).isFalse();
    }

    @Test
    void lowerCasePatternsAreSubstringMatchesOrderedByScoreTypesFirst() {
        FindResult result = find.find("map", index.scope());

        assertThat(result.hits()).extracting(FindHit::reference).containsExactly(
                "util.Map",
                "util.Map#mapping()",
                "util.Map#MAP_SIZE",
                "util.HashMap");
        assertThat(result.hits()).extracting(FindHit::isMember).containsExactly(false, true, true, false);
        assertThat(result.hits().getFirst().type().label()).isEqualTo("web 1.0");
    }

    @Test
    void qualifiedPatternsMatchDisplayNames() {
        assertThat(find.find("util.Map.E", index.scope()).hits()).extracting(FindHit::reference)
                .containsExactly("util.Map.Entry");
        assertThat(find.find("Map.Entry", index.scope()).hits()).extracting(FindHit::reference)
                .containsExactly("util.Map.Entry");
    }

    @Test
    void membersArePreselectedBySubstringAndScored() {
        FindResult result = find.find("getOr", index.scope());

        assertThat(result.hits()).singleElement().satisfies(hit -> {
            assertThat(hit.reference()).isEqualTo("util.Map#getOrDefault(Object,Object)");
            assertThat(hit.member()).isPresent();
            assertThat(hit.score().kind()).isEqualTo(MatchKind.PREFIX);
        });
    }

    @Test
    void capsTheNumberOfHits() {
        List<TypeDescriptor> many = new ArrayList<>();
        List<MemberDescriptor> members = new ArrayList<>();
        IntStream.range(0, FindService.MAX_HITS + 20).forEach(i -> {
            many.add(publicClass("many.Widget" + i));
            members.add(method("many.Widget" + i, "widgetize"));
        });
        index.add(LookupIndex.artifact(ArtifactKey.maven(Gav.parse("com.acme:many:1.0"))), many, members);

        FindResult result = find.find("widget", index.scope());

        assertThat(result.hits()).hasSize(FindService.MAX_HITS);
        assertThat(result.truncated()).isTrue();
        assertThat(result.hits()).allMatch(hit -> !hit.isMember());
    }

    @Test
    void noMatchIsAnEmptyResult() {
        assertThat(find.find("zzz", index.scope()).isEmpty()).isTrue();
    }
}
