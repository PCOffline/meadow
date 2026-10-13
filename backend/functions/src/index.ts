import { initializeApp } from "firebase-admin/app";
import { setGlobalOptions } from "firebase-functions/v2";

initializeApp();
// Only while integration tests measure coverage: the emulator stops its workers with SIGTERM,
// which would end them before Node writes what ran. Exiting normally writes it.
if (process.env.NODE_V8_COVERAGE) process.once("SIGTERM", () => process.exit(0));
// Set once in backend/functions/.env, which Firebase loads when deploying and running.
const region = process.env.MEADOW_REGION?.trim();
if (!region) throw new Error("Set MEADOW_REGION in backend/functions/.env, e.g. MEADOW_REGION=europe-west1");
setGlobalOptions({ region, maxInstances: 10 });

export {
  createProfile, createInviteLink, previewInviteLink, acceptInviteLink, sendInvite, respondInvite, cancelInvite,
  removeApprover, cancelRemoval, stepDown,
} from "./social";
export { createRequest, decideRequest, cancelRequest, saveLimit } from "./requests";
export { emergencyUnlock } from "./emergency";
export { exportMyData, deleteAccount } from "./account";
export { onChatMessage, onProtectionAlert, onProfileUpdated, hourlyCare, expireRequests, runSchedulesForTests } from "./triggers";
