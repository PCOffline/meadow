// The browser opened the sign-in link instead of Meadow: hand the whole link back to the app.
// Only to Meadow itself: another app could claim meadow:// links too, and the link signs in.
const MEADOW = "dev.pcoffline.meadow"; // as in app-links.json
const query = new URLSearchParams(location.search);
const isSignIn = query.get("mode") === "signIn" && !!query.get("oobCode");
const open = document.getElementById("open");
if (isSignIn) {
  open.href = `intent://signin?link=${encodeURIComponent(location.href)}#Intent;scheme=meadow;package=${MEADOW};end`;
  open.hidden = false;
} else {
  document.getElementById("broken").hidden = false;
}
