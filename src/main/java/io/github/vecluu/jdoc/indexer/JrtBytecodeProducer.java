package io.github.vecluu.jdoc.indexer;

import io.github.vecluu.jdoc.bytecode.ClassFileReader;
import io.github.vecluu.jdoc.bytecode.ScannedClass;
import io.github.vecluu.jdoc.error.JarReadException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.module.ModuleDescriptor;
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/**
 * Produces the JDK's types from the {@code jrt:/} filesystem of a Java home, bytecode only (doc source
 * {@link io.github.vecluu.jdoc.model.DocSource#DECOMPILED}): the fallback when the JDK has no {@code src.zip}.
 * Packages are those of {@link JdkPackageFilter}, with exports read from each module's {@code module-info.class}.
 */
@Slf4j
final class JrtBytecodeProducer implements EntryProducer {

    private static final String CLASS = ".class";

    private final Path javaHome;
    private final JdkPackageFilter filter;
    private final ClassFileReader reader;

    JrtBytecodeProducer(Path javaHome, JdkPackageFilter filter, ClassFileReader reader) {
        this.javaHome = javaHome;
        this.filter = filter;
        this.reader = reader;
    }

    @Override
    public void produce(EntrySink sink) {
        try (FileSystem jrt = FileSystems.newFileSystem(URI.create("jrt:/"),
                Map.of("java.home", javaHome.toString()))) {
            for (Path module : sorted(jrt.getPath("/modules"))) {
                List<TypeEntry> entries = new ArrayList<>();
                for (String packageName : packages(module)) {
                    Path directory = module.resolve(packageName.replace('.', '/'));
                    if (Files.isDirectory(directory)) {
                        for (Path file : sorted(directory)) {
                            read(file).ifPresent(scanned -> entries.add(
                                    TypeEntry.undocumented(scanned.type(), scanned.members())));
                        }
                    }
                }
                sink.types(entries);
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof JarReadException readException) {
                throw readException;
            }
            throw new JarReadException("cannot read the jrt:/ filesystem of " + javaHome + ": " + e.getMessage(), e);
        }
    }

    private Set<String> packages(Path module) throws IOException {
        Path moduleInfo = module.resolve("module-info.class");
        if (!Files.isRegularFile(moduleInfo)) {
            return Set.of();
        }
        ModuleDescriptor descriptor;
        try (InputStream in = Files.newInputStream(moduleInfo)) {
            descriptor = ModuleDescriptor.read(in);
        }
        Set<String> exports = descriptor.exports().stream()
                .filter(export -> !export.isQualified())
                .map(ModuleDescriptor.Exports::source)
                .collect(Collectors.toSet());
        Set<String> result = new TreeSet<>();
        for (String packageName : exports) {
            if (filter.includes(packageName, Optional.of(exports))) {
                result.add(packageName);
            }
        }
        return result;
    }

    private Optional<ScannedClass> read(Path file) throws IOException {
        String name = file.getFileName().toString();
        if (!name.endsWith(CLASS) || name.equals("module-info.class") || name.equals("package-info.class")
                || !Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            return reader.read(Files.readAllBytes(file));
        } catch (IllegalArgumentException e) {
            log.debug("skipping unreadable class {}: {}", file, e.getMessage());
            return Optional.empty();
        }
    }

    private static List<Path> sorted(Path directory) throws IOException {
        try (DirectoryStream<Path> children = Files.newDirectoryStream(directory)) {
            List<Path> result = new ArrayList<>();
            children.forEach(result::add);
            result.sort(null);
            return result;
        }
    }
}
