package io.github.vecluu.jdoc.module;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * A {@link PomAnalyzer} that parses only when a POM input changed. The parsing analysis runs with its file access
 * recorded; the result is stored as a {@link ModuleSnapshot} with a {@link PathStamp} of every path it read or
 * tested (POMs of the local parent chain and the reactor, absent parent candidates, directories, a SNAPSHOT parent in
 * the repository). The next call re-stamps those paths and, if none changed, returns the stored analysis without
 * starting the XML parser or hashing anything. Any difference (edit, new or deleted file, a file replaced by a
 * directory, a touched SNAPSHOT parent) falls back to parsing, which stores a fresh snapshot.
 *
 * <p>File times have limited resolution, so an edit right after a snapshot could keep the stamp. A snapshot is
 * therefore only stored when every stamped file last changed at least {@link #SETTLE_TIME} before now; until then
 * every call parses (as without a snapshot).
 */
@Slf4j
public final class SnapshotPomAnalyzer implements PomAnalyzer {

    /** How long a POM must be unchanged before an analysis of it is stored. */
    public static final Duration SETTLE_TIME = Duration.ofSeconds(2);

    private final PomAnalyzer parsing;
    private final RecordingPomFiles files;
    private final ModuleSnapshotStore store;
    private final PathStamper stamper;
    private final Path localRepository;
    private final Clock clock;

    /**
     * @param parsing         the analysis to run on a miss; must do all its file access through {@code files}
     * @param files           the recorded file access of {@code parsing}
     * @param store           where snapshots live
     * @param stamper         stamps paths
     * @param localRepository the local Maven repository {@code parsing} uses
     * @param clock           decides whether stamped files have settled
     */
    public SnapshotPomAnalyzer(PomAnalyzer parsing, RecordingPomFiles files, ModuleSnapshotStore store,
                               PathStamper stamper, Path localRepository, Clock clock) {
        this.parsing = Objects.requireNonNull(parsing, "parsing");
        this.files = Objects.requireNonNull(files, "files");
        this.store = Objects.requireNonNull(store, "store");
        this.stamper = Objects.requireNonNull(stamper, "stamper");
        this.localRepository = Objects.requireNonNull(localRepository, "localRepository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public PomAnalysis analyze(Path moduleDirectory) {
        Optional<ModuleSnapshot> stored = store.read(moduleDirectory)
                .filter(snapshot -> snapshot.localRepository().equals(localRepository))
                .filter(this::unchanged);
        if (stored.isPresent()) {
            log.debug("POMs of {} unchanged since the stored analysis", moduleDirectory);
            return stored.get().analysis();
        }
        RecordingPomFiles.Recorded<PomAnalysis> recorded = files.record(() -> parsing.analyze(moduleDirectory));
        save(moduleDirectory, recorded);
        return recorded.value();
    }

    private boolean unchanged(ModuleSnapshot snapshot) {
        for (Map.Entry<Path, PathStamp> entry : snapshot.stamps().entrySet()) {
            if (!stamper.stamp(entry.getKey()).value().equals(entry.getValue().value())) {
                log.debug("{} changed since the stored analysis", entry.getKey());
                return false;
            }
        }
        return true;
    }

    private void save(Path moduleDirectory, RecordingPomFiles.Recorded<PomAnalysis> recorded) {
        Instant settled = clock.instant().minus(SETTLE_TIME);
        Map<Path, PathStamp> stamps = new LinkedHashMap<>();
        for (Path path : recorded.paths()) {
            PathStamp stamp = stamper.stamp(path);
            if (stamp.changedAt().filter(changed -> !changed.isBefore(settled)).isPresent()) {
                log.debug("not storing the analysis of {}: {} changed too recently", moduleDirectory, path);
                return;
            }
            stamps.put(path, stamp);
        }
        store.write(new ModuleSnapshot(moduleDirectory, localRepository, recorded.value(), stamps));
    }
}
