package io.github.vecluu.jdoc.app;

import static io.github.vecluu.jdoc.app.CommandTestSupport.lines;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.vecluu.jdoc.indexer.ArtifactWithoutSources;
import io.github.vecluu.jdoc.indexer.IndexOptions;
import io.github.vecluu.jdoc.indexer.IndexReport;
import io.github.vecluu.jdoc.indexer.TypeWithoutSources;
import io.github.vecluu.jdoc.model.ArtifactKey;
import io.github.vecluu.jdoc.model.DocSource;
import io.github.vecluu.jdoc.model.Gav;
import io.github.vecluu.jdoc.model.ResolutionStatus;
import io.github.vecluu.jdoc.model.TypeName;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IndexCommandTest {

    @TempDir
    static Path tmp;
    static AppIndex index;

    @BeforeAll
    static void index() {
        index = AppIndex.standard(tmp);
    }

    @AfterAll
    static void close() {
        index.close();
    }

    @Test
    void reportsCountsAndCapsTypesWithoutSourcesPerArtifact() {
        List<TypeWithoutSources> many = IntStream.rangeClosed(1, 12)
                .mapToObj(i -> new TypeWithoutSources(TypeName.of("g.T" + (char) ('a' + i)), DocSource.DECOMPILED))
                .toList();
        IndexReport report = new IndexReport(2, 1, 310, 4120, List.of(
                new ArtifactWithoutSources(ArtifactKey.maven(Gav.of("com.acme", "gen", "2.0")), List.of(
                        new TypeWithoutSources(TypeName.of("com.acme.Model"), DocSource.JAVADOC))),
                new ArtifactWithoutSources(ArtifactKey.maven(Gav.of("org.x", "big", "1.0")), many)),
                List.of("broken 1.0: cannot read\njar"));
        FakeModuleContext context = index.context().reporting(report);

        CommandResult result = new IndexCommand(context).run(true, Optional.empty());

        assertThat(result.exitCode()).isEqualTo(ExitCode.FOUND);
        assertThat(context.options).containsExactly(IndexOptions.indexCommand(true));
        assertThat(lines(result)).containsExactly(
                "module " + tmp.resolve("module") + "  (1 artifact + jdk 25)",
                "indexed 2 artifacts (310 types, 4120 members), 1 up to date",
                "types without sources:",
                "  gen 2.0: 1 type",
                "    com.acme.Model  javadoc",
                "  big 1.0: 12 types",
                "    g.Tb  decompiled",
                "    g.Tc  decompiled",
                "    g.Td  decompiled",
                "    g.Te  decompiled",
                "    g.Tf  decompiled",
                "    g.Tg  decompiled",
                "    g.Th  decompiled",
                "    g.Ti  decompiled",
                "    g.Tj  decompiled",
                "    g.Tk  decompiled",
                "    ... and 2 more",
                "failed to index:",
                "  broken 1.0: cannot read jar");
    }

    @Test
    void reportsCompleteSourcesAndUnresolvedVersions() {
        FakeModuleContext context = index.context(ResolutionStatus.UNRESOLVED,
                List.of("warning: mvn failed, versions unresolved: using newest versions in the local repository"))
                .reporting(new IndexReport(0, 2, 0, 0, List.of(), List.of()));

        CommandResult result = new IndexCommand(context).run(false, Optional.empty());

        assertThat(context.options).containsExactly(IndexOptions.indexCommand(false));
        assertThat(result.errorOutput()).hasSize(1);
        assertThat(lines(result)).containsExactly(
                "module " + tmp.resolve("module") + "  (1 artifact + jdk 25)  [version unresolved]",
                "indexed 0 artifacts (0 types, 0 members), 2 up to date",
                "all library types have sources");
    }
}
