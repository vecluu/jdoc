package io.github.vecluu.jdoc.bytecode;

import io.github.vecluu.jdoc.error.JarReadException;
import io.github.vecluu.jdoc.model.TypeName;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Optional;
import java.util.TreeSet;
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
        int skipped = 0;
        TreeSet<String> packageInfos = new TreeSet<>();
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory() || !name.endsWith(CLASS_SUFFIX) || name.startsWith("META-INF/")
                        || name.equals("module-info.class")) {
                    continue;
                }
                if (name.equals(PACKAGE_INFO) || name.endsWith("/" + PACKAGE_INFO)) {
                    int slash = name.lastIndexOf('/');
                    packageInfos.add(slash < 0 ? "" : name.substring(0, slash).replace('/', '.'));
                    continue;
                }
                EntryResult result = readEntry(zip, entry, jar);
                if (result.unreadable()) {
                    skipped++;
                } else if (result.scanned().isPresent()) {
                    types++;
                    consumer.accept(result.scanned().get());
                }
            }
        } catch (IOException e) {
            throw new JarReadException("cannot read jar " + jar + ": " + e.getMessage(), e);
        }
        return new JarScanSummary(types, skipped, packageInfos.stream().toList());
    }

    @Override
    public Optional<ScannedClass> readClass(Path jar, TypeName type) {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry entry = zip.getEntry(type.classEntryPath());
            if (entry == null) {
                return Optional.empty();
            }
            return readEntry(zip, entry, jar).scanned();
        } catch (IOException e) {
            throw new JarReadException("cannot read jar " + jar + ": " + e.getMessage(), e);
        }
    }

    private EntryResult readEntry(ZipFile zip, ZipEntry entry, Path jar) throws IOException {
        byte[] bytes;
        try (InputStream in = zip.getInputStream(entry)) {
            bytes = in.readAllBytes();
        }
        try {
            return new EntryResult(reader.read(bytes), false);
        } catch (IllegalArgumentException e) {
            log.warn("skipping unreadable class {} in {}: {}", entry.getName(), jar, e.getMessage());
            return new EntryResult(Optional.empty(), true);
        }
    }

    /** Outcome of reading one entry: the type (empty if not a named type) or an unreadable marker. */
    private record EntryResult(Optional<ScannedClass> scanned, boolean unreadable) {
    }
}
