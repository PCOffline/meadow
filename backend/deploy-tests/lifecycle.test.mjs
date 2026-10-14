// The Storage lifecycle rule the deploy sets (apply-lifecycle.cjs), against a stand-in for the
// Cloud Storage API.
import assert from "node:assert/strict";
import { createRequire } from "node:module";
import { test } from "node:test";

const require = createRequire(import.meta.url);
const { applyLifecycle, mergeLifecycle } = require("../apply-lifecycle.cjs");
const rule = require("../storage-lifecycle.json");

/**
 * A fake fetch: [buckets] exist, with [lifecycle] (none when undefined), which a PATCH replaces
 * as the real API does; records every call.
 */
function storage(buckets, { patchStatus = 200, lifecycle } = {}) {
  const calls = [];
  const bucket = { lifecycle };
  const fetch = async (url, init = {}) => {
    calls.push({ url, method: init.method ?? "GET", body: init.body, auth: init.headers?.Authorization });
    const name = decodeURIComponent(new URL(url).pathname.split("/").pop());
    const exists = buckets.includes(name);
    const status = init.method === "PATCH" ? patchStatus : exists ? 200 : 404;
    if (init.method === "PATCH" && status < 300) bucket.lifecycle = JSON.parse(init.body).lifecycle;
    const body = status < 300 ? JSON.stringify(bucket.lifecycle ? { name, lifecycle: bucket.lifecycle } : { name }) : "denied";
    return { ok: status < 300, status, json: async () => JSON.parse(body), text: async () => body };
  };
  return { fetch, calls, bucket };
}

const foreign = [
  { action: { type: "Delete" }, condition: { age: 30, matchesPrefix: ["tmp/"] } },
  { action: { type: "SetStorageClass", storageClass: "COLDLINE" }, condition: { age: 90 } },
];

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

test("keeps rules someone else added to the bucket", async () => {
  const { fetch, bucket } = storage(["meadow.firebasestorage.app"], { lifecycle: { rule: foreign } });
  await applyLifecycle("meadow", "token", fetch);
  assert.deepEqual(bucket.lifecycle, { rule: [...foreign, ...rule.rule] });
});

test("running again changes nothing and doesn't add the rule twice", async () => {
  const { fetch, bucket } = storage(["meadow.firebasestorage.app"], { lifecycle: { rule: foreign } });
  await applyLifecycle("meadow", "token", fetch);
  const once = structuredClone(bucket.lifecycle);
  await applyLifecycle("meadow", "token", fetch);
  assert.deepEqual(bucket.lifecycle, once);
  assert.equal(bucket.lifecycle.rule.filter((r) => r.condition.matchesPrefix?.includes("exports/")).length, 1);
});

test("replaces an older version of Meadow's rule, but not a broader one someone else wrote", () => {
  const older = { action: { type: "Delete" }, condition: { age: 7, matchesPrefix: ["exports/"] } };
  const broader = { action: { type: "Delete" }, condition: { age: 30, matchesPrefix: ["exports/", "tmp/"] } };
  assert.deepEqual(mergeLifecycle({ rule: [older, broader] }), { rule: [broader, ...rule.rule] });
});

test("sets the rule on a bucket with no lifecycle, or an empty one", async () => {
  for (const lifecycle of [undefined, {}, { rule: [] }]) {
    const { fetch, bucket } = storage(["meadow.firebasestorage.app"], { lifecycle });
    await applyLifecycle("meadow", "token", fetch);
    assert.deepEqual(bucket.lifecycle, rule);
  }
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
