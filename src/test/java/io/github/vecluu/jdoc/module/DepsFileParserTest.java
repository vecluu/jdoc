package io.github.vecluu.jdoc.module;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.vecluu.jdoc.model.Gav;
import io.github.vecluu.jdoc.model.ResolvedDependency;
import io.github.vecluu.jdoc.model.Scope;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DepsFileParserTest {

    private final DepsFileParser parser = new DepsFileParser(path -> true);

    @Test
    void parsesPlugin3OutputWithHeader() {
        String content = """

                The following files have been resolved:
                   org.apache.commons:commons-lang3:jar:3.17.0:compile:/home/u/.m2/repository/org/apache/commons/commons-lang3/3.17.0/commons-lang3-3.17.0.jar
                   org.junit.jupiter:junit-jupiter-api:jar:5.11.0:test:/home/u/.m2/repository/org/junit/jupiter/junit-jupiter-api/5.11.0/junit-jupiter-api-5.11.0.jar
                """;

        List<ResolvedDependency> deps = parser.parse(content);

        assertThat(deps).hasSize(2);
        assertThat(deps.get(0).gav()).isEqualTo(Gav.of("org.apache.commons", "commons-lang3", "3.17.0"));
        assertThat(deps.get(0).scope()).isEqualTo(Scope.COMPILE);
        assertThat(deps.get(0).jar()).isEqualTo(Path.of(
                "/home/u/.m2/repository/org/apache/commons/commons-lang3/3.17.0/commons-lang3-3.17.0.jar"));
        assertThat(deps.get(1).scope()).isEqualTo(Scope.TEST);
    }

    @Test
    void stripsModuleSuffixesAndOptionalMarker() {
        String content = """
                   org.slf4j:slf4j-api:jar:2.0.16:compile:/r/slf4j-api-2.0.16.jar -- module org.slf4j
                   com.google.guava:guava:jar:33.0.0-jre:compile:/r/guava.jar -- module com.google.common [auto]
                   javax.inject:javax.inject:jar:1:provided:/r/javax.inject-1.jar -- module javax.inject (auto)
                   org.foo:opt:jar:1.0:runtime:/r/opt-1.0.jar (optional) -- module opt (auto)
                   org.foo:opt2:jar:1.0:runtime:/r/opt2-1.0.jar (optional)
                """;

        List<ResolvedDependency> deps = parser.parse(content);

        assertThat(deps).extracting(dep -> dep.jar().toString())
                .containsExactly("/r/slf4j-api-2.0.16.jar", "/r/guava.jar", "/r/javax.inject-1.jar",
                        "/r/opt-1.0.jar", "/r/opt2-1.0.jar");
        assertThat(deps).extracting(ResolvedDependency::scope)
                .containsExactly(Scope.COMPILE, Scope.COMPILE, Scope.PROVIDED, Scope.RUNTIME, Scope.RUNTIME);
    }

    @Test
    void parsesClassifier() {
        Optional<ResolvedDependency> dep = parser.parseLine(
                "   io.netty:netty-transport-native-epoll:jar:linux-x86_64:4.1.100.Final:runtime:/r/epoll.jar");

        assertThat(dep).hasValueSatisfying(d -> {
            assertThat(d.gav().classifier()).contains("linux-x86_64");
            assertThat(d.gav().version()).isEqualTo("4.1.100.Final");
            assertThat(d.scope()).isEqualTo(Scope.RUNTIME);
        });
    }

    @Test
    void keepsTestJarAndSystemScope() {
        assertThat(parser.parseLine("g:a:test-jar:tests:1.0:test:/r/a-1.0-tests.jar")).isPresent();
        assertThat(parser.parseLine("g:a:jar:1.0:system:/opt/lib/a.jar")).isPresent();
    }

    @Test
    void pathsWithSpacesAndColons() {
        assertThat(parser.parseLine("  g:a:jar:1.0:compile:/home/my user/repo:x/a-1.0.jar  "))
                .map(ResolvedDependency::jar).contains(Path.of("/home/my user/repo:x/a-1.0.jar"));
    }

    @Test
    void skipsNoiseAndNonJarTypes() {
        String content = """
                The following files have been resolved:
                   none

                   g:parent:pom:1.0:compile:/r/parent-1.0.pom
                   g:web:war:1.0:compile:/r/web-1.0.war
                   g:noabs:jar:1.0:compile
                   g:rel:jar:1.0:compile:relative/path.jar
                   g:badscope:jar:1.0:weird:/r/x.jar
                   too:few:/r/x.jar
                [INFO] something
                """;

        assertThat(parser.parse(content)).isEmpty();
    }

    @Test
    void skipsEntriesWhoseJarIsMissing() {
        DepsFileParser checking = new DepsFileParser(Set.of(Path.of("/r/present.jar"))::contains);

        List<ResolvedDependency> deps = checking.parse("""
                   g:present:jar:1:compile:/r/present.jar
                   g:absent:jar:1:compile:/r/absent.jar
                """);

        assertThat(deps).extracting(dep -> dep.gav().artifactId()).containsExactly("present");
    }

    @Test
    void emptyContent() {
        assertThat(parser.parse("")).isEmpty();
    }

    @Test
    void readsOutputOfTestFixtures() {
        ResolvedDependency dep = new ResolvedDependency(
                new Gav("g", "a", "1", Optional.of("cls"), "jar"), Scope.PROVIDED, Path.of("/r/a-1-cls.jar"));

        assertThat(parser.parse(io.github.vecluu.jdoc.testsupport.FakeMavenRepo.depsTxt(List.of(dep))))
                .containsExactly(dep);
    }

    @Test
    void parsesRealMaven3Plugin37OutputIncludingReactorDirectories() throws Exception {
        String content;
        try (var in = getClass().getResourceAsStream("real-dependency-list.txt")) {
            content = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }

        List<ResolvedDependency> deps = new DepsFileParser(path -> path.toString().endsWith(".jar")).parse(content);

        assertThat(deps).hasSize(19);
        assertThat(deps.get(0).gav()).isEqualTo(Gav.of("org.xerial", "sqlite-jdbc", "3.54.1.0"));
        assertThat(deps.get(3).gav().artifactId()).isEqualTo("vineflower");
        assertThat(deps).extracting(dep -> dep.scope()).contains(Scope.COMPILE, Scope.RUNTIME, Scope.PROVIDED,
                Scope.TEST);
        assertThat(deps).noneMatch(dep -> dep.gav().groupId().equals("demo.reactor"));
    }
}
