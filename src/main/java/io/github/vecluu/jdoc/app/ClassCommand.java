package io.github.vecluu.jdoc.app;

import io.github.vecluu.jdoc.indexer.IndexOptions;
import io.github.vecluu.jdoc.lookup.ClassView;
import io.github.vecluu.jdoc.lookup.TypeLookup;
import io.github.vecluu.jdoc.text.ColumnLayout;
import java.nio.file.Path;
import java.util.Optional;

/**
 * {@code jdoc class <type>}: header, supertypes, first doc paragraph, every public and protected member as signature
 * plus first sentence, nested types, inherited member names by declaring type. See {@link ClassViewFormatter}.
 */
public final class ClassCommand implements LookupCommand {

    private final ModuleContext context;
    private final ClassViewFormatter formatter;

    /** A command answering from the given module context. */
    public ClassCommand(ModuleContext context) {
        this.context = context;
        this.formatter = new ClassViewFormatter(new ColumnLayout());
    }

    @Override
    public CommandResult run(String type, Optional<Path> from) {
        return context.run(from, IndexOptions.lookup(), session -> {
            TypeLookup lookup = session.lookup().typeResolver().resolve(type, session.scope());
            if (!(lookup instanceof TypeLookup.Found found)) {
                return LookupOutcomes.typeNotResolved(lookup, session.scope());
            }
            ClassView view = session.lookup().classView().view(found.type(), session.scope());
            return CommandResult.found(formatter.format(view, session.scope()));
        });
    }
}
