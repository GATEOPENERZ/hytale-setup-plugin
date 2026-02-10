package com.gateopenerz.hytaleserver

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.process.ExecOperations
import java.io.File
import javax.inject.Inject

abstract class HytaleServerPlugin @Inject constructor(
    private val toolchains: JavaToolchainService,
    private val execOperations: ExecOperations
) : Plugin<Project> {

    private val stateManager = StateManager()
    private val versionResolver = VersionResolver()
    private val processLauncher by lazy { ProcessLauncher(execOperations, toolchains) }

    override fun apply(project: Project) {
        val ext = project.extensions.create("hytaleServer", HytaleServerExtension::class.java)

        ext.serverDir.convention("run")
        ext.channel.convention("release")
        ext.version.convention("latest")
        ext.jvmArgs.convention(emptyList())
        ext.terminal.convention(emptyList())
        ext.javaVersion.convention(25)

        val serverDirProvider = ext.serverDir.map { project.layout.projectDirectory.dir(it).asFile }
        val stateFileProvider = serverDirProvider.map { File(it, ".hytale-setup-state.json") }

        val setupTask = project.tasks.register("setupHytaleServer") {
            group = "hytale"
            description = "Downloads Hytale Server and Assets using the official downloader"

            inputs.property("serverDir", ext.serverDir)
            inputs.property("channel", ext.channel)
            inputs.property("version", ext.version)
            outputs.file(stateFileProvider)

            onlyIf {
                shouldRunSetup(project, serverDirProvider.get(), ext)
            }

            doLast {
                executeSetup(project, serverDirProvider.get(), ext)
            }
        }

        project.tasks.register("runHytaleServer") {
            group = "hytale"
            description = "Runs Hytale Server via start.bat/start.sh (interactive, shows output, restart-on-update supported)"
            dependsOn(setupTask)

            doLast {
                executeRunServer(project, serverDirProvider.get(), ext)
            }
        }

        project.tasks.register("runHytaleServerInteractive") {
            group = "hytale"
            description = "Launches runHytaleServer in a real terminal window (most reliable for stdin)."
            dependsOn(setupTask)

            doLast {
                executeRunInteractive(project, ext)
            }
        }
    }

    private fun shouldRunSetup(project: Project, serverDir: File, ext: HytaleServerExtension): Boolean {
        if (project.hasProperty("hytaleUpdate") || project.hasProperty("hytaleForceUpdate")) {
            return true
        }

        if (!isInstalled(serverDir)) return true

        val desiredChannel = ext.channel.get()
        val desiredVersion = ext.version.get()

        val state = stateManager.readStateOrNull(serverDir) ?: return true

        if (!state.channel.equals(desiredChannel, ignoreCase = true)) return true

        val detected = versionResolver.detectVersion(serverDir)
        val installedDetected = state.detectedVersion ?: state.version
        if (!installedDetected.equals(detected, ignoreCase = true)) return true

        if (desiredVersion.equals("latest", ignoreCase = true)) {
            val remoteVersion = try {
                versionResolver.fetchLatestRemoteVersion(desiredChannel)
            } catch (e: Exception) {
                println("Warning: Could not check for remote updates: ${e.message}")
                null
            }
            val installedResolved = state.resolvedVersion ?: state.version
            if (remoteVersion != null && !installedResolved.equals(remoteVersion, ignoreCase = true)) {
                println("New Hytale version detected: $remoteVersion (current: $installedResolved)")
                return true
            }
        } else {
            val installedResolved = state.resolvedVersion ?: state.version
            if (!installedResolved.equals(desiredVersion, ignoreCase = true)) return true
        }

        val assetsZip = File(serverDir, "Assets.zip")
        val serverJar = File(serverDir, "Server/HytaleServer.jar")

        if (!assetsZip.exists() || !serverJar.exists()) return true

        val jarOk = stateManager.fileMatchesState(
            file = serverJar,
            expectedSize = state.serverJarSize,
            expectedLastModified = state.serverJarLastModified,
            expectedSha256 = state.serverJarSha256
        )
        if (!jarOk) return true

        val assetsOk = stateManager.fileMatchesState(
            file = assetsZip,
            expectedSize = state.assetsZipSize,
            expectedLastModified = state.assetsZipLastModified,
            expectedSha256 = state.assetsZipSha256
        )
        if (!assetsOk) return true

        return false
    }

    private fun executeSetup(project: Project, serverDir: File, ext: HytaleServerExtension) {
        if (!serverDir.exists()) serverDir.mkdirs()

        val tempDirPath = java.nio.file.Files.createTempDirectory("hytale-setup")
        val tempDir = tempDirPath.toFile()

        try {
            val downloaderZip = File(tempDir, "hytale-downloader.zip")
            val downloaderUrl = "https://downloader.hytale.com/hytale-downloader.zip"

            println("Downloading Hytale Downloader...")
            HytaleDownloader.downloadFile(downloaderUrl, downloaderZip)

            println("Extracting Hytale Downloader...")
            project.copy {
                from(project.zipTree(downloaderZip))
                into(tempDir)
            }

            val os = System.getProperty("os.name").lowercase()
            val executableName =
                if (os.contains("win")) "hytale-downloader-windows-amd64.exe"
                else "hytale-downloader-linux-amd64"

            val downloaderExe = tempDir.walk().find { it.name.equals(executableName, ignoreCase = true) }
            if (downloaderExe == null || !downloaderExe.exists()) {
                val allFiles = tempDir.walk().map { it.relativeTo(tempDir).path }.joinToString(", ")
                throw RuntimeException("Could not find $executableName after extraction in $tempDir. Found files: $allFiles")
            }

            if (!os.contains("win")) {
                execOperations.exec {
                    commandLine("chmod", "+x", downloaderExe.absolutePath)
                }
            }
            downloaderExe.setExecutable(true)

            val channel = ext.channel.get()
            val outputZip = File(tempDir, "hytale-server-assets.zip")

            println("Running Hytale Downloader ($channel)...")
            execOperations.exec {
                workingDir = tempDir
                executable = downloaderExe.absolutePath
                args = mutableListOf("-download-path", outputZip.name).apply {
                    if (channel.equals("pre-release", ignoreCase = true)) {
                        add("-patchline")
                        add("pre-release")
                    }
                }
                standardOutput = System.out
                errorOutput = System.err
                environment("GODEBUG", "netdns=cgo")
            }

            println("Extracting server artifacts to ${serverDir.path}...")
            project.copy {
                from(project.zipTree(outputZip))
                into(serverDir)
            }

            ScriptPatcher.ensureScriptsPatched(serverDir)
            ScriptPatcher.writeJvmArgsFile(serverDir, ext.jvmArgs.getOrElse(emptyList()))

            val detectedVersion = versionResolver.detectVersion(serverDir)
            val desiredVersion = ext.version.get()
            val resolvedVersion =
                if (desiredVersion.equals("latest", ignoreCase = true)) {
                    versionResolver.fetchLatestRemoteVersion(channel) ?: detectedVersion
                } else {
                    detectedVersion
                }
            stateManager.writeState(serverDir, channel, resolvedVersion, detectedVersion)
        } finally {
            println("Cleaning up temporary files...")
            HytaleDownloader.deleteRecursivelyWithRetry(tempDir)
        }
    }

    private fun executeRunServer(project: Project, serverDir: File, ext: HytaleServerExtension) {
        if (!isInstalled(serverDir)) {
            throw RuntimeException("Server is not installed in ${serverDir.path}. Run setupHytaleServer first.")
        }

        val os = System.getProperty("os.name").lowercase()

        val startBat = File(serverDir, "start.bat")
        val startSh = File(serverDir, "start.sh")
        val assetsZip = File(serverDir, "Assets.zip")
        val serverJar = File(serverDir, "Server/HytaleServer.jar")

        if (!assetsZip.exists()) throw RuntimeException("Missing Assets.zip at ${assetsZip.path}")
        if (!serverJar.exists()) throw RuntimeException("Missing HytaleServer.jar at ${serverJar.path}")
        if (os.contains("win") && !startBat.exists()) throw RuntimeException("Missing start.bat at ${startBat.path}")
        if (!os.contains("win") && !startSh.exists()) throw RuntimeException("Missing start.sh at ${startSh.path}")

        val extraArgs = (project.findProperty("hytaleArgs") as String?)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.split(Regex("\\s+"))
            ?: emptyList()

        val javaHome = processLauncher.resolveJavaHomeOrNull(ext.javaVersion.get())

        if (!os.contains("win")) {
            startSh.setExecutable(true)
        }

        while (true) {
            val exitCode =
                if (os.contains("win")) {
                    processLauncher.runWindowsLauncherAttached(serverDir, startBat, extraArgs, javaHome)
                } else {
                    processLauncher.runUnixLauncherAttached(serverDir, startSh, extraArgs, javaHome)
                }

            if (exitCode == 8) continue

            if (exitCode != 0) {
                throw RuntimeException(
                    "Hytale server exited with code $exitCode. " +
                        "If you didn't see any server output, try running Gradle with --no-daemon."
                )
            }

            return
        }
    }

    private fun executeRunInteractive(project: Project, ext: HytaleServerExtension) {
        val os = (System.getProperty("os.name") ?: "").lowercase()
        val rootDir = project.rootProject.projectDir

        val gradlew = if (os.contains("win")) File(rootDir, "gradlew.bat") else File(rootDir, "gradlew")
        if (!gradlew.exists()) {
            throw RuntimeException("Could not find Gradle wrapper at: ${gradlew.absolutePath}")
        }

        fun q(arg: String): String {
            val trimmed = arg.trim()
            return if (trimmed.any { it.isWhitespace() }) "\"$trimmed\"" else trimmed
        }

        val hytaleArgsProp = (project.findProperty("hytaleArgs") as String?)?.trim()?.takeIf { it.isNotEmpty() }
        val forwardedProps = if (hytaleArgsProp != null) listOf("-PhytaleArgs=$hytaleArgsProp") else emptyList()

        val gradleArgs = listOf("--no-daemon", "--console=plain") + forwardedProps + listOf("runHytaleServer")

        if (os.contains("win")) {
            val fullCmd = buildString {
                append(q(gradlew.absolutePath))
                append(" ")
                append(gradleArgs.joinToString(" ") { q(it) })
            }

            execOperations.exec {
                workingDir = rootDir
                executable = "cmd"
                args = listOf("/c", "start", "Hytale Server", "cmd", "/k", fullCmd)
                isIgnoreExitValue = true
            }
            return
        }

        val commandLine = (listOf("bash", gradlew.absolutePath) + gradleArgs).joinToString(" ")

        val userTerminal = ext.terminal.getOrElse(emptyList())
        val candidates = if (userTerminal.isNotEmpty()) {
            listOf("custom" to userTerminal)
        } else {
            Terminal.values().map { it.name.lowercase() to it.command }
        }

        var launched = false
        for ((name, rawCmdArgs) in candidates) {
            val cmdArgs = rawCmdArgs.map { it.replace("\$commandLine", commandLine) }
            try {
                println("Trying to launch terminal: $name")
                execOperations.exec {
                    workingDir = rootDir
                    executable = cmdArgs.first()
                    args = cmdArgs.drop(1)
                    isIgnoreExitValue = true
                }
                println("Successfully launched terminal: $name")
                launched = true
                break
            } catch (_: Exception) {
                println("Failed to launch terminal: $name")
            }
        }

        if (!launched) {
            execOperations.exec {
                workingDir = rootDir
                executable = "bash"
                args = listOf(gradlew.absolutePath) + gradleArgs
                standardInput = System.`in`
                standardOutput = System.out
                errorOutput = System.err
                isIgnoreExitValue = true
            }
        }
    }

    private fun isInstalled(serverDir: File): Boolean {
        val assetsZip = File(serverDir, "Assets.zip")
        val serverJar = File(serverDir, "Server/HytaleServer.jar")
        val hasStart = File(serverDir, "start.bat").exists() || File(serverDir, "start.sh").exists()
        return assetsZip.exists() && serverJar.exists() && hasStart
    }
}
