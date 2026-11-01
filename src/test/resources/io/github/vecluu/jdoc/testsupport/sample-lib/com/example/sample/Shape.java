package com.example.sample;

/**
 * A two-dimensional shape with an area.
 *
 * <p>Implementations must be immutable. Use {@link Shapes#largest(Shape...)} to find the largest shape, e.g. when
 * sorting a drawing.
 *
 * @since 1.0
 */
public interface Shape extends Comparable<Shape> {

    /** The default unit name, {@code "cm"}. */
    String DEFAULT_UNIT = "cm";

    /**
     * Returns the area of this shape.
     *
     * @return the area, never negative
     */
    double area();

    /**
     * Returns the name of this shape.
     *
     * @return a short, human-readable name
     */
    String name();

    /**
     * Scales this shape by a factor.
     *
     * <p>The result is a new shape; this shape is unchanged.
     *
     * @param factor the scale factor, must be positive
     * @return the scaled shape
     * @throws IllegalArgumentException if {@code factor} is not positive
     */
    Shape scale(double factor);

    /**
     * Describes this shape in one line.
     *
     * @return {@code name() + ": " + area()}
     */
    default String describe() {
        return name() + ": " + area();
    }

    /**
     * Compares shapes by {@linkplain #area() area}.
     *
     * @param other the shape to compare with
     * @return a negative number, zero or a positive number
     */
    @Override
    default int compareTo(Shape other) {
        return Double.compare(area(), other.area());
    }

    /**
     * Creates a unit circle.
     *
     * @return a red circle with radius {@code 1}
     */
    static Shape unit() {
        return new Circle(1);
    }
}
