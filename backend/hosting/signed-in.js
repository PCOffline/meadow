// The browser opened the sign-in link instead of Meadow: hand the whole link back to the app.
const query = new URLSearchParams(location.search);
const isSignIn = query.get("mode") === "signIn" && !!query.get("oobCode");
const open = document.getElementById("open");
if (isSignIn) {
  open.href = `meadow://signin?link=${encodeURIComponent(location.href)}`;
  open.hidden = false;
} else {
  document.getElementById("broken").hidden = false;
}
