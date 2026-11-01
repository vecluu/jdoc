package io.github.vecluu.jdoc.decompile;

import io.github.vecluu.jdoc.model.TypeName;
import java.nio.file.Path;

/** Turns the bytecode of one top-level type of a binary jar into Java source text. */
public interface Decompiler {

    /**
     * Decompiles the top-level type enclosing {@code type} (a nested type selects its outermost type), including
     * all its nested, inner, local and anonymous classes.
     *
     * @param jar  the binary jar containing the class entries
     * @param type any binary name inside the top-level type
     * @return the compilation unit source text
     * @throws DecompilationException if the jar cannot be read, the class is absent or decompiling fails
     */
    String decompile(Path jar, TypeName type);
}
