/**
 * The unchecked {@link io.github.vecluu.jdoc.error.JdocException} hierarchy. Checked exceptions are wrapped into
 * these at component boundaries; the CLI maps any of them to exit code 3. Downstream packages may add their own
 * subclasses.
 *
 * <p>A message is one concise line: the CLI prints it as a single {@code error: } line and collapses anything longer.
 * Expected outcomes (not found, ambiguous) are values, never exceptions.
 */
package io.github.vecluu.jdoc.error;
