#!/usr/bin/env bash
# Installs jdoc from the extracted release bundle (VERSION, install.sh, bin/jdoc, lib/jdoc.jar, skill/SKILL.md,
# LICENSE, NOTICE, THIRD-PARTY-NOTICES; the licence files are copied to DIR/share/doc/jdoc/).
#
# Usage: install.sh [--prefix DIR] [--bindir DIR] [--skills-dir DIR] [--no-aot]
#
#   --prefix DIR      install root (default /opt/jdoc): DIR/lib/jdoc.jar, DIR/lib/native/, DIR/lib/jdoc.aot
#   --bindir DIR      where the `jdoc` launcher goes (default /usr/local/bin)
#   --skills-dir DIR  also install the opencode skill as DIR/jdoc/SKILL.md
#   --no-aot          skip training the AOT cache (the launcher then uses its per-user fallback archive)
#
# Needs Java 25 on PATH (the same `java` the launcher will use). Runs offline. Besides copying the jar it
#   1. extracts the sqlite native library for this machine once, so no call extracts it to /tmp;
#   2. trains a JDK AOT cache with the two-step record/create workflow on throwaway modules with a fake `mvn` and a
#      temporary JDOC_CACHE (no network, no real Maven, the user's cache untouched): the JDK, then a tiny library
#      generated beforehand in its own JVM (with sources, javadoc only, binary only) in a temporary local repository;
#      a failed attempt (e.g. a JVM crash) is retried once; a fallback prints one warning line with its reason;
#   3. writes the cache's fingerprint (JVM flags, `stat` of the jar, java binary and JDK image) next to it.
# Everything produced lives under the prefix. Re-running the installer rebuilds all of it.
set -euo pipefail

# JVM flags of every call; the AOT cache is trained with exactly these.
jvm_flags='-XX:TieredStopAtLevel=1 -XX:+UseSerialGC -XX:-UsePerfData'

# Training workload: representative commands, run in one JVM against the JDK (the first one indexes it).
workload='index
--version
--help
method java.util.Optional#orElseThrow
method java.util.List#of
class java.util.Optional
class java.util.HashMap
class Duration
class does.not.Exist
find HashMap
find HSM
member orElseThrow
package java.util
impls java.util.List
source java.util.Optional#orElseThrow
source java.util.ArrayList#add
index'

# Library workload, run after the JDK one when the training library could be generated: the first call resolves the
# second module and indexes its three new jars (sources jar, javadoc jar, binary only), as after adding a dependency.
# shellcheck disable=SC2016 # Widget$Builder is a nested class name, not an expansion
library_workload='class jdoc.training.sources.Widget --from ../library
class jdoc.training.javadoc.Widget --from ../library
class jdoc.training.binary.Widget --from ../library
class jdoc.training.sources.Widget$Builder --from ../library
method jdoc.training.sources.Widget#resize --from ../library
method jdoc.training.javadoc.Widget#resize --from ../library
method jdoc.training.binary.Widget#resize --from ../library
method jdoc.training.javadoc.Gadget#labels --from ../library
method jdoc.training.sources.Part#withWeight --from ../library
find Widget --from ../library
find WiBu --from ../library
member resize --from ../library
member label --from ../library
package jdoc.training.sources --from ../library
package jdoc.training.javadoc --from ../library
package jdoc.training.binary --from ../library
impls jdoc.training.javadoc.Gadget --from ../library
source jdoc.training.sources.Widget#resize --from ../library
source jdoc.training.javadoc.Widget#add --from ../library
source jdoc.training.binary.Widget#resize --from ../library
source jdoc.training.binary.Mode#round --from ../library
index --from ../library'

die() {
  echo "install.sh: $*" >&2
  exit 1
}
warn() { echo "install.sh: warning: $*" >&2; }

usage() { sed -n '5,10s/^# \{0,1\}//p' "$0"; }

