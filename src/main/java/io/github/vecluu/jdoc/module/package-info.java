/**
 * Module and version resolution: finds the Maven module enclosing a path, computes its POM key, runs
 * {@code mvn dependency:list} on a cache miss, parses {@code deps.txt}, keeps per-module meta files, drops the
 * project's own reactor modules and falls back to the last good list or to the newest versions in the local
 * repository. The entry point is {@link io.github.vecluu.jdoc.module.ClasspathResolver}.
 */
package io.github.vecluu.jdoc.module;
