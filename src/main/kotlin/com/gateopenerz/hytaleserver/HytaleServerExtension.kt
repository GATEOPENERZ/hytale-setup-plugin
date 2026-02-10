package com.gateopenerz.hytaleserver

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

abstract class HytaleServerExtension {
    abstract val serverDir: Property<String>
    abstract val channel: Property<String>
    abstract val version: Property<String>
    abstract val jvmArgs: ListProperty<String>
    abstract val terminal: ListProperty<String>
    abstract val javaVersion: Property<Int>

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