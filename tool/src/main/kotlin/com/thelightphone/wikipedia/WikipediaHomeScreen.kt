package com.thelightphone.wikipedia

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightFullscreenModal
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTextInputEditor
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import kotlinx.coroutines.flow.MutableStateFlow
import com.thelightphone.sdk.ui.defaultKeyboardOptions
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.LocalHapticsEnabled
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable

@InitialScreen
class WikipediaHomeScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, WikipediaViewModel>(sealedActivity) {

    override val viewModelClass: Class<WikipediaViewModel>
        get() = WikipediaViewModel::class.java

    override fun createViewModel(): WikipediaViewModel =
        WikipediaViewModel(lightContext.dataStore)

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val state by viewModel.uiState.collectAsState()

        CompositionLocalProvider(LocalHapticsEnabled provides true) {
        LightTheme(colors = themeColors) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                when (val mode = state.mode) {
                    is WikipediaScreenMode.Home -> {
                        HomeContent(
                            onSearchClick = viewModel::openSearch,
                            onRandomClick = viewModel::openRandom,
                            onOnThisDayClick = viewModel::openOnThisDay,
                            onSettingsClick = viewModel::openAbout,
                            recentTitles = state.recentTitles,
                            onOpenRecent = viewModel::openArticle,
                        )
                    }

                    is WikipediaScreenMode.SearchInput -> {
                        SearchInputContent(
                            currentQuery = mode.currentQuery,
                            editorKey = mode.editorKey,
                            onSearchSubmitted = { query -> viewModel.submitSearch(query) },
                            onBack = viewModel::cancelSearch,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    is WikipediaScreenMode.Search -> {
                        SearchResultsContent(
                            query = mode.query,
                            results = mode.results,
                            isLoading = mode.isLoading,
                            onSelect = viewModel::selectSearchResult,
                            onBack = viewModel::cancelSearch,
                        )
                    }

                    is WikipediaScreenMode.Loading -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            LightTopBar(
                                center = LightTopBarCenter.Text("Wikipedia"),
                                modifier = Modifier.padding(bottom = 0.25f.gridUnitsAsDp()),
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                LightText(
                                    text = mode.message,
                                    variant = LightTextVariant.Detail,
                                    align = TextAlign.Center,
                                    modifier = Modifier.padding(top = 2f.gridUnitsAsDp()),
                                )
                            }
                        }
                    }

                    is WikipediaScreenMode.Article -> {
                        ArticleContent(
                            title = mode.title,
                            description = mode.description,
                            extract = mode.extract,
                            thumbnailUrl = mode.thumbnailUrl,
                            links = mode.links,
                            isLoading = mode.isLoading,
                            onBack = viewModel::cancelSearch,
                            onOpenSettings = viewModel::openAbout,
                            onOpenLink = viewModel::openLink,
                            onRandom = viewModel::openRandom,
                        )
                    }

                    is WikipediaScreenMode.About -> {
                        AboutContent(onBack = viewModel::closeAbout)
                    }

                    is WikipediaScreenMode.OnThisDay -> {
                        OnThisDayContent(
                            events = mode.events,
                            isLoading = mode.isLoading,
                            onBack = viewModel::closeOnThisDay,
                            onOpenPage = viewModel::openArticle,
                        )
                    }
                }

                state.errorModal?.let { message ->
                    LightFullscreenModal(
                        message = message,
                        onClose = viewModel::dismissError,
                    )
                }
            }
        }
        }
    }
}

/**
 * Home screen — Wikipedia logo + title, navigation items, and recent articles.
 * The whole screen is a single scrollable column so every item is reachable on
 * the LP3 (which cannot focus an inner weight() scroll region). The "Wikipedia"
 * title uses a capped one-line size: larger than Heading but it never wraps.
 */
