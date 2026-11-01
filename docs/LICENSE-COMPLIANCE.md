# License compliance for shipped artifacts

Status: research for T21, checked 2026-11-01 against `pom.xml` at that date (picocli already being removed by T14,
so it is ignored here). jdoc itself is Apache-2.0 (`LICENSE`, `NOTICE` at the repo root).

## What is shipped

`./mvnw dependency:list -DincludeScope=runtime` resolves exactly six artifacts and no transitive ones:
sqlite-jdbc 3.54.1.0, jsoup 1.23.2, asm 9.11.1, vineflower 1.12.0, slf4j-api 2.0.17, slf4j-simple 2.0.17.
The shaded `jdoc.jar` (about 15 MB) contains only these plus jdoc's own classes. Top-level packages in the jar:
`io/github/vecluu`, `org/sqlite` (with native libraries for 20 OS/arch combinations), `org/jsoup`,
`org/objectweb/asm`, `org/jetbrains/java/decompiler` and `net/fabricmc/fernflower` (Vineflower), `org/slf4j`.

Not shipped: Lombok (`provided` scope, used only as an annotation processor; no `lombok/` entry in the jar),
JUnit, AssertJ and the JUnit launcher (test scope), and Maven plugins.

**Surprise: nested jars.** Vineflower carries three plugin jars under `META-INF/plugins/`, and shading copies them
into `jdoc.jar` as-is:

- `vineflower-idea-not-null.jar` and `vineflower-variable-renaming.jar`: Vineflower's own code (Apache-2.0).
- `vineflower-kotlin-0.1.0.jar` (1.6 MB): the Kotlin plugin. It also bundles JetBrains annotations 24.0.0
  (`org/jetbrains/annotations`, `org/intellij/lang/annotations`) and a repackaged copy of Kotlin's metadata
  library (`org/vineflower/kt/metadata`). That library brings Kotlin's protobuf-lite fork
  (`org/vineflower/kt/protobuf`), which is under BSD-3-Clause, Google.

jdoc decompiles Java only and does not need any of these plugins.

## Findings

| Artifact | Version | SPDX license | Copyright line(s) | NOTICE to propagate? | Obligations when shipping a binary |
| --- | --- | --- | --- | --- | --- |
| org.xerial:sqlite-jdbc (Java part) | 3.54.1.0 | Apache-2.0 AND ISC AND BSD-2-Clause | Copyright 2009 Taro L. Saito (Apache-2.0 files); Copyright (c) 2007 David Crawshaw (ISC headers in `DB.java`, `NativeDB.java/.c`, `JDBC.java`, `Core*.java`, `Function.java`); Copyright (c) 2006, David Crawshaw (`LICENSE.zentus`, BSD-2-Clause) | The upstream repo has a `NOTICE` ("This product includes the following softwares developed by David Crawshaw. See LICENSE.zentus file." plus a stale NestedVM line). The jar has no NOTICE. Propagating it is conservative and costs one line. The NestedVM line does not apply: there is no NestedVM code in the jar. | Ship the Apache-2.0 text. Reproduce the Crawshaw copyright and the BSD-2-Clause text in the documentation (BSD clause 2). Keep the ISC notice "in all copies". |
| sqlite-jdbc native libraries (`org/sqlite/native/**`) | SQLite 3.54.1 | blessing (public domain) + the above | SQLite: no copyright ("The author disclaims copyright"). JNI glue `NativeDB.c`: ISC, David Crawshaw. Also compiled in: `extension-functions.c` (Liam Healy, sqlite.org contrib, no license statement, treated as public domain) | No | None for SQLite. ISC as above. Statically linked libgcc parts are covered by the GCC Runtime Library Exception (no notice duty). |
| org.jsoup:jsoup | 1.23.2 | MIT | Copyright (c) 2009-2026 Jonathan Hedley | No NOTICE | Include the copyright line and the MIT text. The jar already carries `META-INF/jsoup/LICENSE`, which survives shading. |
| org.ow2.asm:asm | 9.11.1 | BSD-3-Clause | Copyright (c) 2000-2011 INRIA, France Telecom | No NOTICE | Reproduce the copyright and the BSD-3 text in the documentation (clause 2). Do not use the holders' names to promote jdoc (clause 3). **The ASM jar contains no license file at all**, so nothing in today's `jdoc.jar` satisfies this. |
| org.vineflower:vineflower | 1.12.0 | Apache-2.0 | Copyright 2000-2022 JetBrains s.r.o. (Fernflower heritage, per file headers); ForgeFlower contributors; Copyright 2019 FabricMC project; Vineflower Development Team | Vineflower ships no NOTICE (the repo has only `LICENSE.md`). Upstream Fernflower added `NOTICE.txt` ("Copyright © 2014–2025 JetBrains s.r.o.") in March 2025. Vineflower does not distribute that NOTICE, so §4(d) is not triggered. Recording the line in THIRD-PARTY-NOTICES is a courtesy. | Ship the Apache-2.0 text (our `LICENSE` covers this). |
| (nested in Vineflower) JetBrains annotations | 24.0.0 | Apache-2.0 | Copyright 2000-2023 JetBrains s.r.o. | No | Apache-2.0 text. |
| (nested in Vineflower) Kotlin metadata, repackaged | from Kotlin | Apache-2.0 | Copyright 2010-2024 JetBrains s.r.o. and Kotlin contributors | Kotlin's NOTICE covers the compiler distribution. Vineflower does not propagate it, so it is not required. | Apache-2.0 text. |
| (nested in Vineflower) Kotlin protobuf fork | from Kotlin | BSD-3-Clause | Copyright 2008, Google Inc. | No | BSD-3 copyright and text in the documentation. |
| org.slf4j:slf4j-api | 2.0.17 | MIT | Copyright (c) 2004-2022 QOS.ch Sarl (Switzerland) | No | MIT text with the copyright line. |
| org.slf4j:slf4j-simple | 2.0.17 | MIT | Copyright (c) 2004-2022 QOS.ch Sarl (Switzerland) | No | Same as slf4j-api. |

