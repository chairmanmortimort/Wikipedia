package com.mortimort.zork

import de.onyxbits.textfiction.zengine.ZMachine3
import de.onyxbits.textfiction.zengine.ZScreen
import de.onyxbits.textfiction.zengine.ZStatus

/**
 * Thin Kotlin wrapper around the vendored pure-Java Z-machine interpreter
 * (de.onyxbits.textfiction.zengine, Apache-2.0). Drives the story and exposes
 * plain String I/O so the Light UI never touches the engine internals.
 */
class ZorkEngine(story: ByteArray) {
    private val screen = ZScreen()
    private val status = ZStatus()
    private val machine = ZMachine3(screen, status, story)

    init {
        machine.restart()
        machine.run()
    }

    /** Returns the text printed since the last call, then clears the buffer. */
    fun drainOutput(): String {
        val w = machine.window[0]
        val len = w.cursor
        val text = String(w.frameBuffer, 0, len)
        w.retrieved()
        return text
    }

    fun statusLine(): String =
        "${status.location ?: ""}   Score ${status.score}   Turns ${status.turns}"

    /** Feed one command line; the engine runs until it next waits for input. */
    fun submit(line: String) {
        machine.fillInputBuffer((line + "\n").toCharArray())
        machine.run()
    }

    fun restart() {
        machine.restart()
        machine.run()
    }
}
