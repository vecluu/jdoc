package com.example.sample.kt;

import java.util.List;

/**
 * Stands in for a Kotlin-compiled class.
 *
 * <p>In the mixed fixture the sources jar holds only {@code KotlinUtils.kt} and the javadoc jar has no page, so
 * this type falls back to decompiling.
 */
public final class KotlinUtils {

    private KotlinUtils() {
    }

    /**
     * Joins names with a separator.
     *
     * @param names     the names
     * @param separator the separator
     * @return the joined text
     */
    public static String joinNames(List<String> names, String separator) {
        return String.join(separator, names);
    }

    /**
     * Doubles a value.
     *
     * @param value the value
     * @return {@code value * 2}
     */
    public static int twice(int value) {
        return value * 2;
    }
}
