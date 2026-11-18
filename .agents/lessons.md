# Lessons for coding agents

Read this file at the start of each session. Each rule comes from a real mistake or bug in this
repository. [AGENTS.md](../AGENTS.md) has the rules for commits, branches, pull requests and tests.
This file adds what those rules don't say.

## Plan the work

- When one request has two fixes and you have one branch, say so before you start. Ask the
  person if they want two pull requests.
- Start the slow checks first. Start the unit tests in the background as soon as the code
  compiles. Then write the stories and the pull request while they run.
- Push only after the checks for your change pass. When a hook asks you to push earlier, run the
  checks to their end first.

## Compose lists

- A `LazyColumn` with item keys keeps its first visible item in place when items are added before
  it. Thus `firstVisibleItemIndex` grows by the number of added items.
- To find out if the person was at the latest item, count the items that were added since the
  last update. Don't compare the index to a fixed number.
- Messages can come in two batches: first the ones saved on the phone, then the server's. Test
  the screen with a small list that then gets much bigger.

## Inputs and writes

- Clear a text box when the person taps Send. Put the text back if the write fails. Keep text that
  the person typed after it.
- Don't wait for the server before you change the screen. `awaitWrite` can wait for 8 seconds.
- Use `ActionState.run` only for actions that run once. It drops a call while it is busy, and it
  drops the same call for 600 ms after a success. For actions that people repeat quickly, such as
  sending messages, start each one in `AppGraph.scope` and handle each failure.
- Firestore keeps the order of writes that start on the same thread. Start them with
  `Dispatchers.Main.immediate`, and make sure that no suspension comes before the write.

## Tests and stories

- Make sure that each new test fails without the fix. Undo the fix, run the test, see it fail,
  then restore the fix.
- Run one test with `--tests '*ClassName*part of the name*'`.
- The Pictures emulator and Robolectric don't start Firebase. Check each default parameter of a
  composable that you show. Replace each one that reaches `AppGraph` or a repository. For
  example, give `ChatContent` a `requestCard` that shows a fixed `RequestCard`.
- Before you push a story, compare it with a story that already works on the same screen.
- Run each test class of the screen that you change, as well as your new tests.
- A test click on a node outside the visible part of a scrolling column does nothing, and the
  test doesn't say so. Content that you add to a column moves the controls under it. A test that
  scrolls down and then clicks a control higher up then misses it. Put new content where it
  moves no control that the screen's tests click. Call `performScrollTo()` before each click in
  a new test.
- Put state that a test sets in `AppGraph.store` back with `AppGraph.store.clearAll()` in a
  `finally`.
- `shadowOf(packageManager).setApplicationIcon` gives an installed app an icon in a Robolectric
  test.
- For a heuristic, such as a colour match or a ranking, first write tests with real cases. Then
  tune the heuristic until they pass. For a match of colours, compare hue first: plain OKLab
  distance lets lightness beat hue.
- The Pictures emulator has none of the apps that the demo data names. A story that depends on
  an app's icon or name sets that state itself in `AppGraph.store`. Put the state back at the end
  of the story.
- Look at the pictures when the workflow is done. They are on the `pictures/pr-<number>` branch.
  Compare each "before" with its "after". A blank "after" picture of a screen that the change
  doesn't touch isn't a bug of the change. Tell the person, and suggest that they run the
  workflow again.

## Limit fields

A field of a limit is in many places. When you add or change one, change each place:

- `AppLimit` (`data/Models.kt`), and `toLimit` (`data/FirestoreExt.kt`). Decide whether chat
  cards carry it (`limitOrNull`, `LimitsRepository.limitFields`).
- `LimitsRepository.save`. It has three paths: the direct write, a request, and the backend's
  `saveLimit`. The direct write is a `set` with no merge, so a field that it leaves out is
  deleted.
- `validLimit` in `backend/firestore.rules`, with a test in `backend/rules-tests`.
- The Cloud Functions that write limits (`requests.ts`). They write with `merge: true`, so they
  keep each field that they don't name.
- `LimitEditor`: the `proposed` limit, a copy (`start`) and a suggestion (`sendTo`).

With Bumblebees, an update of a limit must pass `isStricter` and `sameSchedule` in the rules. A
field that holds nothing back passes them when the minutes and hours stay the same.

Store "no choice" as null. The editor enables Save when `proposed` differs from `existing` by
data class equality. Thus turn a choice that equals the default back into null, so that an
editor that nobody touched has nothing to save.

A copied or a suggested limit comes from another person. Keep each personal choice from the
limit on this phone.

## Code layers

`data` and `usage` import nothing from `ui`. Put a value that `data` and `ui` both need in
`data`. Put pure logic that `data` needs in `data` or `usage`.

## Pull requests

- The Pictures workflow writes in the pull request description. Read the description again
  before you change it. Keep the `<!-- pictures: start ... -->` and `<!-- pictures: end -->`
  lines: the workflow writes between them.
- When a change stores new data, compare it with `legal/privacy_notice.md`. Say in the pull
  request what the notice says about it. Change `legal/` on a branch of its own.
- To read a failed CI job, use `get_job_logs` with `return_content`. The proxy blocks the log
  download URL. When the result is too large, it is saved as JSON. Read `logs_content` from it,
  then search for `FAILED` and `failed test`.

## Cloud sessions

- The container has no Android SDK. Install it in your home directory:
  1. Download `commandlinetools-linux-*_latest.zip` from `dl.google.com` to `~/android-sdk`.
  2. Move it to `~/android-sdk/cmdline-tools/latest`.
  3. Accept the licences with `sdkmanager --licenses`.
  4. Install `platforms;android-<compileSdk>.0`. Read `compileSdk` in `app/build.gradle.kts`.
  5. Write `sdk.dir=<path>` in `local.properties`. Git ignores this file.
- Maven Central sometimes returns 404 through the proxy for a file that exists. Gradle keeps the
  404 in its cache.
- Don't use `--refresh-dependencies` to fix it. It downloads more files, so more of them fail.
- Use a small local mirror that retries a 404 from Maven Central only. Point Gradle at it with an
  init script (`-I`). Keep both files in the scratchpad, not in the repository.
- Start the mirror and Gradle in the same background command, and stop the mirror at the end.
  A background command stops at its time limit, so set the limit for the whole build.
- A plain retry also gets past the 404s: run the same Gradle command again with
  `--max-workers=2`, up to three times. When the cache holds all the dependencies, add
  `--offline`.
- With `--offline`, a Robolectric test fails with "Failed to fetch maven artifact" when it needs
  an SDK jar that isn't in the cache. Run that test again without `--offline`.
- The Functions emulator can't register its Firestore triggers here. The Firebase CLI sends its
  calls to 127.0.0.1 through the proxy, and the proxy blocks them. Thus each `EmulatorTest`
  skips locally. Keep the proxy settings as they are. CI runs these tests: say so in the pull
  request.
- Three Storage tests in `backend/rules-tests/storage.test.mjs` fail in the container, on `main`
  too. CI passes them. The Firestore rules tests run.
- Before you call a failure old, run the same test on `origin/main` in a `git worktree`.
- The first build takes more than 10 minutes. Run it in the background, not in the foreground.
