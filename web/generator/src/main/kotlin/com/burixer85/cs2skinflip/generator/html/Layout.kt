package com.burixer85.cs2skinflip.generator.html

import kotlinx.html.HEAD
import kotlinx.html.HTML
import kotlinx.html.MAIN
import kotlinx.html.body
import kotlinx.html.head
import kotlinx.html.link
import kotlinx.html.main
import kotlinx.html.meta
import kotlinx.html.script
import kotlinx.html.style
import kotlinx.html.title
import kotlinx.html.unsafe
import kotlinx.serialization.json.JsonObject

const val DOCTYPE = "<!DOCTYPE html>"

/** Dark theme mirroring the Android app. Inlined: one file, no extra request, no CDN. */
private val STYLES = """
    :root { --accent:#ff6b35; --surface:#12141a; --raised:#1b1e26; --border:#2a2e38; }
    * { box-sizing: border-box; }
    body { margin:0; background:var(--surface); color:#e6e8ec;
           font-family: system-ui, -apple-system, "Segoe UI", sans-serif; line-height:1.5; }
    main { max-width: 56rem; margin: 0 auto; padding: 2rem 1rem; }
    a { color: var(--accent); }
    h1 { font-size: 1.875rem; }
    table { width:100%; border-collapse:collapse; text-align:left; }
    th, td { padding:.6rem 0; border-bottom:1px solid var(--border); }
    td.num, th.num { text-align:right; font-variant-numeric: tabular-nums; }
    .price { font-size:2.25rem; font-weight:700; color:#fff; }
    .up { color:#34d399; } .down { color:#f87171; }
    .cta { background:var(--raised); border:1px solid var(--border);
           border-radius:.5rem; padding:1.5rem; }
    .btn { display:inline-block; background:var(--accent); color:#000; font-weight:600;
           padding:.6rem 1.25rem; border-radius:.375rem; text-decoration:none; }
    .cards { list-style:none; padding:0; display:grid; gap:.5rem; }
    .cards a { display:flex; justify-content:space-between; gap:1rem;
               border:1px solid var(--border); border-radius:.375rem;
               padding:.75rem 1rem; text-decoration:none; color:#e6e8ec; }
    img.skin { width:16rem; height:auto; }
""".trimIndent()

/**
 * Emits a `<script type="application/ld+json">` block.
 *
 * JSON escapes quotes but NOT `<`, `>` or `/`, so skin-derived text embedded in the JSON can
 * inject raw HTML markup into the raw-text `<script>` context. Two passes make it inert while
 * staying valid JSON (both escapes are decoded away on parse):
 *  1. `</` → `<\/` neutralises the only sequence that can actually close the element early —
 *     a literal `</script>` in the tokenizer.
 *  2. every remaining bare `<` (i.e. an opening tag like `<script>` or `<img>`) → `<`,
 *     so no attacker-controlled start-tag survives verbatim either. The negative lookahead
 *     leaves the `<` of the `<\/` produced in pass 1 untouched.
 */
fun HEAD.jsonLd(data: JsonObject) {
    val safe = data.toString()
        .replace("</", "<\\/")
        .replace(Regex("<(?!\\\\)")) { "\\u003c" }
    script(type = "application/ld+json") {
        unsafe { +safe }
    }
}

fun HEAD.seoMeta(
    pageTitle: String,
    description: String,
    canonical: String,
    imageUrl: String?,
) {
    title(pageTitle)
    meta(name = "description", content = description)
    link(rel = "canonical", href = canonical)
    meta(content = pageTitle) { attributes["property"] = "og:title" }
    meta(content = description) { attributes["property"] = "og:description" }
    meta(content = canonical) { attributes["property"] = "og:url" }
    meta(content = "website") { attributes["property"] = "og:type" }
    meta(content = "CS2SkinFlip") { attributes["property"] = "og:site_name" }
    if (imageUrl != null) {
        meta(content = imageUrl) { attributes["property"] = "og:image" }
    }
    meta(name = "twitter:card", content = "summary")
}

fun buildPage(
    headBlock: HEAD.() -> Unit,
    bodyBlock: MAIN.() -> Unit,
): HTML.() -> Unit = {
    attributes["lang"] = "en"
    head {
        meta(charset = "utf-8")
        meta(name = "viewport", content = "width=device-width, initial-scale=1")
        style { unsafe { +STYLES } }
        headBlock()
    }
    body {
        main { bodyBlock() }
    }
}
