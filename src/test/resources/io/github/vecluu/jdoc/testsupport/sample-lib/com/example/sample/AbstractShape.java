package com.example.sample;

import java.util.Objects;

/**
 * Base class for shapes with a {@link Color}.
 *
 * @since 1.0
 */
public abstract class AbstractShape implements Shape {

    /** The fill color of this shape. */
    protected final Color color;

    /**
     * Creates a shape with the given color.
     *
     * @param color the fill color, not {@code null}
     */
    protected AbstractShape(Color color) {
        this.color = Objects.requireNonNull(color, "color");
    }

    /**
     * Returns the fill color.
     *
     * @return the color
     */
    public Color color() {
        return color;
    }

    /**
     * {@inheritDoc}
     *
     * <p>This implementation returns the simple class name.
     */
    @Override
    public String name() {
        return getClass().getSimpleName();
    }

    /**
     * Validates a scale factor.
     *
     * @param factor the factor to check
     * @return {@code factor}
     * @throws IllegalArgumentException if {@code factor <= 0}
     */
    protected static double checkFactor(double factor) {
        if (factor <= 0) {
            throw new IllegalArgumentException("factor must be positive: " + factor);
        }
        return factor;
    }

    private String secret() {
        return "hidden";
    }

    String packageHelper() {
        return secret();
    }
}
