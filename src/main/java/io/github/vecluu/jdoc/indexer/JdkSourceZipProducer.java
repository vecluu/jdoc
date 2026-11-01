package io.github.vecluu.jdoc.indexer;

import io.github.vecluu.jdoc.error.IndexException;
import io.github.vecluu.jdoc.error.JarReadException;
import io.github.vecluu.jdoc.model.DocSource;
import io.github.vecluu.jdoc.model.MemberDescriptor;
import io.github.vecluu.jdoc.model.TypeName;
import io.github.vecluu.jdoc.source.JavaSourceParser;
import io.github.vecluu.jdoc.source.KnownTypes;
import io.github.vecluu.jdoc.source.ParsedMember;
import io.github.vecluu.jdoc.source.ParsedSourceFile;
import io.github.vecluu.jdoc.source.ParsedType;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import lombok.extern.slf4j.Slf4j;

/**
 * Produces the JDK's types from {@code src.zip} ({@code java.base/java/util/Optional.java}, …): lists every
 * {@code .java} file first (all of them form the {@link KnownTypes}, so supertype names resolve exactly), keeps the
 * packages accepted by {@link JdkPackageFilter} (exports read from each module's {@code module-info.java}), then
 * parses them on a bounded pool of worker threads, one {@link JavaSourceParser} per worker. The calling thread
 * receives the results and passes them to the sink, so a single thread writes to the database.
 */
@Slf4j
final class JdkSourceZipProducer implements EntryProducer {

    private static final String JAVA = ".java";
    private static final String MODULE_INFO = "module-info.java";
    private static final String PACKAGE_INFO = "package-info.java";
    private static final int BATCH_SIZE = 1000;

    private final Path srcZip;
    private final JdkPackageFilter filter;
    private final Supplier<JavaSourceParser> parsers;
    private final int threads;

    JdkSourceZipProducer(Path srcZip, JdkPackageFilter filter, Supplier<JavaSourceParser> parsers, int threads) {
        if (threads < 1) {
            throw new IllegalArgumentException("threads must be positive: " + threads);
        }
        this.srcZip = srcZip;
        this.filter = filter;
        this.parsers = parsers;
        this.threads = threads;
    }

    @Override
    public void produce(Consumer<List<TypeEntry>> sink) {
        ZipFile zip;
        try {
            zip = new ZipFile(srcZip.toFile());
        } catch (java.io.IOException e) {
            throw new JarReadException("cannot open " + srcZip + ": " + e.getMessage(), e);
        }
        try (ZipFile closing = zip) {
            Listing listing = list(zip);
            log.debug("{}: {} source files, {} to index", srcZip, listing.knownTypes().size(), listing.files().size());
            parseAll(zip, listing, sink);
        } catch (java.io.IOException e) {
            throw new JarReadException("cannot close " + srcZip + ": " + e.getMessage(), e);
        }
    }

    /** Every {@code .java} file's type name, and the files to index. */
    private record Listing(Set<TypeName> knownTypes, List<String> files) {
    }

    private Listing list(ZipFile zip) {
        List<String> javaFiles = new ArrayList<>();
        Set<String> modules = new HashSet<>();
        zip.stream().map(ZipEntry::getName).filter(name -> name.endsWith(JAVA)).forEach(name -> {
            javaFiles.add(name);
            int slash = name.indexOf('/');
            if (slash > 0 && name.equals(name.substring(0, slash) + "/" + MODULE_INFO)) {
                modules.add(name.substring(0, slash));
            }
        });
        Map<String, Optional<Set<String>>> exports = new HashMap<>();
        Set<TypeName> known = new HashSet<>();
        List<String> files = new ArrayList<>();
        for (String name : javaFiles) {
            String module = moduleOf(name, modules);
            String relative = module.isEmpty() ? name : name.substring(module.length() + 1);
            String fileName = relative.substring(relative.lastIndexOf('/') + 1);
            if (fileName.equals(MODULE_INFO) || fileName.equals(PACKAGE_INFO) || fileName.indexOf('-') >= 0) {
                continue;
            }
            int lastSlash = relative.lastIndexOf('/');
            String packageName = lastSlash < 0 ? "" : relative.substring(0, lastSlash).replace('/', '.');
            String simpleName = fileName.substring(0, fileName.length() - JAVA.length());
            known.add(TypeName.of(packageName, List.of(simpleName)));
            Optional<Set<String>> moduleExports = exports.computeIfAbsent(module, m -> exportsOf(zip, m));
            if (filter.includes(packageName, moduleExports)) {
                files.add(name);
            }
        }
        Collections.sort(files);
        return new Listing(Set.copyOf(known), List.copyOf(files));
    }

