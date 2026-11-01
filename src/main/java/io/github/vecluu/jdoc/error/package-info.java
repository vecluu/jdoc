/**
 * The unchecked {@link io.github.vecluu.jdoc.error.JdocException} hierarchy. Checked exceptions are wrapped into
 * these at component boundaries; the CLI maps any of them to exit code 3. Downstream packages may add their own
 * subclasses.
 */
package io.github.vecluu.jdoc.error;
