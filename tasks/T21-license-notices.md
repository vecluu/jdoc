# T21 — Licence, NOTICE and third-party notices in jar and tarball

**Owns:** root `LICENSE` (read-only), `NOTICE`, `THIRD-PARTY-NOTICES` (new), `pom.xml` (shade transformers and
filters, project metadata and, if chosen, a dependency-list execution), and one new IT in the test tree (for example
`src/test/java/.../LicenseNoticesIT.java`). This task may edit `pom.xml`, as an exception to the agent guide.
**Depends on:** T14 (picocli removed from the pom), T16 (final dependency set). It touches no Java main code.
**Spec:** `docs/LICENSE-COMPLIANCE.md` (findings table, required contents, recommended shade approach). Read it first.

## Problem
Today the shaded jar's `META-INF/LICENSE.txt` is slf4j's MIT licence. jdoc's own LICENSE and NOTICE are absent,
ASM's BSD-3 text and sqlite-jdbc's ISC/BSD notices are missing, and the release bundle has no licence files at all.

## Goal
- The jar contains `META-INF/LICENSE`, `META-INF/NOTICE` and `META-INF/THIRD-PARTY-NOTICES`, identical to the root
  files. No dependency's licence or notice sits at the `META-INF/` root.
- `THIRD-PARTY-NOTICES` covers every shipped component in the compliance doc's table: sqlite-jdbc including SQLite
  and the Crawshaw ISC/BSD parts, jsoup, ASM, Vineflower, slf4j-api and slf4j-simple. It also covers the nested
  Vineflower plugin contents if they stay. Each entry carries the version, SPDX id, copyright lines and the full
  text for every non-Apache licence.
- `NOTICE` gains only the legally carried-forward sqlite-jdbc attribution (David Crawshaw), nothing else.
- `pom.xml` gets `<url>`, `<inceptionYear>`, `<licenses>`, `<developers>` and `<scm>` for github.com/vecluu/jdoc.

## Scope
1. **Shade:** add the licence-dropping transformer, exclude dependency `META-INF/NOTICE*`, and include the three
   root files under `META-INF/`, as described in the compliance doc. Keep the existing transformers and filters.
2. **Vineflower plugins:** try excluding `META-INF/plugins/**` from the Vineflower artifact. Keep the exclusion
   only if every decompiler test and IT (T06's golden outputs) is unchanged. Record the decision and the jar size
   before and after in your notes. If the plugins stay, document JetBrains annotations, the Kotlin metadata and
   the Kotlin protobuf fork (BSD-3, Google) in `THIRD-PARTY-NOTICES`.
3. **Write `THIRD-PARTY-NOTICES`** by hand from the actual jars in `~/.m2` and the compliance doc. Re-verify the
   copyright lines against the jar or source contents of the exact versions in the pom.
4. **Coverage guard:** a failsafe IT on the built shaded jar that fails when:
   - the three META-INF files are missing or differ from the root files;
   - another licence or notice file appears at the `META-INF/` root;
   - a top-level package prefix or nested jar has no entry in an allow-list mapped to `THIRD-PARTY-NOTICES`;
   - a resolved runtime `groupId:artifactId:version` is not mentioned in `THIRD-PARTY-NOTICES`.

   For the last point, write the runtime dependency list into the build dir during the build and pass its path
   to the IT; failsafe already runs after `package`. A version bump must fail the build until the entry is
   reviewed.

## Coordination
- **T18 (`scripts/assemble.sh`, workflow):** the bundle must contain `LICENSE`, `NOTICE` and
  `THIRD-PARTY-NOTICES` at its root, and its install check should assert they are present. If T18 is already done
  when T21 runs, T21 makes this small change to `assemble.sh` and lists it under "Changes outside my packages".
  Otherwise, add it to T18's brief.
- **T17 (`dist/install.sh`):** optionally copies the three files into the install prefix (for example
  `share/doc/jdoc/`). This is not required for compliance. If T17 is finished, leave it as a follow-up note
  rather than editing the installer.
- **Ordering:** T21 can run alongside T17/T19 (wave 8): it touches only `pom.xml`, the root licence files and one
  IT, and none of T17's or T19's files. It must not run alongside T14 or T16, which may edit `pom.xml`. Changing
  jar contents invalidates any trained AOT cache, which T17's fingerprint handles.
- Do not add per-file source licence headers in this task (see the compliance doc; optional, separate task).

## Tests
- The new IT passes. Make it fail on purpose once (for example, temporarily remove a THIRD-PARTY-NOTICES entry)
  and record that it did fail.
- `./mvnw -B -q -Djdoc.buildDir=target/T21 verify` passes. Inspect the jar's `META-INF/` listing and paste it
  into your notes.
