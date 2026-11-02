#!/usr/bin/env bash
# Offline end-to-end check of jdoc against a real Maven project (normally it/sample-project).
#
# Usage: scripts/smoke-test.sh <jdoc.jar | installed jdoc launcher> <maven project dir>
#
# 1. Online, once: primes a private local repository with the project's jars and the Maven plugins it needs, plus
#    the sources jar of commons-lang3 and the javadoc jar of commons-codec. jspecify gets neither, so it is
#    decompiled. That is the state of an airgapped ~/.m2.
# 2. Offline: runs every jdoc command against that repository with a temporary JDOC_CACHE (jdoc itself calls
#    `mvn -o`), and checks exit codes 0/1/2/3, header lines and doc-source tags.
#
# Environment:
#   SMOKE_M2_REPO          local repository to prime and use (default: a temp dir, deleted afterwards)
#   SMOKE_SHELL            run the launcher with this shell, e.g. dash (ignored for a jar)
#   SMOKE_EXPECT_VERSION   if set, `jdoc --version` must print `jdoc <this>`
#   JDOC_MVN               Maven executable (default: mvn on PATH), used for priming and by jdoc
# Never touches ~/.cache/jdoc or ~/.m2.
set -euo pipefail

die() {
  echo "smoke-test.sh: $*" >&2
  exit 1
}

[ $# -eq 2 ] || die "usage: scripts/smoke-test.sh <jdoc.jar | jdoc launcher> <maven project dir>"
target=$(realpath "$1")
project=$(realpath "$2")
[ -f "$project/pom.xml" ] || die "no pom.xml in $project"
mvn=${JDOC_MVN:-mvn}
command -v "$mvn" > /dev/null || die "Maven not found ($mvn); put mvn on PATH or set JDOC_MVN"

case $target in
  *.jar) jdoc_cmd=(java -jar "$target") ;;
  *)
    [ -x "$target" ] || die "$target is neither a jar nor an executable launcher"
    if [ -n "${SMOKE_SHELL:-}" ]; then jdoc_cmd=("$SMOKE_SHELL" "$target"); else jdoc_cmd=("$target"); fi
    ;;
esac

work=$(mktemp -d "${TMPDIR:-/tmp}/jdoc-smoke.XXXXXX")
trap 'rm -rf "$work"' EXIT
repo=${SMOKE_M2_REPO:-$work/m2}
mkdir -p "$repo"
repo=$(realpath "$repo")
export JDOC_CACHE=$work/cache JDOC_M2_REPO=$repo JDOC_MVN=$mvn
started=$(date +%s)

# 1. Online priming.
prime() { "$mvn" -B -ntp -q -f "$project/pom.xml" "-Dmaven.repo.local=$repo" "$@"; }
echo "priming $repo"
prime dependency:go-offline
prime dependency:sources -DincludeArtifactIds=commons-lang3
prime dependency:resolve -Dclassifier=javadoc -DincludeArtifactIds=commons-codec
for f in org/apache/commons/commons-lang3/*/commons-lang3-*-sources.jar \
  commons-codec/commons-codec/*/commons-codec-*-javadoc.jar; do
  compgen -G "$repo/$f" > /dev/null || die "priming did not produce $f"
