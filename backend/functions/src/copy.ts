/**
 * Every word the backend shows to people: errors, chat notes and notifications.
 *
 * Voice: warm, calm and plain. Sentence case. At most one emoji, at the end.
 * Terms: "Bumblebee" (a friend who looks after your garden), "garden", "limit",
 * "extra time", "approve" / "deny". These must match the app's strings.xml.
 */
import { capitalize } from "./format";

interface Person {
  displayName: string;
  flower: string;
}

export const errors = {
  signIn: "Please sign in first.",
  signInAgain: "For your safety, please sign out and sign in again, then try once more.",
  noProfile: "That person hasn't set up Meadow yet.",
  generic: "Something in that didn't look right. Please check it and try again.",
  noBumblebees: "Invite a Bumblebee first.",
  noFriendCode: "Couldn't find a free friend code. Please try again.",
  nameRequired: "Please choose a name.",
  nameTooLong: "Names can be up to 30 characters.",
  codeRequired: "Enter your friend's code.",
  codeUnknown: "No one has that code. Could you double-check it?",
  ownCode: "That's your own code. 🌼",
  tooManyCodes: "You've tried a lot of friend codes today. Please try again tomorrow, or send an invite link instead. 🐝",
  alreadyBumblebee: (name: string) => `${name} is already your Bumblebee.`,
  alreadyInvited: (name: string) => `You've already invited ${name}.`,
  inviteeBusy: (name: string) => `${name} has lots of invites waiting already. Please try again later.`,
  inviteGone: "That invite isn't around anymore.",
  inviteAnswered: "That invite was already answered.",
  linkGone: "That invite link isn't valid anymore. Ask for a new one.",
  linkUsed: "Someone already accepted this invite link. Ask for a new one.",
  linkExpired: "This invite link has expired. Ask for a new one.",
  ownLink: "That's your own invite link. Send it to a friend instead. 🐝",
  alreadyTheirBumblebee: (name: string) => `You're already ${name}'s Bumblebee.`,
  notBumblebees: "You two aren't Bumblebees.",
  whichApp: "Which app is this about?",
  setALimit: "Set a daily or weekly limit.",
  noLimit: "That app doesn't have a limit.",
  badDate: "That date doesn't look right.",
  unknownRequest: "That kind of request isn't supported.",
  tooManyRequests: "You have lots of requests waiting already. Give your Bumblebees a moment. 🌿",
  duplicateRequest: "You've already asked about this app. Wait for an answer, or cancel it first.",
  requestGone: "That request isn't around anymore.",
  requestAnswered: "That request was already answered.",
  requestExpired: "That request expired. If they still need it, they can ask again.",
  ownRequest: "You can't answer your own request. 🌱",
  notYourRequest: "Only their Bumblebees can answer this.",
  noLongerBumblebee: "You're no longer their Bumblebee.",
  emergencyMinutes: "Pick 15, 30 or 60 minutes.",
  emergencyReason: "Please say briefly what the emergency is.",
  emergencyUsedUp: (allowance: number) =>
    `You've used all ${allowance} emergency unlocks for the last 30 days. Ask a Bumblebee for extra time instead.`,
  emergencyActive: "An emergency unlock is already on.",
  slowDown: "That's a lot in a short time. Please wait a little and try again. 🌿",
  pickABumblebee: "Pick at least one Bumblebee to ask.",
  notYourBumblebees: "You can only ask your own Bumblebees.",
  removalPending: "That Bumblebee is already leaving.",
  noRemoval: "That Bumblebee isn't leaving.",
  needsAYes: "That would loosen the limit, so it needs a Bumblebee's approval.",
};

export const chatNotes = {
  newBumblebee: (bee: Person, owner: Person) => `${bee.flower} ${bee.displayName} is now ${owner.displayName}'s Bumblebee`,
  removedBumblebee: (owner: Person, bee: Person) => `${owner.displayName} ended ${bee.displayName}'s time as their Bumblebee`,
  removalScheduled: (owner: Person, bee: Person) =>
    `${owner.displayName} is ending ${bee.displayName}'s time as their Bumblebee. It takes effect in 24 hours`,
  removalCancelled: (owner: Person, bee: Person) => `${owner.displayName} is keeping ${bee.displayName} as their Bumblebee 🐝`,
  steppedDown: (bee: Person, owner: Person) => `${bee.displayName} stepped down as ${owner.displayName}'s Bumblebee`,
  request: (owner: Person, summary: string, usage: string | null) =>
    `💌 ${owner.displayName} asks for ${summary}${usage ? `. ${usage}` : ""}`,
  yes: (bee: Person, summary: string) => `🌸 ${bee.displayName} approved ${summary}`,
  notToday: (bee: Person, summary: string) => `🍃 ${bee.displayName} denied ${summary}`,
  emergency: (headline: string, scope: string, reason: string) => `${headline}. Lifted ${scope}. Reason: “${reason}”`,
};

