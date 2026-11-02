---
name: jdoc
description: Look up exact signatures and Javadoc of Java library and JDK APIs for the current Maven module. Use before calling any library method you have not verified in this session, instead of guessing or decompiling.
---

# jdoc

Answers come from the versions this module actually resolves
(`mvn -o dependency:list`) plus the module's JDK. Plain text,
one header line naming the type and the artifact version.

## When to call it
- Before writing a call to a library or JDK API whose exact
  signature, parameters or exceptions you have not verified
  in this session: `jdoc method <Type>#<name>`.
  One overload only: `jdoc method 'Map#of(Object,Object)'`
  (simple parameter type names; quote the parentheses).
- Class name unknown: `jdoc find <part of name>` (substring or
  camel humps: `HSR` finds `HttpServletRequest`).
- Exploring an unfamiliar type: `jdoc class <Type>` first,
  then `jdoc method` for the members you will use.
- Need an implementation of an interface: `jdoc impls <Type>`.
- Know a method name, not its class: `jdoc member <name>`.
- Overview of a package: `jdoc package <pkg>`.

## Rules
- Exit 2 means ambiguous: pick the FQCN matching the file's
  imports and re-run. Exit 1 means not on this classpath:
  say so, do not invent the API. Exit 3 is an error (for
  example Maven could not resolve the module offline): show
  the message to the user instead of guessing.
- Use `jdoc source` only when the docs leave behaviour unclear
  (null handling, exceptions, thread safety, defaults).
- Never unzip jars, read ~/.m2, or decompile classes yourself.
- Use only public and protected API. Do not call types in
  packages named `internal` or `impl`.
- Working in another module than the shell's directory:
  add `--from <path to the file you are editing>`.
- A header tagged `[version unresolved]` means the version may
  be wrong: tell the user. `[decompiled, no docs]` means there
  is no documentation: rely on the signature only.
- Output ending with `page 1/N · next: ...`: fetch more pages
  (`--page 2`) only if the member you need is not on the page
  you have.
- Results refresh by themselves when the POM changes; run
  `jdoc index --force` only if answers look stale.
- Syntax of any command: `jdoc --help`, `jdoc <command> --help`.
