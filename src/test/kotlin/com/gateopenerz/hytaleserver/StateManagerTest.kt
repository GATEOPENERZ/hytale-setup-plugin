package com.gateopenerz.hytaleserver

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class StateManagerTest {

    private val stateManager = StateManager()

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `readStateOrNull returns null for missing file`() {
        assertNull(stateManager.readStateOrNull(tempDir))
    }

    @Test
    fun `readStateOrNull returns null for invalid JSON`() {
        File(tempDir, ".hytale-setup-state.json").writeText("not valid json {{{")
        assertNull(stateManager.readStateOrNull(tempDir))
    }

    @Test
    fun `readStateOrNull returns null when channel is missing`() {
        File(tempDir, ".hytale-setup-state.json").writeText("""{"version": "1.0"}""")
        assertNull(stateManager.readStateOrNull(tempDir))
    }

    @Test
    fun `readStateOrNull returns null when version is missing`() {
        File(tempDir, ".hytale-setup-state.json").writeText("""{"channel": "release"}""")
        assertNull(stateManager.readStateOrNull(tempDir))
    }

    @Test
    fun `readStateOrNull parses valid complete state`() {
        val json = """
            {
                "channel": "release",
                "version": "1.0.0",
                "resolvedVersion": "1.0.0",
                "detectedVersion": "1.0.0",
                "serverJarSize": 12345,
                "serverJarLastModified": 67890,
                "serverJarSha256": "abc123",
                "assetsZipSize": 54321,
                "assetsZipLastModified": 98765,
                "assetsZipSha256": "def456"
            }
        """.trimIndent()
        File(tempDir, ".hytale-setup-state.json").writeText(json)

        val state = stateManager.readStateOrNull(tempDir)
        assertNotNull(state)
        assertEquals("release", state!!.channel)
        assertEquals("1.0.0", state.version)
        assertEquals("1.0.0", state.resolvedVersion)
        assertEquals("1.0.0", state.detectedVersion)
        assertEquals(12345L, state.serverJarSize)
        assertEquals(67890L, state.serverJarLastModified)
        assertEquals("abc123", state.serverJarSha256)
        assertEquals(54321L, state.assetsZipSize)
        assertEquals(98765L, state.assetsZipLastModified)
        assertEquals("def456", state.assetsZipSha256)
    }

    @Test
    fun `readStateOrNull falls back to version for missing resolved and detected`() {
        val json = """{"channel": "release", "version": "1.0.0"}"""
        File(tempDir, ".hytale-setup-state.json").writeText(json)

        val state = stateManager.readStateOrNull(tempDir)
        assertNotNull(state)
        assertEquals("1.0.0", state!!.resolvedVersion)
        assertEquals("1.0.0", state.detectedVersion)
    }

    @Test
    fun `readStateOrNull handles numeric version strings`() {
        val json = """{"channel": "pre-release", "version": "2026.01.29"}"""
        File(tempDir, ".hytale-setup-state.json").writeText(json)

        val state = stateManager.readStateOrNull(tempDir)
        assertNotNull(state)
        assertEquals("pre-release", state!!.channel)
        assertEquals("2026.01.29", state.version)
    }

    @Test
    fun `sha256 produces correct hash for known content`() {
        val testFile = File(tempDir, "test.txt")
        testFile.writeText("hello world")

        val hash = stateManager.sha256(testFile)
        assertEquals("b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9", hash)
    }

    @Test
    fun `sha256 produces correct hash for empty file`() {
        val testFile = File(tempDir, "empty.txt")
        testFile.writeText("")

        val hash = stateManager.sha256(testFile)
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", hash)
    }

    @Test
    fun `fileMatchesState returns false when expected values are null`() {
        val testFile = File(tempDir, "test.txt")
        testFile.writeText("content")

        assertFalse(stateManager.fileMatchesState(testFile, null, null, null))
        assertFalse(stateManager.fileMatchesState(testFile, 7L, null, "abc"))
        assertFalse(stateManager.fileMatchesState(testFile, 7L, 123L, null))
        assertFalse(stateManager.fileMatchesState(testFile, null, 123L, "abc"))
    }

    @Test
    fun `fileMatchesState returns true when size and mtime match`() {
        val testFile = File(tempDir, "test.txt")
        testFile.writeText("content")

        assertTrue(stateManager.fileMatchesState(testFile, testFile.length(), testFile.lastModified(), "irrelevant"))
    }

    @Test
    fun `fileMatchesState falls back to sha256 when mtime differs`() {
        val testFile = File(tempDir, "test.txt")
        testFile.writeText("hello world")

        val sha = stateManager.sha256(testFile)
        assertTrue(stateManager.fileMatchesState(testFile, testFile.length(), 0L, sha))
    }

    @Test
    fun `fileMatchesState returns false when sha256 does not match`() {
        val testFile = File(tempDir, "test.txt")
        testFile.writeText("hello world")

        assertFalse(stateManager.fileMatchesState(testFile, testFile.length(), 0L, "wrong_hash"))
    }

    @Test
    fun `writeState creates readable state file`() {
        stateManager.writeState(tempDir, "release", "1.0.0", "1.0.0")

        val state = stateManager.readStateOrNull(tempDir)
        assertNotNull(state)
        assertEquals("release", state!!.channel)
        assertEquals("1.0.0", state.resolvedVersion)
        assertEquals("1.0.0", state.detectedVersion)
    }

    @Test
    fun `writeState includes file metadata when files exist`() {
        val serverDir = File(tempDir, "Server")
        serverDir.mkdirs()
        val serverJar = File(serverDir, "HytaleServer.jar")
        serverJar.writeText("fake jar content")

        val assetsZip = File(tempDir, "Assets.zip")
        assetsZip.writeText("fake assets content")

        stateManager.writeState(tempDir, "release", "1.0.0", "1.0.0")

        val state = stateManager.readStateOrNull(tempDir)
        assertNotNull(state)
        assertNotNull(state!!.serverJarSize)
        assertNotNull(state.serverJarLastModified)
        assertNotNull(state.serverJarSha256)
        assertNotNull(state.assetsZipSize)
        assertNotNull(state.assetsZipLastModified)
        assertNotNull(state.assetsZipSha256)
    }
}
