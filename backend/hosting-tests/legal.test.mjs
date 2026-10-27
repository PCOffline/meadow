// The hosted legal pages: every document renders to a complete, accessible page.
import { test } from "node:test";
import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { existsSync, readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { JSDOM } from "jsdom";
import { render } from "../render-legal.mjs";

const docs = ["privacy_notice.md", "terms_of_use.md", "licenses.md"];
const read = (name) => readFileSync(new URL(`../../legal/${name}`, import.meta.url), "utf8");
const parse = (html) => new JSDOM(html).window.document;

for (const name of docs) {
  test(`${name} renders to a complete page`, () => {
    const page = parse(render(read(name)));
    assert.equal(page.doctype?.name, "html");
    assert.equal(page.documentElement.lang, "en", "pages declare their language");
    assert.ok(page.querySelector('meta[name="viewport"]'), "pages work on phones");
    assert.equal(page.querySelectorAll("h1").length, 1, "exactly one main heading");
    assert.match(page.title, /^.+ · Meadow$/);
    assert.doesNotMatch(page.body.textContent, /\*\*|^#+ /m, "no Markdown left unrendered");
  });
}

test("headings never skip a level", () => {
  for (const name of docs) {
    const levels = [...parse(render(read(name))).querySelectorAll("h1, h2, h3, h4, h5, h6")].map((h) => Number(h.tagName[1]));
    levels.forEach((level, i) => {
      if (i > 0) assert.ok(level <= levels[i - 1] + 1, `${name}: h${levels[i - 1]} is followed by h${level}`);
    });
  }
});

test("tables get header cells, and the title comes from the first heading", () => {
  const page = parse(render("# Hello\n\n| What | Who |\n|---|---|\n| Name | Bees |\n"));
  assert.deepEqual([...page.querySelectorAll("th")].map((c) => c.textContent), ["What", "Who"]);
  assert.deepEqual([...page.querySelectorAll("td")].map((c) => c.textContent), ["Name", "Bees"]);
  assert.equal(page.title, "Hello · Meadow");
});

test("the privacy notice says who to contact and names its processors", () => {
  const page = parse(render(read("privacy_notice.md")));
  assert.match(page.body.textContent, /github\.com\/PCOffline\/meadow/);
  assert.match(page.body.textContent, /Firebase|Google/);
});

test("app links list every signing key, and refuse anything that isn't a fingerprint", async () => {
  const { assetLinks } = await import("../render-app-links.mjs");
  const key = "AB:CD:EF:01:23:45:67:89:AB:CD:EF:01:23:45:67:89:AB:CD:EF:01:23:45:67:89:AB:CD:EF:01:23:45:67:89";
  assert.equal(assetLinks({ packageName: "dev.pcoffline.meadow", sha256: [] }), null);
  const [entry] = assetLinks({ packageName: "dev.pcoffline.meadow", sha256: [key.toLowerCase()] });
  assert.deepEqual(entry.relation, ["delegate_permission/common.handle_all_urls"]);
  assert.equal(entry.target.package_name, "dev.pcoffline.meadow");
  assert.deepEqual(entry.target.sha256_cert_fingerprints, [key]);
  assert.throws(() => assetLinks({ packageName: "x", sha256: ["REPLACE_ME"] }), /fingerprint/);
});

test("the predeploy script writes every hosted page", () => {
  const backend = fileURLToPath(new URL("..", import.meta.url));
  execFileSync(process.execPath, ["predeploy.cjs"], { cwd: backend, stdio: "pipe" });
  for (const page of ["privacy.html", "terms.html", "licenses.html"]) {
    assert.ok(existsSync(new URL(`../hosting/${page}`, import.meta.url)), page);
  }
});
