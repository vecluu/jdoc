package io.github.vecluu.jdoc.app;

import io.github.vecluu.jdoc.indexer.IndexOptions;
import io.github.vecluu.jdoc.lookup.LocatedType;
import io.github.vecluu.jdoc.lookup.LookupScope;
import io.github.vecluu.jdoc.lookup.PackageDoc;
import io.github.vecluu.jdoc.lookup.PackageLookup;
import io.github.vecluu.jdoc.lookup.PackageView;
import io.github.vecluu.jdoc.lookup.ScopedArtifact;
import io.github.vecluu.jdoc.model.TypeDescriptor;
import io.github.vecluu.jdoc.text.ColumnLayout;
import io.github.vecluu.jdoc.text.ResultHeader;
import io.github.vecluu.jdoc.text.TextResult;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * {@code jdoc package <pkg>}: the {@code package-info} doc, then the package's types with first sentences:
 *
 * <pre>
 * java.time  package  (jdk 25)
 * The main API for dates, times, instants, and durations.
 *
 * final class Duration     A time-based amount of time, such as '34.5 seconds'.
 * enum Month               A month-of-year, such as 'July'.
 * </pre>
 *
 * The artifact labels are those of the package's types (several if the package is split), in classpath order like
 * every other precedence in jdoc (the JDK where the scope ranks it). Exit code 1 with
 * {@code no package named x on this classpath} (and similar names) if no API type is in it.
 */
public final class PackageCommand implements LookupCommand {

    private final ModuleContext context;
    private final ColumnLayout columns = new ColumnLayout();

    /** A command answering from the given module context. */
    public PackageCommand(ModuleContext context) {
        this.context = context;
    }

    @Override
    public CommandResult run(String packageName, Optional<Path> from) {
        return context.run(from, IndexOptions.lookup(), session -> {
            PackageLookup lookup = session.lookup().packages().describe(packageName, session.scope());
            return switch (lookup) {
                case PackageLookup.Found found -> CommandResult.found(format(found.view(), session.scope()));
                case PackageLookup.NotFound notFound -> notFound(notFound, session.scope());
            };
        });
    }

    private TextResult format(PackageView view, LookupScope scope) {
        TextResult.Builder text = TextResult.builder(header(view, scope));
        List<String> doc = view.doc().map(PackageDoc::doc).map(DocLines::of).orElse(List.of());
        if (!doc.isEmpty()) {
            text.block(doc).blankLine();
        }
        text.lines(columns.format(view.types().stream()
                .map(type -> ColumnLayout.Row.of(declaration(type.descriptor()), type.descriptor().firstSentence()))
                .toList()));
        return text.build();
    }

    private static String header(PackageView view, LookupScope scope) {
        List<ScopedArtifact> artifacts = view.types().stream()
                .map(LocatedType::artifact)
                .distinct()
                .sorted(Comparator.comparingInt((ScopedArtifact artifact) -> scope.rank(artifact.id())))
                .toList();
        boolean library = artifacts.stream().anyMatch(artifact -> !artifact.isJdk());
        String labels = artifacts.stream().map(ScopedArtifact::label).distinct().collect(Collectors.joining(", "));
        String header = view.name() + "  package  (" + labels + ")";
        return scope.versionUnresolved() && library ? header + "  " + ResultHeader.VERSION_UNRESOLVED_TAG : header;
    }

    private static String declaration(TypeDescriptor type) {
        String modifiers = type.modifiers().replaceAll("\\bpublic\\b", "").strip().replaceAll("\\s+", " ");
        String keyword = type.kind().keyword();
        return (modifiers.isEmpty() ? keyword : modifiers + " " + keyword) + " " + type.name().relativeName()
                + type.typeParameters();
    }

    private static CommandResult notFound(PackageLookup.NotFound notFound, LookupScope scope) {
        TextResult.Builder text = TextResult.builder(
                Headers.message("no package named " + notFound.name() + " on this classpath", scope));
        if (!notFound.suggestions().isEmpty()) {
            text.line("did you mean:");
            notFound.suggestions().forEach(suggestion -> text.line("  " + suggestion));
        }
        return CommandResult.notFound(text.build());
    }
}
