package io.github.vecluu.jdoc.app;

import io.github.vecluu.jdoc.lookup.LocatedType;
import io.github.vecluu.jdoc.lookup.LookupScope;
import io.github.vecluu.jdoc.lookup.MemberLookup;
import io.github.vecluu.jdoc.lookup.TypeLookup;
import io.github.vecluu.jdoc.text.ColumnLayout;
import io.github.vecluu.jdoc.text.TextResult;
import java.util.List;

/**
 * Results for type and member arguments that did not resolve to something: not found (exit 1, with suggestions),
 * ambiguous (exit 2, candidates and {@code Re-run with a fully qualified name.}) and unparsable (exit 3).
 */
final class LookupOutcomes {

    static final String RERUN_HINT = "Re-run with a fully qualified name.";
    private static final String INDENT = "  ";
    private static final ColumnLayout COLUMNS = new ColumnLayout();

    private LookupOutcomes() {
    }

    /** The result for a type lookup that is not {@link TypeLookup.Found}. */
    static CommandResult typeNotResolved(TypeLookup lookup, LookupScope scope) {
        return switch (lookup) {
            case TypeLookup.Found found -> throw new IllegalArgumentException("type was found: " + found.input());
            case TypeLookup.NotFound notFound -> typeNotFound(notFound, scope);
            case TypeLookup.Ambiguous ambiguous -> ambiguous(ambiguous, scope);
            case TypeLookup.Invalid invalid ->
                    CommandResult.error("invalid type name '" + invalid.input() + "': " + invalid.message());
        };
    }

    /** The result for a member lookup that is not {@link MemberLookup.Found}. */
    static CommandResult memberNotResolved(MemberLookup lookup, LookupScope scope) {
        return switch (lookup) {
            case MemberLookup.Found found -> throw new IllegalArgumentException("member was found");
            case MemberLookup.TypeNotResolved typeNotResolved ->
                    typeNotResolved(typeNotResolved.typeLookup(), scope);
            case MemberLookup.MemberNotFound notFound -> memberNotFound(notFound, scope);
            case MemberLookup.Invalid invalid ->
                    CommandResult.error("invalid member reference '" + invalid.input() + "': " + invalid.message()
                            + " (expected <type>#<name>[(<params>)])");
        };
    }

    private static CommandResult typeNotFound(TypeLookup.NotFound notFound, LookupScope scope) {
        TextResult.Builder text = TextResult.builder(
                Headers.message("no type named " + notFound.input() + " on this classpath", scope));
        if (!notFound.suggestions().isEmpty()) {
            text.line("did you mean:").lines(typeRows(notFound.suggestions()));
        }
        return CommandResult.notFound(text.build());
    }

    private static CommandResult ambiguous(TypeLookup.Ambiguous ambiguous, LookupScope scope) {
        TextResult text = TextResult.builder(Headers.message(ambiguous.candidates().size() + " types named "
                        + ambiguous.input() + " on this classpath:", scope))
                .lines(typeRows(ambiguous.candidates()))
                .line(RERUN_HINT)
                .build();
        return CommandResult.ambiguous(text);
    }

    private static CommandResult memberNotFound(MemberLookup.MemberNotFound notFound, LookupScope scope) {
        TextResult.Builder text = TextResult.builder(Headers.type(notFound.type(), scope))
                .line("no member " + notFound.reference().input() + " in " + notFound.type().name().displayName());
        notFound.protectedInherited().forEach(match -> text.line(match.reference() + " is protected and "
                + notFound.type().name().displayName() + " cannot be subclassed"));
        if (!notFound.suggestions().isEmpty()) {
            text.line("did you mean:");
            notFound.suggestions().forEach(suggestion -> text.line(INDENT + suggestion));
        }
        return CommandResult.notFound(text.build());
    }

    /** {@code  java.time.Duration  jdk 25} rows, indented. */
    static List<String> typeRows(List<LocatedType> types) {
        return COLUMNS.format(types.stream()
                .map(type -> ColumnLayout.Row.of(type.name().displayName(), type.label()))
                .toList(), INDENT);
    }
}
