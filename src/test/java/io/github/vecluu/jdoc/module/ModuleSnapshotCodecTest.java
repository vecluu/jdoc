package io.github.vecluu.jdoc.module;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ModuleSnapshotCodecTest {

    private final ModuleSnapshotCodec codec = new ModuleSnapshotCodec();

    @Test
    void roundTrips() {
        ModuleSnapshot snapshot = snapshot(Path.of("/work/app"));

        String text = codec.encode(snapshot).orElseThrow();

        assertThat(text).startsWith("jdoc-module-snapshot\t" + ModuleSnapshotCodec.FORMAT_VERSION + "\n");
        ModuleSnapshot decoded = codec.decode(text).orElseThrow();
        assertThat(decoded.moduleDirectory()).isEqualTo(snapshot.moduleDirectory());
        assertThat(decoded.localRepository()).isEqualTo(snapshot.localRepository());
        assertThat(decoded.analysis()).isEqualTo(snapshot.analysis());
        assertThat(decoded.stamps().keySet()).containsExactlyElementsOf(snapshot.stamps().keySet());
        assertThat(decoded.stamps().values()).extracting(PathStamp::value)
                .containsExactly("file:10:1:2:3:4", "absent", "dir");
    }

    @Test
    void pathsWithTabsOrLineBreaksAreNotStored() {
        assertThat(codec.encode(snapshot(Path.of("/work/a\tb")))).isEmpty();
        assertThat(codec.encode(snapshot(Path.of("/work/a\nb")))).isEmpty();
    }

    @Test
    void otherVersionsAndMalformedTextAreIgnored() {
        String text = codec.encode(snapshot(Path.of("/work/app"))).orElseThrow();

        assertThat(codec.decode(text.replace("snapshot\t" + ModuleSnapshotCodec.FORMAT_VERSION, "snapshot\t0")))
                .isEmpty();
        assertThat(codec.decode(text.replace("key\t", "kee\t"))).isEmpty();
        assertThat(codec.decode(text.lines().filter(line -> !line.startsWith("reactor\t"))
                .reduce("", (a, b) -> a + b + "\n"))).isEmpty();
        assertThat(codec.decode(text + "stamp\tabsent\n")).isEmpty();
        assertThat(codec.decode("")).isEmpty();
    }

    private static ModuleSnapshot snapshot(Path module) {
        PomKey key = new PomKey("ab12", List.of(new PomFingerprint(module.resolve("pom.xml"), "cd34"),
                new PomFingerprint(Path.of("/repo/p.pom"), "mtime:absent")));
        Reactor reactor = new Reactor(module.getParent(), Set.of("g:app", "g:lib"));
        Map<Path, PathStamp> stamps = new LinkedHashMap<>();
        stamps.put(module.resolve("pom.xml"), new PathStamp("file:10:1:2:3:4", Optional.empty()));
        stamps.put(module.resolve("../pom.xml").normalize(), PathStamp.ABSENT);
        stamps.put(module.getParent().resolve("lib"), PathStamp.DIRECTORY);
        return new ModuleSnapshot(module, Path.of("/repo"), new PomAnalysis(key, reactor), stamps);
    }
}
