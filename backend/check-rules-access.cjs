// Checks after every Storage deploy (see firebase.json) that Storage's rules may read Firestore.
// storage.rules reads chats and accounts from Firestore. Without the role below, Storage refuses
// every chat photo and every friend's profile photo. `firebase deploy` offers to grant the role
// only when a person runs it, not in CI, so .github/scripts/setup-deploy-access.sh grants it.
// CommonJS for the same reason as predeploy.cjs.
const { accessToken } = require("./apply-lifecycle.cjs");

const project = process.env.GCLOUD_PROJECT;
const ROLE = "roles/firebaserules.firestoreServiceAgent";
const AGENT = /^serviceAccount:service-\d+@gcp-sa-firebasestorage\.iam\.gserviceaccount\.com$/;

/** The commands that grant the role, for a project owner to run. */
function grantCommands(project) {
  return [
    `gcloud beta services identity create --service firebasestorage.googleapis.com --project ${project}`,
    `gcloud projects add-iam-policy-binding ${project} --role ${ROLE} --condition None \\`,
    `  --member serviceAccount:service-$(gcloud projects describe ${project} --format 'value(projectNumber)')@gcp-sa-firebasestorage.iam.gserviceaccount.com`,
  ].join("\n");
}

/**
 * Whether [project]'s IAM policy gives Storage's service agent [ROLE]: true or false, or null
 * when the policy can't be read.
 */
async function storageMayReadFirestore(project, token, fetchFn = fetch) {
  const response = await fetchFn(`https://cloudresourcemanager.googleapis.com/v1/projects/${encodeURIComponent(project)}:getIamPolicy`, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body: JSON.stringify({ options: { requestedPolicyVersion: 3 } }),
  });
  if (!response.ok) return null;
  const { bindings = [] } = await response.json();
  return bindings.some((binding) => binding.role === ROLE && !binding.condition && (binding.members ?? []).some((member) => AGENT.test(member)));
}

async function main() {
  if (!project) throw new Error("GCLOUD_PROJECT isn't set; run this through `firebase deploy`.");
  const allowed = await storageMayReadFirestore(project, await accessToken("https://www.googleapis.com/auth/cloud-platform"));
  if (allowed) {
    console.log("Storage rules may read Firestore.");
    return;
  }
  const what = allowed === null ? "Couldn't check that Storage rules may read Firestore" : "Storage rules may not read Firestore, so chat photos and friends' photos fail";
  const fix = `Run .github/scripts/setup-deploy-access.sh again, or run as a project owner:\n${grantCommands(project)}`;
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
