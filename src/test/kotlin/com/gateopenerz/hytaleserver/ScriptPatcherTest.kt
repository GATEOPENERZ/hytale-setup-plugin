package com.gateopenerz.hytaleserver

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ScriptPatcherTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `patchStartBat injects JVM args block`() {
        val startBat = File(tempDir, "start.bat")
        startBat.writeText("@echo off\r\nrem Default server arguments\r\njava %JVM_ARGS% -jar HytaleServer.jar\r\n")

        ScriptPatcher.ensureScriptsPatched(tempDir)

        val patched = startBat.readText()
        assertTrue(patched.contains(".hytale-jvm.args"))
        assertTrue(patched.contains("USER_JVM_ARGS"))
        assertTrue(patched.contains("%USER_JVM_ARGS%"))
        assertTrue(patched.contains("rem Default server arguments"))
    }

    @Test
    fun `patchStartBat is idempotent`() {
        val startBat = File(tempDir, "start.bat")
        startBat.writeText("@echo off\r\nrem Default server arguments\r\njava %JVM_ARGS% -jar HytaleServer.jar\r\n")

        ScriptPatcher.ensureScriptsPatched(tempDir)
        val firstPatch = startBat.readText()

        ScriptPatcher.ensureScriptsPatched(tempDir)
        val secondPatch = startBat.readText()

        assertEquals(firstPatch, secondPatch)
    }

    @Test
    fun `patchStartBat does nothing when file is missing`() {
        ScriptPatcher.ensureScriptsPatched(tempDir)
        assertFalse(File(tempDir, "start.bat").exists())
    }

    @Test
    fun `patchStartSh injects JVM args block`() {
        val startSh = File(tempDir, "start.sh")
        startSh.writeText("#!/bin/bash\ncd \"\$SCRIPT_DIR\"\njava \$JVM_ARGS -jar HytaleServer.jar\n")

        ScriptPatcher.ensureScriptsPatched(tempDir)

        val patched = startSh.readText()
        assertTrue(patched.contains(".hytale-jvm.args"))
        assertTrue(patched.contains("USER_JVM_ARGS"))
    }

    @Test
    fun `patchStartSh is idempotent`() {
        val startSh = File(tempDir, "start.sh")
        startSh.writeText("#!/bin/bash\ncd \"\$SCRIPT_DIR\"\njava \$JVM_ARGS -jar HytaleServer.jar\n")

        ScriptPatcher.ensureScriptsPatched(tempDir)
        val firstPatch = startSh.readText()

        ScriptPatcher.ensureScriptsPatched(tempDir)
        val secondPatch = startSh.readText()

        assertEquals(firstPatch, secondPatch)
    }

    @Test
    fun `patchStartSh sets executable permission`() {
        val startSh = File(tempDir, "start.sh")
        startSh.writeText("#!/bin/bash\ncd \"\$SCRIPT_DIR\"\njava \$JVM_ARGS -jar HytaleServer.jar\n")

        ScriptPatcher.ensureScriptsPatched(tempDir)

        assertTrue(startSh.canExecute())
    }

    @Test
    fun `writeJvmArgsFile creates args file`() {
        ScriptPatcher.writeJvmArgsFile(tempDir, listOf("-Xmx4G", "-Xms2G"))

        val content = File(tempDir, ".hytale-jvm.args").readText()
        assertEquals("-Xmx4G -Xms2G", content)
    }

    @Test
    fun `writeJvmArgsFile handles empty list`() {
        ScriptPatcher.writeJvmArgsFile(tempDir, emptyList())

        val content = File(tempDir, ".hytale-jvm.args").readText()
        assertEquals("", content)
    }
}
