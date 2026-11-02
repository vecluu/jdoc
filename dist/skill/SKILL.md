---
name: jdoc
description: Offline lookup of Java library and JDK APIs (signatures, Javadoc, source) at the exact versions the current Maven module resolves. Use when a Java library or its version is unfamiliar (internal, niche, new major version), when you do not know a class or method name, after a compile error on a library call, when null, exception or thread-safety behaviour matters, or instead of unzipping or decompiling jars. Not needed for common JDK APIs you know well.
---

# jdoc

Answers come from the versions this module resolves
(`mvn -o dependency:list`) plus the module's JDK. Plain text;
the first line names the type and the artifact version.

## When to call it
The compiler checks signatures for free; jdoc is for what you
do not know and what the compiler cannot check. Call it when:
- The library or version is unfamiliar: internal or company
  code, a niche library, a new major version.
- You do not know the class or method name: `find`, `member`.
  Do not invent one.
- A compile error names a library symbol (`cannot find symbol`,
  `no suitable method`, `cannot be applied`): look it up, do
  not guess a second time.
- Behaviour matters: null handling, exceptions, mutability,
  thread safety, defaults, deprecation.
- You are about to unzip a jar, read ~/.m2 or run javap.

Skip it for common JDK APIs in their usual form (collections,
streams, String, Optional, java.time, Files), for calls the
project already makes (grep the code instead), and for anything
you looked up earlier in this session.

## Cheapest command first
- Name unknown: `jdoc find <part>` (substring or camel humps:
  `HSR` finds `HttpServletRequest`). Method name only:
  `jdoc member <name>`.
- One method: `jdoc method <Type>#<name>`. If it has many
  overloads and you know which one, add the simple parameter
  types: `jdoc method 'Map#of(Object,Object)'` (quote it).
- Several members of one unfamiliar type: one `jdoc class
  <Type>`, not several `method` calls.
- Implementations of an interface: `jdoc impls <Type>`.
  Package overview: `jdoc package <pkg>`.
- `jdoc source <Type>#<name>` only when the docs leave
  behaviour unclear.
- Several known lookups: chain them in one shell call,
  `jdoc method A#x; jdoc method B#y`.

## Rules
- Exit 1: not on this classpath. Say so; do not invent the API.
  Exit 2: ambiguous. Pick the FQCN matching the file's imports
  and re-run. Exit 3: error (for example Maven could not
  resolve the module offline). Show it to the user.
- Never unzip jars, read ~/.m2 or decompile classes yourself.
- Public and protected API only; avoid packages named
  `internal` or `impl`.
- Editing a file in another module than the shell's directory:
  add `--from <path to that file>`.
- `[version unresolved]` in the header: the version may be
  wrong, tell the user. `[decompiled, no docs]`: signatures
  only, no documentation.
- `page 1/N · next: ...` at the end: fetch `--page 2` only if
  what you need is not on the page you have.
- The first call in a module builds its index (seconds), later
  calls take well under a second. Answers refresh when the POM
  changes; `jdoc index --force` only if they look stale.
- Syntax: `jdoc --help`, `jdoc <command> --help`.

## Examples
- Worth it: the module uses `com.acme:billing-client` and you
  need to create an invoice: `jdoc find Invoice`, then one
  `jdoc class` on the client type you found.
- Worth it: the build says `cannot find symbol: method
  toLocalDate()` on a library type: one `jdoc class <Type>`
  shows what exists, instead of guessing another name.
- Worth it: does a library parser return null or throw on bad
  input? `jdoc method <Type>#parse`; `source` if still unclear.
- Not worth it: `list.stream().map(...).toList()`,
  `Optional.orElseThrow()`, `String.isBlank()`, or a Jackson
  `ObjectMapper` call that appears elsewhere in this project.
