# Changelog

All notable changes to jdoc are documented in this file. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and jdoc follows
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.1.0] - 2026-11-02

First public release: an offline CLI that shows a coding agent the exact signature and Javadoc of the Java
libraries and JDK classes its Maven module uses, in a few hundred tokens per call.

### Added

- Commands `find`, `class`, `method`, `source`, `impls`, `member`, `package` and `index [--force]`, with
  `--from <path>` to pick the module and `--page <n>` for results capped at about 2,000 tokens. Simple names and
  camel humps are accepted; an ambiguous simple name lists its candidates. Exit codes: `0` found, `1` not found,
  `2` ambiguous, `3` error. `jdoc <command> --help` documents each command.
- Offline version resolution per module through `mvn -o dependency:list` (all scopes), so parent POMs, BOMs and
  `dependencyManagement` are handled by Maven. Multi-module reactors without installed siblings work. If Maven fails,
  jdoc uses the last good resolution, or the newest local version marked `[version unresolved]`.
- Docs per type from the best available source: the sources jar (parsed with the JDK's javac Tree API), then the
  javadoc jar (HTML, including JDK 8 javadoc), then a Vineflower decompile. The header says when an answer came
  from a javadoc jar or was decompiled. JDK classes come from the JDK's `lib/src.zip`.
- A SQLite index in `~/.cache/jdoc` (or `$JDOC_CACHE`), built lazily per artifact and refreshed on its own when a
  POM or jar changes. jdoc writes nothing into projects and never goes online.
- Fast startup: the installer extracts the sqlite native library and trains a JDK 25 AOT cache, which brings a warm
  lookup under 100 ms. When the cache no longer matches the jar or JDK, the launcher falls back silently to a
  per-user CDS archive.
- Release bundle `jdoc-<version>.tar.gz` with an offline `install.sh` (`--prefix`, `--bindir`, `--skills-dir`,
  `--no-aot`) for airgapped machines, a POSIX launcher, and the licence files. Each release has `SHA256SUMS` and
  signed build provenance; the jar and the tarball are reproducible from the tagged commit.
- An [opencode](https://opencode.ai) skill and an `AGENTS.md` snippet that tell the agent when a lookup is worth a
  call.

### Known limitations

- Linux only, and Java 25 must be the `java` on `PATH`. Tested on x86_64; aarch64 is untested.
- Maven projects only; dependencies must already be in the local repository. Docs need `-sources.jar` or
  `-javadoc.jar` files there; otherwise only decompiled signatures are shown.
- Java sources only: Kotlin or Scala sources count as missing sources.
- Options must follow the command (`jdoc class X --page 2`, not `jdoc --page 2 class X`).
- The fallback CDS archive only holds the classes of the call that created it, so the fallback is slower when
  that call was a trivial one such as `--version`.
- JDK 25 can rarely crash while creating the AOT cache. The installer then retries once without method profiles
  (2 to 3 ms slower per call); if that fails too, it warns and the launcher uses the fallback archive.

[Unreleased]: https://github.com/vecluu/jdoc/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/vecluu/jdoc/releases/tag/v0.1.0
