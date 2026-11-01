/**
 * Parsing of {@code .java} text (sources jars, the JDK {@code src.zip}, decompiled output) with the javac Tree API
 * into {@link io.github.vecluu.jdoc.model.TypeDescriptor}s and {@link io.github.vecluu.jdoc.model.MemberDescriptor}s
 * with exact source offsets, rendered signatures and rendered doc comments.
 *
 * <p>Entry point: {@link io.github.vecluu.jdoc.source.JavaSourceParser}. Slicing a member's text by its offsets:
 * {@link io.github.vecluu.jdoc.source.MemberSourceSlicer}.
 */
package io.github.vecluu.jdoc.source;
