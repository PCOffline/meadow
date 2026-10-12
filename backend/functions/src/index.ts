import { initializeApp } from "firebase-admin/app";
import { setGlobalOptions } from "firebase-functions/v2";

initializeApp();
// Set once in backend/functions/.env, which Firebase loads when deploying and running.
setGlobalOptions({ region: process.env.MEADOW_REGION || "us-central1", maxInstances: 10 });

export {
  createProfile, createInviteLink, previewInviteLink, acceptInviteLink, sendInvite, respondInvite, cancelInvite,
  removeApprover, cancelRemoval, stepDown,
} from "./social";
export { createRequest, decideRequest, cancelRequest, saveLimit } from "./requests";
export { emergencyUnlock } from "./emergency";
export { exportMyData, deleteAccount } from "./account";
export { onChatMessage, onProtectionAlert, onProfileUpdated, hourlyCare, expireRequests } from "./triggers";
