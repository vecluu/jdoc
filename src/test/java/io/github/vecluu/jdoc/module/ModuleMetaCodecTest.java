package io.github.vecluu.jdoc.module;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModuleMetaCodecTest {

    private final ModuleMetaCodec codec = new ModuleMetaCodec();

    @Test
    void roundTrips() {
        ModuleMeta meta = new ModuleMeta(Path.of("/work/my app"), "ab12", List.of(
                new PomFingerprint(Path.of("/work/my app/pom.xml"), "aaa"),
                new PomFingerprint(Path.of("/work/pom.xml"), "bbb"),
                new PomFingerprint(Path.of("/m2/p-1-SNAPSHOT.pom"), "mtime:123")),
                Instant.parse("2026-11-01T10:15:30.123Z"));

        String json = codec.encode(meta);

        assertThat(json).contains("\"modulePath\": \"/work/my app\"").contains("\"resolvedAtMillis\": ");
        assertThat(codec.decode(json)).contains(meta);
    }

    @Test
    void roundTripsWithoutFingerprints() {
        ModuleMeta meta = new ModuleMeta(Path.of("/w"), "k", List.of(), Instant.EPOCH);

        assertThat(codec.decode(codec.encode(meta))).contains(meta);
    }

    @Test
    void malformedOrIncompleteMetaIsEmpty() {
        assertThat(codec.decode("not json")).isEmpty();
        assertThat(codec.decode("{\"pomKey\":\"k\",\"resolvedAt\":\"2026-01-01T00:00:00Z\"}")).isEmpty();
        assertThat(codec.decode("{\"modulePath\":\"/w\",\"pomKey\":\"k\",\"resolvedAt\":\"yesterday\"}")).isEmpty();
    }

    @Test
    void ignoresUnknownFields() {
        assertThat(codec.decode("{\"modulePath\":\"/w\",\"pomKey\":\"k\",\"resolvedAt\":\"2026-01-01T00:00:00Z\","
                + "\"future\":7}")).isPresent();
    }
}