prefix=/opt/jdoc
bindir=/usr/local/bin
skills=
train=1
while [ $# -gt 0 ]; do
  case $1 in
    --prefix) [ $# -ge 2 ] || die "--prefix needs a value"; prefix=$2; shift 2 ;;
    --prefix=*) prefix=${1#*=}; shift ;;
    --bindir) [ $# -ge 2 ] || die "--bindir needs a value"; bindir=$2; shift 2 ;;
    --bindir=*) bindir=${1#*=}; shift ;;
    --skills-dir) [ $# -ge 2 ] || die "--skills-dir needs a value"; skills=$2; shift 2 ;;
    --skills-dir=*) skills=${1#*=}; shift ;;
    --no-aot) train=0; shift ;;
    -h | --help) usage; exit 0 ;;
    *) usage >&2; die "unknown option: $1" ;;
  esac
done

here=$(cd "$(dirname "$0")" && pwd)
if [ ! -f "$here/lib/jdoc.jar" ] || [ ! -f "$here/bin/jdoc" ]; then
  die "lib/jdoc.jar or bin/jdoc missing next to install.sh; run it from the extracted release bundle"
fi

java=$(command -v java) || die "jdoc needs Java 25 on PATH"
java_version=$("$java" -version 2>&1) || true
case $java_version in
  *'version "25'*) ;;
  *) die "jdoc needs Java 25 on PATH, found: ${java_version%%$'\n'*}" ;;
esac
java_real=$(readlink -f "$java")
java_home=$(cd "$(dirname "$java_real")/.." && pwd)

mkdir -p "$prefix" "$bindir"
prefix=$(cd "$prefix" && pwd)
bindir=$(cd "$bindir" && pwd)
lib=$prefix/lib
mkdir -p "$lib"

work=$(mktemp -d "${TMPDIR:-/tmp}/jdoc-install.XXXXXX")
trap 'rm -rf "$work"' EXIT

# Single-quote a string for sh.
quote() { printf "'%s'" "$(printf '%s' "$1" | sed "s/'/'\\\\''/g")"; }
# Escape a string for the replacement part of a sed s|...|...| command.
sed_escape() { printf '%s' "$1" | sed 's/[\\&|]/\\&/g'; }
stat_key() { stat -L -c '%s %Y %i' "$lib/jdoc.jar" "$java" "$java_home/release" "$java_home/lib/modules" 2>/dev/null || true; }
# The line of a log that says what went wrong: an `error:` line (jdoc, the training library generator), a JVM crash's
# signal or error line (`#  SIGSEGV (0xb) at pc=...`), an exception; else its first line.
first_error() {
  { grep -m 1 -E '^error:|^#  [A-Za-z]|^Exception|^Error' "$1" || grep -m 1 . "$1" || echo "no output"; } 2> /dev/null \
    | sed 's/^#  *//'
}
# Records the training workload in a fresh JVM and creates the cache as jdoc.aot.tmp from it; the arguments are extra
# JVM flags of the recording run. Starts from an empty jdoc cache, so every attempt indexes the JDK and the library.
train_cache() {
  rm -rf "$work/cache" "$work/jdoc.aotconf" "$lib/jdoc.aot.tmp"
  mkdir -p "$work/cache"
  # shellcheck disable=SC2086 # jvm_flags is a list of words on purpose
  (cd "$work/module" \
    && env JDOC_CACHE="$work/cache" JDOC_M2_REPO="$work/m2" JDOC_MVN="$work/bin/mvn" JAVA_HOME="$java_home" \
      "$java" $jvm_flags "${sqlite_props[@]}" "$@" -XX:AOTMode=record -XX:AOTConfiguration="$work/jdoc.aotconf" \
      -Djdoc.aotTraining="$work/workload.txt" -jar "$lib/jdoc.jar" > "$work/train.log" 2>&1 \
    && "$java" $jvm_flags "${sqlite_props[@]}" -XX:AOTMode=create -XX:AOTConfiguration="$work/jdoc.aotconf" \
      -XX:AOTCache="$lib/jdoc.aot.tmp" -jar "$lib/jdoc.jar" >> "$work/train.log" 2>&1) \
    && [ -s "$lib/jdoc.aot.tmp" ]
}

