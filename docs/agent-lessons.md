# Lessons for coding agents

Follow these rules in each session on Meadow. They come from mistakes and findings in earlier
sessions. [AGENTS.md](../AGENTS.md) has the rules for all contributors. This file adds what an
agent learned the hard way.

## Before you start

- Ask when a word in the request can mean two things and the choices remove different code.
  For example, "remove debug" can mean trace mode or the debug screen gallery. The gallery feeds
  the Pictures workflow.
- Put each task on its own branch from `main`, from the first commit. Two branches with the same
  commit merge badly.
- Push only to the branch you were given. Open a new branch only when the person asks for one.

## Robolectric screen tests

- Open dialogs in a test class with no `qualifiers` in its `@Config`. With a tall phone
  (`w411dp-h891dp-xxhdpi`), Robolectric never settles while a dialog is open, and the test hangs
  for 60 seconds.
- Expect a backend action in a plain `ScreenTest` to fail: it has no Firebase. Assert the error
  note (it starts with "🍂"), not success.
- Make a test pass whatever ran before it in the same JVM. An earlier `EmulatorTest` can set up
  Firebase, so an action can take another path. Set the state that makes every path end the
  same way.
- Click with `performSemanticsAction(SemanticsActions.OnClick)` when `performClick()` does
  nothing, for example on a dialog's button.
- On the default screen a `LazyColumn` composes only what fits. Scroll to a node with
  `performScrollToNode` before you tap it.
- Give each test app its own package name. The app catalog keeps labels, and the usage log keeps
  events, for the whole test run.
- Advance `SystemClock` with `ShadowSystemClock.advanceBy` when code waits a time since the last
  event. It starts near zero.
- Compare started activities as a set. `nextStartedActivity` returns the newest first.
- Check accessibility on the narrow default screen too. A tall phone hid a 40 dp touch target
  in the limit editor.

## Emulator tests

- Unset the proxy variables before you start the Firebase emulators, or the Functions emulator
  is refused: `env -u HTTPS_PROXY -u https_proxy -u GLOBAL_AGENT_HTTPS_PROXY ...`.
- Test the offline paths with `backend.db.disableNetwork()`. Turn the network on again in a
  `finally` block.
- Give a local-only Firestore `disableNetwork()`, or it retries a dead server and slows other
  tests.

## Coverage

- Count branches with `coverage_floor.py --classes --exec`, which runs
  `ComposeBranches.java`. JaCoCo's report counts branches per line only, so an estimate from
  the report is wrong.
- Remove dead code before you write tests for it. A `const` flag that is always false hides
  branches that no test can reach.
- Keep production code free of hooks that only tests use. Make the real function `internal`
  and call it from the test.
- Set the CI floor just under the full local measure. CI can be a few tenths lower.
- Delete `app/build/test-results` before you read failures from a filtered run. Old results stay
  there.

## CI

- Push in batches. Each push cancels the CI run before it, so a run that checks a workflow
  change must finish before the next push.
- Run each CI linter locally, at the version CI pins, before you push a script. For example,
  run `ruff` at the version in `build.yml`.
- Read a failed job's log for `FAILED` and `error`, not only its tail. The tail is the
  clean-up.
- Find the cause of each red job. Build the failing step locally, then look at it again on the
  next run. One shard that fails before its tests start is not proof of a bug.

## Reporting

- Report each bug that you find, even when you don't fix it on this branch. Name the file and
  what a person sees.
- Say what you didn't check. Say so when a figure is an estimate.
