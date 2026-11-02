/**
 * Module and version resolution: finds the Maven module enclosing a path, computes its POM key, runs
 * {@code mvn dependency:list} on a cache miss, parses {@code deps.txt}, keeps per-module meta files, drops the
 * project's own reactor modules and falls back to the last good list (STALE) or to the newest versions in the local
 * repository (UNRESOLVED). The entry point is {@link io.github.vecluu.jdoc.module.ClasspathResolver}.
 *
 * <p>Package-wide rules:
 * <ul>
 *   <li>{@code deps.txt} means "Maven succeeded for this key". It is only published by an atomic rename of a temp
 *       file, and a failed run leaves nothing, so the next call retries Maven. Never write it directly.</li>
 *   <li>A cache hit trusts {@code deps.txt}: a jar deleted from the local repository is dropped by the parser, not
 *       re-resolved. {@code settings.xml}, BOM imports and non-SNAPSHOT repository parents are not part of the POM
 *       key.</li>
 *   <li>The POM key includes the module path, so two checkouts never share a cache directory. Old key directories
 *       are never pruned: they feed the STALE fallback, which matches {@code meta.json} by the module's real path
 *       (so {@link io.github.vecluu.jdoc.module.ModuleLocator} must keep returning real paths).</li>
 *   <li>Maven's output never reaches stdout: it is captured, and warnings travel only in
 *       {@code ModuleClasspath.warnings()}.</li>
 * </ul>
 */
package io.github.vecluu.jdoc.module;
