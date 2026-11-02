package io.github.vecluu.jdoc.javadoc;

import io.github.vecluu.jdoc.error.JarReadException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * A {@code -javadoc.jar} opened for reading pages. Keep it open while reading many pages (indexing) and close it
 * afterwards. Pages are decoded as UTF-8 unless their {@code <meta charset>} says otherwise. Not thread-safe for
 * concurrent close.
 */
public final class JavadocJar implements JavadocPageSource, AutoCloseable {

    private static final Pattern META_CHARSET =
            Pattern.compile("<meta[^>]*charset=[\"']?([A-Za-z0-9_\\-]+)", Pattern.CASE_INSENSITIVE);

    private final Path path;
    private final ZipFile zip;

    private JavadocJar(Path path, ZipFile zip) {
        this.path = path;
        this.zip = zip;
    }

    /**
     * Opens a javadoc jar.
     *
     * @throws JarReadException if the file cannot be opened as a zip
     */
    public static JavadocJar open(Path jar) {
        try {
            return new JavadocJar(jar, new ZipFile(jar.toFile()));
        } catch (IOException e) {
            throw new JarReadException("cannot open javadoc jar " + jar + ": " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<String> page(String entryPath) {
        ZipEntry entry = zip.getEntry(entryPath);
        if (entry == null || entry.isDirectory()) {
            return Optional.empty();
        }
        try (InputStream in = zip.getInputStream(entry)) {
            return Optional.of(decode(in.readAllBytes()));
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

    static String decode(byte[] bytes) {
        String head = new String(bytes, 0, Math.min(bytes.length, 2048), StandardCharsets.ISO_8859_1);
        Matcher matcher = META_CHARSET.matcher(head);
        Charset charset = StandardCharsets.UTF_8;
        if (matcher.find()) {
            try {
                charset = Charset.forName(matcher.group(1));
            } catch (IllegalArgumentException e) {
                charset = StandardCharsets.UTF_8;
            }
        }
        return new String(bytes, charset);
    }
}
