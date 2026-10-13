import { initializeApp } from "firebase-admin/app";
import { setGlobalOptions } from "firebase-functions/v2";

initializeApp();
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
