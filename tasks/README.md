# jdoc tasks

The orchestration board: how work is organised, what is in flight, and the backlog. Rules for implementing agents
are in `tasks/AGENT_GUIDE.md`; the brief shape is `tasks/TEMPLATE.md`.

## Workflow

1. The orchestrator writes task briefs (`tasks/Txx-<slug>.md`, shape in `tasks/TEMPLATE.md`): goal, owned files and
   decisions, no code. Briefs are grouped into waves below; briefs in one wave own disjoint files.
2. The maintainer approves each wave before it starts.
3. Implementing agents get `tasks/AGENT_GUIDE.md` plus one brief, work only in the files they own, and write
   `tasks/notes/Txx.md`.
4. The orchestrator verifies (a clean `./mvnw -B verify`), commits in scoped commits, and makes sure `docs/SPEC.md`,
   `docs/ARCHITECTURE.md` and the area maps reflect the change.
5. **Finished briefs and notes are deleted** in the task's last commit, after anything durable has moved into the
   docs or the backlog. Git keeps them: `git log --all --full-history -- tasks/notes/Txx.md`. Task numbers keep
   counting up (T01–T32 are done; the v0.1.0 tag holds the briefs and notes of T01–T27).
6. Long research or implementation goes to agents, so the orchestrator's context stays small.

## In flight

Nothing. T29 and T32 are done (T01–T32 finished).

## Backlog

Found during implementation, not yet scheduled. One line each; take them into a brief, then delete the line.

- Fallback CDS archive only contains the classes of the call that created it (weak if that was `--version`);
  train it explicitly or regenerate it after a real lookup.
- `query.MemberReferenceParser` may treat a method named like the type's simple name as a constructor.
- `app.PackageCommand` header orders artifact labels by type name, not classpath order.
- `cli.TrainingWorkload.parse` has no quoting, so a workload argument cannot contain spaces.
- `index.IndexDatabase.initialiseSchema` takes `BEGIN IMMEDIATE` on every open, so lookups wait behind indexing in
  another process (latency only).
- `module.ModuleMetaCodec` ignores `formatVersion` on decode.
- Classpath cache key ignores `settings.xml`, BOM imports and repository release parents; a jar deleted from the
  local repository is silently dropped on a cache hit.
- `index.DocRepository.insertMemberDocs` matches docs by type and position; inserting a type twice would duplicate rows.
- JDK artifact stamp includes the `src.zip` path, so alternating `JAVA_HOME`s of the same feature version re-index
  the JDK every time; JDK type ids also change per indexing run.
- `JdkIndexer` is wired as a lambda in `CompositionRoot`, so `DefaultJdkIndexer`'s `indexedFile` override is ignored.
- No CLI path sets `IndexOptions.forceJdk`; decide whether `index --force` should rebuild the JDK.
- `javadoc.JavadocJarReader.withoutCopiedSince` re-parses the outer page for every nested type.
- `dist/install.sh` `usage()` prints lines 5–10 of the file with `sed`; editing the header comment breaks `--help`.
- `scripts/bench-startup.sh` default JVM flags differ from the launcher's.
- Duplicated test helpers: `fatJar()` in `InstallerIT`, `BenchStartupScriptIT`, `HotPathClassLoadingIT`,
  `EndToEndIT`; the fake-`mvn` snippet in `install.sh`, `bench-startup.sh` and `InstallerIT`.
- `.gitignore` entry `!.mvn/wrapper/maven-wrapper.jar` is dead.
- Dead or test-only public API: `DocSource.isSourceText`, `TokenBudget.fits`/`estimateTokens`,
  `Gav.sourcesJarPath`/`javadocJarPath`, `TypeReference.isSimpleName`, `TypeCandidate.Relative.isSimpleName`,
  `PageFooter.nextCommand`/`withoutPageOption` (could be package-private).
- Dead or test-only public API: the 3-argument `MemberLookup.MemberNotFound` constructor, `LookupScope.isEmpty`,
  `LookupServices.types`/`typeMembers`/`sourceFiles` (unread outside `lookup`).
- Dead or test-only public API: `BinaryJarScanner.readClass`, `JarScanSummary.skippedEntries`/`packageInfoPackages`,
  `JavadocJarReader.hasTypePage`, `JavadocAnchor.generate`, `ZipArchiveOpener.ZipSourceArchive.zip()`.
