package com.example.sample;

/**
 * Old helpers.
 *
 * @deprecated use {@link Shapes} instead
 */
@Deprecated
public final class OldShapes {

    private OldShapes() {
    }

    /**
     * Sums two areas.
     *
     * @param a the first shape
     * @param b the second shape
     * @return the summed area
     */
    public static double sum(Shape a, Shape b) {
        return a.area() + b.area();
    }
}
