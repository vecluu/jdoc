package io.github.vecluu.jdoc.javadoc;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jsoup.nodes.Element;

/**
 * Reads the parameter list of a member signature on a javadoc page, which carries the real parameter names that
 * class files usually lack. Works on the signature's text, so it reads every layout: JDK 17+
 * {@code div.member-signature}, JDK 13–16 {@code div.memberSignature}, JDK 11–12 {@code pre.methodSignature} and
 * the JDK 8 {@code <pre>} right inside the member's list item.
 *
 * <p>The list is the first parenthesis at generic depth 0 that does not belong to an annotation
 * ({@code @Deprecated(since = "2")} before the member). Annotations (also type-use ones like
 * {@code java.lang.@Nullable String}) and {@code final} are removed from parameters; a receiver parameter
 * ({@code Foo this}) is skipped. Anything that does not look like {@code Type name} makes the whole list unknown.
 * Stateless.
 */
final class SignatureParameterParser {

    private static final String SIGNATURE_SELECTOR = "div.member-signature, div.memberSignature, pre.methodSignature";

    /** The parameters of the member signature inside {@code section}, if it has one that can be read. */
    Optional<List<JavadocParameter>> parameters(Element section) {
        Element signature = section.selectFirst(SIGNATURE_SELECTOR);
        if (signature == null) {
            signature = section.children().stream().filter(child -> child.normalName().equals("pre"))
                    .findFirst().orElse(null);
        }
        return signature == null ? Optional.empty() : parse(signature.wholeText());
    }

    /** The parameters of a plain-text signature such as {@code public static <T> T max(T a, T b) throws X}. */
    Optional<List<JavadocParameter>> parse(String signature) {
        String text = signature.replace(' ', ' ').replace("​", "");
        int open = parameterListStart(text);
        if (open < 0) {
            return Optional.empty();
        }
        int close = matchingParenthesis(text, open);
        if (close < 0) {
            return Optional.empty();
        }
        String inner = withoutAnnotations(text.substring(open + 1, close)).strip();
        if (inner.isEmpty()) {
            return Optional.of(List.of());
        }
        List<JavadocParameter> parameters = new ArrayList<>();
        for (String part : splitTopLevel(inner)) {
            Optional<JavadocParameter> parameter = parameter(part);
            if (parameter.isEmpty()) {
                return Optional.empty();
            }
            if (!parameter.get().name().equals("this")) {
                parameters.add(parameter.get());
            }
        }
        return Optional.of(List.copyOf(parameters));
    }

    private static Optional<JavadocParameter> parameter(String declaration) {
        String text = declaration.strip().replaceAll("\\s+", " ");
        if (text.startsWith("final ")) {
            text = text.substring("final ".length());
        }
        int space = text.lastIndexOf(' ');
        if (space <= 0) {
            return Optional.empty();
        }
        String name = text.substring(space + 1);
        String type = text.substring(0, space).strip().replaceAll("\\s*([.\\[\\]])\\s*", "$1");
        if (!isIdentifier(name) || type.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new JavadocParameter(type, name));
    }

    /** Index of the {@code (} opening the parameter list, or -1. */
    private static int parameterListStart(String text) {
        int depth = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '@') {
                i = annotationEnd(text, i) - 1;
            } else if (c == '<') {
                depth++;
            } else if (c == '>') {
                depth = Math.max(0, depth - 1);
            } else if (c == '(' && depth == 0) {
                return i;
            }
        }
        return -1;
    }

    /** Index just after the annotation starting at {@code at}: name, then an optional argument list. */
    private static int annotationEnd(String text, int at) {
        int i = at + 1;
        while (i < text.length() && (Character.isJavaIdentifierPart(text.charAt(i)) || text.charAt(i) == '.')) {
            i++;
        }
        int next = i;
        while (next < text.length() && Character.isWhitespace(text.charAt(next))) {
            next++;
        }
        if (next < text.length() && text.charAt(next) == '(') {
            int close = matchingParenthesis(text, next);
            return close < 0 ? text.length() : close + 1;
        }
        return i;
    }

    private static int matchingParenthesis(String text, int open) {
        int depth = 0;
        boolean quoted = false;
        for (int i = open; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"' && (i == 0 || text.charAt(i - 1) != '\\')) {
                quoted = !quoted;
            } else if (!quoted && c == '(') {
                depth++;
            } else if (!quoted && c == ')' && --depth == 0) {
                return i;
            }
        }
        return -1;
    }

    private static String withoutAnnotations(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '@') {
                i = annotationEnd(text, i) - 1;
                sb.append(' ');
            } else {
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
            if (c == '<' || c == '(') {
                depth++;
            } else if (c == '>' || c == ')') {
                depth--;
            } else if (c == ',' && depth == 0) {
                parts.add(text.substring(start, i));
                start = i + 1;
            }
        }
        parts.add(text.substring(start));
        return parts;
    }

    private static boolean isIdentifier(String text) {
        if (text.isEmpty() || !Character.isJavaIdentifierStart(text.charAt(0))) {
            return false;
        }
        return text.chars().allMatch(Character::isJavaIdentifierPart);
    }
}
