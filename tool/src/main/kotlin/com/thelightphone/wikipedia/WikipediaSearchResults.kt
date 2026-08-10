package com.thelightphone.wikipedia

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable

@Composable
fun SearchResultsContent(
    query: String,
    results: List<WikiSearchResult>,
    isLoading: Boolean,
    onSelect: (WikiSearchResult) -> Unit,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LightTopBar(
            leftButton = LightBarButton.LightIcon(
                icon = LightIcons.BACK,
                onClick = onBack,
                contentDescription = "Back",
            ),
            center = LightTopBarCenter.Text("Search Results"),
            modifier = Modifier.padding(bottom = 0.25f.gridUnitsAsDp()),
        )

        if (isLoading) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                LightText(
                    text = "Searching…",
                    variant = LightTextVariant.Copy,
                    align = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 1f.gridUnitsAsDp()),
                )
            }
        } else if (results.isEmpty()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 1f.gridUnitsAsDp())
                    .padding(top = 1f.gridUnitsAsDp())
                    .verticalScroll(rememberScrollState()),
            ) {
                LightText(
                    text = "No results found for \"$query\".",
                    variant = LightTextVariant.Copy,
                    lighten = true,
                    modifier = Modifier.padding(top = 2f.gridUnitsAsDp()),
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 1f.gridUnitsAsDp())
                    .padding(top = 0.5f.gridUnitsAsDp())
                    .verticalScroll(rememberScrollState()),
            ) {
                results.forEach { result ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .lightClickable(onClick = { onSelect(result) })
                            .padding(vertical = 1f.gridUnitsAsDp()),
                    ) {
                        LightText(
                            text = result.title,
                            variant = LightTextVariant.Subheading,
                            maxLines = 2,
                        )
                        if (result.snippet.isNotBlank()) {
                            LightText(
                                text = stripHtmlSnippet(result.snippet),
                                variant = LightTextVariant.Detail,
                                lighten = true,
                                modifier = Modifier.padding(top = 0.25f.gridUnitsAsDp()),
                                maxLines = 3,
                            )
                        }
                    }
                }
            }
        }

        LightBottomBar(
            items = listOf(
                LightBarButton.LightIcon(
                    icon = LightIcons.BACK,
                    onClick = onBack,
                    contentDescription = "Back",
                ),
            ),
        )
    }
}

/**
 * Strips HTML tags and Wikipedia search-match highlights from a snippet.
 * The snippet from the API looks like:
 *   <span class="searchmatch">Troy</span> is a city in...
 */
private fun stripHtmlSnippet(snippet: String): String {
    var result = snippet
    // Remove span tags with searchmatch class
    result = result.replace(Regex("<span[^>]*class=\"searchmatch\"[^>]*>"), "")
    // Remove all remaining HTML tags
    result = result.replace(Regex("<[^>]*>"), "")
    // Decode common HTML entities
    result = result.replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
    return result.trim()
}
