package io.github.vecluu.jdoc.app;

import io.github.vecluu.jdoc.indexer.IndexOptions;
import io.github.vecluu.jdoc.lookup.FindResult;
import io.github.vecluu.jdoc.query.NamePattern;
import io.github.vecluu.jdoc.query.ParseResult;
import io.github.vecluu.jdoc.text.ColumnLayout;
import io.github.vecluu.jdoc.text.TextResult;
import java.nio.file.Path;
import java.util.Optional;

/**
 * {@code jdoc find <pattern>}: matching types and members, best first, one per line with the artifact:
 *
 * <pre>
 * 3 matches for HSR
 * jakarta.servlet.http.HttpServletRequest                 jakarta.servlet-api 6.0.0
 * jakarta.servlet.http.HttpServletRequestWrapper          jakarta.servlet-api 6.0.0
 * jakarta.servlet.http.HttpServletResponse#setStatus(int)  jakarta.servlet-api 6.0.0
 * </pre>
 *
 * A capped list says {@code 200+ matches for x (best 200 shown)} in the header and ends with a hint to refine the
 * pattern. Exit code 1 with {@code no types or members match x} if nothing matches.
 */
public final class FindCommand implements LookupCommand {

    static final String REFINE_HINT = "more matches not shown; use a longer pattern";

    private final ModuleContext context;
    private final ColumnLayout columns = new ColumnLayout();

    /** A command answering from the given module context. */
    public FindCommand(ModuleContext context) {
        this.context = context;
    }

    @Override
    public CommandResult run(String pattern, Optional<Path> from) {
        if (NamePattern.parse(pattern) instanceof ParseResult.Failure<NamePattern> failure) {
            return CommandResult.error(failure.message());
        }
        return context.run(from, IndexOptions.lookup(), session -> {
            FindResult result = session.lookup().find().find(pattern.strip(), session.scope());
            if (result.isEmpty()) {
                return CommandResult.notFound(TextResult.builder(
                        Headers.message("no types or members match " + result.pattern(), session.scope())).build());
            }
            String count = result.truncated()
                    ? result.hits().size() + "+ matches for " + result.pattern() + " (best " + result.hits().size()
                            + " shown)"
                    : result.hits().size() + (result.hits().size() == 1 ? " match" : " matches") + " for "
                            + result.pattern();
            TextResult.Builder text = TextResult.builder(Headers.message(count, session.scope()))
                    .lines(columns.format(result.hits().stream()
                            .map(hit -> ColumnLayout.Row.of(hit.reference(), hit.type().label()))
                            .toList()));
            if (result.truncated()) {
                text.line(REFINE_HINT);
            }
            return CommandResult.found(text.build());
        });
    }
}