/** Push notification texts. Titles are short; bodies say what to do next. */
export const pushes = {
  invite: (owner: Person) => ({
    title: `${owner.flower} ${owner.displayName} would like you as their Bumblebee`,
    body: "Open Meadow to approve. You'll get to decide whenever they ask for extra time.",
  }),
  inviteAccepted: (bee: Person) => ({
    title: `${bee.flower} ${bee.displayName} is your Bumblebee now`,
    body: "Your limits are locked in. Only your Bumblebees can give you extra time. 🐝",
  }),
  /** Only the Bumblebee who was removed hears about it. */
  removedBumblebee: (owner: Person) => ({
    title: `${owner.flower} ${owner.displayName} doesn't need you as their Bumblebee anymore`,
    body: "Their requests won't come to you anymore. Thanks for looking after their garden. 🌼",
  }),
  /** Gives the Bumblebee the cool-off day to check in. */
  removalScheduled: (owner: Person) => ({
    title: `${owner.flower} ${owner.displayName} is ending your time as their Bumblebee`,
    body: "It takes effect in 24 hours. Until then their requests still come to you. Maybe check in with them?",
  }),
  removalCancelled: (owner: Person) => ({
    title: `${owner.flower} ${owner.displayName} changed their mind`,
    body: "You're still their Bumblebee. 🐝",
  }),
  steppedDown: (bee: Person, remaining: number) => ({
    title: `${bee.flower} ${bee.displayName} stepped down as your Bumblebee`,
    body: remaining > 0
      ? "Your other Bumblebees are still looking after your garden."
      : "Invite a new Bumblebee so your limits stay locked in.",
  }),
  request: (owner: Person, summary: string, message: string, usage: string | null) => ({
    title: `${owner.flower} ${owner.displayName} asks for ${summary}`,
    body: [usage, message ? `“${message}”` : "Tap to answer, or approve right from here."].filter(Boolean).join(" "),
  }),
  decided: (bee: Person, approved: boolean, summary: string, note: string) => ({
    title: approved ? `🌸 ${bee.displayName} approved` : `🍃 ${bee.displayName} denied`,
    body: note ? `${capitalize(summary)}. “${note}”` : `${capitalize(summary)}.`,
  }),
  chat: (sender: Person, text: string) => ({ title: `${sender.flower} ${sender.displayName}`, body: text }),
  /**
   * Deliberately vague: which protection, strict mode and battery settings are the person's own
   * business. Bumblebees only learn that Meadow may not be working.
   */
  protectionOff: (owner: Person) => ({
    title: `⚠️ Meadow may not be looking after ${owner.displayName}'s garden`,
    body: "Their limits may not be working right now. Maybe check in with them?",
  }),
  gardenClosed: (owner: Person) => ({
    title: `${owner.flower} ${owner.displayName} closed their Meadow garden`,
    body: "Their account is gone, so you won't get their requests anymore.",
  }),
  bumblebeeLeftMeadow: (bee: Person) => ({
    title: `${bee.flower} ${bee.displayName} left Meadow`,
    body: "They're no longer one of your Bumblebees. You can invite someone new from Circle.",
  }),
  quietPhone: (owner: Person) => ({
    title: `⚠️ Meadow may not be looking after ${owner.displayName}'s garden`,
    body: "Meadow hasn't heard from their phone in a while. Maybe check in with them?",
  }),
  emergency: (headline: string, scope: string, reason: string) => ({
    title: headline,
    body: `Lifted ${scope}. “${reason}”`,
  }),
};

export function emergencyHeadline(owner: Person, offline: boolean, overAllowance: boolean): string {
  const extra = [offline ? "while offline" : null, overAllowance ? "beyond their monthly allowance" : null]
    .filter(Boolean).join(", ");
  return `🚨 ${owner.displayName} used an emergency unlock${extra ? ` (${extra})` : ""}`;
}

export function emergencyScope(appName: string | null, minutes: number): string {
  return appName ? `${appName} for ${minutes} minutes` : `all limits for ${minutes} minutes`;
}
