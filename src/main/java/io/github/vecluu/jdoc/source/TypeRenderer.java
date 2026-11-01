package io.github.vecluu.jdoc.source;

import com.sun.source.tree.AnnotatedTypeTree;
import com.sun.source.tree.ArrayTypeTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.IntersectionTypeTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.ParameterizedTypeTree;
import com.sun.source.tree.PrimitiveTypeTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TypeParameterTree;
import com.sun.source.tree.UnionTypeTree;
import com.sun.source.tree.WildcardTree;
import io.github.vecluu.jdoc.model.TypeName;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Renders type trees in the signature format: simple names (nested types as {@code Outer.Inner}, resolved through
 * {@link TypeNameResolver}), type arguments separated by {@code ", "}, annotations dropped. Also computes erased
 * simple types for {@code ParameterInfo}: type arguments removed, type variables replaced by their first bound
 * (else {@code Object}).
 */
final class TypeRenderer {

    private static final String OBJECT = "Object";

    private final TypeNameResolver resolver;

    TypeRenderer(TypeNameResolver resolver) {
        this.resolver = resolver;
    }

    /** The type as shown in a signature: {@code Map.Entry<K, V>}, {@code List<? extends T>}, {@code int[]}. */
    String render(Tree type, TypeScope scope) {
        return switch (type) {
            case PrimitiveTypeTree primitive -> primitive.getPrimitiveTypeKind().name().toLowerCase(Locale.ROOT);
            case IdentifierTree identifier -> className(identifier.getName().toString(), scope);
            case MemberSelectTree select when select.getExpression() instanceof ParameterizedTypeTree outer ->
                    render(outer, scope) + "." + select.getIdentifier();
            case MemberSelectTree select -> className(dottedName(select), scope);
            case ParameterizedTypeTree parameterized -> render(parameterized.getType(), scope)
                    + parameterized.getTypeArguments().stream()
                    .map(argument -> render(argument, scope))
                    .collect(Collectors.joining(", ", "<", ">"));
            case ArrayTypeTree array -> render(array.getType(), scope) + "[]";
            case AnnotatedTypeTree annotated -> render(annotated.getUnderlyingType(), scope);
            case WildcardTree wildcard -> wildcard(wildcard, scope);
            case IntersectionTypeTree intersection -> join(intersection.getBounds(), " & ", scope);
            case UnionTypeTree union -> join(union.getTypeAlternatives(), " | ", scope);
            default -> type.toString();
        };
    }

    /** Type parameter text: {@code <T extends Comparable<? super T>, U>}; empty if there are none. */
    String typeParameters(List<? extends TypeParameterTree> parameters, TypeScope scope) {
        if (parameters.isEmpty()) {
            return "";
        }
        return parameters.stream()
                .map(parameter -> parameter.getName() + (parameter.getBounds().isEmpty()
                        ? ""
                        : " extends " + join(parameter.getBounds(), " & ", scope)))
                .collect(Collectors.joining(", ", "<", ">"));
    }

    /** The erased simple type: {@code List}, {@code Map.Entry}, {@code Object[]}, {@code Comparable}. */
    String erase(Tree type, TypeScope scope) {
        return erase(type, scope, new HashSet<>());
    }

    /** The binary name a written class name refers to (best effort, see {@link TypeNameResolver}). */
    TypeName resolve(String written, TypeScope scope) {
        return resolver.resolve(written, scope.enclosingType());
    }

    /** The dotted name written in a class type, without type arguments or annotations. */
    static String dottedName(Tree type) {
        return switch (type) {
            case IdentifierTree identifier -> identifier.getName().toString();
            case MemberSelectTree select -> dottedName(select.getExpression()) + "." + select.getIdentifier();
            case ParameterizedTypeTree parameterized -> dottedName(parameterized.getType());
            case AnnotatedTypeTree annotated -> dottedName(annotated.getUnderlyingType());
            default -> type.toString();
        };
    }

    private String erase(Tree type, TypeScope scope, Set<String> visiting) {
        return switch (type) {
            case PrimitiveTypeTree primitive -> primitive.getPrimitiveTypeKind().name().toLowerCase(Locale.ROOT);
            case IdentifierTree identifier when scope.isTypeVariable(identifier.getName().toString()) ->
                    eraseVariable(identifier.getName().toString(), scope, visiting);
            case IdentifierTree identifier -> className(identifier.getName().toString(), scope);
            case MemberSelectTree select -> className(dottedName(select), scope);
            case ParameterizedTypeTree parameterized -> erase(parameterized.getType(), scope, visiting);
            case ArrayTypeTree array -> erase(array.getType(), scope, visiting) + "[]";
            case AnnotatedTypeTree annotated -> erase(annotated.getUnderlyingType(), scope, visiting);
            case IntersectionTypeTree intersection -> erase(intersection.getBounds().getFirst(), scope, visiting);
            default -> OBJECT;
        };
    }

    private String eraseVariable(String name, TypeScope scope, Set<String> visiting) {
        Optional<Tree> bound = scope.typeVariables().get(name);
        if (bound.isEmpty() || !visiting.add(name)) {
            return OBJECT;
        }
        return erase(bound.get(), scope, visiting);
    }

    private String className(String written, TypeScope scope) {
        if (scope.isTypeVariable(written)) {
            return written;
        }
        return resolver.resolve(written, scope.enclosingType()).relativeName();
    }

    private String wildcard(WildcardTree wildcard, TypeScope scope) {
        return switch (wildcard.getKind()) {
            case EXTENDS_WILDCARD -> "? extends " + render(wildcard.getBound(), scope);
            case SUPER_WILDCARD -> "? super " + render(wildcard.getBound(), scope);
            default -> "?";
        };
    }

    private String join(List<? extends Tree> types, String separator, TypeScope scope) {
        return types.stream().map(type -> render(type, scope)).collect(Collectors.joining(separator));
    }
}
