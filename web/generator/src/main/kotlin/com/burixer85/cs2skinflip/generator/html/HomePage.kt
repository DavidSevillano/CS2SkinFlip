package com.burixer85.cs2skinflip.generator.html

import com.burixer85.cs2skinflip.shared.format.formatChange
import com.burixer85.cs2skinflip.shared.format.formatPrice
import com.burixer85.cs2skinflip.shared.model.Skin
import kotlinx.html.FlowContent
import kotlinx.html.a
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.html
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.section
import kotlinx.html.span
import kotlinx.html.stream.createHTML
import kotlinx.html.ul

private fun FlowContent.moverList(skins: List<Skin>) {
    if (skins.isEmpty()) {
        p { +"No price movements recorded in the last 24 hours." }
        return
    }
    ul(classes = "cards") {
        skins.forEach { skin ->
            li {
                a(href = "/skin/${skin.id}/") {
                    span { +skin.marketHashName }
                    span { +formatPrice(skin.lowestPrice) }
                    formatChange(skin.priceChange24h)?.let { change ->
                        span(classes = if ((skin.priceChange24h ?: 0.0) > 0.0) "up" else "down") { +change }
                    }
                }
            }
        }
    }
}

fun renderHomePage(siteUrl: String, rising: List<Skin>, falling: List<Skin>): String {
    val title = "CS2 Skin Prices — Live Price Comparison | CS2SkinFlip"
    val description =
        "Compare live CS2 skin prices across Skinport, CS:GO Market and Waxpeer. " +
            "Free price history and price alerts for thousands of skins."

    val page = buildPage(
        headBlock = { seoMeta(title, description, "$siteUrl/", null) },
        bodyBlock = {
            h1 { +"Live CS2 skin prices" }
            p {
                +("We compare prices for thousands of CS2 skins across Skinport, CS:GO Market and " +
                    "Waxpeer. Track any skin and get a push notification when it hits your target price.")
            }

            section {
                h2 { +"Biggest gainers (24h)" }
                moverList(rising)
            }

            section {
                h2 { +"Biggest losers (24h)" }
                moverList(falling)
            }

            section(classes = "cta") {
                h2 { +"Track your favourite skins" }
                p { +"Set a target price and get notified the moment a skin drops below it. Free on Android." }
                a(href = PLAY_STORE_URL, classes = "btn") { +"Get it on Google Play" }
            }
        },
    )

    return DOCTYPE + createHTML().html { page() }
}
