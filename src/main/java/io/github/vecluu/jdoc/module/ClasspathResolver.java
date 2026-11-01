package io.github.vecluu.jdoc.module;

import io.github.vecluu.jdoc.model.ModuleClasspath;
import java.nio.file.Path;

/** Produces the resolved classpath of the Maven module enclosing a path. */
public interface ClasspathResolver {

    /**
     * Resolves the classpath of the module enclosing {@code start}.
     *
     * @param start a file or directory (the {@code --from} path, or the working directory); relative paths are
     *              resolved against the working directory
     * @return the classpath, without the project's own modules
     * @throws io.github.vecluu.jdoc.error.ResolutionException if there is no enclosing module, or Maven failed and
     *                                                         no fallback list is usable
     */
    ModuleClasspath resolve(Path start);
}
