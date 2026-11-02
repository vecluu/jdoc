package io.github.vecluu.jdoc.bytecode;

import io.github.vecluu.jdoc.bytecode.JvmType.ArrayType;
import io.github.vecluu.jdoc.bytecode.JvmType.ClassType;
import io.github.vecluu.jdoc.bytecode.JvmType.MethodType;
import io.github.vecluu.jdoc.bytecode.RawClass.RawField;
import io.github.vecluu.jdoc.bytecode.RawClass.RawMethod;
import io.github.vecluu.jdoc.model.MemberDescriptor;
import io.github.vecluu.jdoc.model.MemberKind;
import io.github.vecluu.jdoc.model.ParameterInfo;
import io.github.vecluu.jdoc.model.TypeDescriptor;
import io.github.vecluu.jdoc.model.TypeKind;
import io.github.vecluu.jdoc.model.Visibility;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.objectweb.asm.Opcodes;

/**
 * Builds the {@link MemberDescriptor}s of a {@link RawClass} in the architecture's signature format.
 *
 * <p>Rules: only public and protected members; synthetic and bridge methods and {@code <clinit>} are skipped;
 * constructors take the simple type name and drop the outer-instance parameter of inner (non-static) classes;
 * enum constants are {@link MemberKind#ENUM_CONSTANT} with signature {@code NAME}; implicit enum {@code values()}
 * and {@code valueOf(String)} are kept. Implicit interface modifiers are left out, as source parsing does: no
 * {@code abstract} on interface and annotation methods (it stays on abstract class methods), {@code default} for
 * interface instance methods with a body, {@code static} for static interface methods, and no {@code static final}
 * on interface fields. Generic signatures are preferred; malformed ones fall back to descriptors.
 *
 * <p>Must render exactly as source parsing does ({@code source.SignatureFormat}); change both together and bump
 * {@code index.Schema.VERSION}. {@code non-sealed} cannot be seen in bytecode.
 */
final class MemberDescriptorFactory {

    private final SignatureParser parser;
    private final RawClass raw;
    private final TypeDescriptor type;
    private final TypeRenderer renderer;

    MemberDescriptorFactory(SignatureParser parser, RawClass raw, TypeDescriptor type) {
        this.parser = parser;
        this.raw = raw;
        this.type = type;
        this.renderer = new TypeRenderer();
    }

    List<MemberDescriptor> create() {
        List<MemberDescriptor> members = new ArrayList<>();
        for (RawField field : raw.fields()) {
            if (isApi(field.access()) && !AccessFlags.has(field.access(), Opcodes.ACC_SYNTHETIC)) {
                members.add(field(field));
            }
        }
        for (RawMethod method : raw.methods()) {
            if (isApi(method.access()) && !isSkippedMethod(method)) {
                members.add(method(method));
            }
        }
        return members;
    }

    private static boolean isApi(int access) {
        return AccessFlags.visibility(access).isApi();
    }

    private static boolean isSkippedMethod(RawMethod method) {
        return method.name().equals("<clinit>")
                || AccessFlags.has(method.access(), Opcodes.ACC_SYNTHETIC)
                || AccessFlags.has(method.access(), Opcodes.ACC_BRIDGE);
    }

    private MemberDescriptor field(RawField field) {
        int access = field.access();
        boolean enumConstant = AccessFlags.has(access, Opcodes.ACC_ENUM);
        String signature;
        if (enumConstant) {
            signature = field.name();
        } else {
            JvmType fieldType = fieldType(field);
            List<String> words = new ArrayList<>();
            if (AccessFlags.visibility(access) == Visibility.PROTECTED) {
                words.add("protected");
            }
            if (AccessFlags.has(access, Opcodes.ACC_STATIC) && !isInterfaceLike()) {
                words.add("static");
            }
            if (AccessFlags.has(access, Opcodes.ACC_FINAL) && !isInterfaceLike()) {
                words.add("final");
            }
            words.add(renderer.render(fieldType));
            words.add(field.name());
            signature = String.join(" ", words);
        }
        return MemberDescriptor.builder()
                .declaringType(type.name())
                .name(field.name())
                .kind(enumConstant ? MemberKind.ENUM_CONSTANT : MemberKind.FIELD)
                .signature(signature)
                .parameters(List.of())
                .visibility(AccessFlags.visibility(access))
                .isStatic(enumConstant || AccessFlags.has(access, Opcodes.ACC_STATIC))
                .deprecated(field.deprecated() || AccessFlags.has(access, Opcodes.ACC_DEPRECATED))
                .firstSentence("")
                .build();
    }

    private JvmType fieldType(RawField field) {
        if (field.signature().isPresent()) {
            try {
                return parser.parseFieldType(field.signature().get());
            } catch (IllegalArgumentException e) {
                // fall back to the descriptor
            }
        }
        return parser.parseFieldType(field.descriptor());
    }

