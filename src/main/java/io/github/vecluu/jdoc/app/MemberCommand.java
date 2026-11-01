package io.github.vecluu.jdoc.app;

import io.github.vecluu.jdoc.indexer.IndexOptions;
import io.github.vecluu.jdoc.lookup.MemberMatch;
import io.github.vecluu.jdoc.text.ColumnLayout;
import io.github.vecluu.jdoc.text.TextResult;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * {@code jdoc member <name>}: every type on the classpath declaring a member with that name, as
 * {@code Type#name(params)} with the artifact, ordered by type:
 *
 * <pre>
 * 2 members named orElseThrow
 * java.util.Optional#orElseThrow()                           jdk 25
 * java.util.Optional#orElseThrow(Supplier)                   jdk 25
 * </pre>
 *
 * Exit code 1 with {@code no member named x on this classpath} if there is none.
 */
public final class MemberCommand implements LookupCommand {

    private final ModuleContext context;
    private final ColumnLayout columns = new ColumnLayout();

    /** A command answering from the given module context. */
    public MemberCommand(ModuleContext context) {
        this.context = context;
    }

    @Override
    public CommandResult run(String name, Optional<Path> from) {
        if (name == null || name.isBlank()) {
            return CommandResult.error("empty member name");
        }
        String wanted = name.strip();
        return context.run(from, IndexOptions.lookup(), session -> {
            List<MemberMatch> members = session.lookup().memberSearch().search(wanted, session.scope());
            if (members.isEmpty()) {
                return CommandResult.notFound(TextResult.builder(
                        Headers.message("no member named " + wanted + " on this classpath", session.scope())).build());
            }
            String header = members.size() + (members.size() == 1 ? " member named " : " members named ") + wanted;
            return CommandResult.found(TextResult.builder(Headers.message(header, session.scope()))
                    .lines(columns.format(members.stream()
                            .map(member -> ColumnLayout.Row.of(member.reference(), member.declaringType().label()))
                            .toList()))
                    .build());
        });
    }
}
