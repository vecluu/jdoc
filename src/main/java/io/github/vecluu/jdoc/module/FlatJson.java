package io.github.vecluu.jdoc.module;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A minimal codec for flat JSON objects whose values are strings or numbers. {@code true}, {@code false} and
 * {@code null} values are accepted when reading and dropped; nested objects and arrays are rejected.
 */
final class FlatJson {

    private FlatJson() {
    }

    /** Writes the fields in iteration order, one per line. Values must be {@link String} or {@link Number}. */
    static String write(Map<String, ?> fields) {
        return fields.entrySet().stream()
                .map(entry -> "  " + quote(entry.getKey()) + ": " + value(entry.getValue()))
                .collect(Collectors.joining(",\n", "{\n", "\n}\n"));
    }

    /**
     * Reads a flat object; numbers become {@link Long} when integral, else {@link Double}.
     *
     * @throws IllegalArgumentException if the text is not a flat JSON object
     */
    static Map<String, Object> read(String text) {
        return new Reader(text).object();
    }

    private static String value(Object value) {
        if (value instanceof Number number) {
            return number.toString();
        }
        if (value instanceof String string) {
            return quote(string);
        }
        throw new IllegalArgumentException("unsupported JSON value: " + value);
    }

    private static String quote(String text) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }

    /** Recursive-descent reader for one flat object. */
    private static final class Reader {
        private final String text;
        private int pos;

        Reader(String text) {
            this.text = text;
        }

        Map<String, Object> object() {
            Map<String, Object> fields = new LinkedHashMap<>();
            skipWhitespace();
            expect('{');
            skipWhitespace();
            if (peek() == '}') {
                pos++;
            } else {
                while (true) {
                    skipWhitespace();
                    String key = string();
                    skipWhitespace();
                    expect(':');
                    skipWhitespace();
                    Object value = value();
                    if (value != null) {
                        fields.put(key, value);
                    }
                    skipWhitespace();
                    char next = next();
                    if (next == '}') {
                        break;
                    }
                    if (next != ',') {
                        throw error("expected ',' or '}'");
                    }
                }
            }
            skipWhitespace();
            if (pos != text.length()) {
                throw error("trailing content");
            }
            return fields;
        }

        private Object value() {
            char c = peek();
            if (c == '"') {
                return string();
            }
            if (c == '-' || Character.isDigit(c)) {
                return number();
            }
            for (String literal : new String[] {"true", "false", "null"}) {
                if (text.startsWith(literal, pos)) {
                    pos += literal.length();
                    return null;
                }
            }
            throw error("unsupported value");
        }

        private Object number() {
            int start = pos;
            while (pos < text.length() && "+-0123456789.eE".indexOf(text.charAt(pos)) >= 0) {
                pos++;
            }
            String number = text.substring(start, pos);
            try {
                if (number.matches("-?\\d+")) {
                    return Long.parseLong(number);
                }
                return Double.parseDouble(number);
            } catch (NumberFormatException e) {
                throw error("bad number " + number);
            }
        }

        private String string() {
            expect('"');
            StringBuilder out = new StringBuilder();
            while (true) {
                char c = next();
                if (c == '"') {
                    return out.toString();
                }
                if (c != '\\') {
                    out.append(c);
                    continue;
                }
                char escape = next();
                switch (escape) {
                    case '"', '\\', '/' -> out.append(escape);
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'u' -> {
                        if (pos + 4 > text.length()) {
                            throw error("truncated unicode escape");
                        }
                        try {
                            out.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                        } catch (NumberFormatException e) {
                            throw error("bad unicode escape");
                        }
                        pos += 4;
                    }
                    default -> throw error("bad escape \\" + escape);
                }
            }
        }

        private void skipWhitespace() {
            while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
                pos++;
            }
        }

        private void expect(char expected) {
            if (next() != expected) {
                throw error("expected '" + expected + "'");
            }
        }

        private char peek() {
            if (pos >= text.length()) {
                throw error("unexpected end");
            }
            return text.charAt(pos);
        }

        private char next() {
            char c = peek();
            pos++;
            return c;
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException("invalid JSON at offset " + pos + ": " + message);
        }
    }
}
