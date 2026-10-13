# Dependency and runtime migration (October 2026)

Branch: `claude/dependency-upgrades`. Each step below is one commit, verified by CI before the
next. Google Maven isn't reachable from the machine this was prepared on, so Android builds are
verified on CI only.

## Inventory

Latest stable versions as of 12 October 2026.

### Runtimes and tools

| What | Was | Now | Notes |
|---|---|---|---|
| Java (build, tests, emulators) | 17 (app jobs), 21 (emulator jobs), 24 in `mise.toml` | 25 LTS on CI and in `mise.toml`; 21 is the minimum | Gradle 8.14 tops out at Java 24, so 25 needs Gradle 9.1+. AGP and Robolectric don't state a maximum; every CI job passes on 25. 21 stays supported because Android Studio bundles it. Java 24 is out of support since 2025-09 |
| Java bytecode target | 17 | 17 | Android 14 (API 34) supports Java 17 at most; 21 brings nothing on ART |
| Node (CI, local, Cloud Functions) | 22 | 24 LTS | `firebase@13` and `@firebase/rules-unit-testing@6` need Node ≥ 24.12. Cloud Functions has Node 24 as GA until 2028-04. Node 26 becomes LTS on 2026-10-28, but firebase-tools 15.33 can't deploy it and Cloud Functions only has it in preview |
| Gradle | 8.14.3 | 9.8.1 | Needed for AGP 9.4 and Java 25 |
| GitHub Actions | checkout v7, setup-java v6, setup-node v7, setup-gradle v6, upload-artifact v7 (the emulator is started by `.github/scripts/android-emulator.sh`) | unchanged | All already on their latest major |

### Android (Gradle catalog)

| Artifact | Was | Now | Breaking changes that apply here |
|---|---|---|---|
| Android Gradle plugin | 8.10.1 | 9.4.0 | Built-in Kotlin: the `org.jetbrains.kotlin.android` plugin must go. Kotlin sources in extra source sets are added with `kotlin`, not `java`. Unit tests only exist for the tested build type, so `testReleaseUnitTest` is gone. `jvmTarget` defaults to `targetCompatibility` |
| Kotlin (compose, serialization plugins) | 2.3.21 | 2.4.21 | Needs AGP ≥ 9.1 (R8). K1 removed; no source changes found |
| compileSdk | 35 | 37 | Required by Compose 1.12, Coil 3.6 and the markdown renderer 0.42+. Needs AGP ≥ 9.2 |
| targetSdk | 35 | 35 | Not a dependency; moving to 36 is a separate, behaviour-tested change (see *Later*) |
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
| org.jetbrains:markdown (tests) | 0.7.3 | 0.7.16 | Parser fixes |
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

Sources, in the order they were used: migration guides (AGP 9 and built-in Kotlin migration,
Gradle 9 upgrading guides, markdown renderer `MIGRATION.md`, TypeScript 6.0 release notes),
then changelogs and release notes (AndroidX, Kotlin, Coil, Robolectric, kotlinx, Firebase
Android and JS SDK, firebase-admin, firebase-functions, marked, zod), then commit history where
neither existed (Firebase JS SDK's Node 24 requirement, Robolectric's SDK and JDK table).

## Steps

All five are done; CI (Android build, unit tests, lint, R8 release, rules, functions, emulator
integration and end-to-end tests) is green after each.

1. **Backend.** TypeScript 7, firebase-admin 14, firebase-functions 7, zod, marked; functions
   runtime Node 24; build hook and `tsconfig` fixes; grpc override.
2. **Build toolchain and runtimes.** Gradle 9.8.1, AGP 9.4.0, Kotlin 2.4.21 (remove the
   `kotlin-android` plugin, `sharedTest` as Kotlin source dirs, drop `jvmTarget`, release
   workflow runs `testDebugUnitTest`). CI on Node 24 and Java 21 everywhere; `mise.toml`
   updated; docs updated.
3. **Libraries.** compileSdk 37 with the AndroidX set, Compose BOM, kotlinx, Coil, markdown
   renderer, konfetti, Robolectric and androidx.test. Follow-ups CI asked for: konfetti's
   `CoreImage` shapes, and Compose lint's observable locale and resource reads.
4. **Java 25.** CI and `mise.toml` on Temurin 25: Gradle, AGP, R8, Robolectric and the Firebase
   emulators all pass on it. The docs keep 21 as the minimum.
5. **Screenshots.** Re-rendered for Material 3 1.4 by the `[screenshots]` workflow.

## Risks

- KGP 2.4.21 is tested up to Gradle 9.7 and AGP 9.3.1. Newer versions are allowed but may warn.
- AGP and Robolectric on Java 25 aren't officially confirmed; CI covers both, so a regression
  would show there first.
- Material 3 1.4 and Compose 1.9+ semantics changes can affect screenshot and accessibility
  tests.

## Done since

- **Installation IDs instead of FCM tokens:** the app registers with `FirebaseMessaging.register()`
  and the backend sends with `sendEachForMulticast({ fids })`.

## Later

- **targetSdk 36:** required by Google Play since 2026-08-31, only if Meadow is published there.
  The app is already edge-to-edge and uses predictive back; test on an API 36 emulator first.
- **Node 26:** once firebase-tools lists `nodejs26` and Cloud Functions makes it GA.
- **Material icons:** the extended set is frozen; switch to Material Symbols drawables when
  convenient.
- **firebase-tools' transitive advisories** (11, development only): wait for an upstream release.
