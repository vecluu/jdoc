/**
 * Reading binary jars and class files with ASM: which types and members exist, with signatures rendered in the
 * same format as source parsing. Entry points: {@link io.github.vecluu.jdoc.bytecode.BinaryJarScanner}
 * ({@link io.github.vecluu.jdoc.bytecode.ZipBinaryJarScanner}) and
 * {@link io.github.vecluu.jdoc.bytecode.ClassFileReader}.
 *
 * <p>Signatures must render exactly as the {@code source} package renders them; what bytecode cannot show (such as
 * {@code non-sealed}) is a documented difference. Only API members are returned; types of every visibility are.
 */
package io.github.vecluu.jdoc.bytecode;
