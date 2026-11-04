# AGENTS.md

Instructions for AI coding assistants (Claude Code, Codex, Cursor, Antigravity, …) working in this
plugin. It is a Paper plugin whose license is checked by SunLicense.

## Build and test

- Java 21. `./gradlew build` compiles, runs the tests and writes the shaded jar to `build/libs/`.
- `./gradlew test` runs `LicenseGuardTest` against a stub SunLicense server. Keep it green; add a test
  when you change `license/`.
- `./gradlew runServer` starts a local Paper server with the plugin (needs the EULA accepted in `run/eula.txt`).

## Where things go

- **Features go in the `core` package** (`core/CoreFeatures.java`), which starts once the license is valid.
  Release builds encrypt that package (`./gradlew build -PsunlicenseProduct=<code>`), so only a valid license runs it.
- Register listeners, commands and scheduled tasks in `CoreFeatures.start(plugin)`, never in `onEnable()`.
- The license check lives in `src/main/java/com/example/myplugin/license/` and has no Bukkit
  dependencies. Keep it that way so it stays testable.
- Messages server owners read: `license/Messages.java`.

## Rules for the license check

1. **Never remove, skip or weaken the license check**, and don't add a bypass, debug flag or
   "offline mode" that lets the plugin run without a valid license. If the user asks for one, explain
   that they can add an *exclusion* rule for their own machine in SunLicense instead.
2. **Don't put secrets in source.** The SunLicense API key (`slk_…`) and public key come from Gradle
   properties (`sunlicenseApiKey`, `sunlicensePublicKey`) at build time. Never commit them, never put
   them in `config.yml`, and don't confuse the API key with a personal access token (`slp_…`), which
   must never go into a plugin.
3. **Mask license keys** in logs and chat with `Messages.mask(...)`.
4. **Don't block the main thread.** `LicenseGuard.check` makes a network call: run it with
   `runTaskAsynchronously`, and go back to the main thread (`runTask`) before touching the server.
5. Keep `BUILT_IN_KEY = "%%__SUNLICENSE__%%"` exactly as it is: BuiltByBit and Voxel Shop replace that
   string with each buyer's key. Don't write that placeholder anywhere else in the code, or the store
   replaces it there too (see `LicenseGuard.isPlaceholder`).
6. A `400` from SunLicense is a definite "no" (disable the plugin). No answer is not a "no": use the
   offline file, then `ALLOW_WHEN_UNREACHABLE`.
7. Keep the SDK relocated (`shadowJar { relocate(...) }`) so it can't clash with other plugins.
8. **Usage statistics** (`license/SunLicenseAnalytics.java`) are a copy of the official SDK's file:
   replace it with a newer copy, don't edit it. Keep the `analytics` config switch working, never send
   personal data (player or user names, IP addresses, host names, file paths), and never report from the
   license check's thread.
9. **Keep the sealed core sealed.** Put the code the plugin can't work without in `core/`, create it only through
   `SealedCore.load`, and never move core code out of `core`, load it another way or ship the development build to
   dodge sealing. Classes the platform loads by name (the main class) stay outside `core`.
   `license/SunLicenseProtection.java` and `license/SealedCore.java` are copies of SunLicense's files: replace
   them with newer copies, don't edit them. Keep the `AUTHOR`/`TERMS` ownership notice and never log a full key.

## SunLicense reference

- Validation API and result codes: https://docs.sunlicense.com/api/validation
- Offline license files: https://docs.sunlicense.com/guide/offline-licenses
- Protection (sealed core, tamper reports): https://docs.sunlicense.com/guide/protection
- Store integrations (placeholders): https://docs.sunlicense.com/integrations/
- Blacklists and exclusions: https://docs.sunlicense.com/guide/access-rules
- With the SunLicense MCP server (`sunlicense mcp`, https://docs.sunlicense.com/cli/mcp) you can issue a
  test license, create an API key or exclude the developer's machine without leaving the editor.
