// Applies storage-lifecycle.json to the project's default bucket after every Storage deploy (see
// firebase.json), so exports never downloaded are deleted after a day. The Firebase CLI deploys
// Storage rules but not bucket settings; this does the rest with Google Cloud's own sign-in.
// CommonJS for the same reason as predeploy.cjs.
const { execFileSync } = require("node:child_process");
const lifecycle = require("./storage-lifecycle.json");

const project = process.env.GCLOUD_PROJECT;
const API = "https://storage.googleapis.com/storage/v1/b";

/** Application Default Credentials (CI, or `gcloud auth application-default login`), else gcloud's own sign-in. */
async function accessToken() {
  try {
    const { GoogleAuth } = require("google-auth-library");
    const token = await new GoogleAuth({ scopes: "https://www.googleapis.com/auth/devstorage.full_control" }).getAccessToken();
    if (token) return token;
  } catch {
    // Not signed in that way; try gcloud.
  }
  try {
    return execFileSync("gcloud", ["auth", "print-access-token"], { encoding: "utf8", stdio: ["ignore", "pipe", "ignore"] }).trim();
  } catch {
    throw new Error(
      "Couldn't sign in to Google Cloud to set the Storage lifecycle rule. Run `gcloud auth application-default login` once, then deploy again.",
    );
  }
}

/**
 * Sets [lifecycle] on [project]'s default bucket and returns the bucket's name. New projects'
 * default bucket is <project>.firebasestorage.app; older ones use <project>.appspot.com.
 */
async function applyLifecycle(project, token, fetchFn = fetch) {
  const call = (path, init = {}) =>
    fetchFn(`${API}/${path}`, { ...init, headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" } });
  let bucket;
  for (const name of [`${project}.firebasestorage.app`, `${project}.appspot.com`]) {
    const found = await call(`${encodeURIComponent(name)}?fields=name`);
    if (found.ok) {
      bucket = name;
      break;
    }
    if (found.status !== 404) throw new Error(`Couldn't read bucket ${name}: ${found.status} ${await found.text()}`);
  }
  if (!bucket) throw new Error(`No default Storage bucket in ${project}. Create it first (docs/self-hosting.md, 2.5).`);
  const updated = await call(`${encodeURIComponent(bucket)}?fields=lifecycle`, {
    method: "PATCH",
    body: JSON.stringify({ lifecycle }),
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

module.exports = { applyLifecycle };
