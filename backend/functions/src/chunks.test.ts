import { test } from "node:test";
import assert from "node:assert/strict";
import { CHUNK_SIZE, inChunks } from "./chunks";

test("runs every item, in order, never more than a chunk at once", async () => {
  let running = 0;
  let most = 0;
  const items = Array.from({ length: 7 }, (_, i) => i);
  const results = await inChunks(items, async (i) => {
    most = Math.max(most, ++running);
    await new Promise((resolve) => setTimeout(resolve, 7 - i));
    running--;
    return i * 2;
  }, 3);
  assert.deepEqual(results, [0, 2, 4, 6, 8, 10, 12]);
  assert.equal(most, 3);
});

test("uses the default chunk size and copes with nothing to do", async () => {
  let most = 0;
  let running = 0;
  await inChunks(Array.from({ length: CHUNK_SIZE + 1 }, (_, i) => i), async () => {
    most = Math.max(most, ++running);
    await Promise.resolve();
    running--;
  });
  assert.equal(most, CHUNK_SIZE);
  assert.deepEqual(await inChunks([], async () => 1), []);
});

test("rejects a chunk size that would never finish", async () => {
  await assert.rejects(inChunks([1], async (i) => i, 0), RangeError);
  await assert.rejects(inChunks([1], async (i) => i, 1.5), RangeError);
});
