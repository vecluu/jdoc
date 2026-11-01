package io.github.vecluu.jdoc.cli;

import io.github.vecluu.jdoc.app.Application;
import io.github.vecluu.jdoc.app.CommandResult;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/** An {@link Application} that records its calls and answers with a scripted result (or throws). */
final class FakeApplication implements Application {

    final List<String> calls = new ArrayList<>();
    private final Supplier<CommandResult> answer;
    boolean closed;

    FakeApplication(Supplier<CommandResult> answer) {
        this.answer = answer;
    }

    private CommandResult record(String call, Optional<Path> from) {
        calls.add(call + from.map(path -> " from=" + path).orElse(""));
        return answer.get();
    }

    @Override
    public CommandResult find(String pattern, Optional<Path> from) {
        return record("find " + pattern, from);
    }

    @Override
    public CommandResult classView(String type, Optional<Path> from) {
        return record("class " + type, from);
    }

    @Override
    public CommandResult method(String reference, Optional<Path> from) {
        return record("method " + reference, from);
    }

    @Override
    public CommandResult source(String reference, Optional<Path> from) {
        return record("source " + reference, from);
    }

    @Override
    public CommandResult impls(String type, Optional<Path> from) {
        return record("impls " + type, from);
    }

    @Override
    public CommandResult member(String name, Optional<Path> from) {
        return record("member " + name, from);
    }

    @Override
    public CommandResult packageView(String packageName, Optional<Path> from) {
        return record("package " + packageName, from);
    }

    @Override
    public CommandResult index(boolean force, Optional<Path> from) {
        return record("index force=" + force, from);
    }

    @Override
    public void close() {
        closed = true;
    }
}
