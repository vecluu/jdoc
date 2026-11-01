package com.example.sample;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Static helpers for {@link Shape shapes}.
 *
 * <p>All methods throw {@link NullPointerException} for {@code null} arguments. Highlights:
 * <ul>
 *   <li>{@link #largest(Shape...)} finds the largest shape</li>
 *   <li>{@link #max(Comparable, Comparable)} compares any values</li>
 * </ul>
 *
 * @since 1.0
 */
public final class Shapes {

    private Shapes() {
    }

    /**
     * Returns the larger of two values.
     *
     * @param <T> the value type
     * @param a   the first value
     * @param b   the second value
     * @return the larger value, {@code a} if both are equal
     */
    public static <T extends Comparable<? super T>> T max(T a, T b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    /**
     * Returns the larger of two ints.
     *
     * @param a the first value
     * @param b the second value
     * @return the larger value
     */
    public static int max(int a, int b) {
        return Math.max(a, b);
    }

    /**
     * Returns the largest of the values.
     *
     * @param values the values
     * @return the largest value
     * @throws IllegalArgumentException if no values are given
     */
    public static double max(double... values) {
        if (values.length == 0) {
            throw new IllegalArgumentException("no values");
        }
        double result = values[0];
        for (double value : values) {
            result = Math.max(result, value);
        }
        return result;
    }

    /**
     * Finds the largest shape.
     *
     * @param shapes the shapes to compare
     * @return the largest shape, or empty if none are given
     */
    public static Optional<Shape> largest(Shape... shapes) {
        Shape best = null;
        for (Shape shape : shapes) {
            if (best == null || shape.compareTo(best) > 0) {
                best = shape;
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * Sums the areas of shapes.
     *
     * @param shapes the shapes
     * @return the total area
     */
    public static double totalArea(List<? extends Shape> shapes) {
        return shapes.stream().mapToDouble(Shape::area).sum();
    }

    /**
     * Groups shapes by {@linkplain Shape#name() name}.
     *
     * @param shapes the shapes
     * @return a map from name to shapes, in encounter order
     */
    public static Map<String, List<Shape>> byName(Collection<? extends Shape> shapes) {
        Map<String, List<Shape>> result = new LinkedHashMap<>();
        for (Shape shape : shapes) {
            result.computeIfAbsent(shape.name(), k -> new java.util.ArrayList<>()).add(shape);
        }
        return result;
    }

    /**
     * A registry of named shapes.
     */
    public static final class Registry {

        private final Map<String, Entry> entries = new LinkedHashMap<>();

        /**
         * Registers a shape under a name, replacing any previous entry.
         *
         * @param name  the name
         * @param shape the shape
         * @return the new entry
         */
        public Entry register(String name, Shape shape) {
            Entry entry = new Entry(name, shape);
            entries.put(name, entry);
            return entry;
        }

        /**
         * Looks up a shape by name.
         *
         * @param name the name
         * @return the entry, or empty if none is registered
         */
        public Optional<Entry> find(String name) {
            return Optional.ofNullable(entries.get(name));
        }

        /**
         * One registered shape.
         *
         * @param name  the registered name
         * @param shape the shape
         */
        public record Entry(String name, Shape shape) {
        }
    }

    /**
     * A visitor over shapes.
     *
     * @param <R> the result type
     */
    public interface Visitor<R> {

        /**
         * Visits a circle.
         *
         * @param circle the circle
         * @return the result
         */
        R visitCircle(Circle circle);

        /**
         * Visits any other shape.
         *
         * @param shape the shape
         * @return the result
         */
        R visitOther(Shape shape);
    }
}
