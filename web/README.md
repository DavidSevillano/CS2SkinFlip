# SEO web

A static site generator that emits **one indexable HTML page per priced CS2 skin**, deployed to Cloudflare Pages. No client-side JavaScript, no server, no database at request time — every page is plain HTML on a CDN.

## Why it exists

Nobody googles "app for skin prices". They google **"AK-47 Redline price"**, **"Butterfly Knife Fade value"**, "M4A4 Howl field-tested". Those long-tail queries are the whole point: each generated page targets exactly one of them, carries the live price and marketplace comparison, and links to the Play Store listing. The Android app is discoverable; a page-per-skin is indexable.

## Architecture

```
GitHub Actions (daily cron) -> :generator:run -> one bulk call -> Render (Fastify) -> Neon
                                    |
                                 dist/ (static HTML)
                                    |
                          wrangler pages deploy -> Cloudflare CDN -> Googlebot / users
```

The key insight: the backend is hit **once per regeneration**, never per page-view. A crawl of tens of thousands of pages by Googlebot pulls entirely from Cloudflare's CDN and adds **zero load** to the Render free tier. The generator makes one bulk `/skins/export` call, renders every page in-process, and writes a `dist/` tree that the CDN serves forever (until the next daily run replaces it).

## Modules

| Module | Type | Notes |
|---|---|---|
| `:shared` | Kotlin Multiplatform (single `jvm()` target today) | Models, formatting helpers, SEO metadata + `Product`/`BreadcrumbList` JSON-LD builders, Ktor API client. All logic lives in `commonMain` so an `androidTarget()` can be added later without moving a single file. |
| `:generator` | JVM application | Renders pages with `kotlinx.html`, writes `dist/`. Entry point `com.burixer85.cs2skinflip.generator.MainKt`. |

## Local run

```bash
# 1. Configure
cp .env.example .env        # BACKEND_URL, SITE_URL

# 2. Start the backend (from ../backend)
npm run dev

# 3. Generate the site
BACKEND_URL=http://localhost:3000 \
SITE_URL=http://localhost:8080 \
OUT_DIR="$PWD/dist" \
  ./gradlew :generator:run
```

**CWD gotcha:** Gradle's `run` task uses the **module** directory as its working directory. With a *relative* `OUT_DIR=dist`, `:generator:run` writes to `web/generator/dist`, not `web/dist`. Pass an **absolute** `OUT_DIR` (as above) to control exactly where the output lands. The CI workflow does the same with `${{ github.workspace }}/web/dist`.

## Commands

| Command | Does |
|---|---|
| `./gradlew build` | Compile + run all tests (shared + generator). |
| `./gradlew :shared:jvmTest` | Run the `:shared` test suite. |
| `./gradlew :generator:test` | Run the `:generator` test suite. |
| `./gradlew :generator:run` | Generate the static site into `OUT_DIR`. |

## Environment variables

Read by `GeneratorConfig.fromEnv`:

| Variable | Required | Meaning |
|---|---|---|
| `BACKEND_URL` | yes | Fastify API base, no trailing slash. |
| `SITE_URL` | yes | Canonical origin, no trailing slash. |
| `OUT_DIR` | no (default `dist`) | Output directory. Pass an absolute path — see the CWD gotcha above. |
| `ANDROID_CERT_SHA256` | no | Release keystore SHA-256 for `.well-known/assetlinks.json`; a placeholder is written if absent. |

## Known constraints

**Cloudflare Pages' 20,000-file-per-deployment limit.** `MAX_SKIN_PAGES = 19,500` caps the skin pages generated. As of the last run the backend returns **13,495 priced skins**, producing **13,500 files** total (13,495 skin pages + `index.html`, `privacy/index.html`, `sitemap.xml`, `robots.txt`, and `.well-known/assetlinks.json`) — comfortably under both limits, so the cap does **not** currently bite. The generator logs the true skin count and warns if it exceeds the cap. If the catalog outgrows this, raise the backend's price floor (fewer, higher-value skins) or move to a host without a file-count limit (e.g. GitHub Pages).

**Price history costs one HTTP request per skin.** So only the top `HISTORY_TOP_N` (1,000) skins get a 30-day price-history table — those are the pages with meaningful search volume, fetched with `HISTORY_CONCURRENCY = 8` parallelism. Every *other* page still carries the live price, the three-marketplace comparison, the 24h change, the skin attributes and internal links to related skins — enough unique, useful data to stay clear of thin-content penalties.

## Deploying

Deployment is fully automated by [`.github/workflows/deploy-web.yml`](../.github/workflows/deploy-web.yml). It runs **daily at 04:00 UTC** and can also be triggered manually from **Actions → Deploy SEO web → Run workflow**.

**First-time setup:** the Cloudflare Pages project `cs2skinflip-web` must **exist before the first deploy** — create it once in the Cloudflare dashboard (or `wrangler pages project create cs2skinflip-web`). The workflow deploys to it but does not create it.

Required GitHub repository secrets:

| Secret | Required | Purpose |
|---|---|---|
| `BACKEND_URL` | yes | Fastify API base for the generator. |
| `SITE_URL` | yes | Canonical origin baked into every page. |
| `CLOUDFLARE_API_TOKEN` | yes | Token with Pages edit permission. |
| `CLOUDFLARE_ACCOUNT_ID` | yes | Target Cloudflare account. |
| `ANDROID_CERT_SHA256` | no | Release keystore SHA-256 for `assetlinks.json`. Placeholder written if absent. |

The workflow runs `./gradlew build` (all tests) before generating, so a broken test **fails the deploy closed** — nothing ships if the suite is red.

## Post-deploy SEO checklist

1. Register the site in **Google Search Console** and verify ownership.
2. Submit `https://<SITE_URL>/sitemap.xml`.
3. Validate a skin page with the [Rich Results Test](https://search.google.com/test/rich-results) — it should detect **`Product`** and **`BreadcrumbList`** structured data.
4. **Android App Links** (deferred, not yet built): set the `ANDROID_CERT_SHA256` secret so a real fingerprint lands in `.well-known/assetlinks.json`, then add a verified `intent-filter` to the app's `AndroidManifest.xml` to deep-link skin URLs into the app.
