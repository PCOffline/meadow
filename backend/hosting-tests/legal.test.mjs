// The hosted legal pages: every document renders to a complete, accessible page.
import { test } from "node:test";
import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { existsSync, readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { render } from "../render-legal.mjs";

const docs = ["privacy_notice.md", "terms_of_use.md", "licenses.md"];
const read = (name) => readFileSync(new URL(`../../legal/${name}`, import.meta.url), "utf8");

for (const name of docs) {
  test(`${name} renders to a complete page`, () => {
    const html = render(read(name));
    assert.match(html, /^<!doctype html>/);
    assert.match(html, /<html lang="en">/, "pages declare their language");
    assert.match(html, /<meta name="viewport"/, "pages work on phones");
    assert.equal((html.match(/<h1>/g) ?? []).length, 1, "exactly one main heading");
    assert.match(html, /<title>[^<]+ · Meadow<\/title>/);
    assert.doesNotMatch(html, /\*\*|^#+ /m, "no Markdown left unrendered");
  });
}

test("headings never skip a level", () => {
  for (const name of docs) {
    const levels = [...render(read(name)).matchAll(/<h([1-6])>/g)].map((m) => Number(m[1]));
    levels.forEach((level, i) => {
      if (i > 0) assert.ok(level <= levels[i - 1] + 1, `${name}: h${levels[i - 1]} is followed by h${level}`);
    });
  }
});

test("tables get header cells, and the title comes from the first heading", () => {
  const html = render("# Hello\n\n| What | Who |\n|---|---|\n| Name | Bees |\n");
  assert.match(html, /<th>What<\/th>/);
  assert.match(html, /<td>Bees<\/td>/);
  assert.match(html, /<title>Hello · Meadow<\/title>/);
});

test("the privacy notice says who to contact and names its processors", () => {
  const html = render(read("privacy_notice.md"));
  assert.match(html, /github\.com\/PCOffline\/meadow/);
  assert.match(html, /Firebase|Google/);
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
