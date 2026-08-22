package com.thelightphone.wikipedia

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextAlign
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightIcon
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
    hasTable: Boolean = false,
    tables: List<WikiTable> = emptyList(),
    isLoading: Boolean,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLink: (String) -> Unit,
) {
    val scrollState = rememberScrollState()

    // Article body renders section headers below the list.
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

                    // Tables/lists (filmographies, discographies, etc.) are dropped by the
                    // plain-text extract, so we surface the parsed rows at the TOP of the
                    // article — immediately visible without scrolling. Each table is shown
                    // under its own section heading (e.g. 2010s / 2020s) when the parser
                    // found one; otherwise they collapse into a single "In this list" group.
                    if (tables.isNotEmpty()) {
                        val totalRows = tables.sumOf { it.rows.size }
                        val groupedByHeading = tables
                            .map { it.heading to it.rows }
                            .groupBy({ it.first }, { it.second })
                            .toList()
                        if (groupedByHeading.size == 1 && groupedByHeading.first().first == null) {
                            // No section headings found — render as one combined list.
                            LightText(
                                text = "In this list ($totalRows)",
                                variant = LightTextVariant.Subheading,
                                modifier = Modifier.padding(bottom = 0.5f.gridUnitsAsDp()),
                            )
                            renderTableRows(groupedByHeading.first().second.flatten())
                        } else {
                            // One or more headed groups — render each under its heading.
                            for ((heading, rowGroups) in groupedByHeading) {
                                if (heading != null) {
                                    LightText(
                                        text = heading,
                                        variant = LightTextVariant.Heading,
                                        modifier = Modifier
                                            .padding(
                                                top = 1f.gridUnitsAsDp(),
                                                bottom = 0.5f.gridUnitsAsDp(),
                                            ),
                                    )
                                }
                                renderTableRows(rowGroups.flatten())
                            }
                        }
                        Spacer(modifier = Modifier.height(1.5f.gridUnitsAsDp()))
                    }

                    // Article body (intro + section headers) below the list.
                    ArticleBody(
                        extract = extract,
                        links = links,
                        onOpenLink = onOpenLink,
                        scrollState = scrollState,
                        renderedHeadings = tables.mapNotNull { it.heading?.lowercase() }.toSet(),
                    )
                }
            }

            LightBottomBar(
                items = listOf(
                    LightBarButton.LightIcon(
                        icon = LightIcons.SEARCH,
                        onClick = onSearch,
                        contentDescription = "Search",
                    ),
                ),
            )
        }
    }
}

/** Renders a flat list of parsed table rows as static title + dimmed meta.
 *  Rows are intentionally NOT tappable — they are list entries (films, albums),
 *  not in-article hyperlinks, so tapping them must not navigate to sub-articles. */
@Composable
private fun renderTableRows(rows: List<WikiTableRow>) {
    rows.forEach { row ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 0.5f.gridUnitsAsDp()),
        ) {
            LightText(
                text = row.title,
                variant = LightTextVariant.Copy,
                color = LightThemeTokens.colors.contentSecondary,
            )
            if (row.meta.isNotBlank()) {
                LightText(
                    text = row.meta,
                    variant = LightTextVariant.Detail,
                    lighten = true,
                    modifier = Modifier.padding(top = 0.25f.gridUnitsAsDp()),
                )
            }
        }
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
 * Section headers (==, ===) are rendered as headings for visual hierarchy. Any
 * line that exactly matches a known article link title (from the links list) is
 * rendered with an underline in secondary color and made tappable, enabling
 * in-article hyperlinks.
 */
