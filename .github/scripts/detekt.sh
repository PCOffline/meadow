#!/usr/bin/env bash
# Runs detekt on the app's Kotlin with config/detekt.yml: likely bugs only, no style.
# Downloads the pinned detekt CLI once and checks its checksum. Needs Java 21 or newer.
set -euo pipefail

VERSION=2.0.0-alpha.6
SHA256=d46ca62ea4d62769b5d5c3ba94d49fa9b80ba11c7dba74ddb6df7fcc2c19c5fd
JAR="${XDG_CACHE_HOME:-$HOME/.cache}/meadow/detekt-cli-$VERSION-all.jar"
ROOT=$(cd "$(dirname "$0")/../.." && pwd)

if ! echo "$SHA256  $JAR" | sha256sum --check --quiet 2>/dev/null; then
  mkdir -p "$(dirname "$JAR")"
  curl -sSfLo "$JAR" "https://repo1.maven.org/maven2/dev/detekt/detekt-cli/$VERSION/detekt-cli-$VERSION-all.jar"
  echo "$SHA256  $JAR" | sha256sum --check --quiet
fi

java -jar "$JAR" --input "$ROOT/app/src" --config "$ROOT/config/detekt.yml" --build-upon-default-config "$@"
