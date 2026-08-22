package com.mortimort.dictionary

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.rememberKeyboardOptions
import com.thelightphone.sdk.ui.*

/** Full-screen text editor for entering a search query. */
class SearchEditorScreen(
    sealedActivity: SealedLightActivity,
    val initialText: String,
) : SimpleLightScreen<String>(sealedActivity) {

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val state = androidx.compose.foundation.text.input.rememberTextFieldState(initialText)
        val kbFlow = rememberKeyboardOptions()

        LightTheme(colors = themeColors) {
            LightTextInputEditor(
                title = "",
                state = state,
                keyboardOptionsFlow = kbFlow,
                onSubmit = { result: CharSequence ->
                    goBack(result.toString())
                },
                onBack = { goBack(null) },
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            )
        }
    }
}
