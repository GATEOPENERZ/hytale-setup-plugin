package com.gateopenerz.hytaleserver

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.process.ExecOperations
import java.io.File
import java.io.FileInputStream
import java.net.URI
import java.security.MessageDigest
import java.util.jar.JarFile
import javax.inject.Inject

abstract class HytaleServerPlugin @Inject constructor(
    private val toolchains: JavaToolchainService,
    private val execOperations: ExecOperations
) : Plugin<Project> {

    private val gson = Gson()

    override fun apply(project: Project) {
        val ext = project.extensions.create("hytaleServer", HytaleServerExtension::class.java)

        ext.serverDir.convention("run")
        ext.channel.convention("release")
        ext.version.convention("latest")
        ext.jvmArgs.convention(emptyList())

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
                if (project.hasProperty("hytaleUpdate") || project.hasProperty("hytaleForceUpdate")) {
                    return@onlyIf true
                }

                val serverDir = serverDirProvider.get()

                if (!isInstalled(serverDir)) return@onlyIf true

                val desiredChannel = ext.channel.get()
                val desiredVersion = ext.version.get()

                val state = readStateOrNull(serverDir) ?: return@onlyIf true

                if (!state.channel.equals(desiredChannel, ignoreCase = true)) return@onlyIf true

                val detected = detectVersion(serverDir)
                val installedDetected = state.detectedVersion ?: state.version
                if (!installedDetected.equals(detected, ignoreCase = true)) return@onlyIf true

                if (desiredVersion.equals("latest", ignoreCase = true)) {
                    val remoteVersion = fetchLatestRemoteVersion(desiredChannel)
                    val installedResolved = state.resolvedVersion ?: state.version
                    if (remoteVersion != null && !installedResolved.equals(remoteVersion, ignoreCase = true)) {
                        println("New Hytale version detected: $remoteVersion (current: $installedResolved)")
                        return@onlyIf true
                    }
                } else {
                    val installedResolved = state.resolvedVersion ?: state.version
                    if (!installedResolved.equals(desiredVersion, ignoreCase = true)) return@onlyIf true
                }

                val assetsZip = File(serverDir, "Assets.zip")
                val serverJar = File(serverDir, "Server/HytaleServer.jar")

                if (!assetsZip.exists() || !serverJar.exists()) return@onlyIf true

                val jarOk = fileMatchesState(
                    file = serverJar,
                    expectedSize = state.serverJarSize,
                    expectedLastModified = state.serverJarLastModified,
                    expectedSha256 = state.serverJarSha256
                )

                if (!jarOk) return@onlyIf true

                val assetsOk = fileMatchesState(
                    file = assetsZip,
                    expectedSize = state.assetsZipSize,
                    expectedLastModified = state.assetsZipLastModified,
                    expectedSha256 = state.assetsZipSha256
                )

                if (!assetsOk) return@onlyIf true

                false
            }

            doLast {
                val serverDir = serverDirProvider.get()

                if (!serverDir.exists()) serverDir.mkdirs()

                val tempDir = project.layout.buildDirectory.dir("hytale/temp_downloader").get().asFile
                recreateDir(tempDir)

                try {
                    val downloaderZip = File(tempDir, "hytale-downloader.zip")
                    val downloaderUrl = "https://downloader.hytale.com/hytale-downloader.zip"

                    println("Downloading Hytale Downloader...")
                    downloadFile(downloaderUrl, downloaderZip)

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
                    }

                    println("Extracting server artifacts to ${serverDir.path}...")
                    project.copy {
                        from(project.zipTree(outputZip))
                        into(serverDir)
                    }

                    ensureScriptsPatched(serverDir)
                    writeJvmArgsFile(serverDir, ext.jvmArgs.getOrElse(emptyList()))
                    writeState(serverDir, ext.channel.get(), ext.version.get())
                } finally {
                    println("Cleaning up temporary files...")
                    deleteRecursivelyWithRetry(project.layout.buildDirectory.dir("hytale/temp_downloader").get().asFile)
                }
            }
        }

        project.tasks.register("runHytaleServer") {
            group = "hytale"
            description = "Runs Hytale Server via start.bat/start.sh (interactive, shows output, restart-on-update supported)"
            dependsOn(setupTask)

            doLast {
                val serverDir = serverDirProvider.get()
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

                val javaHome = resolveJavaHomeOrNull()

                while (true) {
                    val exitCode =
                        if (os.contains("win")) {
                            runWindowsLauncherAttached(serverDir, startBat, extraArgs, javaHome)
                        } else {
                            runUnixLauncherAttached(serverDir, startSh, extraArgs, javaHome)
                        }

                    if (exitCode == 8) {
                        continue
                    }

                    if (exitCode != 0) {
                        throw RuntimeException(
                            "Hytale server exited with code $exitCode. " +
                                "If you didn't see any server output, try running Gradle with --no-daemon."
                        )
                    }

                    return@doLast
                }
            }
        }

        project.tasks.register("runHytaleServerInteractive") {
            group = "hytale"
            description = "Launches runHytaleServer in a real terminal window (most reliable for stdin)."
            dependsOn(setupTask)

            doLast {
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
                    return@doLast
                }

                val commandLine = (listOf(gradlew.absolutePath) + gradleArgs).joinToString(" ")

                val candidates = listOf(
                    listOf("bash", "-lc", "x-terminal-emulator -e $commandLine"),
                    listOf("bash", "-lc", "gnome-terminal -- bash -lc '$commandLine; exec bash'"),
                    listOf("bash", "-lc", "konsole -e bash -lc '$commandLine; exec bash'"),
                    listOf("bash", "-lc", "xterm -e $commandLine")
                )

                var launched = false
                for (c in candidates) {
                    try {
                        execOperations.exec {
                            workingDir = rootDir
                            executable = c.first()
                            args = c.drop(1)
                            isIgnoreExitValue = true
                        }
                        launched = true
                        break
                    } catch (_: Exception) {
                    }
                }

                if (!launched) {
                    execOperations.exec {
                        workingDir = rootDir
                        executable = gradlew.absolutePath
                        args = gradleArgs
                        standardInput = System.`in`
                        standardOutput = System.out
                        errorOutput = System.err
                        isIgnoreExitValue = true
                    }
                }
            }
        }
    }

    private data class SetupState(
        val channel: String,
        val version: String,
        val resolvedVersion: String?,
        val detectedVersion: String?,
        val serverJarSize: Long?,
        val serverJarLastModified: Long?,
        val serverJarSha256: String?,
        val assetsZipSize: Long?,
        val assetsZipLastModified: Long?,
        val assetsZipSha256: String?
    )

    private fun readStateOrNull(serverDir: File): SetupState? {
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
        } catch (_: Exception) {
            null
        }
    }

    private fun fileMatchesState(
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

    private fun sha256(file: File): String {
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

    private fun isInstalled(serverDir: File): Boolean {
        val assetsZip = File(serverDir, "Assets.zip")
        val serverJar = File(serverDir, "Server/HytaleServer.jar")
        val hasStart = File(serverDir, "start.bat").exists() || File(serverDir, "start.sh").exists()
        return assetsZip.exists() && serverJar.exists() && hasStart
    }

    private fun detectVersion(serverDir: File): String {
        val jarFile = File(serverDir, "Server/HytaleServer.jar")
        if (!jarFile.exists()) return "unknown"

        return try {
            val jar = JarFile(jarFile)
            val manifest = jar.manifest
            val ver = manifest.mainAttributes.getValue("Implementation-Version")
                ?: manifest.mainAttributes.getValue("Bundle-Version")
                ?: "unknown"
            jar.close()
            ver
        } catch (_: Exception) {
            "unknown"
        }
    }

    private fun writeState(serverDir: File, channel: String, desiredVersion: String) {
        val assetsZip = File(serverDir, "Assets.zip")
        val serverJar = File(serverDir, "Server/HytaleServer.jar")

        val detectedVersion = detectVersion(serverDir)
        val resolvedVersion =
            if (desiredVersion.equals("latest", ignoreCase = true)) {
                fetchLatestRemoteVersion(channel) ?: detectedVersion
            } else {
                detectedVersion
            }

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

    private fun writeJvmArgsFile(serverDir: File, jvmArgs: List<String>) {
        File(serverDir, ".hytale-jvm.args").writeText(jvmArgs.joinToString(" "))
    }

    private fun ensureScriptsPatched(serverDir: File) {
        val startBat = File(serverDir, "start.bat")
        if (startBat.exists()) {
            val text = startBat.readText()
            var updated = text

            if (!updated.contains(".hytale-jvm.args", ignoreCase = true)) {
                updated = updated.replace(
                    "rem Default server arguments",
                    "rem Extra JVM args injected by Gradle\r\nset USER_JVM_ARGS=\r\nif exist \"%SCRIPT_DIR%\\.hytale-jvm.args\" (\r\n    for /f \"usebackq delims=\" %%A in (\"%SCRIPT_DIR%\\.hytale-jvm.args\") do set USER_JVM_ARGS=%%A\r\n)\r\n\r\nrem Default server arguments"
                )
            }

            if (!updated.contains("%USER_JVM_ARGS%", ignoreCase = true)) {
                updated = updated.replace(
                    "java %JVM_ARGS% -jar HytaleServer.jar",
                    "java %JVM_ARGS% %USER_JVM_ARGS% -jar HytaleServer.jar"
                )
            }

            if (updated != text) startBat.writeText(updated)
        }

        val startSh = File(serverDir, "start.sh")
        if (startSh.exists()) {
            val text = startSh.readText()
            var updated = text

            val anchor = "cd \"\$SCRIPT_DIR\""
            if (!updated.contains(".hytale-jvm.args")) {
                updated = updated.replace(
                    anchor,
                    "cd \"\\\$SCRIPT_DIR\"\n\nUSER_JVM_ARGS=\"\"\nif [ -f \"\\\$SCRIPT_DIR/.hytale-jvm.args\" ]; then\n  read -r USER_JVM_ARGS < \"\\\$SCRIPT_DIR/.hytale-jvm.args\"\nfi"
                )
            }

            val wanted = "java \\\$JVM_ARGS \\\$USER_JVM_ARGS -jar HytaleServer.jar"
            if (!updated.contains(wanted)) {
                updated = updated.replace(
                    "java \$JVM_ARGS -jar HytaleServer.jar",
                    "java \$JVM_ARGS \$USER_JVM_ARGS -jar HytaleServer.jar"
                )
            }

            if (updated != text) startSh.writeText(updated)
            startSh.setExecutable(true)
        }
    }

    private fun runWindowsLauncherAttached(
        serverDir: File,
        startBat: File,
        extraArgs: List<String>,
        javaHome: File?
    ): Int {
        val batPath = startBat.absolutePath

        val result = execOperations.exec {
            workingDir = serverDir
            executable = "cmd"
            args = listOf("/c", "call", batPath) + extraArgs

            standardInput = System.`in`
            standardOutput = System.out
            errorOutput = System.err

            isIgnoreExitValue = true

            applyJavaEnvIfPresent(this, javaHome)
        }

        return result.exitValue
    }

    private fun runUnixLauncherAttached(
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

    private fun resolveJavaHomeOrNull(): File? {
        val fromToolchain = try {
            toolchains.launcherFor {
                languageVersion.set(JavaLanguageVersion.of(25))
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

    private fun recreateDir(dir: File) {
        if (dir.exists()) deleteRecursivelyWithRetry(dir)
        dir.mkdirs()
    }

    private fun deleteRecursivelyWithRetry(dir: File, attempts: Int = 10, sleepMs: Long = 250) {
        repeat(attempts) {
            if (!dir.exists()) return
            try {
                dir.deleteRecursively()
            } catch (_: Exception) {
            }
            if (!dir.exists()) return
            try {
                Thread.sleep(sleepMs)
            } catch (_: InterruptedException) {
            }
        }
    }

    private fun downloadFile(url: String, dest: File) {
        val connection = URI(url).toURL().openConnection()
        connection.getInputStream().use { input ->
            dest.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun fetchLatestRemoteVersion(channel: String): String? {
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
        } catch (e: Exception) {
            println("Warning: Failed to fetch remote version metadata: ${e.message}")
            null
        }
    }

    private fun extractXmlTagValue(xml: String, tag: String): String? {
        val regex = Regex("<$tag>\\s*([^<]+?)\\s*</$tag>", RegexOption.IGNORE_CASE)
        return regex.find(xml)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun extractLastVersionInVersionsBlock(xml: String): String? {
        val versionsBlockRegex = Regex("<versions>\\s*(.*?)\\s*</versions>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val versionsBlock = versionsBlockRegex.find(xml)?.groupValues?.get(1) ?: return null

        val versionRegex = Regex("<version>\\s*([^<]+?)\\s*</version>", RegexOption.IGNORE_CASE)
        return versionRegex.findAll(versionsBlock)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotEmpty() }
            .lastOrNull()
    }
}
