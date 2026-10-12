# Meadow 🌼

A cozy Android app for keeping daily and weekly app time limits **with your friends**.
You plant a limit for an app; when its time is up, the app rests until tomorrow. The only way to
get extra time, or to loosen or remove a limit, is to ask one of your **Bumblebees**: friends
you've chosen to look after your garden. They say yes or not today from their own phone.

## Features

- **Daily and weekly limits** per app. Whichever runs out first wins; weeks start on Monday.
- **Real enforcement.** An accessibility service notices the app on screen and sends a resting app
  home with a calm resting screen. A foreground service polls the usage log as a fallback.
- **Promises that can't be quietly undone**, enforced by Firestore rules and Cloud Functions,
  not just the app's UI:
  - Tightening a limit applies straight away; loosening or removing one needs a Bumblebee's yes.
  - Optional strict mode keeps Meadow's App info, its accessibility switch, uninstall dialogs and
    the clock settings out of reach. Turning it off needs a Bumblebee's yes.
  - Uninstall protection (device admin).
  - Bumblebees are told when a protection is turned off, or when a phone goes quiet for 6+ hours.
- **Bumblebees** can be added or removed any time without anyone's approval. It's your own
  business: only the Bumblebee affected is told. Bumblebees can't see who else looks after you.
- **Invite links.** Tap *Share an invite link* and send it however you like. Your friend opens it,
  says yes, and becomes your Bumblebee. Links are one-time and last a week. Friend codes work too.
- **Requests:** ask for 5–60 extra minutes with a note. Bumblebees can say yes (optionally with
  less time) or not today, from the app or straight from the notification.
- **Chat:** a 1:1 chat with each Bumblebee; requests appear in it as live cards.
- **Emergency unlock:** lift limits for 15, 30 or 60 minutes without waiting for anyone. Three per
  rolling 30 days (counted by the backend), needs a reason and a 5-second confirm, can be ended
  early, and every Bumblebee is told at once. One offline unlock per 30 days, reported once online.
  Phone, dialer and emergency apps are never limited.
- **Notifications:** 5- and 1-minute reminders, resting apps, requests, answers, messages,
  invites and protection alerts.
- **Statistics:** day, week and month views, per-app breakdown, daily average, comparison with the
  previous period and a monthly heatmap. Finished days are cached so history outlives the
  system's short usage log.
- **No registration to start.** A name and a flower are enough. An email is needed only before
  inviting your first Bumblebee, so a reinstall can't wipe your commitments.
- **Profiles:** a name, a short bio, and an emoji critter or flower on a coloured circle, or
  your own photo (cropped to a small square, shown only to your circle).
- **Themes:** Meadow, Cherry blossom, Lavender field, Sunflower, Lily pond and Mushroom forest,
  each by day or by night (or following the phone). Every theme meets WCAG AA contrast.
- **Privacy:** choose whether Bumblebees see today's screen time and your protection status.
  Download everything Meadow holds about you as JSON, or delete your account and all its data.
  A privacy notice, terms of use and licences ship in the app and on the Hosting site; people
  agree at sign-up and again when they change. Usage access and the accessibility service are
  explained before Android's settings open.
- **Updates from GitHub:** Meadow checks this repository's releases once a day on Wi-Fi (can be
  turned off), verifies the download's SHA-256 and signing key, and hands it to Android's
  installer, which always asks first.
- **Look and feel:** cream, moss and petal colours; Fraunces and Nunito fonts; hand-drawn flowers
  that sprout and bloom or wilt with the time left; a sky that follows the clock (sun, clouds and a
  butterfly by day; moon, stars and fireflies at night); sleepy "z"s over resting apps; petal and
  flower confetti for happy moments. All motion stops when system animations are turned off.

## Project layout

