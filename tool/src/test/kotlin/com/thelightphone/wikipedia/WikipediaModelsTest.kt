package com.thelightphone.wikipedia

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Tests for the Wikipedia JSON model parsing. These exercise the real shapes
// returned by the MediaWiki Action API and REST feed endpoints, with no
// network or Android dependency — mirroring the parsing tests in the SDK's
// example tools (e.g. weather, authenticator).
private val json = Json { ignoreUnknownKeys = true }

class WikipediaModelsTest {

    @Test
    fun `decodes On This Day feed with event text and linked pages`() {
        // Shape returned by https://en.wikipedia.org/api/rest_v1/feed/onthisday/events/MM/DD
        val response = json.decodeFromString<WikiOnThisDayResponse>(
            """
            {
              "events": [
                {
                  "text": "1999 — The Vermont State House is struck by lightning.",
                  "pages": [
                    { "title": "Vermont State House", "text": "..." },
                    { "title": "Lightning" }
                  ]
                },
                {
                  "text": "A second event with no linked pages.",
                  "pages": []
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(2, response.events.size)
        val first = response.events[0]
        assertEquals(
            "1999 — The Vermont State House is struck by lightning.",
            first.text,
        )
        assertEquals(listOf("Vermont State House", "Lightning"), first.pages.map { it.title })
        assertTrue(response.events[1].pages.isEmpty())
    }

    @Test
    fun `decodes search response and exposes results plus hasMore`() {
        // Shape returned by action=query&list=search
        val response = json.decodeFromString<WikiSearchResponse>(
            """
            {
              "batchcomplete": "",
              "continue": { "sroffset": 20, "continue": "???" },
              "query": {
                "searchinfo": { "totalhits": 1234 },
                "search": [
                  { "title": "Kotlin", "pageid": 1, "snippet": "Kotlin is..." },
                  { "title": "Java", "pageid": 2, "snippet": "Java is..." }
                ]
              }
            }
            """.trimIndent(),
        )

        assertEquals(2, response.results.size)
        assertEquals("Kotlin", response.results[0].title)
        assertEquals("Java", response.results[1].title)
        assertTrue(response.hasMore)
    }

    @Test
    fun `search response with no continue token reports no more pages`() {
        val response = json.decodeFromString<WikiSearchResponse>(
            """
            {
              "query": {
                "search": [ { "title": "Solo" } ]
              }
            }
            """.trimIndent(),
        )

        assertEquals(1, response.results.size)
        assertFalse(response.hasMore)
    }

    @Test
    fun `decodes extract response and pulls first page text`() {
        // Shape returned by action=query&prop=extracts&explaintext=1
        val response = json.decodeFromString<WikiExtractResponse>(
            """
            {
              "query": {
                "pages": {
                  "123": {
                    "pageid": 123,
                    "title": "Light Phone",
                    "extract": "The Light Phone is a minimalist phone."
                  }
                }
              }
            }
            """.trimIndent(),
        )

        val pages = response.query?.pages ?: emptyMap()
        assertEquals(1, pages.size)
        assertEquals(
            "The Light Phone is a minimalist phone.",
            pages.values.firstOrNull()?.extract,
        )
    }

    @Test
    fun `extract response with missing pages yields empty extract`() {
        val response = json.decodeFromString<WikiExtractResponse>(
            """{ "query": { "pages": {} } }""",
        )

        val pages = response.query?.pages ?: emptyMap()
        assertEquals("", pages.values.firstOrNull()?.extract ?: "")
    }

    @Test
    fun `decodes links response and keeps only main-namespace titles`() {
        // Shape returned by action=query&prop=links. ns=0 are articles;
        // ns=14 (Category) and ns=15 (Category talk) should be filtered.
        val response = json.decodeFromString<WikiLinksResponse>(
            """
            {
              "query": {
                "pages": {
                  "1": {
                    "pageid": 1,
                    "title": "Light Phone",
                    "links": [
                      { "ns": 0, "title": "Minimalism" },
                      { "ns": 0, "title": "Smartphone" },
                      { "ns": 14, "title": "Category:Phones" },
                      { "ns": 15, "title": "Category talk:Phones" }
                    ]
                  }
                }
              }
            }
            """.trimIndent(),
        )

        val links = response.query?.pages?.values?.firstOrNull()?.links ?: emptyList()
        val mainNamespaceTitles = links.filter { it.ns == 0 }.map { it.title }
        assertEquals(listOf("Minimalism", "Smartphone"), mainNamespaceTitles)
        assertEquals(2, mainNamespaceTitles.size)
    }

    @Test
    fun `decodes summary response with optional fields`() {
        // Shape returned by api/rest_v1/page/summary/{title}
        val response = json.decodeFromString<WikiSummaryResponse>(
            """
            {
              "type": "standard",
              "title": "Light Phone",
              "displaytitle": "Light Phone",
              "description": "Minimalist phone",
              "extract": "The Light Phone is a minimalist phone.",
              "thumbnail": { "source": "https://example.com/t.png", "width": 50, "height": 50 },
              "content_urls": {
                "desktop": { "page": "https://en.wikipedia.org/wiki/Light_Phone" }
              },
              "lang": "en"
            }
            """.trimIndent(),
        )

        assertEquals("Light Phone", response.title)
        assertEquals("Minimalist phone", response.descriptionText)
        assertEquals("https://example.com/t.png", response.thumbnailUrl)
        assertEquals("https://en.wikipedia.org/wiki/Light_Phone", response.contentUrls?.desktop?.page)
    }
}
