#!/usr/bin/env bash
# Prints the cache key for the pictures of a commit, for the Pictures workflow.
#
#   .github/scripts/pictures-key.sh <commit>
#
# The key names everything the pictures are drawn from: the commit's app, legal documents and
# Gradle files, and how this checkout draws them (its workflow and drawing script). Two commits
# with the same key draw the same pictures, so main's pictures serve every pull request on it.
set -euo pipefail

COMMIT="$1"
{
  for path in app legal gradle build.gradle.kts settings.gradle.kts gradle.properties; do
    echo "$path $(git rev-parse "$COMMIT:$path" 2>/dev/null || echo none)"
  done
  sha256sum .github/workflows/pictures.yml .github/scripts/draw-pictures.sh
} | sha256sum | cut -c1-40 | sed 's/^/pictures-v1-/'
