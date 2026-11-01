package io.github.vecluu.jdoc.decompile;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.jetbrains.java.decompiler.main.extern.IContextSource;

/**
 * Lazy Vineflower library view of the whole jar: lets the decompiler look up referenced classes (supertypes,
 * signatures) on demand without listing or decompiling the jar. The caller owns and closes the {@link ZipFile}.
 */
final class JarLibrarySource implements IContextSource {

    private final ZipFile zip;

    JarLibrarySource(ZipFile zip) {
        this.zip = zip;
    }

    @Override
    public String getName() {
        return zip.getName();
    }

    @Override
    public Entries getEntries() {
        return Entries.EMPTY;
    }

    @Override
    public boolean isLazy() {
        return true;
    }

    @Override
    public InputStream getInputStream(String resource) throws IOException {
        ZipEntry entry = zip.getEntry(resource);
        return entry == null || entry.isDirectory() ? null : zip.getInputStream(entry);
    }
}
