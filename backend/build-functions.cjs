// Compiles the Cloud Functions before every functions deploy (see firebase.json).
// Runs the TypeScript compiler directly with Node instead of through `npm run build`, so the
// deploy doesn't depend on npm's script shell or the shims in node_modules/.bin, which break on
// some setups. CommonJS for the same reason as predeploy.cjs.
const path = require("node:path");
const { spawnSync } = require("node:child_process");

const functionsDir = path.join(__dirname, "functions");
let typescriptDir;
try {
  // TypeScript 7 only exports its package root, so find the compiler's launcher from there.
  typescriptDir = path.dirname(require.resolve("typescript/package.json", { paths: [functionsDir] }));
} catch {
  console.error("Missing packages: run `npm --prefix functions ci` in backend/ first.");
  process.exit(1);
}
const result = spawnSync(process.execPath, [path.join(typescriptDir, "bin", "tsc"), "--project", functionsDir], {
  stdio: "inherit",
});
if (result.error) throw result.error;
process.exit(result.status ?? 1);
