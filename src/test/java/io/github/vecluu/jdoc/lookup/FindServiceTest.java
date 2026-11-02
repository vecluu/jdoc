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
                method("util.Hidden$MapInside", "map"),
                method("util.Map", "getFooBar"),
                method("util.Map", "orElseThrow"),
                method("util.Hidden$MapInside", "getFooBaz"));
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
    void camelHumpPatternsMatchMembersThatAreNotSubstrings() {
        FindResult result = find.find("gFB", index.scope());

        assertThat(result.hits()).singleElement().satisfies(hit -> {
            assertThat(hit.reference()).isEqualTo("util.Map#getFooBar()");
            assertThat(hit.score().kind()).isEqualTo(MatchKind.CAMEL_HUMPS);
        });
        assertThat(find.find("oET", index.scope()).hits()).extracting(FindHit::reference)
                .containsExactly("util.Map#orElseThrow()");
    }

    @Test
    void camelHumpPatternsRankMembersAndTypesTogether() {
        FindResult result = find.find("MaP", index.scope());

        assertThat(result.hits()).extracting(FindHit::reference).containsExactly(
                "util.Map",
                "util.Map#mapping()",
                "util.Map#MAP_SIZE",
                "util.HashMap",
                "util.MutableAnyPair");
        assertThat(result.hits()).extracting(hit -> hit.score().kind()).containsExactly(MatchKind.EXACT,
                MatchKind.PREFIX, MatchKind.PREFIX, MatchKind.CAMEL_HUMPS, MatchKind.CAMEL_HUMPS);
    }

    @Test
    void lowerCasePatternsKeepTheSubstringPathForMembers() {
        assertThat(find.find("gfb", index.scope()).isEmpty()).isTrue();
        assertThat(find.find("elsethr", index.scope()).hits()).extracting(FindHit::reference)
                .containsExactly("util.Map#orElseThrow()");
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
    void equalScoresListTheModulesLibrariesBeforeTheJdk(@TempDir Path dir) {
        try (LookupIndex jsoupModule = LookupIndex.open(dir)) {
            // the JDK comes first in the scope (as javac sees it), the library after it
            jsoupModule.add(LookupIndex.artifact(ArtifactKey.jdk(25)), List.of(
                            LookupIndex.publicInterface("javax.swing.text.Document"),
                            LookupIndex.publicInterface("org.w3c.dom.Document"),
                            LookupIndex.publicInterface("org.w3c.dom.DocumentType")),
                    List.of(method("javax.swing.text.Document", "getLength")));
            jsoupModule.add("org.jsoup:jsoup:1.17.2", List.of(
                            publicClass("org.jsoup.nodes.Document"),
                            publicClass("org.jsoup.nodes.DocumentType")),
                    LookupIndex.constructor("org.jsoup.nodes.Document", "String baseUri"));
            FindService service = new FindService(new ScopedTypes(jsoupModule.types), jsoupModule.members);

            assertThat(service.find("Document", jsoupModule.scope()).hits()).extracting(FindHit::reference)
                    .containsExactly(
                            "org.jsoup.nodes.Document",
                            "org.jsoup.nodes.Document#Document(String)",
                            "javax.swing.text.Document",
                            "org.w3c.dom.Document",
                            "org.jsoup.nodes.DocumentType",
                            "org.w3c.dom.DocumentType");
            assertThat(service.find("DocumentType", jsoupModule.scope()).hits()).extracting(FindHit::reference)
                    .containsExactly("org.jsoup.nodes.DocumentType", "org.w3c.dom.DocumentType");
            // a better JDK match still wins over a weaker library match
            assertThat(service.find("getLength", jsoupModule.scope()).hits()).extracting(FindHit::reference)
                    .containsExactly("javax.swing.text.Document#getLength()");
        }
    }

    @Test
    void noMatchIsAnEmptyResult() {
        assertThat(find.find("zzz", index.scope()).isEmpty()).isTrue();
    }
}
