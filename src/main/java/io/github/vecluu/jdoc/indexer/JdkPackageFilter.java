package io.github.vecluu.jdoc.indexer;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Which JDK packages are indexed: {@code java.*}, {@code javax.*} and {@code jdk.*} packages that have no
 * {@code internal} segment and that their module exports unconditionally (when the module's exports are known).
 */
public final class JdkPackageFilter {

    private static final List<String> PREFIXES = List.of("java.", "javax.", "jdk.");
    private static final Pattern COMMENTS = Pattern.compile("/\\*.*?\\*/|//[^\\n]*", Pattern.DOTALL);
    private static final Pattern EXPORTS = Pattern.compile("\\bexports\\s+([\\w.]+)\\s*;");

    /**
     * True if the package is indexed.
     *
     * @param packageName dotted package name
     * @param exports     unqualified exports of the package's module, if known
     */
    public boolean includes(String packageName, Optional<Set<String>> exports) {
        if (PREFIXES.stream().noneMatch(packageName::startsWith)) {
            return false;
        }
        for (String segment : packageName.split("\\.")) {
            if (segment.equals("internal")) {
                return false;
            }
        }
        return exports.map(set -> set.contains(packageName)).orElse(true);
    }

    /** The unqualified ({@code exports p;}, not {@code exports p to m;}) exports of a {@code module-info.java}. */
    public static Set<String> unqualifiedExports(String moduleInfoSource) {
        String code = COMMENTS.matcher(moduleInfoSource).replaceAll(" ");
        Set<String> exports = new java.util.TreeSet<>();
        Matcher matcher = EXPORTS.matcher(code);
        while (matcher.find()) {
            exports.add(matcher.group(1));
        }
        return Set.copyOf(exports);
    }
}
