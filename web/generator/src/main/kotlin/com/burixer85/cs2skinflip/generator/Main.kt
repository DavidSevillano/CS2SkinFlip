package com.burixer85.cs2skinflip.generator

import com.burixer85.cs2skinflip.shared.api.Cs2ApiClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.system.exitProcess

fun main(): Unit = runBlocking {
    val config = try {
        GeneratorConfig.fromEnv(System.getenv())
    } catch (error: IllegalStateException) {
        System.err.println(error.message)
        exitProcess(1)
    }

    val client = HttpClient(CIO) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        install(HttpTimeout) {
            // The export is ~6 MB; give it room.
            requestTimeoutMillis = 120_000
            connectTimeoutMillis = 30_000
            socketTimeoutMillis = 120_000
        }
    }

    client.use {
        SiteGenerator(Cs2ApiClient(it, config.backendUrl), config).generate()
    }
}
