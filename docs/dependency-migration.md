# Dependency and runtime migration (October 2026)

Branch: `claude/dependency-upgrades`. Each step below is one commit. CI checked each commit
before the next one. The machine that prepared this can't reach Google Maven, so only CI
checked the Android builds.

## Inventory

Latest stable versions as of 12 October 2026.

### Runtimes and tools

| What | Was | Now | Notes |
|---|---|---|---|
| Java (build, tests, emulators) | 17 (app jobs), 21 (emulator jobs), 24 in `mise.toml` | 25 LTS on CI and in `mise.toml`; 21 is the minimum | Gradle 8.14 tops out at Java 24, so 25 needs Gradle 9.1+. AGP and Robolectric don't state a maximum; every CI job passes on 25. 21 stays supported because Android Studio bundles it. Java 24 is out of support since 2025-09 |
| Java bytecode target | 17 | 17 | Android 14 (API 34) supports Java 17 at most; 21 brings nothing on ART |
| Node (CI, local, Cloud Functions) | 22 | 24 LTS | `firebase@13` and `@firebase/rules-unit-testing@6` need Node ≥ 24.12. Cloud Functions has Node 24 as GA until 2028-04. Node 26 becomes LTS on 2026-10-28, but firebase-tools 15.33 can't deploy it and Cloud Functions only has it in preview |
| Gradle | 8.14.3 | 9.8.1 | Needed for AGP 9.4 and Java 25 |
| GitHub Actions | checkout v7, setup-java v6, setup-node v7, setup-gradle v6, upload-artifact v7, android-emulator-runner v2 | unchanged | All already on their latest major |

### Android (Gradle catalog)

| Artifact | Was | Now | Breaking changes that apply here |
|---|---|---|---|
| Android Gradle plugin | 8.10.1 | 9.4.0 | Built-in Kotlin: the `org.jetbrains.kotlin.android` plugin must go. Kotlin sources in extra source sets are added with `kotlin`, not `java`. Unit tests only exist for the tested build type, so `testReleaseUnitTest` is gone. `jvmTarget` defaults to `targetCompatibility` |
| Kotlin (compose, serialization plugins) | 2.3.21 | 2.4.21 | Needs AGP ≥ 9.1 (R8). K1 removed; no source changes found |
| compileSdk | 35 | 37 | Required by Compose 1.12, Coil 3.6 and the markdown renderer 0.42+. Needs AGP ≥ 9.2 |
| targetSdk | 35 | 36 | Not a dependency; moved separately (see *Done since*) |
| androidx.core | core-ktx 1.16.0 | core 1.19.1 | `core-ktx` is now empty; the extensions moved into `core` |
| activity-compose | 1.10.1 | 1.13.0 | Back handling rebuilt on NavigationEvent, compatible with `BackHandler` and `OnBackPressedCallback` |
| lifecycle | 2.9.0 | 2.11.0 | None used |
| navigation-compose | 2.9.0 | 2.10.2 | `handleDeepLink` ignores unknown links; not used (deep links go through `DeepLinks.toRoute`) |
| work | 2.10.1 | 2.12.0 | Default executor is `Dispatchers.Default`; no effect |
| Compose BOM | 2025.05.01 | 2026.09.00 | Material 3 1.4: the selected navigation bar label colour changes, so screenshots change. v1 test rules deprecated, still work. New lint errors: composables must read the locale and resources observably, so they use `LocalConfiguration` (`currentLocale()`) and `LocalResources` instead of `Locale.getDefault()` and `LocalContext.current.resources` |
| material-icons-extended | 1.7.8 | 1.7.8 | No longer updated; still in the BOM |
| Firebase BoM | 35.0.0 | 35.0.0 | Already latest |
| google-services plugin | 4.5.0 | 4.5.0 | Already latest; uses AGP's public API |
| kotlinx-coroutines | 1.10.2 | 1.11.0 | None used |
| kotlinx-serialization | 1.8.1 | 1.11.0 | None used |
| Coil | 3.2.0 | 3.6.3 | Needs Kotlin 2.4 and compileSdk 37; 3.6.3 fixes R8 with AGP 9.4 |
| konfetti-compose | 2.0.4 | 2.0.5 | `Shape.DrawableShape` now takes a `CoreImage`; drawables go through `ImageUtil.loadDrawable`. Fixes an idling-resource timeout in instrumentation tests |
| multiplatform-markdown-renderer-m3 | 0.34.0 | 0.45.0 | Only `Markdown(content, modifier)` is used; removed APIs don't apply |
| org.jetbrains:markdown (tests) | 0.7.3 | 0.7.17 | Parser fixes |
| Robolectric | 4.14.1 | 4.17 | Deprecated symbols removed (only `shadowOf` used). SDK 36+ needs Java 21; tests stay on SDK 34 |
| androidx.test core / ext / runner / orchestrator | 1.6.1 / 1.2.1 / 1.6.2 / 1.5.1 | 1.7.0 / 1.3.0 / 1.7.0 / 1.6.1 | None used |
| JUnit | 4.13.2 | 4.13.2 | Latest JUnit 4 |