@Composable
private fun HomeContent(
    onSearchClick: () -> Unit,
    onRandomClick: () -> Unit,
    onOnThisDayClick: () -> Unit,
    onSettingsClick: () -> Unit,
    recentTitles: List<String> = emptyList(),
    onOpenRecent: (String) -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 0.5f.gridUnitsAsDp(), start = 3f.gridUnitsAsDp(), end = 3f.gridUnitsAsDp(), bottom = 3f.gridUnitsAsDp()),
    ) {
        // Settings / About — pinned to the very top-right.
        LightTopBar(
            rightButton = LightBarButton.LightIcon(
                icon = LightIcons.SETTINGS,
                onClick = onSettingsClick,
                contentDescription = "About",
            ),
            modifier = Modifier.padding(bottom = 0.5f.gridUnitsAsDp()),
        )

        // Logo + large single-line title
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 0.25f.gridUnitsAsDp()),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(id = R.drawable.wikipedia_logo),
                contentDescription = "Wikipedia logo",
                modifier = Modifier.size(8f.gridUnitsAsDp()),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 0.5f.gridUnitsAsDp()),
            contentAlignment = Alignment.Center,
        ) {
            val titleStyle = LightThemeTokens.typography.heading.copy(
                fontSize = 46.sp,
                fontWeight = FontWeight.Light,
                lineHeight = 52.sp,
            )
            Text(
                text = "Wikipedia",
                style = titleStyle,
                color = LightThemeTokens.colors.content,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Nav items + recent articles (no weight() — parent is the scroller).
        // Sits just below the title with a small gap.
        HomeMenuItem(
            text = "Search",
            icon = LightIcons.SEARCH,
            onClick = onSearchClick,
        )
        HomeMenuItem(
            text = "Random Article",
            icon = LightIcons.LOOP,
            onClick = onRandomClick,
        )
        HomeMenuItem(
            text = "On This Day",
            icon = LightIcons.LIGHT_LOGO,
            onClick = onOnThisDayClick,
        )

        if (recentTitles.isNotEmpty()) {
            Spacer(modifier = Modifier.height(0.5f.gridUnitsAsDp()))
            LightText(
                text = "Recent",
                variant = LightTextVariant.Subheading,
                modifier = Modifier.padding(vertical = 0.5f.gridUnitsAsDp()),
            )
            recentTitles.forEach { title ->
                HomeMenuItem(
                    text = title,
                    icon = LightIcons.LIST,
                    onClick = { onOpenRecent(title) },
                    variant = LightTextVariant.Detail,
                )
            }
        }
    }
}

@Composable
private fun HomeMenuItem(
    text: String,
    icon: com.thelightphone.sdk.ui.LightIconConfiguration,
    onClick: () -> Unit,
    variant: LightTextVariant = LightTextVariant.Heading,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .lightClickable(onClick = onClick)
            .padding(vertical = 0.75f.gridUnitsAsDp()),
    ) {
        LightText(
            text = text,
            variant = variant,
        )
    }
}

/**
 * "On This Day" screen — shows what actually happened on this day.
 *
 * Each event carries a free-text description (e.g. "1999 — The Vermont State
 * House is struck by lightning.") rendered as the headline, with the linked
 * Wikipedia pages shown as tappable chips beneath. Tapping a chip opens that
 * article; the event text itself is not an article and is not tappable. This
 * avoids the old behaviour of opening a huge unrelated article (e.g. "Vietnam")
 * or a blank page when an event had no linked page.
 */
@Composable
private fun OnThisDayContent(
    events: List<WikiOnThisDayEvent>,
    isLoading: Boolean,
    onBack: () -> Unit,
    onOpenPage: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(3f.gridUnitsAsDp()),
    ) {
        LightTopBar(
            leftButton = LightBarButton.LightIcon(
                icon = LightIcons.BACK,
                onClick = onBack,
                contentDescription = "Back",
            ),
            center = LightTopBarCenter.Text("On This Day"),
        )

        if (isLoading) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                LightText(
                    text = "Loading events...",
                    variant = LightTextVariant.Detail,
                    lighten = true,
                )
            }
        } else {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(top = 1f.gridUnitsAsDp()),
            ) {
                if (events.isEmpty()) {
                    LightText(
                        text = "No events found for today.",
                        variant = LightTextVariant.Detail,
                        align = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(2f.gridUnitsAsDp()),
                    )
                } else {
                    events.forEach { event ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 1f.gridUnitsAsDp())
                                .padding(horizontal = 1f.gridUnitsAsDp()),
                        ) {
                            // The event itself: what actually happened on this day.
                            LightText(
                                text = event.text,
                                variant = LightTextVariant.Subheading,
                            )

                            // Linked pages (if any) as tappable chips. An event may
                            // have no linked page (then it's just the text) or many.
                            if (event.pages.isNotEmpty()) {
                                Column(
                                    modifier = Modifier.padding(top = 0.5f.gridUnitsAsDp()),
                                ) {
                                    event.pages.forEach { page ->
                                        val displayTitle = page.title.replace('_', ' ')
                                        LightText(
                                            text = displayTitle,
                                            variant = LightTextVariant.Detail,
                                            color = LightThemeTokens.colors.contentSecondary,
                                            underline = true,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .lightClickable { onOpenPage(page.title) }
                                                .padding(vertical = 0.5f.gridUnitsAsDp()),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Search input screen using the SDK's LightTextInputEditor — integrates with LP3
 * hardware keyboard and supports D-pad navigation properly.
 * Uses editorKey to force TextFieldState reset on re-entry (same pattern as Amtrak SearchScreen).
 */
@Composable
fun SearchInputContent(
    currentQuery: String,
    editorKey: Any,
    onSearchSubmitted: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val textFieldState = rememberTextFieldState(currentQuery)

    LightTextInputEditor(
        title = "Search Wikipedia",
        state = textFieldState,
        onSubmit = { query -> onSearchSubmitted(query.toString()) },
        onBack = onBack,
        keyboardOptionsFlow = MutableStateFlow(defaultKeyboardOptions()),
        modifier = modifier.fillMaxSize(),
        submitLabel = "SEARCH",
        submitIcon = LightIcons.SEARCH,
        editorKey = editorKey,
    )
}