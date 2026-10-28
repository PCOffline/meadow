# Working on Meadow

These rules are for all people and coding agents who change this repository.
[CONTRIBUTING.md](CONTRIBUTING.md) has the checks, the terms and the accessibility rules.

## Commits

Use [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/):
`<type>(<optional scope>): <summary>`. For example: `fix(sync): retry a failed upload once`.

- Types: `feat`, `fix`, `docs`, `test`, `refactor`, `perf`, `build`, `ci`, `chore`, `style`,
  `revert`.
- Scopes, when they help: `app`, `backend`, `rules`, `legal`, `ci`, `e2e`, or a feature name.
- Breaking changes: put `!` after the type, or add a `BREAKING CHANGE:` footer.
- Release markers also apply: `[minor]`, `[major]`, `[skip release]`, `[skip deploy]`. Read
  `docs/self-hosting.md`.

## Branches

Each branch does one task. It changes only what the task needs, and the tests for it.

- Open the pull request when you push the branch for the first time. Then CI and review start
  at once.
- Put refactors, renames, formatting and dependency updates on a branch of their own.
- Fix an unrelated problem on a branch and pull request of its own.
- Keep the diff as small as the task permits.

## Tests

Each feature and each fix adds tests. Each test fails without the change.

- App logic: unit tests in `app/src/test`.
- Screens: a Robolectric screen test in `app/src/test/.../ui/screens`. It extends `ScreenTest`
  and calls `assertAccessible`.
- Journeys across screens: an end-to-end test in `app/src/androidTest`.
- Cloud Functions logic: unit tests next to the code (`backend/functions/src/*.test.ts`).
- Cloud Functions end to end: `backend/integration-tests`, on the emulators.
- Security rules: `backend/rules-tests`, on the emulators.
- Hosted pages, deploy scripts and CI scripts: `backend/hosting-tests`,
  `backend/deploy-tests`, `.github/scripts`.

Before you push, run the checks for your change (`just test`). To fix a red build, fix the
code. Change a test only when the test itself is wrong. Keep each test on and as strict as
before.

## Docs

Docs tell what the code does now. When a change makes a doc wrong, update the doc on the same
branch. This applies to `README.md`, `docs/`, `CONTRIBUTING.md`, this file, app text and code
comments.

- Write only about features that exist. Remove text about features that were removed.
- Make sure that each command, path, version and setting works as written.

## Writing

These rules apply to all text: app text, notifications, docs, code comments and pull requests.
The legal documents follow their own rules (below).

Use [ASD-STE100 Simplified Technical English](https://www.asd-ste100.org/):

- Write short sentences: 20 words or fewer for an instruction, 25 or fewer for a description.
- Give one instruction in each sentence. Put the verb first: "Choose a theme."
- Use the active voice and simple tenses: "Meadow sends a reminder."
- Use one word for one meaning, and the same word each time. The terms are in
  [CONTRIBUTING.md](CONTRIBUTING.md#words).
- Use simple, common words. Use "use", not "utilise". Use "start", not "kick off".
- Put lists of steps or items in vertical lists.

Write like a person ([Humanizer](https://github.com/blader/humanizer)):

- Say the fact. Use "is", "has" and "does", not "serves as" or "boasts".
- Write plain claims, not "not X but Y" and not lists of three for rhythm.
- Use commas, colons and full stops, not dashes.
- Use sentence case in headings. Keep emojis out of headings and names.
- Use bold for terms the reader must find, not for emphasis.
- Stop when the point is made. Leave out summaries, sales words and notes about the text itself.

For agent instructions such as this file, follow
[Matt Pocock's skills](https://github.com/mattpocock/skills):

- State the target behaviour, not the behaviour to avoid.
- Keep each rule in one place. Point to it from other places.
- Remove rules that are out of date or that change nothing.

### App text

App text tells people what they need to know to act.

- Describe what people see and do. Services, providers, error codes, browsers and how a feature
  works go in the README, `docs/` and the legal documents.
- A setting has a short description, or none. For example: "Requires location services".
- Put text under a control only when Android or Google Play requires a disclosure before a
  sensitive permission.

## Legal documents

`legal/` has the privacy notice, the terms of use and the third-party licences. The app shows
them and people agree to them. Thus they bind Meadow and the people who use it.

- **Change them only for a real reason.** A real reason is a change in what Meadow does with
  data, what it promises, or what the law requires. Change them on a branch of their own.
- **Write them carefully.** Use plain language. Make each statement precise, and true of the
  code and the deployment. Promise only what the code does.
- **Follow legal standards and good practice.** For the privacy notice, follow the GDPR
  (Articles 13 and 14). State who the controller is and how to contact them. State each purpose
  and its legal basis, the recipients and processors, and transfers outside the EEA with their
  safeguards. State the retention periods and people's rights: access, rectification, erasure,
  restriction, portability, objection and withdrawal of consent. State the right to complain to
  a supervisory authority. For the terms, state the parties, the service, acceptable use,
  termination, liability and its limits, the governing law, and how changes are announced.
- **Name our real stack and decisions,** not "whoever hosts your copy of Meadow". State what we
  run: Google Firebase (Authentication, Cloud Firestore, Cloud Functions, Cloud Storage, Cloud
  Messaging, Installations, Hosting) as our processor, and the region in `MEADOW_REGION`. State
  the retention periods that the code applies, for example the one-day expiry of exports in
  `backend/storage-lifecycle.json`. State that the app is distributed through GitHub releases.
  A person who runs their own copy changes the documents for their own setup.
- **Stay compatible with the GPL-3.0** (`LICENSE`). The terms can govern the service we run.
  They must keep all the rights that the GPL gives over the source code. Their disclaimer of
  warranty and limitation of liability must be compatible with the GPL.
- **Give them versions.** Change the version and date at the top of each changed document. When
  people must accept the change, also increase `LegalDoc.CURRENT_VERSION` (`ui/legal/Legal.kt`).
- Keep `legal/licenses.md` in step with the dependencies of the app and the backend.