@Composable
private fun ArticleBody(
    extract: String,
    links: List<String>,
    onOpenLink: (String) -> Unit,
    scrollState: androidx.compose.foundation.ScrollState,
    // Headings already rendered at the TOP of the article (from parsed tables), so
    // we don't re-print them (and their empty parent wrappers) at the article tail.
    renderedHeadings: Set<String> = emptySet(),
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
        "references", "sources", "notes", "footnotes", "further reading",
        "external links", "see also",
    )

    // Pre-pass: decide which lines to suppress at the article tail.
    //  1) Headers already shown at the top (2010s/2020s from the parsed tables).
    //  2) "Container" headers with no body text of their own (the == Released films ==
    //     / == Upcoming == wrappers that only hold the list sub-headers).
    //  3) Everything from a terminal section (References/Sources/Notes/…) to the end.
    val suppress = BooleanArray(lines.size)
    var terminalMode = false
    for (i in lines.indices) {
        val line = lines[i].trim()
        val m = sectionRegex.find(line)
        if (m != null) {
            val text = m.groupValues[2].trim().lowercase()
            if (text in terminalSections) terminalMode = true
            if (terminalMode) { suppress[i] = true; continue }
            if (text in renderedHeadings) { suppress[i] = true; continue }
            // Container? True when no non-blank line sits between this header and the
            // next header (i.e. it's just a wrapper around sub-sections).
            val j = (i + 1 until lines.size).firstOrNull { k ->
                sectionRegex.containsMatchIn(lines[k].trim())
            } ?: lines.size
            val hasBody = (i + 1 until j).any { lines[it].trim().isNotEmpty() }
            if (!hasBody) suppress[i] = true
        } else if (terminalMode) {
            suppress[i] = true
        }
    }

    Column {
        lines.forEachIndexed { index, rawLine ->
            if (suppress[index]) return@forEachIndexed
            val line = rawLine.trim()
            if (line.isEmpty()) {
                Spacer(modifier = Modifier.height(0.5f.gridUnitsAsDp()))
                return@forEachIndexed
            }

            // Detect section headers (e.g. "== History ==" / === Subsection ===)
            val sectionMatch = sectionRegex.find(line)
            if (sectionMatch != null) {
                val headerText = sectionMatch.groupValues[2].trim()
                LightText(
                    text = headerText,
                    variant = LightTextVariant.Heading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 1f.gridUnitsAsDp(), bottom = 0.75f.gridUnitsAsDp()),
                )
                return@forEachIndexed
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
fun AboutContent(
    onBack: () -> Unit,
    invertColors: Boolean = false,
    onToggleInvertColors: () -> Unit = {},
    onClearRecents: () -> Unit = {},
    showRandomArticle: Boolean = true,
    onToggleRandomArticle: () -> Unit = {},
    showOnThisDay: Boolean = true,
    onToggleOnThisDay: () -> Unit = {},
) {
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
            ToggleRow(
                label = "Invert colors",
                checked = invertColors,
                onClick = onToggleInvertColors,
            )

            ToggleRow(
                label = "Show random article",
                checked = showRandomArticle,
                onClick = onToggleRandomArticle,
            )

            ToggleRow(
                label = "Show on this day",
                checked = showOnThisDay,
                onClick = onToggleOnThisDay,
            )

            LightText(
                text = "Clear recents",
                variant = LightTextVariant.Copy,
                lighten = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .lightClickable(onClick = onClearRecents)
                    .padding(vertical = 0.75f.gridUnitsAsDp()),
            )

            androidx.compose.foundation.layout.Spacer(
                modifier = Modifier.height(1f.gridUnitsAsDp()),
            )

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

        LightBottomBar(items = listOf())
    }
}

@Composable
fun ConfirmClearRecentsContent(
    onBack: () -> Unit,
    onConfirm: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LightThemeTokens.colors.background),
    ) {
        LightTopBar(
            leftButton = LightBarButton.LightIcon(
                icon = LightIcons.BACK,
                onClick = onBack,
                contentDescription = "Back",
            ),
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 1f.gridUnitsAsDp()),
            contentAlignment = Alignment.Center,
        ) {
            LightText(
                text = "Are you sure you would like to clear your Recents list?",
                variant = LightTextVariant.Copy,
                align = TextAlign.Center,
            )
        }

        LightBottomBar(
            items = listOf(
                LightBarButton.Text(
                    text = "CONFIRM",
                    onClick = onConfirm,
                ),
            ),
        )
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .lightClickable(onClick = onClick)
            .padding(vertical = 0.75f.gridUnitsAsDp()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LightIcon(
            icon = if (checked) LightIcons.TOGGLE_STATE_ON else LightIcons.TOGGLE_STATE_OFF,
        )
        LightText(
            text = label,
            variant = LightTextVariant.Copy,
            modifier = Modifier
                .weight(1f)
                .padding(start = 1f.gridUnitsAsDp()),
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
