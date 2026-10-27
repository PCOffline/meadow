// The pages links land on when they open in the browser instead of the app.
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import vm from "node:vm";

const page = (name) => readFileSync(new URL(`../hosting/${name}`, import.meta.url), "utf8");

/** Runs [name]'s script as if opened at [href], returning its elements by id. */
function open(name, href) {
  const html = page(name);
  const ids = [...html.matchAll(/id="([^"]+)"/g)].map((m) => m[1]);
  const elements = Object.fromEntries(ids.map((id) => [id, { id, href: "#", hidden: /hidden/.test(html.match(new RegExp(`id="${id}"[^>]*>`))[0]) }]));
  const url = new URL(href);
  const script = html.match(/<script>([\s\S]*?)<\/script>/)[1];
  vm.runInNewContext(script, {
    location: { href: url.href, search: url.search, pathname: url.pathname },
    document: { getElementById: (id) => elements[id] },
    URLSearchParams,
  });
  return elements;
}

test("a sign-in link that opened in the browser is handed back to Meadow whole", () => {
  const link = "https://x.web.app/signed-in?apiKey=k&oobCode=c0de&mode=signIn&lang=en";
  const { open: button, broken } = open("signed-in.html", link);
  assert.equal(button.hidden, false);
  assert.equal(broken.hidden, true);
  assert.equal(button.href, `meadow://signin?link=${encodeURIComponent(link)}`);
});

test("without a sign-in code, the page says the link is broken and offers nothing", () => {
  const { open: button, broken } = open("signed-in.html", "https://x.web.app/signed-in");
  assert.equal(button.hidden, true);
  assert.equal(broken.hidden, false);
});

test("an invite link opens in Meadow, and a mangled one doesn't", () => {
  assert.equal(open("invite.html", "https://x.web.app/i/AbCdEfGh12345678").open.href, "meadow://invite/AbCdEfGh12345678");
  const mangled = open("invite.html", "https://x.web.app/i/nope");
  assert.equal(mangled.open.hidden, true);
  assert.equal(mangled.expired.hidden, false);
});

test("the invite page points to where Meadow takes a pasted link", () => {
  assert.match(page("invite.html"), /Circle → Got a friend code\?/);
});
