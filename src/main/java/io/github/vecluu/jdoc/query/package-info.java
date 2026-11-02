/**
 * Pure interpretation of the user's query arguments: type references
 * ({@link io.github.vecluu.jdoc.query.TypeReferenceParser}), member references
 * ({@link io.github.vecluu.jdoc.query.MemberReferenceParser}), overload matching
 * ({@link io.github.vecluu.jdoc.query.OverloadMatcher}) and name matching for {@code jdoc find}
 * ({@link io.github.vecluu.jdoc.query.NamePattern}). Nothing here touches the index, logs or writes to stdout;
 * invalid syntax is reported as a {@link io.github.vecluu.jdoc.query.ParseResult.Failure} value, not an exception.
 */
package io.github.vecluu.jdoc.query;
