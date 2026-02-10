package com.gateopenerz.hytaleserver

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

abstract class HytaleServerExtension {
    abstract val serverDir: Property<String>
    abstract val channel: Property<String>
    abstract val version: Property<String>
    abstract val jvmArgs: ListProperty<String>
    abstract val terminal: ListProperty<String>

    fun ListProperty<String>.set(terminal: Terminal) {
        set(terminal.command)
    }

    val ghostty: Terminal get() = Terminal.GHOSTTY
    val kitty: Terminal get() = Terminal.KITTY
    val konsole: Terminal get() = Terminal.KONSOLE
    val gnomeTerminal: Terminal get() = Terminal.GNOME_TERMINAL
    val xTerminalEmulator: Terminal get() = Terminal.X_TERMINAL_EMULATOR
    val xterm: Terminal get() = Terminal.XTERM

    fun useTerminal(terminal: Terminal) {
        this.terminal.set(terminal.command)
    }
}

enum class Terminal(val command: List<String>) {
    GHOSTTY(listOf("bash", "-lc", "ghostty -e bash -lc '\$commandLine'")),
    KITTY(listOf("bash", "-lc", "kitty --hold bash -lc '\$commandLine'")),
    KONSOLE(listOf("bash", "-lc", "konsole -e bash -lc '\$commandLine; exec bash'")),
    GNOME_TERMINAL(listOf("bash", "-lc", "gnome-terminal -- bash -lc '\$commandLine; exec bash'")),
    X_TERMINAL_EMULATOR(listOf("bash", "-lc", "x-terminal-emulator -e \"\$commandLine\"")),
    XTERM(listOf("bash", "-lc", "xterm -e \"\$commandLine\""))
}