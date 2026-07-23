# CS2SkinFlip

Price-tracking assistant for Counter-Strike 2 skins. Monorepo with an Android app, a Node.js backend, and a static SEO site.

```
CS2SkinFlip/
├── android/        ← Native Android app (Kotlin + Jetpack Compose)
├── backend/        ← REST API (Node.js + Fastify + PostgreSQL + Prisma + Upstash Redis)
├── web/            ← Static site generator (Kotlin Multiplatform) deployed to Cloudflare Pages — see web/README.md
└── store-assets/   ← Play Store listing copy + generated screenshots (Puppeteer) — see store-assets/screenshots/README.md
```

---

## Android

Native CS2 skin price tracker with dark theme, multi-marketplace prices, watchlist, portfolio, and a freemium alert system.

**Stack:** Kotlin · Jetpack Compose + Material Design 3 · Hilt · Room · Retrofit · Coil

### Setup

1. Open the `android/` folder in Android Studio (File → Open → select `android/`)
2. Create `android/local.properties`:
   ```
   sdk.dir=C\:\\Users\\YourUser\\AppData\\Local\\Android\\Sdk
   BACKEND_URL=http://10.0.2.2:3000
   STEAM_API_KEY=your_steam_api_key_here
   ADMOB_APP_ID=your_admob_app_id
   ADMOB_BANNER_UNIT_ID=your_admob_banner_unit_id
   ADMOB_INTERSTITIAL_UNIT_ID=your_admob_interstitial_unit_id
   ```
   (`10.0.2.2` is the emulator's alias for `localhost`; use the device's LAN IP on a physical device. AdMob IDs are optional — falls back to Google's public test ad units in debug builds.)
3. Add `app/google-services.json` (Firebase Analytics + FCM — download from the Firebase console for `cs2skinflip-304c6`)
4. Sync Gradle → Build → Run

### Features

| Screen | Description |
|--------|--------------|
| Home | Top movers 24h, Rising/Falling tabs · pull-to-refresh |
| Search | Filters: weapon, wear (FN/MW/FT/WW/BS), StatTrak, price range |
| Skin Detail | Skinport / CS:GO Market / Waxpeer prices · price chart (7D / 30D / 3M) |
| Portfolio | Real Steam inventory sync (public inventory endpoint) · P&L |
| Watchlist | Price targets · swipe-to-delete · Room persistence |
| Alerts | Freemium: 1 free alert / unlimited via one-time premium purchase (Play Billing) |
| Settings | Language switcher, plan management |

Sign-in is Steam OAuth only. The app also supports in-app language switching (English, Spanish, Portuguese-BR, Russian, Polish, Turkish), an offline banner backed by live connectivity monitoring, and a Play in-app review prompt gated to once per install.

### Architecture

```
android/app/src/main/java/com/burixer85/cs2skinflip/
├── features/           ← UI layer (ViewModel + Screen per feature)
├── core/
│   ├── data/
│   │   ├── local/      ← Room (Watchlist + Alerts)
│   │   ├── remote/     ← Retrofit (backend + Steam Web API)
│   │   └── repository/
│   ├── domain/model/   ← Skin, Alert, Portfolio, WatchlistItem
│   ├── network/        ← NetworkMonitor (offline banner)
│   ├── preferences/    ← AppLanguage / LocaleHelper
│   ├── review/         ← ReviewFlowManager (Play in-app review)
│   ├── di/             ← Hilt modules
│   └── ui/theme/       ← Dark-only theme
└── navigation/         ← NavGraph + Screen sealed class
```

See root [`CLAUDE.md`](CLAUDE.md) for the full architecture writeup.

---

## Backend

REST API for the CS2SkinFlip platform. No live marketplace calls at request time — a bulk price pipeline populates the DB every 6 hours and all reads hit Postgres.

The refresh is **not** scheduled inside the API process: `.github/workflows/refresh-prices.yml` cron-triggers `POST /jobs/refresh-prices` at 00/06/12/18 UTC, which runs the price population → alert check → history cleanup behind a Redis lock.

**Stack:** Node.js · Fastify · TypeScript · PostgreSQL + Prisma · Upstash Redis · Steam OAuth (OpenID 2.0)

### Setup

```bash
cd backend
npm install

# 1. Configure environment
cp .env.example .env
# Fill in DATABASE_URL, UPSTASH credentials, JWT_SECRET, STEAM_API_KEY, JOBS_SECRET
# (and TRUST_PROXY in production) — see CLAUDE.md for the full variable list

# 2. Setup database
npm run db:push

# 3. Start dev server
npm run dev
```

### API Endpoints

| Method | Endpoint | Auth | Description |
|--------|----------|------|--------------|
| `GET` | `/health` | — | Liveness + price-pipeline freshness (`fresh` / `stale` / `unknown`) |
| `GET` | `/auth/steam` | — | Initiate Steam login |
| `GET` | `/auth/steam/callback` | — | Steam OAuth callback |
| `GET` | `/auth/me` | JWT | Current user |
| `POST` | `/auth/logout` | — | Clear the session cookie |
| `DELETE` | `/auth/me` | JWT | Delete the account |
| `PUT` | `/auth/me/fcm-token` | JWT | Save device FCM token |
| `GET` | `/skins` | — | Search/list skins (rate-limited tighter than the global limit) |
| `GET` | `/skins/weapons` | — | Weapon list for the search filters |
| `GET` | `/skins/top-movers` | — | Top 24h movers (`?direction=rising\|falling`) |
| `GET` | `/skins/export` | — | Bulk export consumed by the `web/` SEO generator |
| `GET` | `/skins/:id` | — | Skin detail |
| `GET` | `/skins/:id/price-history` | — | Price history (`?range=24h\|7d\|30d\|90d`, default `24h`) |
| `GET` | `/prices/batch` | — | Batch price lookup |
| `GET` | `/prices/:skinId` | — | Single-skin price lookup |
| `POST` | `/prices/:skinId/refresh` | — | Force-refresh one skin's price |
| `GET` | `/watchlist` | JWT | Watchlist items |
| `POST` / `PUT` / `DELETE` | `/watchlist(/:id)` | JWT | Add / update targets / remove |
| `GET` | `/alerts` | JWT | Price alerts |
| `POST` / `PUT` / `DELETE` | `/alerts(/:id)` | JWT | Create (freemium: 1 free) / update / delete |
| `GET` / `POST` | `/portfolio` | JWT | Portfolio items |
| `POST` | `/portfolio/sync` | JWT | Sync Steam inventory |
| `DELETE` | `/portfolio/:id` | JWT | Remove item |
| `POST` | `/billing/verify-purchase` | JWT | Verify a Google Play premium purchase |
| `POST` | `/jobs/refresh-prices` | `X-Jobs-Secret` | Trigger the bulk price run (202 + `runId`) |
| `GET` | `/jobs/refresh-prices/status` | `X-Jobs-Secret` | Status of the last/current run |

Reads never hit a marketplace: `/skins*` and `/prices*` all serve from Postgres, which the 6h bulk job keeps fresh. The `/jobs` routes are only registered when `JOBS_SECRET` is set — without it nothing schedules a refresh and `/health` reports `stale` within 8h.

Full endpoint notes, price pipeline details, caching, and schema are documented in root [`CLAUDE.md`](CLAUDE.md).

---

## Web

`web/` generates a static, indexable page per priced skin (long-tail SEO) and deploys daily to Cloudflare Pages — no server involved at page-view time. See [`web/README.md`](web/README.md).

---

## Legal

- Privacy policy & Terms of Service linked from the app: `https://davidsevillano.github.io/cs2skinflip-legal/` (separate repo — the canonical copy).
- A second privacy policy is generated at `/privacy` by the `web/` site (source: `web/generator/src/main/kotlin/.../html/PrivacyPage.kt`) — keep both in sync; see [`CLAUDE.md`](CLAUDE.md).
- Contact: burideveloper@gmail.com

---

## Recommended services (free tier)

| Service | Use |
|---------|-----|
| [Neon](https://neon.tech) | PostgreSQL |
| [Upstash](https://upstash.com) | Redis |
| [Render](https://render.com) | Backend hosting |
| [Cloudflare Pages](https://pages.cloudflare.com) | Static SEO site hosting |
