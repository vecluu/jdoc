/**
 * Immutable domain values shared by every layer of jdoc: Maven coordinates ({@link io.github.vecluu.jdoc.model.Gav},
 * {@link io.github.vecluu.jdoc.model.ArtifactKey}), type names, type and member descriptors, rendered documentation
 * and indexed rows. This package depends on nothing else in jdoc; it does no I/O and no logging.
 *
 * <p>Persisted names: the index stores the {@code name()} of {@code TypeKind}, {@code MemberKind}, {@code Visibility}
 * and {@link io.github.vecluu.jdoc.model.DocSource} and reads them back with {@code valueOf}, and it keys artifacts by
 * {@link io.github.vecluu.jdoc.model.ArtifactKey#keyString()} ({@link io.github.vecluu.jdoc.model.Gav#canonical()}).
 * Renaming a constant or changing the canonical form breaks existing {@code index.db} files unless
 * {@code index.Schema.VERSION} is bumped.
 */
package io.github.vecluu.jdoc.model;
