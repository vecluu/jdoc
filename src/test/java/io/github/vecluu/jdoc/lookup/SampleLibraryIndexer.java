package io.github.vecluu.jdoc.lookup;

import io.github.vecluu.jdoc.bytecode.ZipBinaryJarScanner;
import io.github.vecluu.jdoc.indexer.FileStamp;
import io.github.vecluu.jdoc.indexer.JarArtifactIndexer;
import io.github.vecluu.jdoc.indexer.SiblingDocJarLocator;
import io.github.vecluu.jdoc.indexer.ZipArchiveOpener;
import io.github.vecluu.jdoc.javadoc.JavadocPageParser;
import io.github.vecluu.jdoc.source.JavaSourceParser;
import io.github.vecluu.jdoc.testsupport.BuiltLibrary;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

/**
 * IT helper: indexes a built library with the real {@link JarArtifactIndexer} (per-type fallback sources jar, then
 * javadoc page, then decompiled; rendered docs stored) into a {@link LookupIndex} and adds it to the scope.
 */
final class SampleLibraryIndexer {

    private SampleLibraryIndexer() {
    }

    static ScopedArtifact index(LookupIndex index, BuiltLibrary library) {
        JarArtifactIndexer indexer = new JarArtifactIndexer(new ZipBinaryJarScanner(), new SiblingDocJarLocator(),
                new ZipArchiveOpener(), new JavadocPageParser(), JavaSourceParser::new, index.store(),
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
        return index.register(indexer.index(library.gav(), library.binaryJar(), new FileStamp(1L, 1L)));
    }
}
