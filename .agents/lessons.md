# Lessons for coding agents

Each rule comes from a real mistake or bug in this repository. [AGENTS.md](../AGENTS.md) has the
rules for commits, branches, pull requests and tests. This file adds what those rules don't say.

## Plan the work

- Ask when a word in the request has two meanings and each meaning changes different code.
- Ask before you start when one request needs two fixes. Each fix gets its own branch from
  `main` and its own pull request.
- Start the slow checks first. Start the unit tests in the background as soon as the code
  compiles. Write the stories and the pull request while they run.
- Push after the checks for your change pass. When a hook asks you to push earlier, finish the
  checks first.
- Report each bug that you find, also when you don't fix it on this branch. Name the file and
  what a person sees.

## Code

### Layers

`data` and `usage` import nothing from `ui`. Put a value that `data` and `ui` both need in
`data`. Put pure logic that `data` needs in `data` or `usage`.

### Writes and inputs

- Change the screen at once, before the server answers. `awaitWrite` can wait for 8 seconds.
  When a write fails, put the person's input back, and keep what they typed after it.
- Use `ActionState.run` for actions that run once. It drops a call while it is busy, and it drops
  the same call for 600 ms after a success. Start each action that people repeat quickly, such as
  a message, in `AppGraph.scope`, and handle each failure.
- Firestore keeps the order of writes that start on the same thread. Start them with
  `Dispatchers.Main.immediate`, with no suspension before the write.

### Lists

- A `LazyColumn` with item keys keeps its first visible item in place when items come before it.
  Thus `firstVisibleItemIndex` grows by the number of added items. To know if the person was at
  the latest item, count the items that came since the last update.
- Data can come in two batches: first the copy on the phone, then the server's. Test a screen
  with a small list that then gets much bigger.

### Limit fields

A field of a limit lives in many places. When you add or change one, change each place:

- `AppLimit` (`data/Models.kt`) and `toLimit` (`data/FirestoreExt.kt`). Decide whether chat cards
  carry it (`limitOrNull`, `LimitsRepository.limitFields`).
- `LimitsRepository.save`. It has three paths: the direct write, a request, and the backend's
  `saveLimit`. The direct write is a `set` with no merge, so it deletes each field that it
  leaves out.
- `validLimit` in `backend/firestore.rules`, with a test in `backend/rules-tests`.
- The Cloud Functions that write limits (`requests.ts`). They write with `merge: true`, so they
  keep each field that they don't name.
- `LimitEditor`: the `proposed` limit, a copy (`start`) and a suggestion (`sendTo`).

With Bumblebees, an update of a limit must pass `isStricter` and `sameSchedule` in the rules. A
field that holds nothing back passes them when the minutes and hours stay the same.

Store "no choice" as null. The editor turns on Save when `proposed` differs from the limit it
opened with, by data class equality. Thus turn a choice that equals the default back into null.

A copied or a suggested limit comes from another person. Keep each personal choice from the
limit on this phone.

## Tests

- See each new test fail: undo the fix, run the test, see it fail, then put the fix back.
- Run each test class of the screen that you change, as well as your new tests.
- Make dead code go before you test it. A `const` flag that is always false hides branches that
  no test can reach.
- Keep hooks for tests out of production code. Make the real function `internal` and call it
  from the test.
- For a heuristic, such as a colour match or a ranking, write tests with real cases first. Then
  tune the heuristic until they pass.
- Delete `app/build/test-results` before you read the failures of a filtered run. Old results
  stay there.
- Set the CI coverage floor just under the full local measure. CI can be a few tenths lower.

### Robolectric screen tests

- Open dialogs in a test class with no `qualifiers` in its `@Config`. With a tall phone
  (`w411dp-h891dp-xxhdpi`), Robolectric never settles while a dialog is open, and the test hangs
  for 60 seconds.
- Check accessibility on the narrow default screen too. A tall phone hid a 40 dp touch target.
- Expect a backend action in a plain `ScreenTest` to fail: it has no Firebase. Assert the error
  note (it starts with "🍂").
- Make each test pass whatever ran before it in the same JVM. An earlier `EmulatorTest` can set
  up Firebase, so an action can take another path. Set the state that makes each path end the
  same way.
- Put state that a test sets in `AppGraph.store` back with `AppGraph.store.clearAll()` in a
  `finally` block.
