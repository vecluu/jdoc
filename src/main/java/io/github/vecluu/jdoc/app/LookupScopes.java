package io.github.vecluu.jdoc.app;

import io.github.vecluu.jdoc.indexer.IndexedClasspath;
import io.github.vecluu.jdoc.lookup.LookupScope;
import io.github.vecluu.jdoc.lookup.ScopedArtifact;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the {@link LookupScope} from the indexer's {@link IndexedClasspath}. The indexer lists the JDK last; the
 * scope puts it first, as javac does: a library declaring a type with a JDK binary name never shadows the JDK's.
 */
public final class LookupScopes {

    private LookupScopes() {
    }

    /** The scope: JDK first, then the module's artifacts in classpath order. */
    public static LookupScope from(IndexedClasspath classpath) {
        List<ScopedArtifact> artifacts = new ArrayList<>();
        classpath.jdkArtifactId().ifPresent(id -> artifacts.add(scoped(classpath, id)));
        for (long id : classpath.artifactIds()) {
            if (classpath.jdkArtifactId().filter(jdk -> jdk == id).isEmpty()) {
                artifacts.add(scoped(classpath, id));
            }
        }
        return new LookupScope(artifacts, classpath.status());
    }

    private static ScopedArtifact scoped(IndexedClasspath classpath, long id) {
        return new ScopedArtifact(id, classpath.key(id)
                .orElseThrow(() -> new IllegalArgumentException("no artifact key for id " + id)));
    }
}
