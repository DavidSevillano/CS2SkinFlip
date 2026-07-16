// The price pipeline's two coupled timings. They live together because they
// cannot be reasoned about separately: the write cadence determines how far back
// a reader must look to be guaranteed a reference point. They were previously
// spread across three files and silently drifted apart — 24h in the bulk job,
// 12h in the read path — so the two disagreed about the same skin.

// How often the bulk job fetches every marketplace and writes a PriceHistory
// point per skin. Also the effective resolution of the raw retention band.
export const REFRESH_INTERVAL_MS = 6 * 60 * 60 * 1000

// How far below the 24h mark to look for a reference price.
//
// The lower bound is not optional. An unbounded `timestamp <= dayAgo` matches
// the entire retained history, and because `distinct` can't be pushed down to
// Postgres under a timestamp `orderBy`, Prisma materialises every matching row
// in-process — enough to OOM a 512MB instance.
//
// Must stay >= REFRESH_INTERVAL_MS or the window can fall between two writes and
// every skin silently reports a null 24h change. At 4x the cadence it survives
// three consecutive missed runs while still matching only a handful of rows per
// skin. `priceHistory.test.ts` pins that relationship.
export const CHANGE_REFERENCE_WINDOW_MS = 24 * 60 * 60 * 1000
