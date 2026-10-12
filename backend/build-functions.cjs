// Compiles the Cloud Functions before every functions deploy (see firebase.json).
// Runs the TypeScript compiler directly with Node instead of through `npm run build`, so the
// deploy doesn't depend on npm's script shell or the shims in node_modules/.bin, which break on
// some setups. CommonJS for the same reason as predeploy.cjs.
const path = require("node:path");

const functionsDir = path.join(__dirname, "functions");
let tsc;
try {
  tsc = require.resolve("typescript/lib/tsc.js", { paths: [functionsDir] });
} catch {
  console.error("Missing packages: run `npm --prefix functions ci` in backend/ first.");
  process.exit(1);
}
process.argv = [process.argv[0], tsc, "--project", functionsDir];
require(tsc);
