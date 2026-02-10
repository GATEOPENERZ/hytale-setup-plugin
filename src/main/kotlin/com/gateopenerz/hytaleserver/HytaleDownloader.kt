package com.gateopenerz.hytaleserver

import java.io.File
import java.io.IOException
import java.net.URI

internal object HytaleDownloader {

    fun downloadFile(url: String, dest: File) {
        val connection = URI(url).toURL().openConnection().apply {
            connectTimeout = 15_000
            readTimeout = 30_000
        }
        connection.getInputStream().use { input ->
            dest.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }

    fun deleteRecursivelyWithRetry(dir: File, attempts: Int = 10, sleepMs: Long = 250) {
        repeat(attempts) {
            if (!dir.exists()) return
            try {
                dir.deleteRecursively()
            } catch (_: IOException) {}
            if (!dir.exists()) return
            try {
                Thread.sleep(sleepMs)
            } catch (_: InterruptedException) {}
        }
    }
}
