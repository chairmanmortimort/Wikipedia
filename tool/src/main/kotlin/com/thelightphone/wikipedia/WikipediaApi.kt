package com.thelightphone.wikipedia

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import java.net.URLEncoder

private const val BASE_REST = "https://en.wikipedia.org/api/rest_v1"
private const val BASE_ACTION = "https://en.wikipedia.org/w/api.php"

internal class WikipediaApi {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(json)
        }
        expectSuccess = true
    }

    /**
     * Search Wikipedia for [query].
     * Returns up to 20 results from the MediaWiki Action API search endpoint.
     */
    suspend fun search(query: String, offset: Int = 0): Result<List<WikiSearchResult>> = runCatching {
        val response = client.get(BASE_ACTION) {
            url {
                parameter("action", "query")
                parameter("list", "search")
                parameter("srsearch", query.trim())
                parameter("srlimit", "20")
                parameter("sroffset", offset.toString())
                parameter("format", "json")
            }
            timeout {
                requestTimeoutMillis = 15_000L
                connectTimeoutMillis = 10_000L
            }
        }
        val searchResponse: WikiSearchResponse = response.body()
        searchResponse.results
    }

    /**
     * Fetch a summary for a Wikipedia article by title.
     * Uses the REST API summary endpoint for a lightweight response.
     */
    suspend fun fetchSummary(title: String): Result<WikiSummaryResponse> = runCatching {
        val encoded = URLEncoder.encode(title.trim(), Charsets.UTF_8.name())
        val response = client.get("$BASE_REST/page/summary/$encoded") {
            url {
                parameter("redirect", "1")
            }
            timeout {
                requestTimeoutMillis = 15_000L
                connectTimeoutMillis = 10_000L
            }
        }
        response.body()
    }

    /**
     * Fetch the full plain-text extract of an article.
     * Uses the MediaWiki Action API with explaintext=1.
     */
    suspend fun fetchExtract(title: String): Result<String> = runCatching {
        val response = client.get(BASE_ACTION) {
            url {
                parameter("action", "query")
                parameter("prop", "extracts")
                parameter("explaintext", "1")
                parameter("titles", title.trim())
                parameter("format", "json")
            }
            timeout {
                requestTimeoutMillis = 15_000L
                connectTimeoutMillis = 10_000L
            }
        }
        val text = response.bodyAsText()
        val extractResponse: WikiExtractResponse = json.decodeFromString(text)
        val pages = extractResponse.query?.pages ?: emptyMap()
        pages.values.firstOrNull()?.extract ?: ""
    }

    /**
     * Fetch internal wiki links for an article.
     * These are used to allow navigation to other Wikipedia articles from within an article view.
     */
    suspend fun fetchLinks(title: String, limit: Int = 50): Result<List<String>> = runCatching {
        val response = client.get(BASE_ACTION) {
            url {
                parameter("action", "query")
                parameter("prop", "links")
                parameter("titles", title.trim())
                parameter("format", "json")
                parameter("pllimit", limit.toString())
            }
            timeout {
                requestTimeoutMillis = 15_000L
                connectTimeoutMillis = 10_000L
            }
        }
        val linksResponse: WikiLinksResponse = response.body()
        val pages = linksResponse.query?.pages ?: emptyMap()
        pages.values.firstOrNull()?.links?.mapNotNull { link ->
            // Only return main namespace (ns=0) links — these are actual article links
            if (link.ns == 0) link.title else null
        } ?: emptyList()
    }

    /**
     * Fetch a random article title from the main namespace.
     */
    suspend fun fetchRandomTitle(): Result<String> = runCatching {
        val response = client.get(BASE_ACTION) {
            url {
                parameter("action", "query")
                parameter("list", "random")
                parameter("rnnamespace", "0")
                parameter("format", "json")
            }
            timeout {
                requestTimeoutMillis = 15_000L
                connectTimeoutMillis = 10_000L
            }
        }
        val randomResponse: WikiRandomResponse = response.body()
        randomResponse.query?.random?.firstOrNull()?.title ?: ""
    }

    /**
     * Fetch "On this day" events from Wikipedia.
     * Uses the REST feed endpoint /feed/onthisday/{type}/{MM}/{DD} for the
     * current date. Each event carries a free-text description (what actually
     * happened) plus zero or more linked pages. We keep the events intact
     * (rather than flattening to bare page titles) so the UI can show the
     * event text and offer the linked pages as tappable articles.
     * @param type One of: "created", "births", "deaths", "events".
     */
    suspend fun fetchOnThisDay(type: String = "events", month: Int? = null, day: Int? = null): Result<List<WikiOnThisDayEvent>> = runCatching {
        val now = java.time.LocalDate.now()
        val mm = (month ?: now.monthValue).toString().padStart(2, '0')
        val dd = (day ?: now.dayOfMonth).toString().padStart(2, '0')
        val response = client.get("$BASE_REST/feed/onthisday/$type/$mm/$dd") {
            timeout {
                requestTimeoutMillis = 15_000L
                connectTimeoutMillis = 10_000L
            }
        }
        val parsed: WikiOnThisDayResponse = response.body()
        parsed.events
    }

    fun close() {
        client.close()
    }
}
