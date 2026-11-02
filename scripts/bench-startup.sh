#!/usr/bin/env bash
# Startup benchmark for warm jdoc calls (index already built).
#
# Usage: scripts/bench-startup.sh <path/to/jdoc.jar>
#
# Creates a temporary Maven module with a fake `mvn` (empty dependency list), a temporary JDOC_CACHE and
# JDOC_M2_REPO, warms the index with one call (indexes the JDK), then runs every benchmarked command
# BENCH_WARMUP times unmeasured and BENCH_RUNS times measured, and prints the median wall time per command.
# Never touches ~/.cache/jdoc or ~/.m2.
#
# Environment:
#   BENCH_RUNS        measured runs per command (default 11)
#   BENCH_WARMUP      unmeasured runs per command before measuring (default 2)
#   BENCH_JAVA        java executable (default: $JAVA_HOME/bin/java, else `java` on PATH)
#   BENCH_JVM_FLAGS   JVM flags (default: -XX:TieredStopAtLevel=1); e.g. add -XX:AOTCache=... for T17
#   BENCH_COMMANDS    commands to measure, one per line (default: the list below)
#   BENCH_KEEP        if set to 1, keep the temp directory and print its path
#   BENCH_NEW_DEP     if set to 0, skip the "first call after adding a dependency" scenarios (default 1)
#
# "First call after adding a dependency": a small library (not the installer's training library) is compiled once
# with the JDK's javac, javadoc and jar tools. Before every run of such a scenario, a second module's POM gets a new
# dependency version and a fresh copy of the jars appears in the temp repository under that version, so each
# measured call re-resolves the module and indexes a jar it has never seen: `new-dep sources` (with a sources jar,
# `class`), `new-dep javadoc` (javadoc jar only, `class`) and `new-dep binary` (binary only, `source`, decompiled).
# Skipped when javac, javadoc or jar is missing next to the java executable.
#
# Output: one line per command, `<median ms>  <min ms>  <max ms>  <command>`, after a header line.
# Exits non-zero if the warm-up index fails or any measured command exits with a code other than 0 or 1.
set -euo pipefail

