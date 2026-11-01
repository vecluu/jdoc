package io.github.vecluu.jdoc.app;

import java.util.LinkedHashMap;
import java.util.Map;

/** Hand-written sources for the app tests: a mini JDK and a small shapes library. */
final class AppSources {

    static final String LIBRARY_GAV = "com.acme:shapes:1.0";

    private AppSources() {
    }

    static Map<String, String> jdk() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("java/lang/Object.java", """
                package java.lang;

                /** Class {@code Object} is the root of the class hierarchy. */
                public class Object {
                    /** Constructs a new object. */
                    public Object() {
                    }

                    /**
                     * Indicates whether some other object is "equal to" this one.
                     *
                     * @param obj the reference object with which to compare
                     * @return {@code true} if this object is the same as the obj argument
                     */
                    public boolean equals(Object obj) {
                        return this == obj;
                    }

                    /**
                     * Returns the runtime class of this {@code Object}.
                     *
                     * @return the runtime class
                     */
                    public final native Class<?> getClass();

                    /**
                     * Returns a hash code value for the object.
                     *
                     * @return a hash code value for this object
                     */
                    public native int hashCode();

                    /**
                     * Called by the garbage collector.
                     *
                     * @throws Throwable the exception raised by this method
                     */
                    protected void finalize() throws Throwable {
                    }
                }
                """);
        sources.put("java/util/function/Supplier.java", """
                package java.util.function;

                /**
                 * Represents a supplier of results.
                 *
                 * @param <T> the type of results supplied by this supplier
                 */
                public interface Supplier<T> {
                    /**
                     * Gets a result.
                     *
                     * @return a result
                     */
                    T get();
                }
                """);
        sources.put("java/util/Optional.java", """
                package java.util;

                import java.util.function.Supplier;

                /**
                 * A container object which may or may not contain a non-{@code null} value.
                 * If a value is present, {@code isPresent()} returns {@code true}.
                 *
                 * <p>Second paragraph, not shown in the class view.
                 *
                 * @param <T> the type of value
                 * @since 1.8
                 */
                public final class Optional<T> {
                    private final T value;

                    private Optional(T value) {
                        this.value = value;
                    }

                    /**
                     * Returns an {@code Optional} describing the given non-{@code null} value.
                     *
                     * @param value the value to describe, which must be non-{@code null}
                     * @param <T> the type of the value
                     * @return an {@code Optional} with the value present
                     */
                    public static <T> Optional<T> of(T value) {
                        return new Optional<>(value);
                    }

                    /**
                     * If a value is present, returns the value, otherwise throws {@code NoSuchElementException}.
                     *
                     * @return the non-{@code null} value described by this {@code Optional}
                     * @throws NoSuchElementException if no value is present
                     * @since 10
                     */
                    public T orElseThrow() {
                        if (value == null) {
                            throw new NoSuchElementException("No value present");
                        }
                        return value;
                    }

                    /**
                     * If a value is present, returns the value, otherwise throws an exception produced by the
                     * exception supplying function.
                     *
                     * @param <X> Type of the exception to be thrown
                     * @param exceptionSupplier the supplying function that produces an exception to be thrown
                     * @return the value, if present
                     * @throws X if no value is present
                     */
                    public <X extends Throwable> T orElseThrow(Supplier<? extends X> exceptionSupplier) throws X {
                        if (value != null) {
                            return value;
                        }
                        throw exceptionSupplier.get();
                    }

                    /**
                     * Indicates whether some other object is "equal to" this {@code Optional}.
                     *
                     * @param obj an object to be tested for equality
                     * @return {@code true} if the other object is "equal to" this object
                     */
                    @Override
                    public boolean equals(Object obj) {
                        return obj == this;
                    }
                }
                """);
        sources.put("java/time/Duration.java", """
                package java.time;

                /** A time-based amount of time, such as '34.5 seconds'. */
                public final class Duration {
                    private Duration() {
                    }
                }
                """);
        sources.put("javax/xml/datatype/Duration.java", """
                package javax.xml.datatype;

                /** Immutable representation of a time span as defined in the W3C XML Schema 1.0 specification. */
                public abstract class Duration {
                    /** Default no-arg constructor. */
                    public Duration() {
                    }
                }
                """);
        return sources;
    }

    static Map<String, String> library() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("com/acme/package-info.java", """
                /**
                 * Shapes for tests.
                 *
                 * @since 1.0
                 */
                package com.acme;
                """);
        sources.put("com/acme/Shape.java", """
                package com.acme;

                /** A shape with an area. */
                public interface Shape {
                    /**
                     * Computes the area.
                     *
                     * @return the area, never negative
                     */
                    double area();
                }
                """);
        sources.put("com/acme/Base.java", """
                package com.acme;

                /** Common base of the shapes. */
                public abstract class Base implements Shape, Comparable<Base> {
                    /** For subclasses. */
                    protected Base() {
                    }

                    /**
                     * Compares by area.
                     *
                     * @param other the other shape
                     * @return the comparison result
                     */
                    @Override
                    public int compareTo(Base other) {
                        return Double.compare(area(), other.area());
                    }
                }
                """);
        sources.put("com/acme/Circle.java", """
                package com.acme;

                /**
                 * A circle.
                 *
                 * <p>More about circles.
                 */
                public class Circle extends Base {
                    private final double radius;

                    /**
                     * Creates a circle.
                     *
                     * @param radius the radius
                     */
                    public Circle(double radius) {
                        this.radius = radius;
                    }

                    @Override
                    public double area() {
                        return Math.PI * radius * radius;
                    }

                    /**
                     * Old name of {@link #area()}.
                     *
                     * @return the area
                     * @deprecated use {@link #area()}
                     */
                    @Deprecated
                    public double size() {
                        return area();
                    }

                    /** Builds circles. */
                    public static class Builder {
                        /**
                         * Builds the circle.
                         *
                         * @return a unit circle
                         */
                        public Circle build() {
                            return new Circle(1);
                        }
                    }
                }
                """);
        sources.put("com/acme/Square.java", """
                package com.acme;

                /** A unit square. */
                public class Square extends Base {
                    @Override
                    public double area() {
                        return 1;
                    }
                }
                """);
        sources.put("com/acme/Lonely.java", """
                package com.acme;

                /** Nobody extends this. */
                public final class Lonely {
                    private Lonely() {
                    }
                }
                """);
        return sources;
    }
}
