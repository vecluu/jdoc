/**
 * Application services: one {@code <Name>Command} per command ({@code find}, {@code class}, {@code method},
 * {@code source}, {@code impls}, {@code member}, {@code package}), each asking one {@code lookup} service, plus
 * {@link io.github.vecluu.jdoc.app.IndexCommand}, which goes to the indexer instead. Each resolves the module through
 * a {@link io.github.vecluu.jdoc.app.ModuleContext}, answers and formats a
 * {@link io.github.vecluu.jdoc.app.CommandResult}; {@link io.github.vecluu.jdoc.app.Application} is the facade
 * {@code cli} calls.
 *
 * <p>Package-wide rules:
 * <ul>
 *   <li>Nothing here writes to stdout. Not-found and page-out-of-range text are result lines (stdout, exit 1); only
 *       exit-3 errors and classpath warnings go to stderr, and an error result has no stdout lines.</li>
 *   <li>The lookup services and their caches are created per call
 *       ({@link io.github.vecluu.jdoc.app.LookupServicesFactory}); the database is closed by
 *       {@code Application.close}, not by the {@link io.github.vecluu.jdoc.app.ModuleSession}.</li>
 * </ul>
 */
package io.github.vecluu.jdoc.app;
