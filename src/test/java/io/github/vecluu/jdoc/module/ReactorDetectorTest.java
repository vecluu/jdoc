package io.github.vecluu.jdoc.module;

import static io.github.vecluu.jdoc.module.PomFixtures.artifact;
import static io.github.vecluu.jdoc.module.PomFixtures.gav;
import static io.github.vecluu.jdoc.module.PomFixtures.modules;
import static io.github.vecluu.jdoc.module.PomFixtures.parent;
import static io.github.vecluu.jdoc.module.PomFixtures.pom;
import static io.github.vecluu.jdoc.module.PomFixtures.writeFile;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.vecluu.jdoc.model.Gav;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReactorDetectorTest {

    @TempDir
    Path tmp;

    private final ReactorDetector detector = new ReactorDetector(new PomHierarchy(new PomReader()));

    @Test
    void standaloneModuleIsItsOwnReactor() {
        pom(tmp, gav("g", "app", "1"));

        Reactor reactor = detector.detect(tmp);

        assertThat(reactor.rootDirectory()).isEqualTo(tmp);
        assertThat(reactor.moduleCoordinates()).containsExactly("g:app");
    }

    @Test
    void collectsSiblingsAndNestedModules() {
        pom(tmp, gav("g", "root", "1"), modules("core", "services"));
        pom(tmp.resolve("core"), parent("g", "root", "1"), artifact("core"));
        pom(tmp.resolve("services"), parent("g", "root", "1"), artifact("services"), modules("api", "impl"));
        pom(tmp.resolve("services/api"), parent("g", "services", "1"), artifact("api"));
        pom(tmp.resolve("services/impl"), parent("g", "services", "1"), artifact("impl"));

        Reactor reactor = detector.detect(tmp.resolve("services/impl"));

        assertThat(reactor.rootDirectory()).isEqualTo(tmp);
        assertThat(reactor.moduleCoordinates())
                .containsExactlyInAnyOrder("g:root", "g:core", "g:services", "g:api", "g:impl");
        assertThat(reactor.contains(Gav.of("g", "core", "0.9"))).isTrue();
        assertThat(reactor.contains(Gav.of("other", "core", "1"))).isFalse();
    }

    @Test
    void aggregatorWithoutInheritanceAndSeparateParentDirectory() {
        pom(tmp, gav("g", "aggregator", "1"), modules("parent", "app", "lib"));
        pom(tmp.resolve("parent"), gav("g", "parent", "1"));
        pom(tmp.resolve("app"), parent("g", "parent", "1", "<relativePath>../parent</relativePath>"),
                artifact("app"));
        pom(tmp.resolve("lib"), "<groupId>org.lib</groupId>", artifact("lib"), "<version>2</version>");

        Reactor reactor = detector.detect(tmp.resolve("app"));

        assertThat(reactor.rootDirectory()).isEqualTo(tmp);
        assertThat(reactor.moduleCoordinates())
                .containsExactlyInAnyOrder("g:aggregator", "g:parent", "g:app", "org.lib:lib");
    }

    @Test
    void moduleEntryMayNameAPomFile() {
        pom(tmp, gav("g", "root", "1"), modules("alt/build.xml"));
        writeFile(tmp.resolve("alt/build.xml"), "<project>" + gav("g", "alt", "1") + "</project>");

        assertThat(detector.detect(tmp).moduleCoordinates()).containsExactlyInAnyOrder("g:root", "g:alt");
    }

    @Test
    void parentDirectoryPomThatDoesNotListModuleIsNotTheReactor() {
        pom(tmp, gav("g", "unrelated", "1"), modules("other"));
        pom(tmp.resolve("app"), gav("g", "app", "1"));

        Reactor reactor = detector.detect(tmp.resolve("app"));

        assertThat(reactor.rootDirectory()).isEqualTo(tmp.resolve("app"));
        assertThat(reactor.moduleCoordinates()).containsExactly("g:app");
    }

    @Test
    void toleratesMissingAndBrokenModulesAndCycles() {
        pom(tmp, gav("g", "root", "1"), modules("missing", "broken", "ok", "."));
        writeFile(tmp.resolve("broken/pom.xml"), "<project>");
        pom(tmp.resolve("ok"), parent("g", "root", "1"), artifact("ok"), modules(".."));

        assertThat(detector.detect(tmp.resolve("ok")).moduleCoordinates())
                .containsExactlyInAnyOrder("g:root", "g:ok");
    }

    @Test
    void unreadableModulePomGivesEmptyReactor() {
        writeFile(tmp.resolve("pom.xml"), "garbage");

        Reactor reactor = detector.detect(tmp);

        assertThat(reactor.rootDirectory()).isEqualTo(tmp);
        assertThat(reactor.moduleCoordinates()).isEmpty();
    }
}
