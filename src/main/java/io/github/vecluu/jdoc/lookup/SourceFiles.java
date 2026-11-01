package io.github.vecluu.jdoc.lookup;

import io.github.vecluu.jdoc.model.DocSource;
import io.github.vecluu.jdoc.source.JavaSource;
import io.github.vecluu.jdoc.source.JavaSourceParser;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Reads and parses {@code .java} entries on demand, caching texts and parse results for the lifetime of the
 * instance (one call touches few files). The javac-based parser is created on first use and closed by
 * {@link #close()}. Not thread-safe.
 */
public final class SourceFiles implements AutoCloseable {

    private final JarEntryReader reader;
    private final Supplier<JavaSourceParser> parserFactory;
    private final Map<String, Optional<String>> texts = new HashMap<>();
    private final Map<String, Optional<ParsedText>> parsed = new HashMap<>();
    private JavaSourceParser parser;

    public SourceFiles(JarEntryReader reader, Supplier<JavaSourceParser> parserFactory) {
        this.reader = reader;
        this.parserFactory = parserFactory;
    }

    /** Reads entries with {@code reader} and parses with a default {@link JavaSourceParser}. */
    public SourceFiles(JarEntryReader reader) {
        // An anonymous class: a constructor reference or a lambda (whose method returns JavaSourceParser) would load
        // the parser class on every call, although only jdoc source parses.
        this(reader, new Supplier<>() {
            @Override
            public JavaSourceParser get() {
                return new JavaSourceParser();
            }
        });
    }

    /**
     * Reads and parses a source entry.
     *
     * @param jar       sources jar or {@code src.zip}
     * @param entryPath the {@code .java} entry
     * @param docSource {@link DocSource#SOURCES} or {@link DocSource#JDK}
     * @return the parsed text, or empty if the entry does not exist
     */
    public Optional<ParsedText> parse(Path jar, String entryPath, DocSource docSource) {
        String key = jar + "!" + entryPath;
        Optional<ParsedText> cached = parsed.get(key);
        if (cached != null) {
            return cached;
        }
        Optional<ParsedText> result = text(jar, entryPath).map(text -> parseText(entryPath, text, docSource));
        parsed.put(key, result);
        return result;
    }

    /**
     * Reads a source entry without parsing it.
     *
     * @param jar       sources jar or {@code src.zip}
     * @param entryPath the {@code .java} entry
     * @return the text, or empty if the entry does not exist
     */
    public Optional<String> text(Path jar, String entryPath) {
        return texts.computeIfAbsent(jar + "!" + entryPath, key -> reader.readText(jar, entryPath));
    }

    /** Parses text that does not come from a jar entry (decompiled source); not cached. */
    public ParsedText parseText(String path, String text, DocSource docSource) {
        return new ParsedText(text, parser().parse(new JavaSource(path, text, docSource)));
    }

    private JavaSourceParser parser() {
        if (parser == null) {
            parser = parserFactory.get();
        }
        return parser;
    }

    @Override
    public void close() {
        if (parser != null) {
            parser.close();
            parser = null;
        }
    }
}
