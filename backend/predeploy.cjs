// Builds the generated hosting files before every hosting deploy (see firebase.json).
// CommonJS on purpose: the standalone Firebase CLI runs this with its own bundled Node, which
// can't start an ES module directly but can import one.
Promise.all([import("./render-legal.mjs"), import("./render-app-links.mjs")])
  .then(([legal, appLinks]) => {
    legal.writeLegalPages();
    appLinks.writeAssetLinks();
  })
  .catch((error) => {
    if (error.code === "ERR_MODULE_NOT_FOUND") {
      console.error("Missing packages: run `npm ci` in backend/ first.");
    }
    console.error(error);
    process.exit(1);
  });
