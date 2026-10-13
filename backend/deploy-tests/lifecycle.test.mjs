// The Storage lifecycle rule the deploy sets (apply-lifecycle.cjs), against a stand-in for the
// Cloud Storage API.
import assert from "node:assert/strict";
import { createRequire } from "node:module";
import { test } from "node:test";

const require = createRequire(import.meta.url);
const { applyLifecycle } = require("../apply-lifecycle.cjs");
const rule = require("../storage-lifecycle.json");

/** A fake fetch: [buckets] exist; records every call. */
function storage(buckets, { patchStatus = 200 } = {}) {
  const calls = [];
  const fetch = async (url, init = {}) => {
    calls.push({ url, method: init.method ?? "GET", body: init.body, auth: init.headers?.Authorization });
    const name = decodeURIComponent(new URL(url).pathname.split("/").pop());
    const exists = buckets.includes(name);
    const status = init.method === "PATCH" ? patchStatus : exists ? 200 : 404;
    return { ok: status < 300, status, text: async () => (status < 300 ? "{}" : "denied") };
  };
  return { fetch, calls };
}

test("deletes exports, and only exports, a day after they're written", () => {
  assert.deepEqual(rule, { rule: [{ action: { type: "Delete" }, condition: { age: 1, matchesPrefix: ["exports/"] } }] });
});

test("sets the rule on a new project's default bucket", async () => {
  const { fetch, calls } = storage(["meadow.firebasestorage.app"]);
  assert.equal(await applyLifecycle("meadow", "token", fetch), "meadow.firebasestorage.app");
  const patch = calls.find((c) => c.method === "PATCH");
  assert.match(patch.url, /\/b\/meadow\.firebasestorage\.app\?fields=lifecycle$/);
  assert.deepEqual(JSON.parse(patch.body), { lifecycle: rule });
  assert.ok(calls.every((c) => c.auth === "Bearer token"));
});

test("falls back to an older project's appspot.com bucket", async () => {
  const { fetch, calls } = storage(["old.appspot.com"]);
  assert.equal(await applyLifecycle("old", "token", fetch), "old.appspot.com");
  assert.equal(calls.filter((c) => c.method === "PATCH").length, 1);
});

test("stops, saying why, when there's no bucket or the change is refused", async () => {
  await assert.rejects(applyLifecycle("none", "token", storage([]).fetch), /No default Storage bucket in none/);
  await assert.rejects(
    applyLifecycle("meadow", "token", storage(["meadow.firebasestorage.app"], { patchStatus: 403 }).fetch),
    /Couldn't set the lifecycle rule on meadow\.firebasestorage\.app: 403/,
  );
});
