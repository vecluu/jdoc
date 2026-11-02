package io.github.vecluu.jdoc.app;

import io.github.vecluu.jdoc.indexer.ClasspathIndexer;
import io.github.vecluu.jdoc.indexer.IndexOptions;
import io.github.vecluu.jdoc.indexer.IndexResult;
import io.github.vecluu.jdoc.lookup.LookupScope;
import io.github.vecluu.jdoc.model.ModuleClasspath;
import io.github.vecluu.jdoc.module.ClasspathResolver;
import java.nio.file.Path;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The real {@link ModuleContext}: the classpath resolver (Maven only on a POM change), the classpath indexer (only
 * new or changed jars), then the lookup scope with the JDK first. Confined to one thread, like the indexer.
 */
@Slf4j
@RequiredArgsConstructor
public final class IndexingModuleContext implements ModuleContext {

    private final ClasspathResolver resolver;
    private final ClasspathIndexer indexer;
    private final LookupServicesFactory lookupServices;
    private final Path workingDirectory;

    @Override
    public ModuleSession open(Optional<Path> from, IndexOptions options) {
        long start = System.nanoTime();
        ModuleClasspath classpath = resolver.resolve(from.orElse(workingDirectory));
        long resolved = System.nanoTime();
        IndexResult indexed = indexer.index(classpath, options);
        LookupScope scope = LookupScopes.from(indexed.classpath());
        log.debug("module {}: resolved in {} ms, indexed in {} ms ({} artifacts in scope)",
                classpath.moduleDirectory(), (resolved - start) / 1_000_000, (System.nanoTime() - resolved) / 1_000_000,
                scope.artifacts().size());
        return new ModuleSession(classpath, scope, lookupServices.create(), indexed.report());
    }
}
