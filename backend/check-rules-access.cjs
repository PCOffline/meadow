// Checks after every Storage deploy (see firebase.json) that Storage's rules may read Firestore.
// storage.rules reads chats and accounts from Firestore. Without the role below, Storage refuses
// every chat photo and every friend's profile photo. `firebase deploy` offers to grant the role
// only when a person runs it, not in CI, so .github/scripts/setup-backend-accounts.sh grants it.
// CommonJS for the same reason as predeploy.cjs.
const { accessToken } = require("./apply-lifecycle.cjs");

const project = process.env.GCLOUD_PROJECT;
const ROLE = "roles/firebaserules.firestoreServiceAgent";
const API = "https://cloudresourcemanager.googleapis.com/v1/projects";

/** Storage's service agent in the project with [number]. */
const agentOf = (number) => `serviceAccount:service-${number}@gcp-sa-firebasestorage.iam.gserviceaccount.com`;

/** The commands that grant the role, for a project owner to run. */
function grantCommands(project) {
  return [
    `gcloud beta services identity create --service firebasestorage.googleapis.com --project ${project}`,
    `gcloud projects add-iam-policy-binding ${project} --role ${ROLE} --condition None \\`,
    `  --member serviceAccount:service-$(gcloud projects describe ${project} --format 'value(projectNumber)')@gcp-sa-firebasestorage.iam.gserviceaccount.com`,
  ].join("\n");
}

/**
 * Whether [project]'s IAM policy gives its own Storage service agent [ROLE]: true or false, or
 * null when the project or its policy can't be read (refused, unreachable or garbled).
 */
async function storageMayReadFirestore(project, token, fetchFn = fetch) {
  const headers = { Authorization: `Bearer ${token}`, "Content-Type": "application/json" };
  const url = `${API}/${encodeURIComponent(project)}`;
  try {
    const about = await fetchFn(url, { method: "GET", headers });
    if (!about.ok) return null;
    const { projectNumber } = await about.json();
    if (!projectNumber) return null;
    const response = await fetchFn(`${url}:getIamPolicy`, {
      method: "POST",
      headers,
      body: JSON.stringify({ options: { requestedPolicyVersion: 3 } }),
    });
    if (!response.ok) return null;
    const { bindings = [] } = await response.json();
    const agent = agentOf(projectNumber);
    return bindings.some((binding) => binding.role === ROLE && !binding.condition && (binding.members ?? []).includes(agent));
  } catch {
    return null;
  }
}

async function main() {
  if (!project) throw new Error("GCLOUD_PROJECT isn't set; run this through `firebase deploy`.");
  const allowed = await storageMayReadFirestore(project, await accessToken("https://www.googleapis.com/auth/cloud-platform"));
  if (allowed) {
    console.log("Storage rules may read Firestore.");
    return;
  }
  const what = allowed === null ? "Couldn't check that Storage rules may read Firestore" : "Storage rules may not read Firestore, so chat photos and friends' photos fail";
  const fix = `Run .github/scripts/setup-backend-accounts.sh again, or run as a project owner:\n${grantCommands(project)}`;
  if (allowed === null) {
    console.warn(`${process.env.GITHUB_ACTIONS ? "::warning::" : ""}${what}. ${fix}`);
    return;
  }
  throw new Error(`${what}. ${fix}`);
}

if (require.main === module) {
  main().catch((error) => {
    console.error(error.message ?? error);
    process.exit(1);
  });
}

module.exports = { grantCommands, storageMayReadFirestore };
