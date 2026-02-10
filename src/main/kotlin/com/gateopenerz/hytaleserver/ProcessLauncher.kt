package com.gateopenerz.hytaleserver

import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.process.ExecOperations
import java.io.File

internal class ProcessLauncher(
    private val execOperations: ExecOperations,
    private val toolchains: JavaToolchainService
) {

    fun resolveJavaHomeOrNull(javaVersion: Int): File? {
        val fromToolchain = try {
            toolchains.launcherFor {
                languageVersion.set(JavaLanguageVersion.of(javaVersion))
            }.get().metadata.installationPath.asFile
        } catch (_: Exception) {
            null
        }

        if (fromToolchain != null && File(fromToolchain, "bin").exists()) return fromToolchain

        val current = try {
            File(System.getProperty("java.home"))
        } catch (_: Exception) {
            null
        }

        if (current == null) return null

        val currentBin = File(current, "bin")
        if (currentBin.exists()) return current

        val parent = current.parentFile
        if (parent != null && File(parent, "bin").exists()) return parent

        return null
    }

    fun runWindowsLauncherAttached(
        serverDir: File,
        startBat: File,
        extraArgs: List<String>,
        javaHome: File?
    ): Int {
        val result = execOperations.exec {
            workingDir = serverDir
            executable = "cmd"
            args = listOf("/c", "call", startBat.absolutePath) + extraArgs

            standardInput = System.`in`
            standardOutput = System.out
            errorOutput = System.err
            isIgnoreExitValue = true

            applyJavaEnvIfPresent(this, javaHome)
        }
        return result.exitValue
    }

    fun runUnixLauncherAttached(
        serverDir: File,
        startSh: File,
        extraArgs: List<String>,
        javaHome: File?
    ): Int {
        val result = execOperations.exec {
            workingDir = serverDir
            executable = "bash"
            args = listOf(startSh.absolutePath) + extraArgs

            standardInput = System.`in`
            standardOutput = System.out
            errorOutput = System.err
            isIgnoreExitValue = true

            applyJavaEnvIfPresent(this, javaHome)
        }
        return result.exitValue
    }

    private fun applyJavaEnvIfPresent(spec: org.gradle.process.ExecSpec, javaHome: File?) {
        if (javaHome == null) return

        val bin = File(javaHome, "bin")
        if (!bin.exists()) return

        spec.environment("JAVA_HOME", javaHome.absolutePath)

        val currentPath = System.getenv("Path") ?: System.getenv("PATH") ?: ""
        val sep = if ((System.getProperty("os.name") ?: "").lowercase().contains("win")) ";" else ":"
        val newPath = bin.absolutePath + sep + currentPath

        spec.environment("Path", newPath)
        spec.environment("PATH", newPath)
    }
}
