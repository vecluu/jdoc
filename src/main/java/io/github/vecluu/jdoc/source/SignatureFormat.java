package io.github.vecluu.jdoc.source;

import io.github.vecluu.jdoc.model.ParameterInfo;
import io.github.vecluu.jdoc.model.Visibility;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Assembles rendered signatures in the architecture's format (no {@code public}, no annotations, no bodies):
 * <ul>
 *   <li>methods: {@code [protected ][static ][abstract |default ][<T> ]ReturnType name(Type a)[ throws X, Y]}</li>
 *   <li>constructors: {@code [protected ][<T> ]Name(Type a)[ throws X]}</li>
 *   <li>fields: {@code [protected ][static ][final ]Type NAME}</li>
 *   <li>enum constants: {@code NAME}</li>
 * </ul>
 * Only {@code protected} is shown as an access modifier. Implicit interface modifiers are left out by the caller:
 * no {@code abstract} on interface or annotation methods, no {@code static final} on interface fields.
 */
final class SignatureFormat {

    /** Method modifiers relevant to a signature. */
    enum MethodStyle { PLAIN, ABSTRACT, DEFAULT }

    String method(Visibility visibility, boolean isStatic, MethodStyle style, String typeParameters,
                  String returnType, String name, List<ParameterInfo> parameters, List<String> thrown) {
        StringBuilder sb = new StringBuilder(access(visibility));
        if (isStatic) {
            sb.append("static ");
        }
        switch (style) {
            case ABSTRACT -> sb.append("abstract ");
            case DEFAULT -> sb.append("default ");
            case PLAIN -> {
                // no keyword
            }
        }
        appendTypeParameters(sb, typeParameters);
        sb.append(returnType).append(' ').append(name);
        appendParametersAndThrows(sb, parameters, thrown);
        return sb.toString();
    }

    String constructor(Visibility visibility, String typeParameters, String simpleName,
                       List<ParameterInfo> parameters, List<String> thrown) {
        StringBuilder sb = new StringBuilder(access(visibility));
        appendTypeParameters(sb, typeParameters);
        sb.append(simpleName);
        appendParametersAndThrows(sb, parameters, thrown);
        return sb.toString();
    }

    String field(Visibility visibility, boolean isStatic, boolean isFinal, String type, String name) {
        return access(visibility) + (isStatic ? "static " : "") + (isFinal ? "final " : "") + type + " " + name;
    }

    String enumConstant(String name) {
        return name;
    }

    private static String access(Visibility visibility) {
        return visibility == Visibility.PROTECTED ? "protected " : "";
    }

    private static void appendTypeParameters(StringBuilder sb, String typeParameters) {
        if (!typeParameters.isEmpty()) {
            sb.append(typeParameters).append(' ');
        }
    }

    private static void appendParametersAndThrows(StringBuilder sb, List<ParameterInfo> parameters,
                                                  List<String> thrown) {
        sb.append(parameters.stream().map(ParameterInfo::declaration).collect(Collectors.joining(", ", "(", ")")));
        if (!thrown.isEmpty()) {
            sb.append(" throws ").append(String.join(", ", thrown));
        }
    }
}