No Apache-2.0 dependency ships a NOTICE inside its jar. The only NOTICE worth propagating is sqlite-jdbc's (the
David Crawshaw attribution). Every other obligation is about reproducing the license text and copyright line
(MIT, BSD-2/3, ISC).

## Current state of the shaded jar (non-compliant)

- `META-INF/LICENSE.txt` in `jdoc.jar` is the **slf4j MIT licence**. Both slf4j jars carry it and shade keeps the
  first copy. Because the jar has no jdoc `LICENSE` or `NOTICE`, the jar looks MIT-licensed by QOS.ch.
- The jar has no ASM, Vineflower, protobuf or ISC text. The jsoup licence and the two sqlite-jdbc licences survive
  only because they live under distinct paths (`META-INF/jsoup/LICENSE`,
  `META-INF/maven/org.xerial/sqlite-jdbc/LICENSE{,.zentus}`).
- The tarball layout in `OUTLINE.md` contains no licence files.

## Required contents

Three hand-maintained files at the repo root are the single source of truth:

- `LICENSE`: the Apache-2.0 text (unchanged).
- `NOTICE`: the existing jdoc lines, plus a short "This product includes software developed by David Crawshaw
  (sqlite-jdbc, see THIRD-PARTY-NOTICES)" line. This is the only third-party line that a NOTICE file legally
  carries forward. Apache's guidance is to keep NOTICE to what is required, and MIT/BSD texts do not belong there.
- `THIRD-PARTY-NOTICES`: one section per shipped component, including the nested Vineflower plugins and the SQLite
  native code. Each section gives the name, Maven coordinates and version, upstream URL, SPDX id, copyright lines,
  and either the full licence text (MIT, BSD-2-Clause, BSD-3-Clause, ISC) or a reference to `LICENSE` for
  Apache-2.0 components. SQLite gets a public-domain statement.

Where they must appear:

- **Jar:** `META-INF/LICENSE`, `META-INF/NOTICE`, `META-INF/THIRD-PARTY-NOTICES`. Remove dependency copies at the
  `META-INF/` root, which is the slf4j `LICENSE.txt` today. Per-library files under distinct paths may stay;
  they are harmless and redundant.
- **Tarball:** `LICENSE`, `NOTICE`, `THIRD-PARTY-NOTICES` at the bundle root, next to `VERSION`.
- **Installed prefix (recommended, not strictly required):** `install.sh` copies the three files into the prefix,
  for example `<prefix>/share/doc/jdoc/`. The native library the installer extracts comes from the jar on the
  user's own machine, so that is not a new distribution.

