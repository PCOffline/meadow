// The pages links land on when they open in the browser instead of the app.
import { test } from "node:test";
import assert from "node:assert/strict";
import { readdirSync, readFileSync } from "node:fs";
import { JSDOM } from "jsdom";

const hosting = new URL("../hosting/", import.meta.url);
const read = (path) => readFileSync(new URL(path.replace(/^\//, ""), hosting), "utf8");
const pages = readdirSync(hosting).filter((name) => name.endsWith(".html"));

/** [name] as a browser shows it when opened at [href], with its own scripts run. */
function open(name, href) {
  const dom = new JSDOM(read(name), { url: href, runScripts: "outside-only" });
  const { document } = dom.window;
  for (const script of document.querySelectorAll("script[src]")) dom.window.eval(read(script.getAttribute("src")));
  return document;
}

const shown = (document, id) => !document.getElementById(id).hidden;

const { packageName } = JSON.parse(readFileSync(new URL("../app-links.json", import.meta.url), "utf8"));
const handBack = (link) => `intent://signin?link=${encodeURIComponent(link)}#Intent;scheme=meadow;package=${packageName};end`;

test("a sign-in link that opened in the browser is handed back whole, to Meadow and no other app", () => {
  const link = "https://x.web.app/signed-in?apiKey=k&oobCode=c0de&mode=signIn&lang=en";
  const page = open("signed-in.html", link);
  assert.ok(shown(page, "open"));
  assert.ok(!shown(page, "broken"));
  assert.equal(page.getElementById("open").getAttribute("href"), handBack(link));
});

test("without a sign-in code, the page says the link is broken and offers nothing", () => {
  const page = open("signed-in.html", "https://x.web.app/signed-in");
  assert.ok(!shown(page, "open"));
  assert.ok(shown(page, "broken"));
});

test("whatever a sign-in link carries stays inside Meadow's link, as text", () => {
  const evil = `https://x.web.app/signed-in?mode=signIn&oobCode=c&x="><img src=x onerror=alert(1)>&y=javascript:alert(1)#"><script>alert(1)</script>`;
  const page = open("signed-in.html", evil);
  const href = page.getElementById("open").getAttribute("href");
  assert.equal(href, handBack(page.location.href));
  assert.doesNotMatch(href, /[<>"'\s]/);
  assert.equal(page.querySelectorAll("img").length, 0);
  assert.equal(page.querySelectorAll("script:not([src])").length, 0);
});

test("an invite link opens in Meadow, and a mangled one doesn't", () => {
  assert.equal(open("invite.html", "https://x.web.app/i/AbCdEfGh12345678").getElementById("open").getAttribute("href"), "meadow://invite/AbCdEfGh12345678");
  for (const bad of ["https://x.web.app/i/nope", "https://x.web.app/i/javascript:alert(1)", `https://x.web.app/i/"><img src=x onerror=alert(1)>`]) {
    const page = open("invite.html", bad);
    assert.ok(!shown(page, "open"), bad);
    assert.ok(shown(page, "expired"), bad);
    assert.equal(page.getElementById("open").getAttribute("href"), "#", bad);
    assert.equal(page.querySelectorAll("img").length, 0, bad);
  }
});

test("the invite page points to where Meadow takes a pasted link", () => {
  assert.match(open("invite.html", "https://x.web.app/i/AbCdEfGh12345678").body.textContent, /People → Invite a Bumblebee/);
});

test("no page runs inline script or handlers, so the content security policy can forbid them", () => {
  for (const name of pages) {
    const { document } = new JSDOM(read(name)).window;
    assert.equal(document.querySelectorAll("script:not([src])").length, 0, `${name}: inline script`);
    const handlers = [...document.querySelectorAll("*")].flatMap((el) => el.getAttributeNames().filter((a) => a.startsWith("on")));
    assert.deepEqual(handlers, [], `${name}: inline event handlers`);
    assert.equal(document.querySelectorAll("[style], style").length, 0, `${name}: inline styles`);
    for (const script of document.querySelectorAll("script[src]")) {
      assert.ok(script.getAttribute("src").startsWith("/"), `${name}: scripts load from the site root, so /i/… rewrites can't swallow them`);
    }
  }
});

test("every hosted page is served with a strict content security policy", () => {
  const { hosting: config } = JSON.parse(readFileSync(new URL("../firebase.json", import.meta.url), "utf8"));
  const all = config.headers.find((h) => h.source === "**").headers;
  const header = (key) => all.find((h) => h.key === key)?.value;
  const csp = Object.fromEntries(header("Content-Security-Policy").split(";").map((d) => d.trim().split(/\s+/)).map(([k, ...v]) => [k, v]));
  assert.deepEqual(csp["default-src"], ["'none'"]);
  assert.deepEqual(csp["script-src"], ["'self'"]);
  assert.deepEqual(csp["frame-ancestors"], ["'none'"]);
  assert.deepEqual(csp["base-uri"], ["'none'"]);
  assert.equal(header("X-Content-Type-Options"), "nosniff");
  // The sign-in page's address holds a one-time code; it never goes out as a referrer.
  assert.equal(header("Referrer-Policy"), "no-referrer");
});
