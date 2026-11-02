package io.github.vecluu.jdoc.bytecode;

import io.github.vecluu.jdoc.error.JarReadException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link BinaryJarScanner} over a jar on disk using {@link ZipFile} (random access; one class in memory at a time).
 */
@Slf4j
@RequiredArgsConstructor
public final class ZipBinaryJarScanner implements BinaryJarScanner {

    private static final String CLASS_SUFFIX = ".class";
    private static final String PACKAGE_INFO = "package-info.class";

    private final ClassFileReader reader;

    /** A scanner with a default {@link ClassFileReader}. */
    public ZipBinaryJarScanner() {
        this(new ClassFileReader());
    }

    @Override
    public JarScanSummary scan(Path jar, Consumer<ScannedClass> consumer) {
        int types = 0;
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory() || !name.endsWith(CLASS_SUFFIX) || name.startsWith("META-INF/")
                        || name.equals("module-info.class")
                        || name.equals(PACKAGE_INFO) || name.endsWith("/" + PACKAGE_INFO)) {
                    continue;
                }
                Optional<ScannedClass> scanned = readEntry(zip, entry, jar);
                if (scanned.isPresent()) {
                    types++;
                    consumer.accept(scanned.get());
                }
            }
        } catch (IOException e) {
            throw new JarReadException("cannot read jar " + jar + ": " + e.getMessage(), e);
        }
        return new JarScanSummary(types);
    }

    /** Reads one class entry: the type, or empty if it is not a named type or unreadable (logged). */
    private Optional<ScannedClass> readEntry(ZipFile zip, ZipEntry entry, Path jar) throws IOException {
        byte[] bytes;
        try (InputStream in = zip.getInputStream(entry)) {
            bytes = in.readAllBytes();
        }
        try {
            return reader.read(bytes);
        } catch (IllegalArgumentException e) {
            log.warn("skipping unreadable class {} in {}: {}", entry.getName(), jar, e.getMessage());
            return Optional.empty();
        }
    }
}
