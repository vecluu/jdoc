# Licence compliance of the shipped artifacts

Decision record. The files themselves are the truth: `LICENSE`, `NOTICE`, `THIRD-PARTY-NOTICES` at the repository
root, the shade configuration in `pom.xml`, and `LicenseNoticesIT`, which enforces both.

## What ships

The shaded `jdoc.jar` holds jdoc's own classes plus its runtime dependencies (sqlite-jdbc with its native
libraries, jsoup, ASM, Vineflower, slf4j). Vineflower carries three nested plugin jars under `META-INF/plugins/`;
the Kotlin one bundles JetBrains annotations, Kotlin metadata and a BSD-3 protobuf fork. They stay in the jar because
removing them changes decompiler output. Lombok (`provided`), test libraries and Maven plugins do not ship.

## Decisions

- **Three hand-maintained root files are the single source.** `LICENSE` (Apache-2.0), `NOTICE` (jdoc plus the one
  third-party line a NOTICE must carry forward: David Crawshaw, sqlite-jdbc), and `THIRD-PARTY-NOTICES` (one section
  per shipped component, nested ones included, with copyright lines and full MIT/BSD/ISC texts). They are copied to
  `META-INF/` in the jar, to the bundle root, and by the installer to `<prefix>/share/doc/jdoc/`.
- **No dependency licence or NOTICE at the jar's `META-INF/` root.** Without that rule the jar looked MIT-licensed
  (slf4j's `LICENSE.txt` won the shade). Dependency NOTICEs are excluded so a future one cannot silently overwrite
  ours; `LicenseNoticesIT` makes it visible instead.
- **No license-maven-plugin.** It cannot see the nested Vineflower jars or the native code, which are the parts that
  need judgement. Instead `LicenseNoticesIT` checks the jar's licence files against the root files, maps every
  top-level package and nested jar to a notices section (`OWNERS`), and fails when a resolved runtime
  `groupId:artifactId:version` is missing from `THIRD-PARTY-NOTICES`. A version bump therefore forces a re-review.
- **JDK sources and users' artifacts carry no duty.** jdoc reads the user's own `src.zip` and Maven artifacts and
  writes derived data into the user's local cache; that is not distribution. Keep it that way: never ship a pre-built
  index or an AOT cache trained on JDK or third-party data, and never commit JDK source excerpts as test fixtures
  (generate fixtures in `@TempDir`). The AOT cache is built at install time on the user's machine.
- **No per-file licence headers.** Apache-2.0 recommends but does not require them; enforcing them needs another
  plugin. The pom carries `<licenses>`, `<scm>`, `<developers>` metadata, which also ends up in the jar.