    private MemberDescriptor method(RawMethod method) {
        int access = method.access();
        boolean constructor = method.name().equals("<init>");
        String name = constructor ? type.name().simpleName() : method.name();
        MethodType erased = parser.parseMethod(method.descriptor());
        MethodType generic = genericMethod(method).orElse(null);

        List<JvmType> erasedParameters = erased.parameters();
        int dropped = 0;
        if (generic == null && constructor && isInnerClass() && !erasedParameters.isEmpty()) {
            dropped = 1;
        } else if (generic != null && generic.parameters().size() < erasedParameters.size()) {
            dropped = erasedParameters.size() - generic.parameters().size();
        }
        List<JvmType> erasedShown = erasedParameters.subList(dropped, erasedParameters.size());
        MethodType shown = generic != null ? generic
                : new MethodType(List.of(), erasedShown, erased.returnType(), List.of());
        boolean varargs = AccessFlags.has(access, Opcodes.ACC_VARARGS);
        List<ParameterInfo> parameters = parameters(method, shown.parameters(), erasedShown, dropped,
                erasedParameters.size(), varargs);
        List<JvmType> throwsTypes = !shown.throwsTypes().isEmpty() ? shown.throwsTypes()
                : method.exceptions().stream().<JvmType>map(ClassType::of).toList();

        StringBuilder sb = new StringBuilder();
        modifiers(access, constructor).forEach(word -> sb.append(word).append(' '));
        String typeParameters = renderer.renderTypeParameters(shown.typeParameters());
        if (!typeParameters.isEmpty()) {
            sb.append(typeParameters).append(' ');
        }
        if (!constructor) {
            sb.append(renderer.render(shown.returnType())).append(' ');
        }
        sb.append(name)
                .append(parameters.stream().map(ParameterInfo::declaration).collect(Collectors.joining(", ", "(", ")")));
        if (!throwsTypes.isEmpty()) {
            sb.append(throwsTypes.stream().map(renderer::render).collect(Collectors.joining(", ", " throws ", "")));
        }
        return MemberDescriptor.builder()
                .declaringType(type.name())
                .name(name)
                .kind(constructor ? MemberKind.CONSTRUCTOR : MemberKind.METHOD)
                .signature(sb.toString())
                .parameters(parameters)
                .visibility(AccessFlags.visibility(access))
                .isStatic(AccessFlags.has(access, Opcodes.ACC_STATIC))
                .deprecated(method.deprecated() || AccessFlags.has(access, Opcodes.ACC_DEPRECATED))
                .firstSentence("")
                .build();
    }

    private Optional<MethodType> genericMethod(RawMethod method) {
        if (method.signature().isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(parser.parseMethod(method.signature().get()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private boolean isInnerClass() {
        return raw.ownEntry().isPresent()
                && type.kind() == TypeKind.CLASS
                && !AccessFlags.has(TypeDescriptorFactory.effectiveAccess(raw), Opcodes.ACC_STATIC);
    }

    private List<String> modifiers(int access, boolean constructor) {
        List<String> words = new ArrayList<>();
        if (AccessFlags.visibility(access) == Visibility.PROTECTED) {
            words.add("protected");
        }
        if (constructor) {
            return words;
        }
        boolean isStatic = AccessFlags.has(access, Opcodes.ACC_STATIC);
        boolean isAbstract = AccessFlags.has(access, Opcodes.ACC_ABSTRACT);
        boolean inInterface = isInterfaceLike();
        if (isStatic) {
            words.add("static");
        }
        if (isAbstract && !inInterface) {
            words.add("abstract");
        } else if (!isAbstract && !isStatic && inInterface) {
            words.add("default");
        }
        return words;
    }

    private boolean isInterfaceLike() {
        return type.kind() == TypeKind.INTERFACE || type.kind() == TypeKind.ANNOTATION;
    }

    private List<ParameterInfo> parameters(RawMethod method, List<JvmType> shown, List<JvmType> erasedShown,
                                           int dropped, int descriptorCount, boolean varargs) {
        List<Optional<String>> names = parameterNames(method.parameterNames(), shown.size(), dropped,
                descriptorCount);
        List<ParameterInfo> parameters = new ArrayList<>();
        for (int i = 0; i < shown.size(); i++) {
            boolean last = i == shown.size() - 1;
            String typeText = renderer.render(shown.get(i));
            JvmType erasedType = i < erasedShown.size() ? erasedShown.get(i) : shown.get(i);
            String erasedText = renderer.renderErased(erasedType);
            if (varargs && last && shown.get(i) instanceof ArrayType && typeText.endsWith("[]")) {
                typeText = typeText.substring(0, typeText.length() - 2) + "...";
            }
            int index = i;
            String name = names.get(i).filter(n -> !n.isBlank()).orElseGet(() -> "arg" + index);
            parameters.add(new ParameterInfo(name, typeText, erasedText));
        }
        return parameters;
    }

    /** Aligns {@code MethodParameters} names with the shown parameters; unknown names are empty. */
    private static List<Optional<String>> parameterNames(List<Optional<String>> raw, int shownCount, int dropped,
                                                         int descriptorCount) {
        List<Optional<String>> aligned;
        if (raw.size() == shownCount) {
            aligned = raw;
        } else if (raw.size() == descriptorCount && descriptorCount - dropped == shownCount) {
            aligned = raw.subList(dropped, raw.size());
        } else {
            aligned = List.of();
        }
        List<Optional<String>> result = new ArrayList<>(aligned);
        while (result.size() < shownCount) {
            result.add(Optional.empty());
        }
        return result;
    }
}
