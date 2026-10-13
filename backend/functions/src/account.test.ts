import { test } from "node:test";
import assert from "node:assert/strict";
import { EXPORT_TTL_MS, exportPath, isStaleExport } from "./account";

test("uncollected exports are cleared after a day, and nothing else lingers", () => {
  const now = Date.now();
  assert.equal(isStaleExport(exportPath("u1", now - 60_000), now), false);
  assert.equal(isStaleExport(exportPath("u1", now - EXPORT_TTL_MS + 1), now), false);
  assert.equal(isStaleExport(exportPath("u1", now - EXPORT_TTL_MS - 1), now), true);
  // Anything else in the exports folder isn't Meadow's and is cleared too.
  assert.equal(isStaleExport("exports/u1/notes.txt", now), true);
  assert.equal(isStaleExport("exports/u1/meadow-.json", now), true);
});
