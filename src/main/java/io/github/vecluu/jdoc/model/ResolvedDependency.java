package io.github.vecluu.jdoc.model;

import java.nio.file.Path;

/**
 * One entry of a module's resolved classpath.
 *
 * @param gav   coordinates as resolved by Maven
 * @param scope dependency scope
 * @param jar   absolute, normalized path of the artifact file
 */
public record ResolvedDependency(Gav gav, Scope scope, Path jar) {
    public ResolvedDependency {
        Checks.notNull(gav, "gav");
        Checks.notNull(scope, "scope");
        Checks.notNull(jar, "jar");
        if (!jar.isAbsolute()) {
            throw new IllegalArgumentException("jar path must be absolute: " + jar);
        }
        jar = jar.normalize();
    }

    /** The artifact key of this dependency. */
    public ArtifactKey artifactKey() {
        return ArtifactKey.maven(gav);
    }
}
