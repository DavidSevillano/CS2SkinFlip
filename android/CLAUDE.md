# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

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

# Lint check
./gradlew lint
```

## Configuration

The backend URL and Steam API key are injected at build time via `local.properties`:

```
BACKEND_URL=http://10.0.2.2:3000
STEAM_API_KEY=your_key_here
```

`10.0.2.2` is the Android emulator's alias for `localhost` on the host machine. Use the device's actual LAN IP for physical devices.

The Room database uses `fallbackToDestructiveMigration()` — bump `version` in `AppDatabase.kt` whenever entities change.

## Architecture

The app is a single-module Android app using **Jetpack Compose + Hilt + MVVM**.

### Layer structure

```
core/
  di/          — Hilt AppModule (single source of truth for all DI)
  domain/model — Pure Kotlin data classes: Skin, WatchlistItem, Alert, PortfolioItem
  data/
    remote/    — Retrofit interfaces + DTOs + mappers (CS2BackendApiService, SteamApiService)
    local/     — Room database, Entities (WatchlistEntity, AlertEntity), DAOs
    repository — SkinRepository, WatchlistRepository, AlertRepository, PortfolioRepository
    mock/      — MockData fallback used when backend is unreachable
features/
  home/        — Top movers list (HomeViewModel → SkinRepository.getTrendingSkins)
  search/      — Paginated search with filters bottom sheet (SearchViewModel)
  skindetail/  — Single skin with price history (SkinDetailViewModel)
  portfolio/   — Manual portfolio tracking
  watchlist/   — Tracked skins with target prices
  alerts/      — Price alert management
  settings/    — Settings + navigation to Alerts
navigation/    — AppNavigation (NavHost + bottom bar), Screen sealed class
```

### Key data flow

- **`Skin`** is the central domain model. `lowestMarketPrice` is a computed property: `min(steamPrice, skinportPrice, dmarketPrice)`.
- **`CS2BackendApiService`** is the main remote source. All DTOs are mapped to `Skin` via `BackendSkinDto.toDomain()` and `TopMoverDto.toDomain()` in `CS2BackendApiService.kt`.
- **`SkinRepository`** always falls back to `MockData` if the network call fails, so the app is usable offline.
- **Pagination** in `SearchViewModel` uses a synchronous `isLoadingMoreFlag` (not a StateFlow) to prevent duplicate `loadPage()` coroutines when `loadMore()` is called multiple times in the same composition frame.

### DI (AppModule)

Two named Retrofit instances:
- `@Named("steam")` → `https://api.steampowered.com/`
- `@Named("backend")` → `BuildConfig.BACKEND_URL`

Room DB name: `cs2skinflip.db`. Only `watchlist` and `alerts` tables are persisted locally; skin data is fetched from the backend on demand.

### Theme

Dark-only theme. All colors are defined in `core/ui/theme/Color.kt`. Primary accent is `AccentOrange (#FF6B35)`. Rarity-specific colors follow the standard CS2 naming convention (`RarityConsumer` through `RarityKnife`).

### Wear parsing

Wear is parsed from the `marketHashName` suffix in `parseWearFromName()` inside `CS2BackendApiService.kt` — there is no separate `wear` field in the backend DTO.
