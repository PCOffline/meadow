// The check after each Storage deploy that Storage rules may read Firestore
// (check-rules-access.cjs), against a stand-in for the Cloud Resource Manager API.
import assert from "node:assert/strict";
import { createRequire } from "node:module";
import { test } from "node:test";

const require = createRequire(import.meta.url);
const { grantCommands, storageMayReadFirestore } = require("../check-rules-access.cjs");

const ROLE = "roles/firebaserules.firestoreServiceAgent";
const AGENT = "serviceAccount:service-123@gcp-sa-firebasestorage.iam.gserviceaccount.com";

/** A fake fetch that answers getIamPolicy with [bindings], or with [status]; records every call. */
function resourceManager(bindings, status = 200) {
  const calls = [];
  const fetch = async (url, init = {}) => {
    calls.push({ url, method: init.method, auth: init.headers?.Authorization });
    return { ok: status < 300, status, json: async () => ({ bindings }) };
  };
  return { fetch, calls };
}

test("passes when Storage's service agent has the role", async () => {
  const { fetch, calls } = resourceManager([{ role: "roles/owner", members: ["user:a@b.c"] }, { role: ROLE, members: [AGENT] }]);
  assert.equal(await storageMayReadFirestore("meadow", "token", fetch), true);
  assert.deepEqual(calls, [{ url: "https://cloudresourcemanager.googleapis.com/v1/projects/meadow:getIamPolicy", method: "POST", auth: "Bearer token" }]);
});

test("fails when nobody has the role", async () => {
  const { fetch } = resourceManager([{ role: "roles/owner", members: ["user:a@b.c"] }]);
  assert.equal(await storageMayReadFirestore("meadow", "token", fetch), false);
});

test("fails when the policy has no bindings at all", async () => {
  const { fetch } = resourceManager(undefined);
  assert.equal(await storageMayReadFirestore("meadow", "token", fetch), false);
});

test("fails when the role goes to some other account", async () => {
  const { fetch } = resourceManager([{ role: ROLE, members: ["serviceAccount:github-deploy@meadow.iam.gserviceaccount.com"] }]);
  assert.equal(await storageMayReadFirestore("meadow", "token", fetch), false);
});

test("fails when the role holds only under a condition", async () => {
  const { fetch } = resourceManager([{ role: ROLE, members: [AGENT], condition: { expression: "false" } }]);
  assert.equal(await storageMayReadFirestore("meadow", "token", fetch), false);
});

test("can't tell when the policy can't be read", async () => {
  const { fetch } = resourceManager([], 403);
  assert.equal(await storageMayReadFirestore("meadow", "token", fetch), null);
});

test("names the project in the commands that grant the role", () => {
  const commands = grantCommands("meadow");
  assert.match(commands, /services identity create --service firebasestorage\.googleapis\.com --project meadow/);
  assert.match(commands, /add-iam-policy-binding meadow --role roles\/firebaserules\.firestoreServiceAgent/);
  assert.match(commands, /@gcp-sa-firebasestorage\.iam\.gserviceaccount\.com/);
});
