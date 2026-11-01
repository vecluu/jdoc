package io.github.vecluu.jdoc.module;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Orders version strings like Maven's {@code ComparableVersion}, simplified: versions are split into numeric and
 * qualifier items at {@code .}, {@code -} and digit/letter transitions; numbers compare numerically; trailing zeros
 * and release qualifiers ({@code ga}, {@code final}, {@code release}) are ignored; known qualifiers order as
 * {@code alpha < beta < milestone < rc < snapshot < (release) < sp}, unknown qualifiers sort after them
 * alphabetically; a number is newer than a qualifier at the same position.
 */
public final class MavenVersionComparator implements Comparator<String> {

    private static final Map<String, String> ALIASES = Map.of(
            "a", "alpha", "b", "beta", "m", "milestone", "cr", "rc",
            "ga", "", "final", "", "release", "");
    private static final List<String> QUALIFIERS = List.of("alpha", "beta", "milestone", "rc", "snapshot", "", "sp");
    private static final int RELEASE_RANK = QUALIFIERS.indexOf("");

    @Override
    public int compare(String left, String right) {
        List<Object> a = items(left);
        List<Object> b = items(right);
        for (int i = 0; i < Math.max(a.size(), b.size()); i++) {
            int result = compareItems(i < a.size() ? a.get(i) : null, i < b.size() ? b.get(i) : null);
            if (result != 0) {
                return result;
            }
        }
        return 0;
    }

    private static int compareItems(Object left, Object right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return -compareItems(right, null);
        }
        if (left instanceof BigInteger number) {
            if (right == null) {
                return number.signum();
            }
            return right instanceof BigInteger other ? number.compareTo(other) : 1;
        }
        String qualifier = (String) left;
        if (right instanceof BigInteger) {
            return -1;
        }
        return compareQualifiers(qualifier, right == null ? "" : (String) right);
    }

    private static int compareQualifiers(String left, String right) {
        int leftRank = rank(left);
        int rightRank = rank(right);
        if (leftRank != rightRank) {
            return Integer.compare(leftRank, rightRank);
        }
        return leftRank == QUALIFIERS.size() ? left.compareTo(right) : 0;
    }

    private static int rank(String qualifier) {
        int index = QUALIFIERS.indexOf(qualifier);
        return index >= 0 ? index : QUALIFIERS.size();
    }

    private static List<Object> items(String version) {
        List<Object> items = new ArrayList<>();
        String text = version.strip().toLowerCase(Locale.ROOT);
        StringBuilder token = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '.' || c == '-' || c == '_' || c == '+') {
                addToken(items, token);
            } else {
                if (!token.isEmpty() && Character.isDigit(c) != Character.isDigit(token.charAt(token.length() - 1))) {
                    addToken(items, token);
                }
                token.append(c);
            }
        }
        addToken(items, token);
        while (!items.isEmpty() && isNull(items.getLast())) {
            items.removeLast();
        }
        return items;
    }

    private static void addToken(List<Object> items, StringBuilder token) {
        if (token.isEmpty()) {
            return;
        }
        String text = token.toString();
        token.setLength(0);
        if (Character.isDigit(text.charAt(0))) {
            items.add(new BigInteger(text));
        } else {
            items.add(ALIASES.getOrDefault(text, text));
        }
    }

    private static boolean isNull(Object item) {
        return item instanceof BigInteger number ? number.signum() == 0 : rank((String) item) == RELEASE_RANK;
    }
}
