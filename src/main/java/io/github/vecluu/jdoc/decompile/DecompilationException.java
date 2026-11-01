package io.github.vecluu.jdoc.decompile;

import io.github.vecluu.jdoc.error.JdocException;

/** Decompiling a class failed: the jar could not be read, the class is missing or corrupt, or Vineflower failed. */
public class DecompilationException extends JdocException {

    public DecompilationException(String message) {
        super(message);
    }

    public DecompilationException(String message, Throwable cause) {
        super(message, cause);
    }
}
