package com.mortimort.dictionary

import com.thelightphone.sdk.LightViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class PosSection(
    val pos: String,
    val glosses: List<String>,
)

data class DictResult(
    val word: String,
    val sections: List<PosSection>,
    val synonyms: List<String>,
    val antonyms: List<String>,
    val pronunciation: String?,
)

sealed interface DictState {
    data object Loading : DictState
    data object Empty : DictState
    data class Found(val result: DictResult) : DictState
    data class Error(val message: String) : DictState
}

class DictionaryViewModel : LightViewModel<Unit>() {

    private val _state = MutableStateFlow<DictState>(DictState.Empty)
    val state: StateFlow<DictState> = _state.asStateFlow()

    fun search(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            _state.value = DictState.Empty
            return
        }
        _state.value = DictState.Loading
        // Run the network fetch on a plain background thread (don't rely on
        // viewModelScope dispatchers — they can keep this on the main thread
        // and trigger an ANR).
        Thread {
            try {
                val result = fetchWiktionary(trimmed)
                _state.value = if (result != null) {
                    DictState.Found(result)
                } else {
                    DictState.Empty
                }
            } catch (e: Exception) {
                Log.e("DictFetch", "lookup failed for '$trimmed'", e)
                _state.value = DictState.Error("Could not reach Wiktionary. Check your connection.")
            }
        }.start()
    }

    fun clear() {
        _state.value = DictState.Empty
    }

    private fun fetchWiktionary(query: String): DictResult? {
        // MediaWiki API: pull the wikitext of the English Wiktionary entry, live.
        // Covers the full English Wiktionary on demand — nothing is pre-bundled.
        val result = fetchPage(query, query.lowercase())
        if (result != null) return result
        // Proper-noun fallback: try capitalized first letter (e.g. "Telugu" → page exists).
        val cap = query.lowercase().replaceFirstChar { it.uppercase() }
        if (cap != query.lowercase()) return fetchPage(query, cap)
        return null
    }

    // Fetch one title, parse its English section. Returns null on missing page OR when the
    // resolved page has no English section (so the caller can try the capitalized variant).
    // `query` is the original user-supplied word (for the DictResult word field).
    private fun fetchPage(query: String, title: String): DictResult? {
        val url = "https://en.wiktionary.org/w/api.php" +
            "?action=query&prop=revisions&rvprop=content&rvslots=main&format=json" +
            "&redirects=1" +
            "&titles=" + URLEncoder.encode(title, "UTF-8")
        Log.d("DictFetch", "GET $url")
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 5000
        conn.readTimeout = 8000
        conn.setRequestProperty("User-Agent", "light-dictionary/1.0 (Wiktionary lookup)")
        val code = conn.responseCode
        Log.d("DictFetch", "response code=$code for title=$title")
        if (code != 200) {
            conn.disconnect()
            return null
        }
        val text = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()
        val json = JSONObject(text)
        val pages = json.optJSONObject("query")?.optJSONObject("pages") ?: return null
        val keys = pages.keys()
        while (keys.hasNext()) {
            val page = pages.getJSONObject(keys.next())
            if (page.has("missing")) continue
            val rev = page.getJSONArray("revisions").getJSONObject(0)
            val slot = rev.getJSONObject("slots").getJSONObject("main")
            val wikitext = slot.optString("content").ifEmpty { slot.optString("*") }
            val parsed = parseWiktionary(wikitext)
            if (parsed != null) return DictResult(
                word = query,
                sections = parsed.sections,
                synonyms = parsed.synonyms,
                antonyms = parsed.antonyms,
                pronunciation = parsed.pronunciation,
            )
        }
        Log.d("DictFetch", "no English gloss found for title=$title")
        return null
    }

    // Level-3 (===X===) headers inside ==English== that are NOT gloss containers.
    // Anything else at level 3 is treated as a part-of-speech section label.
    private val NON_GLOSS_HEADERS = setOf(
        "pronunciation", "etymology", "synonyms", "antonyms", "related terms",
        "derived terms", "translations", "anagrams", "references", "usage notes",
        "declension", "conjugation", "inflection", "alternative forms", "see also",
        "quotations", "proverbs", "idioms", "hyponyms", "hypernyms", "holonyms",
        "meronyms", "coordinate terms", "troponyms", "descendants", "further reading",
        "appendix", "notes", "abbreviations", "characteristics", "compounds",
        "participle", "participles", "inflection", "definition", "definitions",
    )

    // Minimal parser: walk the English section, grouping numbered glosses by their
    // part-of-speech (===Noun=== / ===Adjective=== / ...). Also collects the first IPA
    // pronunciation, and Synonyms / Antonyms (both the ===Subsection=== list and the
    // inline "#: {{syn|en|...}}" bullets most entries use).
    // Returns null when the resolved page has no English section with any glosses, so the
    // caller can retry a capitalized title (proper-noun fallback).
    private fun parseWiktionary(wikitext: String): ParsedEntry? {
        val lines = wikitext.lines()
        val sections = mutableListOf<PosSection>()
        var currentPos: String? = null
        var currentGlosses = mutableListOf<String>()
        val syns = mutableListOf<String>()
        val ants = mutableListOf<String>()
        var pronunciation: String? = null
        var inEnglish = false
        var inPronunciation = false
        var inSynonyms = false
        var inAntonyms = false

        fun flushSection() {
            if (currentPos != null && currentGlosses.isNotEmpty()) {
                sections.add(PosSection(currentPos!!, currentGlosses.toList()))
            }
            currentGlosses = mutableListOf()
        }

        for (line in lines) {
            val trimmed = line.trim()
            // Count leading '=' to tell level-2 (==X==) from level-3 (===X===) and deeper.
            val level = trimmed.takeWhile { it == '=' }.length
            if (level >= 2 && trimmed.endsWith("=")) {
                val header = trimmed.trim('=').trim()
                if (level == 2) {
                    if (header.equals("English", ignoreCase = true)) {
                        flushSection()
                        currentPos = null
                        inEnglish = true
                        inPronunciation = false
                        inSynonyms = false
                        inAntonyms = false
                    } else if (inEnglish) {
                        break // a new language section ends English collection
                    }
                    continue
                } else if (level >= 3 && inEnglish) {
                    inPronunciation = header.equals("Pronunciation", ignoreCase = true)
                    inSynonyms = header.equals("Synonyms", ignoreCase = true)
                    inAntonyms = header.equals("Antonyms", ignoreCase = true)
                    // Only level-3 headers start a new part-of-speech section; deeper
                    // headers (====Comparative====) are inflection sub-lists, not POS.
                    if (level == 3 && !inPronunciation && !inSynonyms && !inAntonyms &&
                        header.lowercase() !in NON_GLOSS_HEADERS
                    ) {
                        flushSection()
                        currentPos = header
                    }
                    continue
                }
            }
            if (!inEnglish) continue
            if (inPronunciation) {
                // Keep scanning the rest of the Pronunciation section only for the first IPA.
                if (pronunciation == null) {
                    val m = Regex("""/[^/]+/""").find(trimmed)
                    if (m != null) pronunciation = m.value
                }
                continue
            }
            if (inSynonyms || inAntonyms) {
                if (trimmed.startsWith("*") &&
                    !trimmed.startsWith("**") &&
                    !trimmed.startsWith("*:")
                ) {
                    val item = cleanWiki(trimmed.removePrefix("*").trim())
                    if (item.isNotBlank()) (if (inSynonyms) syns else ants).add(item)
                }
                continue
            }
            // Inline synonyms/antonyms live on "#: {{syn|en|...}}" / "#: {{ant|en|...}}" lines.
            if (trimmed.startsWith("#:")) {
                val kind = Regex("""\{\{(syn|ant|synonyms|antonyms)\|""").find(trimmed)?.groupValues?.get(1)
                if (kind != null) {
                    val tm = Regex("""\{\{(?:syn|ant|synonyms|antonyms)\|([^}]*)\}\}""").find(trimmed)
                    if (tm != null) {
                        val parts = tm.groupValues[1].split('|').map { it.trim() }
                        val words = parts.drop(1)
                            .filter { it.isNotBlank() && ":" !in it && !it.lowercase().startsWith("thesaurus") }
                            .map { it.substringBefore('<') }
                        if (kind.startsWith("syn")) syns.addAll(words) else ants.addAll(words)
                    }
                }
                continue
            }
            // Definition gloss: a top-level "#" line. Exclude "##" sub-glosses and
            // "#:" / "#*" / "#;" continuation/example/note markers — never real glosses.
            if (trimmed.startsWith("#") &&
                !trimmed.startsWith("##") &&
                !trimmed.startsWith("#*") &&
                !trimmed.startsWith("#;")
            ) {
                val d = cleanWiki(trimmed.removePrefix("#").trim())
                if (d.isNotBlank()) {
                    if (currentPos == null) currentPos = ""
                    currentGlosses.add(d)
                }
            }
        }
        flushSection()
        if (sections.isEmpty()) {
            Log.d("DictFetch", "no English gloss found")
            return null
        }
        return ParsedEntry(
            sections,
            syns.take(40).distinct(),
            ants.take(40).distinct(),
            pronunciation,
        )
    }

    // Templates that contribute nothing to a readable gloss — drop them entirely.
    private val DROP_TEMPLATES = setOf(
        "q", "qualifier", "lb", "label", "n-g", "non-gloss", "defdate", "senseid",
        "taxfmt", "gloss", "zh-pron", "langname", "w", "zh-m", "attention",
        "u", "vern", "ux", "quote", "rollback", "rfquote", "rfe",
    )
    // Templates where we keep a single meaningful argument (usually a display word).
    private val KEEP_ARG_TEMPLATES_WHITELIST = setOf("l", "m", "ll", "link", "cap", "gloss")

    private fun cleanWiki(s: String): String {
        // Repeatedly strip innermost {{...}} template blocks (handles nested + multi-line),
        // replacing keep-arg templates with their meaningful argument.
        var result = s
        var prev = ""
        var guard = 0
        while (result != prev && guard < 200) {
            prev = result
            result = result.replace(Regex("""(?s)\{\{[^{}]*\}\}""")) { m ->
                replaceTemplate(m.value)
            }
            guard++
        }
        // Remove ''' bold markers, then '' italic markers.
        result = result.replace("'''", "").replace("''", "")
        // Remove [[ ... ]] wikilinks, keeping the display text if present.
        result = result.replace(Regex("""\[\[[^\]]*\|([^\]]*)\]\]"""), "$1")
        result = result.replace(Regex("""\[\[([^\]]*)\]\]"""), "$1")
        // Remove [[File:...]] embeds.
        result = result.replace(Regex("""\[\[File:[^\]]*\]\]"""), "")
        // Collapse whitespace.
        result = result.replace("""\s+""".toRegex(), " ").trim()
        // Tidy dangling colons left by stripped link templates ("followed by : to").
        result = result.replace("""\s*:\s*""".toRegex(), ": ").trim(':').trim()
        result = result.trim()
        // A line that is now only punctuation/whitespace (e.g. "# {{U|fat}}.") is not a
        // real gloss — signal the caller to drop it.
        if (result.isEmpty() || result.none { it.isLetterOrDigit() }) return ""
        return result
    }

    // Replace a single {{...}} template with readable text, or "" if it should be dropped.
    private fun replaceTemplate(t: String): String {
        val inner = t.substring(2, t.length - 2)
        val parts = inner.split('|').map { it.trim() }
        val name = parts.firstOrNull()?.lowercase() ?: ""
        return when {
            name in DROP_TEMPLATES -> ""
            name in KEEP_ARG_TEMPLATES_WHITELIST -> {
                // Keep the last meaningful argument (skip 2-letter language codes / empty).
                val keep = parts.drop(1).lastOrNull { p ->
                    p.isNotBlank() && (p.length != 2 || p.any { !it.isLetter() })
                }
                keep ?: ""
            }
            // Any other template: strip it entirely (avoids leaking "{{uncommon spelling of|...}}").
            else -> ""
        }
    }
}

// Holder for the parse outputs before they become a DictResult.
private data class ParsedEntry(
    val sections: List<PosSection>,
    val synonyms: List<String>,
    val antonyms: List<String>,
    val pronunciation: String?,
)
