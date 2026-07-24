# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Monorepo structure

```
CS2SkinFlip/
├── android/    ← Native Android app (Kotlin + Jetpack Compose + Hilt + MVVM)
├── backend/    ← REST API (Fastify + TypeScript + Prisma + PostgreSQL + Upstash Redis)
└── web/        ← Static SEO site generator (Kotlin Multiplatform), deployed to Cloudflare Pages — see web/README.md
```

`docs/superpowers/plans/` and `docs/superpowers/specs/` contain design docs for both shipped and still-unimplemented features — a plan/spec file existing does **not** mean the feature is in the code. Verify against actual source before treating a plan doc as current behavior.

- **"premium bundle priority alerts"** (per-alert-interval refresh for premium users) is speculative only — `isPremium` gates the alert limit in `routes/alerts.ts` and nothing else.
- **"price history daily aggregation"** is **shipped**, not speculative: `jobs/cleanupPriceHistory.ts` does the in-place downsample and `GET /skins/:id/price-history?range=90d` serves it. The spec's retention numbers are stale (it describes 120d / 2 points per day); the code is the source of truth.

---

# Store assets

`store-assets/screenshots/` genera los 42 PNG de la ficha de Play (6 screenshots
+ 1 feature graphic × 6 locales) desde HTML con Puppeteer. `npm run all`.

La UI simulada dentro del telefono lee las traducciones reales de
`android/app/src/main/res/values-*/strings.xml`, asi que un cambio de traduccion
en la app se hereda en los screenshots. El copy de marketing (captions, feature
graphic) vive en `src/copy.mjs` y hay que mantenerlo en los 6 idiomas — hay un
test de paridad de claves que lo comprueba. Ver `store-assets/screenshots/README.md`.

`store-assets/listing/{en,es,pt-BR,ru,tr,pl}.txt` tiene el titulo y las
descripciones corta/larga de la ficha. Son la fuente de referencia, pero **Play
Console no los lee**: hay que copiarlos a mano por idioma. Los limites de Play
(30 / 80 / 4000 caracteres) no los valida nada automaticamente.

`out/` y `src/html/` son artefactos generados y estan gitignorados; `src/icon.png`
es fuente y lo consume el feature graphic.

---

# Backend

## Commands

```bash
# Development (hot reload)
npm run dev

# Type-check only (no emit)
npx tsc --noEmit

# Database
npm run db:push        # push schema changes without migration files
npm run db:migrate     # create a migration file + apply
npm run db:studio      # open Prisma Studio in browser
npm run db:reset       # reset DB and re-seed (destructive)

# Production build
npm run build && npm start
```

## Environment

Copy `.env` and fill in:

