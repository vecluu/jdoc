#!/usr/bin/env bash
# Checks the release bundle the way a user installs it: verify, extract, run install.sh into temporary
# directories, then call the installed launcher.
#
# Usage: scripts/install-test.sh <out/jdoc-<version>.tar.gz> [<maven project dir>]
#
# Checks: SHA256SUMS (if next to the tarball); tar entries (one top directory, owner 0, the expected file list,
# nothing trained or extracted, licence files at the root); install.sh with a prefix containing a space, a bindir
# and a skills dir; installed files (jar, sqlite library, AOT cache and key, VERSION, licence files, skill); the
# launcher's `--version` and that it picks the AOT cache, also when run by dash; with a project, the smoke test
# through the launcher; finally the silent fallback to the per-user CDS archive after the jar changed.
#
# Environment:
#   INSTALL_TEST_REQUIRE_DASH  if 1, fail when dash is not installed (CI); otherwise the dash checks are skipped
#   plus everything scripts/smoke-test.sh reads (SMOKE_M2_REPO, JDOC_MVN)
# Never touches ~/.cache/jdoc, ~/.m2 or system directories.
set -euo pipefail

die() {
  echo "install-test.sh: FAIL: $*" >&2
  exit 1
}
ok() { echo "ok: $*"; }

if [ $# -lt 1 ] || [ $# -gt 2 ]; then
  die "usage: scripts/install-test.sh <jdoc-<version>.tar.gz> [<maven project dir>]"
fi
tarball=$(realpath "$1")
project=${2:+$(realpath "$2")}
scripts=$(cd "$(dirname "$0")" && pwd)
[ -f "$tarball" ] || die "$tarball not found"
file=$(basename "$tarball")
name=${file%.tar.gz}
version=${name#jdoc-}
if [ "$name" = "$file" ] || [ "$version" = "$name" ]; then die "expected a file named jdoc-<version>.tar.gz"; fi
started=$(date +%s)

work=$(mktemp -d "${TMPDIR:-/tmp}/jdoc-install-test.XXXXXX")
trap 'rm -rf "$work"' EXIT
# Every launcher call uses a temporary cache (the fallback CDS archive would otherwise go to ~/.cache/jdoc).
export JDOC_CACHE=$work/cache

# 1. Checksum.
sums=$(dirname "$tarball")/SHA256SUMS
if [ -f "$sums" ]; then
  (cd "$(dirname "$tarball")" && sha256sum --quiet -c SHA256SUMS) || die "SHA256SUMS does not match"
  ok "SHA256SUMS"
fi

# 2. Tarball contents.
expected="$name/
$name/AGENTS-snippet.md
$name/LICENSE
$name/NOTICE
$name/THIRD-PARTY-NOTICES
$name/VERSION
$name/bin/
$name/bin/jdoc
$name/install.sh
$name/lib/
$name/lib/jdoc.jar
$name/skill/
$name/skill/SKILL.md"
actual=$(tar tzf "$tarball")
[ "$actual" = "$expected" ] || die "unexpected tarball entries:
$actual"
owners=$(tar tvzf "$tarball" --numeric-owner | awk '{print $2}' | sort -u)
[ "$owners" = "0/0" ] || die "tar entries not owned by 0/0: $owners"
ok "tarball entries and owners"

tar -C "$work" -xzf "$tarball"
bundle=$work/$name
[ "$(cat "$bundle/VERSION")" = "$version" ] || die "VERSION is '$(cat "$bundle/VERSION")', expected $version"
if [ ! -x "$bundle/install.sh" ] || [ ! -x "$bundle/bin/jdoc" ]; then die "install.sh or bin/jdoc not executable"; fi
for f in LICENSE NOTICE THIRD-PARTY-NOTICES; do
  [ -s "$bundle/$f" ] || die "$f missing or empty at the bundle root"
done
grep -q 'Apache License' "$bundle/LICENSE" || die "LICENSE is not the Apache licence"
ok "bundle VERSION, permissions and licence files"

# 3. Install, the way the user does (prefix with a space, as /opt paths may have none but $HOME paths might).
prefix="$work/opt dir/jdoc"
bindir=$work/bin
skills=$work/skills
t0=$(date +%s)
bash "$bundle/install.sh" --prefix "$prefix" --bindir "$bindir" --skills-dir "$skills" > "$work/install.log" 2>&1 \
  || {
    cat "$work/install.log" >&2
    die "install.sh failed"
  }
t1=$(date +%s)
grep -q 'warning' "$work/install.log" && {
  cat "$work/install.log" >&2
  die "install.sh printed a warning"
}
for f in lib/jdoc.jar lib/native/libsqlitejdbc.so lib/jdoc.aot lib/jdoc.aot.key VERSION \
  share/doc/jdoc/LICENSE share/doc/jdoc/NOTICE share/doc/jdoc/THIRD-PARTY-NOTICES; do
  [ -s "$prefix/$f" ] || die "$prefix/$f missing after install"
done
cmp -s "$bundle/lib/jdoc.jar" "$prefix/lib/jdoc.jar" || die "installed jar differs from the bundle's"
cmp -s "$bundle/skill/SKILL.md" "$skills/jdoc/SKILL.md" || die "skill not installed"
[ -x "$bindir/jdoc" ] || die "launcher not installed"
if grep -q '@[A-Z_]*@' "$bindir/jdoc"; then die "launcher still has template placeholders"; fi
ok "install.sh in $((t1 - t0)) s"

# 4. The installed launcher.
out=$(JDOC_WRAPPER_DEBUG=1 "$bindir/jdoc" --version 2> "$work/stderr") || die "jdoc --version failed"
[ "$out" = "jdoc $version" ] || die "jdoc --version printed '$out', expected 'jdoc $version'"
grep -q '^jdoc wrapper: aot cache ' "$work/stderr" || die "launcher did not use the AOT cache: $(cat "$work/stderr")"
ok "installed launcher: $out, AOT cache"
smoke_shell=
if command -v dash > /dev/null; then
  out=$(JDOC_WRAPPER_DEBUG=1 dash "$bindir/jdoc" --version 2> "$work/stderr") || die "dash: jdoc --version failed"
  [ "$out" = "jdoc $version" ] || die "dash: jdoc --version printed '$out'"
  grep -q '^jdoc wrapper: aot cache ' "$work/stderr" || die "dash: no AOT cache: $(cat "$work/stderr")"
  ok "launcher under dash"
  smoke_shell=dash
elif [ "${INSTALL_TEST_REQUIRE_DASH:-}" = 1 ]; then
  die "dash not installed"
else
  echo "skip: dash not installed, launcher not tested under dash"
fi

# 5. The full smoke test through the installed launcher.
if [ -n "$project" ]; then
  SMOKE_SHELL=$smoke_shell SMOKE_EXPECT_VERSION=$version "$scripts/smoke-test.sh" "$bindir/jdoc" "$project"
fi

# 6. Silent fallback: a jar newer than the AOT cache's fingerprint makes the launcher use the per-user CDS archive.
touch -d 2000-01-01T00:00:00Z "$prefix/lib/jdoc.jar"
launcher=("$bindir/jdoc")
[ -z "$smoke_shell" ] || launcher=("$smoke_shell" "$bindir/jdoc")
for round in 1 2; do
  out=$(JDOC_WRAPPER_DEBUG=1 "${launcher[@]}" --version 2> "$work/stderr") || die "fallback: jdoc --version failed"
  [ "$out" = "jdoc $version" ] || die "fallback: jdoc --version printed '$out'"
  [ "$(cat "$work/stderr")" = "jdoc wrapper: fallback archive $JDOC_CACHE/cds/jdoc.jsa" ] \
    || die "fallback round $round: unexpected stderr: $(cat "$work/stderr")"
done
[ -s "$JDOC_CACHE/cds/jdoc.jsa" ] || die "fallback archive not created"
ok "stale fingerprint falls back silently to $JDOC_CACHE/cds/jdoc.jsa${smoke_shell:+ (dash)}"
echo "install test passed in $(($(date +%s) - started)) s"
