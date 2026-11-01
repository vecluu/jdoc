/**
 * Application services: one per command ({@code find}, {@code class}, {@code method}, {@code source}, {@code impls},
 * {@code member}, {@code package}, {@code index}). Each resolves the module through a {@link
 * io.github.vecluu.jdoc.app.ModuleContext}, answers with the {@code lookup} services and formats a
 * {@link io.github.vecluu.jdoc.app.CommandResult}. Nothing here writes to stdout.
 */
package io.github.vecluu.jdoc.app;
