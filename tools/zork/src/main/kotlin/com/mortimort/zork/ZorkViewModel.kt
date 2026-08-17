package com.mortimort.zork

import androidx.compose.runtime.*
import com.thelightphone.sdk.LightViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Owns the Z-machine engine and the visible transcript. One [LightViewModel]
 * drives the [ZorkScreen].
 *
 * The transcript is stored newest-first so the most recent response appears at
 * the top of the screen without scrolling.
 */
class ZorkViewModel : LightViewModel<Unit>() {

    private val engine = ZorkEngine(StoryData.bytes)

    val titleCard: String

    private val roomDescription: String

    init {
        val intro = engine.drainOutput()
        val split = intro.indexOf("\n\n")
        if (split >= 0) {
            titleCard = intro.substring(0, split)
            roomDescription = intro.substring(split + 2)
        } else {
            titleCard = intro
            roomDescription = ""
        }
    }

    private val _state = MutableStateFlow(
        ZorkState(
            transcript = roomDescription,
            statusLine = engine.statusLine(),
            input = "",
        ),
    )
    val state: StateFlow<ZorkState> = _state

    fun submit(line: String) {
        val cmd = line.trim()
        if (cmd.isEmpty()) return
        engine.submit(cmd)
        val response = engine.drainOutput()
        _state.value = _state.value.copy(
            transcript = "> $cmd\n$response\n\n${_state.value.transcript}",
            statusLine = engine.statusLine(),
            input = "",
        )
    }

    fun setInput(s: String) {
        _state.value = _state.value.copy(input = s)
    }

    /** Reset the engine to the initial room and clear the transcript. */
    fun restart() {
        engine.restart()
        val intro = engine.drainOutput()
        val split = intro.indexOf("\n\n")
        val room = if (split >= 0) intro.substring(split + 2) else ""
        _state.value = ZorkState(
            transcript = room,
            statusLine = engine.statusLine(),
            input = "",
        )
    }
}

data class ZorkState(
    val transcript: String,
    val statusLine: String,
    val input: String,
)
