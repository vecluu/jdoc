package io.github.vecluu.jdoc.indexer;

import io.github.vecluu.jdoc.error.SourceParseException;
import io.github.vecluu.jdoc.model.DocSource;
import io.github.vecluu.jdoc.source.JavaSource;
import io.github.vecluu.jdoc.source.JavaSourceParser;
import io.github.vecluu.jdoc.source.KnownTypes;
import io.github.vecluu.jdoc.source.ParsedSourceFile;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** The {@code .java} files of an opened sources jar, parsed on request. Not thread-safe. */
@Slf4j
@RequiredArgsConstructor
final class SourceFiles {

    private final SourceArchive archive;
    private final JavaSourceParser parser;
    private final KnownTypes knownTypes;
    private final DocSource docSource;

    /**
     * Parses one file.
     *
     * @return the parsed file, or empty if the archive has no such entry or javac failed internally (logged)
     */
    Optional<ParsedSourceFile> parse(String path) {
        Optional<String> text = archive.read(path);
        if (text.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(parser.parse(new JavaSource(path, text.get(), docSource), knownTypes));
        } catch (SourceParseException e) {
            log.warn("cannot parse {}: {}", path, e.getMessage());
            return Optional.empty();
        }
    }
}