```
app/                       Android app (Kotlin, Jetpack Compose, Material 3)
  data/                    Repositories (Firebase), local store, sync, invite links
  usage/                   Usage log reading and pure screen-time arithmetic, app catalog, history
  enforcement/             Limit engine, accessibility service, guardian service, resting screen
  notifications/           Channels, FCM service, answer-from-notification worker
  update/                  Update checks, download verification and install from GitHub releases
  work/                    Periodic sync (usage summary and heartbeat)
  ui/                      Screens + ViewModels, typed navigation, theme, components
  src/test/                JVM, Robolectric and Compose tests
  src/androidTest/         End-to-end journey (Android emulator + Firebase emulators)
legal/                     Privacy notice, terms of use, licences (Markdown, shipped in the app and on Hosting)
backend/
  firestore.rules          Security rules: the source of truth for "can't loosen without a yes"
  firestore.indexes.json   Composite indexes
  storage.rules            Profile photos: owner-only writes, small images only
  hosting/                 Invite landing page, legal pages and assetlinks.json (Firebase Hosting)
  rules-tests/             Security rules tests (Firestore emulator)
  integration-tests/       Cloud Functions tests on the Auth, Firestore, Functions and Storage emulators
  functions/src/           Cloud Functions (TypeScript)
    copy.ts                Every user-facing word the backend sends
    schemas.ts             zod input schemas for every callable
    format.ts              Pure helpers (summaries, durations, codes, tokens)
    social.ts              Profiles, invite links and codes, adding and removing Bumblebees
    requests.ts            Asking for extra time or limit changes, and answering
    emergency.ts           Emergency unlocks
    account.ts             Data export and account deletion
    triggers.ts            Chat pushes, protection alerts, profile changes, hourly housekeeping
```

## Setup

You need a Firebase project on the **Blaze** plan (Cloud Functions require it). See *Cost* below.

1. **Create the project** at <https://console.firebase.google.com>.
   - Authentication → Sign-in method → enable **Email/Password** and **Anonymous**.
   - Firestore Database → create it in production mode.
   - Storage → get started (for profile photos).
