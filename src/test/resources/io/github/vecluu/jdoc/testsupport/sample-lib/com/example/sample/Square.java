package com.example.sample;

public class Square extends AbstractShape {

    private final double side;

    /**
     * Creates a square.
     *
     * @param side the side length
     */
    public Square(double side) {
        super(Color.BLUE);
        this.side = side;
    }

    /**
     * Returns the side length.
     *
     * @return the side length
     */
    public double side() {
        return side;
    }

    @Override
    public double area() {
        return side * side;
    }

    @Override
    public Square scale(double factor) {
        return new Square(side * checkFactor(factor));
    }

    /**
     * A corner of this square (an inner, non-static class).
     */
    public class Corner {

        /**
         * Returns the square this corner belongs to.
         *
         * @return the owning square
         */
        public Square owner() {
            return Square.this;
        }
    }
}
