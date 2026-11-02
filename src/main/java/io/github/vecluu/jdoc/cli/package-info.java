/**
 * The command line: a small hand-written parser and help text, pagination and printing (the only package writing to
 * stdout), exit codes, the composition root that wires every concrete component
 * ({@link io.github.vecluu.jdoc.cli.CompositionRoot}, the one class allowed to import every package), and the AOT
 * training entry points used by {@code dist/install.sh} ({@link io.github.vecluu.jdoc.cli.TrainingLibrary},
 * {@link io.github.vecluu.jdoc.cli.TrainingWorkload}).
 *
 * <p>Package-wide rules, all for start-up time ({@code HotPathClassLoadingIT}, {@code docs/decisions/startup.md}):
 * <ul>
 *   <li>Nothing on the lookup path may load javac, jsoup, ASM or Vineflower. Indexers and the decompiler are
 *       memoized suppliers, written so that the concrete class is not resolved when the supplier is created (no
 *       constructor references; see the comments in {@code CompositionRoot} and {@code lookup.SourceFiles}).</li>
 *   <li>{@code FailureReporter} is the only class here with a logger, so slf4j loads only when a call fails.</li>
 *   <li>No reflection and no framework: the command table is a plain enum with a {@code switch}, which the AOT cache
 *       covers.</li>
 * </ul>
 */
package io.github.vecluu.jdoc.cli;
