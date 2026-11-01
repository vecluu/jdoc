package io.github.vecluu.jdoc.source;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.SourcePositions;
import io.github.vecluu.jdoc.model.SourceRange;
import java.util.Optional;

/** Character ranges of declarations and their doc comments in one parsed compilation unit. */
final class SourceRanges {

    private final CompilationUnitTree unit;
    private final SourcePositions positions;
    private final String text;

    SourceRanges(CompilationUnitTree unit, SourcePositions positions, String text) {
        this.unit = unit;
        this.positions = positions;
        this.text = text;
    }

    /** From the declaration's first token (annotations included) to the end of its body or {@code ;}. */
    Optional<SourceRange> declaration(Tree tree) {
        long start = positions.getStartPosition(unit, tree);
        long end = positions.getEndPosition(unit, tree);
        if (start < 0 || end < start || end > text.length()) {
            return Optional.empty();
        }
        return Optional.of(new SourceRange((int) start, (int) end));
    }

    /**
     * The doc comment ({@code /**} through {@code *}{@code /}) directly before the declaration. Call only for
     * declarations that javac reports as documented.
     */
    Optional<SourceRange> docComment(Tree tree) {
        long start = positions.getStartPosition(unit, tree);
        if (start < 0 || start > text.length()) {
            return Optional.empty();
        }
        int open = text.lastIndexOf("/**", (int) start);
        if (open < 0) {
            return Optional.empty();
        }
        int close = text.indexOf("*/", open + 3);
        if (close < 0 || close + 2 > start) {
            return Optional.empty();
        }
        return Optional.of(new SourceRange(open, close + 2));
    }

    /** The doc comment (if any) through the end of the declaration. */
    Optional<SourceRange> withDoc(Optional<SourceRange> declaration, Optional<SourceRange> doc) {
        if (declaration.isEmpty() || doc.isEmpty()) {
            return declaration;
        }
        return Optional.of(new SourceRange(doc.get().start(), declaration.get().end()));
    }

    /**
     * True if the text between the end of {@code elementType} and the end of {@code variable} contains
     * {@code ...}, i.e. the parameter is varargs. The Tree API does not expose the varargs flag.
     */
    boolean isVarargs(Tree elementType, Tree variable) {
        long from = positions.getEndPosition(unit, elementType);
        long to = positions.getEndPosition(unit, variable);
        if (from < 0 || to <= from || to > text.length()) {
            return false;
        }
        return text.substring((int) from, (int) to).contains("...");
    }
}
