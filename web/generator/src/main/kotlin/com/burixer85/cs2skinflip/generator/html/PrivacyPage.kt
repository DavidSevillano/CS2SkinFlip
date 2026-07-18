package com.burixer85.cs2skinflip.generator.html

import kotlinx.html.a
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.html
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.section
import kotlinx.html.stream.createHTML
import kotlinx.html.ul

private const val CONTACT_EMAIL = "burideveloper@gmail.com"

fun renderPrivacyPage(siteUrl: String): String {
    val title = "Privacy Policy | CS2SkinFlip"
    val description = "How CS2SkinFlip collects, uses and stores your data."

    val page = buildPage(
        headBlock = { seoMeta(title, description, "$siteUrl/privacy/", null) },
        bodyBlock = {
            a(href = "/") { +"CS2SkinFlip" }
            h1 { +"Privacy Policy" }
            p { +"Last updated: 18 July 2026" }

            section {
                h2 { +"Who we are" }
                p {
                    +("CS2SkinFlip is a price-tracking service for Counter-Strike 2 skins, available " +
                        "as an Android application and this website.")
                }
            }

            section {
                h2 { +"Data we collect" }
                ul {
                    li {
                        +("Steam account data. If you sign in to the app, we store your Steam ID, " +
                            "username and avatar URL so we can associate your watchlist, alerts and " +
                            "portfolio with your account. We never see or store your Steam password.")
                    }
                    li {
                        +("Session cookie. After you sign in, we set a technical, httpOnly session " +
                            "cookie (valid for up to 7 days) so you stay signed in between visits. It is " +
                            "strictly necessary for sign-in to work — it is not used for tracking or " +
                            "advertising, and it is deleted when you sign out or delete your account.")
                    }
                    li {
                        +("Notification token. If you enable price alerts, we store a Firebase Cloud " +
                            "Messaging token so we can send you a push notification.")
                    }
                    li { +"Your tracked items: watchlist, alerts, portfolio and the target prices you set." }
                    li {
                        +("Purchase information. If you buy the one-time premium unlock, Google Play " +
                            "processes your payment directly — we never see or store your payment details. " +
                            "We receive only a purchase token from Google Play, which we verify with Google to " +
                            "confirm the purchase and mark your account as premium.")
                    }
                    li { +"Analytics. The Android app uses Firebase Analytics to record anonymous, aggregated usage events." }
                    li { +"Advertising. The Android app shows ads served by Google AdMob, which may collect a device advertising identifier." }
                }
            }

            section {
                h2 { +"What this website collects" }
                p {
                    +("This website requires no account, runs no JavaScript, and sets no advertising " +
                        "or tracking cookies. Skin prices shown here are public market data.")
                }
            }

            section {
                h2 { +"How we use your data" }
                p {
                    +("Solely to operate the service: to show your watchlist, evaluate your price " +
                        "alerts and send the notifications you asked for. We do not sell your data " +
                        "or share it with third parties other than the infrastructure providers that " +
                        "run the service (Google Firebase, Neon, Upstash, Render and Cloudflare).")
                }
            }

            section {
                h2 { +"Deleting your data" }
                p {
                    +"Email "
                    a(href = "mailto:$CONTACT_EMAIL") { +CONTACT_EMAIL }
                    +" from the address associated with your account and we will delete your account and all associated data."
                }
            }

            section {
                h2 { +"Contact" }
                p {
                    +"Questions? Email "
                    a(href = "mailto:$CONTACT_EMAIL") { +CONTACT_EMAIL }
                    +"."
                }
            }
        },
    )

    return DOCTYPE + createHTML().html { page() }
}
