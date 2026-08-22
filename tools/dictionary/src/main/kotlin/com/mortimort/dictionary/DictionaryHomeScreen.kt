package com.mortimort.dictionary

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.*

@InitialScreen
class DictionaryHomeScreen(private val sealedActivity: SealedLightActivity) :
    LightScreen<Unit, DictionaryViewModel>(sealedActivity) {

    override val viewModelClass: Class<DictionaryViewModel>
        get() = DictionaryViewModel::class.java

    override fun createViewModel(): DictionaryViewModel = DictionaryViewModel()

    override fun willShow() {
        // Live mode: nothing to preload. State starts at Empty ("Tap to search").
    }

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val state by viewModel.state.collectAsState()

        LightTheme(colors = themeColors) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                LightTopBar(
                    leftButton = if (state !is DictState.Loading && _lastQuery.isNotEmpty()) {
                        LightBarButton.LightIcon(
                            icon = LightIcons.BACK,
                            onClick = {
                                _lastQuery = ""
                                viewModel.clear()
                            },
                        )
                    } else {
                        null
                    },
                    rightButton = LightBarButton.LightIcon(
                        icon = LightIcons.SETTINGS,
                        onClick = {
                            navigateTo(
                                screenFactory = { activity -> CreditsScreen(activity) },
                            )
                        },
                    ),
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    when (val s = state) {
                        is DictState.Loading -> {
                            LightText(
                                text = "Looking up…",
                                variant = LightTextVariant.Detail,
                            )
                        }
                        is DictState.Empty -> {
                            if (_lastQuery.isNotEmpty()) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.lightClickable {
                                        // Same as the back arrow: return to clean home.
                                        _lastQuery = ""
                                        viewModel.clear()
                                    },
                                ) {
                                    LightText(
                                        text = "No entry for \"${_lastQuery}\".",
                                        variant = LightTextVariant.Copy,
                                    )
                                    Spacer(modifier = Modifier.height(1f.gridUnitsAsDp()))
                                    LightText(
                                        text = "Tap to go back",
                                        variant = LightTextVariant.Detail,
                                        lighten = true,
                                    )
                                }
                            } else {
                                LightText(
                                    text = "Tap to search",
                                    variant = LightTextVariant.Subheading,
                                    modifier = Modifier.lightClickable {
                                        openSearch()
                                    },
                                )
                            }
                        }
                        is DictState.Found -> {
                            val r = s.result
                            LightScrollView(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 1f.gridUnitsAsDp())
                                    .lightClickable { openSearch() },
                            ) {
                                LightText(
                                    text = r.word,
                                    variant = LightTextVariant.Subheading,
                                    modifier = Modifier.padding(top = 1f.gridUnitsAsDp(), bottom = 0.5f.gridUnitsAsDp()),
                                    maxLines = 2,
                                )
                                if (r.pronunciation != null) {
                                    Spacer(modifier = Modifier.height(1f.gridUnitsAsDp()))
                                    LightText(
                                        text = r.pronunciation,
                                        variant = LightTextVariant.Detail,
                                        lighten = true,
                                        monospace = true,
                                        modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
                                    )
                                }
                                r.sections.forEach { sec ->
                                    if (sec.pos.isNotBlank()) {
                                        LightText(
                                            text = sec.pos,
                                            variant = LightTextVariant.Subheading,
                                            modifier = Modifier.padding(
                                                top = 2f.gridUnitsAsDp(),
                                                bottom = 0.5f.gridUnitsAsDp(),
                                            ),
                                        )
                                    }
                                    sec.glosses.forEachIndexed { i, g ->
                                        LightText(
                                            text = "${i + 1}. $g",
                                            variant = LightTextVariant.Copy,
                                            modifier = Modifier.padding(vertical = 0.5f.gridUnitsAsDp()),
                                        )
                                    }
                                }
                                if (r.synonyms.isNotEmpty()) {
                                    LightText(
                                        text = "Synonyms",
                                        variant = LightTextVariant.Subheading,
                                        modifier = Modifier.padding(
                                            top = 2f.gridUnitsAsDp(),
                                            bottom = 0.5f.gridUnitsAsDp(),
                                        ),
                                    )
                                    r.synonyms.forEach { syn ->
                                        LightText(
                                            text = syn,
                                            variant = LightTextVariant.Detail,
                                            lighten = true,
                                            modifier = Modifier.padding(vertical = 0.5f.gridUnitsAsDp()),
                                        )
                                    }
                                }
                                if (r.antonyms.isNotEmpty()) {
                                    LightText(
                                        text = "Antonyms",
                                        variant = LightTextVariant.Subheading,
                                        modifier = Modifier.padding(
                                            top = 2f.gridUnitsAsDp(),
                                            bottom = 0.5f.gridUnitsAsDp(),
                                        ),
                                    )
                                    r.antonyms.forEach { ant ->
                                        LightText(
                                            text = ant,
                                            variant = LightTextVariant.Detail,
                                            lighten = true,
                                            modifier = Modifier.padding(vertical = 0.5f.gridUnitsAsDp()),
                                        )
                                    }
                                }
                            }
                        }
                        is DictState.Error -> {
                            LightText(text = s.message, variant = LightTextVariant.Copy)
                        }
                    }
                }
            }
        }
    }

    private fun openSearch() {
        navigateTo(
            screenFactory = { activity -> SearchEditorScreen(activity, _lastQuery) },
            resultCallback = { word: String? ->
                _lastQuery = word ?: ""
                viewModel.search(_lastQuery)
            },
        )
    }

    override fun onScreenDestroy() {
        super.onScreenDestroy()
    }

    private var _lastQuery: String by mutableStateOf("")
}