    private static String moduleOf(String entryName, Set<String> modules) {
        int slash = entryName.indexOf('/');
        return slash > 0 && modules.contains(entryName.substring(0, slash)) ? entryName.substring(0, slash) : "";
    }

    private Optional<Set<String>> exportsOf(ZipFile zip, String module) {
        if (module.isEmpty()) {
            return Optional.empty();
        }
        return read(zip, module + "/" + MODULE_INFO).map(JdkPackageFilter::unqualifiedExports);
    }

    private Optional<String> read(ZipFile zip, String entryName) {
        ZipEntry entry = zip.getEntry(entryName);
        if (entry == null) {
            return Optional.empty();
        }
        try (var in = zip.getInputStream(entry)) {
            return Optional.of(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.io.IOException e) {
            throw new JarReadException("cannot read " + entryName + " from " + srcZip + ": " + e.getMessage(), e);
        }
    }

    /** One parsed file, or the failure that stopped a worker. */
    private record FileResult(List<TypeEntry> entries, Optional<Throwable> failure) {
    }

    private void parseAll(ZipFile zip, Listing listing, Consumer<List<TypeEntry>> sink) {
        KnownTypes knownTypes = KnownTypes.of(listing.knownTypes());
        List<String> files = listing.files();
        BlockingQueue<FileResult> results = new ArrayBlockingQueue<>(256);
        AtomicInteger next = new AtomicInteger();
        AtomicBoolean stop = new AtomicBoolean();
        ExecutorService pool = Executors.newFixedThreadPool(threads, Thread.ofPlatform()
                .name("jdoc-jdk-parser-", 0).daemon(true).factory());
        try {
            for (int i = 0; i < threads; i++) {
                pool.execute(() -> work(zip, files, knownTypes, next, stop, results));
            }
            List<TypeEntry> batch = new ArrayList<>();
            for (int received = 0; received < files.size(); received++) {
                FileResult result = results.take();
                if (result.failure().isPresent()) {
                    throw new IndexException("indexing the JDK failed: " + result.failure().get(),
                            result.failure().get());
                }
                batch.addAll(result.entries());
                if (batch.size() >= BATCH_SIZE) {
                    sink.accept(List.copyOf(batch));
                    batch.clear();
                }
            }
            sink.accept(List.copyOf(batch));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IndexException("interrupted while indexing the JDK", e);
        } finally {
            stop.set(true);
            pool.shutdownNow();
        }
    }

    private void work(ZipFile zip, List<String> files, KnownTypes knownTypes, AtomicInteger next, AtomicBoolean stop,
                      BlockingQueue<FileResult> results) {
        try (JavaSourceParser parser = parsers.get()) {
            SourceFiles sources = new SourceFiles(new ZipEntries(zip), parser, knownTypes, DocSource.JDK);
            int index;
            while (!stop.get() && (index = next.getAndIncrement()) < files.size()) {
                String file = files.get(index);
                List<TypeEntry> entries;
                try {
                    entries = sources.parse(file).map(JdkSourceZipProducer::entries).orElse(List.of());
                } catch (RuntimeException e) {
                    log.warn("skipping {}: {}", file, e.toString());
                    entries = List.of();
                }
                results.put(new FileResult(entries, Optional.empty()));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException | Error e) {
            try {
                results.put(new FileResult(List.of(), Optional.of(e)));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static List<TypeEntry> entries(ParsedSourceFile file) {
        List<TypeEntry> entries = new ArrayList<>(file.types().size());
        for (ParsedType type : file.types()) {
            List<MemberDescriptor> members = type.members().stream()
                    .map(ParsedMember::descriptor)
                    .filter(member -> member.visibility().isApi())
                    .toList();
            entries.add(new TypeEntry(type.descriptor(), members));
        }
        return entries;
    }

    /** The open zip as a {@link SourceArchive}; closing is left to the owner. */
    private final class ZipEntries implements SourceArchive {
        private final ZipFile zip;

        ZipEntries(ZipFile zip) {
            this.zip = zip;
        }

        @Override
        public Optional<String> read(String entryPath) {
            return JdkSourceZipProducer.this.read(zip, entryPath);
        }

        @Override
        public void close() {
            // owned by the producer
        }
    }
}
