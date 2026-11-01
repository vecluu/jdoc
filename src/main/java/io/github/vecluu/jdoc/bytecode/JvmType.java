package io.github.vecluu.jdoc.bytecode;

import java.util.List;
import java.util.Optional;

/**
 * A Java type as encoded in a class file descriptor or generic {@code Signature} attribute (JVMS 4.7.9.1).
 * Package-internal model produced by {@link SignatureParser} and rendered by {@link TypeRenderer}.
 */
sealed interface JvmType permits JvmType.Primitive, JvmType.ClassType, JvmType.TypeVariable, JvmType.ArrayType {

    /** A primitive type or {@code void}, by its Java keyword. */
    record Primitive(String keyword) implements JvmType {
    }

    /** A type variable reference, {@code T}. */
    record TypeVariable(String name) implements JvmType {
    }

    /** An array type. */
    record ArrayType(JvmType component) implements JvmType {
    }

    /**
     * A class or interface type. The first segment carries the full internal name ({@code java/util/Map}),
     * further segments are simple names of inner classes of a parameterized outer type ({@code Outer<T>.Inner}).
     */
    record ClassType(List<Segment> segments) implements JvmType {
        public ClassType {
            segments = List.copyOf(segments);
        }

        /** A non-generic class type from its internal name. */
        static ClassType of(String internalName) {
            return new ClassType(List.of(new Segment(internalName, List.of())));
        }

        /** The internal binary name of the whole type, {@code a/b/Outer$Inner}. */
        String internalName() {
            StringBuilder sb = new StringBuilder(segments.getFirst().name());
            for (int i = 1; i < segments.size(); i++) {
                sb.append('$').append(segments.get(i).name());
            }
            return sb.toString();
        }

        boolean isObject() {
            return internalName().equals("java/lang/Object");
        }
    }

    /** One segment of a {@link ClassType}: a name plus its type arguments. */
    record Segment(String name, List<TypeArgument> arguments) {
        public Segment {
            arguments = List.copyOf(arguments);
        }
    }

    /** Wildcard kind of a type argument. */
    enum Variance {
        EXACT, EXTENDS, SUPER, UNBOUNDED
    }

    /** A type argument: exact type, {@code ? extends}, {@code ? super} or {@code ?} (no type). */
    record TypeArgument(Variance variance, Optional<JvmType> type) {
    }

    /** A declared type parameter with its optional class bound and interface bounds. */
    record TypeParameter(String name, Optional<JvmType> classBound, List<JvmType> interfaceBounds) {
        public TypeParameter {
            interfaceBounds = List.copyOf(interfaceBounds);
        }
    }

    /** A parsed method descriptor or signature. */
    record MethodType(List<TypeParameter> typeParameters, List<JvmType> parameters, JvmType returnType,
                      List<JvmType> throwsTypes) {
        public MethodType {
            typeParameters = List.copyOf(typeParameters);
            parameters = List.copyOf(parameters);
            throwsTypes = List.copyOf(throwsTypes);
        }
    }

    /** A parsed class signature. */
    record ClassSignature(List<TypeParameter> typeParameters, ClassType superclass, List<ClassType> interfaces) {
        public ClassSignature {
            typeParameters = List.copyOf(typeParameters);
            interfaces = List.copyOf(interfaces);
        }
    }
}
