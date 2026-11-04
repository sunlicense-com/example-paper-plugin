package com.example.myplugin.core;

import com.example.myplugin.Features;
import com.example.myplugin.MyPlugin;

/**
 * Your plugin's features: register listeners, commands and tasks here. Everything in this package is the
 * "core": release builds encrypt it, and only a valid license unlocks it (see {@code license/SealedCore}).
 * Put the code your plugin can't work without here. Bukkit finds the main class and plugin.yml outside the core,
 * so those stay where they are.
 */
public final class CoreFeatures implements Features {
    @Override
    public void start(MyPlugin plugin) {
        plugin.getLogger().info("MyPlugin is running.");
    }

    @Override
    public void stop() {
    }
}
