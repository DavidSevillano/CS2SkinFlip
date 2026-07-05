# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Monorepo structure

```
CS2SkinFlip/
├── android/    ← Native Android app (Kotlin + Jetpack Compose + Hilt + MVVM)
└── backend/    ← REST API (Fastify + TypeScript + Prisma + PostgreSQL + Upstash Redis)
```

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
| `DATABASE_URL` | Neon (PostgreSQL) connection string |
| `UPSTASH_REDIS_REST_URL` / `_TOKEN` | Upstash Redis (serverless) |
| `JWT_SECRET` | Min 32 chars |
| `FRONTEND_URL` | Used for CORS allowlist |
| `FCM_SERVICE_ACCOUNT_PATH` | Optional — path to Firebase service account JSON for push notifications |

The price aggregator uses only public bulk endpoints — no marketplace API keys are required.

The Firebase service account file (`firebase-service-account.json`) is gitignored. Download from Firebase console → Project Settings → Service Accounts.

## Architecture

Fastify + Prisma + TypeScript. No test suite yet.

### Startup sequence (`app.ts`)

```
buildServer() → listen → populateSkins() → populatePrices() → startPriceRefreshJob()
```

`populateSkins` skips if the DB already has ≥ 12 000 rows. `populatePrices` runs the full bulk price fetch on startup and re-runs every 2 hours.

### Route structure (`src/routes/`)

| File | Prefix | Notes |
|---|---|---|
| `skins.ts` | `/skins` | Public — search, detail, price history, top-movers |
| `auth.ts` | `/auth` | Steam OAuth only (`GET /auth/steam` → `/auth/steam/callback`) → JWT; `PUT /auth/me/fcm-token` to save FCM token |
| `watchlist.ts` | `/watchlist` | Auth required |
| `alerts.ts` | `/alerts` | Auth required |
| `portfolio.ts` | `/portfolio` | Auth required |
| `prices.ts` | `/prices` | On-demand single-skin and batch price refresh |

### Price pipeline

Three marketplaces are fetched in parallel on startup and every 2h (`populatePrices`).
**All endpoints are bulk and public — no API keys, no rate-limit concerns** (12 calls/day per marketplace):

1. **Skinport** (`fetchSkinportPrices`) — `GET https://api.skinport.com/v1/items?app_id=730&currency=USD`, ~24k items.
2. **CS:GO Market** (`fetchCsgoMarketPrices`) — `GET https://market.csgo.com/api/v2/prices/USD.json`, ~25k items.
3. **Waxpeer** (`fetchWaxpeerPrices`) — `GET https://api.waxpeer.com/v1/prices?game=csgo`, ~20k items. `min` field is USD × 1000 (divide by 1000).

**CSDeals and DMarket dropped** (2026-07): CSDeals' bulk endpoint only lists ~2.6k actively-stocked items (vs ~20-25k for the others), so it almost never matched anything outside the most common skins. DMarket's public bulk aggregator (`price-aggregator/v1/aggregated-prices`) was retired outright; its replacement requires signed API-key auth and per-title lookups, incompatible with the no-keys bulk design here.

`calcLowestPrice()` takes the MIN of all non-null positive values across the three. `SkinPrice.lowestPrice` stores this and is the primary price shown in the Android app.

**No live calls anywhere.** The DB is the single source of truth. `GET /skins/:id`, `GET /prices/batch`, and the search/top-movers endpoints all read straight from `SkinPrice` — search responses already contain the marketplace prices and `lowestPrice` correct on first render. The Android `SkinRepository` is correspondingly simple: no `livePriceCache`, no batch refresh after list loads.

**Top movers** (`PriceService.getTopMovers()`): pure DB read, ordered by the indexed `priceChange24h` column (kept fresh by the bulk job). `priceChange24h` is then refined per skin from the `PriceHistory` table for accuracy between bulk runs.

### Alert notifications (FCM)

`src/services/fcm.ts` lazily initialises the Firebase Admin SDK from `FCM_SERVICE_ACCOUNT_PATH`. `sendAlertNotification()` sends a data-only FCM message (no `notification` key) so the Android app builds the notification in-process and can attach the `skinId` for tap navigation.

The alert-check job (`src/services/alerts.ts`) fetches the user's `fcmToken` from the DB after triggering an alert and calls `sendAlertNotification` if a token exists.

### Alert updates (`PUT /alerts/:id`)

Supports partial updates: `isActive`, `targetPrice`, and `type`. When `targetPrice` or `type` changes, the alert is automatically re-armed (`isTriggered = false`, `isActive = true`).

### Skin catalog (`populateSkins.ts`)

Source: `ByMykel/CSGO-API` GitHub JSON. Each skin × wear combination = one DB row. StatTrak variants:
- Regular: `StatTrak™ {name} ({wear})`
- Knives (`★`-prefix): `★ StatTrak™ {nameWithoutStar} ({wear})`

Skin IDs are slugs derived from `marketHashName` via `slugify()`.

### Caching (Redis / Upstash)

| Key | TTL |
|---|---|
| `steam:player:{steamId}` | 1 hour |
| `steam:inventory:{steamId}` | 10 min |
| `prices:{skinId}` | 5 min |
| `top-movers:20` | 15 min |

`top-movers:20` is explicitly invalidated at the end of every bulk price run.

### Schema notes

`SkinPrice`: `skinportPrice`, `csgoMarketPrice`, `waxpeerPrice` (nullable floats, USD). `lowestPrice` = computed MIN, indexed for sorting. `priceChange24h` is also indexed for the home top-movers query.

`User`: Steam OAuth only — `steamId` is required and unique. Has `fcmToken String?` for FCM push notifications. (Email/password auth was removed in favor of Steam-only sign-in.)

### Filtering (`GET /skins`)

All filters are combined as `AND` conditions. Wear is matched via `marketHashName ILIKE '%(${wear}%)'`. StatTrak via `ILIKE '%StatTrak%'`. Search uses `regexp_replace(lower(...), '[^a-z0-9]', '', 'g')` to strip punctuation before matching.

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
```
`10.0.2.2` is the Android emulator's alias for `localhost`. Use the device's actual LAN IP for physical devices.

### `app/google-services.json`
Required for Firebase (Analytics + FCM). Download from the Firebase console for project `cs2skinflip-304c6`, package `com.burixer85.cs2skinflip`. The file is gitignored — never commit it.

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
features/
  home/         — Top movers list (HomeViewModel → SkinRepository.getTrendingSkins)
  search/       — Paginated search with filters bottom sheet (SearchViewModel)
  skindetail/   — Single skin with price history (SkinDetailViewModel)
  portfolio/    — Manual portfolio tracking
  watchlist/    — Tracked skins with target prices
  alerts/       — Price alert management (create, edit, toggle, delete)
  settings/     — Settings screen + entry point to Alerts
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
