// Writes hosting/.well-known/assetlinks.json from app-links.json, so Android opens invite and
// sign-in links straight in Meadow. Runs before every hosting deploy (see predeploy.cjs).
// Add each signing key's SHA-256 (./gradlew signingReport) to "sha256" in app-links.json.
import { mkdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";

export function assetLinks({ packageName, sha256 }) {
  const fingerprints = (sha256 ?? []).map((f) => f.trim().toUpperCase());
  for (const f of fingerprints) {
    if (!/^([0-9A-F]{2}:){31}[0-9A-F]{2}$/.test(f)) throw new Error(`Not a SHA-256 fingerprint: ${f}`);
  }
  if (fingerprints.length === 0) return null;
  return [{
    relation: ["delegate_permission/common.handle_all_urls"],
    target: { namespace: "android_app", package_name: packageName, sha256_cert_fingerprints: fingerprints },
  }];
}

export function writeAssetLinks() {
  const config = JSON.parse(readFileSync(new URL("app-links.json", import.meta.url), "utf8"));
  const target = new URL("hosting/.well-known/assetlinks.json", import.meta.url);
  const links = assetLinks(config);
  if (links) {
    mkdirSync(new URL("hosting/.well-known/", import.meta.url), { recursive: true });
    writeFileSync(target, JSON.stringify(links, null, 2) + "\n");
    console.log(`hosting/.well-known/assetlinks.json (${links[0].target.sha256_cert_fingerprints.length} key(s))`);
  } else {
    rmSync(target, { force: true });
    console.warn("No fingerprints in app-links.json: links will open in the browser, not the app.");
  }
}
