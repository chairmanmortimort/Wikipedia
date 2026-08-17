package com.mortimort.zork

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.rememberKeyboardOptions
import com.thelightphone.sdk.ui.*

/**
 * Zork I for LightOS — a calm, monochrome text adventure.
 *
 * Driven by [ZorkViewModel]. Shows the transcript newest-first, a command
 * input that opens the LP3 keyboard editor, and a gear icon to reach the
 * About page.
 */
@InitialScreen
class ZorkScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, ZorkViewModel>(sealedActivity) {

    override val viewModelClass: Class<ZorkViewModel>
        get() = ZorkViewModel::class.java

    override fun createViewModel(): ZorkViewModel = ZorkViewModel()

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
                    center = LightTopBarCenter.Text("Zork I"),
                    rightButton = LightBarButton.LightIcon(
                        icon = LightIcons.SETTINGS,
                        onClick = {
                            navigateTo(
                                screenFactory = { activity -> ZorkAboutScreen(activity, viewModel::restart) },
                                resultCallback = { },
                            )
                        },
                    ),
                )

                Box(modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        LightText(
                            text = state.statusLine,
                            variant = LightTextVariant.Detail,
                            lighten = true,
                            align = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 0.5f.gridUnitsAsDp()),
                        )
                        LightScrollView {
                            LightText(
                                text = state.transcript,
                                variant = LightTextVariant.Copy,
                                align = TextAlign.Left,
                                modifier = Modifier.padding(horizontal = 2f.gridUnitsAsDp()),
                            )
                        }
                    }
                }

                LightTextField(
                    label = "Command",
                    value = state.input,
                    placeholder = "Tap to type a command",
                    onClick = {
                        navigateTo(
                            screenFactory = { activity -> ZorkInputScreen(activity, state.input) },
                            resultCallback = { result: String? -> result?.let { viewModel.submit(it) } },
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2f.gridUnitsAsDp(), vertical = 1f.gridUnitsAsDp()),
                )
            }
        }
    }
}

/**
 * Full-screen LP3 keyboard editor for a single Zork command. Returns the typed
 * text (or null on back) via the screen result.
 */
class ZorkInputScreen(
    sealedActivity: SealedLightActivity,
    private val initial: String,
) : SimpleLightScreen<String>(sealedActivity) {

    @Composable
    override fun Content() {
        val colors by LightThemeController.colors.collectAsState()
        val state = rememberTextFieldState(initial)
        val keyboardOptionsFlow = rememberKeyboardOptions()
        LightTheme(colors = colors) {
            LightTextInputEditor(
                title = "Command",
                state = state,
                keyboardOptionsFlow = keyboardOptionsFlow,
                onSubmit = { result: CharSequence -> goBack(result.toString().trim()) },
                onBack = { goBack(null) },
                modifier = Modifier.background(LightThemeTokens.colors.background),
            )
        }
    }
}

/**
 * About page showing the Zork I title card, engine license, and SDK info.
 * Reachable via the gear icon on the main screen.
 *
 * Uses a plain Compose [Row] + [Modifier.clickable] instead of the SDK's
 * [LightTopBar]/[LightBarButton] because those route through
 * [com.thelightphone.sdk.ui.lightClickable], which no-ops when haptics are
 * disabled (the default on the LP3 emulator).
 */
class ZorkAboutScreen(
    sealedActivity: SealedLightActivity,
    private val restart: () -> Unit,
) : SimpleLightScreen<Unit>(sealedActivity) {

    @Composable
    override fun Content() {
        val colors by LightThemeController.colors.collectAsState()
        LightTheme(colors = colors) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top bar row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(LightThemeTokens.colors.background),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { goBack(null) },
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        LightText(
                            text = "←",
                            variant = LightTextVariant.Paragraph,
                            modifier = Modifier.padding(start = 16.dp),
                        )
                    }
                    LightText(
                        text = "About",
                        variant = LightTextVariant.Paragraph,
                        modifier = Modifier
                            .weight(1f)
                            .wrapContentWidth(Alignment.CenterHorizontally),
                    )
                }
                // Scrollable content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 2f.gridUnitsAsDp(), vertical = 2f.gridUnitsAsDp()),
                ) {
                    Spacer(Modifier.height(1f.gridUnitsAsDp()))
                    LightText(
                        text = AboutText.CONTENT,
                        variant = LightTextVariant.Copy,
                        align = TextAlign.Left,
                    )
                    Spacer(Modifier.height(2f.gridUnitsAsDp()))
                    // Restart button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(LightThemeTokens.colors.background),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { restart(); goBack(null) },
                            contentAlignment = Alignment.Center,
                        ) {
                            LightText(
                                text = "Restart",
                                variant = LightTextVariant.Paragraph,
                            )
                        }
                    }
                }
            }
        }
    }
}
