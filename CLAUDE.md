# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

GetMeHome is a straightforward **Paper-only** home plugin (fork of SimonOrJ/GetMeHome) supporting multi-home, per-permission home limits, warmup/cooldown delays, and locale-based messages. It requires **Paper 26.2+** (`api-version` 26.2); commands are registered natively via `LifecycleEvents.COMMANDS` + Brigadier (see `CommandRegistrar`) rather than YAML `CommandMap` declarations, because on 26.x a Bukkit CommandMap declaration produces a ghost command with no executor and native Brigadier registration pre-empts tab-completion.

## Build & run

All Gradle commands run via the wrapper (Gradle 9.6.1). Building requires a JDK 25 toolchain — the foojay resolver in `settings.gradle.kts` auto-downloads it if not installed.

- `./gradlew shadowJar` — build the plugin jar (the only artifact; the plain `jar` task is disabled and `build` depends on `shadowJar`)
- `./gradlew build` — full build (produces the shadowJar)
- `./gradlew runServer` — boot a local Paper 26.2 debug server with the plugin loaded (run-paper)
- `./gradlew tasks` — list tasks
- There are no tests (no test source set).

## Architecture

- **Entry point**: `GetMeHome.java` (JavaPlugin). `onEnable` registers all command executors, loads config/storage, registers `SaveListener`.
- **`command/`**: command executors — `HomeCommands` (home/sethome/setdefaulthome/delhome, one shared instance), `ListHomesCommand`, `MetaCommand` (`/getmehome reload`/`clearcache`).
- **`storage/`**: `HomeStorageAPI` interface and `StorageYAML` implementation (homes.yml, keyed by player UUID); `SaveListener` persists homes on player quit / world save.
- **`config/`**: `YamlPermValue`/`PermValue` parse the shared `{perm, value, operation, worlds}` list format used by both `limit.yml` (home limits) and `delay.yml` (warmup/cooldown); `ConfigUpgrader` migrates old configs on version bump.
- **Messaging**: `MessageTool` + `I18n` pick messages from `src/main/resources/i18n/*.properties` based on `player.locale()`; colors come from `GetMeHome.getFocusColor()/getContentColor()` (legacy `ChatColor`).
- **`DelayTimer`**: implements warmup/cooldown with `BukkitRunnable` timers; teleport executes back on the main thread.
- **Notes**: `TempUtils` holds Adventure helpers (`legacyString2Component`, `legacyChar2TextColor`) as a migration bridge, but the codebase still uses legacy `ChatColor` strings — deprecation warnings from `-Xlint:deprecation` are expected, not errors. bStats is bundled (relocated to `com.simonorj.mc.getmehome.shade.org.bstats`) but `setupMetrics()` is commented out, so metrics are currently disabled.

## Versioning & publishing (spans build.gradle.kts + CI)

- **Single source of truth for the version** = the `version:` field in `src/main/resources/plugin.yml`. `build.gradle.kts` reads it via SnakeYAML and derives everything from it.
- `build.gradle.kts` derives the published version from GitHub env vars:
  - `v`-prefixed SemVer git tag (no `-`, e.g. `v3.0.0`) → release: the published version is the tag minus the `v` prefix (`3.0.0`), and the CI guard enforces that tag(without `v`) == plugin.yml version
  - main-branch push → `{version}-dev.{run}` → Hangar `beta` channel
  - local build → `{version}-dev`
- The `io.papermc.hangar-publish-plugin` publishes the shadowJar and syncs `README.md` to the Hangar project page. Key tasks: `publishPluginPublicationToHangar`, `syncPluginPublicationMainResourcePagePageToHangar`.
- CI workflows (`.github/workflows/`):
  - `build.yml` — PRs to main + main pushes: wrapper validation, shadowJar build, artifact upload, build-summary PR comment.
  - `publish.yml` — main pushes (beta snapshot) and `v`-prefixed SemVer tags (release): shadowJar fast-fail → Hangar publish with retry + "version already exists" idempotency → README page sync → GitHub Release (tags only) → auto-increment `plugin.yml` patch and push back to main via `GITHUB_TOKEN`.
- Operational constraints:
  - Hangar channels `beta` / `release` are case-sensitive and must be pre-created on the Hangar project page (the plugin does not create them).
  - The version-bump commit uses the default `GITHUB_TOKEN` because main has no branch protection — no PAT/BOT_PAT needed.
  - `HANGAR_API_TOKEN` secret needs Hangar `create_version` scope (plus page-edit scope for README sync).
- Key `gradle.properties`: `plugin_jdk_min_version=25`, `plugin_bytecode_target=25`, `paper_api_version=26.1.2.build.74-stable`, `plugin_support_paper_versions=26.2`, `plugin_debug_server_version=26.2`.

## Release process

To cut a release, push a `v`-prefixed SemVer tag matching the current `plugin.yml` version, e.g. `v3.0.0` when `plugin.yml` says `3.0.0` (GitHub convention). CI publishes `3.0.0` (the tag minus `v`) to the `release` channel, creates a GitHub Release tagged `v3.0.0`, then bumps `plugin.yml` to the next patch on main. The tag (minus the `v`) must always equal `plugin.yml` (the workflow's version guard fails otherwise).
