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
     * Fetch the raw wikitext of an article (used to detect tables/lists that the
     * plain-text extract silently drops). We only need to know whether a table
     * marker is present, so we don't fully parse the wikitext.
     */
    suspend fun fetchRawExtract(title: String): Result<String> = runCatching {
        val response = client.get(BASE_ACTION) {
            url {
                parameter("action", "query")
                parameter("prop", "revisions")
                parameter("rvprop", "content")
                parameter("rvslots", "main")
                parameter("titles", title.trim())
                parameter("format", "json")
            }
            timeout {
                requestTimeoutMillis = 15_000L
                connectTimeoutMillis = 10_000L
            }
        }
        val text = response.bodyAsText()
        val rawResponse: WikiRawExtractResponse = json.decodeFromString(text)
        rawResponse.query?.pages?.values?.firstOrNull()
            ?.revisions?.firstOrNull()?.slots?.main?.content ?: ""
    }

    /**
     * Parse the raw wikitext into a list of [WikiTable]s so tables/lists that the
     * plain-text extract drops (filmographies, discographies, etc.) can be rendered.
     * Hardened: any malformed cell/table returns an empty list rather than crashing
     * the article view.
     */
    fun parseTables(rawWikitext: String): List<WikiTable> {
        return runCatching { parseWikitextTables(rawWikitext) }.getOrDefault(emptyList())
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
     * happened) plus zero or more linked pages.
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

// Templates whose rendered text we drop entirely (footnotes, anchors, etc.).
private val DROP_TEMPLATE_NAMES = setOf(
    "efn", "efn-ua", "note", "tooltip", "vanchor", "visible anchor", "sup", "clarify",
)

/** Clean a single wikitext table cell into plain display text. */
private fun cleanWikiCell(cell: String): String {
    var c = cell.trim()
    // Drop <ref>…</ref> and self-closing <ref/>
    c = c.replace(Regex("<ref[^>]*>.*?</ref>", RegexOption.DOT_MATCHES_ALL), "")
    c = c.replace(Regex("<ref[^>]*/?>"), "")
    // [[a|b]] -> b, [[a]] -> a  (manual scan — no regex, handles nesting)
    c = stripWikiLinks(c)
    // {{...}} templates (manual brace-counting; drops footnotes, keeps display value)
    c = stripWikiTemplates(c)
    // Strip remaining HTML tags
    c = c.replace(Regex("<[^>]+>"), "")
    // Strip '' italics / ''' bold
    c = c.replace(Regex("'+"), "")
    // Strip table attribute prefixes: scope="row" | , scope="col" | , rowspan/colspan
    c = c.replace(Regex("^\\s*scope=\"[^\"]*\"\\s*(?:colspan=\"\\d+\"\\s*)?\\|\\s*"), "")
    c = c.replace(Regex("^\\s*rowspan=\"\\d+\"\\s*(?:colspan=\"\\d+\"\\s*)?\\|\\s*"), "")
    c = c.replace(Regex("^\\s*colspan=\"\\d+\"\\s*\\|\\s*"), "")
    return c.replace(Regex("\\s+"), " ").trim()
}

/** Replace [[wikilinks]] with their display text (text after the last pipe). */
private fun stripWikiLinks(s: String): String {
    val out = StringBuilder()
    var i = 0
    while (i < s.length) {
        if (s.startsWith("[[", i)) {
            var depth = 1
            var j = i + 2
            while (j < s.length && depth > 0) {
                if (s.startsWith("[[", j)) { depth++; j += 2 }
                else if (s.startsWith("]]", j)) { depth--; j += 2 }
                else j++
            }
            val end = if (j >= 2) j - 2 else j
            val inner = s.substring(i + 2, end)
            val disp = if (inner.contains("|")) inner.substringAfterLast("|") else inner
            out.append(disp)
            i = j
        } else {
            out.append(s[i]); i++
        }
    }
    return out.toString()
}

/** Remove {{templates}}. Footnote-style templates are dropped; others keep their
 *  last pipe segment as a display fallback. Nesting handled via brace counting. */
private fun stripWikiTemplates(s: String): String {
    val out = StringBuilder()
    var i = 0
    while (i < s.length) {
        if (s.startsWith("{{", i)) {
            var depth = 1
            var j = i + 2
            while (j < s.length && depth > 0) {
                if (s.startsWith("{{", j)) { depth++; j += 2 }
                else if (s.startsWith("}}", j)) { depth--; j += 2 }
                else j++
            }
            val end = if (j >= 2) j - 2 else j
            val inner = s.substring(i + 2, end)
            val name = inner.substringBefore("|").trim().lowercase()
            if (name !in DROP_TEMPLATE_NAMES) {
                out.append(inner.substringAfterLast("|"))
            }
            i = j
        } else {
            out.append(s[i]); i++
        }
    }
    return out.toString()
}

