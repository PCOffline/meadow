// Runs a command on the emulators (`firebase emulators:exec`), then waits for the Functions
// emulator's workers to exit. The integration tests run this under c8, which reads the coverage
// files as soon as its command exits. The emulator stops its workers with SIGTERM and does not wait
// for them. A worker writes its coverage file as it exits (see functions/src/index.ts), so without
// this wait c8 can read the files before a worker has written its own.
const { execFileSync, spawnSync } = require("node:child_process");

/** The process group of this process: the workers the emulator starts stay in it. */
function processGroup() {
  return execFileSync("ps", ["-o", "pgid=", "-p", String(process.pid)], { encoding: "utf8" }).trim();
}

/** Whether a Functions emulator worker in [group] is still running. */
function workersRunning(group) {
  try {
    execFileSync("pgrep", ["-g", group, "-f", "functionsEmulatorRuntime"], { stdio: "ignore" });
    return true;
  } catch {
    return false;
  }
}

/**
 * Waits until [running] says no, checking every [stepMs], for at most [timeoutMs]. True when they
 * stopped in time.
 */
async function waitUntilStopped(running, { timeoutMs = 30_000, stepMs = 100 } = {}) {
  const end = Date.now() + timeoutMs;
  while (running()) {
    if (Date.now() > end) return false;
    await new Promise((resolve) => setTimeout(resolve, stepMs));
  }
  return true;
}

async function main(args) {
  const firebase = require.resolve("firebase-tools/lib/bin/firebase.js");
  const result = spawnSync(process.execPath, [firebase, "emulators:exec", ...args], { stdio: "inherit" });
  if (result.error) throw result.error;
  const group = processGroup();
  if (!(await waitUntilStopped(() => workersRunning(group)))) {
    console.error("The Functions emulator's workers did not stop in 30 seconds: coverage can be missing.");
  }
  process.exit(result.status ?? 1);
}

if (require.main === module) main(process.argv.slice(2));

module.exports = { waitUntilStopped };
