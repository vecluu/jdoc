package io.github.vecluu.jdoc.app;

import io.github.vecluu.jdoc.indexer.IndexOptions;
import io.github.vecluu.jdoc.lookup.MemberLookup;
import io.github.vecluu.jdoc.lookup.MemberMatch;
import io.github.vecluu.jdoc.lookup.MemberSource;
import io.github.vecluu.jdoc.model.RenderedDoc;
import io.github.vecluu.jdoc.text.TextResult;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * {@code jdoc source <type>#<name>[(<params>)]}: the member's declaration with its body (and Javadoc), one block per
 * matching overload, separated by a blank line. Notes are Java comments above the code: {@code // inherited from X},
 * {@code // decompiled}; a javadoc-jar doc is shown as a {@code /** ... *}{@code /} comment above decompiled code.
 * A member without source shows its signature and {@code // no source: <reason>}. Exit code 1 if no overload has
 * source.
 */
public final class SourceCommand implements LookupCommand {

    private final ModuleContext context;

    /** A command answering from the given module context. */
    public SourceCommand(ModuleContext context) {
        this.context = context;
    }

    @Override
    public CommandResult run(String reference, Optional<Path> from) {
        return context.run(from, IndexOptions.lookup(), session -> {
            MemberLookup lookup = session.lookup().memberLookup().lookup(reference, session.scope());
            if (!(lookup instanceof MemberLookup.Found found)) {
                return LookupOutcomes.memberNotResolved(lookup, session.scope());
            }
            TextResult.Builder text = TextResult.builder(Headers.type(found.type(), session.scope()));
            boolean any = false;
            boolean first = true;
            for (MemberMatch member : found.members()) {
                if (!first) {
                    text.blankLine();
                }
                first = false;
                MemberSource source = session.lookup().sources().source(member, session.scope());
                any |= source instanceof MemberSource.Available;
                text.block(block(source));
            }
            return any ? CommandResult.found(text.build()) : CommandResult.notFound(text.build());
        });
    }

    private static List<String> block(MemberSource source) {
        List<String> lines = new ArrayList<>();
        MemberMatch member = source.member();
        if (member.inherited()) {
            lines.add("// inherited from " + member.declaringType().name().displayName());
        }
        switch (source) {
            case MemberSource.Available available -> {
                if (available.decompiled()) {
                    lines.add("// decompiled");
                }
                available.doc().ifPresent(doc -> lines.addAll(comment(doc)));
                lines.addAll(DocLines.verbatim(available.code()));
            }
            case MemberSource.Unavailable unavailable -> {
                lines.add(member.descriptor().signature());
                lines.add("// no source: " + unavailable.reason());
            }
        }
        return lines;
    }

    static List<String> comment(RenderedDoc doc) {
        List<String> body = DocLines.of(doc);
        if (body.isEmpty()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        lines.add("/**");
        body.forEach(line -> lines.add(line.isEmpty() ? " *" : " * " + line));
        lines.add(" */");
        return lines;
    }
}
