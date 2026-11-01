package io.github.vecluu.jdoc.lookup;

import io.github.vecluu.jdoc.model.MemberDescriptor;
import io.github.vecluu.jdoc.model.MemberKind;
import io.github.vecluu.jdoc.model.ParameterInfo;
import io.github.vecluu.jdoc.query.ParameterTypes;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Decides whether a member of a subtype overrides (methods) or hides (fields) a member of a supertype, without type
 * attribution.
 *
 * <p>Fields and enum constants match by name. Methods match by name and erased parameter types; a supertype
 * parameter declared as a bare type variable ({@code T}, {@code E[]}) matches any type in that position, so
 * {@code compareTo(Shape)} overrides {@code Comparable.compareTo(T)}. Constructors never override. Stateless.
 */
public final class OverrideMatcher {

    private static final Pattern TYPE_VARIABLE = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*(\\[])*(\\.\\.\\.)?");

    /** True if {@code sub} overrides or hides {@code sup}. */
    public boolean overrides(MemberDescriptor sub, MemberDescriptor sup) {
        if (!sub.name().equals(sup.name())) {
            return false;
        }
        if (sub.kind() == MemberKind.CONSTRUCTOR || sup.kind() == MemberKind.CONSTRUCTOR) {
            return false;
        }
        if (!sub.kind().isExecutable() || !sup.kind().isExecutable()) {
            return !sub.kind().isExecutable() && !sup.kind().isExecutable();
        }
        List<ParameterInfo> subParams = sub.parameters();
        List<ParameterInfo> supParams = sup.parameters();
        if (subParams.size() != supParams.size()) {
            return false;
        }
        for (int i = 0; i < subParams.size(); i++) {
            if (!parameterMatches(subParams.get(i), supParams.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean parameterMatches(ParameterInfo sub, ParameterInfo sup) {
        String subErased = ParameterTypes.normalizeLenient(sub.erasedType());
        String supErased = ParameterTypes.normalizeLenient(sup.erasedType());
        if (subErased.equals(supErased)) {
            return true;
        }
        return isTypeVariable(sup) && arrayDepth(subErased) >= arrayDepth(supErased);
    }

    /** A declared type that is a bare identifier different from its erasure: {@code T} erased to {@code Object}. */
    private static boolean isTypeVariable(ParameterInfo parameter) {
        String declared = parameter.type().strip();
        if (!TYPE_VARIABLE.matcher(declared).matches()) {
            return false;
        }
        String declaredBase = declared.replace("...", "").replace("[]", "");
        String erasedBase = ParameterTypes.normalizeLenient(parameter.erasedType()).replace("[]", "");
        return !declaredBase.equals(erasedBase);
    }

    private static int arrayDepth(String type) {
        int depth = 0;
        int index = type.indexOf("[]");
        while (index >= 0) {
            depth++;
            index = type.indexOf("[]", index + 2);
        }
        return depth;
    }
}
