# Meadow 🌼

A cozy Android app for keeping daily and weekly app time limits **with your friends**.
You plant a limit for an app; when its time is up the app closes and rests until tomorrow.
The only way to get more time — or to loosen, remove or switch off anything — is to ask one of
your approvers, and they say yes or no from their own phone.

## Features

- **Daily & weekly limits** per app. Whichever runs out first wins; weeks start Monday.
- **Real enforcement.** An accessibility service notices the app on screen and sends limited apps
  home with a calm "resting" screen once their time is up. A foreground service polls the usage
  log as a fallback if the accessibility service is ever paused.
- **Commitments can't be quietly undone.**
  - Tightening applies immediately; loosening/removing a limit needs an approver's yes.
  - Adding or removing approvers once you have one needs your existing approvers' yes.
  - Strict mode (optional) keeps Meadow's App info, accessibility toggle, uninstall dialogs and
    the date/time settings out of reach. Turning it off needs approval.
  - Device-admin uninstall protection.
  - **Emergency unlock:** in a real emergency you can lift all limits (or one app's) for 15, 30 or
    60 minutes without waiting for anyone. It's rationed to 3 per rolling 30 days (counted by the
    backend), needs a written reason and a 5-second confirm, can be ended early, and every approver
    is notified at once with the reason. Without a connection, one offline unlock per 30 days is
    allowed and reported as soon as the phone is back online.
  - Phone, dialer and emergency apps can never be limited.
  - Approvers are alerted when a protection is switched off, or when a phone stops checking in
    for 6+ hours.
  - All of the above is enforced **server-side** by Firestore rules and Cloud Functions, not just
    in the app UI.
- **Social:** invite approvers with a 6-character friend code, accept/decline invites, step down,
  see the people you approve for (their usage today, limits and protection status).
- **Requests:** ask for 5–60 extra minutes with a note; approvers can approve (optionally fewer
  minutes), decline with a kind word, or answer straight from the notification.
- **Chat:** a 1:1 chat opens with each approver; requests appear in it as live cards with
  approve/decline buttons.
- **Notifications:** 5-minute and 1-minute warnings, time's-up, new requests, answers, messages,
  invites and protection alerts (FCM push).
- **Statistics:** day / week / month views, per-app breakdown, daily average, comparison with the
  previous period, monthly heatmap. Finished days are cached so monthly history survives the
  system's short usage-log retention.
- **No registration to start.** A name and a flower are enough (anonymous Firebase account).
  An email is required only before inviting your first approver, so commitments survive a
  reinstall — and the backend refuses invites from email-less accounts.
- **Look & feel:** cream, moss and petal palette; Fraunces (soft) and Nunito fonts; hand-drawn
  flowers that sprout in and bloom or wilt with the time left; a living sky header that follows
  the clock (peach mornings, starry nights, drifting clouds, a butterfly by day, fireflies by night);
  sleepy "z"s over resting apps; petal showers when a limit is planted or a friend says yes;
  bouncy cards and tabs, animated onboarding and screen transitions; light and dark themes.
  All motion stops when system animations are turned off.

## Project layout

```
app/                     Android app (Kotlin, Jetpack Compose, Material 3)
  data/                  Firebase repositories, local store, sync
  usage/                 UsageStatsManager tracking, app catalog, history cache
  enforcement/           Limit engine, accessibility service, guardian service, block screen, device admin
  notifications/         Channels, FCM service, approve-from-notification receiver
  work/                  Periodic sync (usage summary + heartbeat)
  ui/                    Screens: garden, limits, stats, requests, circle, chat, settings, setup
backend/                 Firebase backend
  firestore.rules        Security rules (the source of truth for "can't loosen without approval")
  firestore.indexes.json Composite indexes
  functions/             Cloud Functions (TypeScript): profiles, invites, requests, push, housekeeping
```

## Setup

You need a Firebase project on the **Blaze** plan (Cloud Functions and scheduled functions require it;
normal personal usage stays within the free tier).

1. **Create the Firebase project** at <https://console.firebase.google.com>.
   - Authentication → Sign-in method → enable **Email/Password** and **Anonymous**.
   - Firestore Database → create (production mode).
   - Cloud Messaging is enabled by default.
