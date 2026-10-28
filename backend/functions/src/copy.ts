/**
 * Every word the backend shows to people: errors, chat notes and notifications.
 *
 * Written to the rules in AGENTS.md ("Writing"), with the terms in CONTRIBUTING.md ("Words").
 * These must match the app's strings.xml.
 */
import { capitalize } from "./format";

interface Person {
  displayName: string;
  flower: string;
  /** Someone with a photo goes by it, so their flower isn't put in front of their name. */
  photoUrl?: string | null;
}

/** The flower that leads someone's name, or nothing once they use a photo. */
const lead = (p: Person): string => (p.photoUrl ? "" : `${p.flower} `);

export const errors = {
  signIn: "Sign in first.",
  signInAgain: "To keep your account safe, sign out and sign in again. Then try again.",
  noProfile: "That person hasn't set up Meadow yet.",
  generic: "Something in this isn't correct. Check it and try again.",
  noBumblebees: "Invite a Bumblebee first.",
  noFriendCode: "Meadow can't make a friend code now. Try again.",
  nameRequired: "Choose a name.",
  nameTooLong: "Use 30 characters or fewer for your name.",
  codeRequired: "Enter your friend's code.",
  codeUnknown: "Nobody has this code. Check it and try again.",
  ownCode: "This is your own code. 🌼",
  tooManyCodes: "You tried many friend codes today. Try again tomorrow, or send an invite link. 🐝",
  alreadyBumblebee: (name: string) => `${name} is already your Bumblebee.`,
  alreadyInvited: (name: string) => `You already invited ${name}.`,
  inviteeBusy: (name: string) => `${name} has many invites to answer. Try again later.`,
  inviteGone: "This invite isn't available anymore.",
  inviteAnswered: "This invite already has an answer.",
  linkGone: "This invite link doesn't work anymore. Ask for a new one.",
  linkUsed: "Someone already accepted this invite link. Ask for a new one.",
  linkExpired: "This invite link expired. Ask for a new one.",
  ownLink: "This is your own invite link. Send it to a friend. 🐝",
  alreadyTheirBumblebee: (name: string) => `You're already ${name}'s Bumblebee.`,
  notBumblebees: "You two aren't Bumblebees.",
  whichApp: "Which app is this for?",
  setALimit: "Set a daily or weekly limit.",
  noLimit: "This app has no limit.",
  badDate: "Check the date.",
  unknownRequest: "Meadow can't send this type of request.",
  tooManyRequests: "You have many requests that wait for an answer. Give your Bumblebees some time. 🌿",
  duplicateRequest: "You already asked about this app. Wait for an answer, or withdraw the request first.",
  requestGone: "This request isn't available anymore.",
  requestAnswered: "This request already has an answer.",
  requestExpired: "This request expired. They can ask again.",
  ownRequest: "You can't answer your own request. 🌱",
  notYourRequest: "Only their Bumblebees can answer this.",
  noLongerBumblebee: "You're no longer their Bumblebee.",
  emergencyMinutes: "Choose 15, 30 or 60 minutes.",
  emergencyReason: "Write a short reason for the emergency.",
  emergencyUsedUp: (allowance: number) =>
    `You used all ${allowance} emergency unlocks for the last 30 days. Ask a Bumblebee for more time.`,
  emergencyActive: "An emergency unlock is already on.",
  slowDown: "Too many tries in a short time. Wait a little, then try again. 🌿",
  pickABumblebee: "Choose one or more Bumblebees to ask.",
  notYourBumblebees: "You can only ask your own Bumblebees.",
  removalPending: "This Bumblebee is already leaving.",
  noRemoval: "This Bumblebee isn't leaving.",
  needsAYes: "This makes the limit easier, so a Bumblebee must approve it.",
  noSharedChat: "You can only do this with someone you already chat with.",
};

export const chatNotes = {
  newBumblebee: (bee: Person, owner: Person) => `${lead(bee)}${bee.displayName} is now ${owner.displayName}'s Bumblebee`,
  removedBumblebee: (owner: Person, bee: Person) => `${owner.displayName} ended ${bee.displayName}'s time as their Bumblebee`,
  removalScheduled: (owner: Person, bee: Person) =>
    `${owner.displayName} is ending ${bee.displayName}'s time as their Bumblebee. This happens in 24 hours`,
  removalCancelled: (owner: Person, bee: Person) => `${owner.displayName} is keeping ${bee.displayName} as their Bumblebee 🐝`,
  steppedDown: (bee: Person, owner: Person) => `${bee.displayName} stepped down as ${owner.displayName}'s Bumblebee`,
  request: (owner: Person, summary: string, usage: string | null) =>
    `💌 ${owner.displayName} asks for ${summary}${usage ? `. ${usage}` : ""}`,
  yes: (bee: Person, summary: string) => `🌸 ${bee.displayName} approved ${summary}`,
  notToday: (bee: Person, summary: string) => `🍃 ${bee.displayName} denied ${summary}`,
  emergency: (headline: string, scope: string, reason: string) => `${headline}. Lifted ${scope}. Reason: “${reason}”`,
};

