package com.gateopenerz.hytaleserver

import java.io.File

internal object ScriptPatcher {

    fun writeJvmArgsFile(serverDir: File, jvmArgs: List<String>) {
        File(serverDir, ".hytale-jvm.args").writeText(jvmArgs.joinToString(" "))
    }

    fun ensureScriptsPatched(serverDir: File) {
        patchStartBat(File(serverDir, "start.bat"))
        patchStartSh(File(serverDir, "start.sh"))
    }

    private fun patchStartBat(startBat: File) {
        if (!startBat.exists()) return

        val text = startBat.readText()
        var updated = text

        if (!updated.contains(".hytale-jvm.args", ignoreCase = true)) {
            val anchor = "rem Default server arguments"
            if (updated.contains(anchor)) {
                updated = updated.replace(
                    anchor,
                    "rem Extra JVM args injected by Gradle\r\nset USER_JVM_ARGS=\r\nif exist \"%SCRIPT_DIR%\\.hytale-jvm.args\" (\r\n    for /f \"usebackq delims=\" %%A in (\"%SCRIPT_DIR%\\.hytale-jvm.args\") do set USER_JVM_ARGS=%%A\r\n)\r\n\r\nrem Default server arguments"
                )
            } else {
                println("Warning: Could not patch start.bat — expected anchor '$anchor' not found")
            }
        }

        if (!updated.contains("%USER_JVM_ARGS%", ignoreCase = true)) {
            val anchor = "java %JVM_ARGS% -jar HytaleServer.jar"
            if (updated.contains(anchor)) {
                updated = updated.replace(anchor, "java %JVM_ARGS% %USER_JVM_ARGS% -jar HytaleServer.jar")
            } else {
                println("Warning: Could not patch start.bat — expected anchor '$anchor' not found")
            }
        }

        if (updated != text) startBat.writeText(updated)
    }

    private fun patchStartSh(startSh: File) {
        if (!startSh.exists()) return

        val text = startSh.readText()
        var updated = text

        val cdAnchor = "cd \"\$SCRIPT_DIR\""
        if (!updated.contains(".hytale-jvm.args")) {
            if (updated.contains(cdAnchor)) {
                updated = updated.replace(
                    cdAnchor,
                    "cd \"\\\$SCRIPT_DIR\"\n\nUSER_JVM_ARGS=\"\"\nif [ -f \"\\\$SCRIPT_DIR/.hytale-jvm.args\" ]; then\n  read -r USER_JVM_ARGS < \"\\\$SCRIPT_DIR/.hytale-jvm.args\"\nfi"
                )
            } else {
                println("Warning: Could not patch start.sh — expected anchor 'cd \"\$SCRIPT_DIR\"' not found")
            }
        }

        val wanted = "java \\\$JVM_ARGS \\\$USER_JVM_ARGS -jar HytaleServer.jar"
        if (!updated.contains(wanted)) {
            val javaAnchor = "java \$JVM_ARGS -jar HytaleServer.jar"
            if (updated.contains(javaAnchor)) {
                updated = updated.replace(javaAnchor, "java \$JVM_ARGS \$USER_JVM_ARGS -jar HytaleServer.jar")
            } else {
                println("Warning: Could not patch start.sh — expected anchor 'java \$JVM_ARGS ...' not found")
            }
        }

        if (updated != text) startSh.writeText(updated)
        startSh.setExecutable(true)
    }
}