2. **Connect the Android app.** Project settings → Add app → Android, package
   `dev.pcoffline.meadow`. Download `google-services.json` into `app/` (it's git-ignored).
   The build reads the project id from it to know your invite-link domain (`<project-id>.web.app`).
3. **Let invite links open the app.** Put your signing certificate's SHA-256 into
   `backend/hosting/.well-known/assetlinks.json`
   (`./gradlew signingReport` prints it; use the release key for release builds).
   Without it, links still work through the landing page's *Open in Meadow* button.
4. **Deploy the backend**
   ```bash
   npm install -g firebase-tools
   cd backend
   firebase login
   firebase use --add                       # pick your project
   (cd functions && npm install)
   firebase deploy --only firestore,functions,hosting,storage
   ```
5. **Build the app**
   ```bash
   ./gradlew assembleDebug                  # app/build/outputs/apk/debug/app-debug.apk
   ```
   Without `google-services.json` the app still builds, but shows a "connect your backend" screen.

### First run

1. Swipe through the intro and pick a name and a flower.
2. Grant **usage access** and turn on the **Meadow garden keeper** (both required), plus
   notifications, show-over-other-apps, uninstall protection and unrestricted battery.
   On Android 13+, sideloaded apps need *App info → ⋮ → Allow restricted settings* first.
3. Plant limits on the Garden tab.
4. Circle tab → add an email → *Share an invite link* and send it to a friend.

### Cost

Auth, Firestore within its free quota, and Cloud Messaging are free for personal use. Cloud
Functions need the Blaze (pay-as-you-go) plan, but its free monthly allowance (about 2M calls)
covers a group of friends; expect $0, or a few cents for stored function images. Hosting's free
tier covers the invite page. Set a budget alert in Google Cloud billing to be safe.

## Tests

| Layer | What it covers | Command |
|---|---|---|
| Unit (JVM) | Screen-time arithmetic, budgets, strict-mode rules, models, deep links, release parsing and versions, Markdown, theme contrast | `./gradlew testDebugUnitTest` |
| Robolectric + Compose | Local store persistence and wipe, consent and legal screens | (same) |
| Functions unit | Copy, schemas, formatting, emergency rules | `cd backend/functions && npm test` |
| Security rules | Who can read and write what (Firestore emulator) | `cd backend && npm ci && npm run test:rules` |
| Functions integration | Every callable and trigger on the emulators, including exactly who gets notified | `cd backend && npm ci && npm --prefix functions ci && npm run test:integration` |
| End-to-end | Onboarding, disclosures, profile, privacy, legal and account deletion on an Android emulator against the Firebase emulators | see below |
| Lint | Android lint | `./gradlew lintDebug` |

End-to-end, locally: start the emulators with
`cd backend && npx firebase emulators:start --only auth,firestore,functions,storage --project demo-meadow`,
start an Android emulator, then run `./gradlew connectedDebugAndroidTest -Pmeadow.emulatorHost=10.0.2.2`.
`-Pmeadow.emulatorHost` only affects debug builds.

The security and integration tests need Java 21 for the emulators. In the emulator, every push
Cloud Functions would send is also written to `_testOutbox`, which the rules keep closed.

CI (`.github/workflows/build.yml`) runs all of these on every push and uploads the debug APK.
Add a `GOOGLE_SERVICES_JSON` repository secret to get a connected APK from CI.

## Releases and updates

Push a tag like `v1.2.0` and `.github/workflows/release.yml` tests, builds and signs the APK and
publishes it as a GitHub release; installed copies offer it within a day. The version name and
code come from the tag. It needs these repository secrets:

| Secret | What |
|---|---|
| `GOOGLE_SERVICES_JSON` | Your `google-services.json` |
| `MEADOW_KEYSTORE_BASE64` | `base64 -w0 release.jks` |
| `MEADOW_KEYSTORE_PASSWORD`, `MEADOW_KEY_ALIAS`, `MEADOW_KEY_PASSWORD` | The keystore's passwords and alias |

Every release must be signed with the same key, or installed copies can't update. Keep the
keystore safe. In-app updates suit sideloaded installs; an app on Google Play must update through
Play instead. Forks can point the updater at their own repository with `-Pmeadow.updateRepo=owner/repo`.

## How enforcement works

| Layer | What it does |
|---|---|
| `ForegroundTime` | Pure arithmetic: foreground time per app from usage events, one app on screen at a time. |
| `LimitEngine` | Adds extra time and emergency unlocks, works out what's left today and this week. |
| `GuardianAccessibilityService` | Notices app switches, re-checks right when time would run out, shows the resting screen. In strict mode, backs out of settings screens that could turn Meadow off. |
| `GuardianService` | Foreground service: status notification, fallback polling, protection checks. |
| `SyncWorker` | Every 15 min: restarts the guardian if needed, shares today's usage, sends a heartbeat. |
| `LocalStore` | Enforcement only reads limits from the phone, so they keep working offline or signed out. |
| Firestore rules | Phones can only make limits stricter once Bumblebees exist; extra time, invites, requests and Bumblebee lists are written only by Cloud Functions. |

No Android app can make itself impossible to remove for the phone's owner: safe mode, a factory
reset, ADB or a rooted phone always get around it. Meadow makes getting around it deliberate and
visible, because Bumblebees are told.

## Voice and words

Copy lives in `app/src/main/res/values/strings*.xml` and `backend/functions/src/copy.ts`.
Warm, calm and plain; sentence case; at most one emoji, at the end. Use these terms exactly:

| Term | Meaning |
|---|---|
| Bumblebee | A friend who looks after your garden (code calls them *approvers*) |
| garden | Your limits, together |
| limit | Time allowed for one app, per day and/or week |
| resting | An app whose time is up for now |
| extra time | What you ask a Bumblebee for |
| say yes / not today | A Bumblebee's answers |
| emergency unlock | Lifting limits without a Bumblebee |

## Firestore data model

```
users/{uid}                 displayName, flower, avatarColor, photoUrl, bio, friendCode, strictMode,
                            privacy{shareUsage, shareProtection}, protection{}, lastHeartbeat,
                            emergencyUntil, emergencyPackage, emergencyUses[]   (readable by your circle)
  private/account           approverIds[], approveeIds[], fcmTokens[], theme, darkMode,
                            acceptedTerms{version, at}                          (owner only)
  limits/{package}          packageName, appName, dailyMinutes, weeklyMinutes (0 = none)
  grants/{requestId}        packageName, minutes, dayKey                        (functions only)
  usage/{yyyy-MM-dd}        totalMinutes, apps[]                     (Bumblebees, only while shareUsage is on)
  emergencies/{id}          reason, minutes, packageName, offline, overAllowance (functions only)
  alerts/{id}               kind, lost[]                                        (triggers a push to Bumblebees)
friendCodes/{code}          uid                                                 (functions only)
inviteLinks/{token}         ownerUid, expiresAt, usedBy                         (functions only)
invites/{id}                fromUid, toUid, status
requests/{id}               requesterId, type, approverIds[], status, …
chats/{uidA_uidB}           participants[], names{}, avatars{}, lastMessage, lastRead{}
  messages/{id}             senderId, kind (text|request|system), text, requestId
```

## Credits

Fonts: [Fraunces](https://github.com/undercasetype/Fraunces) and [Nunito](https://github.com/googlefonts/nunito),
both under the SIL Open Font License (see `app/src/main/assets/licenses/`). Confetti by
[Konfetti](https://github.com/DanielMartinus/Konfetti), images by [Coil](https://github.com/coil-kt/coil).
See `legal/licenses.md` for the full list.
