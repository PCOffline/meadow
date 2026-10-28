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
- [7. Build, deploy and release with GitHub Actions](#7-build-deploy-and-release-with-github-actions)
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
Java and just versions as CI (see `mise.toml`); install the Firebase CLI as above.

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
   the second one, and land on the first.
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
   `<project-id>.firebasestorage.app`. Profile photos must live there (or in an older project's
   `<project-id>.appspot.com`): the Firestore rules refuse photo links to any other bucket.

Nothing else to set up: the deploy (section 4) uploads `backend/storage.rules`, which lets
people change only their own photo, lets their Bumblebees and the people they look after see it,
keeps each chat's photos and GIFs (under `chats/`) to its two people, and keeps each export file
to its owner, and sets the bucket's lifecycle rule. A chat's pictures are deleted when it closes,
and any picture never sent in a message is deleted after an hour (the app notes each in
`chatUploads` before uploading it, as the rules require, and `hourlyCare` sweeps).

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

Setting the lifecycle rule needs a Google Cloud sign-in as well as the Firebase one: install
[gcloud](https://cloud.google.com/sdk/docs/install) and run `gcloud auth application-default login`
once. Without it, the deploy stops after the rules and says so.

`firebase use --add` writes `backend/.firebaserc`, which stays out of git. With
[just](https://just.systems) the same is `just setup`, `just use-project` and `just deploy`; `just`
lists the other commands, such as `just deploy-rules` or `just deploy-functions`.

The deploy:

- uploads the Firestore rules and creates the indexes;
- uploads the Storage rules, then sets the bucket's lifecycle rule (`backend/apply-lifecycle.cjs`,
  from `backend/storage-lifecycle.json`): data exports are deleted as soon as the app downloads
  them, and the rule deletes any never downloaded once it is a day old (Cloud Storage applies
  lifecycle rules within about a day of that, so within two days at most). It replaces only
  Meadow's own rule (a delete rule for `exports/`) and keeps any other rules on the bucket;
- compiles the functions (`backend/build-functions.cjs`) and deploys them to the region in
  `backend/functions/.env` (see below),
  along with two scheduled jobs (`expireRequests` every 5 minutes and `hourlyCare` every hour);
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
- **Storage → Rules** shows Meadow's rules, and the bucket's **Lifecycle** tab (in the Google
  Cloud console) has Meadow's rule, delete objects under `exports/` after 1 day, next to any
  rules you added yourself.
- `https://<project-id>.web.app/privacy` opens the privacy notice.

**The functions region** is set in one place, `backend/functions/.env`:

```
MEADOW_REGION=europe-west1
```

The backend, the app's build, the emulators and CI all read it from there, and nothing else in
the code names a region: if it's missing, the deploy and the app's build stop and say so. Set it
**before the first deploy**, to the region next to your Firestore database: `us-central1` for `nam5`,
`europe-west1` for `eur3`, or the database's own region if you picked a single one. Commit the
change, so builds from GitHub Actions use it too.

To move the functions later, change it, deploy again, and confirm when the CLI offers to delete
the functions in the old region. Then build and install a new app: older builds keep calling the
old region and fail with "Meadow isn't available right now".

## 5. Let links open the app

Invite links (`https://<project-id>.web.app/i/…`) and sign-in links
(`https://<project-id>.firebaseapp.com/__/auth/links…`) open straight in Meadow once Android trusts
both addresses for the app. They serve the same Hosting site, so one file covers both. That trust
is per signing key.

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
   `/.well-known/assetlinks.json` on both addresses.

Without this step, or for a build signed with a key that isn't listed (say, a release from
GitHub when only the debug key is), links open in the browser instead. Both pages there have an
*Open in Meadow* button that hands the link to the app, so they still work, with one more tap.

## 6. Build the app on your computer

With `app/google-services.json` in place:

```bash
./gradlew assembleDebug
```

The APK is at `app/build/outputs/apk/debug/app-debug.apk`. Copy it to your phone, or install it
with `adb install -r app/build/outputs/apk/debug/app-debug.apk`. The build reads your project ID
from `google-services.json`, so invite and sign-in links use your own web address automatically.

Without `google-services.json` the app still builds, but opens on a "connect your backend" screen.

## 7. Build, deploy and release with GitHub Actions

This part is optional. It needs a fork (step 1).

### 7.1 Turn on Actions

GitHub pauses workflows on new forks. Open your fork's **Actions** tab and click **I understand my
workflows, go ahead and enable them**.

Leave **Settings → Actions → General → Workflow permissions** on **Read repository contents and
packages permissions**. Each workflow asks for exactly what it needs: Build only reads, Deliver
may tag and publish releases, and Screenshots may commit images.

If **Settings → Actions → General → Actions permissions** limits which actions may run, allow
actions by GitHub and verified creators, and add this one to the list of allowed actions (Build and
Screenshots start their Android emulator with it):

```
reactivecircus/android-emulator-runner@*
```

Every workflow pins its actions to a commit, so a changed tag can't change what runs; Dependabot
proposes updates weekly.

### 7.2 What the workflows do

| Workflow | When it runs | What it does | Secrets it needs |
|---|---|---|---|
| **Build** (`build.yml`) | Every pull request and every push to `main` (run it from the Actions tab for any other branch) | Builds the app, runs unit tests and lint; tests the functions, security rules, hosting pages; runs integration tests on the Firebase emulators and end-to-end tests on an Android emulator | None. With `GOOGLE_SERVICES_JSON` it builds against your backend; without it, it builds the "connect your backend" version |
| **Deliver** (`deliver.yml`) | After Build passes on `main` (when turned on, see 7.5 and 7.6), when you push a tag like `v0.4.0`, or by hand | Deploys the backend to Firebase, then tests, builds and signs the release APK and publishes it on your fork's Releases page | The five in 7.4 to release; the Google Cloud access in 7.6 to deploy |
| **Screenshots** (`screenshots.yml`) | Pushes to `main` that change the app's screens, or by hand | Re-renders the README images, commits them to the branch, and runs Build on that commit | None |

Until you turn on automatic deploys (7.6), deploy the backend from your computer (step 4)
whenever `backend/` changes.

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

### 7.5 Publish releases

The app and the backend have their own versions, both following
[semantic versioning](https://semver.org): the app's releases are tagged `v0.4.2`, the backend's
deploys `backend-v0.7.0` (7.6). The latest tag is the source of truth: each new version is it,
raised by one part. Without tags, both start at 0.1.0; a fork that keeps upstream's tags carries on
from them. The app's minor and patch parts stay below 1000: Android's version code is
major × 1,000,000 + minor × 1,000 + patch, and the build stops at 1000.

Automatic runs raise a version only when that part of Meadow changed, by the commits that changed
it:

| In a commit message | Means | 0.4.2 becomes | 1.4.2 becomes |
|---|---|---|---|
| nothing | a fix | 0.4.3 | 1.4.3 |
| `[minor]` | a new feature | 0.5.0 | 1.5.0 |
| `[major]` or `[breaking]` | an incompatible change | 0.5.0 | 2.0.0 |

Below 1.0.0, an incompatible change raises the minor part, as SemVer has it for 0.x versions, so
commits alone never take Meadow to 1.0.0. That's a choice made by hand: run Deliver and choose
*major* (0.9.3 → 1.0.0). From then on, versions follow on from the new tag.

**Automatically.** In **Settings → Secrets and variables → Actions → Variables**, add
`AUTO_RELEASE` with the value `true`. From then on, every push to `main` that passes Build and
changes the app publishes a release. Changes that don't touch the app (docs, the backend's code,
workflows) don't make one, and `[skip release]` in the last commit's message skips one.

**By hand.** Open **Actions → Deliver → Run workflow** on `main`. Under *Publish an APK* and
*Deploy the backend*, choose which part of each version to raise (*patch*, *minor* or *major*),
*auto* to read it from the commits as automatic runs do, or *none*. Choosing a part makes a new
version even when nothing changed.

**With a tag**, to pick the exact version (`just release 0.4.0` does the same). It must be above
the latest release, on a commit that is on `main` and passed Build:

```bash
git tag v0.4.0
git push origin v0.4.0
```

Each way, the signed APK appears on your fork's **Releases** page as `meadow-<version>.apk` when
the workflow finishes. Versions must look like `1.2.3` and must go up with every release. Every
installed copy updates to a new release, so Deliver only releases commits on `main` that passed
Build, and runs the tests before the signing key is on the runner.

Release builds check for updates on the repository that built them, so copies installed from your
fork update from your fork's releases. People can turn the checks off in Settings. If you
publish on Google Play instead, updates come from Play.

### 7.6 Deploy the backend automatically

The Deliver workflow can deploy `backend/` (rules, indexes, functions and hosting) after every
push to `main` that passes Build and changes `backend/` or `legal/`. When a release goes out in
the same run, it waits for the deploy, so a new app never meets an old backend.

GitHub needs permission to deploy to your project. The safer way stores no key: GitHub signs in
with Workload Identity Federation, and only the Deliver workflow on your fork's `main` can. With
[gcloud](https://cloud.google.com/sdk/docs/install) signed in as the project's owner, run:

```bash
just deploy-access <project-id> <owner>/<repo>
# or: .github/scripts/setup-deploy-access.sh <project-id> <owner>/<repo>
```

Use the repository name exactly as GitHub shows it, for example `PCOffline/meadow`. The script
creates a `github-deploy` service account with the roles a deploy needs. It may act as the
functions' service account only, not as any other in the project, and the sign-in also checks
the repository's numeric ID (for a public repository), so another repository later given the same
name can't deploy. It prints the variables to add under **Settings → Secrets and variables →
Actions → Variables**:

| Variable | Value |
|---|---|
| `FIREBASE_PROJECT` | Your project ID |
| `GCP_WORKLOAD_IDENTITY_PROVIDER` | The provider name the script printed (`projects/…/providers/github`) |
| `GCP_SERVICE_ACCOUNT` | `github-deploy@<project-id>.iam.gserviceaccount.com` |
| `AUTO_DEPLOY` | `true`, to deploy after every passing push to `main` |

If you'd rather use a key, or your organisation doesn't allow identity federation: create a key
for the service account (**IAM → Service accounts → github-deploy → Keys → Add key → JSON**), add
its contents as the secret `FIREBASE_SERVICE_ACCOUNT`, and leave the two `GCP_` variables out.
Many organisations block key creation by default; the keyless way avoids that.

Without `AUTO_DEPLOY`, deploy by hand from **Actions → Deliver → Run workflow** (choose how to
raise the version under *Deploy the backend*), or from your computer as in step 4. `[skip deploy]` in a commit message skips one
automatic deploy. Each deploy of a changed backend is tagged with its version (`backend-v0.7.0`,
raised as in 7.5), and the next deploys only when `backend/` or `legal/` changed since. A deploy
by hand with *auto* of an unchanged backend deploys the same version again.

A deploy removes functions that are no longer in the code without asking, as `firebase deploy
--force` does.

## 8. Keeping your copy up to date

To bring in changes from the original repository:

```bash
git remote add upstream https://github.com/PCOffline/meadow.git   # once
git pull upstream main
git push
```

With automatic deploys and releases turned on (7.5 and 7.6), the push to `main` deploys the
backend and publishes a new release, in that order. Otherwise, if anything in `backend/` changed,
deploy it again:

```bash
cd backend
npm ci && npm --prefix functions ci
firebase deploy --only firestore,functions,storage,hosting
```

Deploy the backend **before** releasing an app that depends on it. Finally, publish a new release
(step 7.5).

## Developing against the emulators

You can run the whole backend on your computer without touching your project. This needs
Java 21 or newer and Node 24.

```bash
cd backend
npm ci && npm --prefix functions ci
npm run emulators              # or `just emulators`
```

The first start adds demo people: Sam (with two Bumblebees, limits, a week of screen time, an
open request and a chat), Bee, Robin and Kai. Sign in as `sam@meadow.test`, `bee@meadow.test`,
`robin@meadow.test` or `kai@meadow.test` with the password `meadow-demo`. Everything you do is
saved in `backend/.emulator-data/` when you stop the emulators (Ctrl+C) and comes back next
time; `npm run emulators -- --fresh` starts over.

In another terminal, point a debug build at them (`10.0.2.2` is your computer, as seen from an
Android emulator):

```bash
./gradlew installDebug -Pmeadow.emulatorHost=10.0.2.2
```

[CONTRIBUTING.md](../CONTRIBUTING.md#checks) lists each test suite and how to run it. To watch every request
and why it was allowed or refused, see [Debugging Meadow](debugging.md).

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
app calls them directly (each function still checks who's signed in; the trigger and scheduled
functions stay private). Google Cloud organizations made since 2024, such as one that comes with
Google Workspace, forbid that by default with the *Domain restricted sharing* policy. The deploy
then can't make them public, and every call fails with `UNAUTHENTICATED`.

Override the policy for this project only. Being the project's Owner isn't enough: organization
policies are set on the organization, which needs the *Organization Policy Administrator* role
there. In the [Google Cloud console](https://console.cloud.google.com):

1. In the project picker at the top, pick your **organization**, not the project. Open
   **IAM & Admin → IAM → Grant access**, enter your account, pick the role
   **Organization Policy Administrator**, and save. If you can't, ask a Google Workspace super
   admin to do it.
2. Switch the picker back to your project. Open **IAM & Admin → Organization policies** and find
   **Domain restricted sharing** (`iam.allowedPolicyMemberDomains`). Choose **Manage policy**,
   then **Override parent's policy**, policy enforcement **Replace**, **Add a rule**, policy values
   **Allow All**, and **Set policy**.
3. If **Restrict allowed policy members in IAM allow policies**
   (`iam.managed.allowedPolicyMembers`) is enforced too, override it the same way, with
   enforcement **Off**.

The same as step 2, from the command line once you have the role:
`gcloud org-policies reset iam.allowedPolicyMemberDomains --project=<project-id>`.

Wait two minutes, then make the callable functions public. From `backend/`:

```bash
for f in $(grep -rhoE "export const \w+ = onCall" functions/src | awk '{print $3}'); do
  gcloud functions add-invoker-policy-binding "$f" --gen2 --member=allUsers \
    --region=<region> --project=<project-id>
done
```

This takes effect at once, without deploying again, and later deploys keep it. To check one:
`gcloud run services get-iam-policy createprofile --region=<region> --project=<project-id>` lists
`allUsers` with `roles/run.invoker`.

**The functions can't reach Firestore** (their log says `7 PERMISSION_DENIED: Missing or
insufficient permissions`, and the app gets `INTERNAL`). The functions run as the Compute Engine
default service account (`<project-number>-compute@developer.gserviceaccount.com`). Newer Google Cloud projects give it no roles, as with the Cloud Build
role above. Grant it only what the functions use: Firestore, Storage (data exports),
Authentication (deleting accounts), Cloud Messaging (notifications), and calling the functions
from Firestore triggers and schedules:

```bash
SA=$(gcloud run services describe createprofile --region=<region> --project=<project-id> \
  --format='value(spec.template.spec.serviceAccountName)')
for role in roles/datastore.user roles/storage.objectAdmin roles/firebaseauth.admin \
  roles/firebasecloudmessaging.admin roles/eventarc.eventReceiver roles/run.invoker; do
  gcloud projects add-iam-policy-binding <project-id> --member="serviceAccount:$SA" \
    --role="$role" --condition=None
done
```

It takes effect within a minute, without deploying again.

**`Firebase Storage has not been set up` or `could not find the default bucket`.** Do step 2.5,
then deploy again.

**Firestore says *The query requires an index*.** The indexes are still building, or weren't
deployed. Check **Firestore → Indexes**; if they're missing, run
`firebase deploy --only firestore:indexes`.

**The app says "Meadow isn't available right now", or stays on "Gathering petals…".** Something
on the server's side failed. The app doesn't tell people why, so build it with trace mode to see
the cause on screen and in the log (see [Debugging Meadow](debugging.md)). Without trace mode,
`adb logcat -s MeadowSync MeadowBackend MeadowFirestore` shows it too. By the code it names:

- `PERMISSION_DENIED` reading your profile (`users/<uid>`): the security rules refused it. Usually
  they were never deployed, so Firestore still has the locked-down rules it starts with. Run
  `firebase deploy --only firestore,storage` from `backend/`.
- `NOT_FOUND` or `FAILED_PRECONDITION` from Firestore: there is no database the app can use.
  Meadow uses the one named `(default)`, in Firestore Native mode; check **Firestore** in the
  console.
- `NOT_FOUND` from a Cloud Function: it isn't deployed where the app looked. Usually the app was
  built with a different `MEADOW_REGION` than the functions were deployed with: check
  **Functions** in the console for the region, set the same one in `backend/functions/.env`, and
  build the app again. Otherwise, deploy the functions again.
- `UNAUTHENTICATED` or `PERMISSION_DENIED` from a Cloud Function, right after signing in (the app
  asks you to sign in again; a trace build adds `Cloud Function failed: UNAUTHENTICATED`): Google
  Cloud refused the call before it reached Meadow, because the functions aren't public. See
  *Failed to set the IAM Policy* above.
- `INTERNAL` from a Cloud Function: it crashed; `firebase functions:log` shows why. If the log
  says `PERMISSION_DENIED: Missing or insufficient permissions`, see *The functions can't reach
  Firestore* above.

If instead the app says you seem to be offline, or the loader says it's taking longer than usual,
the phone can't reach Firebase. Check its connection.

**The app opens on "connect your backend".** It was built without `app/google-services.json`
(locally) or without the `GOOGLE_SERVICES_JSON` secret (in Actions).

**Sign-in links open in the browser instead of the app.** The fingerprint of the key that signed
the installed APK isn't in `backend/app-links.json`, or hosting wasn't deployed after adding it.
See step 5. Android may take a while to check again; reinstalling the app makes it check at once.

**No push notifications.** Check step 2.6. On the phone, allow Meadow's notifications and turn off
battery optimisation for it (Meadow's *Troubleshoot* screen walks through it, including the extra
steps some phone makers add).

**Deliver fails at *Add Firebase config* or *Add signing key*.** A secret is missing or
misspelled; see step 7.4. If it fails at *Publish* with a permissions error, check that your
organization's Actions settings (if the fork belongs to one) let workflows use the permissions they
ask for.

**Deliver fails at *Sign in to Google Cloud*.** For keyless sign-in, check that
`GCP_WORKLOAD_IDENTITY_PROVIDER` is the full name the setup script printed and that the script
was run with the repository name exactly as GitHub shows it (a renamed repository needs the
script run again with the new name). Only runs on `main` can sign in. If your organisation
restricts identity federation, use a key instead (7.6).

**Deliver fails at *Deploy* with `Permission denied` or `does not have permission`.** The
`github-deploy` service account is missing a role, often for a product added since. The error
names the permission; grant the role that has it under **IAM**, or run the setup script again.