if [ $# -ne 1 ] || [ ! -f "$1" ]; then
  echo "usage: $0 <path/to/jdoc.jar>" >&2
  exit 2
fi
jar=$(cd "$(dirname "$1")" && pwd)/$(basename "$1")

runs=${BENCH_RUNS:-11}
warmup=${BENCH_WARMUP:-2}
jvm_flags=${BENCH_JVM_FLAGS:--XX:TieredStopAtLevel=1}
if [ -n "${BENCH_JAVA:-}" ]; then
  java=$BENCH_JAVA
elif [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  java=$JAVA_HOME/bin/java
else
  java=$(command -v java) || { echo "error: no java on PATH" >&2; exit 2; }
fi
java_home=$(cd "$(dirname "$(readlink -f "$java")")/.." && pwd)

default_commands='--version
class java.util.Optional
method java.util.Optional#orElseThrow
find HashMap
member orElseThrow
source java.util.Optional#orElseThrow'
commands=${BENCH_COMMANDS:-$default_commands}

work=$(mktemp -d "${TMPDIR:-/tmp}/jdoc-bench.XXXXXX")
# shellcheck disable=SC2317 # called by the EXIT trap
cleanup() {
  if [ "${BENCH_KEEP:-0}" = 1 ]; then
    echo "kept: $work" >&2
  else
    rm -rf "$work"
  fi
}
trap cleanup EXIT

mkdir -p "$work/module" "$work/cache" "$work/m2" "$work/bin"
cat > "$work/module/pom.xml" <<'EOF'
<project>
  <modelVersion>4.0.0</modelVersion>
  <groupId>bench</groupId>
  <artifactId>bench</artifactId>
  <version>1</version>
</project>
EOF
# Fake mvn: writes the module's deps.list (if any, else an empty list) to -DoutputFile=... and succeeds.
cat > "$work/bin/mvn" <<'EOF'
#!/bin/sh
out=
for a in "$@"; do case "$a" in -DoutputFile=*) out="${a#-DoutputFile=}";; esac; done
if [ -n "$out" ]; then
  if [ -f deps.list ]; then cp deps.list "$out"; else : > "$out"; fi
fi
exit 0
EOF
chmod +x "$work/bin/mvn"

export JDOC_CACHE="$work/cache" JDOC_M2_REPO="$work/m2" JDOC_MVN="$work/bin/mvn" JAVA_HOME="$java_home"

# Milliseconds since the epoch, with sub-ms precision where available (bash 5 EPOCHREALTIME, else GNU date).
now_us() {
  if [ -n "${EPOCHREALTIME:-}" ]; then
    local t=${EPOCHREALTIME/[.,]/}
    echo "$t"
  else
    echo $(( $(date +%s%N) / 1000 ))
  fi
}

cd "$work/module"
# shellcheck disable=SC2086 # jvm_flags is a list of words on purpose
jdoc() { "$java" $jvm_flags -jar "$jar" "$@"; }

if ! jdoc index > "$work/index.out" 2> "$work/index.err"; then
  echo "error: warm-up index failed:" >&2
  cat "$work/index.err" >&2
  exit 1
fi

# The library for the "new dependency" scenarios: two types with docs, compiled once.
new_dep=${BENCH_NEW_DEP:-1}
if [ "$new_dep" = 1 ]; then
  for tool in javac javadoc jar; do
    if [ ! -x "$java_home/bin/$tool" ]; then
      echo "note: $java_home/bin/$tool missing, skipping the new-dependency scenarios" >&2
      new_dep=0
    fi
  done
fi
if [ "$new_dep" = 1 ]; then
  lib=$work/newdep-lib
  mkdir -p "$lib/src/bench/fresh" "$lib/classes" "$lib/apidocs" "$work/newdep-module"
  cat > "$lib/src/bench/fresh/Ledger.java" <<'EOF2'
package bench.fresh;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * An append-only list of {@link Entry entries}, newest last.
 *
 * @param <T> the payload type
 */
public class Ledger<T extends Comparable<? super T>> implements Iterable<Ledger.Entry<T>> {

    /** One booked payload with its sequence number. */
    public record Entry<T>(long sequence, T payload) {
    }

    private final List<Entry<T>> entries = new ArrayList<>();

    /**
     * Books a payload.
     *
     * @param payload the payload, not null
     * @return the new entry
     * @throws IllegalArgumentException if the payload is null
     */
    public Entry<T> book(T payload) {
        if (payload == null) {
            throw new IllegalArgumentException("payload");
        }
        Entry<T> entry = new Entry<>(entries.size(), payload);
        entries.add(entry);
        return entry;
    }

    /**
     * The largest payload booked so far.
     *
     * @return the maximum, or empty if nothing was booked
     */
    public Optional<T> max() {
        return entries.stream().map(Entry::payload).max(Comparable::compareTo);
    }

    @Override
    public java.util.Iterator<Entry<T>> iterator() {
        return List.copyOf(entries).iterator();
    }
}
EOF2
  cat > "$lib/src/bench/fresh/Ledgers.java" <<'EOF2'
package bench.fresh;

/** Factory methods for {@link Ledger}. */
public final class Ledgers {

    private Ledgers() {
    }

    /**
     * A ledger pre-filled with the given payloads.
     *
     * @param payloads booked in order
     * @param <T>      the payload type
     * @return a new ledger
     */
    @SafeVarargs
    public static <T extends Comparable<? super T>> Ledger<T> of(T... payloads) {
        Ledger<T> ledger = new Ledger<>();
        for (T payload : payloads) {
            ledger.book(payload);
        }
        return ledger;
    }
}
EOF2
  "$java_home/bin/javac" -g -d "$lib/classes" "$lib"/src/bench/fresh/*.java
  "$java_home/bin/javadoc" -quiet -Xdoclint:none -notimestamp -d "$lib/apidocs" -sourcepath "$lib/src" bench.fresh \
    > /dev/null
  "$java_home/bin/jar" --create --file "$lib/binary.jar" -C "$lib/classes" .
  "$java_home/bin/jar" --create --file "$lib/sources.jar" -C "$lib/src" .
  "$java_home/bin/jar" --create --file "$lib/javadoc.jar" -C "$lib/apidocs" .
fi
new_dep_serial=0

# Adds a dependency the index has never seen to the new-dependency module: a new version of bench.fresh:<flavour>
# with copies of the jars, declared in the module's POM and listed by the fake mvn.
# shellcheck disable=SC2317 # called indirectly, as the prepare command of measure
add_new_dependency() {
  local flavour=$1 version dir
  new_dep_serial=$((new_dep_serial + 1))
  version=1.$new_dep_serial
  dir=$work/m2/bench/fresh/$flavour/$version
  mkdir -p "$dir"
  cp "$lib/binary.jar" "$dir/$flavour-$version.jar"
  case $flavour in
    sources) cp "$lib/sources.jar" "$dir/$flavour-$version-sources.jar" ;;
    javadoc) cp "$lib/javadoc.jar" "$dir/$flavour-$version-javadoc.jar" ;;
  esac
  cat > "$work/newdep-module/pom.xml" <<EOF2
<project>
  <modelVersion>4.0.0</modelVersion>
  <groupId>bench</groupId>
  <artifactId>newdep</artifactId>
  <version>1</version>
  <dependencies>
    <dependency><groupId>bench.fresh</groupId><artifactId>$flavour</artifactId><version>$version</version></dependency>
  </dependencies>
</project>
EOF2
  printf '\nThe following files have been resolved:\n   bench.fresh:%s:jar:%s:compile:%s\n' \
    "$flavour" "$version" "$dir/$flavour-$version.jar" > "$work/newdep-module/deps.list"
}

echo "java: $java ($java_home)"
echo "flags: $jvm_flags"
echo "runs: $runs (after $warmup warm-up)"
printf '%8s %8s %8s  %s\n' median_ms min_ms max_ms command
status=0

# measure <label> <prepare command or ''> <jdoc args...>: runs in the current directory.
measure() {
  local label=$1 prepare=$2 i code start end sorted median min max
  shift 2
  local times=()
  i=0
  while [ "$i" -lt "$warmup" ]; do
    [ -n "$prepare" ] && $prepare
    jdoc "$@" > /dev/null 2>&1 || true
    i=$((i + 1))
  done
  i=0
  while [ "$i" -lt "$runs" ]; do
    i=$((i + 1))
    [ -n "$prepare" ] && $prepare
    start=$(now_us)
    code=0
    jdoc "$@" > /dev/null 2> "$work/last.err" || code=$?
    end=$(now_us)
    if [ "$code" -gt 1 ]; then
      echo "error: '$label' exited with $code:" >&2
      cat "$work/last.err" >&2
      status=1
    fi
    times+=($(( (end - start) / 1000 )))
  done
  sorted=$(printf '%s\n' "${times[@]}" | sort -n)
  median=$(echo "$sorted" | sed -n "$(( (runs + 1) / 2 ))p")
  min=$(echo "$sorted" | head -n 1)
  max=$(echo "$sorted" | tail -n 1)
  printf '%8s %8s %8s  %s\n' "$median" "$min" "$max" "$label"
}

while IFS= read -r line; do
  [ -z "$line" ] && continue
  # shellcheck disable=SC2086 # split the command line into words on purpose
  measure "$line" '' $line
done <<< "$commands"

if [ "$new_dep" = 1 ]; then
  cd "$work/newdep-module"
  measure 'new-dep sources: class bench.fresh.Ledger' 'add_new_dependency sources' class bench.fresh.Ledger
  measure 'new-dep javadoc: class bench.fresh.Ledger' 'add_new_dependency javadoc' class bench.fresh.Ledger
  measure 'new-dep binary: source bench.fresh.Ledgers#of' 'add_new_dependency binary' source 'bench.fresh.Ledgers#of'
fi
exit $status
