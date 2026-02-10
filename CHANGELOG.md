# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.6] - 2026-02-10

### Added
- `javaVersion` extension property — configurable Java toolchain version (default: `25`), replacing the previous hardcoded value.
- Gradle version catalog (`gradle/libs.versions.toml`) for centralized dependency management.
- Unit tests for `StateManager`, `VersionResolver`, and `ScriptPatcher`.
- Warning messages when script patching cannot find expected anchors in `start.bat` / `start.sh`.
- `CHANGELOG.md`.

### Changed
- **Major refactor**: decomposed monolithic `HytaleServerPlugin.kt` (672 lines) into focused single-responsibility classes:
  - `StateManager` — state file read/write, file integrity checks, SHA-256.
  - `VersionResolver` — server version detection (jar manifest), remote version fetching, XML parsing.
  - `ScriptPatcher` — `start.bat` / `start.sh` patching and JVM args file management.
  - `ProcessLauncher` — server process execution and Java environment setup.
  - `HytaleDownloader` — file downloading with timeouts and cleanup utilities.
  - `SetupState` — promoted to top-level internal data class.
  - `Terminal` — extracted enum to its own file.
- `downloadFile()` now sets connect/read timeouts (15s/30s) to prevent indefinite hangs.
- `detectVersion()` now uses `JarFile.use {}` to prevent file handle leaks.
- `onlyIf` network check wrapped in try-catch so DNS/timeout errors log a warning instead of failing the build.
- Narrowed exception catching from blanket `Exception` to `IOException` / `JsonSyntaxException` where appropriate.
- Updated `foojay-resolver-convention` from `0.8.0` to `0.9.0`.

### Removed
- Unused `recreateDir()` method (dead code).

## [1.0.5] - 2026-02-10

### Added
- Terminal configuration support for `runHytaleServerInteractive` on Linux.
- Built-in terminal presets: Ghostty, Kitty, Konsole, GNOME Terminal, x-terminal-emulator, xterm.
- `useTerminal()` convenience method and `terminal.set(preset)` DSL support.

## [1.0.4] - 2026-02-09

### Added
- Initial public release on Gradle Plugin Portal.
- `setupHytaleServer` task — downloads and extracts Hytale Server via official downloader.
- `runHytaleServer` task — runs server attached to Gradle process.
- `runHytaleServerInteractive` task — launches server in a real terminal window.
- Configurable `channel`, `version`, `serverDir`, and `jvmArgs`.
- Smart up-to-date checking with SHA-256 integrity verification.
- Java toolchain auto-detection.
