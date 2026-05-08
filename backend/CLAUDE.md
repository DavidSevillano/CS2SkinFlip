# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

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
| `DATABASE_URL` | Neon (PostgreSQL) connection string |
| `UPSTASH_REDIS_REST_URL` / `_TOKEN` | Upstash Redis (serverless) |
| `JWT_SECRET` | Min 32 chars |
| `FRONTEND_URL` | Used for CORS allowlist |

## Architecture

Fastify + Prisma + TypeScript. No test suite yet.

### Startup sequence (`app.ts`)

```
buildServer() → listen → populateSkins() → populatePricesFromSkinport() → startPriceRefreshJob()
```

`populateSkins` skips if the DB already has ≥ 12 000 rows (base skins + StatTrak variants). `populatePricesFromSkinport` runs the full bulk price fetch on startup. `startPriceRefreshJob` re-runs prices every 2 hours.

### Route structure (`src/routes/`)

| File | Prefix | Notes |
|---|---|---|
| `skins.ts` | `/skins` | Public — search, detail, price history, top-movers |
| `auth.ts` | `/auth` | Steam OpenID login → JWT cookie |
| `watchlist.ts` | `/watchlist` | Auth required |
| `alerts.ts` | `/alerts` | Auth required |
| `portfolio.ts` | `/portfolio` | Auth required |
| `prices.ts` | `/prices` | On-demand single-skin price refresh |

### Price pipeline

Three bulk sources are fetched in parallel on startup and every 2h:

1. **Skinport** (`fetchSkinportPrices`) — `api.skinport.com/v1/items?app_id=730&currency=USD`, one request, ~24k items, no auth required.
2. **DMarket** (`fetchDMarketPrices`) — `price-aggregator/v1/aggregated-prices`. Cap is 10,000 items per page (the `limit` param is silently ignored). Pagination uses capital-`Offset` param (`?Offset=0`, `?Offset=10000`, …). The API returns all games mixed together (~51k total); filter by `GameID === 'a8db'` to keep only CS2 items (~25k across 6 pages). `BestPrice` is in USD dollars (not cents). Do NOT use `exchange/v1/market/items` — it returns per-seller listings alphabetically starting with agents.
3. **CS:GO Market** (`fetchCsgoMarketPrices`) — `market.csgo.com/api/v2/prices/USD.json`, ~25k items, `market_hash_name` field, price is a USD string (just `parseFloat`), no auth required. Best coverage of the three.

`calcLowestPrice()` takes the MIN of all non-null, positive values. `SkinPrice.lowestPrice` stores this and is the primary price shown in the Android app.

Steam is intentionally excluded — it returns 429 from server IPs. No free proxy/API exists that provides Steam prices server-side.

### Skin catalog (`populateSkins.ts`)

Source: `ByMykel/CSGO-API` GitHub JSON. Each skin × wear combination becomes one DB row. StatTrak variants are generated explicitly:
- Regular: `StatTrak™ {name} ({wear})`
- Knives (names starting with `★`): `★ StatTrak™ {nameWithoutStar} ({wear})`

Skin IDs are slugs derived from `marketHashName` via `slugify()`.

### Caching (Redis / Upstash)

| Key | TTL |
|---|---|
| `steam:player:{steamId}` | 1 hour |
| `steam:inventory:{steamId}` | 10 min |
| `prices:{skinId}` | 5 min |
| `top-movers:20` | 15 min |

The `top-movers:20` cache is explicitly invalidated at the end of every bulk price run and on startup.

### Filtering (`GET /skins`)

All query filters are combined as an `AND` conditions array to avoid Prisma `where` object field-merging bugs. Wear is matched via `marketHashName ILIKE '%(${wear}%)'`. StatTrak is matched via `marketHashName ILIKE '%StatTrak%'`.

### Schema notes

`SkinPrice` has three price columns: `skinportPrice`, `dmarketPrice`, `csgoMarketPrice`. All are nullable floats in USD. `lowestPrice` is the computed minimum across all three and is indexed for sorting.
