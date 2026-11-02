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
# Fake mvn: writes an empty dependency list to -DoutputFile=... and succeeds.
cat > "$work/bin/mvn" <<'EOF'
#!/bin/sh
out=
for a in "$@"; do case "$a" in -DoutputFile=*) out="${a#-DoutputFile=}";; esac; done
if [ -n "$out" ]; then : > "$out"; fi
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

echo "java: $java ($java_home)"
echo "flags: $jvm_flags"
echo "runs: $runs (after $warmup warm-up)"
printf '%8s %8s %8s  %s\n' median_ms min_ms max_ms command
status=0
while IFS= read -r line; do
  [ -z "$line" ] && continue
  # shellcheck disable=SC2086 # split the command line into words on purpose
  set -- $line
  i=0
  while [ "$i" -lt "$warmup" ]; do jdoc "$@" > /dev/null 2>&1 || true; i=$((i + 1)); done
  times=()
  i=0
  while [ "$i" -lt "$runs" ]; do
    i=$((i + 1))
    start=$(now_us)
    code=0
    jdoc "$@" > /dev/null 2> "$work/last.err" || code=$?
    end=$(now_us)
    if [ "$code" -gt 1 ]; then
      echo "error: '$line' exited with $code:" >&2
      cat "$work/last.err" >&2
      status=1
    fi
    times+=($(( (end - start) / 1000 )))
  done
  sorted=$(printf '%s\n' "${times[@]}" | sort -n)
  median=$(echo "$sorted" | sed -n "$(( (runs + 1) / 2 ))p")
  min=$(echo "$sorted" | head -n 1)
  max=$(echo "$sorted" | tail -n 1)
  printf '%8s %8s %8s  %s\n' "$median" "$min" "$max" "$line"
done <<< "$commands"
exit $status
