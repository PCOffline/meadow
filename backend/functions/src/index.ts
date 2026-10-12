import { initializeApp } from "firebase-admin/app";
import { setGlobalOptions } from "firebase-functions/v2";

initializeApp();
setGlobalOptions({ region: "us-central1", maxInstances: 10 });

export {
  createProfile, createInviteLink, previewInviteLink, acceptInviteLink, sendInvite, respondInvite, cancelInvite,
  removeApprover, cancelRemoval, stepDown,
} from "./social";
export { createRequest, decideRequest, cancelRequest, saveLimit } from "./requests";
export { emergencyUnlock } from "./emergency";
export { exportMyData, deleteAccount } from "./account";
export { onChatMessage, onProtectionAlert, onProfileUpdated, hourlyCare } from "./triggers";
