package io.github.vecluu.jdoc.text;

import java.util.ArrayList;
import java.util.List;

/**
 * Two-column layout: the left column is padded to its widest entry, capped at a maximum width so that one long
 * signature does not push every description to the right. Left entries wider than the cap are followed by just the
 * gap. Rows with an empty right column have no trailing spaces.
 *
 * <pre>
 *   java.time.Duration           jdk 25
 *   javax.xml.datatype.Duration  jdk 25
 * </pre>
 */
public final class ColumnLayout {

    /** Default maximum width of the padded left column. */
    public static final int DEFAULT_MAX_LEFT_WIDTH = 48;

    /** Default number of spaces between the columns. */
    public static final int DEFAULT_GAP = 2;

    private final int maxLeftWidth;
    private final int gap;

    /** A layout with the default cap and gap. */
    public ColumnLayout() {
        this(DEFAULT_MAX_LEFT_WIDTH, DEFAULT_GAP);
    }

    /**
     * @param maxLeftWidth maximum width the left column is padded to
     * @param gap          spaces between the columns, at least 1
     */
    public ColumnLayout(int maxLeftWidth, int gap) {
        if (maxLeftWidth < 0 || gap < 1) {
            throw new IllegalArgumentException("invalid column layout: max width " + maxLeftWidth + ", gap " + gap);
        }
        this.maxLeftWidth = maxLeftWidth;
        this.gap = gap;
    }

    /** Lays out the rows without indentation. */
    public List<String> format(List<Row> rows) {
        return format(rows, "");
    }

    /** Lays out the rows, prefixing every line with {@code indent}. */
    public List<String> format(List<Row> rows, String indent) {
        int width = Math.min(maxLeftWidth, rows.stream()
                .filter(row -> width(row.left()) <= maxLeftWidth)
                .mapToInt(row -> width(row.left()))
                .max()
                .orElse(0));
        List<String> lines = new ArrayList<>(rows.size());
        for (Row row : rows) {
            lines.add(indent + format(row, width));
        }
        return List.copyOf(lines);
    }

    private String format(Row row, int width) {
        if (row.right().isEmpty()) {
            return row.left();
        }
        int padding = Math.max(width - width(row.left()), 0) + gap;
        return row.left() + " ".repeat(padding) + row.right();
    }

    private static int width(String text) {
        return text.codePointCount(0, text.length());
    }

    /**
     * One row of a two-column layout.
     *
     * @param left  left column, e.g. a signature or a type name
     * @param right right column, e.g. a first sentence; may be empty
     */
    public record Row(String left, String right) {
        public Row {
            Lines.requireSingleLine(left);
            right = right == null ? "" : Lines.requireSingleLine(right);
        }

        /** A row of two cells. */
        public static Row of(String left, String right) {
            return new Row(left, right);
        }
    }
}
