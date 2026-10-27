# Working on Meadow

Rules for anyone changing this repository, people and coding agents alike. `CONTRIBUTING.md`
covers voice, words and accessibility; the README's *Tests* section lists the checks.

## Commits

Every commit follows [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/):
`<type>(<optional scope>): <summary>`, for example `fix(sync): retry a failed upload once`.

- Types: `feat`, `fix`, `docs`, `test`, `refactor`, `perf`, `build`, `ci`, `chore`, `style`,
  `revert`.
- Scopes, when they help: `app`, `backend`, `rules`, `legal`, `ci`, `e2e`, or a feature name.
- Breaking changes: `!` after the type, or a `BREAKING CHANGE:` footer.
- Release markers still apply on top (`[minor]`, `[major]`, `[skip release]`, `[skip deploy]`;
  see `docs/self-hosting.md`).

## Branches

One branch, one task. A branch changes only what its task needs, plus the tests for it.

- No drive-by refactors, renames, formatting or dependency bumps. Put them on their own branch.
- Something unrelated that needs fixing gets its own branch and pull request.
- Keep the diff as small as the task allows.

## Tests

Every feature and every fix adds tests that would fail without it:

- App logic: unit tests in `app/src/test`.
- Screens: a Robolectric screen test in `app/src/test/.../ui/screens` that extends `ScreenTest` and calls `assertAccessible`.
- Journeys across screens: an end-to-end test in `app/src/androidTest`.
- Cloud Functions logic: unit tests next to the code (`backend/functions/src/*.test.ts`).
- Cloud Functions end to end: `backend/integration-tests`, against the emulators.
- Security rules: `backend/rules-tests`, against the emulators.
- Hosted pages, deploy and CI scripts: `backend/hosting-tests`, `backend/deploy-tests`,
  `.github/scripts`.

Run the checks that cover your change before pushing (`just test`). Never skip, disable or
weaken a test to get a green build.

## Docs

Docs describe the code as it is, not as it was or will be. A change that makes a doc wrong
updates that doc in the same branch: `README.md`, `docs/`, `CONTRIBUTING.md`, this file,
in-app copy, and comments in code.

- Don't document what isn't built. Remove what no longer exists.
- Commands, paths, versions and settings in docs must work as written.

## Legal documents

`legal/` holds the privacy notice, the terms of use and the third-party licences. The app shows
them and people agree to them, so they bind both Meadow and the people who use it.

- **Change them rarely and deliberately.** Only for a real change in what Meadow does with
  data, what it promises, or what the law requires. Never as part of an unrelated branch.
- **Write them carefully.** Plain language, precise and unambiguous. Every statement must be
  true of the code and the deployment. No promises the code doesn't keep.
- **Follow legal standards and good practice.** For the privacy notice, the GDPR (Articles 13
  and 14): who the controller is and how to reach them, each purpose and its legal basis, the
  recipients and processors, transfers outside the EEA and their safeguards, retention periods,
  people's rights (access, rectification, erasure, restriction, portability, objection,
  withdrawing consent) and the right to complain to a supervisory authority. For the terms:
  who the parties are, what the service is, acceptable use, termination, liability and its
  limits, governing law, and how changes are announced.
- **Name our actual stack and decisions,** not "whoever hosts your copy of Meadow". State
  what we run: Google Firebase (Authentication, Cloud Firestore, Cloud Functions, Cloud
  Storage, Cloud Messaging, Installations, Hosting) as our processor, the region set in
  `MEADOW_REGION`, the retention periods the code enforces (such as the one-day expiry of
  exports in `backend/storage-lifecycle.json`), and the app's distribution through GitHub
  releases. Someone running their own copy adapts the documents to their own setup.
- **Stay consistent with the GPL-3.0** (`LICENSE`). The terms may govern the service we run
  but must not restrict the rights the GPL grants over the source code, and must keep its
  disclaimer of warranty and limitation of liability compatible with it.
- **Version them.** Bump the version and date at the top of each changed document. When people
  must accept the change, raise `LegalDoc.CURRENT_VERSION` (`ui/legal/Legal.kt`) too.
- Keep `legal/licenses.md` in step with the app's and the backend's dependencies.
