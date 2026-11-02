package io.github.vecluu.jdoc.cli;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The Java sources of the {@link TrainingLibrary}: one small API package with the shapes jdoc meets in real libraries
 * (interface with default and static methods, generics, overloads, varargs, a nested builder, a record, an enum with
 * a constant body, an annotation, {@code {@inheritDoc}}, deprecation, package documentation). Each library flavour
 * gets its own copy under its own package name, so lookups never become ambiguous between flavours.
 */
final class TrainingLibrarySources {

    private static final String PACKAGE_TOKEN = "PKG";

    private static final Map<String, String> TEMPLATES = templates();

    private TrainingLibrarySources() {
    }

    /**
     * The sources for one package, path relative to the source root (e.g. {@code a/b/Widget.java}) to text.
     *
     * @param packageName a valid Java package name
     */
    static Map<String, String> forPackage(String packageName) {
        String directory = packageName.replace('.', '/') + "/";
        Map<String, String> sources = new LinkedHashMap<>();
        TEMPLATES.forEach((file, text) -> sources.put(directory + file, text.replace(PACKAGE_TOKEN, packageName)));
        return sources;
    }

    private static Map<String, String> templates() {
        Map<String, String> templates = new LinkedHashMap<>();
        templates.put("package-info.java", """
                /**
                 * Gadgets and widgets used to train jdoc's startup cache.
                 *
                 * <p>Start with {@link PKG.Widget}.
                 */
                package PKG;
                """);
        templates.put("Gadget.java", """
                package PKG;

                import java.util.List;
                import java.util.function.Function;

                /**
                 * Something that can be resized and described.
                 *
                 * @param <T> the label type
                 * @since 1.0
                 */
                public interface Gadget<T> extends Comparable<Gadget<T>> {

                    /** The default size. */
                    int DEFAULT_SIZE = 8;

                    /**
                     * Resizes the gadget.
                     *
                     * @param size the new size, at least 1
                     * @return this gadget
                     * @throws IllegalArgumentException if {@code size} is below 1
                     */
                    Gadget<T> resize(int size);

                    /**
                     * The label, mapped. Uses {@link #label()}.
                     *
                     * @param mapper applied to the label
                     * @param <R>    the result type
                     * @return the mapped label
                     */
                    default <R> R label(Function<? super T, ? extends R> mapper) {
                        return mapper.apply(label());
                    }

                    /**
                     * The label of this gadget.
                     *
                     * @return the label, never null
                     */
                    T label();

                    /**
                     * The labels of several gadgets.
                     *
                     * @param gadgets the gadgets
                     * @param <T>     the label type
                     * @return their labels in order
                     */
                    static <T> List<T> labels(List<? extends Gadget<T>> gadgets) {
                        return gadgets.stream().map(Gadget::label).toList();
                    }

                    @Override
                    default int compareTo(Gadget<T> other) {
                        return String.valueOf(label()).compareTo(String.valueOf(other.label()));
                    }
                }
                """);
        templates.put("Widget.java", """
                package PKG;

                import java.util.ArrayList;
                import java.util.List;
                import java.util.Map;
                import java.util.Optional;

                /**
                 * A widget with a name, a size and parts.
                 *
                 * <pre>{@code
                 * Widget widget = Widget.builder().name("knob").size(3).build();
                 * }</pre>
                 *
                 * @see Gadget
                 */
                @Marker(value = "widget", priority = 2)
                public class Widget implements Gadget<String> {

                    /** The largest supported size. */
                    public static final int MAX_SIZE = 1 << 10;

                    /** The parts, in insertion order. */
                    protected final List<Part> parts = new ArrayList<>();

                    private final String name;
                    private int size;

                    /** A widget named {@code "widget"} with the default size. */
                    public Widget() {
                        this("widget");
                    }

                    /**
                     * A widget with the default size.
                     *
                     * @param name the name
                     */
                    public Widget(String name) {
                        this(name, DEFAULT_SIZE);
                    }

                    /**
                     * A widget.
                     *
                     * @param name the name
                     * @param size the size
                     */
                    public Widget(String name, int size) {
                        this.name = name;
                        this.size = size;
                    }

                    /** {@inheritDoc} */
                    @Override
                    public Widget resize(int size) {
                        if (size < 1) {
                            throw new IllegalArgumentException("size " + size);
                        }
                        this.size = Math.min(size, MAX_SIZE);
                        return this;
                    }

                    /**
                     * Resizes by a factor.
                     *
                     * @param factor multiplies the size
                     * @param mode   how to round
                     * @return this widget
                     */
                    public Widget resize(double factor, Mode mode) {
                        return resize(mode.round(size * factor));
                    }

                    @Override
                    public String label() {
                        return name + "#" + size;
                    }

                    /**
                     * Adds parts.
                     *
                     * @param more the parts to add
                     * @return the number of parts afterwards
                     */
                    public int add(Part... more) {
                        parts.addAll(List.of(more));
                        return parts.size();
                    }

                    /**
                     * The first part with the given name.
                     *
                     * @param name the part name
                     * @return the part, if any
                     */
                    public Optional<Part> part(String name) {
                        return parts.stream().filter(part -> part.name().equals(name)).findFirst();
                    }

                    /**
                     * Counts parts by name.
                     *
                     * @return part name to count
                     * @deprecated use {@link #part(String)} instead
                     */
                    @Deprecated(since = "1.0")
                    public Map<String, Long> census() {
                        return Map.of();
                    }

                    /**
                     * Starts a builder.
                     *
                     * @return a new builder
                     */
                    public static Builder builder() {
                        return new Builder();
                    }

                    /** Builds {@link Widget}s. */
                    public static final class Builder {
                        private String name = "widget";
                        private int size = DEFAULT_SIZE;

                        private Builder() {
                        }

                        /**
                         * Sets the name.
                         *
                         * @param name the name
                         * @return this builder
                         */
                        public Builder name(String name) {
                            this.name = name;
                            return this;
                        }

                        /**
                         * Sets the size.
                         *
                         * @param size the size
                         * @return this builder
                         */
                        public Builder size(int size) {
                            this.size = size;
                            return this;
                        }

                        /**
                         * Builds the widget.
                         *
                         * @return a new widget
                         */
                        public Widget build() {
                            return new Widget(name, size);
                        }
                    }
                }
                """);
        templates.put("Part.java", """
                package PKG;

                /**
                 * One part of a {@link Widget}.
                 *
                 * @param name   the part name, not blank
                 * @param weight the weight in grams
                 */
                public record Part(String name, double weight) {

                    /** A part without weight. */
                    public static final Part EMPTY = new Part("empty", 0);

                    /**
                     * Validates the part.
                     *
                     * @throws IllegalArgumentException if the name is blank
                     */
                    public Part {
                        if (name.isBlank()) {
                            throw new IllegalArgumentException("blank name");
                        }
                    }

                    /**
                     * This part with another weight.
                     *
                     * @param newWeight the weight
                     * @return a new part
                     */
                    public Part withWeight(double newWeight) {
                        return new Part(name, newWeight);
                    }
                }
                """);
        templates.put("Mode.java", """
                package PKG;

                /** How {@link Widget#resize(double, Mode)} rounds. */
                public enum Mode {
                    /** Rounds down. */
                    FLOOR,
                    /** Rounds up. */
                    CEILING {
                        @Override
                        int round(double value) {
                            return (int) Math.ceil(value);
                        }
                    };

                    /**
                     * Rounds a value.
                     *
                     * @param value the value
                     * @return the rounded value
                     */
                    int round(double value) {
                        return (int) Math.floor(value);
                    }
                }
                """);
        templates.put("Marker.java", """
                package PKG;

                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.lang.annotation.Target;

                /** Marks a type for the training run. */
                @Retention(RetentionPolicy.RUNTIME)
                @Target(ElementType.TYPE)
                public @interface Marker {

                    /**
                     * The marker text.
                     *
                     * @return the text
                     */
                    String value();

                    /**
                     * The priority.
                     *
                     * @return the priority, 0 by default
                     */
                    int priority() default 0;
                }
                """);
        return Collections.unmodifiableMap(templates);
    }
}
