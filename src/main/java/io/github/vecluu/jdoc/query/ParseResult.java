package io.github.vecluu.jdoc.query;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Outcome of parsing user input: a value or a descriptive error message.
 *
 * @param <T> the parsed value type
 */
public sealed interface ParseResult<T> permits ParseResult.Success, ParseResult.Failure {

    /** A successful result. */
    static <T> ParseResult<T> success(T value) {
        return new Success<>(value);
    }

    /** A failed result with a message for the user. */
    static <T> ParseResult<T> failure(String message) {
        return new Failure<>(message);
    }

    /** The value, if parsing succeeded. */
    Optional<T> value();

    /** The error message, if parsing failed. */
    Optional<String> error();

    /** True if parsing succeeded. */
    default boolean isSuccess() {
        return this instanceof Success;
    }

    /** The value, or an {@link IllegalStateException} carrying the error message. */
    default T orElseThrow() {
        return value().orElseThrow(() -> new IllegalStateException(error().orElse("parse failed")));
    }

    /** Transforms a successful value; failures pass through. */
    default <R> ParseResult<R> map(Function<? super T, ? extends R> mapper) {
        return switch (this) {
            case Success<T> success -> success(mapper.apply(success.result()));
            case Failure<T> failure -> failure(failure.message());
        };
    }

    /**
     * A parsed value.
     *
     * @param result the value
     * @param <T>    the value type
     */
    record Success<T>(T result) implements ParseResult<T> {
        public Success {
            Objects.requireNonNull(result, "result");
        }

        @Override
        public Optional<T> value() {
            return Optional.of(result);
        }

        @Override
        public Optional<String> error() {
            return Optional.empty();
        }
    }

    /**
     * A parse error.
     *
     * @param message what is wrong with the input, for the user
     * @param <T>     the value type that was expected
     */
    record Failure<T>(String message) implements ParseResult<T> {
        public Failure {
            Objects.requireNonNull(message, "message");
        }

        @Override
        public Optional<T> value() {
            return Optional.empty();
        }

        @Override
        public Optional<String> error() {
            return Optional.of(message);
        }
    }
}