2. **Deploy the backend**
   ```bash
   npm install -g firebase-tools
   cd backend
   firebase login
   firebase use --add            # pick your project
   cd functions && npm install && cd ..
   firebase deploy --only firestore,functions
   ```
3. **Connect the Android app**
   - Firebase console → Project settings → Add app → Android, package name `dev.pcoffline.meadow`.
   - Download `google-services.json` into `app/`. It is git-ignored.
4. **Build**
   ```bash
   ./gradlew assembleDebug
   # APK: app/build/outputs/apk/debug/app-debug.apk
   ```
   Open in Android Studio, or install with `adb install app/build/outputs/apk/debug/app-debug.apk`.
   Without `google-services.json` the app still builds, but shows a "connect your backend" screen.

### CI

`.github/workflows/build.yml` builds the debug APK (uploaded as an artifact) and compiles the
functions on every push. To get a connected APK from CI, add a repository secret
`GOOGLE_SERVICES_JSON` containing the file's contents.

## First run

1. Swipe through the intro, pick a name and a flower — no email needed yet.
2. Grant **usage access** and turn on the **Meadow garden keeper** accessibility service (both
   required), plus notifications, display-over-apps, uninstall protection and unrestricted
   battery (recommended).
   - Android 13+ blocks accessibility for sideloaded apps until you open Meadow's
     *App info → ⋮ → Allow restricted settings*.
3. Plant limits on the Garden tab.
4. Circle tab → add an email to your garden, then enter your friend's code under *Invite someone to be your approver*; they accept
   in their Circle tab. From then on, extra time and loosening need their yes.

## How enforcement works

| Layer | What it does |
|---|---|
| `LimitEngine` | Measures foreground time per app from the usage event log (one app on screen at a time), adds approved grants, computes remaining daily/weekly time. |
| `GuardianAccessibilityService` | Detects app switches, schedules a re-check right when time would run out, sends the app home and shows `BlockActivity`. In strict mode, backs out of settings screens that could disable Meadow. |
| `GuardianService` | Foreground service: status notification, fallback polling when accessibility is off, protection checks. |
| `SyncWorker` | Every 15 min: restarts the guardian if killed, uploads today's usage summary, heartbeat. |
| `LocalStore` | Enforcement reads limits/grants only from the on-device copy, so limits keep working offline or signed out. |
| Firestore rules | Clients can only create limits or make them stricter once they have approvers; grants, invites, requests and approver lists are written by Cloud Functions only. |

### Limits of what an app can do

No Android app can make itself impossible to remove for the phone's owner: safe mode, a factory
reset, ADB or a rooted device can always get around it. Meadow's approach is to make bypassing
deliberate and visible — approvers are told when protections go off or the phone goes quiet.

## Firestore data model

```
users/{uid}                 displayName, flower, friendCode, approverIds[], approveeIds[], strictMode,
                            fcmTokens[], protection{}, lastHeartbeat,
                            emergencyUntil, emergencyPackage, emergencyUses[]   (functions only, except ending early)
  limits/{package}          packageName, appName, dailyMinutes, weeklyMinutes (0 = none)
  grants/{requestId}        packageName, minutes, dayKey          (functions only)
  usage/{yyyy-MM-dd}        totalMinutes, apps[]                  (readable by approvers)
  emergencies/{id}          reason, minutes, packageName, offline, overAllowance (functions only)
  alerts/{id}               kind, detail                          (triggers approver push)
friendCodes/{code}          uid                                   (functions only)
invites/{id}                fromUid, toUid, status
requests/{id}               requesterId, type, approverIds[], status, …
chats/{uidA_uidB}           participants[], names{}, flowers{}, lastMessage, lastRead{}
  messages/{id}             senderId, kind (text|request|system), text, requestId
```

## Credits

Fonts: [Fraunces](https://github.com/undercasetype/Fraunces) and [Nunito](https://github.com/googlefonts/nunito),
both under the SIL Open Font License (see `app/src/main/assets/licenses/`).
