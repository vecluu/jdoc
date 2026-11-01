package io.github.vecluu.jdoc.model;

/** Kind of a Java type declaration. */
public enum TypeKind {
    CLASS("class"),
    INTERFACE("interface"),
    ENUM("enum"),
    RECORD("record"),
    ANNOTATION("@interface");

    private final String keyword;

    TypeKind(String keyword) {
        this.keyword = keyword;
    }

    /** The Java keyword that declares this kind, {@code @interface} for annotations. */
    public String keyword() {
        return keyword;
    }
}
