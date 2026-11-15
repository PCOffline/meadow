// The wait in emulators-exec.cjs: c8 must read the coverage files only after the Functions
// emulator's workers have written them as they exit.
import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { existsSync, mkdtempSync, rmSync } from "node:fs";
import { createRequire } from "node:module";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { test } from "node:test";

const require = createRequire(import.meta.url);
const { waitUntilStopped } = require("../emulators-exec.cjs");

const alive = (child) => child.exitCode === null && child.signalCode === null;

/**
 * A stand-in for a worker: on SIGTERM it takes [writeMs] to write [file], as a worker takes time
 * to write its coverage, then exits.
 */
async function worker(file, writeMs) {
  const code = `
    process.once("SIGTERM", () => setTimeout(() => {
      require("node:fs").writeFileSync(${JSON.stringify(file)}, "{}");
      process.exit(0);
    }, ${writeMs}));
    process.send("ready");
    setInterval(() => {}, 1000);
  `;
  const child = spawn(process.execPath, ["-e", code], { stdio: ["ignore", "ignore", "inherit", "ipc"] });
  await new Promise((resolve) => child.once("message", resolve));
  return child;
}

test("a worker's file is there only once the wait is over, not when it is told to stop", async () => {
  const dir = mkdtempSync(join(tmpdir(), "coverage-"));
  try {
    const file = join(dir, "coverage.json");
    const child = await worker(file, 300);
    child.kill("SIGTERM");
    // What c8 saw before: the command was over, but the worker was still writing.
    assert.equal(existsSync(file), false);
    assert.equal(await waitUntilStopped(() => alive(child), { stepMs: 20 }), true);
    assert.equal(existsSync(file), true);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("the wait gives up after its time, and says so", async () => {
  const started = Date.now();
  assert.equal(await waitUntilStopped(() => true, { timeoutMs: 100, stepMs: 20 }), false);
  assert.ok(Date.now() - started >= 100);
});

test("with no workers left, the wait is over at once", async () => {
  let checks = 0;
  const running = () => {
    checks++;
    return false;
  };
  assert.equal(await waitUntilStopped(running), true);
  assert.equal(checks, 1);
});
