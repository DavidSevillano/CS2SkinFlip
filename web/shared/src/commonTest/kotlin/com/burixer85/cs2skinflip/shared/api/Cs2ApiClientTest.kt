package com.burixer85.cs2skinflip.shared.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private class Recorder {
    val urls = mutableListOf<String>()
}

private fun clientReturning(
    body: String,
    status: HttpStatusCode = HttpStatusCode.OK,
): Pair<HttpClient, Recorder> {
    val recorder = Recorder()
    val engine = MockEngine { request ->
        recorder.urls += request.url.toString()
        respond(
            content = body,
            status = status,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )
    }
    val client = HttpClient(engine) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }
    return client to recorder
}

class FetchExportTest {
    @Test
    fun parsesTheExportPayload() = runTest {
        val (client, recorder) = clientReturning(
            """
            [{"id":"ak","marketHashName":"AK-47 | Redline (Field-Tested)","weapon":"AK-47",
              "rarity":"Classified","iconUrl":"https://cdn/x.png","skinportPrice":12.5,
              "csgoMarketPrice":null,"waxpeerPrice":11.0,"lowestPrice":11.0,
              "priceChange24h":3.2,"updatedAt":"2026-07-01T00:00:00.000Z"}]
            """.trimIndent(),
        )

        val skins = Cs2ApiClient(client, "https://api.example.com").fetchExport()

        assertEquals(1, skins.size)
        assertEquals("ak", skins[0].id)
        assertEquals(11.0, skins[0].lowestPrice)
        assertEquals(null, skins[0].csgoMarketPrice)
        assertEquals("https://api.example.com/skins/export", recorder.urls.single())
    }

    @Test
    fun toleratesUnknownFields() = runTest {
        val (client, _) = clientReturning(
            """[{"id":"ak","marketHashName":"n","weapon":"w","rarity":"r","iconUrl":"i",
                 "updatedAt":"2026-07-01T00:00:00.000Z","somethingNew":42}]""",
        )

        val skins = Cs2ApiClient(client, "https://api.example.com").fetchExport()

        assertEquals("ak", skins.single().id)
    }

    @Test
    fun throwsOnAnErrorResponseBecauseTheBuildIsWorthlessWithoutIt() = runTest {
        val (client, _) = clientReturning("boom", HttpStatusCode.InternalServerError)

        assertFailsWith<IllegalStateException> {
            Cs2ApiClient(client, "https://api.example.com").fetchExport()
        }
    }
}

class FetchPriceHistoryTest {
    @Test
    fun parsesHistoryAndRequestsTheGivenRange() = runTest {
        val (client, recorder) = clientReturning(
            """[{"price":10.5,"timestamp":"2026-06-01T00:00:00.000Z","source":"steam"}]""",
        )

        val history = Cs2ApiClient(client, "https://api.example.com").fetchPriceHistory("ak", "30d")

        assertEquals(1, history.size)
        assertEquals(10.5, history[0].price)
        assertTrue(recorder.urls.single().endsWith("/skins/ak/price-history?range=30d"))
    }

    @Test
    fun defaultsToThirtyDayRange() = runTest {
        val (client, recorder) = clientReturning("[]")

        Cs2ApiClient(client, "https://api.example.com").fetchPriceHistory("ak")

        assertTrue(recorder.urls.single().contains("range=30d"))
    }

    @Test
    fun returnsEmptyListOnAnErrorResponseSoOneBadChartCannotFailTheBuild() = runTest {
        val (client, _) = clientReturning("boom", HttpStatusCode.InternalServerError)

        val history = Cs2ApiClient(client, "https://api.example.com").fetchPriceHistory("ak", "30d")

        assertEquals(emptyList(), history)
    }

    @Test
    fun urlEncodesTheSkinId() = runTest {
        val (client, recorder) = clientReturning("[]")

        Cs2ApiClient(client, "https://api.example.com").fetchPriceHistory("a/b", "30d")

        assertTrue(recorder.urls.single().contains("a%2Fb"))
    }
}
