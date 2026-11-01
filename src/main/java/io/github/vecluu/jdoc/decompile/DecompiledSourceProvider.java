package io.github.vecluu.jdoc.decompile;

import io.github.vecluu.jdoc.model.Gav;
import io.github.vecluu.jdoc.model.TypeName;
import java.nio.file.Path;

/** Supplies decompiled source text of a library type, typically from a cache. Not used for JDK types. */
public interface DecompiledSourceProvider {

    /**
     * Returns the decompiled source of the top-level type enclosing {@code type}.
     *
     * @param gav  coordinates of the artifact the jar belongs to (selects the cache directory)
     * @param jar  the binary jar
     * @param type any binary name inside the top-level type
     * @return the compilation unit source text
     * @throws DecompilationException if decompiling fails
     * @throws io.github.vecluu.jdoc.error.CacheIoException if the cache cannot be read or written
     */
    String source(Gav gav, Path jar, TypeName type);
}
