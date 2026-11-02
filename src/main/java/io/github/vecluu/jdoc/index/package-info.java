/**
 * The SQLite index ({@code index.db}): schema and connection handling ({@link io.github.vecluu.jdoc.index.IndexDatabase}),
 * one repository per aggregate (artifacts, types, supertypes, members, rendered docs, failed artifacts) and the
 * inter-process indexing lock ({@link io.github.vecluu.jdoc.index.IndexLock}). No other package contains SQL.
 *
 * <p>Every read method is scoped to a collection of artifact ids; how the scope is passed to SQLite is an internal
 * detail of this package.
 *
 * <p>An {@code IndexDatabase} is one connection used by one thread; the indexer's parallel parsing feeds a single
 * writer thread. Inserts rely on the write lock of the surrounding transaction (ids are assigned as
 * {@code max(id) + 1}), so never insert outside {@code inTransaction}. Anything that changes what is stored bumps
 * {@link io.github.vecluu.jdoc.index.Schema#VERSION}: old files are dropped and rebuilt.
 */
package io.github.vecluu.jdoc.index;
