package io.github.vecluu.jdoc.bytecode;

import io.github.vecluu.jdoc.bytecode.JvmType.ArrayType;
import io.github.vecluu.jdoc.bytecode.JvmType.ClassType;
import io.github.vecluu.jdoc.bytecode.JvmType.Primitive;
import io.github.vecluu.jdoc.bytecode.JvmType.Segment;
import io.github.vecluu.jdoc.bytecode.JvmType.TypeArgument;
import io.github.vecluu.jdoc.bytecode.JvmType.TypeParameter;
import io.github.vecluu.jdoc.bytecode.JvmType.TypeVariable;
import io.github.vecluu.jdoc.model.TypeName;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Renders {@link JvmType}s with simple names, the way source parsing renders resolved type names:
 *
 * <ul>
 *   <li>top-level types by simple name ({@code java.util.List<java.lang.String>} → {@code List<String>});</li>
 *   <li>nested types by their name relative to the package ({@code java.util.Map$Entry} → {@code Map.Entry});</li>
 *   <li>inner types of parameterized outer types as a chain ({@code Outer<T>.Inner<U>});</li>
 *   <li>type arguments joined by {@code ", "}; wildcards as {@code ?}, {@code ? extends X}, {@code ? super X};
 *       arrays as {@code X[]}; type parameter bounds joined by {@code " & "}, an {@code Object} bound omitted.</li>
 * </ul>
 * Stateless.
 *
 * <p>Not to be confused with the {@code source} package's {@code TypeRenderer}, which renders the same format from
 * javac trees; a rule changed here must change there too.
 */
final class TypeRenderer {

    /** The type as written in source: {@code Map<String, List<Shape>>}. */
    String render(JvmType type) {
        return switch (type) {
            case Primitive p -> p.keyword();
            case TypeVariable v -> v.name();
            case ArrayType a -> render(a.component()) + "[]";
            case ClassType c -> renderClass(c);
        };
    }

    /** The erasure's simple form, without type arguments: {@code Map}, {@code int[]}, type variables kept. */
    String renderErased(JvmType type) {
        return switch (type) {
            case Primitive p -> p.keyword();
            case TypeVariable v -> v.name();
            case ArrayType a -> renderErased(a.component()) + "[]";
            case ClassType c -> name(TypeName.fromInternalName(c.internalName()));
        };
    }

    /** A type name in simple form, {@code Map.Entry}. */
    String name(TypeName type) {
        return type.relativeName();
    }

    /** Type parameter list, {@code <K, V extends Comparable<? super V>>}; empty string if there are none. */
    String renderTypeParameters(List<TypeParameter> parameters) {
        if (parameters.isEmpty()) {
            return "";
        }
        return parameters.stream().map(this::renderTypeParameter).collect(Collectors.joining(", ", "<", ">"));
    }

    private String renderTypeParameter(TypeParameter parameter) {
        List<String> bounds = new ArrayList<>();
        parameter.classBound()
                .filter(bound -> !(bound instanceof ClassType c && c.isObject()))
                .ifPresent(bound -> bounds.add(render(bound)));
        parameter.interfaceBounds().forEach(bound -> bounds.add(render(bound)));
        return bounds.isEmpty() ? parameter.name() : parameter.name() + " extends " + String.join(" & ", bounds);
    }

    private String renderClass(ClassType type) {
        List<Segment> segments = type.segments();
        if (segments.size() == 1) {
            return name(TypeName.fromInternalName(type.internalName())) + arguments(segments.getFirst().arguments());
        }
        TypeName outer = TypeName.fromInternalName(segments.getFirst().name());
        StringBuilder sb = new StringBuilder(name(outer)).append(arguments(segments.getFirst().arguments()));
        for (int i = 1; i < segments.size(); i++) {
            sb.append('.').append(segments.get(i).name()).append(arguments(segments.get(i).arguments()));
        }
        return sb.toString();
    }

    private String arguments(List<TypeArgument> arguments) {
        if (arguments.isEmpty()) {
            return "";
        }
        return arguments.stream().map(this::renderArgument).collect(Collectors.joining(", ", "<", ">"));
    }

    private String renderArgument(TypeArgument argument) {
        return switch (argument.variance()) {
            case UNBOUNDED -> "?";
            case EXTENDS -> "? extends " + render(argument.type().orElseThrow());
            case SUPER -> "? super " + render(argument.type().orElseThrow());
            case EXACT -> render(argument.type().orElseThrow());
        };
    }
}
