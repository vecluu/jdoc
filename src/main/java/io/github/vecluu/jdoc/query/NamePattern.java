package io.github.vecluu.jdoc.query;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * A {@code jdoc find} pattern: matches names case-insensitively as a substring or by camel humps.
 *
 * <p>Camel humps: every pattern character either continues the previous match in the name or jumps to the start of
 * a later word; the first character must match a word start. Word starts are the first character, upper-case
 * letters, the first digit of a run of digits, and any character after {@code _}, {@code $}, {@code .} or a digit.
 * Characters are compared ignoring case, so {@code HSR}, {@code HSRequest} and {@code HttpSR} all match
 * {@code HttpServletRequest}, {@code UD} and {@code URLD} match {@code URLDecoder}, {@code XHR} matches
 * {@code XMLHttpRequest}, and {@code B64E} matches {@code Base64Encoder}.
 *
 * <p>As in IntelliJ, a pattern is read as camel humps only when it contains at least one upper-case letter: an
 * all-lower-case pattern such as {@code map} is a plain case-insensitive prefix/substring match, so it does not
 * match {@code MutableAnyPair}.
 *
 * @param pattern the pattern as typed (stripped); never blank
 */
public record NamePattern(String pattern) {

    public NamePattern {
        if (pattern == null || pattern.isBlank()) {
            throw new IllegalArgumentException("find pattern must not be blank");
        }
        pattern = pattern.strip();
    }

    /** True if the pattern is read as camel humps, i.e. it contains an upper-case letter. */
    public boolean usesCamelHumps() {
        return pattern.chars().anyMatch(Character::isUpperCase);
    }

    /** Parses a pattern typed by the user; a blank pattern is a failure. */
    public static ParseResult<NamePattern> parse(String text) {
        if (text == null || text.isBlank()) {
            return ParseResult.failure("empty find pattern");
        }
        return ParseResult.success(new NamePattern(text));
    }

    /** How the name matches, or empty if it does not. */
    public Optional<NameMatch> match(String name) {
        if (name == null || name.isEmpty()) {
            return Optional.empty();
        }
        return kind(name).map(kind -> new NameMatch(kind, name.length()));
    }

    /** True if the name matches in any way. */
    public boolean matches(String name) {
        return match(name).isPresent();
    }

    private Optional<MatchKind> kind(String name) {
        String lowerName = name.toLowerCase(Locale.ROOT);
        String lowerPattern = pattern.toLowerCase(Locale.ROOT);
        boolean humps = usesCamelHumps();
        if (lowerName.equals(lowerPattern)) {
            return Optional.of(MatchKind.EXACT);
        }
        if (lowerName.startsWith(lowerPattern)) {
            return Optional.of(MatchKind.PREFIX);
        }
        if (humps && new HumpMatcher(pattern, name).matches()) {
            return Optional.of(MatchKind.CAMEL_HUMPS);
        }
        if (lowerName.contains(lowerPattern)) {
            return Optional.of(MatchKind.SUBSTRING);
        }
        return Optional.empty();
    }

    /**
     * Camel-hump matcher for one pattern/name pair, as a dynamic program over (pattern index, name index) in
     * O(pattern length x name length) time and O(name length) memory.
     */
    private static final class HumpMatcher {
        private final String pattern;
        private final String name;
        private final boolean[] wordStart;

        HumpMatcher(String pattern, String name) {
            this.pattern = pattern;
            this.name = name;
            this.wordStart = wordStarts(name);
        }

        /**
         * {@code rest[n]}: the remaining pattern {@code pattern[p..]} matches when the previous pattern character was
         * matched at {@code name[n - 1]}: either the next character continues at {@code n}, or it jumps to a word
         * start after {@code n} ({@code jump[n + 1]}: some word start {@code k >= n + 1} matches it and the rest
         * follows from {@code k + 1}).
         */
        boolean matches() {
            int length = name.length();
            if (pattern.length() > length) {
                return false;
            }
            boolean[] rest = new boolean[length + 1];
            Arrays.fill(rest, true);
            boolean[] jump = new boolean[length + 1];
            for (int p = pattern.length() - 1; p >= 0; p--) {
                jump[length] = false;
                for (int m = length - 1; m >= 0; m--) {
                    jump[m] = (wordStart[m] && same(p, m) && rest[m + 1]) || jump[m + 1];
                }
                if (p == 0) {
                    return jump[0];
                }
                boolean[] previous = new boolean[length + 1];
                for (int n = 0; n <= length; n++) {
                    boolean continues = n < length && same(p, n) && rest[n + 1];
                    previous[n] = continues || (n < length && jump[n + 1]);
                }
                rest = previous;
            }
            return false;
        }

        private boolean same(int p, int n) {
            char a = pattern.charAt(p);
            char b = name.charAt(n);
            return a == b || Character.toLowerCase(a) == Character.toLowerCase(b);
        }

        private static boolean[] wordStarts(String name) {
            boolean[] starts = new boolean[name.length()];
            for (int i = 0; i < name.length(); i++) {
                char c = name.charAt(i);
                if (i == 0 || Character.isUpperCase(c)) {
                    starts[i] = true;
                    continue;
                }
                char previous = name.charAt(i - 1);
                boolean afterSeparator = previous == '_' || previous == '$' || previous == '.';
                boolean digitRunStart = Character.isDigit(c) && !Character.isDigit(previous);
                boolean afterDigit = !Character.isDigit(c) && Character.isDigit(previous);
                starts[i] = afterSeparator || digitRunStart || afterDigit;
            }
            return starts;
        }
    }
}
