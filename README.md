# Hytale Setup Plugin

A Gradle plugin to automatically set up and run a Hytale server for development.

## Features
- Downloads Hytale Server artifacts and Assets using the official Hytale Downloader.
- Supports `release` and `pre-release` channels.
- Persists a local state file to avoid re-downloading when unchanged.
- Configurable JVM arguments (via `.hytale-jvm.args` injected into start scripts).
- Java toolchain support (sets JAVA_HOME when launching).

## Usage

In your `build.gradle.kts`:

```kotlin
plugins {
    id("io.github.gateopenerz.hytale-server") version "1.0.6"
}

hytaleServer {
    // Property-style configuration (explicit .set() is the most portable)
    channel.set("release")   // or "pre-release"
    version.set("latest")    // or a specific version like "2026.01.29-..."
    serverDir.set("run")

    jvmArgs.set(listOf("-Xmx4G", "-Xms2G"))

    // Java version for the server (default: 25)
    javaVersion.set(25)
}
```

> Note: In Kotlin DSL you can also use `channel = "release"` style assignment with lazy properties. ([Gradle Documentation][1])

## Terminal Configuration (Linux Only)

You can configure which terminal emulator to use when running `runHytaleServerInteractive`. This is especially useful for maintaining your preferred workflow. If no terminal is configured, the plugin will attempt to find a supported one from a list of common presets.

```kotlin
hytaleServer {
    // ... other config

    // Use a preset terminal (Recommended)
    useTerminal(ghostty)
    
    // Supported presets are:
    // ghostty, kitty, konsole, gnomeTerminal, xTerminalEmulator, xterm

    // You can also set the terminal property directly using a preset:
    // terminal.set(kitty)

    // Or use a completely custom command:
    // '$commandLine' is a placeholder that will be replaced with the Hytale server execution command.
    // terminal.set(listOf("bash", "-lc", "my-term -e bash -lc '\$commandLine'"))
}
```


## Tasks

* `setupHytaleServer`
  Downloads the Hytale Downloader, runs it, and extracts Server/Assets into `serverDir`.
  The downloader may prompt you to authenticate via a device code URL.

* `runHytaleServer`
  Runs the server via `start.bat` / `start.sh` from `serverDir` (attached to the current process).

* `runHytaleServerInteractive`
  Opens a real terminal window (Windows) and runs `runHytaleServer` with `--no-daemon --console=plain`
  for reliable interactive stdin.

## Runtime flags (project properties)

* Force re-download/update:

  * `-PhytaleUpdate` or `-PhytaleForceUpdate`

* Pass extra args to the start script:

  * `-PhytaleArgs="--someFlag value"`

Example:

```bash
./gradlew runHytaleServerInteractive -PhytaleUpdate -PhytaleArgs="--foo bar"
```

## Configuration

| Option        | Default   | Description                                                                  |
| ------------- | --------- | ---------------------------------------------------------------------------- |
| `channel`     | `release` | Downloader channel / patchline (`release` or `pre-release`).                 |
| `version`     | `latest`  | Desired server version. (`latest` = use local install unless forced update). |
| `serverDir`   | `run`     | Directory for server files.                                                  |
| `jvmArgs`     | `[]`      | JVM arguments list written to `.hytale-jvm.args`.                            |
| `terminal`    | `[]`      | Custom terminal command arguments for Linux. (If empty, tries presets).      |
| `javaVersion` | `25`      | Java version for the toolchain used to run the server.                       |
