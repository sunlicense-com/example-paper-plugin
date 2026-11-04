/*
 * MyPlugin by You. Licensed software: each server needs its own license. Terms: https://example.com/terms
 * Licensing by SunLicense (https://sunlicense.com).
 */
package com.example.myplugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import com.example.myplugin.license.LicenseGuard;
import com.example.myplugin.license.LicenseStatus;
import com.example.myplugin.license.Messages;
import com.example.myplugin.license.SealedCore;
import com.example.myplugin.license.SunLicenseAnalytics;
import com.example.myplugin.license.SunLicenseProtection;

/**
 * A Paper plugin protected by SunLicense. The license is checked off the main thread at start-up and
 * every {@code recheck-hours}; your features ({@code core/CoreFeatures}) start only once it is valid
 * ({@link #startFeatures()}). Release builds seal the core, so only a valid license can run it.
 */
public final class MyPlugin extends JavaPlugin {
    /**
     * BuiltByBit and Voxel Shop replace this string with the buyer's license key when they download the
     * jar (create a "%%__SUNLICENSE__%%" placeholder in the store, see docs.sunlicense.com/integrations).
     * Builds from anywhere else keep it, and the key comes from config.yml instead.
     */
    private static final String BUILT_IN_KEY = "%%__SUNLICENSE__%%";

    /**
     * When SunLicense can't be reached and there is no valid offline license file: keep running (true,
     * the default: an outage on our side shouldn't take your customers' servers down) or stop (false).
     */
    private static final boolean ALLOW_WHEN_UNREACHABLE = true;

    /** How soon to try again after no answer. */
    private static final long RETRY_TICKS = 20L * 60 * 10;

    /** The core's entry class (sealed in release builds). */
    private static final String CORE = "com.example.myplugin.core.CoreFeatures";

    /** The ownership notice logged at start-up: who makes the plugin and where its terms are. */
    private static final String AUTHOR = "You";
    private static final String TERMS = "https://example.com/terms";

    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean retryScheduled = new AtomicBoolean();
    private LicenseGuard guard;
    private SunLicenseAnalytics analytics;
    private Features features;
    private String licenseKey;
    private String serverId;
    private volatile LicenseStatus status;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        LicenseGuard.Settings settings;
        try {
            settings = LicenseGuard.Settings.load(getResource("sunlicense.properties"));
            guard = new LicenseGuard(settings, getDataFolder().toPath().resolve("license.lic"),
                    SunLicenseProtection.builder().product(getName(), AUTHOR).terms(TERMS));
            serverId = serverId();
        } catch (IOException e) {
            getLogger().severe("Can't start the license check: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        licenseKey = LicenseGuard.pickKey(BUILT_IN_KEY, getConfig().getString("license-key", ""));
        guard.protection().license(licenseKey, serverId);
        getLogger().info(guard.protection().notice());
        SealedCore.checkEarly(guard.protection(), getClassLoader());   // so the first check reports a modified jar
        // Anonymous usage statistics for your Analytics tab, every 30 minutes on a background thread (config.yml: analytics).
        analytics = SunLicenseAnalytics.builder().baseUrl(settings.url()).apiKey(settings.apiKey())
                .licenseKey(licenseKey).fingerprint(serverId).productVersion(getPluginMeta().getVersion())
                .enabled(getConfig().getBoolean("analytics", true)).start();
        checkAsync();
        long hours = getConfig().getLong("recheck-hours", 6);
        if (hours > 0) {
            long ticks = hours * 60 * 60 * 20;
            getServer().getScheduler().runTaskTimerAsynchronously(this, () -> apply(guard.check(licenseKey, serverId)), ticks, ticks);
        }
    }

    private void checkAsync() {
        getServer().getScheduler().runTaskAsynchronously(this, () -> apply(guard.check(licenseKey, serverId)));
    }

    @Override
    public void onDisable() {
        if (features != null) {
            features.stop();
        }
        if (analytics != null) {
            analytics.close();
        }
    }

    /** Runs on an async thread; anything touching the server goes back to the main thread. */
    private void apply(LicenseStatus result) {
        status = result;
        getServer().getScheduler().runTask(this, () -> {
            if (!isEnabled()) {
                return;
            }
            switch (result.state()) {
                case VALID, VALID_OFFLINE -> {
                    if (started.compareAndSet(false, true)) {
                        getLogger().info("License " + Messages.mask(licenseKey) + ": " + result.message());
                        startFeatures();
                    }
                }
                case REJECTED, NOT_CONFIGURED -> {
                    getLogger().severe("License " + Messages.mask(licenseKey) + ": " + result.message());
                    getLogger().severe("MyPlugin is disabled until this is fixed.");
                    getServer().getPluginManager().disablePlugin(this);
                }
                case UNREACHABLE -> {
                    getLogger().warning(result.message() + " Trying again in 10 minutes.");
                    if (ALLOW_WHEN_UNREACHABLE) {
                        if (started.compareAndSet(false, true)) {
                            startFeatures();
                        }
                    } else if (!started.get()) {
                        getLogger().severe("MyPlugin needs a license check to start; it will start once SunLicense answers.");
                    }
                    if (retryScheduled.compareAndSet(false, true)) {   // one retry at a time
                        getServer().getScheduler().runTaskLaterAsynchronously(this, () -> {
                            retryScheduled.set(false);
                            apply(guard.check(licenseKey, serverId));
                        }, RETRY_TICKS);
                    }
                }
            }
        });
    }

    /** Starts the core (core/CoreFeatures). In a release build it needs the unlock from a valid license check. */
    private void startFeatures() {
        try {
            features = SealedCore.load(guard.protection(), getClassLoader(), CORE, Features.class);
            features.start(this);
        } catch (SunLicenseProtection.Locked e) {
            started.set(false);   // starts with the next valid check
            getLogger().severe("MyPlugin can't start yet: " + e.getMessage()
                    + " It starts as soon as SunLicense confirms the license (the confirmation is kept for 7 days).");
        } catch (Exception e) {
            getLogger().severe("MyPlugin's core failed to load: " + e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("recheck")) {
            sender.sendMessage(Component.text("Checking the license…", NamedTextColor.GRAY));
            checkAsync();
            return true;
        }
        LicenseStatus s = status;
        if (s == null) {
            sender.sendMessage(Component.text("The license hasn't been checked yet.", NamedTextColor.GRAY));
        } else {
            NamedTextColor color = s.allowed() ? NamedTextColor.GREEN : s.state() == LicenseStatus.State.UNREACHABLE
                    ? NamedTextColor.YELLOW : NamedTextColor.RED;
            sender.sendMessage(Component.text("License " + Messages.mask(licenseKey) + " (" + s.state() + "): " + s.message(), color));
        }
        return true;
    }

    /**
     * A random id for this server, kept in the plugin's folder, so restarts count as the same machine.
     * Copying the folder to another server copies the id too; the license's machine limit still applies
     * to each id.
     */
    private String serverId() throws IOException {
        Path file = getDataFolder().toPath().resolve(".server-id");
        if (Files.isRegularFile(file)) {
            String id = Files.readString(file, StandardCharsets.UTF_8).trim();
            if (!id.isEmpty()) {
                return id;
            }
        }
        Files.createDirectories(file.getParent());
        String id = "paper-" + UUID.randomUUID();
        Files.writeString(file, id, StandardCharsets.UTF_8);
        return id;
    }
}
