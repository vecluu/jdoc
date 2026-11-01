package io.github.vecluu.jdoc.model;

/** Kind of a type member. */
public enum MemberKind {
    METHOD, CONSTRUCTOR, FIELD, ENUM_CONSTANT;

    /** True for methods and constructors, the kinds that have parameter lists. */
    public boolean isExecutable() {
        return this == METHOD || this == CONSTRUCTOR;
    }
}
