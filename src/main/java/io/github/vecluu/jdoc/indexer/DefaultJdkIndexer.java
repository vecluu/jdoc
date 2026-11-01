package io.github.vecluu.jdoc.indexer;

import io.github.vecluu.jdoc.bytecode.ClassFileReader;
import io.github.vecluu.jdoc.config.JdkInfo;
import io.github.vecluu.jdoc.model.IndexedArtifact;
import io.github.vecluu.jdoc.source.JavaSourceParser;
import java.nio.file.Path;
import java.time.Clock;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link JdkIndexer}: indexes {@code $JAVA_HOME/lib/src.zip} (doc source JDK, parsed in parallel) or, if the JDK has
 * no {@code src.zip}, the {@code jrt:/} filesystem bytecode only (doc source DECOMPILED, one warning logged).
 */
@Slf4j
public final class DefaultJdkIndexer implements JdkIndexer {

    private final IndexStore store;
    private final Supplier<JavaSourceParser> parsers;
    private final ClassFileReader classReader;
    private final JdkPackageFilter filter;
    private final int threads;
    private final Clock clock;

    /**
     * @param store       where to write
     * @param parsers     creates one parser per worker thread
     * @param classReader reads class files for the {@code jrt:/} fallback
     * @param filter      the packages to index
     * @param threads     parser threads (bounded pool)
     * @param clock       for the indexed-at time
     */
    public DefaultJdkIndexer(IndexStore store, Supplier<JavaSourceParser> parsers, ClassFileReader classReader,
                             JdkPackageFilter filter, int threads, Clock clock) {
        if (threads < 1) {
            throw new IllegalArgumentException("threads must be positive: " + threads);
        }
        this.store = store;
        this.parsers = parsers;
        this.classReader = classReader;
        this.filter = filter;
        this.threads = threads;
        this.clock = clock;
    }

    /** A sensible parser-thread count for this machine: the available processors, at most 8. */
    public static int defaultThreads() {
        return Math.max(1, Math.min(8, Runtime.getRuntime().availableProcessors()));
    }

    @Override
    public StoredArtifact index(JdkInfo jdk, FileStamp stamp) {
        long start = System.nanoTime();
        Path indexed = indexedFile(jdk);
        EntryProducer producer;
        if (jdk.srcZip().isPresent()) {
            producer = new JdkSourceZipProducer(jdk.srcZip().get(), filter, parsers, threads);
        } else {
            log.warn("{} has no lib/src.zip: JDK types are indexed from bytecode only, without docs", jdk.javaHome());
            producer = new JrtBytecodeProducer(jdk.javaHome(), filter, classReader);
        }
        IndexedArtifact artifact = IndexedArtifact.builder()
                .key(jdk.artifactKey())
                .jarPath(indexed)
                .jarMtime(stamp.mtimeMillis())
                .jarSize(stamp.size())
                .indexedAt(clock.instant())
                .build();
        StoredArtifact stored = store.replace(artifact, producer);
        log.debug("indexed {} ({} types, {} members) in {} ms", jdk.artifactKey().label(), stored.typeCount(),
                stored.memberCount(), (System.nanoTime() - start) / 1_000_000);
        return stored;
    }
}