| Variable | Description |
|---|---|
| `DATABASE_URL` | Neon (PostgreSQL) connection string — must be the **pooled** endpoint (host contains `-pooler`) |
| `DIRECT_DATABASE_URL` | Same Neon host **without** `-pooler`. Used only by `prisma migrate` / `db push` / `db pull`, which need session state the pooler drops. Not read at runtime, so Render doesn't set it |
| `UPSTASH_REDIS_REST_URL` / `_TOKEN` | Upstash Redis (serverless) |
| `JWT_SECRET` | Min 32 chars |
| `FRONTEND_URL` | Used for CORS allowlist |
| `JOBS_SECRET` | Min 32 chars. Protects `POST /jobs/refresh-prices`; must match the `JOBS_SECRET` GitHub repo secret. **Without it the `/jobs` routes don't register and no scheduled refresh can run** — `/health` goes `stale` within 8h and the uptime monitor alerts |
| `TRUST_PROXY` | Reverse-proxy hop count in front of the app. Required in production for per-IP rate limiting to work — see [Rate limiting](#rate-limiting). Defaults to `false` (local dev) |
| `LOG_LEVEL` | Pino level — defaults to `info` in every environment. Set to `warn`/`error` to quieten, `debug` to troubleshoot |
| `FCM_SERVICE_ACCOUNT_PATH` | Optional — path to Firebase service account JSON for push notifications |
| `GOOGLE_PLAY_SERVICE_ACCOUNT_PATH` | Optional — path to a Google Play service account JSON, used to verify one-time premium purchases |
| `GOOGLE_PLAY_PACKAGE_NAME` | Defaults to `com.burixer85.cs2skinflip` |
| `PREMIUM_PRODUCT_ID` | Defaults to `premium_unlimited_alerts` — must match the Play Console in-app product ID |

The price aggregator uses only public bulk endpoints — no marketplace API keys are required.

### Legal docs (privacy policy / terms of service)

There are **two separate copies** — keep them in sync when either changes:
1. **Canonical, linked from the Android app** (`SettingsScreen.kt`, `PRIVACY_URL`/`TERMS_URL`): `https://davidsevillano.github.io/cs2skinflip-legal/{privacy,terms}.html`, a separate GitHub Pages repo not in this monorepo.
2. **Generated copy on the SEO site**: `web/generator/.../html/PrivacyPage.kt`, served at `/privacy` on the Cloudflare-hosted site. Not linked from the app.

Both must mention the one-time premium purchase (`purchaseToken` sent to Google Play, no payment data stored), AdMob, Firebase Analytics, and Steam OAuth. There is no Terms of Service equivalent generated by `web/generator` — only the privacy page.

The Firebase service account file (`firebase-service-account.json`) is gitignored. Download from Firebase console → Project Settings → Service Accounts.

### Premium unlock (unlimited alerts)

Free accounts get 1 alert (`FREE_ALERT_LIMIT` in `src/routes/alerts.ts`); a one-time Google Play purchase unlocks unlimited alerts by setting `User.isPremium = true`.

- Android launches Play Billing for the non-consumable product `premium_unlimited_alerts`, tagging the purchase with the user's ID via `setObfuscatedAccountId` so a token can't be replayed against a different account.
- `POST /billing/verify-purchase` (`src/routes/billing.ts`) takes the resulting `purchaseToken`, verifies + acknowledges it against the Google Play Developer API (`src/services/googlePlay.ts`, lazily initialised like `fcm.ts`), checks the obfuscated account ID matches the caller, then sets `isPremium`.
- Android also re-queries Play Billing for unconfirmed purchases on login/app start (`BillingRepository.syncPendingPurchases()`) as a safety net for purchases that succeeded but never reached the backend (e.g. app killed mid-flow).

## Architecture

Fastify + Prisma + TypeScript. Vitest (`npm test`), colocated `*.test.ts` next to the module under test; prisma/redis/axios are mocked via `vi.mock`, nothing hits the network or a real DB.

### Startup sequence (`app.ts`)

```
buildServer() → listen → populateSkins() → populatePrices() only if SkinPrice is empty
```

`populateSkins` skips if the DB already has ≥ 12 000 rows. `populatePrices` runs on startup **only in a real bootstrap** (empty `SkinPrice`: new DB, `db:reset`) — a routine restart refreshes nothing.

**The periodic refresh is not scheduled in this process.** `.github/workflows/refresh-prices.yml` cron-triggers `POST /jobs/refresh-prices` every 6h (00/06/12/18 UTC), which runs populatePrices → alert check → history cleanup behind a Redis lock (`src/jobs/runner.ts`). A `setInterval` here would restart its clock on every deploy and double up if the service ever ran 2 instances.

The 6h cadence therefore lives in the workflow cron, mirrored by `REFRESH_INTERVAL_MS` in `src/config/priceHistory.ts`. Change one and you must change the other: `CHANGE_REFERENCE_WINDOW_MS` is defined against it, and `priceHistory.test.ts` pins the relationship.

### Route structure (`src/routes/`)

| File | Prefix | Notes |
|---|---|---|
| `skins.ts` | `/skins` | Public — search, detail, price history, top-movers |
| `auth.ts` | `/auth` | Steam OAuth only (`GET /auth/steam` → `/auth/steam/callback`) → JWT; `PUT /auth/me/fcm-token` to save FCM token |
| `watchlist.ts` | `/watchlist` | Auth required |
| `alerts.ts` | `/alerts` | Auth required |
| `portfolio.ts` | `/portfolio` | Auth required |
| `prices.ts` | `/prices` | On-demand single-skin and batch price refresh |
| `jobs.ts` | `/jobs` | Cron trigger — `X-Jobs-Secret` header; `POST /jobs/refresh-prices` (202 + runId), `GET /jobs/refresh-prices/status`. Not registered at all without `JOBS_SECRET` |

### Price pipeline

Three marketplaces are fetched in parallel every 6h by the cron-triggered job (`populatePrices`), plus once on startup if `SkinPrice` is empty.
**All endpoints are bulk and public — no API keys, no rate-limit concerns** (4 calls/day per marketplace):

1. **Skinport** (`fetchSkinportPrices`) — `GET https://api.skinport.com/v1/items?app_id=730&currency=USD&tradable=1`, ~24.8k items. **`tradable` must stay `1`.** The flag selects between two different result sets rather than switching a filter off: measured 2026-07-24, `tradable=0` returns 10.9k priced items and `tradable=1` returns 24.8k. `0` is dominated by trade-locked listings, which are cheaper than the same skin you could actually flip, so it biased Skinport low and handed it `lowestPrice` more often than it deserved.
2. **CS:GO Market** (`fetchCsgoMarketPrices`) — `GET https://market.csgo.com/api/v2/prices/USD.json`, ~25k items.
3. **Waxpeer** (`fetchWaxpeerPrices`) — `GET https://api.waxpeer.com/v1/prices?game=csgo`, ~20k items. `min` field is USD × 1000 (divide by 1000).

**CSDeals and DMarket dropped** (2026-07): CSDeals' bulk endpoint only lists ~2.6k actively-stocked items (vs ~20-25k for the others), so it almost never matched anything outside the most common skins. DMarket's public bulk aggregator (`price-aggregator/v1/aggregated-prices`) was retired outright; its replacement requires signed API-key auth and per-title lookups, incompatible with the no-keys bulk design here.

`calcLowestPrice()` is **not** a plain MIN — a plain MIN trusts whichever marketplace is most wrong. When all three quote a skin, any quote further than `MAX_QUOTE_DEVIATION_FROM_MEDIAN` (4x) from the median of the three is discarded before the MIN is taken. With fewer than three quotes there is no majority to appeal to, so those rows keep the plain MIN and are instead refused by top movers. `SkinPrice.lowestPrice` stores the result and is the primary price shown in the Android app; the individual marketplace columns always record what each one actually said, rejected or not.

Thresholds and the production measurements behind them live in `src/config/priceQuality.ts`. Re-derive them with `scripts/measure-price-quality.ts` rather than adjusting by feel.

**No live calls anywhere.** The DB is the single source of truth. `GET /skins/:id`, `GET /prices/batch`, and the search/top-movers endpoints all read straight from `SkinPrice` — search responses already contain the marketplace prices and `lowestPrice` correct on first render. The Android `SkinRepository` is correspondingly simple: no `livePriceCache`, no batch refresh after list loads.

**Top movers** (`PriceService.getTopMovers()`): pure DB read, ordered by the indexed `priceChange24h` column (kept fresh by the bulk job). `priceChange24h` is then refined per skin from the `PriceHistory` table for accuracy between bulk runs.

### Alert notifications (FCM)

`src/services/fcm.ts` lazily initialises the Firebase Admin SDK from `FCM_SERVICE_ACCOUNT_PATH`. `sendAlertNotification()` sends a data-only FCM message (no `notification` key) so the Android app builds the notification in-process and can attach the `skinId` for tap navigation.

The alert-check job (`src/services/alerts.ts`) fetches the user's `fcmToken` from the DB after triggering an alert and calls `sendAlertNotification` if a token exists.

### Alert updates (`PUT /alerts/:id`)

Supports partial updates: `isActive`, `targetPrice`, and `type`. When `targetPrice` or `type` changes, the alert is automatically re-armed (`isTriggered = false`, `isActive = true`).

### Skin catalog (`populateSkins.ts`)

Source: `ByMykel/CSGO-API` GitHub JSON — **the catalog only, no prices**. It used to also fetch Steam prices from `prices.csgotrader.app` and seed `SkinPrice` with them; removed 2026-07-24 for two independent reasons. The endpoint had stopped serving JSON (it answers 200 with the site's HTML, and the "N prices loaded" log was counting the character indices of that string, so it reported ~36 700 prices every run). And even working, seeding `SkinPrice` here broke the bootstrap: `app.ts` runs `populatePrices` only when `SkinPrice` is empty, so a seeded row made a brand-new database skip the marketplace fetch and serve Steam-derived prices until the first cron run hours later.

Each skin × wear combination = one DB row. StatTrak variants:
- Regular: `StatTrak™ {name} ({wear})`
- Knives (`★`-prefix): `★ StatTrak™ {nameWithoutStar} ({wear})`

Skin IDs are slugs derived from `marketHashName` via `slugify()`.

### Caching (Redis / Upstash)

| Key | TTL |
|---|---|
| `steam:player:{steamId}` | 1 hour |
| `steam:inventory:{steamId}` | 10 min |
| `prices:{skinId}` | 5 min |
| `top-movers:{direction}:{limit}` | 15 min |
| `pipeline:prices:lastSuccessfulRun` | none (never expires) |

Top-movers keys are invalidated at the end of every bulk price run, on a catalog import, and on boot — all three via `invalidateTopMoversCache()` (`src/services/prices.ts`), which globs `top-movers:*`.

**Never write a `top-movers:` key literal outside `services/prices.ts`.** That module owns the format and the invalidation glob, derived from one prefix. They were independent until 2026-07: the key grew a `direction` segment while three call sites went on deleting `top-movers:20`, a key nobody wrote any more, so a bulk run left the lists cached and top-movers served superseded prices until the TTL expired. Nothing errored — which is why `prices.test.ts` fails the build if any other module spells the literal out.

`app.ts` wipes every `prices:*` key on boot — anything that must survive a deploy has to live outside that namespace, which is why the freshness marker below is `pipeline:`-prefixed and not `prices:`.

### Health / price-pipeline monitoring

`GET /health` returns `"prices": "fresh" | "stale" | "unknown"` alongside `status: "ok"`, plus `pricesLastRun` (ISO string or null) for debugging. Freshness comes from `pipeline:prices:lastSuccessfulRun` (epoch ms), written at the end of `populatePrices` **only when `updated > 0`** — every marketplace fetcher swallows its own errors and returns an empty map, so all three APIs breaking still completes the job normally; without that guard `/health` would stay green while serving frozen prices, which is the whole point of the marker. Stale threshold is 8h against a 6h job interval (one run plus slack). Redis down or a never-run pipeline both report `unknown`; the request still returns 200.

The uptime monitor is configured as a **keyword monitor on `fresh`** — only the healthy payload contains that substring, so `stale`/`unknown` both alert. Changing these strings breaks the monitor.

### Schema notes

`SkinPrice`: `skinportPrice`, `csgoMarketPrice`, `waxpeerPrice` (nullable floats, USD). `lowestPrice` = computed MIN, indexed for sorting. `priceChange24h` is also indexed for the home top-movers query.

`User`: Steam OAuth only — `steamId` is required and unique. Has `fcmToken String?` for FCM push notifications. (Email/password auth was removed in favor of Steam-only sign-in.)

### Filtering (`GET /skins`)

All filters are combined as `AND` conditions. Wear is matched via `marketHashName ILIKE '%(${wear}%)'`. StatTrak via `ILIKE '%StatTrak%'`. Search uses `regexp_replace(lower(...), '[^a-z0-9]', '', 'g')` to strip punctuation before matching.

`price_asc` / `price_desc` paginate with `skip`/`take` in the query. They used to fetch `limit * 2` candidates, re-sort them in memory and slice at the offset, so page 3 sliced `[100, 150)` out of a 100-element array and every page from there on returned empty — indistinguishable from "no more results" to a client. The re-sort was redundant anyway: the database had already ordered by the same column, and its `?? 0` null handling could never fire because rows with a null `lowestPrice` are excluded by the where clause whenever that sort is active.

`sort` defaults to `random`, and **any** filter present takes the random branch. It randomises by starting at a random offset within the already-counted `total`, not by loading every matching id to shuffle in memory — the latter made a broad search like `wear=Field-Tested` ship thousands of ids per request to return 50 of them, which was the single most expensive thing this API did against Neon's metered transfer. It also bought nothing, since `skip` was applied to a freshly shuffled array on every call and paging returned overlapping random slices rather than a stable sequence.

### Top movers quality filter (Rising/Falling)

`PriceService.getTopMovers(direction, limit)` (`src/services/prices.ts`) takes `direction: 'rising' | 'falling'`, over-fetches `limit * 5` candidates ordered by `priceChange24h`, then discards rows without a real 24h-ago reference price or with an absolute move under $0.25, before truncating to `limit`. Route: `GET /skins/top-movers?direction=`. Cached in Redis per direction as `top-movers:{direction}:{limit}` (15 min TTL) — see [Caching](#caching-redis--upstash) for how those keys get invalidated.

**Corroboration is the filter that matters, and it is the reason the home screen stopped showing nonsense.** A 24h change is only as trustworthy as the price it was computed from, and a price no second marketplace quotes cannot be checked against anything. Two rules, both from `config/priceQuality.ts`:

- **At least two marketplaces must quote the skin**, enforced in SQL as an `OR` over the three column pairs. It lives in the query rather than the in-process filter chain because it disqualifies most of what sorts to the top, and rejected rows cost egress on a metered connection if they come back first. There is deliberately no `MIN_TOP_MOVER_QUOTES` constant: Prisma has no counting operator over sibling columns, so the pairs encode the rule structurally and an exported `2` would be a number nobody reads.
- `MAX_TOP_MOVER_QUOTE_SPREAD` (3x) then drops rows whose quotes exist but disagree — the same skin at $373 and $2 762 has no meaningful price to rank.

Measured before the change (2026-07-24): single-quote skins were 3.7% of the catalog but supplied **15 of the top 20 risers**, led by a Battle-Scarred `M4A4 | Bullet Rain` at $61 938 and +50 608%. After: the top riser is +182% and every entry carries at least two agreeing quotes. `scripts/preview-top-movers.ts` renders both lists against production.

Note the reference prices in `PriceHistory` are historical, so changes computed against garbage written before this fix stay wrong until those points age past the 24–48h reference window — it self-heals within about two days of the first corrected run. It does **not** heal completely: the corroboration rules test a skin's *current* quotes, and a skin that had one quote yesterday and two today still gets a 24h change computed against an uncorroborated reference. `PriceHistory` stores no quote count, so nothing downstream can tell.

**Movement worth acting on.** `MIN_TOP_MOVER_PRICE` ($5) and `MIN_TOP_MOVER_ABS_MOVE` ($2) exist because percentage alone rewards cheap skins for nothing — a $1.20 item moving $0.57 is +90% and outranked a $336 knife moving $216. Six of the top twenty risers were sub-$5 skins whose whole move was under a dollar. `scripts/measure-mover-thresholds.ts` sweeps the grid: every combination tried still fills 20/20, because the catalog dwarfs the list, so this is purely a question of who the screen is for, not of starving it. At $5/$2 the cheapest entry goes from $1.20 to $7.78 and the median from $34.55 to $92.10, while a $7.78 R8 Revolver still qualifies; $10/$5 would push the cheapest to $29.79 and start excluding what a small-capital user can act on.

The Android card (`SkinCardCompact`) still shows only the percentage. Showing the dollar move alongside it needs a Play Store release to reach anyone, which is why the filtering was done backend-side first — it ships on the next deploy.

### Storage budget (Neon free tier: 0.5 GB)

`PriceHistory` is the only table that matters here — 332 MB of a 349 MB total when measured on 2026-07-24, against a 500 MB ceiling, and the database was only 20 days into a 90-day retention band.

**Most of that was empty space, not data.** The bulk job writes 4 points per skin per day and `cleanupPriceHistory` deletes 3 of them once they age past the raw band. Half a million deletes a day leave half-empty pages: `VACUUM` marks the space reusable but never compacts, and new rows append to the end instead. Measured: 112.7 bytes of real tuple sitting in a 262-byte heap footprint, with indexes at ~3x their packed size (the `[skinId, timestamp]` index alone was 109 MB, the cuid text primary key another 67 MB).

`scripts/compact-price-history.ts` runs `VACUUM (FULL, ANALYZE)`. First run took **2.3s and reclaimed 196 MB** (332 → 136 MB), no rows lost. It needs `DIRECT_DATABASE_URL` — `VACUUM` cannot run through a transaction-mode pooler — and takes an `ACCESS EXCLUSIVE` lock for the duration, so reads and writes to `PriceHistory` block while it runs.

The churn regenerates the slack, so **this has to be re-run periodically**. `cleanupPriceHistory` measures the table after each run and says so itself: it logs size and bytes/row every time, `warn`s once the table passes 1.6x its packed cost (`PACKED_BYTES_PER_ROW = 231`, measured — projecting with it while the table is dirty understates by 2.5x, which is the mistake that twice produced the wrong diagnosis), and `error`s once the database passes 80% of the ceiling, where compaction alone may no longer be enough. It does not compact by itself: `VACUUM FULL` locks the table and needs `DIRECT_DATABASE_URL`, which Render does not set. At the packed cost of ~231 bytes/row, steady state under the current 7d-raw + 90d-daily retention is ~347 MB for `PriceHistory` and ~364 MB overall, which fits — but only while the table stays compacted. Do not cut retention to solve a bloat problem: `?range=90d` is a shipped feature and the space is recoverable without touching it.

If it becomes a recurring chore rather than a one-off, the structural fixes in order of value are: a `BigInt autoincrement` primary key instead of the cuid text one (the PK index is pure overhead — nothing looks a row up by `id` except the downsample's own `WHERE id IN`), dropping `source` (it is `'bulk'` in all 589 563 rows), and finally daily partitioning, where dropping a partition reclaims space with no bloat at all.

`scripts/measure-neon-budget.ts` reports per-table sizes, the steady-state projection, and what a proposed change would cost.

### Transfer budget (Neon free tier: 5 GB/cycle)

Consumption sat at 4.1 GB with 11 days of the cycle left when checked on 2026-07-24, i.e. on course to breach. The bulk job's reads account for only ~180 MB/month of it, so the job is not the problem.

The identified culprit was `GET /skins` loading every matching id to shuffle in memory — see [Filtering](#filtering-get-skins). Whether that was all of it is unproven: `pg_stat_statements` was not installed, so there was no per-statement attribution.

**It is installed now** (`CREATE EXTENSION pg_stat_statements`, 2026-07-24). It records from that point forward only. `scripts/measure-egress-sources.ts` ranks statements by total rows returned, which is what transfer is actually made of — run it after a day of real traffic and the top entry names the next culprit.

Its first hour already found one: the 24h-change reference lookup in `getTopMovers` and `GET /skins` was returning **392 rows per call to produce ~100 values**, selecting every column — dragging a 25-char cuid `id` and a `source` that is `'bulk'` in all 589 563 rows across the wire for nothing. Both now use raw `DISTINCT ON` pushed to Postgres, the same fix `populatePrices` already had: Prisma's `distinct` cannot be pushed down under a timestamp `orderBy`, so it fetches every matching row and dedupes in-process. Measured 4.0x fewer rows (400 → 100 for 100 candidate skins) with identical reference prices. **If you add a third caller of this window, use the raw form** — the Prisma one looks equivalent and quietly costs 4x.

Do not read `pg_stat_user_tables.seq_tup_read` as egress. `Skin` and `SkinPrice` each show ~2 000 full scans and tens of millions of tuples read, but that is work inside Postgres: the search route's `regexp_replace` scan reads the whole 24k-row table to return 50 rows, and only the 50 cross the wire. High scan counts there are a compute signal (CU-hrs, currently 15 of 100), not a transfer one.

### Logging

Pino, configured in `server.ts` from `LOG_LEVEL` (default `info`, so job output like `[PricePopulate] Done — X skins updated` is visible in Render's logs). `pino-pretty` is only attached when `NODE_ENV === 'development'`; production emits JSON.

**Pino uses printf-style interpolation, so the error must go in the merge object, not as a trailing argument:**

```ts
log.error({ err }, '[PriceRefresh] Scheduled run failed')   // ✅ serializes type/message/stack
log.error('[PriceRefresh] Scheduled run failed:', err)      // ❌ err silently discarded — no %s/%o placeholder
```

### Rate limiting

`@fastify/rate-limit` is registered globally in `server.ts`, keyed by request IP: `RATE_LIMIT_MAX` (default 100) requests per `RATE_LIMIT_WINDOW` (default `'1 minute'`). `skins.ts` applies a tighter override on search: `RATE_LIMIT_SEARCH_MAX` (default 30) over the same window.

Keying on IP only works if `request.ip` is the caller. In production the app sits behind Cloudflare and Render's load balancer, so the socket address is theirs — **`TRUST_PROXY` must be set or every user shares one bucket** and a few concurrent users 429 everyone.

`TRUST_PROXY` is a hop count, deliberately **not** `true`: `true` trusts the whole `X-Forwarded-For` chain and resolves `request.ip` to its leftmost entry, which the client supplies and can forge — letting an attacker mint a fresh bucket per request and escape the limit entirely. A hop count resolves to an address appended by our own proxies. `parseTrustProxy` (`src/config/trustProxy.ts`) coerces the env string; both behaviours are pinned by `trustProxy.test.ts`.

Get the hop count from `GET /debug/client-ip` (needs `DEBUG_SECRET` + the `X-Debug-Secret` header) rather than guessing: call it from a machine whose public IP you know and set `TRUST_PROXY` to the `hops` whose `ip` matches. Default is `false` (correct for local dev, where nothing fronts the app).

**Render is 3** (measured 2026-07-16, stable across samples): the socket is a container-local proxy (`127.0.0.1`) and `X-Forwarded-For` arrives as `[client, Cloudflare edge, Render LB]`. `trustProxy.test.ts` replays that exact chain. The Render service is **not** Blueprint-managed, so `render.yaml` is documentation only — `TRUST_PROXY` has to be set in the Render dashboard, and a deploy will not pick it up from the repo. If `/debug/client-ip` ever shows `resolvedIp` drifting off the real caller, re-measure: a wrong count degrades to coarser buckets silently rather than erroring.

---

# Android

## Build commands

```bash
# Debug APK
./gradlew assembleDebug

# Release APK
./gradlew assembleRelease

# Install on connected device/emulator
./gradlew installDebug

# Run all tests
./gradlew test

# Run a single test class
./gradlew test --tests "com.burixer85.cs2skinflip.ExampleUnitTest"

# Fast type-check (no APK)
./gradlew :app:compileDebugKotlin

# Lint check
./gradlew lint
```

## Configuration

### `android/local.properties`
```
BACKEND_URL=http://10.0.2.2:3000
STEAM_API_KEY=your_key_here
ADMOB_APP_ID=your_admob_app_id
ADMOB_BANNER_UNIT_ID=your_admob_banner_unit_id
ADMOB_INTERSTITIAL_UNIT_ID=your_admob_interstitial_unit_id
```
`10.0.2.2` is the Android emulator's alias for `localhost`. Use the device's actual LAN IP for physical devices.

If `ADMOB_APP_ID` is absent, `build.gradle.kts` falls back to Google's public test AdMob Application ID (`ca-app-pub-3940256099942544~3347511713`); `AdsManager` falls back similarly for the banner/interstitial ad unit IDs in debug builds, so a fresh clone without these keys configured still works with Google's sample test ads.

### `app/google-services.json`
Required for Firebase (Analytics + FCM). Download from the Firebase console for project `cs2skinflip-304c6`, package `com.burixer85.cs2skinflip`. The file is gitignored — never commit it.

### Network security

`network_security_config.xml` sets `cleartextTrafficPermitted="false"` globally (system trust anchors only, no exceptions) — referenced via `android:networkSecurityConfig` in the manifest. `compileSdk`/`targetSdk` = 36, `minSdk` = 26.

### Room database
Uses `fallbackToDestructiveMigration()` — bump `version` in `AppDatabase.kt` whenever entities change. Only `watchlist` and `alerts` tables are persisted locally; skin data is always fetched from the backend.

## Architecture

Single-module Android app using **Jetpack Compose + Hilt + MVVM**.

### Layer structure

```
core/
  di/           — Hilt AppModule (single source of truth for all DI)
  analytics/    — AnalyticsService (Firebase Analytics wrapper, @Singleton)
  auth/         — AuthRepository (JWT token storage, Steam callback handling, FCM token update)
  domain/model  — Pure Kotlin data classes: Skin, WatchlistItem, Alert, PortfolioItem
  data/
    remote/     — Retrofit interfaces + DTOs + mappers (CS2BackendApiService, SteamApiService)
    local/      — Room database, Entities, DAOs
    repository  — SkinRepository, WatchlistRepository, AlertRepository, PortfolioRepository
    mock/       — MockData fallback used when backend is unreachable
  network/      — NetworkMonitor (connectivity StateFlow backing the app-wide offline banner)
  preferences/  — AppLanguage, LocaleHelper (in-app language override)
  review/       — ReviewFlowManager, PlayReviewLauncher (Play in-app review prompt)
features/
  home/         — Top movers Rising/Falling tabs (HomeViewModel → SkinRepository.getTrendingSkins)
  search/       — Paginated search with filters bottom sheet (SearchViewModel)
  skindetail/   — Single skin with price history (SkinDetailViewModel)
  portfolio/    — Real Steam inventory sync + manual entries, P&L (PortfolioViewModel)
  watchlist/    — Tracked skins with target prices
  alerts/       — Price alert management (create, edit, toggle, delete)
  settings/     — Settings screen + entry point to Alerts, language switcher
navigation/     — AppNavigation (NavHost + bottom bar), Screen sealed class
```

### Key data flow

- **`Skin`** is the central domain model. `lowestMarketPrice` first uses `lowestPrice` (pre-computed by the backend), falling back to `min(skinportPrice, csgoMarketPrice, waxpeerPrice)`.
- **`CS2BackendApiService`** is the main remote source. DTOs are mapped to `Skin` via `BackendSkinDto.toDomain()` and `TopMoverDto.toDomain()`. Mappers live in the same file as the DTOs.
- **`SkinRepository`** always falls back to `MockData` if the network call fails. It is a thin pass-through — backend responses already contain final prices (no client-side batch refresh, no `livePriceCache`).
- **Pagination** in `SearchViewModel` uses `currentPage` + `SearchUiState.Success.isLoadingMore` to prevent duplicate page fetches.

### DI (AppModule)

Two named Retrofit instances:
- `@Named("steam")` → `https://api.steampowered.com/`
- `@Named("backend")` → `BuildConfig.BACKEND_URL` — has `AuthInterceptor` attached

Room DB name: `cs2skinflip.db`.

### Firebase

**Analytics** (`AnalyticsService`, `@Singleton`): wraps `FirebaseAnalytics`. Methods: `logScreenView`, `logSkinViewed` (SELECT_ITEM), `logSkinAddedToWatchlist`, `logAlertCreated`, `logAlertEdited`, `logAlertDeleted`, `logSearch`. Injected into `AlertsViewModel`, `SkinDetailViewModel`.

**FCM** (`CS2SkinFlipMessagingService`): annotated `@AndroidEntryPoint`. Uses a manual `CoroutineScope(Dispatchers.IO + SupervisorJob())` because `FirebaseMessagingService` has no lifecycle. `onNewToken` calls `authRepository.updateFcmToken(token)`. `onMessageReceived` builds and shows a notification with a `PendingIntent` that puts `skinId` as an extra on the launch `Intent`.

**Notification tap → skin detail**: `MainActivity` reads `intent.getStringExtra("skinId")` in both `onCreate` and `onNewIntent`, stores it in `var notificationSkinId by mutableStateOf<String?>(null)`. `AppNavigation` receives `initialSkinId` + `onNavigatedToSkin` and uses `LaunchedEffect(initialSkinId)` to navigate once then clear the ID.

### Alerts feature

`AlertsViewModel` manages three state flows:
- `uiState` — the list of alerts (Loading / NotLoggedIn / Success / Error). `NotLoggedIn` renders a "Sign in with Steam" prompt — there is no email/password form.
- `createState` — the "create alert" bottom sheet (skin search, type, price)
- `editState` — the "edit alert" bottom sheet (type + price for an existing alert)

`EditAlertState.alert != null` signals the edit sheet is open. `submitEdit()` and `submitCreateAlert()` are `suspend` functions that return `true` on success so the caller can dismiss the sheet.

### Theme

Dark-only theme. Colors in `core/ui/theme/Color.kt`. Primary accent: `AccentOrange (#FF6B35)`. Rarity colors follow the standard CS2 naming convention.

### Wear parsing

Wear is parsed from the `marketHashName` suffix in `parseWearFromName()` inside `CS2BackendApiService.kt` — there is no separate `wear` field in the backend DTO.

### Portfolio (real Steam sync)

The **client**, not the backend, calls Steam's public inventory endpoint directly (`.../inventory/{steamId}/730/2` via `SteamApiService`) so each device uses its own IP rather than the server's shared IP hitting Steam's rate limit. A 401/403 is mapped to `SteamInventoryPrivateException` (private inventory). `POST /portfolio/sync` seeds `acquirePrice` with the current market price for newly-synced items — real cost basis is unknown to Steam's API — so P&L starts at 0 instead of showing a false ~-100% loss.

### Localization

`AppLanguage` (SYSTEM_DEFAULT, English, Spanish, Portuguese-BR, Russian, Polish, Turkish) + `LocaleHelper`, backed by plain `SharedPreferences` and applied in `attachBaseContext` — this runs before Hilt/DataStore exist, so it can't depend on the DI graph. `LocaleHelper.wrap(context)` calls `Locale.setDefault()` + `createConfigurationContext` to override the app locale independent of the device's system language. Translated resources live in `res/values-{es,pl,pt-rBR,ru,tr}/`.

### In-app review prompt

`ReviewFlowManager.maybeRequestReview(activity, trigger)` fires on `ALERT_NOTIFICATION_OPENED` or `SESSION_MILESTONE`, gated to at most one attempt per install via `UserPreferences.reviewRequested` regardless of which trigger fires first.

### Offline / network handling

`NetworkMonitor` (Hilt singleton) wraps `ConnectivityManager`, exposing `isOnline: StateFlow<Boolean>` via a registered `NetworkCallback`. It requires both `NET_CAPABILITY_INTERNET` and `NET_CAPABILITY_VALIDATED`, so a captive portal / no-real-internet Wi-Fi doesn't count as online. Backs a persistent app-wide offline banner; the app no longer silently falls back to mock data on a network failure, and premium purchase failures are surfaced to the user instead of swallowed.
