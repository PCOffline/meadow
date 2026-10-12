// Renders the legal documents in /legal into static pages for Firebase Hosting.
// Runs before every hosting deploy (see firebase.json); the output is not committed.
import { readFileSync, writeFileSync } from "node:fs";
import { marked } from "marked";

const pages = [
  ["privacy_notice.md", "privacy.html"],
  ["terms_of_use.md", "terms.html"],
  ["licenses.md", "licenses.html"],
];

const template = (title, body) => `<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>${title} · Meadow</title>
  <link rel="stylesheet" href="/style.css">
</head>
<body>
  <main class="card doc">
${body}
    <p class="links"><a href="/privacy">Privacy notice</a> · <a href="/terms">Terms of use</a> · <a href="/licenses">Licences</a></p>
  </main>
</body>
</html>
`;

export function render(markdown) {
  const html = marked.parse(markdown, { gfm: true });
  const title = markdown.match(/^#\s+(.+)$/m)?.[1]?.trim() ?? "Meadow";
  return template(title, html);
}

if (import.meta.url === `file://${process.argv[1]}`) {
  for (const [source, target] of pages) {
    const markdown = readFileSync(new URL(`../legal/${source}`, import.meta.url), "utf8");
    writeFileSync(new URL(`hosting/${target}`, import.meta.url), render(markdown));
    console.log(`hosting/${target}`);
  }
}
