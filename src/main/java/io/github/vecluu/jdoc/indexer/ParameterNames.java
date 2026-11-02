package io.github.vecluu.jdoc.indexer;

import io.github.vecluu.jdoc.model.MemberDescriptor;
import io.github.vecluu.jdoc.model.ParameterInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Puts real parameter names (from a javadoc page) into a member read from bytecode, whose names are usually
 * {@code arg0}, {@code arg1}: the {@link ParameterInfo}s get the names and the stored signature is re-rendered.
 * The caller decides that the names belong to the member (exact name and erased-type match); this class only
 * refuses names it cannot apply safely and then keeps the member unchanged.
 */
final class ParameterNames {

    private ParameterNames() {
    }

    /**
     * The member with {@code names} as parameter names; unchanged if the count differs, a name is not a Java
     * identifier, two names are equal, or the signature does not contain the member's current parameter list.
     */
    static MemberDescriptor apply(MemberDescriptor member, List<String> names) {
        List<ParameterInfo> current = member.parameters();
        if (names.size() != current.size() || names.isEmpty() || !names.stream().allMatch(ParameterNames::isIdentifier)
                || names.stream().distinct().count() != names.size()) {
            return member;
        }
        List<ParameterInfo> renamed = new ArrayList<>(current.size());
        for (int i = 0; i < current.size(); i++) {
            ParameterInfo parameter = current.get(i);
            renamed.add(new ParameterInfo(names.get(i), parameter.type(), parameter.erasedType()));
        }
        if (renamed.equals(current)) {
            return member;
        }
        String oldList = member.name() + parameterList(current);
        String signature = member.signature();
        int at = signature.indexOf(oldList);
        if (at < 0 || (at > 0 && signature.charAt(at - 1) != ' ')) {
            return member;
        }
        String newSignature = signature.substring(0, at) + member.name() + parameterList(renamed)
                + signature.substring(at + oldList.length());
        return member.toBuilder().parameters(renamed).signature(newSignature).build();
    }

    private static String parameterList(List<ParameterInfo> parameters) {
        return parameters.stream().map(ParameterInfo::declaration).collect(Collectors.joining(", ", "(", ")"));
    }

    private static boolean isIdentifier(String text) {
        if (text == null || text.isEmpty() || !Character.isJavaIdentifierStart(text.charAt(0))) {
            return false;
        }
        return text.chars().allMatch(Character::isJavaIdentifierPart);
    }
}
