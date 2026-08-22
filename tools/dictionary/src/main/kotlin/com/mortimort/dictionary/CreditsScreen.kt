package com.mortimort.dictionary

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.*

/**
 * Settings screen: credits and license info for the live data source.
 * Opens from the gear icon on the home screen top bar.
 */
class CreditsScreen(
    sealedActivity: SealedLightActivity,
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
                    center = LightTopBarCenter.Text("Dictionary"),
                    leftButton = LightBarButton.LightIcon(
                        icon = LightIcons.BACK,
                        onClick = { goBack(null) },
                    ),
                )
                LightScrollView(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(1f.gridUnitsAsDp()),
                ) {
                    LightText(
                        text = "Definitions",
                        variant = LightTextVariant.Subheading,
                        modifier = Modifier.padding(top = 1f.gridUnitsAsDp()),
                    )
                    LightText(
                        text = "English Wiktionary — a collaborative, " +
                            "open-content dictionary. Text is licensed under " +
                            "CC BY-SA 4.0 by the Wiktionary contributors.",
                        variant = LightTextVariant.Detail,
                        lighten = true,
                        modifier = Modifier.padding(vertical = 0.5f.gridUnitsAsDp()),
                    )
                    LightText(
                        text = "Data source",
                        variant = LightTextVariant.Subheading,
                        modifier = Modifier.padding(top = 2f.gridUnitsAsDp()),
                    )
                    LightText(
                        text = "Definitions are fetched live from " +
                            "en.wiktionary.org via the MediaWiki API. " +
                            "Extraction by kaikki.org.",
                        variant = LightTextVariant.Detail,
                        lighten = true,
                        modifier = Modifier.padding(vertical = 0.5f.gridUnitsAsDp()),
                    )
                    LightText(
                        text = "Tool",
                        variant = LightTextVariant.Subheading,
                        modifier = Modifier.padding(top = 2f.gridUnitsAsDp()),
                    )
                    LightText(
                        text = "Built with the Light Phone SDK. " +
                            "© 2026 chairmanmortimort.",
                        variant = LightTextVariant.Detail,
                        lighten = true,
                        modifier = Modifier.padding(vertical = 0.5f.gridUnitsAsDp()),
                    )
                }
            }
        }
    }
}
