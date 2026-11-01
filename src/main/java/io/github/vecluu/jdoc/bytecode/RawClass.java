package io.github.vecluu.jdoc.bytecode;

import java.util.List;
import java.util.Optional;

/**
 * Raw facts collected from one class file by {@link ClassCollector}, before any interpretation.
 *
 * @param access          class access flags (including ASM's pseudo flags {@code ACC_RECORD}, {@code ACC_DEPRECATED})
 * @param internalName    internal name, {@code a/b/Outer$Inner}
 * @param signature       generic class signature, if present
 * @param superName       internal name of the superclass, if any
 * @param interfaces      internal names of direct superinterfaces
 * @param innerClasses    entries of the {@code InnerClasses} attribute
 * @param local           true if an {@code EnclosingMethod} attribute is present (local or anonymous class)
 * @param sealed          true if a {@code PermittedSubclasses} attribute is present
 * @param deprecated      {@code @Deprecated} annotation present
 * @param fields          fields in class-file order
 * @param methods         methods in class-file order
 */
record RawClass(int access, String internalName, Optional<String> signature, Optional<String> superName,
                List<String> interfaces, List<InnerClassEntry> innerClasses, boolean local, boolean sealed,
                boolean deprecated, List<RawField> fields, List<RawMethod> methods) {

    RawClass {
        interfaces = List.copyOf(interfaces);
        innerClasses = List.copyOf(innerClasses);
        fields = List.copyOf(fields);
        methods = List.copyOf(methods);
    }

    /** The {@code InnerClasses} entry describing this class itself, if it is a nested class. */
    Optional<InnerClassEntry> ownEntry() {
        return entry(internalName);
    }

    /** The {@code InnerClasses} entry for the given internal name. */
    Optional<InnerClassEntry> entry(String name) {
        return innerClasses.stream().filter(e -> e.name().equals(name)).findFirst();
    }

    /** One {@code InnerClasses} attribute entry; outer and inner names are absent for local/anonymous classes. */
    record InnerClassEntry(String name, Optional<String> outerName, Optional<String> innerName, int access) {
    }

    /** One field. */
    record RawField(int access, String name, String descriptor, Optional<String> signature, boolean deprecated) {
    }

    /**
     * One method.
     *
     * @param parameterNames names from the {@code MethodParameters} attribute, in order; an entry is empty when the
     *                       attribute has no name for it; the whole list is empty when the attribute is absent
     */
    record RawMethod(int access, String name, String descriptor, Optional<String> signature, List<String> exceptions,
                     List<Optional<String>> parameterNames, boolean deprecated) {
        RawMethod {
            exceptions = List.copyOf(exceptions);
            parameterNames = List.copyOf(parameterNames);
        }
    }
}
