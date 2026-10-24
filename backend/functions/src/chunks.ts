/** How many documents a scheduled job works on at once. */
export const CHUNK_SIZE = 50;

/**
 * Runs [fn] on every item, [size] at a time: each chunk runs in parallel and the next starts once
 * it has finished. Results keep the order of [items].
 */
export async function inChunks<T, R>(items: readonly T[], fn: (item: T) => Promise<R>, size = CHUNK_SIZE): Promise<R[]> {
  if (!Number.isInteger(size) || size < 1) throw new RangeError(`Chunk size must be a positive integer, not ${size}`);
  const results: R[] = [];
  for (let i = 0; i < items.length; i += size) {
    results.push(...(await Promise.all(items.slice(i, i + size).map(fn))));
  }
  return results;
}