# 1. The jar. Drop the old cache first so no call pairs it with the new jar.
rm -f "$lib/jdoc.aot" "$lib/jdoc.aot.key" "$lib/jdoc.aot.tmp"
cp "$here/lib/jdoc.jar" "$lib/jdoc.jar.new"
chmod 644 "$lib/jdoc.jar.new"
mv -f "$lib/jdoc.jar.new" "$lib/jdoc.jar"
if [ -f "$here/VERSION" ]; then
  cp "$here/VERSION" "$prefix/VERSION"
  chmod 644 "$prefix/VERSION"
fi
# Licence files travel with the jar.
for f in LICENSE NOTICE THIRD-PARTY-NOTICES; do
  if [ -f "$here/$f" ]; then
    mkdir -p "$prefix/share/doc/jdoc"
    cp "$here/$f" "$prefix/share/doc/jdoc/$f"
    chmod 644 "$prefix/share/doc/jdoc/$f"
  fi
done

# 2. The sqlite native library for this OS and architecture, as sqlite-jdbc itself names the folder.
rm -rf "$lib/native"
native_ok=0
if folder=$("$java" -cp "$lib/jdoc.jar" org.sqlite.util.OSInfo 2> /dev/null) && [ -n "$folder" ]; then
  for name in libsqlitejdbc.so libsqlitejdbc.dylib; do
    entry=org/sqlite/native/$folder/$name
    mkdir -p "$work/native"
    if command -v unzip > /dev/null 2>&1; then
      unzip -p "$lib/jdoc.jar" "$entry" > "$work/native/lib" 2> /dev/null || true
    elif [ -x "$java_home/bin/jar" ]; then
      (cd "$work/native" && "$java_home/bin/jar" xf "$lib/jdoc.jar" "$entry" && mv -f "$entry" lib) 2> /dev/null || true
    fi
    if [ -s "$work/native/lib" ]; then
      mkdir -p "$lib/native"
      cp "$work/native/lib" "$lib/native/libsqlitejdbc.so"
      chmod 644 "$lib/native/libsqlitejdbc.so"
      native_ok=1
      break
    fi
  done
fi
[ "$native_ok" = 1 ] || warn "could not extract the sqlite native library; each call will extract it to the temp dir"
sqlite_props=()
if [ "$native_ok" = 1 ]; then
  sqlite_props=("-Dorg.sqlite.lib.path=$lib/native" -Dorg.sqlite.lib.name=libsqlitejdbc.so)
fi

# 3. The launcher.
sed -e "s|@LIB@|$(sed_escape "$(quote "$lib")")|" -e "s|@JVM_FLAGS@|$(sed_escape "$jvm_flags")|" \
  "$here/bin/jdoc" > "$bindir/jdoc.new"
chmod 755 "$bindir/jdoc.new"
mv -f "$bindir/jdoc.new" "$bindir/jdoc"

# 4. AOT cache: record a training run, create the cache, then store its fingerprint.
if [ "$train" = 1 ]; then
  mkdir -p "$work/module" "$work/cache" "$work/m2" "$work/bin"
  cat > "$work/module/pom.xml" << 'EOF'
<project>
  <modelVersion>4.0.0</modelVersion>
  <groupId>jdoc.training</groupId>
  <artifactId>training</artifactId>
  <version>1</version>
</project>
EOF
  cat > "$work/bin/mvn" << 'EOF'
#!/bin/sh
# Fake mvn for the training run: the module's deps.list if it has one, else an empty dependency list.
out=
for a in "$@"; do case "$a" in -DoutputFile=*) out="${a#-DoutputFile=}" ;; esac; done
if [ -n "$out" ]; then
  if [ -f deps.list ]; then cp deps.list "$out"; else : > "$out"; fi
fi
exit 0
EOF
  chmod 755 "$work/bin/mvn"
  printf '%s\n' "$workload" > "$work/workload.txt"

  echo "training the AOT cache (indexes the JDK and a small library once, takes a few seconds)..."
  # The training library is compiled and documented in its own JVM, so javac and javadoc classes stay out of the
  # recording. Without it (e.g. a runtime without the javadoc tool) only the JDK workload is trained.
  mkdir -p "$work/library"
  trained_on="the JDK"
  # shellcheck disable=SC2086 # jvm_flags is a list of words on purpose
  if "$java" $jvm_flags -cp "$lib/jdoc.jar" io.github.vecluu.jdoc.cli.TrainingLibrary "$work/m2" \
    "$work/library/deps.list" > "$work/library.log" 2>&1; then
    cat > "$work/library/pom.xml" << 'EOF'
