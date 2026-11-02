package io.github.vecluu.jdoc.indexer;

import io.github.vecluu.jdoc.error.JarReadException;
import io.github.vecluu.jdoc.javadoc.JavadocJar;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** {@link ArchiveOpener} for real zip files; javadoc jars are read with {@link JavadocJar}. */
public final class ZipArchiveOpener implements ArchiveOpener {

    @Override
    public SourceArchive openSources(Path jar) {
        return ZipSourceArchive.open(jar);
    }

    @Override
    public JavadocArchive openJavadoc(Path jar) {
        JavadocJar javadoc = JavadocJar.open(jar);
        return new JavadocArchive() {
            @Override
            public Optional<String> page(String entryPath) {
                return javadoc.page(entryPath);
            }

            @Override
            public boolean hasPage(String entryPath) {
                return javadoc.hasPage(entryPath);
            }

            @Override
            public void close() {
                javadoc.close();
            }
        };
    }

    /** A zip of {@code .java} files; safe for concurrent reads. */
    static final class ZipSourceArchive implements SourceArchive {
        private final Path path;
        private final ZipFile zip;

        private ZipSourceArchive(Path path, ZipFile zip) {
            this.path = path;
            this.zip = zip;
        }

        static ZipSourceArchive open(Path jar) {
            try {
                return new ZipSourceArchive(jar, new ZipFile(jar.toFile(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new JarReadException("cannot open " + jar + ": " + e.getMessage(), e);
            }
        }

        @Override
        public Optional<String> read(String entryPath) {
            ZipEntry entry = zip.getEntry(entryPath);
            if (entry == null || entry.isDirectory()) {
                return Optional.empty();
            }
            try (InputStream in = zip.getInputStream(entry)) {
                return Optional.of(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new JarReadException("cannot read " + entryPath + " from " + path + ": " + e.getMessage(), e);
            }
        }

        @Override
        public void close() {
            try {
                zip.close();
            } catch (IOException e) {
                throw new JarReadException("cannot close " + path + ": " + e.getMessage(), e);
            }
        }
    }
}
