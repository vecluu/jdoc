package com.example.sample;

/**
 * An immutable point in the plane.
 *
 * @param x the x coordinate
 * @param y the y coordinate
 * @since 1.0
 */
public record Point(int x, int y) {

    /** The origin, {@code (0, 0)}. */
    public static final Point ORIGIN = new Point(0, 0);

    /**
     * Creates a point.
     *
     * @throws IllegalArgumentException if a coordinate is {@link Integer#MIN_VALUE}
     */
    public Point {
        if (x == Integer.MIN_VALUE || y == Integer.MIN_VALUE) {
            throw new IllegalArgumentException("coordinate out of range");
        }
    }

    /**
     * Returns the Manhattan distance to another point.
     *
     * @param other the other point
     * @return {@code |x - other.x| + |y - other.y|}
     */
    public int distance(Point other) {
        return Math.abs(x - other.x) + Math.abs(y - other.y);
    }
}
