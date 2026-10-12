# Running your own Meadow

This guide sets up a complete copy of Meadow: your own Firebase backend, your own build of the
app, and (optionally) automatic builds and releases on GitHub. Plan for about an hour the first
time.

- [What you'll set up](#what-youll-set-up)
- [Before you start](#before-you-start)
- [1. Get the code](#1-get-the-code)
- [2. Create the Firebase project](#2-create-the-firebase-project)
- [3. Register the Android app](#3-register-the-android-app)
- [4. Deploy the backend](#4-deploy-the-backend)
- [5. Let links open the app](#5-let-links-open-the-app)
- [6. Build the app on your computer](#6-build-the-app-on-your-computer)
- [7. Build and release with GitHub Actions](#7-build-and-release-with-github-actions)
- [8. Keeping your copy up to date](#8-keeping-your-copy-up-to-date)
- [Developing against the emulators](#developing-against-the-emulators)
- [Troubleshooting](#troubleshooting)

## What you'll set up

Meadow's backend is a single [Firebase](https://firebase.google.com) project. Each part has one
job:

| Firebase product | What Meadow uses it for | What you configure by hand |
|---|---|---|
| Authentication | Signing in with email and password, emailed links, or anonymously | Turn on three sign-in providers |
| Cloud Firestore | Name cards, limits, requests, chats, invites | Create the database. Rules and indexes are deployed from `backend/` |
| Cloud Functions | Everything that needs trust: invites, requests and answers, emergency unlocks, alerts, data export, account deletion, scheduled clean-up | Nothing. Deployed from `backend/functions/` |
| Cloud Storage | Profile photos and data-export files | Create the default bucket. Rules are deployed from `backend/` |
| Hosting | Invite pages, the sign-in link landing page, legal pages, Android app links | Nothing. Deployed from `backend/hosting/` |
| Cloud Messaging | Push notifications | Nothing, but check it's enabled |

Once the project exists, one command deploys all the rules, indexes, functions and pages. You
never create collections or write rules in the console.

**Cost.** A group of friends fits in the free allowances. Cloud Functions need the pay-as-you-go
**Blaze** plan, so you need a billing account, but a small group should pay $0 a month, or a few
cents for storing function images.

## Before you start

You need:

- A Google account and a way to pay (for the Blaze plan; you won't be charged at low usage).
- **Node.js 24** with npm. Check with `node --version`.
- **Java 21 or newer** to build the app and run the Firebase emulators; CI uses 25. The one
  bundled with Android Studio works.
- **git**, and a GitHub account if you want automatic builds and releases.
- The **Firebase CLI**: `npm install -g firebase-tools`, then `firebase login`.

If you use [mise](https://mise.jdx.dev), `mise install` in the repository sets up the same Node,
Java and Firebase CLI versions as CI (see `mise.toml`).

Commands below are for Linux or macOS. On Windows, use WSL.

## 1. Get the code

If you want GitHub Actions to build and release your copy, **fork** the repository on GitHub
first (the *Fork* button on the repository page) and clone your fork. Otherwise, clone the
original:

```bash
git clone https://github.com/<you>/meadow.git
cd meadow
```

## 2. Create the Firebase project

### 2.1 The project

1. Open <https://console.firebase.google.com> and choose **Create a project**.
2. Give it a name. The **project ID** shown under the name becomes part of your web address
   (`https://<project-id>.web.app`) and can't be changed later. Click it to edit it if you like.
3. Google Analytics isn't used by Meadow. Turn it off.

### 2.2 The Blaze plan and a budget

1. In the console, open **⚙ (next to Project Overview) → Usage and billing → Details &
   settings → Modify plan** and choose **Blaze**. Link or create a billing account.
2. Add a budget so any surprise reaches you by email: open
   <https://console.cloud.google.com/billing>, choose your billing account, then **Budgets &
   alerts → Create budget**. Scope it to your project, set an amount such as $1, and keep the
   default alert thresholds.

### 2.3 Authentication

1. **Build → Authentication → Get started.**
2. On the **Sign-in method** tab, add:
   - **Email/Password.** Turn on both switches: *Email/Password* and **Email link
     (passwordless sign-in)**. Save.
   - **Anonymous.** Turn it on. Save. People can then try Meadow before adding an email.
3. On the **Settings** tab, open **Authorized domains** and check that `<project-id>.web.app`
   and `<project-id>.firebaseapp.com` are listed. They are by default. Sign-in links go through
   the first one.
4. Optional: on the **Templates** tab you can change the sender name and the wording of the
   sign-in and password-reset emails.

### 2.4 Cloud Firestore

1. **Build → Firestore Database → Create database.**
2. If asked for an edition, choose **Standard**. Keep the database ID as **(default)**: Meadow
   uses the default database.
3. Choose a **location** close to the people who'll use it. It can't be changed later. If
   unsure, `nam5 (United States)` or `eur3 (Europe)` are good choices. Note it: the functions
   should run next to it (step 4).
4. Choose **Start in production mode** and **Create**.

That's all. Leave the database empty: the deploy in step 4 replaces the default rules with
`backend/firestore.rules` and creates the indexes in `backend/firestore.indexes.json`.
Collections appear on their own as people use the app.

### 2.5 Cloud Storage

1. **Build → Storage → Get started.**
2. Choose a location. `US-CENTRAL1`, `US-EAST1` and `US-WEST1` include a free allowance; other
   regions are billed from the first byte, but Meadow stores very little.
3. Choose **Start in production mode** and **Create**. This makes the default bucket,
   `<project-id>.firebasestorage.app`.

Again, nothing else to set up: the deploy uploads `backend/storage.rules`, which only lets people
read and write their own photo and their own export files.

Optional: data exports are deleted as soon as the app downloads them. To also clean up any export
that was never downloaded, add a lifecycle rule in Google Cloud: open
<https://console.cloud.google.com/storage/browser>, choose the bucket, then **Lifecycle → Add a
rule → Delete object**, with conditions **Age: 1 day** and **Object name matches prefix:
`exports/`**.

### 2.6 Cloud Messaging

Open **⚙ → Project settings → Cloud Messaging** and check that **Firebase Cloud Messaging API
(V1)** says *Enabled*. It is on by default for new projects. If it says *Disabled*, use the ⋮ menu
next to it to enable it. Nothing else is needed: the server key and sender ID are not used.

## 3. Register the Android app

1. Open **⚙ → Project settings → General**. Under **Your apps**, click the **Android** icon.
2. **Android package name:** `dev.pcoffline.meadow`. Leave the nickname and SHA-1 empty; you'll
   add fingerprints in step 5.
3. **Register app**, then **Download google-services.json** and save it as `app/google-services.json`
   in your clone.
4. The next two screens show how to add the Google services Gradle plugin and the Firebase BoM.
   Meadow already has both (see `gradle/libs.versions.toml`), so click **Next** through them and
   **Continue to console**. Meadow deliberately leaves out `firebase-analytics`.

`google-services.json` holds identifiers, not secrets: the same values ship inside every APK. What
protects your data are the security rules deployed in step 4. The file is still kept out of git
(it's in `.gitignore`), so each copy of Meadow uses its own project and nobody else spends your
quota with your key.

Optional but recommended: limit the key to your app. In
<https://console.cloud.google.com/apis/credentials>, edit the key named *Android key (auto created
by Firebase)*. Under **Application restrictions** choose **Android apps** and add
`dev.pcoffline.meadow` with the SHA-1 fingerprint of every key you sign with (see step 5 for how
to find them). Save.

## 4. Deploy the backend

From your clone:

```bash
cd backend
firebase use --add            # pick your project, and name the alias "default"
npm ci                        # tools for the hosting pages and tests
npm --prefix functions ci     # the Cloud Functions' own packages
firebase deploy --only firestore,functions,storage,hosting
```

`firebase use --add` writes `backend/.firebaserc`, which stays out of git.

The deploy:

- uploads the Firestore rules and creates the indexes;
- uploads the Storage rules;
- compiles the functions (`backend/build-functions.cjs`) and deploys them to the region in
  `backend/functions/.env` (see below),
  along with two scheduled jobs: `expireRequests` every 5 minutes and `hourlyCare` every hour;
- renders the legal pages and app links (`backend/predeploy.cjs`) and publishes
  `backend/hosting/` to `https://<project-id>.web.app`.

The **first** deploy turns on the Google Cloud APIs that functions need (Cloud Functions, Cloud
Build, Artifact Registry, Cloud Run, Eventarc, Pub/Sub and Cloud Scheduler). This can take several
minutes. If it fails right after turning an API on, wait two minutes and run the same command
again. If it asks whether to set a cleanup policy for function images, answer **yes**; it keeps
storage costs at zero.

**Check that it worked:**

- **Functions** in the console lists about 20 functions, including `createRequest`,
  `expireRequests` and `hourlyCare`.
- **Firestore → Rules** shows Meadow's rules, and **Firestore → Indexes** lists four indexes.
  They show *Building* for a few minutes, then *Enabled*.
- **Storage → Rules** shows Meadow's rules.
- `https://<project-id>.web.app/privacy` opens the privacy notice.

**The functions region** is set in one place, `backend/functions/.env`:

```
MEADOW_REGION=us-central1
```

The backend, the app's build, the emulators and CI all read it from there. Set it **before the
first deploy**, to the region next to your Firestore database: `us-central1` for `nam5`,
`europe-west1` for `eur3`, or the database's own region if you picked a single one. Commit the
change, so builds from GitHub Actions use it too.

To move the functions later, change it, deploy again, and confirm when the CLI offers to delete
the functions in the old region. Then build and install a new app: older builds keep calling the
old region and fail with "Meadow's server can't do this right now".

## 5. Let links open the app

Invite links (`https://<project-id>.web.app/i/…`) and sign-in links open straight in Meadow once
Android trusts your web address for the app. That trust is per signing key.

1. Find the **SHA-256** fingerprint of each key you sign with:
   - The debug key, used by `assembleDebug`: run `./gradlew signingReport` and look under
     *Variant: debug*.
   - Your release key, if you make one (step 7):
     `keytool -list -v -keystore release.jks -alias <alias>`.
2. Add each one in the Firebase console: **⚙ → Project settings → General → Your apps → Add
   fingerprint**. Add the SHA-1 fingerprints too while you're there.
3. Replace the list under `"sha256"` in `backend/app-links.json` with your fingerprints. The one
   already there belongs to the original project and won't match your keys.
4. Publish them: `cd backend && firebase deploy --only hosting`. This writes
   `https://<project-id>.web.app/.well-known/assetlinks.json`.

Without this step, invite links still work through the web page's *Open in Meadow* button, but
sign-in links open in the browser, which then asks to open them on the phone.

## 6. Build the app on your computer

With `app/google-services.json` in place:

```bash
./gradlew assembleDebug
```

The APK is at `app/build/outputs/apk/debug/app-debug.apk`. Copy it to your phone, or install it
with `adb install -r app/build/outputs/apk/debug/app-debug.apk`. The build reads your project ID
from `google-services.json`, so invite and sign-in links use your own web address automatically.

Without `google-services.json` the app still builds, but opens on a "connect your backend" screen.

## 7. Build and release with GitHub Actions

This part is optional. It needs a fork (step 1).

### 7.1 Turn on Actions

GitHub pauses workflows on new forks. Open your fork's **Actions** tab and click **I understand my
workflows, go ahead and enable them**.

Then open **Settings → Actions → General → Workflow permissions** and choose **Read and write
permissions**, so the Release workflow can publish releases. Save.

### 7.2 What the workflows do

| Workflow | When it runs | What it does | Secrets it needs |
|---|---|---|---|
| **Build** (`build.yml`) | Every push and pull request | Builds the app, runs unit tests and lint; tests the functions, security rules, hosting pages; runs integration tests on the Firebase emulators and end-to-end tests on an Android emulator | None. With `GOOGLE_SERVICES_JSON` it builds against your backend; without it, it builds the "connect your backend" version |
| **Release** (`release.yml`) | When you push a tag like `v1.2.0` | Tests, builds and signs the release APK and publishes it on your fork's Releases page | All five below |
| **Screenshots** (`screenshots.yml`) | Pushes whose commit message contains `[screenshots]` | Re-renders the README images and commits them | None |

The workflows never deploy the backend. Deploy it from your computer (step 4) whenever
`backend/` changes.

### 7.3 Make a release key

Every release must be signed with the same key, or installed copies can't update. Make one once
and keep it somewhere safe, outside the repository:

```bash
keytool -genkeypair -v -keystore release.jks -alias meadow \
  -keyalg RSA -keysize 4096 -validity 10000
```

It asks for a keystore password, your name (anything is fine), and a key password. Remember both
passwords and the alias (`meadow` above). Then add the key's fingerprints as in step 5.

### 7.4 Add the secrets

In your fork, open **Settings → Secrets and variables → Actions → New repository secret** and
add each of these:

| Name | Value |
|---|---|
| `GOOGLE_SERVICES_JSON` | The whole contents of `app/google-services.json`, pasted as is |
| `MEADOW_KEYSTORE_BASE64` | The keystore as one line of base64: `base64 -w0 release.jks` on Linux, `base64 -i release.jks` on macOS |
| `MEADOW_KEYSTORE_PASSWORD` | The keystore password |
| `MEADOW_KEY_ALIAS` | The key alias, for example `meadow` |
| `MEADOW_KEY_PASSWORD` | The key password |

### 7.5 Publish a release

```bash
git tag v1.0.0
git push origin v1.0.0
```

The Release workflow appears under **Actions**. When it finishes, the signed APK is on your
fork's **Releases** page as `meadow-1.0.0.apk`. Versions must look like `v1.2.3` and must go up
with every release.

Release builds check for updates on the repository that built them, so copies installed from your
fork update from your fork's releases. People can turn the checks off in Settings. If you
publish on Google Play instead, updates come from Play.

## 8. Keeping your copy up to date

To bring in changes from the original repository:

```bash
git remote add upstream https://github.com/PCOffline/meadow.git   # once
git pull upstream main
git push
```

Then, if anything in `backend/` changed, deploy it again:

```bash
cd backend
npm ci && npm --prefix functions ci
firebase deploy --only firestore,functions,storage,hosting
```

Deploy the backend **before** releasing an app that depends on it. Finally, tag a new release
(step 7.5).

## Developing against the emulators

You can run the whole backend on your computer without touching your project. This needs
Java 21 or newer and Node 24.

```bash
cd backend
npm ci && npm --prefix functions ci
node build-functions.cjs
npx firebase emulators:start --only auth,firestore,functions,storage --project demo-meadow
```

In another terminal, point a debug build at them (`10.0.2.2` is your computer, as seen from an
Android emulator):

```bash
./gradlew installDebug -Pmeadow.emulatorHost=10.0.2.2
```

The README's *Tests* section lists every test suite and how to run it.

## Troubleshooting

**`functions predeploy error` while deploying.** The functions are compiled before upload by
`node "$PROJECT_DIR/build-functions.cjs"`. If it says *Missing packages*, run
`npm --prefix functions ci` in `backend/`. If it shows TypeScript errors, the code doesn't
compile; run the same command yourself to see them. Older copies of `firebase.json` ran
`npm --prefix "$RESOURCE_DIR" run build`, which fails on some setups with
`/bin/sh: 0: Illegal option --`; update `firebase.json` from this repository.

**`HTTP Error: 403, … API has not been used in project … or it is disabled`.** A Google Cloud API
was only just turned on. Wait two minutes and deploy again.

**`Could not build the function due to a missing permission on the build service account`.**
Newer Google Cloud projects build functions as the Compute Engine default service account and no
longer give it any roles. Grant it the Cloud Build role, then deploy again (your project number is
on the Firebase console's **Project settings** page):

```bash
gcloud projects add-iam-policy-binding <project-id> \
  --member="serviceAccount:<project-number>-compute@developer.gserviceaccount.com" \
  --role="roles/cloudbuild.builds.builder"
```

**`Repository "gcf-artifacts" not found`.** The first deploy to a region didn't create the
repository for function images. Create it, then deploy again:

```bash
gcloud artifacts repositories create gcf-artifacts --repository-format=docker \
  --location=<region> --project=<project-id>
```

**`Failed to set the IAM Policy on the Service`.** Callable functions must be public, because the
app calls them directly (each function still checks who's signed in). Google Cloud organizations
made since 2024 forbid that by default with the *Domain restricted sharing* policy
(`iam.allowedPolicyMemberDomains`). An organization admin can override it for this project only:

```bash
gcloud org-policies reset iam.allowedPolicyMemberDomains --project=<project-id>
```

This needs the *Organization Policy Administrator* role on the organization. Then deploy again.

**`Firebase Storage has not been set up` or `could not find the default bucket`.** Do step 2.5,
then deploy again.

**Firestore says *The query requires an index*.** The indexes are still building, or weren't
deployed. Check **Firestore → Indexes**; if they're missing, run
`firebase deploy --only firestore:indexes`.

**The app says "Meadow's server can't do this right now".** The app called a function that
isn't deployed where it looked. Usually the app was built with a different `MEADOW_REGION` than
the functions were deployed with: check **Functions** in the console for the region, set the same
one in `backend/functions/.env`, and build the app again. Otherwise, deploy the functions again.

**The app says "Meadow's server turned this down".** The functions can't be called from the
internet, usually because the deploy couldn't make them public: see *Failed to set the IAM
Policy* below.

**The app opens on "connect your backend".** It was built without `app/google-services.json`
(locally) or without the `GOOGLE_SERVICES_JSON` secret (in Actions).

**Sign-in links open in the browser instead of the app.** The fingerprint of the key that signed
the installed APK isn't in `backend/app-links.json`, or hosting wasn't deployed after adding it.
See step 5. Android may take a while to check again; reinstalling the app makes it check at once.

**No push notifications.** Check step 2.6. On the phone, allow Meadow's notifications and turn off
battery optimisation for it (Meadow's *Troubleshoot* screen walks through it, including the extra
steps some phone makers add).

**Release workflow fails at *Add Firebase config* or *Add signing key*.** A secret is missing or
misspelled; see step 7.4. If it fails at *Publish* with a permissions error, see step 7.1.
