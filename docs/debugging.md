# Debugging Meadow

This guide shows how to see the cause of a failure, and the requests that the app sends to
Firebase. On the phone, you need only a debug build of Meadow.

## Connect to the phone

1. On the phone, open *About phone* and tap *Build number* seven times. This turns on
   **Developer options**.
2. Turn on **USB debugging** or **Wireless debugging**.
3. On your computer, run `adb devices`. Make sure that the phone is in the list. `adb` is in the
   Android SDK platform tools.

WSL can't see USB devices. Use wireless debugging (`adb pair <ip>:<port>` one time, then
`adb connect <ip>:<port>`), or the Windows `adb.exe`. An Android emulator also works.

## Every screen by hand

A debug build has a second icon, **Meadow screens**. It shows each screen in each state, for
example an empty garden, a resting app outside its hours, or a friend who shares nothing. The
data comes from the demo garden in `app/src/demo/java/…/showcase/DemoGarden.kt`.

1. At the top of the list, choose a theme, day or night, or *Offline*.
2. Open a state. Use back to return to the list.

In Growth, you can tap the periods and the days of the month, as in the app.

The data is not real, but the buttons work as in the app, for the account that is signed in.
Release builds don't have the gallery or its data.

A debug build has your debug signature, so you can't install it over a release build. Remove
the release build first, or use a different phone or an emulator.

To add a state, add a `Scenario` to `app/src/debug/java/…/showcase/Gallery.kt`. `GalleryTest`
draws each scenario.

## Logs

The app logs the failures that it doesn't show to people:

```bash
adb logcat -s Meadow MeadowSync MeadowBackend MeadowFirestore MeadowSocial MeadowListener MeadowGuardian
```

Error messages on screen show only what people can do (go online, sign in again). For all other
errors, they tell people to try again later. The log has the cause, for example the code of a
failed Cloud Function call (`NOT_FOUND`, `PERMISSION_DENIED`…).

## Android Studio

Run the debug build from Android Studio to use:

- **Logcat**: the log above, with filters.
- **The debugger**: breakpoints in any location, for example `Backend.call` or `SyncManager`.
- **App Inspection → Network Inspector**: Cloud Functions calls and Storage transfers, with their
  bodies. Firestore uses its own gRPC connection, so it isn't in this view. Use the emulators'
  **Requests** tab for Firestore (below).

## All local: the Firebase emulators

The emulators show each request and the security rule that allowed or refused it. They never
touch your real project.

1. In `backend/firebase.json`, set `"ui": { "enabled": true }`. CI keeps it off.
2. Start the emulators:

   ```bash
   cd backend
   npm ci && npm --prefix functions ci
   npm run emulators
   ```

   This starts them with demo people to sign in as. It keeps your data between runs. Read
   [Developing against the emulators](self-hosting.md#developing-against-the-emulators).
3. Connect a debug build to the emulators. From an Android emulator, `10.0.2.2` is your
   computer. From a phone, use the address of your computer on the local network:

   ```bash
   ./gradlew installDebug -Pmeadow.emulatorHost=10.0.2.2
   ```

4. Open <http://localhost:4000>. It shows the data, the logs of each function, and the Firestore
   **Requests** tab. That tab shows how the rules decided each read and write.

If you start the emulators by hand, use `npx firebase`. The standalone `firebase` binary has an
older Node than the functions need.

## Your live project

- **Function logs**: run `npx firebase functions:log` in `backend/`, or open **Functions → Logs**
  in the console.
- **Security rules**: **Firestore → Rules → Rules Playground** tries a read or write as a user
  and shows the rule that decided it.
- **Accounts**: **Authentication → Users** shows if a sign-in occurred.

For frequent errors, read [Troubleshooting](self-hosting.md#troubleshooting) in the
self-hosting guide.
