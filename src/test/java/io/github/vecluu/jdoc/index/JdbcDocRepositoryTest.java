package io.github.vecluu.jdoc.index;

import static io.github.vecluu.jdoc.index.IndexFixtures.artifact;
import static io.github.vecluu.jdoc.index.IndexFixtures.method;
import static io.github.vecluu.jdoc.index.IndexFixtures.type;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.vecluu.jdoc.model.IndexedArtifact;
import io.github.vecluu.jdoc.model.IndexedMember;
import io.github.vecluu.jdoc.model.IndexedType;
import io.github.vecluu.jdoc.model.MemberDescriptor;
import io.github.vecluu.jdoc.model.RenderedDoc;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JdbcDocRepositoryTest {

    @TempDir
    Path tmp;

    private IndexDatabase database;
    private JdbcArtifactRepository artifacts;
    private JdbcTypeRepository types;
    private JdbcMemberRepository members;
    private DocRepository docs;

    @BeforeEach
    void open() {
        database = IndexDatabase.open(tmp.resolve("index.db"), Duration.ofSeconds(5));
        artifacts = new JdbcArtifactRepository(database);
        types = new JdbcTypeRepository(database);
        members = new JdbcMemberRepository(database);
        docs = new JdbcDocRepository(database);
    }

    @AfterEach
    void close() {
        database.close();
    }

    private static RenderedDoc doc(String description, String... tags) {
        return new RenderedDoc(description, description.lines().findFirst().orElse(""), Arrays.asList(tags), false);
    }

    @Test
    void roundTripsTypeAndMemberDocsByPosition() {
        IndexedArtifact artifact = artifacts.save(artifact("g:a:1"));
        List<IndexedType> stored = types.insertAll(artifact.id(), List.of(type("p.A"), type("p.B")));
        long a = stored.get(0).id();
        long b = stored.get(1).id();
        List<MemberDescriptor> ofA = List.of(method("p.A", "x"), method("p.A", "y"), method("p.A", "z"));
        members.insertAll(Map.of(a, ofA, b, List.of(method("p.B", "w"))));
        String longText = ("Paragraph with <pre> and unicode: ✓ Größe 😀.\n\n    int x = 1;\n\n").repeat(2_000);
        RenderedDoc inherits = new RenderedDoc("", "", List.of("@param v the v"), true);

        docs.insertTypeDocs(Map.of(a, doc("Type A."), b, RenderedDoc.missing()));
        docs.insertMemberDocs(Map.of(a, List.of(doc(longText, "@since 9"), RenderedDoc.missing(), inherits),
                b, List.of(doc(""))));

        assertThat(docs.typeDoc(a)).contains(doc("Type A."));
        assertThat(docs.typeDoc(b)).isEmpty();
        List<IndexedMember> membersOfA = members.membersOf(a);
        assertThat(docs.memberDoc(membersOfA.get(0).id())).contains(doc(longText, "@since 9"));
        assertThat(docs.memberDoc(membersOfA.get(1).id())).isEmpty();
        assertThat(docs.memberDoc(membersOfA.get(2).id())).contains(inherits);
        assertThat(docs.memberDoc(members.membersOf(b).getFirst().id())).contains(doc(""));
        assertThat(docs.memberDoc(9_999)).isEmpty();
    }

    @Test
    void packageDocsArePerArtifactAndReplaceable() {
        long first = artifacts.save(artifact("g:a:1")).id();
        long second = artifacts.save(artifact("g:b:1")).id();

        docs.savePackageDoc(first, "p", doc("First p."));
        docs.savePackageDoc(first, "", doc("Default package."));
        docs.savePackageDoc(second, "p", doc("Second p."));
        docs.savePackageDoc(second, "q", RenderedDoc.missing());
        docs.savePackageDoc(first, "p", doc("First p again."));

        assertThat(docs.packageDoc(first, "p")).contains(doc("First p again."));
        assertThat(docs.packageDoc(first, "")).contains(doc("Default package."));
        assertThat(docs.packageDoc(second, "p")).contains(doc("Second p."));
        assertThat(docs.packageDoc(second, "q")).isEmpty();
        assertThat(docs.packageDoc(first, "q")).isEmpty();
    }

    @Test
    void docsAreDeletedWithTheirArtifact() {
        IndexedArtifact artifact = artifacts.save(artifact("g:a:1"));
        long typeId = types.insertAll(artifact.id(), List.of(type("p.A"))).getFirst().id();
        members.insertAll(typeId, List.of(method("p.A", "x")));
        long memberId = members.membersOf(typeId).getFirst().id();
        docs.insertTypeDocs(Map.of(typeId, doc("A.")));
        docs.insertMemberDocs(Map.of(typeId, List.of(doc("X."))));
        docs.savePackageDoc(artifact.id(), "p", doc("P."));

        artifacts.delete(artifact.id());

        assertThat(docs.typeDoc(typeId)).isEqualTo(Optional.empty());
        assertThat(docs.memberDoc(memberId)).isEmpty();
        assertThat(docs.packageDoc(artifact.id(), "p")).isEmpty();
    }
}
