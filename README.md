# Paper plugin with SunLicense

A working, copyable [Paper](https://papermc.io) plugin with [SunLicense](https://sunlicense.com) built in:
each server needs a valid license key, leaked keys can be revoked or blocked, and your customers' servers
keep running if SunLicense is ever unreachable.

- Gradle + Shadow, the official [Java SDK](https://github.com/sunlicense-com/sunlicense-java) relocated into your package
- License key from **config.yml**, or built in by **BuiltByBit / Voxel Shop** placeholders
- Clear console messages for every result (expired, revoked, too many servers, blacklisted, …), keys masked
- Re-checked every few hours, so a revoke reaches running servers
- **Offline fallback** with a signed license file (Ed25519, verified without network)
- Tests against a stub server, CI on GitHub Actions, and [AGENTS.md](AGENTS.md) for AI coding assistants

Works on Paper 1.21 and newer (built against 1.21.11 with Java 21). Spigot servers aren't supported
(the plugin uses Paper's Adventure API).

## 1. Set up SunLicense

In the [dashboard](https://app.sunlicense.com):

1. **Products → New product** (your plugin), then a **policy**, e.g. "Pro", *2 machines*: a machine is a server here.
2. **API keys → Create API key**. It can only validate licenses, so it's safe inside a jar.
3. Optional, for the offline fallback: open any license and copy **Public key**.

## 2. Make it yours

1. Use this template (or copy the folder), then rename `com.example.myplugin`, `MyPlugin` and `my-plugin`
   (`settings.gradle.kts`, `plugin.yml`, the Java package).
2. Put the API key in your **user** Gradle properties (`~/.gradle/gradle.properties`), never in git:

   ```properties
   sunlicenseApiKey=slk_...
   sunlicensePublicKey=MCowBQYDK2VwAyEA...
   ```

   For a local SunLicense stack add `sunlicenseUrl=http://localhost`.
3. Build: `./gradlew build` → `build/libs/my-plugin-1.0.0.jar` (Java 21 needed; Gradle downloads itself).
4. Try it: `./gradlew runServer` starts a local Paper server with the plugin (the first time, accept
   Minecraft's EULA in `run/eula.txt` and start it again; `stop` stops it). Put a test license key in `run/plugins/MyPlugin/config.yml` and run `/myplugin recheck`.

Your features go in `MyPlugin.startFeatures()`, which runs once the license is valid.

## 3. Sell it

| Where | How the key gets to the server |
|---|---|
| [BuiltByBit](https://docs.sunlicense.com/integrations/builtbybit), [Voxel Shop](https://docs.sunlicense.com/integrations/voxel-shop) | Create a `%%__SUNLICENSE__%%` placeholder in the store. It replaces `BUILT_IN_KEY` in `MyPlugin` with each buyer's key on download, so there's nothing to configure |
| [SpigotMC](https://docs.sunlicense.com/integrations/spigotmc), [Tebex](https://docs.sunlicense.com/integrations/tebex), [Gumroad](https://docs.sunlicense.com/integrations/gumroad) and the other stores | The buyer gets a key by email or on the redeem page and puts it in `config.yml` |

## What happens on a server

| SunLicense answers | The plugin |
|---|---|
| Valid | Starts your features; logs the masked key |
| Expired, revoked, suspended, unknown key, too many servers, blacklisted | Logs why (see `license/Messages.java`) and disables itself |
| Nothing (outage, network, rate limit) | Uses `plugins/MyPlugin/license.lic` if it's a valid offline license for this key; otherwise keeps running and retries every 10 minutes (`ALLOW_WHEN_UNREACHABLE = false` to stop instead) |

Each server keeps a random id in `plugins/MyPlugin/.server-id`, which is what counts against the
policy's machine limit. `/myplugin` shows the license status; `/myplugin recheck` checks again (ops only).

Developing against your own license? Add an **exclusion** for your machine or IP in SunLicense
([blacklists and exclusions](https://docs.sunlicense.com/guide/access-rules)) so your test servers
don't use up slots.

## Files

```
build.gradle.kts                          Paper API, SDK, Shadow relocation, settings baked into the jar
src/main/java/.../MyPlugin.java           start-up, re-checks, the command, your features
src/main/java/.../license/LicenseGuard.java   online check → offline file → status (no Bukkit, tested)
src/main/java/.../license/OfflineLicense.java Ed25519 offline license file check
src/main/java/.../license/Messages.java   what the server owner reads
src/main/resources/config.yml             license-key, recheck-hours
src/test/java/.../LicenseGuardTest.java   every result against a stub SunLicense
.github/workflows/build.yml               CI
```

## Licence

[MIT No Attribution](LICENSE): copy any of it into your plugin, commercial or not, without credit.
