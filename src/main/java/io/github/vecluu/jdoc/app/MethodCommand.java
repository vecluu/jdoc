package io.github.vecluu.jdoc.app;

import io.github.vecluu.jdoc.indexer.IndexOptions;
import io.github.vecluu.jdoc.lookup.MemberDoc;
import io.github.vecluu.jdoc.lookup.MemberLookup;
import io.github.vecluu.jdoc.lookup.MemberMatch;
import io.github.vecluu.jdoc.text.TextResult;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * {@code jdoc method <type>#<name>[(<params>)]}: the full rendered Javadoc of the member, or of every matching
 * overload, one block each, separated by a blank line:
 *
 * <pre>
 * java.util.Optional&lt;T&gt;  public final class  (jdk 25)
 * T orElseThrow()
 * If a value is present, returns the value, otherwise throws {@code NoSuchElementException}.
 *
 * &#64;return the non-{@code null} value described by this {@code Optional}
 * &#64;throws NoSuchElementException if no value is present
 * &#64;since 10
 * </pre>
 *
 * The signature line gets {@code [inherited from X]} for members declared by a supertype and {@code [doc from X]}
 * when the text was taken from an overridden method; a member without docs shows {@code (no documentation)}.
 */
public final class MethodCommand implements LookupCommand {

    static final String NO_DOCUMENTATION = "(no documentation)";

    private final ModuleContext context;

    /** A command answering from the given module context. */
    public MethodCommand(ModuleContext context) {
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
            boolean first = true;
            for (MemberMatch member : found.members()) {
                if (!first) {
                    text.blankLine();
                }
                first = false;
                MemberDoc doc = session.lookup().docs().memberDoc(member, session.scope());
                text.block(block(member, doc));
            }
            return CommandResult.found(text.build());
        });
    }

    private static List<String> block(MemberMatch member, MemberDoc doc) {
        List<String> lines = new ArrayList<>();
        StringBuilder signature = new StringBuilder(member.descriptor().signature());
        if (member.inherited()) {
            signature.append("  [inherited from ").append(member.declaringType().name().displayName()).append(']');
        }
        doc.inheritedFrom().ifPresent(type -> signature.append("  [doc from ").append(type.displayName()).append(']'));
        lines.add(signature.toString());
        List<String> docLines = DocLines.of(doc.doc());
        lines.addAll(docLines.isEmpty() ? List.of(NO_DOCUMENTATION) : docLines);
        return lines;
    }
}
