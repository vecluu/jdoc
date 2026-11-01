package io.github.vecluu.jdoc.testsupport;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Stream;

/**
 * Writes deterministic jars for tests: a manifest, directory entries, entries sorted by name and a fixed timestamp.
 */
public final class JarWriter {

    /** Timestamp used for every entry. */
    public static final FileTime ENTRY_TIME = FileTime.from(Instant.parse("2024-01-01T00:00:00Z"));

    private JarWriter() {
    }

    /** Writes every regular file below {@code root} into a jar, with paths relative to {@code root}. */
    public static Path writeDirectory(Path root, Path jar) throws IOException {
        Map<String, byte[]> entries = new TreeMap<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                entries.put(root.relativize(file).toString().replace('\\', '/'), Files.readAllBytes(file));
            }
        }
        return writeEntries(entries, jar);
    }

    /** Writes the given entries (path to content) into a jar, adding parent directory entries. */
    public static Path writeEntries(Map<String, byte[]> entries, Path jar) throws IOException {
        Map<String, byte[]> sorted = new TreeMap<>(entries);
        TreeSet<String> directories = new TreeSet<>();
        for (String name : sorted.keySet()) {
            int slash = name.indexOf('/');
            while (slash > 0) {
                directories.add(name.substring(0, slash + 1));
                slash = name.indexOf('/', slash + 1);
            }
        }
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        if (jar.getParent() != null) {
            Files.createDirectories(jar.getParent());
        }
        try (OutputStream out = Files.newOutputStream(jar);
             JarOutputStream jarOut = new JarOutputStream(out)) {
            JarEntry manifestEntry = entry("META-INF/MANIFEST.MF");
            jarOut.putNextEntry(manifestEntry);
            manifest.write(jarOut);
            jarOut.closeEntry();
            for (String directory : directories) {
                if (!directory.equals("META-INF/")) {
                    jarOut.putNextEntry(entry(directory));
                    jarOut.closeEntry();
                }
            }
            for (Map.Entry<String, byte[]> file : sorted.entrySet()) {
                if (file.getKey().equals("META-INF/MANIFEST.MF")) {
                    continue;
                }
                jarOut.putNextEntry(entry(file.getKey()));
                jarOut.write(file.getValue());
                jarOut.closeEntry();
            }
        }
        return jar;
    }

    private static JarEntry entry(String name) {
        JarEntry entry = new JarEntry(name);
        entry.setLastModifiedTime(ENTRY_TIME);
        return entry;
    }
}
