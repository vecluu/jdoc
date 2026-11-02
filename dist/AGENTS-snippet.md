## Java library APIs
Do not guess unfamiliar Java library APIs and never decompile
or unzip jars. When a library or its version is unfamiliar,
after a compile error on a library call, or when null or
exception behaviour matters, use the `jdoc` skill (CLI:
`jdoc --help`); it answers for this module's dependency
versions. Common JDK calls and calls this project already
makes need no lookup.
