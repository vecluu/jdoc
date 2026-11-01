package io.github.vecluu.jdoc.indexer;

/**
 * What the refresh rules compare: a file's modification time and size.
 *
 * @param mtimeMillis last-modified time in epoch milliseconds
 * @param size        size in bytes
 */
public record FileStamp(long mtimeMillis, long size) {
}
