/**
 * Every word the backend shows to people: errors, chat notes and notifications.
 *
 * Voice: warm, calm and plain. Sentence case. At most one emoji, at the end.
 * Terms: "Bumblebee" (a friend who looks after your garden), "garden", "limit",
 * "extra time", "say yes" / "not today". These must match the app's strings.xml.
 */
import { capitalize } from "./format";

interface Person {
  displayName: string;
  flower: string;
}

const plural = (n: number, one: string, many: string) => (n === 1 ? one : many);

export const errors = {
  signIn: "Please sign in first.",
  noProfile: "That person hasn't set up Meadow yet.",
  generic: "Something in that request wasn't right.",
  needsEmail: "Add an email to your account first, so your garden can't get lost.",
  noBumblebees: "Invite a Bumblebee first.",
  noFriendCode: "Couldn't find a free friend code. Please try again.",
  nameRequired: "Please choose a name.",
  nameTooLong: "Names can be up to 30 characters.",
  codeRequired: "Enter your friend's code.",
  codeUnknown: "No one has that code. Could you double-check it?",
  ownCode: "That's your own code. 🌼",
  alreadyBumblebee: (name: string) => `${name} is already your Bumblebee.`,
  alreadyInvited: (name: string) => `You've already invited ${name}.`,
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
  duplicateRequest: "You've already asked for this. It's waiting for an answer.",
  strictAlreadyOff: "Strict mode is already off.",
  requestGone: "That request isn't around anymore.",
  requestAnswered: "That request was already answered.",
  ownRequest: "You can't answer your own request. 🌱",
  notYourRequest: "Only their Bumblebees can answer this.",
  noLongerBumblebee: "You're no longer their Bumblebee.",
  emergencyMinutes: "Pick 15, 30 or 60 minutes.",
  emergencyReason: "Please say briefly what the emergency is.",
  emergencyUsedUp: (allowance: number) =>
    `You've used all ${allowance} emergency unlocks for the last 30 days. Ask a Bumblebee for extra time instead.`,
  emergencyActive: "An emergency unlock is already on.",
};

export const chatNotes = {
  newBumblebee: (bee: Person, owner: Person) => `${bee.flower} ${bee.displayName} is now ${owner.displayName}'s Bumblebee`,
  removedBumblebee: (owner: Person, bee: Person) => `${owner.displayName} removed ${bee.displayName} as a Bumblebee`,
  steppedDown: (bee: Person, owner: Person) => `${bee.displayName} stepped down as ${owner.displayName}'s Bumblebee`,
  request: (owner: Person, summary: string) => `💌 ${owner.displayName} asks for ${summary}`,
  yes: (bee: Person, summary: string) => `🌸 ${bee.displayName} said yes to ${summary}`,
  notToday: (bee: Person, summary: string) => `🍃 ${bee.displayName} said not today to ${summary}`,
  emergency: (headline: string, scope: string, reason: string) => `${headline}. Lifted ${scope}. Reason: “${reason}”`,
};

/** Push notification texts. Titles are short; bodies say what to do next. */
export const pushes = {
  invite: (owner: Person) => ({
    title: `${owner.flower} ${owner.displayName} would like you as their Bumblebee`,
    body: "Open Meadow to say yes. You'll get to decide whenever they ask for extra time.",
  }),
  inviteAccepted: (bee: Person) => ({
    title: `${bee.flower} ${bee.displayName} is your Bumblebee now`,
    body: "Your limits are locked in. Only your Bumblebees can give you extra time. 🐝",
  }),
  removedBumblebee: (owner: Person, bee: Person, remaining: number) => ({
    title: `${owner.flower} ${owner.displayName} removed ${bee.displayName} as a Bumblebee`,
    body: remaining > 0
      ? `${owner.displayName} has ${remaining} ${plural(remaining, "Bumblebee", "Bumblebees")} left.`
      : `${owner.displayName} has no Bumblebees left, so their limits aren't locked in anymore.`,
  }),
  steppedDown: (bee: Person, remaining: number) => ({
    title: `${bee.flower} ${bee.displayName} stepped down as your Bumblebee`,
    body: remaining > 0
      ? "Your other Bumblebees are still looking after your garden."
      : "Invite a new Bumblebee so your limits stay locked in.",
  }),
  request: (owner: Person, summary: string, message: string) => ({
    title: `${owner.flower} ${owner.displayName} asks for ${summary}`,
    body: message ? `“${message}”` : "Tap to answer, or say yes right from here.",
  }),
  decided: (bee: Person, approved: boolean, summary: string, note: string) => ({
    title: approved ? `🌸 ${bee.displayName} said yes!` : `🍃 ${bee.displayName} said not today`,
    body: note ? `${capitalize(summary)}. “${note}”` : `${capitalize(summary)}.`,
  }),
  chat: (sender: Person, text: string) => ({ title: `${sender.flower} ${sender.displayName}`, body: text }),
  protectionOff: (owner: Person, lost: string[]) => ({
    title: `⚠️ ${owner.displayName} turned off ${protectionNames(lost)}`,
    body: "Their limits may not be working right now. Maybe check in with them?",
  }),
  quietPhone: (owner: Person) => ({
    title: `🌙 ${owner.displayName}'s phone has gone quiet`,
    body: "Meadow hasn't heard from it in over 6 hours. It may be off, or Meadow may have been removed.",
  }),
  emergency: (headline: string, scope: string, reason: string) => ({
    title: headline,
    body: `Lifted ${scope}. “${reason}”`,
  }),
};

const PROTECTION_NAMES: Record<string, string> = {
  usageAccess: "usage access",
  accessibility: "the garden keeper",
  deviceAdmin: "uninstall protection",
};

/** "usage access and the garden keeper" */
export function protectionNames(keys: string[]): string {
  const names = keys.map((k) => PROTECTION_NAMES[k]).filter((n): n is string => !!n);
  if (names.length === 0) return "a protection";
  if (names.length === 1) return names[0];
  return `${names.slice(0, -1).join(", ")} and ${names[names.length - 1]}`;
}

export function emergencyHeadline(owner: Person, offline: boolean, overAllowance: boolean): string {
  const extra = [offline ? "while offline" : null, overAllowance ? "beyond their monthly allowance" : null]
    .filter(Boolean).join(", ");
  return `🚨 ${owner.displayName} used an emergency unlock${extra ? ` (${extra})` : ""}`;
}

export function emergencyScope(appName: string | null, minutes: number): string {
  return appName ? `${appName} for ${minutes} minutes` : `all limits for ${minutes} minutes`;
}
