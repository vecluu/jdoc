package io.github.vecluu.jdoc.app;

import io.github.vecluu.jdoc.indexer.ArtifactWithoutSources;
import io.github.vecluu.jdoc.indexer.IndexFailure;
import io.github.vecluu.jdoc.indexer.IndexOptions;
import io.github.vecluu.jdoc.indexer.IndexReport;
import io.github.vecluu.jdoc.indexer.TypeWithoutSources;
import io.github.vecluu.jdoc.lookup.ScopedArtifact;
import io.github.vecluu.jdoc.text.ColumnLayout;
import io.github.vecluu.jdoc.text.TextResult;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * {@code jdoc index [--force]}: resolves and indexes the current module up front and reports what it did:
 *
 * <pre>
 * module /home/me/project/app  (12 artifacts + jdk 25)
 * indexed 2 artifacts (310 types, 4120 members), 11 up to date
 * types without sources:
 *   sample-lib 1.0.0: 2 types
 *     com.example.sample.gen.GeneratedModel  javadoc
 *     com.example.sample.kt.KotlinUtils      decompiled
 * failed to index:
 *   broken 1.0: cannot read jar ...
 *   corrupt 2.1: zip END header not found  (previously failed; retried when the jar changes or with --force)
 * </pre>
 *
 * Each artifact lists at most {@value #TYPES_PER_ARTIFACT} types, then {@code ... and N more}. Without missing
 * sources the report says {@code all library types have sources}. Counts include the JDK.
 */
public final class IndexCommand {

    /** Types listed per artifact in the missing-sources report. */
    public static final int TYPES_PER_ARTIFACT = 10;

    private static final String INDENT = "  ";
    private static final String PREVIOUSLY = "  (previously failed; retried when the jar changes or with --force)";

    private final ModuleContext context;
    private final ColumnLayout columns = new ColumnLayout();

    /** A command working on the given module context. */
    public IndexCommand(ModuleContext context) {
        this.context = context;
    }

    /**
     * Indexes the module.
     *
     * @param force rebuild every artifact of the module from scratch (the JDK only if its file changed)
     * @param from  the {@code --from} path, or empty for the working directory
     */
    public CommandResult run(boolean force, Optional<Path> from) {
        return context.run(from, IndexOptions.indexCommand(force), session -> {
            IndexReport report = session.report();
            TextResult.Builder text = TextResult.builder(header(session))
                    .line("indexed " + plural(report.artifactsIndexed(), "artifact") + " ("
                            + plural(report.typesIndexed(), "type") + ", " + plural(report.membersIndexed(), "member")
                            + "), " + report.artifactsReused() + " up to date");
            if (report.missingSources().isEmpty()) {
                text.line("all library types have sources");
            } else {
                text.line("types without sources:");
                report.missingSources().forEach(artifact -> text.block(missing(artifact)));
            }
            if (!report.failures().isEmpty()) {
                text.line("failed to index:");
                report.failures().forEach(failure -> text.line(INDENT + failure(failure)));
            }
            return CommandResult.found(text.build());
        });
    }

    private static String failure(IndexFailure failure) {
        return failure.artifact().label() + ": " + failure.reason() + (failure.previouslyFailed() ? PREVIOUSLY : "");
    }

    private static String header(ModuleSession session) {
        List<ScopedArtifact> artifacts = session.scope().artifacts();
        long libraries = artifacts.stream().filter(artifact -> !artifact.key().isJdk()).count();
        String jdk = artifacts.stream().filter(artifact -> artifact.key().isJdk()).findFirst()
                .map(artifact -> " + " + artifact.label())
                .orElse(", no jdk");
        return Headers.message("module " + session.classpath().moduleDirectory() + "  ("
                + plural(libraries, "artifact") + jdk + ")", session.scope());
    }

    private List<String> missing(ArtifactWithoutSources artifact) {
        List<TypeWithoutSources> types = artifact.types();
        List<ColumnLayout.Row> rows = types.stream().limit(TYPES_PER_ARTIFACT)
                .map(type -> ColumnLayout.Row.of(type.name().displayName(),
                        type.docSource().name().toLowerCase(Locale.ROOT)))
                .toList();
        List<String> lines = new ArrayList<>();
        lines.add(INDENT + artifact.artifact().label() + ": " + plural(types.size(), "type"));
        lines.addAll(columns.format(rows, INDENT + INDENT));
        if (types.size() > TYPES_PER_ARTIFACT) {
            lines.add(INDENT + INDENT + "... and " + (types.size() - TYPES_PER_ARTIFACT) + " more");
        }
        return lines;
    }

    private static String plural(long count, String noun) {
        return count + " " + noun + (count == 1 ? "" : "s");
    }
}
