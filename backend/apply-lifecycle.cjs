// Applies storage-lifecycle.json to the project's default bucket after every Storage deploy (see
// firebase.json), so exports never downloaded are deleted after a day. Other rules on the bucket
// are kept; only Meadow's own is replaced. The Firebase CLI deploys
// Storage rules but not bucket settings; this does the rest with Google Cloud's own sign-in.
// CommonJS for the same reason as predeploy.cjs.
const { execFileSync } = require("node:child_process");
const lifecycle = require("./storage-lifecycle.json");

const project = process.env.GCLOUD_PROJECT;
const API = "https://storage.googleapis.com/storage/v1/b";

/** Application Default Credentials (CI, or `gcloud auth application-default login`), else gcloud's own sign-in. */
async function accessToken(scopes = "https://www.googleapis.com/auth/devstorage.full_control") {
  try {
    const { GoogleAuth } = require("google-auth-library");
    const token = await new GoogleAuth({ scopes }).getAccessToken();
    if (token) return token;
  } catch {
    // Not signed in that way; try gcloud.
  }
  try {
    return execFileSync("gcloud", ["auth", "print-access-token"], { encoding: "utf8", stdio: ["ignore", "pipe", "ignore"] }).trim();
  } catch {
    throw new Error(
      "Couldn't sign in to Google Cloud. Run `gcloud auth application-default login` once, then deploy again.",
    );
  }
}

/** The rules that clean up the same files the same way: same action, same prefixes. */
const sameTarget = (a, b) =>
  a.action?.type === b.action?.type &&
  JSON.stringify([...(a.condition?.matchesPrefix ?? [])].sort()) === JSON.stringify([...(b.condition?.matchesPrefix ?? [])].sort());

/**
 * [current]'s rules with Meadow's own (any rule aimed at the same files as one in [lifecycle])
 * replaced by [lifecycle]'s, so rules someone else added to the bucket stay as they are.
 */
function mergeLifecycle(current, ours = lifecycle) {
  const theirs = (current?.rule ?? []).filter((rule) => !ours.rule.some((own) => sameTarget(rule, own)));
  return { rule: [...theirs, ...ours.rule] };
}

/**
 * Adds Meadow's [lifecycle] rules to [project]'s default bucket, keeping the bucket's other
 * rules, and returns the bucket's name. New projects' default bucket is
 * <project>.firebasestorage.app; older ones use <project>.appspot.com.
 */
async function applyLifecycle(project, token, fetchFn = fetch) {
  const call = (path, init = {}) =>
    fetchFn(`${API}/${path}`, { ...init, headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" } });
  let bucket;
  let current;
  for (const name of [`${project}.firebasestorage.app`, `${project}.appspot.com`]) {
    const found = await call(`${encodeURIComponent(name)}?fields=name,lifecycle`);
    if (found.ok) {
      bucket = name;
      current = (await found.json()).lifecycle;
      break;
    }
    if (found.status !== 404) throw new Error(`Couldn't read bucket ${name}: ${found.status} ${await found.text()}`);
  }
  if (!bucket) throw new Error(`No default Storage bucket in ${project}. Create it first (docs/self-hosting.md, 2.5).`);
  const updated = await call(`${encodeURIComponent(bucket)}?fields=lifecycle`, {
    method: "PATCH",
    body: JSON.stringify({ lifecycle: mergeLifecycle(current) }),
  });
  if (!updated.ok) throw new Error(`Couldn't set the lifecycle rule on ${bucket}: ${updated.status} ${await updated.text()}`);
  return bucket;
}

async function main() {
  if (!project) throw new Error("GCLOUD_PROJECT isn't set; run this through `firebase deploy`.");
  const bucket = await applyLifecycle(project, await accessToken());
  console.log(`Storage lifecycle rule set on gs://${bucket}: exports never downloaded are deleted after a day.`);
}

if (require.main === module) {
  main().catch((error) => {
    console.error(error.message ?? error);
    process.exit(1);
  });
}

module.exports = { accessToken, applyLifecycle, mergeLifecycle };
