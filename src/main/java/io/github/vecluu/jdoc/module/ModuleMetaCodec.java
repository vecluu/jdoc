package io.github.vecluu.jdoc.module;

import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/**
 * Encodes {@link ModuleMeta} as a flat JSON object:
 *
 * <pre>
 * {
 *   "formatVersion": 1,
 *   "modulePath": "/work/app",
 *   "pomKey": "ab12...",
 *   "pomHashes": "&lt;sha256&gt;\t/work/app/pom.xml\n&lt;sha256&gt;\t/work/pom.xml",
 *   "resolvedAt": "2026-11-01T10:00:00Z",
 *   "resolvedAtMillis": 1793527200000
 * }
 * </pre>
 */
@Slf4j
public final class ModuleMetaCodec {

    private static final long FORMAT_VERSION = 1;

    /** Encodes the meta as JSON text. */
    public String encode(ModuleMeta meta) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("formatVersion", FORMAT_VERSION);
        fields.put("modulePath", meta.moduleDirectory().toString());
        fields.put("pomKey", meta.pomKey());
        fields.put("pomHashes", meta.fingerprints().stream()
                .map(fingerprint -> fingerprint.fingerprint() + "\t" + fingerprint.file())
                .collect(Collectors.joining("\n")));
        fields.put("resolvedAt", meta.resolvedAt().toString());
        fields.put("resolvedAtMillis", meta.resolvedAt().toEpochMilli());
        return FlatJson.write(fields);
    }

    /** Decodes meta JSON; empty (logged at debug) if the text is malformed or misses required fields. */
    public Optional<ModuleMeta> decode(String json) {
        try {
            Map<String, Object> fields = FlatJson.read(json);
            Path modulePath = Path.of(string(fields, "modulePath"));
            String pomKey = string(fields, "pomKey");
            Instant resolvedAt = Instant.parse(string(fields, "resolvedAt"));
            List<PomFingerprint> fingerprints = new ArrayList<>();
            Object hashes = fields.get("pomHashes");
            if (hashes instanceof String text && !text.isEmpty()) {
                for (String line : text.split("\n")) {
                    int tab = line.indexOf('\t');
                    if (tab > 0) {
                        fingerprints.add(new PomFingerprint(Path.of(line.substring(tab + 1)), line.substring(0, tab)));
                    }
                }
            }
            return Optional.of(new ModuleMeta(modulePath, pomKey, fingerprints, resolvedAt));
        } catch (IllegalArgumentException | DateTimeException e) {
            log.debug("ignoring malformed module meta: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private static String string(Map<String, Object> fields, String name) {
        if (fields.get(name) instanceof String value && !value.isEmpty()) {
            return value;
        }
        throw new IllegalArgumentException("missing field " + name);
    }
}
