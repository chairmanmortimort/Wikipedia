package com.mortimort.translator

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.mortimort.translator.data.Language
import com.mortimort.translator.data.TranslationResult
import com.mortimort.translator.data.TranslationState
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SealedLightContext
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextInputEditor
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import com.thelightphone.sdk.rememberKeyboardOptions

@InitialScreen
class TranslatorHomeScreen(
    sealedActivity: SealedLightActivity,
) : LightScreen<Unit, TranslatorViewModel>(sealedActivity) {

    override val viewModelClass: Class<TranslatorViewModel>
        get() = TranslatorViewModel::class.java

    override fun createViewModel(): TranslatorViewModel =
        TranslatorViewModel(lightContext)

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
                    leftButton = null,
                    center = LightTopBarCenter.Text("Translator"),
                    rightButton = LightBarButton.LightIcon(
                        icon = LightIcons.SETTINGS,
                        onClick = {
                            val s = state
                            val (src, tgt) = when (s) {
                                is TranslationState.Ready -> s.sourceLang to s.targetLang
                                is TranslationState.Translating -> s.sourceLang to s.targetLang
                                else -> Language.ENGLISH to Language.SPANISH
                            }
                            navigateTo(
                                screenFactory = { activity -> TranslatorSettingsScreen(activity, src, tgt) },
                                resultCallback = { goBack() },
                            )
                        },
                    ),
                )

                Box(modifier = Modifier.weight(1f)) {
                    when (val s = state) {
                        is TranslationState.Ready -> ReadyBody(s)
                        is TranslationState.Translating -> TranslatingBody(s)
                        else -> Unit
                    }
                }
            }
        }
    }

    @Composable
    private fun ReadyBody(state: TranslationState.Ready) {
        Column(modifier = Modifier.padding(2f.gridUnitsAsDp())) {
            // Language chips — tappable to cycle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                LanguageChip(
                    label = state.sourceLang.displayName,
                    onClick = { openLanguagePicker(isSource = true, state.sourceLang) },
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .lightClickable { viewModel.swapLanguages() }
                        .padding(horizontal = 1f.gridUnitsAsDp()),
                    contentAlignment = Alignment.Center,
                ) {
                    LightText(
                        text = "\u21C5",
                        variant = LightTextVariant.Copy,
                        lighten = true,
                    )
                }
                LanguageChip(
                    label = state.targetLang.displayName,
                    onClick = { openLanguagePicker(isSource = false, state.targetLang) },
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(1f.gridUnitsAsDp()))

            // Input area — tappable to open editor
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .lightClickable { openInputEditor(state.inputText) }
                    .padding(vertical = 1f.gridUnitsAsDp()),
            ) {
                LightText(
                    text = state.inputText.ifBlank { "Tap to type" },
                    variant = LightTextVariant.Copy,
                    lighten = state.inputText.isBlank(),
                )
            }

            // Error message
            state.error?.let {
                Spacer(modifier = Modifier.height(2f.gridUnitsAsDp()))
                LightText(
                    text = it,
                    variant = LightTextVariant.Detail,
                    lighten = true,
                )
            }

            // Translated result
            state.result?.let {
                Spacer(modifier = Modifier.height(2f.gridUnitsAsDp()))
                LightScrollView {
                    Column(modifier = Modifier.padding(2f.gridUnitsAsDp())) {
                        LightText(
                            text = state.result.translatedText,
                            variant = LightTextVariant.Heading,
                            align = TextAlign.Start,
                        )
                        Spacer(modifier = Modifier.height(1f.gridUnitsAsDp()))
                        LightText(
                            text = "Translation",
                            variant = LightTextVariant.Detail,
                            lighten = true,
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun TranslatingBody(state: TranslationState.Translating) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(2f.gridUnitsAsDp()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LightText(
                text = "Translating...",
                variant = LightTextVariant.Subheading,
            )
            Spacer(modifier = Modifier.height(1f.gridUnitsAsDp()))
            LightText(
                text = "${state.sourceLang.displayName} -> ${state.targetLang.displayName}",
                variant = LightTextVariant.Detail,
                lighten = true,
            )
            Spacer(modifier = Modifier.height(1f.gridUnitsAsDp()))
            LightText(
                text = state.inputText.ifBlank { "..." },
                variant = LightTextVariant.Paragraph,
                align = TextAlign.Center,
            )
        }
    }

    private fun openInputEditor(initialText: String) {
        navigateTo(
            screenFactory = { activity -> TranslatorInputScreen(activity, initialText) },
            resultCallback = { result: String? ->
                result?.let {
                    viewModel.setText(it)
                    viewModel.translate()
                }
            },
        )
    }

    private fun openLanguagePicker(isSource: Boolean, current: Language) {
        navigateTo(
            screenFactory = { activity -> TranslatorLanguagePickerScreen(activity, isSource, current) },
            resultCallback = { picked: Language? ->
                picked?.let {
                    if (isSource) viewModel.setSourceLang(it) else viewModel.setTargetLang(it)
                }
            },
        )
    }
}

@Composable
private fun LanguageChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .lightClickable(onClick = onClick)
            .padding(vertical = 0.5f.gridUnitsAsDp()),
        contentAlignment = Alignment.Center,
    ) {
        LightText(
            text = label,
            variant = LightTextVariant.Copy,
        )
    }
}

