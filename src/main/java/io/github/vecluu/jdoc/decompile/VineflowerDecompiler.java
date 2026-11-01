package io.github.vecluu.jdoc.decompile;

import io.github.vecluu.jdoc.model.TypeName;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Map;
import java.util.TreeMap;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.java.decompiler.main.decompiler.BaseDecompiler;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.main.extern.IResultSaver;

/**
 * {@link Decompiler} running Vineflower in-process. Only the class entries of the requested top-level type
 * ({@code Outer.class} and every {@code Outer$*.class}) are decompiled; the rest of the jar is a lazy library for
 * resolving references. Output keeps generic signatures and parameter/local names where the class files carry
 * them, has no banner and no decompiler comments, uses four-space indentation and {@code \n} line ends, and is
 * deterministic (single-threaded). Vineflower's log goes to slf4j at debug level.
 */
@Slf4j
public final class VineflowerDecompiler implements Decompiler {

    private static final Map<String, Object> OPTIONS = Map.ofEntries(
            Map.entry(IFernflowerPreferences.DECOMPILE_GENERIC_SIGNATURES, "1"),
            Map.entry(IFernflowerPreferences.USE_DEBUG_VAR_NAMES, "1"),
            Map.entry(IFernflowerPreferences.USE_METHOD_PARAMETERS, "1"),
            Map.entry(IFernflowerPreferences.DECOMPILE_INNER, "1"),
            Map.entry(IFernflowerPreferences.DECOMPILE_ENUM, "1"),
            Map.entry(IFernflowerPreferences.REMOVE_BRIDGE, "1"),
            Map.entry(IFernflowerPreferences.REMOVE_SYNTHETIC, "1"),
            Map.entry(IFernflowerPreferences.BANNER, ""),
            Map.entry(IFernflowerPreferences.DECOMPILER_COMMENTS, "0"),
            Map.entry(IFernflowerPreferences.SOURCE_FILE_COMMENTS, "0"),
            Map.entry(IFernflowerPreferences.DUMP_BYTECODE_ON_ERROR, "0"),
            Map.entry(IFernflowerPreferences.DUMP_EXCEPTION_ON_ERROR, "0"),
            Map.entry(IFernflowerPreferences.BYTECODE_SOURCE_MAPPING, "0"),
            Map.entry(IFernflowerPreferences.NEW_LINE_SEPARATOR, "1"),
            Map.entry(IFernflowerPreferences.INDENT_STRING, "    "),
            Map.entry(IFernflowerPreferences.THREADS, "1"),
            Map.entry(IFernflowerPreferences.SKIP_EXTRA_FILES, "1"),
            Map.entry(IFernflowerPreferences.LOG_LEVEL, "warn"));

    @Override
    public String decompile(Path jar, TypeName type) {
        TypeName topLevel = type.topLevel();
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            Map<String, byte[]> classes = readClasses(zip, topLevel);
            if (classes.isEmpty()) {
                throw new DecompilationException(
                        "Cannot decompile " + topLevel.displayName() + ": no class entry " + topLevel.classEntryPath()
                                + " in " + jar);
            }
            return run(jar, zip, topLevel, classes);
        } catch (IOException e) {
            throw new DecompilationException("Cannot decompile " + topLevel.displayName() + ": cannot read " + jar, e);
        }
    }

    private static Map<String, byte[]> readClasses(ZipFile zip, TypeName topLevel) throws IOException {
        String internalName = topLevel.internalName();
        String outerEntry = internalName + ".class";
        String nestedPrefix = internalName + "$";
        Map<String, byte[]> classes = new TreeMap<>();
        ZipEntry outer = zip.getEntry(outerEntry);
        if (outer == null) {
            return classes;
        }
        try (InputStream in = zip.getInputStream(outer)) {
            classes.put(internalName, in.readAllBytes());
        }
        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String name = entry.getName();
            if (name.startsWith(nestedPrefix) && name.endsWith(".class") && name.indexOf('/', nestedPrefix.length()) < 0) {
                try (InputStream in = zip.getInputStream(entry)) {
                    classes.put(name.substring(0, name.length() - ".class".length()), in.readAllBytes());
                }
            }
        }
        return classes;
    }

    private static String run(Path jar, ZipFile zip, TypeName topLevel, Map<String, byte[]> classes) {
        String label = jar.getFileName() + "!" + topLevel.internalName();
        SelectedClassesSource source = new SelectedClassesSource(label, topLevel.internalName(), classes);
        Slf4jFernflowerLogger logger = new Slf4jFernflowerLogger();
        try {
            BaseDecompiler decompiler = new BaseDecompiler(NoOpResultSaver.INSTANCE, OPTIONS, logger);
            decompiler.addSource(source);
            decompiler.addLibrary(new JarLibrarySource(zip));
            decompiler.decompileContext();
        } catch (RuntimeException | LinkageError e) {
            throw new DecompilationException("Cannot decompile " + topLevel.displayName() + ": " + e.getMessage(), e);
        }
        if (logger.classFailure().isPresent()) {
            Throwable cause = logger.classFailure().get();
            throw new DecompilationException(
                    "Cannot decompile " + topLevel.displayName() + ": " + cause.getMessage(), cause);
        }
        String text = source.output().orElseThrow(() -> new DecompilationException(
                "Cannot decompile " + topLevel.displayName() + ": Vineflower produced no output for " + label));
        log.debug("Decompiled {} ({} classes, {} chars)", label, classes.size(), text.length());
        return text;
    }

    /** Vineflower requires a saver; all output goes through {@link SelectedClassesSource}'s sink instead. */
    private enum NoOpResultSaver implements IResultSaver {
        INSTANCE;

        @Override
        public void saveFolder(String path) {
            // unused
        }

        @Override
        public void copyFile(String source, String path, String entryName) {
            // unused
        }

        @Override
        public void saveClassFile(String path, String qualifiedName, String entryName, String content, int[] mapping) {
            // unused
        }

        @Override
        public void createArchive(String path, String archiveName, Manifest manifest) {
            // unused
        }

        @Override
        public void saveDirEntry(String path, String archiveName, String entryName) {
            // unused
        }

        @Override
        public void copyEntry(String source, String path, String archiveName, String entry) {
            // unused
        }

        @Override
        public void saveClassEntry(String path, String archiveName, String qualifiedName, String entryName,
                String content) {
            // unused
        }

        @Override
        public void closeArchive(String path, String archiveName) {
            // unused
        }
    }
}
