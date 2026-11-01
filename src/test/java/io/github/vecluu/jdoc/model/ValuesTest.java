package io.github.vecluu.jdoc.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ValuesTest {

    @Test
    void sourceRange() {
        SourceRange range = new SourceRange(2, 5);
        assertThat(range.length()).isEqualTo(3);
        assertThat(range.slice("abcdefg")).isEqualTo("cde");
        assertThat(range.contains(new SourceRange(2, 5))).isTrue();
        assertThat(range.contains(new SourceRange(1, 3))).isFalse();
        assertThat(new SourceRange(4, 4).slice("abcd")).isEmpty();
        assertThatThrownBy(() -> range.slice("abc")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SourceRange(-1, 2)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SourceRange(3, 2)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void scopeParsesMavenText() {
        assertThat(Scope.parse("compile")).contains(Scope.COMPILE);
        assertThat(Scope.parse(" TEST ")).contains(Scope.TEST);
        assertThat(Scope.parse("import")).isEmpty();
        assertThat(Scope.parse(null)).isEmpty();
        assertThat(Scope.PROVIDED.mavenName()).isEqualTo("provided");
    }

    @Test
    void enums() {
        assertThat(TypeKind.ANNOTATION.keyword()).isEqualTo("@interface");
        assertThat(TypeKind.RECORD.keyword()).isEqualTo("record");
        assertThat(Visibility.PUBLIC.isApi()).isTrue();
        assertThat(Visibility.PROTECTED.isApi()).isTrue();
        assertThat(Visibility.PACKAGE.isApi()).isFalse();
        assertThat(Visibility.PRIVATE.isApi()).isFalse();
        assertThat(Visibility.PACKAGE.keyword()).isEmpty();
        assertThat(MemberKind.CONSTRUCTOR.isExecutable()).isTrue();
        assertThat(MemberKind.ENUM_CONSTANT.isExecutable()).isFalse();
        assertThat(DocSource.SOURCES.headerTag()).isEmpty();
        assertThat(DocSource.JDK.headerTag()).isEmpty();
        assertThat(DocSource.JAVADOC.headerTag()).contains("[from javadoc jar]");
        assertThat(DocSource.DECOMPILED.headerTag()).contains("[decompiled, no docs]");
        assertThat(DocSource.JDK.isSourceText()).isTrue();
        assertThat(DocSource.JAVADOC.isSourceText()).isFalse();
    }

    @Test
    void resolvedDependencyRequiresAbsoluteJar() {
        Gav gav = Gav.of("g", "a", "1");
        ResolvedDependency dep = new ResolvedDependency(gav, Scope.COMPILE, Path.of("/repo/g/../g/a-1.jar"));
        assertThat(dep.jar()).isEqualTo(Path.of("/repo/g/a-1.jar"));
        assertThat(dep.artifactKey()).isEqualTo(ArtifactKey.maven(gav));
        assertThatThrownBy(() -> new ResolvedDependency(gav, Scope.COMPILE, Path.of("a-1.jar")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void moduleClasspathCopiesLists() {
        List<String> warnings = new ArrayList<>(List.of("w"));
        ModuleClasspath classpath = new ModuleClasspath(Path.of("/m"), "abc", null, ResolutionStatus.STALE, warnings);
        warnings.add("x");
        assertThat(classpath.warnings()).containsExactly("w");
        assertThat(classpath.dependencies()).isEmpty();
        assertThatThrownBy(() -> new ModuleClasspath(Path.of("/m"), " ", List.of(), ResolutionStatus.RESOLVED, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void renderedDoc() {
        assertThat(RenderedDoc.missing().inheritsDoc()).isTrue();
        assertThat(RenderedDoc.missing().isEmpty()).isTrue();
        RenderedDoc doc = new RenderedDoc("Text.", "Text.", List.of("@since 1"), false);
        assertThat(doc.isEmpty()).isFalse();
        assertThat(new RenderedDoc(null, null, null, false).description()).isEmpty();
        assertThat(new RenderedDoc("", "", List.of("@return x"), false).isEmpty()).isFalse();
    }

    @Test
    void indexedArtifactDefaults() {
        IndexedArtifact artifact = IndexedArtifact.builder()
                .id(1)
                .key(ArtifactKey.jdk(25))
                .jarPath(Path.of("/jdk/lib/src.zip"))
                .indexedAt(Instant.EPOCH)
                .build();
        assertThat(artifact.sourcesJar()).isEmpty();
        assertThat(artifact.javadocJar()).isEmpty();
        assertThatThrownBy(() -> artifact.toBuilder().key(null).build()).isInstanceOf(NullPointerException.class);
    }
}
