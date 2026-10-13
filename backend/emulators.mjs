// Starts the Firebase emulators for development: builds the functions, keeps everything between
// runs in .emulator-data/, and adds the demo people (emulator-seed.mjs) on a fresh start.
//   npm run emulators              # carry on where you left off
//   npm run emulators -- --fresh   # start empty, with only the demo people
import { spawn, spawnSync } from "node:child_process";
import { existsSync, readFileSync, rmSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { isSeeded, PASSWORD, PROJECT, seed } from "./emulator-seed.mjs";

const here = fileURLToPath(new URL(".", import.meta.url));
const DATA = ".emulator-data";
const region = readFileSync(new URL("functions/.env", import.meta.url), "utf8").match(/^MEADOW_REGION=(.+)$/m)?.[1].trim();

if (process.argv.includes("--fresh")) rmSync(new URL(DATA, import.meta.url), { recursive: true, force: true });
if (spawnSync(process.execPath, ["build-functions.cjs"], { cwd: here, stdio: "inherit" }).status !== 0) process.exit(1);

const args = ["firebase", "emulators:start", "--only", "auth,firestore,functions,storage", "--project", PROJECT, "--export-on-exit", DATA];
if (existsSync(new URL(`${DATA}/firebase-export-metadata.json`, import.meta.url))) args.push("--import", DATA);
const emulators = spawn("npx", args, { cwd: here, stdio: "inherit", shell: process.platform === "win32" });
// Ctrl+C reaches the emulators too; wait for them to save before leaving.
process.on("SIGINT", () => {});
emulators.on("exit", (code) => process.exit(code ?? 0));

async function ready() {
  for (let i = 0; i < 90; i++) {
    try {
      const res = await fetch(`http://127.0.0.1:5001/${PROJECT}/${region}/createProfile`, {
        method: "POST", headers: { "Content-Type": "application/json" }, body: '{"data":{}}',
      });
      if (res.status !== 404) return true;
    } catch {
      // Not listening yet.
    }
    await new Promise((r) => setTimeout(r, 2000));
  }
  return false;
}

if (await ready()) {
  if (await isSeeded()) {
    console.log("\n🌼 Demo people from an earlier run are still here.");
  } else {
    const people = await seed();
    console.log(`\n🌼 Demo people (password "${PASSWORD}"): ${Object.values(people).map((p) => p.email).join(", ")}`);
  }
  console.log("   Point a debug build here: ./gradlew installDebug -Pmeadow.emulatorHost=10.0.2.2\n");
}
