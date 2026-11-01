package com.example.sample;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * A generic container of shapes.
 *
 * @param <T> the shape type
 * @since 1.1
 */
@Experimental("API may change")
public class Box<T extends Shape> {

    private final List<T> items = new ArrayList<>();

    /** Creates an empty box. */
    public Box() {
    }

    /**
     * Adds a shape.
     *
     * @param item the shape to add
     * @return this box
     */
    public Box<T> put(T item) {
        items.add(item);
        return this;
    }

    /**
     * Adds a shape several times.
     *
     * @param item  the shape to add
     * @param times how often to add it
     * @return this box
     */
    public Box<T> put(T item, int times) {
        for (int i = 0; i < times; i++) {
            items.add(item);
        }
        return this;
    }

    /**
     * Returns the shapes in insertion order.
     *
     * @return an unmodifiable list
     */
    public List<T> items() {
        return List.copyOf(items);
    }

    /**
     * Maps every shape into a new box.
     *
     * @param <U>    the new shape type
     * @param mapper the mapping function
     * @return a new box
     */
    public <U extends Shape> Box<U> map(Function<? super T, ? extends U> mapper) {
        Box<U> result = new Box<>();
        for (T item : items) {
            result.put(mapper.apply(item));
        }
        return result;
    }

    /**
     * Hook for subclasses, called before a shape is added.
     *
     * @param item the shape
     */
    protected void beforePut(T item) {
    }
}
