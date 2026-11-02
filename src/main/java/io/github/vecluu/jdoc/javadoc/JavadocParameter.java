package io.github.vecluu.jdoc.javadoc;

/**
 * One parameter as a javadoc page's member signature shows it.
 *
 * @param type the type as shown, annotations removed, simple or qualified names: {@code List<? extends T>},
 *             {@code Map.Entry<K,V>}, {@code String...}, {@code int[]}
 * @param name the real parameter name from the documented source
 */
public record JavadocParameter(String type, String name) {

    public JavadocParameter {
        if (type == null || type.isBlank() || name == null || name.isBlank()) {
            throw new IllegalArgumentException("parameter type and name must not be blank");
        }
    }
}
