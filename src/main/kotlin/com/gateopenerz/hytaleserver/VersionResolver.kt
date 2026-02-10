package com.gateopenerz.hytaleserver

import java.io.File
import java.io.IOException
import java.net.URI
import java.util.jar.JarFile

internal class VersionResolver {

    fun detectVersion(serverDir: File): String {
        val jarFile = File(serverDir, "Server/HytaleServer.jar")
        if (!jarFile.exists()) return "unknown"

        return try {
            JarFile(jarFile).use { jar ->
                val manifest = jar.manifest
                manifest?.mainAttributes?.getValue("Implementation-Version")
                    ?: manifest?.mainAttributes?.getValue("Bundle-Version")
                    ?: "unknown"
            }
        } catch (_: IOException) {
            "unknown"
        }
    }

    fun fetchLatestRemoteVersion(channel: String): String? {
        val metadataUrl = "https://maven.hytale.com/${channel.lowercase()}/com/hypixel/hytale/Server/maven-metadata.xml"
        return try {
            println("Checking for updates at $metadataUrl...")
            val connection = URI(metadataUrl).toURL().openConnection().apply {
                connectTimeout = 5000
                readTimeout = 5000
            }
            val xml = connection.getInputStream().bufferedReader().use { it.readText() }

            val lastVersion = extractLastVersionInVersionsBlock(xml)
            if (!lastVersion.isNullOrBlank()) return lastVersion

            val release = extractXmlTagValue(xml, "release")
            if (!release.isNullOrBlank()) return release

            extractXmlTagValue(xml, "latest")
        } catch (e: IOException) {
            println("Warning: Failed to fetch remote version metadata: ${e.message}")
            null
        }
    }

    internal fun extractXmlTagValue(xml: String, tag: String): String? {
        val regex = Regex("<$tag>\\s*([^<]+?)\\s*</$tag>", RegexOption.IGNORE_CASE)
        return regex.find(xml)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
    }

    internal fun extractLastVersionInVersionsBlock(xml: String): String? {
        val versionsBlockRegex = Regex(
            "<versions>\\s*(.*?)\\s*</versions>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        val versionsBlock = versionsBlockRegex.find(xml)?.groupValues?.get(1) ?: return null

        val versionRegex = Regex("<version>\\s*([^<]+?)\\s*</version>", RegexOption.IGNORE_CASE)
        return versionRegex.findAll(versionsBlock)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotEmpty() }
            .lastOrNull()
    }
}
