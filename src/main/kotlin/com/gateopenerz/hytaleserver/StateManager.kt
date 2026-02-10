package com.gateopenerz.hytaleserver

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.security.MessageDigest

internal class StateManager {

    private val gson = Gson()

    fun readStateOrNull(serverDir: File): SetupState? {
        val file = File(serverDir, ".hytale-setup-state.json")
        if (!file.exists()) return null

        return try {
            val mapType = object : TypeToken<Map<String, Any?>>() {}.type
            val m: Map<String, Any?> = gson.fromJson(file.readText(), mapType)

            fun s(key: String): String? = m[key]?.toString()
            fun l(key: String): Long? {
                val v = m[key] ?: return null
                return when (v) {
                    is Number -> v.toLong()
                    is String -> v.toLongOrNull()
                    else -> v.toString().toLongOrNull()
                }
            }

            val channel = s("channel") ?: return null
            val legacyVersion = s("version") ?: return null
            val resolvedVersion = s("resolvedVersion") ?: legacyVersion
            val detectedVersion = s("detectedVersion") ?: legacyVersion

            SetupState(
                channel = channel,
                version = legacyVersion,
                resolvedVersion = resolvedVersion,
                detectedVersion = detectedVersion,
                serverJarSize = l("serverJarSize"),
                serverJarLastModified = l("serverJarLastModified"),
                serverJarSha256 = s("serverJarSha256"),
                assetsZipSize = l("assetsZipSize"),
                assetsZipLastModified = l("assetsZipLastModified"),
                assetsZipSha256 = s("assetsZipSha256")
            )
        } catch (_: JsonSyntaxException) {
            null
        } catch (_: IOException) {
            null
        }
    }

    fun writeState(
        serverDir: File,
        channel: String,
        resolvedVersion: String,
        detectedVersion: String
    ) {
        val assetsZip = File(serverDir, "Assets.zip")
        val serverJar = File(serverDir, "Server/HytaleServer.jar")

        val state = linkedMapOf<String, Any?>(
            "channel" to channel,
            "version" to resolvedVersion,
            "resolvedVersion" to resolvedVersion,
            "detectedVersion" to detectedVersion
        )

        if (serverJar.exists()) {
            state["serverJarSize"] = serverJar.length()
            state["serverJarLastModified"] = serverJar.lastModified()
            state["serverJarSha256"] = sha256(serverJar)
        }

        if (assetsZip.exists()) {
            state["assetsZipSize"] = assetsZip.length()
            state["assetsZipLastModified"] = assetsZip.lastModified()
            state["assetsZipSha256"] = sha256(assetsZip)
        }

        File(serverDir, ".hytale-setup-state.json").writeText(gson.toJson(state))
    }

    fun fileMatchesState(
        file: File,
        expectedSize: Long?,
        expectedLastModified: Long?,
        expectedSha256: String?
    ): Boolean {
        if (expectedSize == null || expectedLastModified == null || expectedSha256.isNullOrBlank()) return false

        val sizeSame = file.length() == expectedSize
        val mtimeSame = file.lastModified() == expectedLastModified

        if (sizeSame && mtimeSame) return true

        val actual = sha256(file)
        return actual.equals(expectedSha256, ignoreCase = true)
    }

    fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buf = ByteArray(1024 * 64)
            while (true) {
                val r = fis.read(buf)
                if (r <= 0) break
                md.update(buf, 0, r)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
