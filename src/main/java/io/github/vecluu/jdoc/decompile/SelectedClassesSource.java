package io.github.vecluu.jdoc.decompile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jetbrains.java.decompiler.main.extern.IContextSource;
import org.jetbrains.java.decompiler.main.extern.IResultSaver;

/**
 * Vineflower input holding only the class files of one top-level type (in memory) and capturing the decompiled
 * text of that top-level type.
 */
final class SelectedClassesSource implements IContextSource {

    private final String name;
    private final String topLevelInternalName;
    private final Map<String, byte[]> classes;
    private volatile String output;

    /**
     * @param name                 label for logging
     * @param topLevelInternalName internal name of the top-level type, e.g. {@code a/b/Outer}
     * @param classes              internal name → class file bytes, the top-level type and its nested classes
     */
    SelectedClassesSource(String name, String topLevelInternalName, Map<String, byte[]> classes) {
        this.name = name;
        this.topLevelInternalName = topLevelInternalName;
        this.classes = new LinkedHashMap<>(classes);
    }

    /** The captured source of the top-level type after decompiling. */
    Optional<String> output() {
        return Optional.ofNullable(output);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Entries getEntries() {
        List<Entry> entries = new ArrayList<>();
        classes.keySet().forEach(className -> entries.add(Entry.atBase(className)));
        return new Entries(entries, List.of(), List.of());
    }

    @Override
    public byte[] getClassBytes(String className) {
        byte[] bytes = classes.get(className);
        return bytes == null ? null : bytes.clone();
    }

    @Override
    public InputStream getInputStream(String resource) {
        if (!resource.endsWith(CLASS_SUFFIX)) {
            return null;
        }
        byte[] bytes = classes.get(resource.substring(0, resource.length() - CLASS_SUFFIX.length()));
        return bytes == null ? null : new ByteArrayInputStream(bytes);
    }

    @Override
    public IOutputSink createOutputSink(IResultSaver saver) {
        return new IOutputSink() {
            @Override
            public void begin() {
                // nothing to prepare, output is kept in memory
            }

            @Override
            public void acceptClass(String qualifiedName, String fileName, String content, int[] mapping) {
                if (topLevelInternalName.equals(qualifiedName)) {
                    output = content;
                }
            }

            @Override
            public void acceptDirectory(String directory) {
                // directories are irrelevant for in-memory output
            }

            @Override
            public void acceptOther(String path) {
                // only class files are fed in
            }

            @Override
            public void close() {
                // nothing to release
            }
        };
    }
}
