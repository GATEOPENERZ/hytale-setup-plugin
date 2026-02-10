package com.gateopenerz.hytaleserver

enum class Terminal(val command: List<String>) {
    GHOSTTY(listOf("bash", "-lc", "ghostty -e bash -lc '\$commandLine'")),
    KITTY(listOf("bash", "-lc", "kitty --hold bash -lc '\$commandLine'")),
    KONSOLE(listOf("bash", "-lc", "konsole -e bash -lc '\$commandLine; exec bash'")),
    GNOME_TERMINAL(listOf("bash", "-lc", "gnome-terminal -- bash -lc '\$commandLine; exec bash'")),
    X_TERMINAL_EMULATOR(listOf("bash", "-lc", "x-terminal-emulator -e \"\$commandLine\"")),
    XTERM(listOf("bash", "-lc", "xterm -e \"\$commandLine\""))
}
