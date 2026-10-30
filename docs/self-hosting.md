# Running your own Meadow

This guide sets up a full copy of Meadow:

- your own Firebase backend;
- your own build of the app;
- optionally, automatic builds and releases on GitHub.

The first time, this takes approximately one hour.

- [What you set up](#what-you-set-up)
- [Before you start](#before-you-start)
- [1. Get the code](#1-get-the-code)
- [2. Create the Firebase project](#2-create-the-firebase-project)
- [3. Register the Android app](#3-register-the-android-app)
- [4. Deploy the backend](#4-deploy-the-backend)
- [5. Let links open the app](#5-let-links-open-the-app)
- [6. Build the app on your computer](#6-build-the-app-on-your-computer)
- [7. Build, deploy and release with GitHub Actions](#7-build-deploy-and-release-with-github-actions)
- [8. Keep your copy up to date](#8-keep-your-copy-up-to-date)
- [Developing against the emulators](#developing-against-the-emulators)
- [Troubleshooting](#troubleshooting)

## What you set up

The backend of Meadow is one [Firebase](https://firebase.google.com) project. Each part has one
job:

| Firebase product | What Meadow uses it for | What you set up by hand |
|---|---|---|
| Authentication | Sign-in with email and password, email links, or anonymously | Turn on three sign-in providers |
| Cloud Firestore | Name cards, limits, requests, chats, invites | Create the database. The deploy sends the rules and indexes from `backend/` |
| Cloud Functions | All actions that need trust: invites, requests and answers, emergency unlocks, alerts, data export, account deletion, scheduled clean-up | Nothing. The deploy sends them from `backend/functions/` |
| Cloud Storage | Profile photos, chat pictures and data-export files | Create the default bucket. The deploy sends the rules from `backend/` |
| Hosting | Invite pages, the page for sign-in links, legal pages, Android app links | Nothing. The deploy sends them from `backend/hosting/` |
| Cloud Messaging | Push notifications | Nothing. Make sure that it is on |

When the project exists, one command deploys all the rules, indexes, functions and pages. You
never create collections or write rules in the console.

**Cost.** The free allowances are sufficient for a group of friends. Cloud Functions need the
pay-as-you-go **Blaze** plan, so you need a billing account. A small group usually pays $0 each
month, or a few cents to store function images.

## Before you start

You need:

- A Google account and a payment method, for the Blaze plan. Low usage costs nothing.
- **Node.js 24** with npm. To check, run `node --version`.
- **Java 21 or newer**, to build the app and run the Firebase emulators. CI uses 25. The Java in
  Android Studio works.
- **git**. For automatic builds and releases, also a GitHub account.
- The **Firebase CLI**. Run `npm install -g firebase-tools`, then `firebase login`.

If you use [mise](https://mise.jdx.dev), run `mise install` in the repository. It installs the
same Node, Java and just versions as CI (see `mise.toml`). Install the Firebase CLI as above.

The commands below are for Linux or macOS. On Windows, use WSL.

## 1. Get the code

For automatic builds and releases with GitHub Actions, first **fork** the repository on GitHub
(the *Fork* button on the repository page). Then clone your fork. If you don't need GitHub
Actions, clone the original:

```bash
git clone https://github.com/<you>/meadow.git
cd meadow
```

## 2. Create the Firebase project

### 2.1 The project

1. Open <https://console.firebase.google.com> and choose **Create a project**.
2. Type a name. The **project ID** below the name becomes part of your web address
   (`https://<project-id>.web.app`). You can't change it later. To edit it, click it.
3. Turn off Google Analytics. Meadow doesn't use it.

### 2.2 The Blaze plan and a budget

1. In the console, open **⚙ (next to Project Overview) → Usage and billing → Details &
   settings → Modify plan**. Choose **Blaze**. Link a billing account, or create one.
2. Add a budget, so that you get an email if costs go up:
   1. Open <https://console.cloud.google.com/billing> and choose your billing account.
   2. Open **Budgets & alerts → Create budget**.
   3. Set the scope to your project and the amount to, for example, $1. Keep the default alert
      thresholds.

### 2.3 Authentication

1. Open **Build → Authentication → Get started.**
2. On the **Sign-in method** tab, add these providers:
   - **Email/Password.** Turn on the two switches: *Email/Password* and **Email link
     (passwordless sign-in)**. Save.
   - **Anonymous.** Turn it on. Save. Then people can try Meadow before they add an email.
3. On the **Settings** tab, open **Authorized domains**. Make sure that the list has
   `<project-id>.web.app` and `<project-id>.firebaseapp.com`. They are there by default. Sign-in
   links go through the second address and open on the first.
4. Optional: on the **Templates** tab, change the sender name and the text of the sign-in and
   password-reset emails.

### 2.4 Cloud Firestore

1. Open **Build → Firestore Database → Create database.**
2. If the console asks for an edition, choose **Standard**. Keep the database ID **(default)**.
   Meadow uses the default database.
3. Choose a **location** near the people who use Meadow. You can't change it later. If you don't
   know, choose `nam5 (United States)` or `eur3 (Europe)`. Write it down: the functions must run
   near it (step 4).
4. Choose **Start in production mode**, then **Create**.

Leave the database empty. The deploy in step 4 replaces the default rules with
`backend/firestore.rules` and creates the indexes in `backend/firestore.indexes.json`. The app
creates the collections when people use it.

### 2.5 Cloud Storage

1. Open **Build → Storage → Get started.**
2. Choose a location. `US-CENTRAL1`, `US-EAST1` and `US-WEST1` have a free allowance. Other
   regions cost money from the first byte, but Meadow stores very little.
3. Choose **Start in production mode**, then **Create**. This creates the default bucket,
   `<project-id>.firebasestorage.app`. Profile photos must be in this bucket, or in
   `<project-id>.appspot.com` for an older project. The Firestore rules refuse photo links to
   other buckets.

You don't need to set up more. The deploy (step 4) sends `backend/storage.rules` and sets the
lifecycle rule of the bucket. The Storage rules do these things:

- Each person can change only their own photo.
- Their Bumblebees, and the people they look after, can see the photo.
- Only the two people in a chat can see its photos and GIFs (under `chats/`).
- Only the owner of an export file can read it.

When a chat closes, its pictures are deleted. A picture that nobody sent in a message is deleted
after one hour. Before the app uploads a picture, it records it in `chatUploads`, as the rules
require. Then `hourlyCare` deletes the pictures that nobody sent.

### 2.6 Cloud Messaging

Open **⚙ → Project settings → Cloud Messaging**. Make sure that **Firebase Cloud Messaging API
(V1)** shows *Enabled*. It is on by default for new projects. If it shows *Disabled*, use the ⋮
menu next to it to enable it. You don't need the server key or the sender ID.

## 3. Register the Android app

1. Open **⚙ → Project settings → General**. Under **Your apps**, click the **Android** icon.
2. **Android package name:** `dev.pcoffline.meadow`. Leave the nickname and SHA-1 empty. You add
   fingerprints in step 5.
3. Click **Register app**. Then click **Download google-services.json**, and save it as
   `app/google-services.json` in your clone.
4. The next two screens show how to add the Google services Gradle plugin and the Firebase BoM.
   Meadow already has both (see `gradle/libs.versions.toml`). Click **Next** on the two screens,
   then **Continue to console**. Meadow doesn't use `firebase-analytics`.

`google-services.json` contains identifiers, not secrets. Each APK contains the same values. The
security rules from step 4 protect your data. But `.gitignore` keeps the file out of git. Thus
each copy of Meadow uses its own project, and other people can't use your quota with your key.

Optional, but recommended: limit the key to your app.

1. Open <https://console.cloud.google.com/apis/credentials>.
2. Edit the key with the name *Android key (auto created by Firebase)*.
3. Under **Application restrictions**, choose **Android apps**.
4. Add `dev.pcoffline.meadow` with the SHA-1 fingerprint of each key that you sign with. Step 5
   tells you how to find them.
5. Save.

## 4. Deploy the backend

From your clone:

```bash
cd backend
firebase use --add            # choose your project, and use the alias "default"
npm ci                        # tools for the hosting pages and tests
npm --prefix functions ci     # the packages of the Cloud Functions
firebase deploy --only firestore,functions,storage,hosting
```

The lifecycle rule needs a Google Cloud sign-in and a Firebase sign-in. Install
[gcloud](https://cloud.google.com/sdk/docs/install) and run
`gcloud auth application-default login` one time. Without it, the deploy stops after the rules
and tells you why.

`firebase use --add` writes `backend/.firebaserc`, which stays out of git. With
[just](https://just.systems), use `just setup`, `just use-project` and `just deploy`. Run `just` to
see the other commands, for example `just deploy-rules` or `just deploy-functions`.

The deploy does these steps:

1. It sends the Firestore rules and creates the indexes.
2. It sends the Storage rules.
3. It compiles the functions (`backend/build-functions.cjs`). Then it deploys them to the
   region in `backend/functions/.env` (see below), with two scheduled jobs: `expireRequests`
   every 5 minutes and `hourlyCare` every hour.
4. It makes the legal pages and the app links (`backend/predeploy.cjs`). Then it publishes
   `backend/hosting/` to `https://<project-id>.web.app`.
5. When all of this is published, it sets the lifecycle rule of the bucket
   (`backend/apply-lifecycle.cjs`, from `backend/storage-lifecycle.json`). It replaces only the
   rule of Meadow (a delete rule for `exports/`) and keeps all other rules on the bucket.
6. Then it makes sure that the Storage rules can read Firestore
   (`backend/check-rules-access.cjs`). The rules read chats and accounts to decide who sees a
   photo. If they can't, the deploy fails, but the backend is already published. In Deliver, the
   app release doesn't run then.

The app deletes each data export when it downloads it. The lifecycle rule deletes the exports
that nobody downloaded after one day. Cloud Storage applies lifecycle rules in approximately one
day, so an export stays for two days at most.

The **first** deploy turns on the Google Cloud APIs that the functions need: Cloud Functions,
Cloud Build, Artifact Registry, Cloud Run, Eventarc, Pub/Sub and Cloud Scheduler. This can take
some minutes. If the deploy fails just after it turns on an API, wait two minutes and run the
same command again. If the deploy asks to set a cleanup policy for function images, answer
**yes**. Then the storage cost stays at zero. If it asks to grant a role to Cloud Storage for
Firebase for cross-service rules, answer **yes**.

**Make sure that the deploy worked:**

- **Functions** in the console shows approximately 20 functions, with `createRequest`,
  `expireRequests` and `hourlyCare`.
- **Firestore → Rules** shows the Meadow rules. **Firestore → Indexes** shows four indexes. They
  show *Building* for some minutes, then *Enabled*.
- **Storage → Rules** shows the Meadow rules. In the Google Cloud console, the **Lifecycle** tab
  of the bucket shows the Meadow rule: delete objects under `exports/` after 1 day. Your own
  rules stay next to it.
- `https://<project-id>.web.app/privacy` opens the privacy notice.

**The functions region** is in one file, `backend/functions/.env`:

```
MEADOW_REGION=europe-west1
```

The backend, the app build, the emulators and CI all read the region from this file. No other
part of the code has a region. If the value is missing, the deploy and the app build stop and
tell you.

Set the region **before the first deploy**. Use the region near your Firestore database:
`us-central1` for `nam5`, `europe-west1` for `eur3`, or the region of the database if you chose
one region. Commit the change, so that builds from GitHub Actions use it too.

To move the functions later:

1. Change the region and deploy again.
2. When the CLI asks to delete the functions in the old region, agree.
3. Build and install a new app. Older builds still call the old region, and show "Meadow isn't
   available now".

## 5. Let links open the app

Invite links (`https://<project-id>.web.app/i/…`) and sign-in links
(`https://<project-id>.firebaseapp.com/__/auth/links…`) open in Meadow when Android trusts the
two addresses for the app. The two addresses serve the same Hosting site, so one file is
sufficient. Android trusts each signing key separately.

1. Find the **SHA-256** fingerprint of each key that you sign with:
   - The debug key, for `assembleDebug`: run `./gradlew signingReport` and look under
     *Variant: debug*.
   - Your release key, if you make one (step 7):
     `keytool -list -v -keystore release.jks -alias <alias>`.
2. Add each fingerprint in the Firebase console: **⚙ → Project settings → General → Your apps →
   Add fingerprint**. Also add the SHA-1 fingerprints.
3. In `backend/app-links.json`, replace the list under `"sha256"` with your fingerprints. The
   fingerprint in the file is for the original project. It doesn't match your keys.
4. Publish them: `cd backend && firebase deploy --only hosting`. This writes
   `/.well-known/assetlinks.json` on the two addresses.

Without this step, links open in the browser. This also occurs for a build with a key that isn't
in the list, for example a release from GitHub when the list has only the debug key. The two
pages in the browser have an *Open in Meadow* button, so the links still work with one more tap.

## 6. Build the app on your computer

Put `app/google-services.json` in its place. Then run:

```bash
./gradlew assembleDebug
```

The APK is at `app/build/outputs/apk/debug/app-debug.apk`. Copy it to your phone, or install it
with `adb install -r app/build/outputs/apk/debug/app-debug.apk`. The build reads your project ID
from `google-services.json`, so invite and sign-in links use your own web address.

Without `google-services.json`, the app builds, but it opens on the "Meadow can't start" screen.

## 7. Build, deploy and release with GitHub Actions

This part is optional. You need a fork (step 1).

### 7.1 Turn on Actions

GitHub stops workflows on new forks. Open the **Actions** tab of your fork and click
**I understand my workflows, go ahead and enable them**.

Keep **Settings → Actions → General → Workflow permissions** on **Read repository contents and
packages permissions**. Each workflow asks only for what it needs: Build only reads, Deliver can
tag and publish releases, and Screenshots can commit images.

**Settings → Actions → General → Actions permissions** can limit the actions that can run. If it
does, allow actions by GitHub and verified creators. Also add this action to the list of allowed
actions. Build and Screenshots use it to start their Android emulator:

```
reactivecircus/android-emulator-runner@*
```

Each workflow pins its actions to a commit, so a changed tag can't change what runs. Dependabot
proposes updates each week.

### 7.2 What the workflows do

| Workflow | When it runs | What it does | Secrets it needs |
|---|---|---|---|
| **Build** (`build.yml`) | Each pull request and each push to `main`. For a different branch, start it from the Actions tab | Builds the app and runs unit tests and lint. Tests the functions, security rules and hosting pages. Runs integration tests on the Firebase emulators and end-to-end tests on an Android emulator | None. With `GOOGLE_SERVICES_JSON`, it builds for your backend. Without it, it builds the "Meadow can't start" version |
| **Deliver** (`deliver.yml`) | After Build passes on `main` (when on, see 7.5 and 7.6), when you push a tag like `v0.4.0`, or by hand | Deploys the backend to Firebase. Then it tests, builds and signs the release APK, and publishes it on the Releases page of your fork | The five secrets in 7.4 to release. The Google Cloud access in 7.6 to deploy |
| **Screenshots** (`screenshots.yml`) | Pushes to `main` that change the app's screens, or by hand | Makes the README images again, commits them to the branch, and runs Build on that commit | None |

Until you turn on automatic deploys (7.6), deploy the backend from your computer (step 4) each
time `backend/` changes.

### 7.3 Make a release key

Sign all releases with the same key. If you don't, installed copies can't update. Make the key
one time, and keep it in a safe location outside the repository:

```bash
keytool -genkeypair -v -keystore release.jks -alias meadow \
  -keyalg RSA -keysize 4096 -validity 10000
```

The command asks for a keystore password, your name (any name is good), and a key password.
Remember the two passwords and the alias (`meadow` above). Then add the fingerprints of the key,
as in step 5.

### 7.4 Add the secrets

In your fork, open **Settings → Secrets and variables → Actions → New repository secret**. Add
each of these secrets:

| Name | Value |
|---|---|
| `GOOGLE_SERVICES_JSON` | All of `app/google-services.json`, with no changes |
| `MEADOW_KEYSTORE_BASE64` | The keystore as one line of base64: `base64 -w0 release.jks` on Linux, `base64 -i release.jks` on macOS |
| `MEADOW_KEYSTORE_PASSWORD` | The keystore password |
| `MEADOW_KEY_ALIAS` | The key alias, for example `meadow` |
| `MEADOW_KEY_PASSWORD` | The key password |

### 7.5 Publish releases

The app and the backend have separate versions. Both use
[semantic versioning](https://semver.org). App releases have tags like `v0.4.2`. Backend deploys
have tags like `backend-v0.7.0` (7.6).

- The latest tag sets the version. Each new version is the latest tag, with one part increased.
- Without tags, the two start at 0.1.0. A fork that keeps the upstream tags continues from them.
- The minor and patch parts of the app stay below 1000. The Android version code is
  major × 1,000,000 + minor × 1,000 + patch, and the build stops at 1000.

Automatic runs increase a version only when that part of Meadow changed. The commits that
changed it set the part to increase:

| In a commit message | Meaning | 0.4.2 becomes | 1.4.2 becomes |
|---|---|---|---|
| nothing | a fix | 0.4.3 | 1.4.3 |
| `[minor]` | a new feature | 0.5.0 | 1.5.0 |
| `[major]` or `[breaking]` | an incompatible change | 0.5.0 | 2.0.0 |

Below 1.0.0, an incompatible change increases the minor part, as SemVer says for 0.x versions.
Thus commits alone never make Meadow 1.0.0. To go to 1.0.0, run Deliver by hand and choose
*major* (0.9.3 → 1.0.0). After that, versions continue from the new tag.

There are three ways to release.

**Automatically.** In **Settings → Secrets and variables → Actions → Variables**, add
`AUTO_RELEASE` with the value `true`. Then each push to `main` that passes Build and changes the
app publishes a release. Changes that don't touch the app (docs, the backend code, workflows)
don't make a release. `[skip release]` in the message of the last commit stops one release.

**By hand.** Open **Actions → Deliver → Run workflow** on `main`. Under *Publish an APK* and
*Deploy the backend*, choose the part of each version to increase (*patch*, *minor* or *major*).
Or choose *auto*, to read it from the commits as automatic runs do, or *none*. If you choose a
part, Deliver makes a new version also when nothing changed.

**With a tag**, to set the exact version. `just release 0.4.0` does the same. The version must
be above the latest release. The commit must be on `main` and must have passed Build:

```bash
git tag v0.4.0
git push origin v0.4.0
```

When the workflow is done, the signed APK is on the **Releases** page of your fork as
`meadow-<version>.apk`. Versions must have the form `1.2.3` and must increase with each release.
Each installed copy updates to a new release. Thus Deliver releases only commits on `main` that
passed Build. It also runs the tests before it puts the signing key on the runner.

Release builds look for updates on the repository that built them. Thus copies from your fork
update from the releases of your fork. People can turn off these checks in Settings. If you
publish on Google Play, updates come from Google Play.

### 7.6 Deploy the backend automatically

The Deliver workflow can deploy `backend/` (rules, indexes, functions and hosting) after each
push to `main` that passes Build and changes `backend/` or `legal/`. If a release is in the same
run, the release waits for the deploy. Thus a new app never uses an old backend.

GitHub needs permission to deploy to your project. The safer way stores no key: GitHub signs in
with Workload Identity Federation, and only the Deliver workflow on the `main` branch of your fork
can sign in. Sign in to [gcloud](https://cloud.google.com/sdk/docs/install) as the project owner,
then run:

```bash
just deploy-access <project-id> <owner>/<repo>
# or: .github/scripts/setup-deploy-access.sh <project-id> <owner>/<repo>
```

Type the repository name exactly as GitHub shows it, for example `PCOffline/meadow`. The script
creates a `github-deploy` service account with the roles that a deploy needs:

- It can act only as the service account of the functions, not as other accounts in the
  project.
- It can read the IAM policy of the project, to check the Storage role below.
- For a public repository, the sign-in also checks the numeric ID of the repository. Thus a
  different repository with the same name later can't deploy.

The script also lets the Storage rules read Firestore. A deploy from CI can't grant this role.
The script needs the beta commands of gcloud. Install them first: `gcloud components install beta`.

The script shows the variables to add under **Settings → Secrets and variables → Actions →
Variables**:

| Variable | Value |
|---|---|
| `FIREBASE_PROJECT` | Your project ID |
| `GCP_WORKLOAD_IDENTITY_PROVIDER` | The provider name from the script (`projects/…/providers/github`) |
| `GCP_SERVICE_ACCOUNT` | `github-deploy@<project-id>.iam.gserviceaccount.com` |
| `AUTO_DEPLOY` | `true`, to deploy after each push to `main` that passes |

To use a key, or if your organisation doesn't allow identity federation:

1. Create a key for the service account: **IAM → Service accounts → github-deploy → Keys → Add
   key → JSON**.
2. Add its contents as the secret `FIREBASE_SERVICE_ACCOUNT`.
3. Don't add the two `GCP_` variables.

Many organisations block key creation by default. The way without a key doesn't have this
problem.

Without `AUTO_DEPLOY`, deploy by hand from **Actions → Deliver → Run workflow**. Under *Deploy
the backend*, choose how to increase the version. You can also deploy from your computer, as in
step 4.

- `[skip deploy]` in a commit message stops one automatic deploy.
- Each deploy of a changed backend gets a version tag (`backend-v0.7.0`, increased as in 7.5).
  The next deploy occurs only when `backend/` or `legal/` changed after it.
- A deploy by hand with *auto* deploys an unchanged backend again with the same version.

A deploy removes the functions that are not in the code, with no question, as
`firebase deploy --force` does.

## 8. Keep your copy up to date

To get changes from the original repository:

```bash
git remote add upstream https://github.com/PCOffline/meadow.git   # one time
git pull upstream main
git push
```

With automatic deploys and releases on (7.5 and 7.6), the push to `main` deploys the backend
first, then publishes a new release. If they are off and `backend/` changed, deploy again:

```bash
cd backend
npm ci && npm --prefix functions ci
firebase deploy --only firestore,functions,storage,hosting
```

Deploy the backend **before** you release an app that needs it. Then publish a new release
(step 7.5).

## Developing against the emulators

You can run all of the backend on your computer, with no effect on your project. You need Java
21 or newer and Node 24.

```bash
cd backend
npm ci && npm --prefix functions ci
npm run emulators              # or `just emulators`
```

The first start adds demo people:

- Sam, with two Bumblebees, limits, a week of screen time, an open request and a chat;
- Bee, Robin and Kai.

Sign in as `sam@meadow.test`, `bee@meadow.test`, `robin@meadow.test` or `kai@meadow.test` with the
password `meadow-demo`. When you stop the emulators (Ctrl+C), they save all your changes in
`backend/.emulator-data/`. The next start loads them again. To start with new data, run
`npm run emulators -- --fresh`.

In a different terminal, connect a debug build to the emulators. From an Android emulator,
`10.0.2.2` is your computer:

```bash
./gradlew installDebug -Pmeadow.emulatorHost=10.0.2.2
```

[CONTRIBUTING.md](../CONTRIBUTING.md#checks) lists each test suite and how to run it. To see each
request and why the rules allowed or refused it, read [Debugging Meadow](debugging.md).

## Troubleshooting

**`functions predeploy error` during the deploy.** Before the upload,
`node "$PROJECT_DIR/build-functions.cjs"` compiles the functions.

- If it shows *Missing packages*, run `npm --prefix functions ci` in `backend/`.
- If it shows TypeScript errors, the code doesn't compile. Run the same command to see the
  errors.
- Older copies of `firebase.json` ran `npm --prefix "$RESOURCE_DIR" run build`. On some systems,
  this fails with `/bin/sh: 0: Illegal option --`. Get `firebase.json` from this repository.

**`HTTP Error: 403, … API has not been used in project … or it is disabled`.** A Google Cloud API
is new. Wait two minutes and deploy again.

**The deploy fails with *Storage rules may not read Firestore*, or chat photos fail.** Cloud
Storage needs a role to read Firestore from the Storage rules. Without it, Storage refuses chat
photos and the profile photos of friends. The check runs after the deploy publishes the
backend, so only the app release waits for the fix. Run `.github/scripts/setup-deploy-access.sh`
again, or run these commands as the project owner:

```bash
gcloud beta services identity create --service firebasestorage.googleapis.com --project <project-id>
gcloud projects add-iam-policy-binding <project-id> --role roles/firebaserules.firestoreServiceAgent \
  --member "serviceAccount:service-<project-number>@gcp-sa-firebasestorage.iam.gserviceaccount.com" \
  --condition None
```

**`Could not build the function due to a missing permission on the build service account`.**
Newer Google Cloud projects build functions as the Compute Engine default service account, and
give it no roles. Give it the Cloud Build role, then deploy again. Your project number is on the
**Project settings** page of the Firebase console:

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

**`Failed to set the IAM Policy on the Service`.** Callable functions must be public, because
the app calls them directly. Each function still checks who is signed in. The trigger and
scheduled functions stay private. Google Cloud organizations from 2024 and later, for example
one from Google Workspace, prevent public functions by default with the *Domain restricted
sharing* policy. Then the deploy can't make the functions public, and each call fails with
`UNAUTHENTICATED`.

Override the policy for this project only. The Owner role on the project is not sufficient.
Organization policies are set on the organization, and you need the *Organization Policy
Administrator* role there. In the [Google Cloud console](https://console.cloud.google.com):

1. In the project picker at the top, choose your **organization**, not the project. Open
   **IAM & Admin → IAM → Grant access**. Enter your account, choose the role
   **Organization Policy Administrator**, and save. If you can't, ask a Google Workspace super
   admin to do it.
2. In the picker, choose your project again. Open **IAM & Admin → Organization policies** and
   find **Domain restricted sharing** (`iam.allowedPolicyMemberDomains`). Choose
   **Manage policy**, then **Override parent's policy**. Set policy enforcement to **Replace**.
   Choose **Add a rule**, set policy values to **Allow All**, then choose **Set policy**.
3. If **Restrict allowed policy members in IAM allow policies**
   (`iam.managed.allowedPolicyMembers`) is also enforced, override it the same way, with
   enforcement **Off**.

When you have the role, this command does step 2:
`gcloud org-policies reset iam.allowedPolicyMemberDomains --project=<project-id>`.

Wait two minutes. Then make the callable functions public. From `backend/`:

```bash
for f in $(grep -rhoE "export const \w+ = onCall" functions/src | awk '{print $3}'); do
  gcloud functions add-invoker-policy-binding "$f" --gen2 --member=allUsers \
    --region=<region> --project=<project-id>
done
```

This takes effect immediately, with no new deploy, and later deploys keep it. To check one
function, run
`gcloud run services get-iam-policy createprofile --region=<region> --project=<project-id>`. The
output must show `allUsers` with `roles/run.invoker`.

**The functions can't get to Firestore.** Their log shows `7 PERMISSION_DENIED: Missing or
insufficient permissions`, and the app gets `INTERNAL`. The functions run as the Compute Engine
default service account (`<project-number>-compute@developer.gserviceaccount.com`). Newer Google
Cloud projects give it no roles, as with the Cloud Build role above. Give it only the roles that
the functions use: Firestore, Storage (data exports), Authentication (account deletion), Cloud
Messaging (notifications), and calls to the functions from Firestore triggers and schedules:

```bash
SA=$(gcloud run services describe createprofile --region=<region> --project=<project-id> \
  --format='value(spec.template.spec.serviceAccountName)')
for role in roles/datastore.user roles/storage.objectAdmin roles/firebaseauth.admin \
  roles/firebasecloudmessaging.admin roles/eventarc.eventReceiver roles/run.invoker; do
  gcloud projects add-iam-policy-binding <project-id> --member="serviceAccount:$SA" \
    --role="$role" --condition=None
done
```

This takes effect in one minute, with no new deploy.

**`Firebase Storage has not been set up` or `could not find the default bucket`.** Do step 2.5,
then deploy again.

**Firestore says *The query requires an index*.** The indexes are not ready, or the deploy didn't
send them. Look at **Firestore → Indexes**. If they are missing, run
`firebase deploy --only firestore:indexes`.

**The app shows "Meadow isn't available now", or stays on "Opening your garden…".** A server
operation failed. The app doesn't show the cause to people. To see the cause on screen and in the
log, build the app with trace mode (read [Debugging Meadow](debugging.md)). Without trace mode,
`adb logcat -s MeadowSync MeadowBackend MeadowFirestore` also shows it. Find the code in the log:

- `PERMISSION_DENIED` on a read of your profile (`users/<uid>`): the security rules refused it.
  Usually the rules were never deployed, so Firestore still has its locked start rules. Run
  `firebase deploy --only firestore,storage` from `backend/`.
- `NOT_FOUND` or `FAILED_PRECONDITION` from Firestore: the app can't find a database. Meadow uses
  the database with the name `(default)`, in Firestore Native mode. Check **Firestore** in the
  console.
- `NOT_FOUND` from a Cloud Function: the function isn't in the region where the app looked.
  Usually the app build used a different `MEADOW_REGION` from the functions deploy. Find the
  region under **Functions** in the console. Set the same region in `backend/functions/.env`,
  and build the app again. If the regions are the same, deploy the functions again.
- `UNAUTHENTICATED` or `PERMISSION_DENIED` from a Cloud Function, just after sign-in: Google Cloud
  refused the call before it got to Meadow, because the functions aren't public. The app asks you
  to sign in again. A trace build also shows `Cloud Function failed: UNAUTHENTICATED`. Read
  *Failed to set the IAM Policy* above.
- `INTERNAL` from a Cloud Function: the function crashed. `firebase functions:log` shows the
  cause. If the log shows `PERMISSION_DENIED: Missing or insufficient permissions`, read
  *The functions can't get to Firestore* above.

If the app shows "Meadow can't connect", or the loading screen says that it takes longer than
usual, the phone can't get to Firebase. Check its connection.

**The app opens on "Meadow can't start".** The build had no `app/google-services.json` (on your
computer) or no `GOOGLE_SERVICES_JSON` secret (in Actions).

**Sign-in links open in the browser, not in the app.** One of these is the cause:

- The fingerprint of the key that signed the installed APK isn't in `backend/app-links.json`.
- You didn't deploy hosting after you added the fingerprint.

Read step 5. Android can take some time to check again. If you install the app again, Android
checks immediately.

**No push notifications.** Do step 2.6 again. On the phone, allow notifications for Meadow and
turn off battery optimisation for it. The *Troubleshoot* screen in Meadow shows how, with the
extra steps for some phone makers.

**Deliver fails at *Add Firebase config* or *Add signing key*.** A secret is missing, or its name
is wrong. Read step 7.4. If Deliver fails at *Publish* with a permissions error, check the Actions
settings of your organization, if the fork is in one. They must let workflows use the
permissions that they ask for.

**Deliver fails at *Sign in to Google Cloud*.** For sign-in without a key, check these items:

- `GCP_WORKLOAD_IDENTITY_PROVIDER` is the full name that the setup script showed.
- You ran the script with the repository name exactly as GitHub shows it. If you renamed the
  repository, run the script again with the new name.
- The run is on `main`. Only runs on `main` can sign in.

If your organisation limits identity federation, use a key (7.6).

**Deliver fails at *Deploy* with `Permission denied` or `does not have permission`.** The
`github-deploy` service account doesn't have a role that it needs, often for a new product. The
error names the permission. Under **IAM**, give the role that has it, or run the setup script
again.
