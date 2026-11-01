package io.github.vecluu.jdoc.bytecode;

import io.github.vecluu.jdoc.model.TypeKind;
import io.github.vecluu.jdoc.model.Visibility;
import org.objectweb.asm.Opcodes;

/** Pure mappings from class-file access flags to model values. */
final class AccessFlags {

    private AccessFlags() {
    }

    static boolean has(int access, int flag) {
        return (access & flag) != 0;
    }

    /** Visibility from {@code ACC_PUBLIC}/{@code ACC_PROTECTED}/{@code ACC_PRIVATE}, else package-private. */
    static Visibility visibility(int access) {
        if (has(access, Opcodes.ACC_PUBLIC)) {
            return Visibility.PUBLIC;
        }
        if (has(access, Opcodes.ACC_PROTECTED)) {
            return Visibility.PROTECTED;
        }
        if (has(access, Opcodes.ACC_PRIVATE)) {
            return Visibility.PRIVATE;
        }
        return Visibility.PACKAGE;
    }

    /**
     * Type kind from class access flags; {@code superName} is the internal superclass name (records are also
     * recognised by extending {@code java/lang/Record} when ASM's {@code ACC_RECORD} pseudo flag is missing).
     */
    static TypeKind kind(int access, String superName) {
        if (has(access, Opcodes.ACC_ANNOTATION)) {
            return TypeKind.ANNOTATION;
        }
        if (has(access, Opcodes.ACC_INTERFACE)) {
            return TypeKind.INTERFACE;
        }
        if (has(access, Opcodes.ACC_ENUM)) {
            return TypeKind.ENUM;
        }
        if (has(access, Opcodes.ACC_RECORD) || "java/lang/Record".equals(superName)) {
            return TypeKind.RECORD;
        }
        return TypeKind.CLASS;
    }
}
