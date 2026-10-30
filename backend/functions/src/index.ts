import { initializeApp } from "firebase-admin/app";
import { setGlobalOptions } from "firebase-functions/v2";

initializeApp();
// Only while integration tests measure coverage: the emulator stops its workers with SIGTERM,
// which would end them before Node writes what ran. Exiting normally writes it.
if (process.env.NODE_V8_COVERAGE) process.once("SIGTERM", () => process.exit(0));
// Set once in backend/functions/.env, which Firebase loads when deploying and running.
const region = process.env.MEADOW_REGION?.trim();
if (!region) throw new Error("Set MEADOW_REGION in backend/functions/.env, e.g. MEADOW_REGION=europe-west1");
// A whole CPU rather than the default sixth of one: starting up (loading the Admin SDK) and each
// call are several times faster, and one instance answers many calls at once (concurrency needs a
// whole CPU), so far fewer calls wait for a new one to start. Billing is per request, so a small
// group still stays inside the free allowance.
// Every function runs as its own service account, which holds only what the functions use
// (.github/scripts/setup-deploy-access.sh creates it). "name@" means name@<project>.iam.gserviceaccount.com.
setGlobalOptions({ region, maxInstances: 10, cpu: 1, memory: "512MiB", concurrency: 80, serviceAccount: "meadow-functions@" });

export {
  createProfile, createInviteLink, previewInviteLink, acceptInviteLink, sendInvite, inviteFromChat, respondInvite, cancelInvite,
  removeApprover, cancelRemoval, stepDown,
} from "./social";
export { createRequest, decideRequest, cancelRequest, saveLimit } from "./requests";
export { emergencyUnlock } from "./emergency";
export { exportMyData, deleteAccount } from "./account";
export { sendTestPush } from "./diagnostics";
export { onChatMessage, onProtectionAlert, onProfileUpdated, hourlyCare, expireRequests, runSchedulesForTests } from "./triggers";
