package com.example.sample;

/**
 * A circle defined by its radius.
 *
 * <pre>{@code
 * Circle c = new Circle(2.0);
 * double a = c.area();
 * }</pre>
 *
 * @see Square
 * @since 1.0
 */
public final class Circle extends AbstractShape {

    private final double radius;

    /**
     * Creates a red circle.
     *
     * @param radius the radius
     */
    public Circle(double radius) {
        this(radius, Color.RED);
    }

    /**
     * Creates a circle with a color.
     *
     * @param radius the radius, must not be negative
     * @param color  the fill color
     * @throws IllegalArgumentException if {@code radius} is negative
     */
    public Circle(double radius, Color color) {
        super(color);
        if (radius < 0) {
            throw new IllegalArgumentException("negative radius: " + radius);
        }
        this.radius = radius;
    }

    /**
     * Returns the radius.
     *
     * @return the radius
     */
    public double radius() {
        return radius;
    }

    /** {@inheritDoc} */
    @Override
    public double area() {
        return Math.PI * radius * radius;
    }

    @Override
    public Circle scale(double factor) {
        return new Circle(radius * checkFactor(factor), color);
    }

    /**
     * Returns the diameter.
     *
     * @return the diameter
     * @deprecated use {@link #radius()} times two; will be removed in 2.0
     */
    @Deprecated(since = "1.1")
    public double diameter() {
        return radius * 2;
    }
}
