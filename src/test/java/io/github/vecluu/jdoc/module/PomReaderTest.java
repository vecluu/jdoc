package io.github.vecluu.jdoc.module;

import static io.github.vecluu.jdoc.module.PomFixtures.artifact;
import static io.github.vecluu.jdoc.module.PomFixtures.dependencies;
import static io.github.vecluu.jdoc.module.PomFixtures.dependency;
import static io.github.vecluu.jdoc.module.PomFixtures.gav;
import static io.github.vecluu.jdoc.module.PomFixtures.modules;
import static io.github.vecluu.jdoc.module.PomFixtures.parent;
import static io.github.vecluu.jdoc.module.PomFixtures.pom;
import static io.github.vecluu.jdoc.module.PomFixtures.writeFile;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.vecluu.jdoc.error.ResolutionException;
import io.github.vecluu.jdoc.model.Gav;
import io.github.vecluu.jdoc.model.Scope;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PomReaderTest {

    @TempDir
    Path tmp;

    private final PomReader reader = new PomReader();

    @Test
    void readsOwnCoordinates() {
        PomModel model = reader.read(pom(tmp, gav("com.example", "app", "1.2.3")));

        assertThat(model.coordinates()).contains(Gav.of("com.example", "app", "1.2.3"));
        assertThat(model.parent()).isEmpty();
        assertThat(model.file()).isEqualTo(tmp.resolve("pom.xml"));
        assertThat(model.directory()).isEqualTo(tmp);
    }

    @Test
    void inheritsGroupAndVersionFromParent() {
        PomModel model = reader.read(pom(tmp, parent("com.example", "parent", "2.0"), artifact("child")));

        assertThat(model.coordinates()).contains(Gav.of("com.example", "child", "2.0"));
        assertThat(model.parent()).map(ParentReference::gav).contains(Gav.of("com.example", "parent", "2.0"));
    }

    @Test
    void ownGroupOverridesParent() {
        PomModel model = reader.read(pom(tmp, parent("com.example", "parent", "2.0"),
                "<groupId>org.other</groupId>", artifact("child")));

        assertThat(model.coordinates()).contains(Gav.of("org.other", "child", "2.0"));
    }

    @Test
    void relativePathDefaultsToParentDirectoryPom() {
        PomModel model = reader.read(pom(tmp.resolve("child"), parent("g", "p", "1"), artifact("c")));

        assertThat(model.parent().orElseThrow().relativePath()).contains("../pom.xml");
        assertThat(model.parent().orElseThrow().localPomCandidate(model.directory())).contains(tmp.resolve("pom.xml"));
    }

    @Test
    void emptyRelativePathMeansNoLocalParent() {
        PomModel model = reader.read(pom(tmp, parent("g", "p", "1", "<relativePath/>"), artifact("c")));

        assertThat(model.parent().orElseThrow().relativePath()).isEmpty();
        assertThat(model.parent().orElseThrow().localPomCandidate(tmp)).isEmpty();
    }

    @Test
    void explicitRelativePathToDirectoryResolvesItsPom() throws Exception {
        Files.createDirectories(tmp.resolve("parent"));
        PomModel model = reader.read(pom(tmp.resolve("child"),
                parent("g", "p", "1", "<relativePath>../parent</relativePath>"), artifact("c")));

        assertThat(model.parent().orElseThrow().localPomCandidate(model.directory()))
                .contains(tmp.resolve("parent/pom.xml"));
    }

    @Test
    void explicitRelativePathToFile() {
        PomModel model = reader.read(pom(tmp.resolve("child"),
                parent("g", "p", "1", "<relativePath>../build/parent-pom.xml</relativePath>"), artifact("c")));

        assertThat(model.parent().orElseThrow().localPomCandidate(model.directory()))
                .contains(tmp.resolve("build/parent-pom.xml"));
    }

    @Test
    void readsModulesIncludingProfilesAndSubprojects() {
        PomModel model = reader.read(pom(tmp, gav("g", "root", "1"), modules("a", "b/c"),
                "<subprojects><subproject>d</subproject></subprojects>",
                "<profiles><profile><id>x</id>" + modules("e") + "</profile></profiles>"));

        assertThat(model.modules()).containsExactly("a", "b/c", "d", "e");
    }

    @Test
    void readsDirectDependenciesOnly() {
        PomModel model = reader.read(pom(tmp, gav("g", "app", "1"),
                "<dependencyManagement>" + dependencies(dependency("managed", "only")) + "</dependencyManagement>",
                dependencies(
                        dependency("org.slf4j", "slf4j-api"),
                        dependency("org.junit", "junit", "<scope>test</scope>"),
                        dependency("com.x", "bom", "<type>pom</type><scope>import</scope>"),
                        dependency("com.x", "native", "<classifier>linux</classifier>"),
                        dependency("${project.groupId}", "sibling"),
                        "<dependency><artifactId>no-group</artifactId></dependency>")));

        assertThat(model.dependencies()).extracting(DeclaredDependency::coordinates)
                .containsExactly("org.slf4j:slf4j-api", "org.junit:junit", "com.x:bom", "com.x:native", "g:sibling");
        assertThat(model.dependencies().get(1).scope()).isEqualTo(Scope.TEST);
        assertThat(model.dependencies().get(2).isJar()).isFalse();
        assertThat(model.dependencies().get(2).scope()).isEqualTo(Scope.COMPILE);
        assertThat(model.dependencies().get(3).classifier()).contains("linux");
    }

    @Test
    void interpolatesOwnProperties() {
        PomModel model = reader.read(pom(tmp, "<groupId>g</groupId>", artifact("app"),
                "<version>${revision}</version>",
                "<properties><revision>1.5</revision><lib.group>org.lib</lib.group></properties>",
                dependencies(dependency("${lib.group}", "lib"), dependency("${unknown}", "x"))));

        assertThat(model.coordinates()).contains(Gav.of("g", "app", "1.5"));
        assertThat(model.dependencies().get(0).groupId()).isEqualTo("org.lib");
        assertThat(model.dependencies().get(1).hasUnresolvedProperty()).isTrue();
    }

    @Test
    void worksWithoutNamespace() {
        Path file = writeFile(tmp.resolve("pom.xml"),
                "<project><groupId>g</groupId><artifactId>a</artifactId><version>1</version>"
                        + "<modules><module>m</module></modules></project>");

        PomModel model = reader.read(file);

        assertThat(model.coordinates()).contains(Gav.of("g", "a", "1"));
        assertThat(model.modules()).containsExactly("m");
    }

    @Test
    void missingVersionGivesNoCoordinates() {
        PomModel model = reader.read(pom(tmp, "<groupId>g</groupId>", artifact("a")));

        assertThat(model.coordinates()).isEqualTo(Optional.empty());
    }

    @Test
    void rejectsMalformedXml() {
        Path file = writeFile(tmp.resolve("pom.xml"), "<project><groupId>g</project>");

        assertThatThrownBy(() -> reader.read(file)).isInstanceOf(ResolutionException.class)
                .hasMessageContaining("malformed POM");
    }

    @Test
    void rejectsNonPomRoot() {
        Path file = writeFile(tmp.resolve("pom.xml"), "<settings/>");

        assertThatThrownBy(() -> reader.read(file)).isInstanceOf(ResolutionException.class)
                .hasMessageContaining("not a POM");
    }

    @Test
    void rejectsDoctypeAndExternalEntities() {
        Path secret = writeFile(tmp.resolve("secret.txt"), "secret");
        Path file = writeFile(tmp.resolve("pom.xml"), """
                <?xml version="1.0"?>
                <!DOCTYPE project [<!ENTITY x SYSTEM "%s">]>
                <project><groupId>&x;</groupId><artifactId>a</artifactId><version>1</version></project>
                """.formatted(secret.toUri()));

        assertThatThrownBy(() -> reader.read(file)).isInstanceOf(ResolutionException.class);
    }

    @Test
    void missingFileIsResolutionException() {
        assertThatThrownBy(() -> reader.read(tmp.resolve("absent.xml"))).isInstanceOf(ResolutionException.class)
                .hasMessageContaining("cannot read");
    }
}