- Give each test app its own package name. The app catalog keeps labels, and the usage log keeps
  events, for the whole test run.
- Give an installed app an icon with `shadowOf(packageManager).setApplicationIcon`.
- Advance the clock with `ShadowSystemClock.advanceBy` when code waits a time since the last
  event. `SystemClock` starts near zero.
- Compare started activities as a set. `nextStartedActivity` returns the newest first.

### Clicks

- A click on a node outside the visible part of a scrolling column does nothing, and the test
  doesn't say so. Call `performScrollTo()` before each click, or `performScrollToNode` in a
  `LazyColumn`, which composes only what fits.
- Content that you add to a column moves the controls under it. Put it where it moves no control
  that the screen's tests click.
- Click with `performSemanticsAction(SemanticsActions.OnClick)` when `performClick()` does
  nothing, for example on a dialog's button.

### Firebase emulator tests

- Test the offline paths with `backend.db.disableNetwork()`. Turn the network on again in a
  `finally` block.
- Give a local-only Firestore `disableNetwork()`, or it retries a dead server and slows other
  tests.

### Stories and pictures

- The Pictures emulator and Robolectric don't start Firebase. Check each default parameter of a
  composable that you show. Replace each one that reaches `AppGraph` or a repository. For
  example, give `ChatContent` a `requestCard` that shows a fixed `RequestCard`.
- The Pictures emulator has none of the apps that the demo data names. A story that needs an
  app's icon or name sets that state in `AppGraph.store`, and puts it back at the end.
- Compare a new story with a story that already works on the same screen before you push it.
- Look at the pictures when the workflow is done. They are on the `pictures/pr-<number>` branch.
  Compare each "before" with its "after". A blank "after" of a screen that the change doesn't
  touch is a flaw of the run: tell the person, and suggest that they run the workflow again.

## CI and pull requests

- Push in batches. Each push cancels the CI run before it, so let a run that checks a workflow
  change finish before the next push.
- Run each CI linter locally at the version that CI pins in `build.yml`, for example `ruff`.
- Read a failed job with `get_job_logs` and `return_content`. The proxy blocks the log download
  URL. When the result is saved as JSON, read `logs_content` from it. Search it for `FAILED`,
  `failed test` and `error`: the tail is the clean-up.
- Find the cause of each red job. Run the failing step locally, then look at it on the next run.
  One shard that fails before its tests start is not proof of a bug.
- Read the pull request description again before you change it. Keep the
  `<!-- pictures: start ... -->` and `<!-- pictures: end -->` lines: the workflow writes between
  them.
- When a change stores new data, compare it with `legal/privacy_notice.md`. Say in the pull
  request what the notice says about it.

## Cloud sessions

- The container has no Android SDK. Install it in your home directory:
  1. Download `commandlinetools-linux-*_latest.zip` from `dl.google.com` to `~/android-sdk`.
  2. Move it to `~/android-sdk/cmdline-tools/latest`.
  3. Accept the licences with `sdkmanager --licenses`.
  4. Install `platforms;android-<compileSdk>.0`. Read `compileSdk` in `app/build.gradle.kts`.
  5. Write `sdk.dir=<path>` in `local.properties`. Git ignores this file.
- Run the first build in the background. It takes more than 10 minutes.
- Maven Central sometimes returns 404 through the proxy for a file that exists, and Gradle caches
  the 404. Run the same Gradle command again with `--max-workers=2`, up to three times.
- When retries don't help, use a small local mirror that retries a 404 from Maven Central only.
  Point Gradle at it with an init script (`-I`). Keep both files in the scratchpad. Start the
  mirror and Gradle in one background command, with a time limit for the whole build.
- Add `--offline` when the cache holds all the dependencies. A Robolectric test that then fails
  with "Failed to fetch maven artifact" needs an SDK jar: run it again without `--offline`.
- The Functions emulator can't register its Firestore triggers here, because the proxy blocks
  the Firebase CLI's calls to 127.0.0.1. Thus each `EmulatorTest` skips locally. CI runs these
  tests: say so in the pull request.
- Three Storage tests in `backend/rules-tests/storage.test.mjs` fail in the container, on `main`
  too. CI passes them.
- Run a failing test on `origin/main` in a `git worktree` before you call the failure old.
