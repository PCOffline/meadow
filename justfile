# Meadow's everyday commands. Run `just` to list them.
# `mise install` sets up Java, Node and just itself; `just setup` does the rest.

# The Firebase CLI pinned in backend/package.json, run with the Node the functions need.
firebase := "npx firebase"

[private]
default:
    @just --list

# Install the tools and every dependency
[group('setup')]
setup:
    mise install
    cd backend && npm ci && npm --prefix functions ci

# Choose the Firebase project to deploy to (writes backend/.firebaserc)
[group('setup')]
use-project:
    cd backend && {{ firebase }} use --add

# Build a debug APK
[group('app')]
build:
    ./gradlew assembleDebug

# Build a release APK, signed with the MEADOW_KEYSTORE_* variables if they are set
[group('app')]
build-release version="":
    ./gradlew assembleRelease {{ if version != "" { "-Pmeadow.version=" + version } else { "" } }}

# Install a debug build on the connected phone or emulator
[group('app')]
install:
    ./gradlew installDebug

# Install a debug build that logs every request and shows error causes (docs/debugging.md)
[group('app')]
install-trace:
    ./gradlew installDebug -Pmeadow.trace=true

# Install a traced debug build that talks to the local emulators (10.0.2.2 is this computer, from an Android emulator)
[group('app')]
install-local host="10.0.2.2":
    ./gradlew installDebug -Pmeadow.emulatorHost={{ host }} -Pmeadow.trace=true

# Follow the app's log
[group('app')]
logs:
    adb logcat -s MeadowTrace MeadowSync MeadowBackend MeadowFirestore MeadowSocial MeadowListener MeadowGuardian

# Follow the app's log plus the Firestore SDK's own (trace builds; very verbose)
[group('app')]
logs-firestore:
    adb logcat -s MeadowTrace Firestore

# Android lint and every test suite (CI also runs the end-to-end tests)
[group('test')]
check: lint test

# All unit, rules, integration and hosting tests
[group('test')]
test: test-app test-functions test-rules test-integration test-hosting test-deploy

# App unit, screen and accessibility tests
[group('test')]
test-app:
    ./gradlew testDebugUnitTest

# Android lint, detekt, and Biome and oxlint (type-aware) on the backend
[group('test')]
lint:
    ./gradlew lintDebug
    .github/scripts/detekt.sh
    cd backend/functions && npm run lint
    cd backend && npm run lint

# Cloud Functions unit tests
[group('test')]
test-functions:
    cd backend/functions && npm test

# Firestore and Storage security rules, on the emulators
[group('test')]
test-rules:
    cd backend && npm run test:rules

# Cloud Functions on the emulators
[group('test')]
test-integration:
    cd backend && npm run test:integration

# The hosted legal pages and app links
[group('test')]
test-hosting:
    cd backend && npm run test:hosting

# What the deploy does besides the Firebase CLI (the Storage lifecycle rule)
[group('test')]
test-deploy:
    cd backend && npm run test:deploy

# End to end on a running Android emulator; start `just emulators` in another terminal first
[group('test')]
test-e2e:
    ./gradlew connectedDebugAndroidTest -Pmeadow.emulatorHost=10.0.2.2

# Run the whole backend locally, with demo people; data is kept between runs (`just emulators --fresh` starts over)
[group('backend')]
emulators *args:
    cd backend && npm run emulators -- {{ args }}

# Deploy everything: rules, indexes, functions, hosting and the Storage lifecycle rule
[group('backend')]
deploy:
    cd backend && {{ firebase }} deploy

# Deploy only the Cloud Functions
[group('backend')]
deploy-functions:
    cd backend && {{ firebase }} deploy --only functions

# Deploy only the Firestore and Storage rules and indexes, and the Storage lifecycle rule
[group('backend')]
deploy-rules:
    cd backend && {{ firebase }} deploy --only firestore,storage

# Deploy only the hosted pages (legal pages, invite and sign-in links)
[group('backend')]
deploy-hosting:
    cd backend && {{ firebase }} deploy --only hosting

# Show the deployed functions' recent logs
[group('backend')]
functions-log:
    cd backend && {{ firebase }} functions:log

# Let GitHub Actions deploy to Firebase without a stored key (docs/self-hosting.md, 7.6)
[group('backend')]
deploy-access project repo:
    .github/scripts/setup-deploy-access.sh {{ project }} {{ repo }}

# Tag an app version and push the tag; the Deliver workflow builds, signs and publishes the APK
[group('release')]
release version:
    #!/usr/bin/env bash
    set -euo pipefail
    if ! [[ "{{ version }}" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
        echo "Use a version like 0.4.0, above the latest release" >&2
        exit 1
    fi
    if ! git diff --quiet || ! git diff --cached --quiet; then
        echo "Commit or stash your changes first" >&2
        exit 1
    fi
    git fetch --quiet origin main
    if [ "$(git rev-parse HEAD)" != "$(git rev-parse origin/main)" ]; then
        echo "Release from main as it is on GitHub: check out main and pull first" >&2
        exit 1
    fi
    git tag "v{{ version }}"
    git push origin "v{{ version }}"
