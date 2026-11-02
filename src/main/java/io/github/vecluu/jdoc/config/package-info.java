/**
 * Access to the process environment ({@link io.github.vecluu.jdoc.config.JdocEnvironment}), the layout of the cache
 * directory ({@link io.github.vecluu.jdoc.config.CachePaths}) and facts about the JDK
 * ({@link io.github.vecluu.jdoc.config.JdkInfo}). No other package reads environment variables, {@code user.home}
 * or the working directory; everything else is handed a {@code JdocEnvironment}, so tests can point the cache, the
 * local repository, {@code JAVA_HOME} and Maven at temporary directories.
 *
 * <p>Every path in the environment is absolute and normalized, and blank variables count as unset. Nothing here logs
 * or writes to stdout.
 */
package io.github.vecluu.jdoc.config;
