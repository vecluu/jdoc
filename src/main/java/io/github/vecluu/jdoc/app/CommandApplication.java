package io.github.vecluu.jdoc.app;

import java.nio.file.Path;
import java.util.Optional;

/**
 * The {@link Application} made of the command services, all answering from one {@link ModuleContext}.
 */
public final class CommandApplication implements Application {

    private final LookupCommand find;
    private final LookupCommand classView;
    private final LookupCommand method;
    private final LookupCommand source;
    private final LookupCommand impls;
    private final LookupCommand member;
    private final LookupCommand packageView;
    private final IndexCommand index;
    private final Runnable onClose;

    /**
     * @param context the module context every command uses
     * @param onClose releases shared resources (the database) when the application is closed
     */
    public CommandApplication(ModuleContext context, Runnable onClose) {
        this.find = new FindCommand(context);
        this.classView = new ClassCommand(context);
        this.method = new MethodCommand(context);
        this.source = new SourceCommand(context);
        this.impls = new ImplsCommand(context);
        this.member = new MemberCommand(context);
        this.packageView = new PackageCommand(context);
        this.index = new IndexCommand(context);
        this.onClose = onClose;
    }

    @Override
    public CommandResult find(String pattern, Optional<Path> from) {
        return find.run(pattern, from);
    }

    @Override
    public CommandResult classView(String type, Optional<Path> from) {
        return classView.run(type, from);
    }

    @Override
    public CommandResult method(String reference, Optional<Path> from) {
        return method.run(reference, from);
    }

    @Override
    public CommandResult source(String reference, Optional<Path> from) {
        return source.run(reference, from);
    }

    @Override
    public CommandResult impls(String type, Optional<Path> from) {
        return impls.run(type, from);
    }

    @Override
    public CommandResult member(String name, Optional<Path> from) {
        return member.run(name, from);
    }

    @Override
    public CommandResult packageView(String packageName, Optional<Path> from) {
        return packageView.run(packageName, from);
    }

    @Override
    public CommandResult index(boolean force, Optional<Path> from) {
        return index.run(force, from);
    }

    @Override
    public void close() {
        onClose.run();
    }
}
