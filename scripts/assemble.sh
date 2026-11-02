#!/usr/bin/env bash
# Builds the release bundle out/jdoc-<version>.tar.gz and out/SHA256SUMS from an already built jar.
#
# Usage: scripts/assemble.sh <version>
#
# Environment:
#   JDOC_JAR          the shaded jar to bundle (default target/jdoc.jar)
#   SOURCE_DATE_EPOCH timestamp of every tar entry (default: the last commit's time)
#
# The jar must have been built with -Drevision=<version>; this is checked against its `--version`. The tarball is
# reproducible for a given jar: fixed entry order, owner, permissions and timestamps, gzip without name or time.
# Nothing trained or extracted on this machine goes into the bundle: install.sh builds the AOT cache and extracts
# the sqlite library on the user's machine.
set -euo pipefail

die() {
  echo "assemble.sh: $*" >&2
  exit 1
}

[ $# -eq 1 ] || die "usage: scripts/assemble.sh <version>"
version=$1
[[ $version =~ ^[0-9A-Za-z][0-9A-Za-z.+_-]*$ ]] || die "invalid version: $version"

root=$(cd "$(dirname "$0")/.." && pwd)
cd "$root"
jar=${JDOC_JAR:-target/jdoc.jar}
[ -f "$jar" ] || die "$jar not found; build it first: ./mvnw -B -Drevision=$version verify"

jar_version=$(java -jar "$jar" --version) || die "$jar --version failed"
[ "$jar_version" = "jdoc $version" ] \
  || die "$jar reports '$jar_version', expected 'jdoc $version'; rebuild with -Drevision=$version"

if [ -z "${SOURCE_DATE_EPOCH:-}" ]; then
  SOURCE_DATE_EPOCH=$(git log -1 --format=%ct 2> /dev/null) || die "not a git checkout; set SOURCE_DATE_EPOCH"
fi
export SOURCE_DATE_EPOCH

name=jdoc-$version
out=$root/out
stage=$out/$name
rm -rf "$out"
mkdir -p "$stage/bin" "$stage/lib" "$stage/skill" "$stage/training"

install -m 644 "$jar" "$stage/lib/jdoc.jar"
install -m 755 dist/bin/jdoc "$stage/bin/jdoc"
install -m 755 dist/install.sh "$stage/install.sh"
install -m 644 dist/AGENTS-snippet.md LICENSE NOTICE THIRD-PARTY-NOTICES "$stage/"
install -m 644 dist/skill/SKILL.md "$stage/skill/SKILL.md"
install -m 755 dist/training/fake-mvn "$stage/training/fake-mvn"
printf '%s\n' "$version" > "$stage/VERSION"
chmod 644 "$stage/VERSION"
chmod 755 "$stage" "$stage/bin" "$stage/lib" "$stage/skill" "$stage/training"

tar -C "$out" --sort=name --format=gnu --mtime="@$SOURCE_DATE_EPOCH" \
  --owner=0 --group=0 --numeric-owner --mode='go-w' -cf - "$name" \
  | gzip -9n > "$out/$name.tar.gz"
rm -rf "$stage"
(cd "$out" && sha256sum "$name.tar.gz" > SHA256SUMS)

echo "assembled out/$name.tar.gz ($(($(stat -c %s "$out/$name.tar.gz") / 1024)) KiB)"
cat "$out/SHA256SUMS"
