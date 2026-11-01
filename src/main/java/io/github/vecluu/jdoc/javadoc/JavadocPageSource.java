package io.github.vecluu.jdoc.javadoc;

import java.util.Optional;

/** Read access to the HTML pages of a javadoc tree (normally a {@code -javadoc.jar}); a boundary for tests. */
public interface JavadocPageSource {

    /**
     * The page's HTML, decoded as UTF-8 (or the charset its {@code <meta>} declares).
     *
     * @param entryPath path inside the javadoc tree, {@code a/b/Outer.Inner.html}
     * @return the HTML, or empty if there is no such page
     * @throws io.github.vecluu.jdoc.error.JarReadException if the container cannot be read
     */
    Optional<String> page(String entryPath);

    /** True if the page exists. */
    boolean hasPage(String entryPath);
}
