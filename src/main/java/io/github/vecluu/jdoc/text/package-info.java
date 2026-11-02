/**
 * Pure output primitives: the result text model ({@link io.github.vecluu.jdoc.text.TextResult}), the token budget
 * and pagination ({@link io.github.vecluu.jdoc.text.Paginator}), two-column alignment
 * ({@link io.github.vecluu.jdoc.text.ColumnLayout}) and the result header line
 * ({@link io.github.vecluu.jdoc.text.ResultHeader}). Nothing here performs I/O or logs; printing is {@code cli}'s job.
 *
 * <p>Text values reject line breaks ({@code TextBlock}, {@code TextResult}, {@code Page}, {@code ColumnLayout.Row},
 * {@code ResultHeader}): multi-line doc text must be split into lines before it reaches this package.
 */
package io.github.vecluu.jdoc.text;
