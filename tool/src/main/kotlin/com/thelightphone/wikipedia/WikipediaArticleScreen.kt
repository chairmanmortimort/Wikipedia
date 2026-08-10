package com.thelightphone.wikipedia

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextAlign
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import kotlinx.coroutines.launch

@Composable
fun ArticleContent(
    title: String,
    description: String?,
    extract: String,
    thumbnailUrl: String?,
    links: List<String>,
    isLoading: Boolean,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLink: (String) -> Unit,
    onRandom: () -> Unit,
) {
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    // Visible section headers with their scroll offsets, used to "skip ahead".
    val sections = remember { mutableStateListOf<Pair<String, Int>>() }
    sections.clear()
    // Root Y of the scroll container, captured so section offsets are relative to it.
    val containerTopY = remember { mutableIntStateOf(0) }

    // Parse the extract into section headers (=, ==, ===) so we can offer
    // "skip ahead to next section" and wire it to a bottom-bar control.
    val sectionRegex = Regex("^(=+)\\s*(.+?)\\s*=+$")
    val terminalSections = setOf(
        "references", "sources", "further reading",
        "external links", "see also", "footnotes",
    )

    Column(modifier = Modifier.fillMaxSize()) {
        LightTopBar(
            leftButton = LightBarButton.LightIcon(
                icon = LightIcons.BACK,
                onClick = onBack,
                contentDescription = "Back",
            ),
            center = LightTopBarCenter.Text(title),
            rightButton = LightBarButton.LightIcon(
                icon = LightIcons.SETTINGS,
                onClick = onOpenSettings,
                contentDescription = "About",
            ),
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
                    text = "Loading article…",
                    variant = LightTextVariant.Copy,
                    align = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 1f.gridUnitsAsDp()),
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 1f.gridUnitsAsDp())
                    .padding(top = 0.5f.gridUnitsAsDp())
                    .verticalScroll(scrollState)
                    .onGloballyPositioned { coords ->
                        containerTopY.intValue = coords.localToRoot(Offset.Zero).y.toInt()
                    },
            ) {
                Column {
                    // Article title (large, for readability on monochrome display)
                    LightText(
                        text = title,
                        variant = LightTextVariant.Heading,
                        align = TextAlign.Center,
                        modifier = Modifier.padding(top = 0.5f.gridUnitsAsDp()),
                    )

                    // Description line (if available)
                    description?.let { desc ->
                        if (desc.isNotBlank()) {
                            LightText(
                                text = desc,
                                variant = LightTextVariant.Subheading,
                                modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
                            )
                        }
                    }

                    // Article content — parse the plain text extract into sections
                    ArticleBody(
                        extract = extract,
                        links = links,
                        onOpenLink = onOpenLink,
                        sections = sections,
                        scrollState = scrollState,
                        containerTopY = containerTopY,
                    )

                    // Links section — every related article is shown and tappable.
                    if (links.isNotEmpty()) {
                        Column(modifier = Modifier.padding(top = 1.5f.gridUnitsAsDp())) {
                            LightText(
                                text = "Related Articles",
                                variant = LightTextVariant.Subheading,
                                modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
                            )
                            links.forEach { linkTitle ->
                                LightText(
                                    text = linkTitle,
                                    variant = LightTextVariant.Copy,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .lightClickable(onClick = { onOpenLink(linkTitle) })
                                        .padding(vertical = 0.5f.gridUnitsAsDp()),
                                    color = LightThemeTokens.colors.contentSecondary,
                                    underline = true,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Back is handled by the universal top-left button, so the bottom bar
        // keeps only the remaining actions: Random article + Skip to next section.
        LightBottomBar(
            items = listOf(
                LightBarButton.LightIcon(
                    icon = LightIcons.LOOP,
                    onClick = onRandom,
                    contentDescription = "Random article",
                ),
                LightBarButton.LightIcon(
                    icon = LightIcons.ARROW_DOWN,
                    onClick = {
                        // Skip ahead to the next section header below the current scroll.
                        coroutineScope.launch {
                            val current = scrollState.value
                            val next = sections
                                .firstOrNull { it.second > current + 4 }
                                ?.second
                            if (next != null) {
                                scrollState.scrollTo(next)
                            }
                        }
                    },
                    contentDescription = "Skip to next section",
                ),
            ),
        )
    }
}

/**
 * Renders Wikipedia plain-text extract, preserving section structure.
 *
 * The MediaWiki Action API with explaintext=1 produces text like:
 *   Intro paragraph text.
 *   == Section ==
 *   Paragraph under section.
 *   === Subsection ===
 *   More text.
 *
 * Section headers (==, ===) are rendered as headings for visual hierarchy and
 * their vertical scroll offset is recorded into [sections] so the caller can
 * offer a "skip ahead to next section" control. Any line that exactly matches a
 * known article link title (from the links list) is rendered with an underline
 * in secondary color and made tappable, enabling in-article hyperlinks.
 */
@Composable
private fun ArticleBody(
    extract: String,
    links: List<String>,
    onOpenLink: (String) -> Unit,
    sections: androidx.compose.runtime.snapshots.SnapshotStateList<Pair<String, Int>>,
    scrollState: androidx.compose.foundation.ScrollState,
    containerTopY: androidx.compose.runtime.MutableIntState,
) {
    if (extract.isBlank()) {
        LightText(
            text = "No article text available.",
            variant = LightTextVariant.Copy,
            lighten = true,
        )
        return
    }

    val linkSet = links.toSet()
    val lines = extract.lines()
    val sectionRegex = Regex("^(=+)\\s*(.+?)\\s*=+$")
    val terminalSections = setOf(
        "references", "sources", "further reading",
        "external links", "see also", "footnotes",
    )

    Column {
        lines.forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) {
                Spacer(modifier = Modifier.height(0.5f.gridUnitsAsDp()))
                return@forEach
            }

            // Detect section headers (e.g. "== History ==" / === Subsection ===)
            val sectionMatch = sectionRegex.find(line)
            if (sectionMatch != null) {
                val headerText = sectionMatch.groupValues[2].trim()
                // Stop rendering at terminal sections — not useful on a small display.
                if (headerText.lowercase() in terminalSections) {
                    return@forEach
                }
                val headerKey = headerText
                LightText(
                    text = headerText,
                    variant = LightTextVariant.Heading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 1f.gridUnitsAsDp(), bottom = 0.75f.gridUnitsAsDp())
                        .onGloballyPositioned { coords ->
                            // Record this section's offset (relative to the scroll
                            // container) so "skip ahead" can jump to it.
                            val y = coords.localToRoot(Offset.Zero).y.toInt() -
                                containerTopY.intValue + scrollState.value
                            val idx = sections.indexOfFirst { it.first == headerKey }
                            if (idx >= 0) {
                                sections[idx] = headerKey to y
                            } else {
                                sections.add(headerKey to y)
                            }
                        },
                )
                return@forEach
            }

            // Check if this line is a known article link — make it tappable + underlined
            if (linkSet.contains(line)) {
                LightText(
                    text = line,
                    variant = LightTextVariant.Paragraph,
                    modifier = Modifier
                        .fillMaxWidth()
                        .lightClickable(onClick = { onOpenLink(line) })
                        .padding(vertical = 0.5f.gridUnitsAsDp()),
                    color = LightThemeTokens.colors.contentSecondary,
                    underline = true,
                )
            } else {
                LightText(
                    text = line,
                    variant = LightTextVariant.Paragraph,
                    modifier = Modifier.padding(vertical = 0.25f.gridUnitsAsDp()),
                )
            }
        }
    }
}

@Composable
fun AboutContent(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        LightTopBar(
            leftButton = LightBarButton.LightIcon(
                icon = LightIcons.BACK,
                onClick = onBack,
                contentDescription = "Back",
            ),
            center = LightTopBarCenter.Text("About"),
            modifier = Modifier.padding(bottom = 0.25f.gridUnitsAsDp()),
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(2f.gridUnitsAsDp())
                .verticalScroll(rememberScrollState()),
        ) {
            Column {
                LightText(
                    text = "Wikipedia Tool",
                    variant = LightTextVariant.Heading,
                )
                LightText(
                    text = "Uses the Wikipedia REST API and MediaWiki Action API.",
                    variant = LightTextVariant.Detail,
                    modifier = Modifier.padding(top = 1f.gridUnitsAsDp()),
                )
                LightText(
                    text = "Content is licensed under CC BY-SA 4.0.",
                    variant = LightTextVariant.Detail,
                    modifier = Modifier.padding(top = 0.5f.gridUnitsAsDp()),
                )
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

@Composable
private fun Spacer(modifier: Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(0.5f.gridUnitsAsDp())
            .background(com.thelightphone.sdk.ui.LightThemeTokens.colors.background),
    )
}
