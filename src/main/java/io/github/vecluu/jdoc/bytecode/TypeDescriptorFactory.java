package io.github.vecluu.jdoc.bytecode;

import io.github.vecluu.jdoc.bytecode.JvmType.ClassSignature;
import io.github.vecluu.jdoc.model.DocSource;
import io.github.vecluu.jdoc.model.TypeDescriptor;
import io.github.vecluu.jdoc.model.TypeKind;
import io.github.vecluu.jdoc.model.TypeName;
import io.github.vecluu.jdoc.model.Visibility;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.objectweb.asm.Opcodes;

/**
 * Builds the {@link TypeDescriptor} of a {@link RawClass}.
 *
 * <p>Modifiers text: visibility keyword, then {@code abstract} (classes only; implicit for interfaces),
 * {@code static} (nested types declared static, per {@code InnerClasses}), {@code final} (classes only; implicit
 * for enums and records), {@code sealed} (classes and interfaces with {@code PermittedSubclasses}). Supertypes are
 * the superclass (also the implicit {@code Object}, {@code Enum} or {@code Record}; none for interfaces and
 * annotation types) followed by the direct superinterfaces (for annotation types {@code Annotation}), as source
 * parsing reports them.
 */
final class TypeDescriptorFactory {

    private final SignatureParser parser;

    TypeDescriptorFactory(SignatureParser parser) {
        this.parser = parser;
    }

    TypeDescriptor create(RawClass raw) {
        TypeName name = TypeName.fromInternalName(raw.internalName());
        int access = effectiveAccess(raw);
        TypeKind kind = AccessFlags.kind(raw.access(), raw.superName().orElse(null));
        return TypeDescriptor.builder()
                .name(name)
                .kind(kind)
                .visibility(AccessFlags.visibility(access))
                .modifiers(modifiers(raw, access, kind))
                .typeParameters(typeParameters(raw))
                .supertypes(supertypes(raw, kind))
                .deprecated(raw.deprecated() || AccessFlags.has(raw.access(), Opcodes.ACC_DEPRECATED))
                .firstSentence("")
                .docSource(DocSource.DECOMPILED)
                .entryPath(name.classEntryPath())
                .declarationRange(Optional.empty())
                .build();
    }

    /** Access flags as declared: the own {@code InnerClasses} entry for nested types, else the class flags. */
    static int effectiveAccess(RawClass raw) {
        return raw.ownEntry().map(RawClass.InnerClassEntry::access).orElse(raw.access());
    }

    private static String modifiers(RawClass raw, int access, TypeKind kind) {
        List<String> words = new ArrayList<>();
        Visibility visibility = AccessFlags.visibility(access);
        if (!visibility.keyword().isEmpty()) {
            words.add(visibility.keyword());
        }
        if (kind == TypeKind.CLASS && AccessFlags.has(access, Opcodes.ACC_ABSTRACT)) {
            words.add("abstract");
        }
        if (raw.ownEntry().isPresent() && AccessFlags.has(access, Opcodes.ACC_STATIC)) {
            words.add("static");
        }
        if (kind == TypeKind.CLASS && AccessFlags.has(access, Opcodes.ACC_FINAL)) {
            words.add("final");
        }
        if (raw.sealed() && (kind == TypeKind.CLASS || kind == TypeKind.INTERFACE)) {
            words.add("sealed");
        }
        return String.join(" ", words);
    }

    private String typeParameters(RawClass raw) {
        if (raw.signature().isEmpty()) {
            return "";
        }
        try {
            ClassSignature signature = parser.parseClass(raw.signature().get());
            return new TypeRenderer().renderTypeParameters(signature.typeParameters());
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    private static List<TypeName> supertypes(RawClass raw, TypeKind kind) {
        List<TypeName> result = new ArrayList<>();
        if (kind != TypeKind.INTERFACE && kind != TypeKind.ANNOTATION) {
            raw.superName().ifPresent(superName -> result.add(TypeName.fromInternalName(superName)));
        }
        raw.interfaces().forEach(iface -> result.add(TypeName.fromInternalName(iface)));
        return result;
    }
}
