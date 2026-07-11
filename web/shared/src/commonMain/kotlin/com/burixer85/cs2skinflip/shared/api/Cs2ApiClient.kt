package com.burixer85.cs2skinflip.shared.api

import com.burixer85.cs2skinflip.shared.model.PriceHistoryPoint
import com.burixer85.cs2skinflip.shared.model.Skin
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.encodeURLPathPart
import io.ktor.http.isSuccess

/**
 * Read-only client for the CS2SkinFlip backend. The static site generator is the only
 * consumer; the database is never touched directly.
 */
class Cs2ApiClient(
    private val client: HttpClient,
    private val baseUrl: String,
) {
    /** Every priced skin, ordered by value descending. One call per site regeneration. */
    suspend fun fetchExport(): List<Skin> {
        val response: HttpResponse = client.get("$baseUrl/skins/export")
        // Without the export there is nothing to render, so fail loudly.
        check(response.status.isSuccess()) { "GET /skins/export failed: ${response.status}" }
        return response.body()
    }

    /**
     * Price history for one skin. Returns an empty list on failure: a missing chart must
     * never abort a 20k-page build.
     */
    suspend fun fetchPriceHistory(skinId: String, range: String = "30d"): List<PriceHistoryPoint> {
        val encoded = skinId.encodeURLPathPart()
        val response: HttpResponse = client.get("$baseUrl/skins/$encoded/price-history?range=$range")
        if (!response.status.isSuccess()) return emptyList()
        return response.body()
    }
}
