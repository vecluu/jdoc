package io.github.vecluu.jdoc.lookup;

import io.github.vecluu.jdoc.model.ArtifactKey;
import io.github.vecluu.jdoc.model.DocSource;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Small hand-written sources for the lookup unit tests: a mini JDK ({@code java.lang.Object}, {@code Comparable},
 * in a {@code src.zip} with {@code java.base/} prefix) and a library in package {@code a}.
 */
final class SampleSources {

    static final String OBJECT = """
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
                 * Returns a string representation of the object.
                 *
                 * @return a string representation of the object
                 */
                public String toString() {
                    return "Object";
                }
            }
            """;

    static final String COMPARABLE = """
            package java.lang;

            /**
             * This interface imposes a total ordering on the objects of each class that implements it.
             *
             * @param <T> the type of objects that this object may be compared to
             */
            public interface Comparable<T> {
                /**
                 * Compares this object with the specified object for order.
                 *
                 * @param o the object to be compared
                 * @return a negative integer, zero, or a positive integer
                 */
                int compareTo(T o);
            }
            """;

    static final String NAMED = """
            package a;

            /** Something with a name. */
            public interface Named extends Comparable<Named> {
                /**
                 * Returns the name.
                 *
                 * @return the name, never empty
                 */
                String name();

                /**
                 * Compares by name.
                 *
                 * @param other the other
                 * @return the comparison result
                 */
                default int compareTo(Named other) {
                    return name().compareTo(other.name());
                }

                /**
                 * Creates a named thing.
                 *
                 * @param name the name
                 * @return a new thing
                 */
                static Named of(String name) {
                    return null;
                }
            }
            """;

    static final String BASE = """
            package a;

            /**
             * Base class for named things. Second sentence.
             *
             * <p>Second paragraph.
             */
            public abstract class Base implements Named {
                /** The base limit. */
                public static final int LIMIT = 1;

                /** Creates a base. */
                protected Base() {
                }

                /**
                 * {@inheritDoc}
                 *
                 * <p>Base adds this.
                 */
                @Override
                public String name() {
                    return "base";
                }

                /**
                 * Sizes it.
                 *
                 * @param scale the scale
                 * @return the size
                 */
                public int size(int scale) {
                    return scale;
                }

                /**
                 * Sizes it twice.
                 *
                 * @param a first
                 * @param b second
                 * @return the size
                 */
                public int size(int a, int b) {
                    return a + b;
                }

                /** Helps. */
                protected static void helper() {
                }

                void hidden() {
                }
            }
            """;

    static final String IMPL = """
            package a;

            /** A concrete implementation. */
            public class Impl extends Base {
                public static final int LIMIT = 2;

                /** Creates an impl. */
                public Impl() {
                }

                @Override
                public int size(int scale) {
                    return scale * 2;
                }

                /** Does its own thing. */
                public void own() {
                    System.out.println("own");
                }

                @Override
                public String toString() {
                    return "Impl";
                }

                /** A nested helper. */
                public static class Nested {
                }

                static class Secret {
                }
            }
            """;

    static final String OTHER = """
            package a;

            /** Another implementation, directly of the interface. */
            public class Other implements Named {
                @Override
                public String name() {
                    return "other";
                }
            }
            """;

    static final String PACKAGE_INFO = """
            /**
             * Named things and their implementations.
             *
             * <p>More about the package.
             */
            package a;
            """;

    static final String RECORD = """
            package a;

            /**
             * A point.
             *
             * @param x the x coordinate
             */
            public record Point(int x) {
            }
            """;

    private SampleSources() {
    }

    static Map<String, String> jdk() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("java/lang/Object.java", OBJECT);
        sources.put("java/lang/Comparable.java", COMPARABLE);
        return sources;
    }

    static Map<String, String> library() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("a/package-info.java", PACKAGE_INFO);
        sources.put("a/Named.java", NAMED);
        sources.put("a/Base.java", BASE);
        sources.put("a/Impl.java", IMPL);
        sources.put("a/Other.java", OTHER);
        sources.put("a/Point.java", RECORD);
        return sources;
    }

    /** Adds the mini JDK and the library (in this classpath order) to the index. */
    static void addTo(LookupIndex index) {
        index.addSources(ArtifactKey.jdk(25), DocSource.JDK, "java.base/", jdk());
        index.addSources("com.acme:named:1.0", library());
    }
}
