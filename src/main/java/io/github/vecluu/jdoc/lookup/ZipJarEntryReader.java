package io.github.vecluu.jdoc.lookup;

import io.github.vecluu.jdoc.error.JarReadException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** {@link JarEntryReader} opening the zip for each read (a call reads only a few entries). Stateless. */
public final class ZipJarEntryReader implements JarEntryReader {

    @Override
    public Optional<String> readText(Path jar, String entryPath) {
        try (ZipFile zip = new ZipFile(jar.toFile(), StandardCharsets.UTF_8)) {
            ZipEntry entry = zip.getEntry(entryPath);
            if (entry == null || entry.isDirectory()) {
                return Optional.empty();
            }
            try (InputStream in = zip.getInputStream(entry)) {
                return Optional.of(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new JarReadException("cannot read " + entryPath + " from " + jar + ": " + e.getMessage(), e);
        }
    }
}
