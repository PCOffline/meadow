// Every function runs as the narrow meadow-functions account, never the Compute Engine default
// account (which builds them and may hold more).
import assert from "node:assert/strict";
import { test } from "node:test";

process.env.MEADOW_REGION ??= "europe-west1";

test("every function runs as meadow-functions", async () => {
  const functions = Object.entries(await import("./index.js")).filter(([, value]) => typeof value === "function");
  assert.ok(functions.length > 20);
  for (const [name, fn] of functions) {
    const endpoint = (fn as { __endpoint?: { serviceAccountEmail?: string } }).__endpoint;
    assert.equal(endpoint?.serviceAccountEmail, "meadow-functions@", name);
  }
});
