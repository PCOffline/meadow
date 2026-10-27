// The token is the last path segment: /i/<token>
const token = location.pathname.split("/").filter(Boolean).pop() || "";
const open = document.getElementById("open");
if (/^[A-Za-z0-9]{16}$/.test(token)) {
  open.href = `meadow://invite/${token}`;
} else {
  open.hidden = true;
  document.getElementById("expired").hidden = false;
}