<project>
  <modelVersion>4.0.0</modelVersion>
  <groupId>jdoc.training</groupId>
  <artifactId>library</artifactId>
  <version>1</version>
  <dependencies>
    <dependency><groupId>jdoc.training</groupId><artifactId>training-sources</artifactId><version>1.0</version></dependency>
    <dependency><groupId>jdoc.training</groupId><artifactId>training-javadoc</artifactId><version>1.0</version></dependency>
    <dependency><groupId>jdoc.training</groupId><artifactId>training-binary</artifactId><version>1.0</version></dependency>
  </dependencies>
</project>
EOF
    printf '%s\n' "$library_workload" >> "$work/workload.txt"
    trained_on="the JDK and the training library"
  else
    reason=$(first_error "$work/library.log")
    warn "could not generate the training library ($reason); the AOT cache covers JDK lookups only"
  fi
  # The JVM can crash while it creates the cache (seen once with JDK 25.0.5: SIGSEGV in
  # ArchiveBuilder::make_training_data_shareable, not reproducible in 200+ runs with the same inputs), so a failed
  # attempt is retried once with a fresh recording. The retry leaves out the training data (JEP 515 method profiles
  # and class-init records) that the crash was processing; it costs 2-3 ms per lookup, so the first attempt keeps it.
  trained=0
  if train_cache; then
    trained=1
  else
    echo "install.sh: AOT training failed ($(first_error "$work/train.log")); retrying once without method profiles"
    if train_cache -XX:+UnlockDiagnosticVMOptions -XX:-AOTRecordTraining; then
      trained=1
    fi
  fi
  if [ "$trained" = 1 ]; then
    chmod 644 "$lib/jdoc.aot.tmp"
    {
      echo "# Fingerprint of jdoc.aot, written by install.sh on $(date -u +%Y-%m-%dT%H:%M:%SZ); sourced by bin/jdoc."
      echo "# The cache is used only while all values below still match. Trained with:"
      echo "#   java: $java -> $java_real"
      printf '%s\n' "$java_version" | sed 's/^/#   /'
      echo "#   jar: $lib/jdoc.jar"
      echo "aot_flags=$(quote "$jvm_flags")"
      echo "aot_home=$(quote "$java_home")"
      echo "aot_stat=$(quote "$(stat_key)")"
    } > "$lib/jdoc.aot.key.tmp"
    chmod 644 "$lib/jdoc.aot.key.tmp"
    mv -f "$lib/jdoc.aot.tmp" "$lib/jdoc.aot"
    mv -f "$lib/jdoc.aot.key.tmp" "$lib/jdoc.aot.key"
    echo "AOT cache: $lib/jdoc.aot ($(($(stat -c %s "$lib/jdoc.aot") / 1048576)) MB, trained on $trained_on)"
  else
    rm -f "$lib/jdoc.aot.tmp"
    reason=$(first_error "$work/train.log")
    warn "AOT training failed twice ($reason); jdoc works, but starts slower. Last lines of the training log:"
    tail -n 20 "$work/train.log" >&2 || true
  fi
fi

# 5. The opencode skill.
if [ -n "$skills" ]; then
  if [ -f "$here/skill/SKILL.md" ]; then
    mkdir -p "$skills/jdoc"
    cp "$here/skill/SKILL.md" "$skills/jdoc/SKILL.md"
    chmod 644 "$skills/jdoc/SKILL.md"
  else
    warn "skill/SKILL.md not in the bundle; skill not installed"
  fi
fi

version=unknown
[ -f "$here/VERSION" ] && version=$(cat "$here/VERSION")
echo "installed jdoc $version: $bindir/jdoc -> $lib/jdoc.jar"
