# CS2SkinFlip

Trading assistant for Counter-Strike 2 skins. Monorepo with Android app + Node.js backend.

```
CS2SkinFlip/
├── android/    ← Native Android app (Kotlin + Jetpack Compose)
└── backend/    ← REST API (Node.js + Fastify + PostgreSQL)
```

---

## Android

Native CS2 skin trading assistant with dark theme, multi-marketplace prices, watchlist, and freemium alerts.

**Stack:** Kotlin 1.9 · Jetpack Compose + Material Design 3 · Clean Architecture · Hilt · Room · Retrofit · Coil

### Setup

1. Open the `android/` folder in Android Studio (File → Open → select `android/`)
2. Create `android/local.properties`:
   ```
   sdk.dir=C\:\\Users\\YourUser\\AppData\\Local\\Android\\Sdk
   STEAM_API_KEY=your_steam_api_key_here
   ```
3. Sync Gradle → Build → Run

### Features

| Screen | Description |
|--------|-------------|
| Home | Top movers 24h · pull-to-refresh · skeleton loaders |
| Search | Filters: weapon, wear (FN/MW/FT/WW/BS), price range |
| Skin Detail | Steam / CSFloat / Skinport / DMarket prices · 30d chart |
| Portfolio | Steam inventory sync (mock OAuth) · total P&L |
| Watchlist | Price targets · swipe-to-delete · Room persistence |
| Alerts | Freemium: 5 free / unlimited Premium ($4.99/mo) |
| Settings | Plan management · Premium banner |

### Architecture

```
android/app/src/main/java/com/burixer85/cs2skinflip/
├── features/           ← UI layer (ViewModel + Screen per feature)
├── core/
│   ├── data/
│   │   ├── local/      ← Room (Watchlist + Alerts)
│   │   ├── remote/     ← Retrofit (Steam Web API)
│   │   ├── mock/       ← 14 real CS2 skins for MVP
│   │   └── repository/
│   ├── domain/model/   ← Skin, Alert, Portfolio, WatchlistItem
│   ├── di/             ← Hilt modules
│   └── ui/
│       ├── theme/      ← Dark theme (#0D1117)
│       └── components/ ← SkinCard, PriceChangeChip, PremiumBanner
└── navigation/         ← NavGraph + Screen sealed class
```

---

## Backend

REST API for the CS2SkinFlip trading platform.

**Stack:** Node.js 20 · Fastify · TypeScript · PostgreSQL + Prisma · Upstash Redis · Steam OAuth (OpenID 2.0)

### Setup

```bash
cd backend
npm install

# 1. Configure environment
cp .env.example .env
# Fill in DATABASE_URL, UPSTASH credentials, JWT_SECRET, STEAM_API_KEY

# 2. Setup database
npm run db:generate
npm run db:push
npm run db:seed

# 3. Start dev server
npm run dev
```

### API Endpoints

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| `GET` | `/health` | — | Health check |
| `GET` | `/auth/steam` | — | Initiate Steam login |
| `GET` | `/auth/steam/callback` | — | Steam OAuth callback |
| `GET` | `/auth/me` | JWT | Current user |
| `POST` | `/auth/logout` | — | Clear session |
| `GET` | `/skins` | — | List/search skins |
| `GET` | `/skins/top-movers` | — | Top 24h price movers |
| `GET` | `/skins/:id` | — | Skin detail |
| `GET` | `/skins/:id/price-history` | — | Price history (30d) |
| `GET` | `/prices/:skinId` | — | Live aggregated prices |
| `GET` | `/portfolio` | JWT | User portfolio + P&L |
| `POST` | `/portfolio/sync` | JWT | Sync Steam inventory |
| `GET` | `/watchlist` | JWT | Watchlist items |
| `POST` | `/watchlist` | JWT | Add to watchlist |
| `PUT` | `/watchlist/:id` | JWT | Update targets |
| `DELETE` | `/watchlist/:id` | JWT | Remove item |
| `GET` | `/alerts` | JWT | Price alerts |
| `POST` | `/alerts` | JWT | Create alert (freemium: 5 free) |
| `PUT` | `/alerts/:id` | JWT | Update alert |
| `DELETE` | `/alerts/:id` | JWT | Delete alert |

### Environment Variables

| Variable | Description |
|----------|-------------|
| `DATABASE_URL` | PostgreSQL connection string |
| `UPSTASH_REDIS_REST_URL` | Upstash Redis URL |
| `UPSTASH_REDIS_REST_TOKEN` | Upstash Redis token |
| `JWT_SECRET` | Min 32-char secret |
| `STEAM_API_KEY` | Steam Web API key |
| `STEAM_CALLBACK_URL` | OAuth callback URL |
| `FRONTEND_URL` | CORS origin / redirect target |

---

## Recommended services (free tier)

| Service | Use |
|---------|-----|
| [Neon](https://neon.tech) / [Supabase](https://supabase.com) | PostgreSQL |
| [Upstash](https://upstash.com) | Redis |
| [Railway](https://railway.app) / [Render](https://render.com) | Backend hosting |
