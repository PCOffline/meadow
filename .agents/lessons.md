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

## Pull requests

- The Pictures workflow writes in the pull request description. Read the description again
  before you change it.
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
- The first build takes more than 10 minutes. Run it in the background, not in the foreground.