private fun parseWikiTableBlock(block: String): WikiTable {
    // Each parsed row keeps its cells plus flags of which cells were header ("!")
    // cells (Wikipedia uses "! scope=\"row\"" to mark the title cell per row).
    val rows = mutableListOf<Pair<MutableList<String>, MutableList<Boolean>>>()
    var current = mutableListOf<String>()
    var currentHdr = mutableListOf<Boolean>()
    var caption: String? = null
    for (raw in block.split("\n")) {
        val line = raw.trim()
        if (line.startsWith("|+")) {
            caption = cleanWikiCell(line.substring(2))
            continue
        }
        if (line.startsWith("|-") || line.startsWith("|}")) {
            if (current.isNotEmpty()) { rows.add(current to currentHdr); current = mutableListOf(); currentHdr = mutableListOf() }
            continue
        }
        if (line.startsWith("!")) {
            current.add(cleanWikiCell(line.substring(1)))
            currentHdr.add(true)
        } else if (line.startsWith("|")) {
            current.add(cleanWikiCell(line.substring(1)))
            currentHdr.add(false)
        }
    }
    if (current.isNotEmpty()) rows.add(current to currentHdr)

    if (rows.isEmpty()) return WikiTable(heading = caption, rows = emptyList())

    val header = rows.first().first
    val n = header.size
    // Pad short rows at the END (do NOT borrow cells from the previous row —
    // that produced wrong date-as-title mappings for the 2016+ A24 layout).
    val filled = rows.map { (cells, hdr) ->
        val padded = if (cells.size < n) cells + List(n - cells.size) { "" } else cells
        padded to hdr
    }

    val headers = filled.first().first.map { it.lowercase() }
    val globalTitle = headers.indexOfFirst { "title" in it }.takeIf { it >= 0 } ?: 0
    val yearRe = Regex("\\b(?:19|20)\\d{2}\\b")

    val tableRows = filled.drop(1).mapNotNull { (cells, hdrFlags) ->
        // Title cell: prefer the first header ("! scope=row") cell; else the
        // column whose header says "title".
        val hdrIdx = hdrFlags.indexOfFirst { it }.takeIf { it >= 0 }
        val titleIdx = hdrIdx ?: globalTitle
        val title = cells.getOrNull(titleIdx)?.trim() ?: ""
        if (title.isBlank()) return@mapNotNull null
        // Meta: date (any cell containing a 4-digit year) + director (a short
        // non-date cell), dropping the long synopsis column for a clean look.
        val others = cells.mapIndexedNotNull { idx, c ->
            c.takeIf { idx != titleIdx && c.isNotBlank() }
        }
        val dateC = others.firstOrNull { yearRe.containsMatchIn(it) }
        val dirC = others.firstOrNull { it !== dateC && it.length <= 60 && !yearRe.containsMatchIn(it) }
        val meta = listOfNotNull(dateC, dirC).joinToString(" · ")
        WikiTableRow(title = title, meta = meta)
    }
    return WikiTable(heading = caption, rows = tableRows)
}

/**
 * Parse all tables in the raw wikitext. Returns only tables that produced rows.
 *
 * Each table is stamped with the wikitext SECTION heading (e.g. `== 2010s ==`)
 * that precedes it, so multi-list articles (like "List of A24 films") render as
 * separate groups — "2010s", "2020s", etc. — instead of one merged block. Falls
 * back to the table's own caption when no section header is active.
 */
private fun parseWikitextTables(rawWikitext: String): List<WikiTable> {
    if (rawWikitext.isBlank()) return emptyList()
    val sectionRegex = Regex("^(={1,6})\\s*(.+?)\\s*=+$")
    val tables = mutableListOf<WikiTable>()
    var currentSection: String? = null
    var i = 0
    val wt = rawWikitext
    while (i < wt.length) {
        if (wt.startsWith("{|", i)) {
            // Balanced table block: collect from "{|" ... to matching "|}".
            val start = i
            var depth = 1
            i += 2
            while (i < wt.length && depth > 0) {
                if (wt.startsWith("{|", i)) depth++
                else if (wt.startsWith("|}", i)) depth--
                i += 2
            }
            val block = wt.substring(start, i)
            val parsed = parseWikiTableBlock(block)
            if (parsed.rows.isNotEmpty()) {
                // Prefer the surrounding section heading; keep the caption as fallback.
                tables.add(parsed.copy(heading = currentSection ?: parsed.heading))
            }
        } else {
            // Scan to end of line; detect section headers at line start.
            val lineEnd = wt.indexOf('\n', i).let { if (it < 0) wt.length else it }
            val line = wt.substring(i, lineEnd).trim()
            val m = sectionRegex.find(line)
            if (m != null) currentSection = m.groupValues[2].trim()
            i = if (lineEnd < wt.length) lineEnd + 1 else wt.length
        }
    }
    return tables
}
