package io.github.vecluu.jdoc.query;

/** Java identifier checks used by the parsers. */
final class Identifiers {
    private Identifiers() {
    }

    static boolean isIdentifier(String text) {
        if (text.isEmpty() || !Character.isJavaIdentifierStart(text.codePointAt(0))) {
            return false;
        }
        return text.codePoints().skip(1).allMatch(Character::isJavaIdentifierPart);
    }

    static boolean startsUppercase(String text) {
        return !text.isEmpty() && Character.isUpperCase(text.codePointAt(0));
    }
}
