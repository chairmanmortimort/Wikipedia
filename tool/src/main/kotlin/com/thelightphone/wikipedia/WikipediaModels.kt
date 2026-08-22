package com.thelightphone.wikipedia

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Data models for the Wikipedia REST API and MediaWiki Action API.
 * 
 * The REST API (api/rest_v1) is used for summaries and random articles.
 * The MediaWiki Action API (w/api.php) is used for full article text extracts
 * and search, because it supports plain-text extracts and more flexible queries.
 */

// === REST API: Page Summary ===
// https://en.wikipedia.org/api/rest_v1/page/summary/{title}
// Returns a compact JSON summary with description, extract, and thumbnail.

@Serializable
data class WikiSummaryResponse(
    val type: String = "",
    val title: String = "",
    @SerialName("displaytitle") val displayTitle: String = "",
    val description: String? = null,
    val extract: String = "",
    @SerialName("extract_html") val extractHtml: String = "",
    val thumbnail: WikiThumbnail? = null,
    @SerialName("originalimage") val originalImage: WikiThumbnail? = null,
    @SerialName("content_urls") val contentUrls: WikiContentUrls? = null,
    @SerialName("lang") val lang: String = "en",
) {
    val descriptionText: String get() = description ?: ""
    val thumbnailUrl: String? get() = thumbnail?.source
    val originalUrl: String? get() = originalImage?.source
}

@Serializable
data class WikiThumbnail(
    val source: String = "",
    val width: Int = 0,
    val height: Int = 0,
)

@Serializable
data class WikiContentUrls(
    val desktop: WikiContentUrl? = null,
    val mobile: WikiContentUrl? = null,
)

@Serializable
data class WikiContentUrl(
    val page: String = "",
    val revisions: String? = null,
    val edit: String? = null,
    val talk: String? = null,
)

// === MediaWiki Action API: Search ===
// https://en.wikipedia.org/w/api.php?action=query&list=search&format=json
// Returns search results with title, snippet, and pageid.

@Serializable
data class WikiSearchResponse(
    val query: WikiSearchQuery? = null,
    @SerialName("continue") val continueInfo: WikiContinue? = null,
) {
    val results: List<WikiSearchResult> get() = query?.search ?: emptyList()
    val hasMore: Boolean get() = continueInfo != null
}

@Serializable
data class WikiSearchQuery(
    @SerialName("searchinfo") val searchInfo: WikiSearchInfo? = null,
    val search: List<WikiSearchResult> = emptyList(),
)

@Serializable
data class WikiSearchInfo(
    val totalhits: Int = 0,
)

@Serializable
data class WikiSearchResult(
    val title: String = "",
    val pageid: Int = 0,
    val size: Int = 0,
    val wordcount: Int = 0,
    val snippet: String = "",
    val timestamp: String = "",
)

@Serializable
data class WikiContinue(
    @SerialName("sroffset") val offset: Int = 0,
    val continueToken: String = "",
)

// === MediaWiki Action API: Extract (full article text) ===
// https://en.wikipedia.org/w/api.php?action=query&prop=extracts&explaintext=1&titles={title}&format=json
// Returns plain text article content.

@Serializable
data class WikiExtractResponse(
    val query: WikiExtractQuery? = null,
)

@Serializable
data class WikiExtractQuery(
    val pages: Map<String, WikiExtractPage> = emptyMap(),
)

@Serializable
data class WikiExtractPage(
    val pageid: Int = 0,
    val title: String = "",
    val extract: String = "",
)

// === MediaWiki Action API: Links (internal links in an article) ===
// https://en.wikipedia.org/w/api.php?action=query&prop=links&titles={title}&format=json
// Returns the internal wiki links for an article.

@Serializable
data class WikiLinksResponse(
    val query: WikiLinksQuery? = null,
)

@Serializable
data class WikiLinksQuery(
    val pages: Map<String, WikiLinksPage> = emptyMap(),
)

@Serializable
data class WikiLinksPage(
    val pageid: Int = 0,
    val title: String = "",
    val links: List<WikiLink> = emptyList(),
)

@Serializable
data class WikiLink(
    val ns: Int = 0,
    val title: String = "",
    val wildcard: String? = null,
)

// === MediaWiki Action API: Raw wikitext (table detection) ===
// https://en.wikipedia.org/w/api.php?action=query&prop=revisions&rvprop=content&titles={title}&format=json
// Returns the raw wikitext. We only inspect it for table markers ({| ... |}).

@Serializable
data class WikiRawExtractResponse(
    val query: WikiRawExtractQuery? = null,
)

@Serializable
data class WikiRawExtractQuery(
    val pages: Map<String, WikiRawExtractPage> = emptyMap(),
)

@Serializable
data class WikiRawExtractPage(
    val pageid: Int = 0,
    val title: String = "",
    val revisions: List<WikiRawRevision> = emptyList(),
)

@Serializable
data class WikiRawRevision(
    val slots: WikiRawSlots? = null,
)

@Serializable
data class WikiRawSlots(
    val main: WikiRawMainSlot? = null,
)

@Serializable
data class WikiRawMainSlot(
    @SerialName("*") val content: String = "",
)

// === Domain model ===
// A combined representation of a Wikipedia article for display on LP3.

data class WikiArticle(
    val title: String,
    val description: String? = null,
    val extract: String = "",
    val thumbnailUrl: String? = null,
    val originalImageUrl: String? = null,
    val links: List<String> = emptyList(),
    val hasTable: Boolean = false,
    val tables: List<WikiTable> = emptyList(),
) {
    val displayTitle: String get() = title
}

/** A single parsed row from a Wikipedia wikitext table (e.g. a filmography entry). */
data class WikiTableRow(
    val title: String = "",
    val meta: String = "",
)

/** A Wikipedia wikitext table, parsed so its rows can render on LP3.
 *  The plain-text extract silently drops these, so we parse the raw wikitext instead. */
data class WikiTable(
    val heading: String? = null,
    val rows: List<WikiTableRow> = emptyList(),
)

// === REST API: Random article ===
// https://en.wikipedia.org/w/api.php?action=query&list=random&rnnamespace=0
// Returns a single random page title from the main namespace.

@Serializable
data class WikiRandomResponse(
    val query: WikiRandomQuery? = null,
)

@Serializable
data class WikiRandomQuery(
    val random: List<WikiRandomResult> = emptyList(),
)

@Serializable
data class WikiRandomResult(
    val id: Int = 0,
    val title: String = "",
    @SerialName("ns") val namespace: Int = 0,
)

// === REST API: On This Day ===
// https://en.wikipedia.org/api/rest_v1/feed/onthisday/{type}/{MM}/{DD}
// Returns historical events for the current date. Each event has a free-text
// description and a list of linked pages (articles).

@Serializable
data class WikiOnThisDayResponse(
    @SerialName("events") val events: List<WikiOnThisDayEvent> = emptyList(),
)

@Serializable
data class WikiOnThisDayEvent(
    @SerialName("text") val text: String = "",
    @SerialName("pages") val pages: List<WikiOnThisDayPage> = emptyList(),
)

@Serializable
data class WikiOnThisDayPage(
    @SerialName("title") val title: String = "",
    @SerialName("text") val text: String? = null,
)
