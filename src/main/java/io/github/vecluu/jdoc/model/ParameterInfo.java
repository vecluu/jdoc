package io.github.vecluu.jdoc.model;

/**
 * One parameter of a method or constructor.
 *
 * @param name       parameter name ({@code arg0} etc. when unknown)
 * @param type       type as declared, with simple names: {@code List<String>}, {@code String...}
 * @param erasedType erased simple type used for overload matching: {@code List}; varargs as {@code String[]};
 *                   type variables replaced by the simple name of their first bound, else {@code Object}
 */
public record ParameterInfo(String name, String type, String erasedType) {
    public ParameterInfo {
        Checks.notBlank(name, "parameter name");
        Checks.notBlank(type, "parameter type");
        Checks.notBlank(erasedType, "erased parameter type");
    }

    /** True for a varargs parameter ({@code String...}). */
    public boolean isVarargs() {
        return type.endsWith("...");
    }

    /** {@code type name}, as in a rendered signature. */
    public String declaration() {
        return type + " " + name;
    }
}