done
for f in commons-codec/commons-codec/*/commons-codec-*-sources.jar org/jspecify/jspecify/*/jspecify-*-sources.jar \
  org/jspecify/jspecify/*/jspecify-*-javadoc.jar; do
  if compgen -G "$repo/$f" > /dev/null; then die "$f must not be in the repository"; fi
done
primed=$(date +%s)

# 2. Offline checks.
checks=0
out=
err=$work/stderr
# jdoc <expected exit> <args...>: runs jdoc, keeps stdout in $out.
jdoc() {
  local want=$1 rc=0
  shift
  out=$("${jdoc_cmd[@]}" "$@" 2> "$err") || rc=$?
  if [ "$rc" != "$want" ]; then
    {
      echo "FAIL: jdoc $* exited $rc, expected $want"
      echo "--- stdout"
      printf '%s\n' "$out"
      echo "--- stderr"
      cat "$err"
    } >&2
    exit 1
  fi
  checks=$((checks + 1))
  echo "ok: jdoc $* (exit $rc)"
}
fail() {
  {
    echo "FAIL: $1"
    echo "--- stdout"
    printf '%s\n' "$out"
  } >&2
  exit 1
}
# contains <text>: the last output contains text.
contains() { [[ $out == *"$1"* ]] || fail "output does not contain '$1'"; }
# header <text>: the first output line contains text.
header() { [[ ${out%%$'\n'*} == *"$1"* ]] || fail "header does not contain '$1'"; }
# no_tag: the header has no doc-source tag (docs from sources or src.zip).
no_tag() { [[ ${out%%$'\n'*} != *'['* ]] || fail "header has an unexpected tag"; }

cd "$project"
jdoc 0 --help
for c in find class method source impls member package index; do contains "  $c "; done
contains "Exit codes: 0 found, 1 not found, 2 ambiguous, 3 error."
jdoc 0 method --help
contains "Usage: jdoc method"
jdoc 0 --version
[[ $out == "jdoc "* ]] || fail "unexpected version line"
if [ -n "${SMOKE_EXPECT_VERSION:-}" ]; then [ "$out" = "jdoc $SMOKE_EXPECT_VERSION" ] || fail "wrong version"; fi

jdoc 0 index
contains "(3 artifacts + jdk "
contains "types without sources:"
contains "commons-codec "
contains "jspecify "
[[ $out != *"commons-lang3 "* ]] || fail "commons-lang3 has a sources jar but is reported without sources"
jdoc 0 index --force

# JDK, from src.zip.
jdoc 0 method java.util.Optional#orElseThrow
header "(jdk "
no_tag
contains "NoSuchElementException"

# Sources jar.
jdoc 0 method org.apache.commons.lang3.StringUtils#isBlank
header "(commons-lang3 "
no_tag
contains "isBlank(CharSequence"
jdoc 0 class org.apache.commons.lang3.StringUtils
header "org.apache.commons.lang3.StringUtils"
contains "page 1/"
contains "next: "
jdoc 0 class org.apache.commons.lang3.StringUtils --page 2
jdoc 0 source org.apache.commons.lang3.StringUtils#isBlank
contains "return"
jdoc 0 package org.apache.commons.lang3
contains "StringUtils"
jdoc 0 impls org.apache.commons.lang3.builder.Builder
contains "org.apache.commons.lang3.builder.EqualsBuilder"
jdoc 0 member isBlank
contains "StringUtils#isBlank"
jdoc 0 find StringUtils
contains "org.apache.commons.lang3.StringUtils"
jdoc 0 find HashMap
contains "java.util.HashMap"

# Javadoc jar only.
jdoc 0 class org.apache.commons.codec.digest.DigestUtils
header "(commons-codec "
header "[from javadoc jar]"
jdoc 0 method 'org.apache.commons.codec.digest.DigestUtils#sha256Hex(String)'
header "[from javadoc jar]"
contains "sha256Hex"

# Binary jar only.
jdoc 0 class org.jspecify.annotations.Nullable
header "(jspecify "
header "[decompiled, no docs]"

# --from: the module comes from the file, not the working directory.
cd "$work"
jdoc 0 class org.apache.commons.lang3.StringUtils --from "$project/src/main/java/com/example/sample/Greeter.java"
header "(commons-lang3 "
cd "$project"

# Not found, ambiguous, usage error.
jdoc 1 class does.not.Exist
jdoc 1 method java.util.Optional#noSuchMethod
jdoc 2 class Duration
contains "java.time.Duration"
contains "javax.xml.datatype.Duration"
jdoc 2 class StringUtils
contains "org.apache.commons.codec.binary.StringUtils"
contains "org.apache.commons.lang3.StringUtils"
jdoc 3 nosuchcommand
jdoc 3 class java.util.Optional --page 0

[ ! -e "$project/target" ] || die "jdoc wrote into the project ($project/target)"
done=$(date +%s)
echo "smoke test passed: $checks jdoc calls; priming $((primed - started)) s, checks $((done - primed)) s"
