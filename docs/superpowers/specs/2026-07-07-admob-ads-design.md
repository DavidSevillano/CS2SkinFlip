# AdMob ads for free users

## Problem

The app currently has no ad monetization — the only revenue path is the
one-time `premium_unlimited_alerts` purchase (`BillingRepository.kt`,
`billing.ts`). Free users see no ads at all, so there's no incentive tied
to "remove ads" and no revenue from the majority of users who never buy
premium.

## Goal

Show AdMob ads to free (non-premium) users, low-friction: a persistent
banner on high-traffic/low-engagement screens, and an occasional
interstitial on a high-intent screen. Premium users (`isPremium == true`)
never see ads, reinforcing the value of the one-time purchase.

## Ad placement

| Screen | Ad type | Rule |
|---|---|---|
| Home (top movers) | Banner, fixed at bottom | Always visible for free users |
| Search (results) | Banner, fixed at bottom | Hidden while the filters bottom sheet is open |
| Skin Detail | Interstitial, on exit | Every 4th visit per session, min. 5 min since the last interstitial |
| Watchlist, Portfolio, Alerts, Settings | None | These are "task" screens (compare/decide/manage) where an ad competes for attention at the wrong moment |

Rationale: Home/Search are passive-scroll screens, so a banner doesn't
interrupt anything. Skin Detail is where users spend focused decision
time, so it gets an interstitial instead of a banner (which would
compete for attention against the price chart), and it fires on **exit**
rather than entry so it never delays the value the user came for.

## AdMob account

| Purpose | Value |
|---|---|
| App ID | `ca-app-pub-7056913970910850~8741780000` |
| Banner ad unit | `ca-app-pub-7056913970910850/5836415064` |
| Interstitial ad unit | `ca-app-pub-7056913970910850/4523333395` |

## Configuration

Same pattern as `STEAM_API_KEY`/`BACKEND_URL` in `android/local.properties`:

```
ADMOB_APP_ID=ca-app-pub-7056913970910850~8741780000
ADMOB_BANNER_UNIT_ID=ca-app-pub-7056913970910850/5836415064
ADMOB_INTERSTITIAL_UNIT_ID=ca-app-pub-7056913970910850/4523333395
```

- `build.gradle.kts`: reads these three properties. The App ID is injected
  into `AndroidManifest.xml` via `manifestPlaceholders["admobAppId"]`
  (consumed by a `<meta-data android:name="com.google.android.gms.ads.APPLICATION_ID" android:value="${admobAppId}"/>`
  entry). The two ad unit IDs become `buildConfigField("String", "ADMOB_BANNER_UNIT_ID", ...)`
  and `buildConfigField("String", "ADMOB_INTERSTITIAL_UNIT_ID", ...)`.
- In **debug** builds, `AdsManager` ignores `BuildConfig` and uses Google's
  official test ad unit IDs (hardcoded constants) instead, so development
  never generates invalid production traffic. In **release**, it reads the
  real IDs from `BuildConfig`.
- New Gradle dependencies: `com.google.android.gms:play-services-ads` and
  `com.google.android.ump:user-messaging-platform`.

## Architecture

### `core/ads/AdsManager.kt` (`@Singleton`, Hilt)

Central owner of the AdMob SDK. Public API consumed by feature screens:

