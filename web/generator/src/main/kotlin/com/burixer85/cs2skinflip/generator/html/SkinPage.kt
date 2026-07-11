package com.burixer85.cs2skinflip.generator.html

import com.burixer85.cs2skinflip.shared.format.formatChange
import com.burixer85.cs2skinflip.shared.format.formatPrice
import com.burixer85.cs2skinflip.shared.format.isStatTrak
import com.burixer85.cs2skinflip.shared.format.marketplaceEntries
import com.burixer85.cs2skinflip.shared.format.parseWear
import com.burixer85.cs2skinflip.shared.model.PriceHistoryPoint
import com.burixer85.cs2skinflip.shared.model.Skin
import com.burixer85.cs2skinflip.shared.seo.breadcrumbJsonLd
import com.burixer85.cs2skinflip.shared.seo.canonicalUrl
import com.burixer85.cs2skinflip.shared.seo.metaDescription
import com.burixer85.cs2skinflip.shared.seo.pageTitle
import com.burixer85.cs2skinflip.shared.seo.productJsonLd
import kotlinx.html.a
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.html
import kotlinx.html.img
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.section
import kotlinx.html.span
import kotlinx.html.stream.createHTML
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.tr
import kotlinx.html.ul

const val PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=com.burixer85.cs2skinflip"

private fun day(timestamp: String): String = timestamp.take(10)

fun renderSkinPage(
    siteUrl: String,
    skin: Skin,
    history: List<PriceHistoryPoint>,
    related: List<Skin>,
): String {
    val markets = marketplaceEntries(skin)
    val cheapest = markets.firstOrNull()
    val canonical = canonicalUrl(siteUrl, skin.id)
    val change = formatChange(skin.priceChange24h)
    val wear = parseWear(skin.marketHashName)

    val page = buildPage(
        headBlock = {
            seoMeta(pageTitle(skin), metaDescription(skin), canonical, skin.iconUrl)
            jsonLd(productJsonLd(skin))
            jsonLd(breadcrumbJsonLd(siteUrl, skin))
        },
        bodyBlock = {
            a(href = "/") { +"CS2SkinFlip" }

            h1 { +"${skin.marketHashName} Price" }

            if (skin.iconUrl.isNotBlank()) {
                img(alt = skin.marketHashName, src = skin.iconUrl, classes = "skin") {
                    attributes["loading"] = "lazy"
                }
            }

            p {
                span(classes = "price") { +formatPrice(skin.lowestPrice) }
                if (change != null) {
                    +" "
                    span(classes = if ((skin.priceChange24h ?: 0.0) > 0.0) "up" else "down") {
                        +"$change (24h)"
                    }
                }
            }

            p {
                +if (cheapest != null) {
                    "${skin.marketHashName} currently sells for as little as " +
                        "${formatPrice(cheapest.price)} on ${cheapest.name}. Prices are compared " +
                        "across ${markets.size} marketplace${if (markets.size == 1) "" else "s"} " +
                        "and refreshed daily."
                } else {
                    "No marketplace is currently quoting a price for ${skin.marketHashName}."
                }
            }

            section {
                h2 { +"Details" }
                table {
                    tbody {
                        tr { th { +"Weapon" }; td { +skin.weapon } }
                        tr { th { +"Rarity" }; td { +skin.rarity } }
                        if (wear != null) {
                            tr { th { +"Wear" }; td { +wear } }
                        }
                        tr { th { +"StatTrak" }; td { +if (isStatTrak(skin.marketHashName)) "Yes" else "No" } }
                    }
                }
            }

            section {
                h2 { +"Price comparison by marketplace" }
                if (markets.isEmpty()) {
                    p { +"No marketplace is currently quoting a price for this skin." }
                } else {
                    table {
                        thead {
                            tr {
                                th { +"Marketplace" }
                                th(classes = "num") { +"Price (USD)" }
                            }
                        }
                        tbody {
                            markets.forEachIndexed { index, entry ->
                                tr {
                                    td {
                                        +entry.name
                                        if (index == 0) +" (cheapest)"
                                    }
                                    td(classes = "num") { +formatPrice(entry.price) }
                                }
                            }
                        }
                    }
                }
            }

            if (history.isNotEmpty()) {
                val low = history.minOf { it.price }
                val high = history.maxOf { it.price }
                section {
                    h2 { +"30-day price history" }
                    p {
                        +(
                            "Over the last 30 days, ${skin.marketHashName} ranged between " +
                                "${formatPrice(low)} and ${formatPrice(high)}."
                            )
                    }
                    table {
                        thead {
                            tr {
                                th { +"Date" }
                                th(classes = "num") { +"Lowest price" }
                            }
                        }
                        tbody {
                            history.asReversed().take(30).forEach { point ->
                                tr {
                                    td { +day(point.timestamp) }
                                    td(classes = "num") { +formatPrice(point.price) }
                                }
                            }
                        }
                    }
                }
            }

            section(classes = "cta") {
                h2 { +"Track ${skin.marketHashName} in the app" }
                p { +"Get a push notification the moment this skin drops below your target price. Free on Android." }
                a(href = PLAY_STORE_URL, classes = "btn") { +"Get it on Google Play" }
            }

            if (related.isNotEmpty()) {
                section {
                    h2 { +"More ${skin.weapon} skins" }
                    ul(classes = "cards") {
                        related.forEach { other ->
                            li {
                                a(href = "/skin/${other.id}/") {
                                    span { +other.marketHashName }
                                    span { +formatPrice(other.lowestPrice) }
                                }
                            }
                        }
                    }
                }
            }
        },
    )

    return DOCTYPE + createHTML().html { page() }
}
