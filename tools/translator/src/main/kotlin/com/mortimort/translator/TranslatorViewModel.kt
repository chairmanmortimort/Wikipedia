package com.mortimort.translator

import com.mortimort.translator.data.Language
import com.mortimort.translator.data.MyMemoryRequest
import com.mortimort.translator.data.MyMemoryResponse
import com.mortimort.translator.data.TranslationResult
import com.mortimort.translator.data.TranslationState
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.net.URLDecoder
import kotlinx.serialization.json.Json

class TranslatorViewModel(
    private val lightContext: SealedLightContext,
) : LightViewModel<Unit>() {

    private val json = Json { ignoreUnknownKeys = true }

    private val _state = MutableStateFlow<TranslationState<Unit>>(
        TranslationState.Ready(
            inputText = "",
            sourceLang = Language.ENGLISH,
            targetLang = Language.SPANISH,
        )
    )
    val state: StateFlow<TranslationState<Unit>> = _state.asStateFlow()

    private var lastInputText = ""
    private var lastSourceLang = Language.ENGLISH
    private var lastTargetLang = Language.SPANISH

    fun setText(text: String) {
        val s = _state.value
        if (s is TranslationState.Ready) {
            // The LP3 keyboard sim can emit literal "%20" for spaces; decode so the
            // text we translate is what the user actually meant.
            val decoded = runCatching { URLDecoder.decode(text, "UTF-8") }.getOrDefault(text)
            _state.value = s.copy(inputText = decoded, error = null)
            lastInputText = decoded
        }
    }

    fun setSourceLang(lang: Language) {
        val s = _state.value
        if (s is TranslationState.Ready) {
            _state.value = s.copy(sourceLang = lang)
            lastSourceLang = lang
        }
    }

    fun setTargetLang(lang: Language) {
        val s = _state.value
        if (s is TranslationState.Ready) {
            _state.value = s.copy(targetLang = lang)
            lastTargetLang = lang
        }
    }

    fun swapLanguages() {
        val s = _state.value
        if (s is TranslationState.Ready) {
            _state.value = s.copy(sourceLang = s.targetLang, targetLang = s.sourceLang)
            lastSourceLang = s.targetLang
            lastTargetLang = s.sourceLang
        }
    }

    fun translate() {
        val s = _state.value
        if (s !is TranslationState.Ready) return
        if (s.inputText.isBlank()) {
            _state.value = s.copy(error = "Enter text to translate")
            return
        }
        // Normalize any whitespace (incl. NBSP U+00A0 and other unicode spaces) to regular spaces,
        // then collapse repeats. The LP3 keyboard may emit non-breaking spaces.
        val normalized = s.inputText
            .replace(Regex("[\\u00A0\\u2007\\u202F\\u2009\\u2005\\u2003\\u2002\\u3000\\t\\r\\n]+"), " ")
            .replace(Regex(" +"), " ")
            .trim()

        if (s.sourceLang == s.targetLang) {
            _state.value = s.copy(error = "Source and target must be different")
            return
        }

        lastInputText = normalized
        lastSourceLang = s.sourceLang
        lastTargetLang = s.targetLang

        _state.value = TranslationState.Translating(
            inputText = s.inputText,
            sourceLang = s.sourceLang,
            targetLang = s.targetLang,
        )

        Thread {
            val result = runCatching {
                fetchWithRetry(lastInputText, lastSourceLang, lastTargetLang)
            }
            val finalState = result.fold(
                onSuccess = { response ->
                    if (response.quotaFinished == true) {
                        TranslationState.Ready(
                            inputText = lastInputText,
                            sourceLang = lastSourceLang,
                            targetLang = lastTargetLang,
                            result = null,
                            error = "Daily translation quota reached. Try again later.",
                            quotaFinished = true,
                        )
                    } else if (response.exception_code != null) {
                        TranslationState.Ready(
                            inputText = lastInputText,
                            sourceLang = lastSourceLang,
                            targetLang = lastTargetLang,
                            result = null,
                            error = response.exception_code + ": " + (response.responseDetails ?: ""),
                            quotaFinished = false,
                        )
                    } else if (response.responseStatus != 200) {
                        TranslationState.Ready(
                            inputText = lastInputText,
                            sourceLang = lastSourceLang,
                            targetLang = lastTargetLang,
                            result = null,
                            error = "Server error ${response.responseStatus}: " + (response.responseDetails ?: ""),
                            quotaFinished = false,
                        )
                    } else {
                        val decodedText = URLDecoder.decode(response.responseData.translatedText, "UTF-8")
                        TranslationState.Ready(
                            inputText = lastInputText,
                            sourceLang = lastSourceLang,
                            targetLang = lastTargetLang,
                            result = response.responseData.copy(translatedText = decodedText),
                            error = null,
                            quotaFinished = false,
                        )
                    }
                },
                onFailure = { e ->
                    TranslationState.Ready(
                        inputText = lastInputText,
                        sourceLang = lastSourceLang,
                        targetLang = lastTargetLang,
                        result = null,
                        error = "Network error: ${e.message ?: "unknown"}. Check your connection and try again.",
                        quotaFinished = false,
                    )
                },
            )
            _state.value = finalState
        }.start()
    }

    private fun fetchWithRetry(
        text: String,
        source: Language,
        target: Language,
    ): MyMemoryResponse {
        val pair = "${source.code}|${target.code}"
        val request = MyMemoryRequest(q = text, langpair = pair)
        return try {
            fetchMyMemory(request)
        } catch (e: java.io.IOException) {
            Thread.sleep(800)
            fetchMyMemory(request)
        }
    }

    private fun fetchMyMemory(request: MyMemoryRequest): MyMemoryResponse {
        val encodedQ = URLEncoder.encode(request.q, "UTF-8")
        val encodedPair = URLEncoder.encode(request.langpair, "UTF-8")
        val urlString = "https://api.mymemory.translated.net/get?" +
                "q=$encodedQ" +
                "&langpair=$encodedPair" +
                "&de=chairmanmortimort@lighttranslator.local"

        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 15_000
        conn.readTimeout = 15_000

        val responseCode = conn.responseCode
        val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
        val reader = BufferedReader(InputStreamReader(stream))
        val body = reader.readText()
        reader.close()
        conn.disconnect()
        return json.decodeFromString<MyMemoryResponse>(body)
    }
}