## Shade configuration (recommended approach)

- Add `ApacheLicenseResourceTransformer`. It drops every `META-INF/LICENSE`, `LICENSE.txt` and `LICENSE.md` from
  all input jars, including the slf4j copy.
- Do **not** use `ApacheNoticeResourceTransformer`. No dependency has a NOTICE, and its generated boilerplate
  ("This product includes software developed by ...") would only add noise. Instead, exclude `META-INF/NOTICE*`
  in the existing `*:*` filter, so a future dependency NOTICE cannot silently overwrite ours. The coverage check
  below makes such a NOTICE visible so someone merges it by hand.
- Add three `IncludeResourceTransformer` entries that copy `${project.basedir}/LICENSE`, `NOTICE` and
  `THIRD-PARTY-NOTICES` to `META-INF/LICENSE`, `META-INF/NOTICE` and `META-INF/THIRD-PARTY-NOTICES`. Do not put
  them in `src/main/resources/META-INF`: the licence transformer would drop the project's own `LICENSE` too.
- Optional but recommended: exclude `META-INF/plugins/**` from `org.vineflower:vineflower`. jdoc does not decompile
  Kotlin, and the plugins are about 1.7 MB of the jar, including protobuf (BSD-3), Kotlin metadata and JetBrains
  annotations. **Caveat:** Vineflower loads plugins it finds on the classpath, and the idea-not-null plugin can
  change decompiled output. Accept the exclusion only if the decompiler ITs and golden outputs stay identical. If
  the plugins stay, their three extra components must be listed in THIRD-PARTY-NOTICES.

## Keeping the list complete

`license-maven-plugin` (org.codehaus.mojo) is not worth adding. The set is six artifacts plus nested content, and
the plugin cannot see the nested Vineflower jars, the ISC/BSD files inside sqlite-jdbc or the SQLite native code.
Those are the parts that need human judgement. Use a hand-maintained `THIRD-PARTY-NOTICES` plus an automated guard:

- A failsafe IT that opens the built shaded jar and checks:
  - `META-INF/LICENSE`, `NOTICE` and `THIRD-PARTY-NOTICES` exist and equal the root files byte for byte.
  - No other `META-INF/LICENSE*` or `NOTICE*` exists at the root.
  - Every top-level package prefix and every nested jar in the shaded jar is on an allow-list that maps it to a
    THIRD-PARTY-NOTICES section.
- The same IT, or a unit test, reads the resolved runtime dependency list. One option is a
  `maven-dependency-plugin:list` execution with `outputFile` into the build dir, read through a system property.
  The check fails when a `groupId:artifactId:version` is missing from THIRD-PARTY-NOTICES. A version bump
  therefore fails the build until someone re-reviews the entry.

## JDK sources and javadoc (no obligation)

jdoc does not ship the JDK. At runtime it reads the user's own `src.zip` (GPL-2.0 WITH Classpath-exception-2.0)
and the user's Maven artifacts. It writes index data derived from them (signatures, doc comment text, decompiled
source) into the user's local cache on the same machine. That is the user's private use of material they already
have, not a distribution by us, so no licence duty arises. Two practices keep it that way: never ship a pre-built
index or AOT cache trained on JDK or third-party data in the release, and never commit JDK source excerpts as test
fixtures (generate fixtures in `@TempDir`, as T01 already does). The AOT cache is built at install time on the
user's machine, which is fine.

## Headers and pom metadata

- **Source headers:** Apache-2.0 recommends per-file headers but does not require them. For a single-author repo
  with root `LICENSE`/`NOTICE`, the low-cost recommendation is a two-line SPDX header (`SPDX-License-Identifier:
  Apache-2.0` and a copyright line), not the 13-line boilerplate. Enforcing it means an extra plugin (Spotless or
  license-maven-plugin `check-file-header`) in a project that keeps plugins minimal. Treat it as optional and out
  of scope for T21 unless the user wants it, because touching every source file collides with concurrent tasks.
- **pom metadata:** yes. `<url>`, `<inceptionYear>`, `<licenses>` (Apache-2.0, SPDX name, distribution `repo`),
  `<developers>` (vecluu) and `<scm>` (github.com/vecluu/jdoc) cost nothing. They end up in
  `META-INF/maven/io.github.vecluu/jdoc/pom.xml` inside the jar and are required if jdoc is ever published to
  Maven Central.
