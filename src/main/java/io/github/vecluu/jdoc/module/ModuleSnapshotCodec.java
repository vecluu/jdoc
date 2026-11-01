package io.github.vecluu.jdoc.module;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * Encodes a {@link ModuleSnapshot} as tab-separated lines:
 *
 * <pre>
 * jdoc-module-snapshot	1
 * module	/work/app
 * repository	/home/me/.m2/repository
 * key	ab12...
 * fingerprint	&lt;sha256 or mtime:...&gt;	/work/app/pom.xml
 * reactor	/work
 * reactor-module	com.acme:app
 * stamp	file:512:1793527200000000000:1793527200000000000:2049:1234	/work/app/pom.xml
 * stamp	absent	/work/app/../pom.xml
 * </pre>
 *
 * <p>Bump {@link #FORMAT_VERSION} whenever {@link PomKeyCalculator} or {@link ReactorDetector} change what they
 * compute or which files they look at: snapshots of another version are ignored.
 */
@Slf4j
public final class ModuleSnapshotCodec {

    /** Version of the format and of the analysis it caches. */
    static final int FORMAT_VERSION = 1;

    private static final String MAGIC = "jdoc-module-snapshot";

    /**
     * Encodes a snapshot; empty if a path contains a tab or line break and so cannot be stored.
     */
    public Optional<String> encode(ModuleSnapshot snapshot) {
        StringBuilder text = new StringBuilder();
        List<String> values = new ArrayList<>();
        line(text, values, MAGIC, Integer.toString(FORMAT_VERSION));
        line(text, values, "module", snapshot.moduleDirectory().toString());
        line(text, values, "repository", snapshot.localRepository().toString());
        line(text, values, "key", snapshot.analysis().key().value());
        for (PomFingerprint fingerprint : snapshot.analysis().key().fingerprints()) {
            line(text, values, "fingerprint", fingerprint.fingerprint(), fingerprint.file().toString());
        }
        Reactor reactor = snapshot.analysis().reactor();
        line(text, values, "reactor", reactor.rootDirectory().toString());
        for (String coordinates : reactor.moduleCoordinates().stream().sorted().toList()) {
            line(text, values, "reactor-module", coordinates);
        }
        snapshot.stamps().forEach((path, stamp) -> line(text, values, "stamp", stamp.value(), path.toString()));
        boolean storable = values.stream().noneMatch(value -> value.isEmpty() || value.indexOf('\t') >= 0
                || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0);
        return storable ? Optional.of(text.toString()) : Optional.empty();
    }

    /** Decodes a snapshot; empty (logged at debug) if the text is malformed or of another format version. */
    public Optional<ModuleSnapshot> decode(String text) {
        try {
            return Optional.of(parse(text));
        } catch (IllegalArgumentException e) {
            log.debug("ignoring module snapshot: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private static ModuleSnapshot parse(String text) {
        String[] lines = text.split("\n");
        if (lines.length == 0 || !(MAGIC + "\t" + FORMAT_VERSION).equals(lines[0])) {
            throw new IllegalArgumentException("unknown format: " + (lines.length == 0 ? "" : lines[0]));
        }
        Optional<Path> module = Optional.empty();
        Optional<Path> repository = Optional.empty();
        Optional<String> key = Optional.empty();
        Optional<Path> reactorRoot = Optional.empty();
        List<PomFingerprint> fingerprints = new ArrayList<>();
        Set<String> reactorModules = new HashSet<>();
        Map<Path, PathStamp> stamps = new LinkedHashMap<>();
        for (int i = 1; i < lines.length; i++) {
            String[] fields = lines[i].split("\t", -1);
            switch (fields[0]) {
                case "module" -> module = Optional.of(Path.of(field(fields, 1, 2)));
                case "repository" -> repository = Optional.of(Path.of(field(fields, 1, 2)));
                case "key" -> key = Optional.of(field(fields, 1, 2));
                case "fingerprint" -> fingerprints.add(new PomFingerprint(Path.of(field(fields, 2, 3)),
                        field(fields, 1, 3)));
                case "reactor" -> reactorRoot = Optional.of(Path.of(field(fields, 1, 2)));
                case "reactor-module" -> reactorModules.add(field(fields, 1, 2));
                case "stamp" -> stamps.put(Path.of(field(fields, 2, 3)),
                        new PathStamp(field(fields, 1, 3), Optional.empty()));
                default -> throw new IllegalArgumentException("unknown line: " + fields[0]);
            }
        }
        PomKey pomKey = new PomKey(required(key, "key"), fingerprints);
        Reactor reactor = new Reactor(required(reactorRoot, "reactor"), reactorModules);
        return new ModuleSnapshot(required(module, "module"), required(repository, "repository"),
                new PomAnalysis(pomKey, reactor), stamps);
    }

    private static String field(String[] fields, int index, int expectedLength) {
        if (fields.length != expectedLength || fields[index].isEmpty()) {
            throw new IllegalArgumentException("malformed " + fields[0] + " line");
        }
        return fields[index];
    }

    private static <T> T required(Optional<T> value, String name) {
        return value.orElseThrow(() -> new IllegalArgumentException("missing " + name));
    }

    private static void line(StringBuilder text, List<String> values, String name, String... fields) {
        text.append(name);
        for (String field : fields) {
            values.add(field);
            text.append('\t').append(field);
        }
        text.append('\n');
    }
}
