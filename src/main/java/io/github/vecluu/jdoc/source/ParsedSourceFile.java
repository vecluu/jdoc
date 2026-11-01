package io.github.vecluu.jdoc.source;

import io.github.vecluu.jdoc.model.RenderedDoc;
import io.github.vecluu.jdoc.model.SourceRange;
import io.github.vecluu.jdoc.model.TypeName;
import java.util.List;
import java.util.Optional;

/**
 * The result of parsing one {@code .java} file.
 *
 * @param path            the entry path that was parsed
 * @param packageName     the declared package, empty for the default package
 * @param packageDoc      the package comment of a {@code package-info.java}, if present
 * @param packageDocRange source range of that comment
 * @param types           every top-level and member type (no local or anonymous classes), outer before inner
 * @param problems        javac syntax errors, one line each; the result is best-effort if non-empty
 */
public record ParsedSourceFile(
        String path,
        String packageName,
        Optional<RenderedDoc> packageDoc,
        Optional<SourceRange> packageDocRange,
        List<ParsedType> types,
        List<String> problems) {

    public ParsedSourceFile {
        packageName = packageName == null ? "" : packageName;
        packageDoc = packageDoc == null ? Optional.empty() : packageDoc;
        packageDocRange = packageDocRange == null ? Optional.empty() : packageDocRange;
        types = types == null ? List.of() : List.copyOf(types);
        problems = problems == null ? List.of() : List.copyOf(problems);
    }

    /** The parsed type with this binary name. */
    public Optional<ParsedType> type(TypeName name) {
        return types.stream().filter(type -> type.name().equals(name)).findFirst();
    }

    /** True if javac reported syntax errors. */
    public boolean hasProblems() {
        return !problems.isEmpty();
    }
}
