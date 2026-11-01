package io.github.vecluu.jdoc.javadoc;

import io.github.vecluu.jdoc.model.MemberDescriptor;
import io.github.vecluu.jdoc.model.MemberKind;
import io.github.vecluu.jdoc.model.ParameterInfo;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A member anchor of a javadoc page, parsed or generated, in either layout:
 *
 * <ul>
 *   <li>{@link Layout#MODERN} (JDK 10+): {@code of(java.lang.Object)}, {@code max(T,T)}, varargs
 *       {@code max(double...)}, constructors {@code <init>(double)}, fields {@code NAME};</li>
 *   <li>{@link Layout#LEGACY} (JDK 8): {@code of-java.lang.Object-}, {@code foo--}, arrays as {@code :A}, constructors
 *       by simple type name {@code Circle-double-}, fields {@code NAME}.</li>
 * </ul>
 *
 * @param name           member name; {@code <init>} for constructors in the modern layout
 * @param parameterTypes parameter types as written in the anchor; empty for fields and enum constants
 */
public record JavadocAnchor(String name, Optional<List<String>> parameterTypes) {

    /** Constructor name used by modern anchors. */
    public static final String CONSTRUCTOR_NAME = "<init>";

    /** Javadoc anchor layouts. */
    public enum Layout {
        /** JDK 10 and later: {@code name(a.B,int)}. */
        MODERN,
        /** JDK 8 and earlier: {@code name-a.B-int-}. */
        LEGACY
    }

    public JavadocAnchor {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("anchor name must not be blank");
        }
        parameterTypes = parameterTypes == null ? Optional.empty() : parameterTypes.map(List::copyOf);
    }

    /**
     * Parses an anchor id (percent-encoding allowed). Ids that are not member anchors (e.g. {@code method-summary},
     * {@code param-x}, {@code type-param-T}) yield empty.
     */
    public static Optional<JavadocAnchor> parse(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        String anchor = decode(id.strip());
        int paren = anchor.indexOf('(');
        if (paren > 0 && anchor.endsWith(")")) {
            String name = anchor.substring(0, paren);
            if (!isMemberName(name)) {
                return Optional.empty();
            }
            String inner = anchor.substring(paren + 1, anchor.length() - 1).strip();
            List<String> types = inner.isEmpty() ? List.of() : splitTopLevel(inner);
            return Optional.of(new JavadocAnchor(name, Optional.of(types)));
        }
        int dash = anchor.indexOf('-');
        if (dash > 0 && anchor.endsWith("-") && anchor.length() > dash + 1) {
            String name = anchor.substring(0, dash);
            if (!isJavaIdentifier(name)) {
                return Optional.empty();
            }
            String inner = anchor.substring(dash + 1, anchor.length() - 1);
            List<String> types = new ArrayList<>();
            if (!inner.isEmpty()) {
                for (String part : inner.split("-", -1)) {
                    if (part.isEmpty()) {
                        return Optional.empty();
                    }
                    types.add(part.replace(":A", "[]"));
                }
            }
            return Optional.of(new JavadocAnchor(name, Optional.of(types)));
        }
        return isJavaIdentifier(anchor) ? Optional.of(new JavadocAnchor(anchor, Optional.empty())) : Optional.empty();
    }

    /**
     * Generates an anchor id.
     *
     * @param name           member name (constructors: the simple type name)
     * @param kind           member kind
     * @param parameterTypes erased parameter types, fully qualified where known ({@code java.lang.String},
     *                       {@code int[]}, varargs {@code java.lang.String...})
     */
    public static String generate(String name, MemberKind kind, List<String> parameterTypes, Layout layout) {
        if (!kind.isExecutable()) {
            return name;
        }
        return switch (layout) {
            case MODERN -> (kind == MemberKind.CONSTRUCTOR ? CONSTRUCTOR_NAME : name)
                    + parameterTypes.stream().collect(Collectors.joining(",", "(", ")"));
            case LEGACY -> parameterTypes.isEmpty() ? name + "--" : name + "-" + parameterTypes.stream()
                    .map(type -> type.replace("...", "[]").replace("[]", ":A"))
                    .map(type -> type + "-")
                    .collect(Collectors.joining());
        };
    }

    /**
     * Generates the anchor id of a member from its erased parameter types (simple names, as the model stores them;
     * pages use qualified names, which {@link #matches} tolerates). Varargs parameters are written with {@code ...}
     * in the modern layout, as javadoc does.
     */
    public static String generate(MemberDescriptor member, Layout layout) {
        List<String> types = member.parameters().stream()
                .map(p -> p.isVarargs() && layout == Layout.MODERN ? erasedVarargs(p) : p.erasedType())
                .toList();
        return generate(member.name(), member.kind(), types, layout);
    }

    /** True if this anchor names a field or enum constant (no parameter list). */
    public boolean isField() {
        return parameterTypes.isEmpty();
    }

    /**
     * True if this anchor exactly denotes {@code member}: same name ({@code <init>} matches constructors), same kind
     * of anchor (field vs. executable) and, per parameter, the anchor type's simple form equals the simple form of
     * the member's erased or declared type (generics dropped, packages and outer types dropped, {@code ...} as
     * {@code []}).
     */
    public boolean matches(MemberDescriptor member) {
        if (!sameName(member) || isField() == member.kind().isExecutable()) {
            return false;
        }
        if (isField()) {
            return true;
        }
        List<String> types = parameterTypes.get();
        if (types.size() != member.arity()) {
            return false;
        }
        for (int i = 0; i < types.size(); i++) {
            String anchorType = simpleForm(types.get(i));
            ParameterInfo parameter = member.parameters().get(i);
            if (!anchorType.equals(simpleForm(parameter.erasedType()))
                    && !anchorType.equals(simpleForm(parameter.type()))) {
                return false;
            }
        }
        return true;
    }

    /** True if name and arity (or field-ness) agree: the fuzzy fallback when no anchor matches exactly. */
    public boolean matchesLoosely(MemberDescriptor member) {
        if (!sameName(member) || isField() == member.kind().isExecutable()) {
            return false;
        }
        return isField() || parameterTypes.get().size() == member.arity();
    }

    private boolean sameName(MemberDescriptor member) {
        if (member.kind() == MemberKind.CONSTRUCTOR) {
            return name.equals(CONSTRUCTOR_NAME) || name.equals(member.name());
        }
        return name.equals(member.name());
    }

    /** {@code java.util.Map.Entry<K,V>...} → {@code Entry[]}. */
    static String simpleForm(String type) {
        String text = stripTypeArguments(type.strip()).replace("...", "[]").replace(":A", "[]").replace(" ", "");
        int arrayStart = text.indexOf('[');
        String base = arrayStart < 0 ? text : text.substring(0, arrayStart);
        String dims = arrayStart < 0 ? "" : text.substring(arrayStart);
        int dot = base.lastIndexOf('.');
        return (dot < 0 ? base : base.substring(dot + 1)) + dims;
    }

    private static String erasedVarargs(ParameterInfo parameter) {
        String erased = parameter.erasedType();
        return erased.endsWith("[]") ? erased.substring(0, erased.length() - 2) + "..." : erased;
    }

    private static String stripTypeArguments(String type) {
        StringBuilder sb = new StringBuilder();
        int depth = 0;
        for (char c : type.toCharArray()) {
            if (c == '<') {
                depth++;
            } else if (c == '>') {
                depth = Math.max(0, depth - 1);
            } else if (depth == 0) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static List<String> splitTopLevel(String text) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '<') {
                depth++;
            } else if (c == '>') {
                depth--;
            } else if (c == ',' && depth == 0) {
                parts.add(text.substring(start, i).strip());
                start = i + 1;
            }
        }
        parts.add(text.substring(start).strip());
        return parts;
    }

    private static boolean isMemberName(String name) {
        return name.equals(CONSTRUCTOR_NAME) || isJavaIdentifier(name);
    }

    private static boolean isJavaIdentifier(String text) {
        if (text.isEmpty() || !Character.isJavaIdentifierStart(text.charAt(0))) {
            return false;
        }
        return text.chars().allMatch(Character::isJavaIdentifierPart);
    }

    private static String decode(String id) {
        if (id.indexOf('%') < 0) {
            return id;
        }
        try {
            return URLDecoder.decode(id.replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return id;
        }
    }
}
