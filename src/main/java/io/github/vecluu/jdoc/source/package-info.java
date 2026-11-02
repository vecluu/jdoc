/**
 * Parsing of {@code .java} text (sources jars, the JDK {@code src.zip}, decompiled output) with the javac Tree API
 * into {@link io.github.vecluu.jdoc.model.TypeDescriptor}s and {@link io.github.vecluu.jdoc.model.MemberDescriptor}s
 * with exact source offsets, rendered signatures and rendered doc comments.
 *
 * <p>Entry point: {@link io.github.vecluu.jdoc.source.JavaSourceParser}. Slicing a member's text by its offsets:
 * {@link io.github.vecluu.jdoc.source.MemberSourceSlicer}.
 *
 * <p>Package-wide rules:
 * <ul>
 *   <li>Signatures must render exactly as the {@code bytecode} package renders them (format in {@code docs/SPEC.md}),
 *       or types indexed from javadoc jars or bytecode differ from types indexed from sources.</li>
 *   <li>Parsing only, never attribution: names are resolved by guessing from imports ({@code TypeNameResolver}),
 *       except for the JDK, where the {@code src.zip}-wide {@code KnownTypes} makes supertypes exact.</li>
 *   <li>A {@code JavaSourceParser} is not thread-safe: one per worker thread.</li>
 *   <li>Doc comments are rendered with the {@code javadoc} package, which uses jsoup, so parsing loads jsoup as well
 *       as javac. Keep both off the lookup path ({@code HotPathClassLoadingIT}).</li>
 * </ul>
 */
package io.github.vecluu.jdoc.source;
