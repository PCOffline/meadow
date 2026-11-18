# Lessons for coding agents

Each rule comes from a real mistake in this repository. [AGENTS.md](../AGENTS.md) has the rules
for commits, branches, pull requests and tests. This file adds what those rules don't say.

## Plan the work

- Ask when a word in the request has two meanings and each meaning changes different code.
- Start the slow checks first. Run the unit tests in the background as soon as the code
  compiles, and write the stories and the pull request while they run.
- Report each bug that you find, also when you don't fix it on this branch. Name the file and
  what a person sees.

## Code

- `data` and `usage` import nothing from `ui`. Put a value that both sides need in `data`.
- Change the screen at once, before the server answers. `awaitWrite` can wait for 8 seconds.
  When the write fails, show the failure and put back what the person entered.
- Use `ActionState.run` for an action that runs once. It drops a call while it is busy, and the
  same call for 600 ms after a success. Start an action that people repeat quickly in
  `AppGraph.scope`.
- Firestore keeps the order of writes that start on the same thread. Start them with
  `Dispatchers.Main.immediate`, with no suspension before the write.
- A stored field lives in many places: the model, the reader, each write path, the security
  rules and their tests, the Cloud Functions and the editor. Find and change each one.
  - A `set` with no merge deletes each field that it leaves out.
  - The Cloud Functions write with `merge: true`, so they keep each field that they don't name.
- Store "no choice" as null, so that an editor that nobody touched has nothing to save.

## Tests

- See each new test fail: undo the fix, run the test, then put the fix back.
- Run each test class of the screen that you change, as well as your new tests.
- Make each test pass whatever ran before it in the same JVM. An earlier `EmulatorTest` can set
  up Firebase. Put state that a test sets in `AppGraph.store` back in a `finally` block.
- Give each test app its own package name. The app catalog and the usage log keep their data for
  the whole test run.
- Remove dead code before you test it. Make the real function `internal` and call it from the
  test, so that production code has no hooks for tests.
- For a heuristic, such as a colour match or a ranking, write tests with real cases first. Then
  tune the heuristic until they pass.
- Delete `app/build/test-results` before you read the failures of a filtered run. Old results
  stay there.
- Test offline paths with `backend.db.disableNetwork()`. Turn the network on again in a
  `finally` block.

### Robolectric screen tests

- A plain `ScreenTest` has no Firebase, so each backend action fails. Assert the error note.
- Open dialogs in a test class with no `qualifiers` in its `@Config`. With a tall phone,
  Robolectric never settles while a dialog is open, and the test hangs.
- Check accessibility on the narrow default screen too. A tall phone can hide a small touch
  target.
- A click on a node outside the visible part of a scrolling column does nothing, and the test
  doesn't say so. Scroll to each node before you click it. Content that you add to a column
  moves the controls under it, so run the screen's tests again.
- Click with `performSemanticsAction(SemanticsActions.OnClick)` when `performClick()` does
  nothing, for example on a dialog's button.

### Stories and pictures

- The Pictures emulator has no Firebase and none of the apps that the demo data names. Replace
  each default parameter that reaches `AppGraph` or a repository. Set app state in
  `AppGraph.store`, and put it back at the end of the story.
- Look at the pictures when the workflow is done. They are on the `pictures/pr-<number>` branch.
  A blank "after" of a screen that the change doesn't touch is a flaw of the run: suggest that
  the person runs the workflow again.

## CI and pull requests

- Push in batches. Each push cancels the CI run before it.
- Run each CI linter locally, at the version that `build.yml` pins.
- Read a failed job with `get_job_logs` and `return_content`. The proxy blocks the log download
  URL. Search the log for `FAILED` and `error`: its tail is the clean-up.
- Keep the `<!-- pictures: start ... -->` and `<!-- pictures: end -->` lines when you change a
  pull request description. The Pictures workflow writes between them.
- When a change stores new data, compare it with `legal/privacy_notice.md`. Say in the pull
  request what the notice says about it.

## Cloud sessions

- Install the Android SDK in your home directory. Get the command-line tools from
  `dl.google.com`, install the platform that `compileSdk` in `app/build.gradle.kts` names, and
  write `sdk.dir` in `local.properties`.
- Run the first build in the background. It takes more than 10 minutes.
- Maven Central sometimes returns 404 through the proxy for a file that exists. Run the same
  Gradle command again with `--max-workers=2`, up to three times.
- The Functions emulator can't register its triggers here, so each `EmulatorTest` skips. CI runs
  them: say so in the pull request.
- Run a failing test on `origin/main` in a `git worktree` before you call the failure old.
