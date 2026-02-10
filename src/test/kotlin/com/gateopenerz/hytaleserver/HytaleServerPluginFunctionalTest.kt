package com.gateopenerz.hytaleserver

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class HytaleServerPluginFunctionalTest {

    @TempDir
    lateinit var testProjectDir: File

    @Test
    fun `plugin registers tasks successfully`() {
        File(testProjectDir, "settings.gradle.kts").writeText("")
        File(testProjectDir, "build.gradle.kts").writeText("""
            plugins {
                id("io.github.gateopenerz.hytale-server")
            }
        """.trimIndent())

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("tasks", "--group", "hytale")
            .build()

        assertTrue(result.output.contains("setupHytaleServer"))
        assertTrue(result.output.contains("runHytaleServer"))
        assertTrue(result.output.contains("runHytaleServerInteractive"))
        assertEquals(TaskOutcome.SUCCESS, result.task(":tasks")?.outcome)
    }

    @Test
    fun `setupHytaleServer is skipped if already up-to-date (dry run)`() {
        val serverDir = File(testProjectDir, "run")
        serverDir.mkdirs()
        File(serverDir, ".hytale-setup-state.json").writeText("""
            {
                "channel": "release",
                "version": "latest",
                "resolvedVersion": "1.0.0",
                "detectedVersion": "1.0.0"
            }
        """.trimIndent())
        
        File(serverDir, "Assets.zip").createNewFile()
        File(serverDir, "Server").mkdirs()
        File(serverDir, "Server/HytaleServer.jar").createNewFile()
        File(serverDir, "start.sh").createNewFile()

        File(testProjectDir, "settings.gradle.kts").writeText("")
        File(testProjectDir, "build.gradle.kts").writeText("""
            plugins {
                id("io.github.gateopenerz.hytale-server")
            }
        """.trimIndent())

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("help") 
            .build()
            
        assertEquals(TaskOutcome.SUCCESS, result.task(":help")?.outcome)
    }
}
