#!/usr/bin/env bash
# Prints the GitHub release notes of one version: its CHANGELOG.md section, then the install and verify steps.
#
# Usage: scripts/release-notes.sh <version>
#
# Fails if CHANGELOG.md has no non-empty "## [<version>]" section, so a release cannot go out without notes.
set -euo pipefail

die() {
  echo "release-notes.sh: $*" >&2
  exit 1
}

[ $# -eq 1 ] || die "usage: scripts/release-notes.sh <version>"
version=$1
[[ $version =~ ^[0-9A-Za-z][0-9A-Za-z.+_-]*$ ]] || die "invalid version: $version"

root=$(cd "$(dirname "$0")/.." && pwd)
changelog=$root/CHANGELOG.md
[ -f "$changelog" ] || die "$changelog not found"

# The lines after "## [<version>]" up to the next "## " heading or the link references, without blank edges.
section=$(awk -v heading="## [$version]" '
  index($0, heading) == 1 { found = 1; next }
  found && (/^## / || /^\[[^]]+\]: /) { exit }
  found { print }
' "$changelog" | sed -e '/./,$!d' | tac | sed -e '/./,$!d' | tac)
[ -n "$section" ] || die "CHANGELOG.md has no section for $version"

cat << EOF
$section

### Install

Download \`jdoc-$version.tar.gz\` and \`SHA256SUMS\`, then follow the
[install steps](https://github.com/vecluu/jdoc/blob/v$version/README.md#install):

\`\`\`sh
gh release download v$version -R vecluu/jdoc
gh attestation verify jdoc-$version.tar.gz -R vecluu/jdoc
sha256sum -c SHA256SUMS
tar xzf jdoc-$version.tar.gz
sudo ./jdoc-$version/install.sh --skills-dir <opencode skills dir>
\`\`\`

Full history: [CHANGELOG.md](https://github.com/vecluu/jdoc/blob/v$version/CHANGELOG.md).
EOF
