package com.example.sample;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an API as experimental.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface Experimental {

    /**
     * Why the API is experimental.
     *
     * @return the reason
     */
    String value() default "";

    /**
     * The version that introduced the API.
     *
     * @return a version
     */
    String since() default "1.0";
}
