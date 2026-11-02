/**
 * Shared test fixtures. Extend these rather than writing new builders; per-package fakes live next to the tests that
 * use them.
 *
 * <ul>
 *   <li>{@link io.github.vecluu.jdoc.testsupport.TestLibrary}: compiles in-memory sources and writes real binary,
 *       sources and javadoc jars ({@link io.github.vecluu.jdoc.testsupport.BuiltLibrary});</li>
 *   <li>{@link io.github.vecluu.jdoc.testsupport.SampleLibrary}: the shared {@code com.example:sample-lib:1.0.0},
 *       whose sources are under {@code src/test/resources/io/github/vecluu/jdoc/testsupport/sample-lib/}, with
 *       presets for the per-type doc-source fallback;</li>
 *   <li>{@link io.github.vecluu.jdoc.testsupport.FakeMavenRepo}: a local repository layout in a temp directory and
 *       the {@code deps.txt} format;</li>
 *   <li>{@link io.github.vecluu.jdoc.testsupport.TestEnvironments}: a {@code JdocEnvironment} rooted in a temp
 *       directory (cache, repository, fake {@code mvn}, working directory);</li>
 *   <li>{@link io.github.vecluu.jdoc.testsupport.JarWriter}: deterministic jars;</li>
 *   <li>{@link io.github.vecluu.jdoc.testsupport.FatJar}: the path of the shaded {@code jdoc.jar}, for ITs that run
 *       or install it;</li>
 *   <li>{@link io.github.vecluu.jdoc.testsupport.Prerequisites}: a test that needs an external tool or file calls it;
 *       it skips the test locally when the prerequisite is missing and fails it in CI ({@code CI=true}).</li>
 * </ul>
 *
 * <p>Real third-party jars that an IT needs are downloaded by the build, not by the test: the {@code it-fixture-jars}
 * execution in {@code pom.xml} copies them in {@code pre-integration-test}, and their directory reaches the ITs as the
 * failsafe system property {@code jdoc.itFixtureJars} (read by {@code indexer.Jdk8JavadocParameterNamesIT}, which
 * checks for them with {@code Prerequisites}).
 */
package io.github.vecluu.jdoc.testsupport;
