# Debugging Meadow

How to see what the app asks of Firebase, what comes back, and why something failed. Nothing
needs installing on the phone besides a debug build of Meadow.

## Connect to the phone

Turn on **Developer options** on the phone (tap *Build number* seven times in *About phone*),
then **USB debugging** or **Wireless debugging**. From your computer, `adb devices` should list
it. The Android SDK platform tools include `adb`.

On WSL, USB devices aren't visible. Use wireless debugging (`adb pair <ip>:<port>` once, then
`adb connect <ip>:<port>`), or the Windows `adb.exe`. An Android emulator also works.

## Trace mode

A debug build made with `-Pmeadow.trace=true` logs every exchange with Firebase:

```bash
./gradlew installDebug -Pmeadow.trace=true
adb logcat -s MeadowTrace
```

| Line | Meaning |
|---|---|
| `trace on: project=… region=… emulators=… version=…` | Which backend this build talks to. Check this first. |
| `auth: <uid> anonymous=… providers=[…]` / `auth: signed out` | Every sign-in change. |
| `→ call createProfile {…}` | A Cloud Function call and what it sent. |
| `← call createProfile in 412 ms: {…}` | Its answer. |
| `✗ call createProfile failed after 95 ms` | Its error, with the code (`NOT_FOUND`, `PERMISSION_DENIED`…) in the stack trace. |
| `⇢ listen doc users/abc` | The app started listening to a document or collection. |
| `⇠ doc users/abc: exists=true fromCache=false pendingWrites=false data={…}` | Each snapshot. `fromCache=true` means the server hasn't answered yet. |
| `✗ listener doc users/abc failed` | The server refused or failed the read, with the reason. |
| `→ upload avatars/abc (48213 bytes)` / `→ download exports/abc/…` | Storage transfers. |
| `push registered as …` / `push received: {…}` | Push registration and each push. |
| `push dropped: not for the signed-in account` | A push whose `to` isn't the account signed in now (or nobody is), so it isn't shown. |

On screen, error messages in a trace build also show the technical cause underneath, such as
`Firestore: PERMISSION_DENIED …`. Normal builds only tell people what they can act on (offline,
sign in again) and otherwise ask them to try again later.

Trace mode also turns on the Firestore SDK's own log, which shows every read, write and listen
at the wire level. It is very verbose:

```bash
adb logcat -s MeadowTrace Firestore
```

The trace contains people's data (names, messages, limits). Release builds never have it: the
flag only applies to debug builds.

Without trace mode, failures the app hides from people are still logged:
`adb logcat -s MeadowSync MeadowBackend MeadowFirestore MeadowSocial MeadowListener MeadowGuardian`.

## Android Studio

Run the debug build from Android Studio for:

- **Logcat**: the logs above, with filtering.
- **The debugger**: breakpoints anywhere, for example `Backend.call` or `SyncManager`.
- **App Inspection → Network Inspector**: Cloud Functions calls and Storage transfers, with their
  bodies. Firestore uses its own gRPC connection and doesn't appear there; use trace mode for it.

## Everything local: the Firebase emulators

The emulators show every request and which security rule allowed or denied it. Your real project
is never touched. Set `"ui": { "enabled": true }` in `backend/firebase.json` (CI keeps it off),
then:

```bash
cd backend
npm ci && npm --prefix functions ci
npm run emulators
```

This starts them with demo people to sign in as, and keeps your data between runs (see
[Developing against the emulators](self-hosting.md#developing-against-the-emulators)). If you
start them by hand, use `npx firebase`, not a standalone `firebase` binary: the standalone one
bundles an older Node than the functions need.

Point a debug build at them. `10.0.2.2` is your computer as seen from an Android emulator; from a
phone, use your computer's address on the local network:

```bash
./gradlew installDebug -Pmeadow.emulatorHost=10.0.2.2 -Pmeadow.trace=true
```

Open <http://localhost:4000> for the data, each function's logs, and the Firestore **Requests**
tab with the rules evaluation of every read and write.

## Your live project

- **Function logs**: `npx firebase functions:log` in `backend/`, or **Functions → Logs** in the
  console.
- **Security rules**: **Firestore → Rules → Rules Playground** simulates a read or write as a given
  user and shows which rule decided it.
- **Accounts**: **Authentication → Users** shows whether a sign-in happened.

The self-hosting guide's [Troubleshooting](self-hosting.md#troubleshooting) section covers the
common errors.
