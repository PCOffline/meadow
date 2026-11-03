// Every function runs as the narrow meadow-functions account, never the Compute Engine default
// account (which builds them and may hold more).
import assert from "node:assert/strict";
import { test } from "node:test";

process.env.MEADOW_REGION ??= "europe-west1";
process.env.GCLOUD_PROJECT = "meadow-test";

// The whole address: Cloud Scheduler gets it as it is, and refuses the short "name@" form.
test("every function and schedule runs as meadow-functions, by its whole address", async () => {
  const functions = Object.entries(await import("./index.js")).filter(([, value]) => typeof value === "function");
  assert.ok(functions.length > 20);
  assert.ok(functions.some(([, fn]) => (fn as { __endpoint?: { scheduleTrigger?: object } }).__endpoint?.scheduleTrigger));
  for (const [name, fn] of functions) {
    const endpoint = (fn as { __endpoint?: { serviceAccountEmail?: string } }).__endpoint;
    assert.equal(endpoint?.serviceAccountEmail, "meadow-functions@meadow-test.iam.gserviceaccount.com", name);
  }
});
