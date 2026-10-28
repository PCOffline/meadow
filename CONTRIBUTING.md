# Contributing to Meadow

Thank you for your help. Read [AGENTS.md](AGENTS.md) for the rules on commits, branches, tests
and writing.

## Set up

1. Install Java 21 or newer and Node 24. `mise install` installs both. CI uses Java 25.
2. Run `just` to see the shortcuts for build, install, test and deploy.

## Checks

CI runs all checks on each pull request and on each push to `main`. Before you push, run the
checks for the parts you changed. `just check` runs most of them.

| Part | Command |
|---|---|
| App unit, screen and accessibility tests | `./gradlew testDebugUnitTest` |
| Lint | `./gradlew lintDebug`, `.github/scripts/detekt.sh`, and `npm run lint` in `backend/` and `backend/functions/` |
| Cloud Functions unit tests | `cd backend/functions && npm test` |
| Security rules (Firestore and Storage) | `cd backend && npm ci && npm run test:rules` |
| Cloud Functions on the emulators | `cd backend && npm ci && npm --prefix functions ci && npm run test:integration` |
| Hosted pages (legal, invite and sign-in) | `cd backend && npm run test:hosting` |
| Deploy scripts | `cd backend && npm run test:deploy` |
| End to end, on an Android emulator | Start the Firebase emulators (`npm run emulators` in `backend/`). Then run `./gradlew connectedDebugAndroidTest -Pmeadow.emulatorHost=10.0.2.2` |
| CI scripts and workflows | `python3 -m unittest discover -s .github/scripts -p 'test_*.py'`, `actionlint`, `shellcheck` and `ruff check` |

Run the end-to-end tests on an emulator only. Some of these tests turn Meadow's permissions on
and off.

## Every screen by hand

Install a debug build with `./gradlew installDebug`, or get the `meadow-debug-apk` artifact from
CI. Open **Meadow screens**, the second icon. It shows each screen in each state, with made-up
data, in each theme. Read [Debugging Meadow](docs/debugging.md) for more.

## Words

App text is in `app/src/main/res/values/strings*.xml` and `backend/functions/src/copy.ts`. Use
these terms, and use each term for one meaning only:

| Term | Meaning |
|---|---|
| Bumblebee | A friend who looks after your garden. The code calls them *approvers*. |
| garden | All your limits |
| limit | The time one app can open, each day, each week or both |
| resting | An app whose time is used, for now |
| more time | What you ask a Bumblebee for |
| approve / deny | The two answers a Bumblebee gives |
| emergency unlock | All apps open without a Bumblebee, for a short time |

## Accessibility

`assertAccessible` (in `app/src/sharedTest`) checks each screen. It checks that each control has
a label, each touch target is 48dp or more, and each screen has headings. The theme tests check
each colour pair against WCAG AA contrast. Give each new screen a screen test that calls
`assertAccessible`.

## README images

`ShowcaseTest` makes the screenshots and animations. The Screenshots workflow runs it on an
emulator when a push to `main` changes the app's screens. Then it commits the images to `main`.
To run it on a different branch, start it from the Actions tab. `docs/art/build.py` draws
the banner and the illustrations.
