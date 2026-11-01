/**
 * The SQLite index ({@code index.db}): schema and connection handling ({@link io.github.vecluu.jdoc.index.IndexDatabase}),
 * one repository per aggregate (artifacts, types, supertypes, members, rendered docs, failed artifacts) and the
 * inter-process indexing lock ({@link io.github.vecluu.jdoc.index.IndexLock}). No other package contains SQL.
 *
 * <p>Every read method is scoped to a collection of artifact ids; how the scope is passed to SQLite is an internal
 * detail of this package.
 */
package io.github.vecluu.jdoc.index;
