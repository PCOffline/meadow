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
- **Bumblebees** can be added or removed any time without anyone's approval. Everyone involved is
  always told, so nobody is dropped quietly.
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
- **Look and feel:** cream, moss and petal colours; Fraunces and Nunito fonts; hand-drawn flowers
  that sprout and bloom or wilt with the time left; a sky that follows the clock (sun, clouds and a
  butterfly by day; moon, stars and fireflies at night); sleepy "z"s over resting apps; petal and
  flower confetti for happy moments; light and dark themes. All motion stops when system
  animations are turned off.

## Project layout

```
app/                       Android app (Kotlin, Jetpack Compose, Material 3)
  data/                    Repositories (Firebase), local store, sync, invite links
  usage/                   Usage log reading and pure screen-time arithmetic, app catalog, history
  enforcement/             Limit engine, accessibility service, guardian service, resting screen
  notifications/           Channels, FCM service, answer-from-notification worker
  work/                    Periodic sync (usage summary and heartbeat)
  ui/                      Screens + ViewModels, typed navigation, theme, components
  src/test/                JVM unit tests
backend/
  firestore.rules          Security rules: the source of truth for "can't loosen without a yes"
  firestore.indexes.json   Composite indexes
  hosting/                 Invite landing page and assetlinks.json (Firebase Hosting)
  rules-tests/             Security rules tests (Firestore emulator)
  functions/src/           Cloud Functions (TypeScript)
    copy.ts                Every user-facing word the backend sends
    schemas.ts             zod input schemas for every callable
    format.ts              Pure helpers (summaries, durations, codes, tokens)
    social.ts              Profiles, invite links and codes, adding and removing Bumblebees
    requests.ts            Asking for extra time or limit changes, and answering
    emergency.ts           Emergency unlocks
    triggers.ts            Chat pushes, protection alerts, hourly housekeeping
```

## Setup

You need a Firebase project on the **Blaze** plan (Cloud Functions require it). See *Cost* below.

1. **Create the project** at <https://console.firebase.google.com>.
   - Authentication → Sign-in method → enable **Email/Password** and **Anonymous**.
   - Firestore Database → create it in production mode.
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
   firebase deploy --only firestore,functions,hosting
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

| What | Command |
|---|---|
| Android unit tests | `./gradlew testDebugUnitTest` |
| Android lint | `./gradlew lintDebug` |
| Functions unit tests | `cd backend/functions && npm test` |
| Security rules (emulator, needs Java) | `cd backend && npm install && npm run test:rules` |

CI (`.github/workflows/build.yml`) runs all of these on every push and uploads the debug APK.
Add a `GOOGLE_SERVICES_JSON` repository secret to get a connected APK from CI.

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
users/{uid}                 displayName, flower, friendCode, approverIds[], approveeIds[], strictMode,
                            fcmTokens[], protection{}, lastHeartbeat,
                            emergencyUntil, emergencyPackage, emergencyUses[]   (functions only, except ending early)
  limits/{package}          packageName, appName, dailyMinutes, weeklyMinutes (0 = none)
  grants/{requestId}        packageName, minutes, dayKey                        (functions only)
  usage/{yyyy-MM-dd}        totalMinutes, apps[]                                (readable by Bumblebees)
  emergencies/{id}          reason, minutes, packageName, offline, overAllowance (functions only)
  alerts/{id}               kind, lost[]                                        (triggers a push to Bumblebees)
friendCodes/{code}          uid                                                 (functions only)
inviteLinks/{token}         ownerUid, expiresAt, usedBy                         (functions only)
invites/{id}                fromUid, toUid, status
requests/{id}               requesterId, type, approverIds[], status, …
chats/{uidA_uidB}           participants[], names{}, flowers{}, lastMessage, lastRead{}
  messages/{id}             senderId, kind (text|request|system), text, requestId
```

## Credits

Fonts: [Fraunces](https://github.com/undercasetype/Fraunces) and [Nunito](https://github.com/googlefonts/nunito),
both under the SIL Open Font License (see `app/src/main/assets/licenses/`). Confetti by
[Konfetti](https://github.com/DanielMartinus/Konfetti).
