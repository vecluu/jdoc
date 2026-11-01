package io.github.vecluu.jdoc.app;

import io.github.vecluu.jdoc.indexer.IndexOptions;
import io.github.vecluu.jdoc.lookup.Subtype;
import io.github.vecluu.jdoc.lookup.TypeLookup;
import io.github.vecluu.jdoc.text.ColumnLayout;
import io.github.vecluu.jdoc.text.TextResult;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * {@code jdoc impls <type>}: known subtypes and implementations on the classpath, direct ones first, one per line
 * with the artifact; subtypes reached through another subtype are marked {@code indirect}:
 *
 * <pre>
 * java.util.Collection&lt;E&gt;  public interface  (jdk 25)
 * java.util.List          jdk 25
 * java.util.ArrayList     jdk 25  indirect
 * </pre>
 *
 * Exit code 1 (with the header and {@code no known subtypes on this classpath}) if there are none.
 */
public final class ImplsCommand implements LookupCommand {

    static final String NONE = "no known subtypes on this classpath";

    private final ModuleContext context;
    private final ColumnLayout columns = new ColumnLayout();

    /** A command answering from the given module context. */
    public ImplsCommand(ModuleContext context) {
        this.context = context;
    }

    @Override
    public CommandResult run(String type, Optional<Path> from) {
        return context.run(from, IndexOptions.lookup(), session -> {
            TypeLookup lookup = session.lookup().typeResolver().resolve(type, session.scope());
            if (!(lookup instanceof TypeLookup.Found found)) {
                return LookupOutcomes.typeNotResolved(lookup, session.scope());
            }
            List<Subtype> subtypes = session.lookup().hierarchy().subtypes(found.type(), session.scope());
            TextResult.Builder text = TextResult.builder(Headers.type(found.type(), session.scope()));
            if (subtypes.isEmpty()) {
                return CommandResult.notFound(text.line(NONE).build());
            }
            text.lines(columns.format(subtypes.stream()
                    .map(subtype -> ColumnLayout.Row.of(subtype.type().name().displayName(),
                            subtype.type().label() + (subtype.direct() ? "" : "  indirect")))
                    .toList()));
            return CommandResult.found(text.build());
        });
    }
}