- `suspend fun initialize(activity: Activity)` — called once from
  `MainActivity.onCreate`. Runs the UMP consent flow
  (`ConsentInformation.requestConsentInfoUpdate` → show the consent form
  if required) and only calls `MobileAds.initialize()` once consent is
  resolved (or immediately if not required for the user's region).
- `fun bannerAdUnitId(): String` / `fun interstitialAdUnitId(): String` —
  return test IDs in debug, real `BuildConfig` IDs in release.
- Interstitial state, held in memory for the process lifetime (a "session"
  for the purposes of the 4-visit counter): `skinDetailVisitCount: Int`,
  `lastInterstitialShownAt: Long?`, and a preloaded `InterstitialAd?`.
- `fun onSkinDetailEntered()` — increments the visit counter.
- `fun maybeShowInterstitialOnExit(activity: Activity, isPremium: Boolean)` —
  called from Skin Detail's exit point. Delegates the go/no-go decision to
  a pure function (see Testing) and, if it says yes, shows the preloaded
  interstitial and kicks off loading the next one in the background. If no
  interstitial is preloaded yet (slow network), it silently does nothing —
  navigation is never blocked waiting for an ad.

### `core/ads/BannerAdView.kt`

A Composable wrapping `AdView` via `AndroidView`, taking an `adUnitId`
parameter. Callers gate it with `if (!isPremium) BannerAdView(...)` —
when hidden, it renders nothing and reserves no layout space.

### `core/data/repository/PremiumStatusRepository.kt` (`@Singleton`, Hilt)

Single source of truth for "does this user currently see ads":

- `val isPremium: StateFlow<Boolean>` — defaults to `false`.
- `suspend fun refresh()` — calls `backendApi.getMe()` and updates
  `isPremium` from the response. On network failure, the flow keeps its
  last known value (or `false` if there's never been a successful fetch)
  instead of throwing — ad-gating must fail open to "show ads", never
  crash.
- `refresh()` is called: once at app start (if logged in), right after the
  Steam login callback completes, and right after
  `BillingRepository` confirms a purchase (`isPremium == true` from
  `verify-purchase`).

This is a new, separate cache from the `getMe()` calls already made by
`SettingsViewModel` and `BillingRepository` for their own purposes — those
are left untouched since they need the full `MeResponseDto`, not just the
boolean flag, and refactoring them into this shared repository is out of
scope for this change.

## Data flow

1. App start → `MainActivity.onCreate` calls `AdsManager.initialize()`
   (consent + SDK init) and, if logged in, `PremiumStatusRepository.refresh()`.
2. `HomeScreen` / `SearchScreen` collect `premiumStatusRepository.isPremium`
   and conditionally render `BannerAdView` at the bottom of the screen.
3. `SkinDetailScreen` calls `AdsManager.onSkinDetailEntered()` on
   composition and, in a `DisposableEffect(Unit) { onDispose { ... } }`,
   calls `AdsManager.maybeShowInterstitialOnExit(activity, isPremium)`.
4. On purchase completion (`BillingRepository`) or login
   (`AuthRepository.handleCallback`), `PremiumStatusRepository.refresh()`
   is triggered so ads disappear immediately without needing an app
   restart.

## Error handling / edge cases

- Interstitial not preloaded in time → skip silently, never block
  navigation.
- `GET /auth/me` fails (offline) → `PremiumStatusRepository` keeps the
  last known value; a user who was confirmed premium stays ad-free
  offline, a user who was never confirmed premium keeps seeing ads
  (fail-open, not fail-closed on cost but fail-closed on ad noise).
- UMP consent form fails to load → `AdsManager.initialize()` proceeds to
  `MobileAds.initialize()` anyway rather than blocking ads forever; AdMob
  falls back to non-personalized ads in that case per its own SDK
  behavior.

## Testing

No existing unit test suite for Android beyond the default
`ExampleUnitTest` placeholder, and this change doesn't warrant instrumented
UI tests. Two pieces of pure logic get JUnit tests:

- The interstitial go/no-go decision, extracted as a standalone pure
  function (e.g. `shouldShowInterstitial(visitCount: Int, lastShownAt: Long?, now: Long, isPremium: Boolean): Boolean`)
  so it's testable without any Android/Activity dependency: fires on the
  4th/8th/... visit, does not fire again inside the 5-minute cooldown even
  if the count matches, never fires for premium users.
- `PremiumStatusRepository.refresh()` falls back to the last known value
  (or `false`) when `backendApi.getMe()` throws.

Everything else (banner visibility, actual ad rendering, UMP form
display) is verified manually: banners visible on Home/Search, hidden
when simulating a premium account, interstitial timing checked by
navigating in/out of Skin Detail repeatedly.

## Out of scope

- Ad mediation / multiple ad networks — AdMob only.
- Rewarded ads.
- Persisting the interstitial visit counter/cooldown across app restarts
  (it's session/process-scoped only).
- Server-side changes — `GET /auth/me` already returns `isPremium`.
