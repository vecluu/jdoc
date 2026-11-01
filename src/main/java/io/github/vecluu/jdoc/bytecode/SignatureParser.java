package io.github.vecluu.jdoc.bytecode;

import io.github.vecluu.jdoc.bytecode.JvmType.ArrayType;
import io.github.vecluu.jdoc.bytecode.JvmType.ClassSignature;
import io.github.vecluu.jdoc.bytecode.JvmType.ClassType;
import io.github.vecluu.jdoc.bytecode.JvmType.MethodType;
import io.github.vecluu.jdoc.bytecode.JvmType.Primitive;
import io.github.vecluu.jdoc.bytecode.JvmType.Segment;
import io.github.vecluu.jdoc.bytecode.JvmType.TypeArgument;
import io.github.vecluu.jdoc.bytecode.JvmType.TypeParameter;
import io.github.vecluu.jdoc.bytecode.JvmType.TypeVariable;
import io.github.vecluu.jdoc.bytecode.JvmType.Variance;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Parses field/method descriptors and generic signatures (JVMS 4.3 and 4.7.9.1) into {@link JvmType}s.
 * Descriptors are a subset of the signature grammar, so one parser handles both. Stateless; each call uses its
 * own cursor. Malformed input throws {@link IllegalArgumentException}.
 */
final class SignatureParser {

    /** Parses a field descriptor or field signature. */
    JvmType parseFieldType(String signature) {
        Cursor c = new Cursor(signature);
        JvmType type = c.javaType();
        c.expectEnd();
        return type;
    }

    /** Parses a method descriptor or method signature. */
    MethodType parseMethod(String signature) {
        Cursor c = new Cursor(signature);
        List<TypeParameter> typeParameters = c.peek() == '<' ? c.typeParameters() : List.of();
        c.expect('(');
        List<JvmType> parameters = new ArrayList<>();
        while (c.peek() != ')') {
            parameters.add(c.javaType());
        }
        c.expect(')');
        JvmType returnType = c.javaType();
        List<JvmType> throwsTypes = new ArrayList<>();
        while (c.hasMore() && c.peek() == '^') {
            c.next();
            throwsTypes.add(c.referenceType());
        }
        c.expectEnd();
        return new MethodType(typeParameters, parameters, returnType, throwsTypes);
    }

    /** Parses a class signature. */
    ClassSignature parseClass(String signature) {
        Cursor c = new Cursor(signature);
        List<TypeParameter> typeParameters = c.peek() == '<' ? c.typeParameters() : List.of();
        ClassType superclass = c.classType();
        List<ClassType> interfaces = new ArrayList<>();
        while (c.hasMore()) {
            interfaces.add(c.classType());
        }
        return new ClassSignature(typeParameters, superclass, interfaces);
    }

    private static final class Cursor {
        private final String text;
        private int pos;

        Cursor(String text) {
            if (text == null || text.isEmpty()) {
                throw new IllegalArgumentException("empty signature");
            }
            this.text = text;
        }

        boolean hasMore() {
            return pos < text.length();
        }

        char peek() {
            if (!hasMore()) {
                throw error("unexpected end");
            }
            return text.charAt(pos);
        }

        char next() {
            char c = peek();
            pos++;
            return c;
        }

        void expect(char expected) {
            if (next() != expected) {
                pos--;
                throw error("expected '" + expected + "'");
            }
        }

        void expectEnd() {
            if (hasMore()) {
                throw error("trailing characters");
            }
        }

        IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " at " + pos + " in signature '" + text + "'");
        }

        JvmType javaType() {
            char c = peek();
            String keyword = switch (c) {
                case 'B' -> "byte";
                case 'C' -> "char";
                case 'D' -> "double";
                case 'F' -> "float";
                case 'I' -> "int";
                case 'J' -> "long";
                case 'S' -> "short";
                case 'Z' -> "boolean";
                case 'V' -> "void";
                default -> null;
            };
            if (keyword != null) {
                pos++;
                return new Primitive(keyword);
            }
            return referenceType();
        }

        JvmType referenceType() {
            return switch (peek()) {
                case 'L' -> classType();
                case 'T' -> {
                    pos++;
                    String name = identifier(";");
                    pos++;
                    yield new TypeVariable(name);
                }
                case '[' -> {
                    pos++;
                    yield new ArrayType(javaType());
                }
                default -> throw error("expected a reference type");
            };
        }

        ClassType classType() {
            expect('L');
            List<Segment> segments = new ArrayList<>();
            String name = identifier("<.;");
            segments.add(new Segment(name, typeArgumentsIfAny()));
            while (peek() == '.') {
                pos++;
                String inner = identifier("<.;");
                segments.add(new Segment(inner, typeArgumentsIfAny()));
            }
            expect(';');
            return new ClassType(segments);
        }

        private List<TypeArgument> typeArgumentsIfAny() {
            if (peek() != '<') {
                return List.of();
            }
            pos++;
            List<TypeArgument> arguments = new ArrayList<>();
            while (peek() != '>') {
                arguments.add(typeArgument());
            }
            pos++;
            return arguments;
        }

        private TypeArgument typeArgument() {
            return switch (peek()) {
                case '*' -> {
                    pos++;
                    yield new TypeArgument(Variance.UNBOUNDED, Optional.empty());
                }
                case '+' -> {
                    pos++;
                    yield new TypeArgument(Variance.EXTENDS, Optional.of(referenceType()));
                }
                case '-' -> {
                    pos++;
                    yield new TypeArgument(Variance.SUPER, Optional.of(referenceType()));
                }
                default -> new TypeArgument(Variance.EXACT, Optional.of(referenceType()));
            };
        }

        List<TypeParameter> typeParameters() {
            expect('<');
            List<TypeParameter> parameters = new ArrayList<>();
            while (peek() != '>') {
                String name = identifier(":");
                expect(':');
                Optional<JvmType> classBound = Optional.empty();
                if (peek() != ':' && peek() != '>' && isReferenceStart(peek())) {
                    classBound = Optional.of(referenceType());
                }
                List<JvmType> interfaceBounds = new ArrayList<>();
                while (peek() == ':') {
                    pos++;
                    interfaceBounds.add(referenceType());
                }
                parameters.add(new TypeParameter(name, classBound, interfaceBounds));
            }
            pos++;
            return parameters;
        }

        private static boolean isReferenceStart(char c) {
            return c == 'L' || c == 'T' || c == '[';
        }

        private String identifier(String terminators) {
            int start = pos;
            while (hasMore() && terminators.indexOf(text.charAt(pos)) < 0) {
                pos++;
            }
            if (pos == start) {
                throw error("expected an identifier");
            }
            if (!hasMore() && !terminators.isEmpty()) {
                throw error("unterminated identifier");
            }
            return text.substring(start, pos);
        }
    }
}