/** What a chat notification says for a message that is a picture. Matches the app's chat_photo / chat_gif. */
export const chatMedia = {
  photo: "📷 Photo",
  gif: "GIF",
};

/** Push notification texts. Titles are short; bodies say what to do next. */
export const pushes = {
  invite: (owner: Person) => ({
    title: `${lead(owner)}${owner.displayName} wants you as their Bumblebee`,
    body: "Open Meadow to approve. Then you decide when they ask for more time.",
  }),
  /** Someone you chat with offers to look after your garden. */
  offer: (bee: Person) => ({
    title: `${lead(bee)}${bee.displayName} offers to be your Bumblebee`,
    body: "Open Meadow to approve. Then they decide when you ask for more time.",
  }),
  offerAccepted: (owner: Person) => ({
    title: `${lead(owner)}You're ${owner.displayName}'s Bumblebee now`,
    body: "You decide when they ask for more time. 🐝",
  }),
  inviteAccepted: (bee: Person) => ({
    title: `${lead(bee)}${bee.displayName} is your Bumblebee now`,
    body: "Your limits are locked. Only your Bumblebees can give you more time. 🐝",
  }),
  /** Only the Bumblebee who was removed hears about it. */
  removedBumblebee: (owner: Person) => ({
    title: `${lead(owner)}${owner.displayName} doesn't need you as their Bumblebee anymore`,
    body: "Their requests stop coming to you. Thank you for looking after their garden. 🌼",
  }),
  /** Gives the Bumblebee the cool-off day to check in. */
  removalScheduled: (owner: Person) => ({
    title: `${lead(owner)}${owner.displayName} is ending your time as their Bumblebee`,
    body: "This happens in 24 hours. Until then, their requests still come to you. You can talk to them about it.",
  }),
  removalCancelled: (owner: Person) => ({
    title: `${lead(owner)}${owner.displayName} changed their mind`,
    body: "You're still their Bumblebee. 🐝",
  }),
  steppedDown: (bee: Person, remaining: number) => ({
    title: `${lead(bee)}${bee.displayName} stepped down as your Bumblebee`,
    body: remaining > 0
      ? "Your other Bumblebees still look after your garden."
      : "Invite a new Bumblebee to keep your limits locked.",
  }),
  request: (owner: Person, summary: string, message: string, usage: string | null) => ({
    title: `${lead(owner)}${owner.displayName} asks for ${summary}`,
    body: [usage, message ? `“${message}”` : "Tap to answer, or approve from here."].filter(Boolean).join(" "),
  }),
  decided: (bee: Person, approved: boolean, summary: string, note: string) => ({
    title: approved ? `🌸 ${bee.displayName} approved` : `🍃 ${bee.displayName} denied`,
    body: note ? `${capitalize(summary)}. “${note}”` : `${capitalize(summary)}.`,
  }),
  chat: (sender: Person, text: string) => ({ title: `${lead(sender)}${sender.displayName}`, body: text }),
  /**
   * Deliberately vague: which protection, strict mode and battery settings are the person's own
   * business. Bumblebees only learn that Meadow may not be working.
   */
  protectionOff: (owner: Person) => ({
    title: `⚠️ Meadow possibly doesn't work on ${owner.displayName}'s phone`,
    body: "Their limits possibly don't work now. Talk to them about it.",
  }),
  gardenClosed: (owner: Person) => ({
    title: `${lead(owner)}${owner.displayName} closed their Meadow garden`,
    body: "Their account is deleted. You don't get their requests anymore.",
  }),
  bumblebeeLeftMeadow: (bee: Person) => ({
    title: `${lead(bee)}${bee.displayName} left Meadow`,
    body: "They aren't your Bumblebee anymore. You can invite a different person from People.",
  }),
  quietPhone: (owner: Person) => ({
    title: `⚠️ Meadow possibly doesn't work on ${owner.displayName}'s phone`,
    body: "Meadow got no signal from their phone for some time. Talk to them about it.",
  }),
  emergency: (headline: string, scope: string, reason: string) => ({
    title: headline,
    body: `Lifted ${scope}. “${reason}”`,
  }),
  /** Sent only when someone asks for it, from Troubleshoot. */
  test: (channel: "chat" | "requests") => ({
    title: channel === "chat" ? "Chat notifications work 💬" : "Request notifications work 💌",
    body: "If you can see this, Meadow can reach this phone.",
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
