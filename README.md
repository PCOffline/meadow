<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/images/banner-dark.svg">
  <img src="docs/images/banner-light.svg" alt="Meadow: gentle app limits, kept with friends" width="100%">
</picture>

<p align="center">
  <a href="https://github.com/PCOffline/meadow/actions/workflows/build.yml"><img src="https://github.com/PCOffline/meadow/actions/workflows/build.yml/badge.svg" alt="Build status"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/licence-GPL--3.0-5B7F55" alt="Licence: GPL-3.0"></a>
  <img src="https://img.shields.io/badge/Android-8.0%2B-5B7F55" alt="Android 8.0 or newer">
</p>

**Meadow is a cozy way to spend less time on the apps that pull you in.** Give each app a little
patch of time. When it's used up, the app rests until tomorrow. Want more? Ask one of your
**Bumblebees**, the friends you've chosen to look after your garden. Willpower is hard; a kind
friend who has to say yes is much harder to argue with.

<p align="center">
  <img src="docs/images/screen-garden.png" alt="The garden: a flower for each app, blooming while there's time left, resting when it's used up" width="250">
  &nbsp;
  <img src="docs/images/screen-chat.png" alt="A chat with a Bumblebee, who said yes to 15 more minutes" width="250">
  &nbsp;
  <img src="docs/images/screen-stats.png" alt="A week of screen time, down 20% on the week before" width="250">
</p>

## How it works

<table>
  <tr>
    <td align="center" width="33%"><img src="docs/images/step-plant.svg" alt="" width="150"><br><b>Plant a limit</b><br>Choose how long an app may bloom each day or week.</td>
    <td align="center" width="33%"><img src="docs/images/step-rest.svg" alt="" width="150"><br><b>It rests</b><br>When the time is used up, the app closes gently until tomorrow.</td>
    <td align="center" width="33%"><img src="docs/images/step-ask.svg" alt="" width="150"><br><b>Ask a Bumblebee</b><br>Need more? A friend can say yes, right from their notification.</td>
  </tr>
</table>

## Features

### A garden that grows with your time
Every app you limit is a flower that blooms while there's time left and wilts as it runs out. The
sky follows your day, from a peach sunrise to a starry night with fireflies. Gentle reminders
arrive at five minutes and one minute left.

### Friends who have your back
Invite a friend with a link and they become your Bumblebee. Only a Bumblebee can give you extra
time, loosen a limit or remove one. Making a limit stricter is always up to you. Each Bumblebee
gets a chat with you, and your requests appear in it as cards they can answer with one tap.

<p align="center">
  <img src="docs/images/screen-circle.png" alt="Your circle: two Bumblebees, and a friend whose garden you look after" width="250">
  &nbsp;
  <img src="docs/images/screen-resting.png" alt="A resting app under a night sky, with a button to ask a Bumblebee" width="250">
</p>

### Hard to wriggle out of
Strict mode keeps Meadow's own settings, uninstalling and the phone's clock out of reach, and
turning it off needs a Bumblebee's yes too. If something gets switched off anyway, or your phone
goes quiet, your Bumblebees are told. Your limits keep working offline.

### For real emergencies
Lost and need maps? Unlock everything for up to an hour, three times in 30 days, with a short note
for your Bumblebees. Calls and emergency apps are never limited.

### See your days take shape
Daily, weekly and monthly views show where your time went, how it compares with the week before,
and which days were the calmest.

### Make it yours
Six themes, each by day or by night: Meadow, Cherry blossom, Lavender field, Sunflower, Lily pond
and Mushroom forest. Pick a critter or flower for your picture, or use a photo, and add a few
words about yourself.

<p align="center">
  <img src="docs/images/themes.png" alt="The garden in four themes: Cherry blossom, Sunflower, Mushroom forest and Lavender field by night" width="100%">
</p>

### Private by default
No email needed to start; later you can keep your garden safe with a sign-in link emailed to
you, or a password. You choose whether your Bumblebees see today's screen time. Your
detailed history never leaves your phone. Who looks after whom is your own business: only the
person affected is told when you add or remove a Bumblebee. Download everything Meadow holds about
you, or delete your account, any time. Read the [privacy notice](legal/privacy_notice.md).

### Kind to everyone
Built for TalkBack, large text and switch access: every control is labelled, touch targets are
large, and every theme meets WCAG AA contrast. All motion stops when you turn animations off.

<p align="center">
  <img src="docs/images/welcome.webp" alt="Meadow's welcome screens" width="250">
</p>

---

## Get Meadow

Download the latest APK from [Releases](https://github.com/PCOffline/meadow/releases) and
open it on your phone (you may need to allow installing from your browser or files app). Meadow
checks for new releases once a day on Wi-Fi and asks before updating; you can turn this off in
Settings.

On first launch, Meadow asks for **usage access** (to measure time) and for its **garden keeper**
accessibility service (to rest apps whose time is up). On Android 13 and newer, sideloaded apps
need *App info → ⋮ → Allow restricted settings* before the garden keeper can be turned on.

Meadow needs a backend to run. The releases here are built for this project's own Firebase
project. To run your own copy, see [Running your own Meadow](docs/self-hosting.md).

## How Meadow keeps its promises

- Limits are enforced on the phone, from a copy that works offline and signed out.
- An accessibility service notices which app is on screen and closes it when its time is up; a
  background service double-checks the usage log.
- Loosening a limit, extra time and Bumblebee changes go through the backend, whose security
  rules only let a phone make things stricter on its own.
- Nothing on Android can stop a phone's owner who is determined (safe mode, a factory reset,
  developer tools). Meadow makes getting around it deliberate and visible, because your
  Bumblebees are told.

## Tests

The `justfile` has a shortcut for each of these and for building, installing and deploying: run
`just` to list them (for example `just check`, `just install-trace` or `just deploy`).

| What | Command |
|---|---|
| App unit, screen and accessibility tests | `./gradlew testDebugUnitTest` |
| Lint | `./gradlew lintDebug` |
| Cloud Functions unit tests | `cd backend/functions && npm test` |
| Security rules (Firestore and Storage) | `cd backend && npm ci && npm run test:rules` |
| Cloud Functions on the emulators | `cd backend && npm ci && npm --prefix functions ci && npm run test:integration` |
| Hosted legal pages | `cd backend && npm run test:hosting` |
| Deploy scripts | `cd backend && npm run test:deploy` |
| End to end, on an Android emulator | Start the Firebase emulators (`npm run emulators` in `backend/`), then `./gradlew connectedDebugAndroidTest -Pmeadow.emulatorHost=10.0.2.2` |
| CI scripts and workflows | `python3 -m unittest discover -s .github/scripts -p 'test_*.py'`, plus `actionlint` and `shellcheck` |

Building and testing need Java 21 or newer (CI uses 25) and Node 24; `mise install` sets both up.
CI runs everything on every pull request and every push to `main`. To see every request the app
makes and what comes back, see [Debugging Meadow](docs/debugging.md).

## Contributing

Issues and pull requests are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md) for the voice guide,
accessibility checks and how the README images are made.

## Licence and credits

Meadow is free software under the [GNU GPL v3](LICENSE). See the [terms of use](legal/terms_of_use.md)
and [open-source licences](legal/licenses.md). Fonts: [Fraunces](https://github.com/undercasetype/Fraunces)
and [Nunito](https://github.com/googlefonts/nunito) (SIL Open Font License).
