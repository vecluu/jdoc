package io.github.vecluu.jdoc;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.vecluu.jdoc.CodeMapReferences.Span;
import io.github.vecluu.jdoc.CodeMapReferences.TypeRef;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CodeMapReferencesTest {

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

        assertThat(CodeMapReferences.codeSpans(markdown))
                .containsExactly(new Span(1, "model.Gav"), new Span(1, "a b"), new Span(6, "pom.xml"));
    }

    @Test
    void linkTargetsDropAnchorsAndUrls() {
        String markdown = "See [arch](../ARCHITECTURE.md#rules), [web](https://example.com) and [here](#top).";

        assertThat(CodeMapReferences.linkTargets(markdown)).containsExactly(new Span(1, "../ARCHITECTURE.md"));
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
        assertThat(CodeMapReferences.pathReference("docs/SPEC.md")).contains("docs/SPEC.md");
        assertThat(CodeMapReferences.pathReference("dist/skill/")).contains("dist/skill");
        assertThat(CodeMapReferences.pathReference("scripts/assemble.sh <version>")).contains("scripts/assemble.sh");
        assertThat(CodeMapReferences.pathReference("./mvnw -B verify")).contains("mvnw");
        assertThat(CodeMapReferences.pathReference("pom.xml")).contains("pom.xml");
        assertThat(CodeMapReferences.pathReference("CLAUDE.md")).contains("CLAUDE.md");
        assertThat(CodeMapReferences.pathReference("tasks/")).contains("tasks");
    }

    @Test
    void patternsAndNonRepositoryPathsAreIgnored() {
        assertThat(CodeMapReferences.pathReference("src/test/java/io/github/vecluu/jdoc/<package>/")).isEmpty();
        assertThat(CodeMapReferences.pathReference("scripts/*.sh")).isEmpty();
        assertThat(CodeMapReferences.pathReference("src/test/java/.../testsupport")).isEmpty();
        assertThat(CodeMapReferences.pathReference("lib/jdoc.aot")).isEmpty();
        assertThat(CodeMapReferences.pathReference("target/jdoc.jar")).isEmpty();
        assertThat(CodeMapReferences.pathReference("~/.cache/jdoc")).isEmpty();
        assertThat(CodeMapReferences.pathReference("deps.txt")).isEmpty();
        assertThat(CodeMapReferences.pathReference("tasks")).isEmpty();
    }

    private static Optional<TypeRef> type(String span) {
        return CodeMapReferences.typeReference(span, PACKAGES);
    }

    private static TypeRef ref(String packageName, List<String> types, String member) {
        return new TypeRef(Optional.ofNullable(packageName), types, Optional.ofNullable(member));
    }
}
