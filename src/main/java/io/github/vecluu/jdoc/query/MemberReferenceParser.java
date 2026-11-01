package io.github.vecluu.jdoc.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Parses {@code <type>#<name>[(<params>)]}.
 *
 * <ul>
 *   <li>No parentheses: every overload; {@code ()}: exactly zero parameters.</li>
 *   <li>Parameters are comma separated, whitespace tolerant, normalized by {@link ParameterTypes} (generics erased,
 *       varargs as arrays, qualified names reduced to simple names; a parameter name after the type is ignored).</li>
 *   <li>Constructors: {@code Type#Type(...)} and {@code Type#<init>(...)}.</li>
 * </ul>
 */
public final class MemberReferenceParser {

    /** The JVM name of constructors, accepted as input. */
    public static final String INIT = "<init>";

    private final TypeReferenceParser typeParser;

    /** A parser using a default {@link TypeReferenceParser}. */
    public MemberReferenceParser() {
        this(new TypeReferenceParser());
    }

    /** A parser using the given type parser. */
    public MemberReferenceParser(TypeReferenceParser typeParser) {
        this.typeParser = typeParser;
    }

    /** Parses the argument; invalid syntax yields a failure with a descriptive message. */
    public ParseResult<MemberReference> parse(String input) {
        if (input == null || input.isBlank()) {
            return ParseResult.failure("empty member reference, expected <type>#<name>[(<params>)]");
        }
        String trimmed = input.strip();
        int hash = trimmed.indexOf('#');
        if (hash < 0) {
            return ParseResult.failure("'" + trimmed + "' has no '#', expected <type>#<name>[(<params>)]");
        }
        if (trimmed.indexOf('#', hash + 1) >= 0) {
            return ParseResult.failure("'" + trimmed + "' has more than one '#'");
        }
        String typeText = trimmed.substring(0, hash).strip();
        if (typeText.isEmpty()) {
            return ParseResult.failure("missing type before '#' in '" + trimmed + "'");
        }
        ParseResult<TypeReference> type = typeParser.parse(typeText);
        if (type instanceof ParseResult.Failure<TypeReference>(String message)) {
            return ParseResult.failure(message);
        }
        return parseMember(trimmed, type.orElseThrow(), trimmed.substring(hash + 1));
    }

    private ParseResult<MemberReference> parseMember(String input, TypeReference type, String memberText) {
        int open = memberText.indexOf('(');
        String name = (open < 0 ? memberText : memberText.substring(0, open)).strip();
        if (name.isEmpty()) {
            return ParseResult.failure("missing member name after '#' in '" + input + "'");
        }
        if (!name.equals(INIT) && !Identifiers.isIdentifier(name)) {
            return ParseResult.failure("'" + name + "' is not a valid member name in '" + input + "'");
        }
        boolean constructor = name.equals(INIT) || name.equals(type.simpleName());
        String memberName = constructor ? type.simpleName() : name;
        if (open < 0) {
            if (memberText.indexOf(')') >= 0) {
                return ParseResult.failure("')' without '(' in '" + input + "'");
            }
            return ParseResult.success(new MemberReference(input, type, memberName, constructor, Optional.empty()));
        }
        String rest = memberText.substring(open + 1).strip();
        if (!rest.endsWith(")")) {
            return ParseResult.failure("parameter list is not closed with ')' in '" + input + "'");
        }
        String paramsText = rest.substring(0, rest.length() - 1);
        if (paramsText.indexOf('(') >= 0 || paramsText.indexOf(')') >= 0) {
            return ParseResult.failure("unexpected parenthesis in parameter list of '" + input + "'");
        }
        return parseParameters(paramsText).map(params ->
                new MemberReference(input, type, memberName, constructor, Optional.of(params)));
    }

    private static ParseResult<List<String>> parseParameters(String text) {
        if (text.isBlank()) {
            return ParseResult.success(List.of());
        }
        List<String> params = new ArrayList<>();
        for (String piece : splitTopLevel(text)) {
            ParseResult<String> param = ParameterTypes.normalize(piece);
            if (param instanceof ParseResult.Failure<String>(String message)) {
                return ParseResult.failure(message);
            }
            params.add(param.orElseThrow());
        }
        return ParseResult.success(List.copyOf(params));
    }

    private static List<String> splitTopLevel(String text) {
        List<String> pieces = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '<') {
                depth++;
            } else if (c == '>') {
                depth--;
            } else if (c == ',' && depth == 0) {
                pieces.add(text.substring(start, i));
                start = i + 1;
            }
        }
        pieces.add(text.substring(start));
        return pieces;
    }
}
