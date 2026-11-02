package io.github.vecluu.jdoc;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.vecluu.jdoc.DocReferences.Span;
import io.github.vecluu.jdoc.DocReferences.TypeRef;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DocReferencesTest {

    private static final Set<String> PACKAGES = Set.of("model", "index", "cli", "testsupport");

    @Test
    void codeSpansSkipFencedBlocksAndJoinWrappedSpans() {
        String markdown = """
                Intro `model.Gav` and `a
                  b` end.
                ```
                `inside` a fence
                ```
                After `pom.xml`.
                """;

        assertThat(DocReferences.codeSpans(markdown))
                .containsExactly(new Span(1, "model.Gav"), new Span(1, "a b"), new Span(6, "pom.xml"));
    }

    @Test
    void linkTargetsDropAnchorsAndUrls() {
        String markdown = "See [arch](../ARCHITECTURE.md#rules), [web](https://example.com) and [here](#top).";

        assertThat(DocReferences.linkTargets(markdown)).containsExactly(new Span(1, "../ARCHITECTURE.md"));
    }

    @Test
    void packageQualifiedTypesAndMembers() {
        assertThat(type("model.Gav")).contains(ref("model", List.of("Gav"), null));
        assertThat(type("index.Schema.VERSION")).contains(ref("index", List.of("Schema"), "VERSION"));
        assertThat(type("model.ArtifactKey.Maven(gav)")).contains(ref("model", List.of("ArtifactKey", "Maven"), null));
        assertThat(type("cli.JdocCli#execute(String[])")).contains(ref("cli", List.of("JdocCli"), "execute"));
        assertThat(type("io.github.vecluu.jdoc.cli.TrainingLibrary"))
                .contains(ref("cli", List.of("TrainingLibrary"), null));
    }

    @Test
    void bareTypesNestedTypesAndMembers() {
        assertThat(type("Gav")).contains(ref(null, List.of("Gav"), null));
        assertThat(type("TypeLookup.Found.shadowed")).contains(ref(null, List.of("TypeLookup", "Found"), "shadowed"));
        assertThat(type("JdocEnvironment.fromSystem()")).contains(ref(null, List.of("JdocEnvironment"), "fromSystem"));
        assertThat(type("Type#<init>")).contains(ref(null, List.of("Type"), null));
    }

    @Test
    void nonReferencesAreIgnored() {
        assertThat(type("LICENSE")).isEmpty();
        assertThat(type("SOURCES")).isEmpty();
        assertThat(type("java.util.Optional")).isEmpty();
        assertThat(type("jdoc.aotTraining")).isEmpty();
        assertThat(type("index.db")).isEmpty();
        assertThat(type("Outer.java")).isEmpty();
        assertThat(type("Map$Entry")).isEmpty();
        assertThat(type("List<MemberMatch>")).isEmpty();
        assertThat(type("Main-Class")).isEmpty();
        assertThat(type("N matches for p")).isEmpty();
        assertThat(type("Gav.a.b")).isEmpty();
    }

    @Test
    void pathsStartAtARootDirectoryOrNameARootFile() {
        assertThat(DocReferences.pathReference("docs/SPEC.md")).contains("docs/SPEC.md");
        assertThat(DocReferences.pathReference("dist/skill/")).contains("dist/skill");
        assertThat(DocReferences.pathReference("scripts/assemble.sh <version>")).contains("scripts/assemble.sh");
        assertThat(DocReferences.pathReference("./mvnw -B verify")).contains("mvnw");
        assertThat(DocReferences.pathReference("pom.xml")).contains("pom.xml");
        assertThat(DocReferences.pathReference("CLAUDE.md")).contains("CLAUDE.md");
    }

    @Test
    void patternsAndNonRepositoryPathsAreIgnored() {
        assertThat(DocReferences.pathReference("src/test/java/io/github/vecluu/jdoc/<package>/")).isEmpty();
        assertThat(DocReferences.pathReference("scripts/*.sh")).isEmpty();
        assertThat(DocReferences.pathReference("src/test/java/.../testsupport")).isEmpty();
        assertThat(DocReferences.pathReference("lib/jdoc.aot")).isEmpty();
        assertThat(DocReferences.pathReference("target/jdoc.jar")).isEmpty();
        assertThat(DocReferences.pathReference("~/.cache/jdoc")).isEmpty();
        assertThat(DocReferences.pathReference("deps.txt")).isEmpty();
        assertThat(DocReferences.pathReference("tasks")).isEmpty();
        assertThat(DocReferences.pathReference("tasks/README.md")).isEmpty();
    }

    private static Optional<TypeRef> type(String span) {
        return DocReferences.typeReference(span, PACKAGES);
    }

    private static TypeRef ref(String packageName, List<String> types, String member) {
        return new TypeRef(Optional.ofNullable(packageName), types, Optional.ofNullable(member));
    }
}