class TranslatorInputScreen(
    sealedActivity: SealedLightActivity,
    private val initial: String,
) : SimpleLightScreen<String>(sealedActivity) {

    @Composable
    override fun Content() {
        val state = androidx.compose.foundation.text.input.rememberTextFieldState(initial)
        val keyboardOptionsFlow = rememberKeyboardOptions()

        LightTheme(colors = LightThemeController.colors.value) {
            LightTextInputEditor(
                title = "Translate",
                state = state,
                onSubmit = { text ->
                    goBack(text.toString())
                },
                onBack = {
                    goBack(null)
                },
                keyboardOptionsFlow = keyboardOptionsFlow,
                modifier = Modifier.fillMaxSize(),
                submitLabel = "Done",
            )
        }
    }
}

class TranslatorSettingsScreen(
    sealedActivity: SealedLightActivity,
    private val sourceLang: Language,
    private val targetLang: Language,
) : SimpleLightScreen<Unit>(sealedActivity) {

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()

        LightTheme(colors = themeColors) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(
                        icon = LightIcons.BACK,
                        onClick = { goBack() },
                    ),
                    center = LightTopBarCenter.Text("About"),
                )

                LightScrollView {
                    Column(modifier = Modifier.padding(2f.gridUnitsAsDp())) {
                        LightText(
                            text = "Translator",
                            variant = LightTextVariant.Heading,
                        )
                        Spacer(modifier = Modifier.height(1f.gridUnitsAsDp()))

                        LightText(
                            text = "Languages",
                            variant = LightTextVariant.Copy,
                            lighten = true,
                        )
                        Spacer(modifier = Modifier.height(0.25f.gridUnitsAsDp()))
                        LightText(
                            text = "${sourceLang.displayName} → ${targetLang.displayName}",
                            variant = LightTextVariant.Copy,
                        )
                        Spacer(modifier = Modifier.height(1f.gridUnitsAsDp()))

                        LightText(
                            text = "About",
                            variant = LightTextVariant.Copy,
                            lighten = true,
                        )
                        Spacer(modifier = Modifier.height(0.25f.gridUnitsAsDp()))
                        LightText(
                            text = "Free translation powered by MyMemory. No account, no API key.",
                            variant = LightTextVariant.Copy,
                            lighten = true,
                        )
                        Spacer(modifier = Modifier.height(1f.gridUnitsAsDp()))

                        LightText(
                            text = "Source",
                            variant = LightTextVariant.Copy,
                            lighten = true,
                        )
                        Spacer(modifier = Modifier.height(0.25f.gridUnitsAsDp()))
                        LightText(
                            text = "github.com/chairmanmortimort",
                            variant = LightTextVariant.Detail,
                            lighten = true,
                        )
                    }
                }
            }
        }
    }
}

class TranslatorLanguagePickerScreen(
    sealedActivity: SealedLightActivity,
    private val isSource: Boolean,
    private val current: Language,
) : SimpleLightScreen<Language>(sealedActivity) {

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()

        LightTheme(colors = themeColors) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(
                        icon = LightIcons.BACK,
                        onClick = { goBack(null) },
                    ),
                    center = LightTopBarCenter.Text(
                        if (isSource) "Source language" else "Target language"
                    ),
                )

                LightScrollView {
                    Column(modifier = Modifier.padding(2f.gridUnitsAsDp())) {
                        Language.values().forEach { lang ->
                            val selected = lang == current
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .lightClickable { goBack(lang) }
                                    .padding(vertical = 0.75f.gridUnitsAsDp()),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                LightText(
                                    text = lang.displayName,
                                    variant = if (selected) LightTextVariant.Subheading else LightTextVariant.Copy,
                                    lighten = !selected,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
