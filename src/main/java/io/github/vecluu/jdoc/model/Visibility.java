package io.github.vecluu.jdoc.model;

/** Java access level of a type or member. */
public enum Visibility {
    PUBLIC("public"),
    PROTECTED("protected"),
    PACKAGE(""),
    PRIVATE("private");

    private final String keyword;

    Visibility(String keyword) {
        this.keyword = keyword;
    }

    /** True for the levels jdoc shows: public and protected. */
    public boolean isApi() {
        return this == PUBLIC || this == PROTECTED;
    }

    /** The modifier keyword, empty for package-private. */
    public String keyword() {
        return keyword;
    }
}