### Backend (npm)

| Package | Was | Now | Breaking changes that apply here |
|---|---|---|---|
| typescript | 5.9.3 | 7.0.2 | Native compiler. Same output for this project. The package only exports its root, so `build-functions.cjs` can't `require.resolve("typescript/lib/tsc.js")` any more. `types` defaults to `[]`, so `@types/node` is now explicit |
| firebase-admin | 13.10.0 | 14.5.0 | Node ≥ 22. Legacy namespace API and legacy FCM types removed; none used. `sendEachForMulticast({ tokens })` deprecated in favour of installation IDs; still works |
| firebase-functions | 6.6.0 | 7.4.0 | Needed for admin 14. Removes `functions.config()` and changes v1 types; only v2 APIs are used |
| zod | 4.6.6 | 4.6.7 | Version bump only |
| marked | 18.0.14 | 18.1.1 | Fixes only; rendered legal pages are byte-identical |
| firebase, @firebase/rules-unit-testing, firebase-tools | 13.0.0, 6.0.0, 15.33.0 | unchanged | Already latest. The first two need Node ≥ 24.12; firebase-tools' emulators need Java ≥ 21 |
| @grpc/grpc-js (inside `firebase`, tests only) | 1.9.16 | 1.14.6 via `overrides` | 1.9 has two published advisories |

Sources, in the order of use:

1. Migration guides: AGP 9 and built-in Kotlin, Gradle 9 upgrading guides, the markdown
   renderer's `MIGRATION.md`, TypeScript 6.0 release notes.
2. Changelogs and release notes: AndroidX, Kotlin, Coil, Robolectric, kotlinx, Firebase Android
   and JS SDK, firebase-admin, firebase-functions, marked, zod.
3. Commit history, where no guide or changelog had the data: the Node 24 requirement of the
   Firebase JS SDK, and the SDK and JDK table of Robolectric.

## Steps

All five steps are done. After each step, all of CI passed: Android build, unit tests, lint, R8
release, rules, functions, emulator integration and end-to-end tests.

1. **Backend.** TypeScript 7, firebase-admin 14, firebase-functions 7, zod and marked. The
   functions run on Node 24. Fixes to the build hook and `tsconfig`. A grpc override.
2. **Build toolchain and runtimes.** Gradle 9.8.1, AGP 9.4.0 and Kotlin 2.4.21. The
   `kotlin-android` plugin and `jvmTarget` are removed. `sharedTest` is a Kotlin source
   directory. The release workflow runs `testDebugUnitTest`. All of CI uses Node 24 and Java 21.
   `mise.toml` and the docs have the new versions.
3. **Libraries.** compileSdk 37 with the AndroidX set, the Compose BOM, kotlinx, Coil, the
   markdown renderer, konfetti, Robolectric and androidx.test. CI then asked for two more
   changes: `CoreImage` shapes for konfetti, and locale and resource reads that Compose lint can
   observe.
4. **Java 25.** CI and `mise.toml` use Temurin 25. Gradle, AGP, R8, Robolectric and the Firebase
   emulators all pass on it. The docs keep 21 as the minimum.
5. **Screenshots.** The Screenshots workflow made them again for Material 3 1.4.

## Risks

- KGP 2.4.21 has tests up to Gradle 9.7 and AGP 9.3.1. Newer versions work, but can show
  warnings.
- AGP and Robolectric don't officially support Java 25. CI runs both on it, so CI shows a
  regression first.
- Semantics changes in Material 3 1.4 and Compose 1.9+ can change the results of screenshot and
  accessibility tests.

## Done since

- **Installation IDs, not FCM tokens.** The app registers with `FirebaseMessaging.register()`.
  The backend sends with `sendEachForMulticast({ fids })`.
- **targetSdk 36 (Android 16).** The Android 16 changes for apps that target it have no effect
  on Meadow:
  - The app is already edge-to-edge, with no opt-out.
  - It handles back only with `OnBackPressedCallback` and `BackHandler`. It doesn't use
    `onBackPressed` or `KEYCODE_BACK`.
  - It doesn't lock the orientation, the aspect ratio or the resizability.
  - It doesn't use `elegantTextHeight` or `scheduleAtFixedRate`.

  The changes for all apps (JobScheduler quotas, safer intent redirection) apply for all
  targets.

## Later

- **Node 26:** move when firebase-tools lists `nodejs26` and Cloud Functions makes it GA.
- **Material icons:** the extended set gets no more updates. Move to Material Symbols drawables
  at a good time.
- **Advisories in the dependencies of firebase-tools** (11, development only): wait for a new
  release of firebase-tools.
