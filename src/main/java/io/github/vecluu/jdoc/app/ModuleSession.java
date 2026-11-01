package io.github.vecluu.jdoc.app;

import io.github.vecluu.jdoc.indexer.IndexReport;
import io.github.vecluu.jdoc.lookup.LookupScope;
import io.github.vecluu.jdoc.lookup.LookupServices;
import io.github.vecluu.jdoc.model.ModuleClasspath;
import java.util.List;
import java.util.Objects;

/**
 * The current module, resolved and indexed, ready for lookups. Closing it releases the lookup services' per-call
 * resources (the javac parser); the database stays open.
 *
 * @param classpath the resolved classpath (module directory, status, warnings)
 * @param scope     the artifacts lookups are restricted to, JDK first
 * @param lookup    the lookup services for this call
 * @param report    what indexing did on the way
 */
public record ModuleSession(ModuleClasspath classpath, LookupScope scope, LookupServices lookup, IndexReport report)
        implements AutoCloseable {

    public ModuleSession {
        Objects.requireNonNull(classpath, "classpath");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(lookup, "lookup");
        Objects.requireNonNull(report, "report");
    }

    /** User-facing warnings of the resolution (stale classpath, unresolved versions), one line each. */
    public List<String> warnings() {
        return classpath.warnings();
    }

    @Override
    public void close() {
        lookup.close();
    }
}
