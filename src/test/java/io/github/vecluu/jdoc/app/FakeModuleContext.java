package io.github.vecluu.jdoc.app;

import io.github.vecluu.jdoc.indexer.IndexOptions;
import io.github.vecluu.jdoc.indexer.IndexReport;
import io.github.vecluu.jdoc.model.ResolutionStatus;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** A {@link ModuleContext} on an {@link AppIndex}; records how it was opened. */
final class FakeModuleContext implements ModuleContext {

    private final AppIndex index;
    private final ResolutionStatus status;
    private final List<String> warnings;
    private IndexReport report = new IndexReport(0, 2, 0, 0, List.of(), List.of());
    final List<Optional<Path>> froms = new ArrayList<>();
    final List<IndexOptions> options = new ArrayList<>();

    FakeModuleContext(AppIndex index, ResolutionStatus status, List<String> warnings) {
        this.index = index;
        this.status = status;
        this.warnings = warnings;
    }

    FakeModuleContext reporting(IndexReport report) {
        this.report = report;
        return this;
    }

    @Override
    public ModuleSession open(Optional<Path> from, IndexOptions options) {
        froms.add(from);
        this.options.add(options);
        return index.session(status, warnings, report);
    }
}
