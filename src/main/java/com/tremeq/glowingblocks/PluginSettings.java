package com.tremeq.glowingblocks;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public record PluginSettings(boolean globalMode, boolean rainbow, boolean npcGlow, boolean aliases,
                             boolean defaultGlobal, int range, int playerLimit, int globalLimit,
                             long joinDelay, long chunkDelay, long rainbowInterval, List<ChatColor> colors) {
    public static PluginSettings read(FileConfiguration config) {
        String visibility = config.getString("defaults.visibility", "private");
        if (!visibility.equalsIgnoreCase("private") && !visibility.equalsIgnoreCase("global"))
            throw new IllegalArgumentException("defaults.visibility must be private or global");
        List<String> configured = config.getStringList("rainbow.colors");
        if (!config.contains("rainbow.colors")) configured = List.of("RED", "GOLD", "YELLOW", "GREEN", "AQUA", "BLUE", "LIGHT_PURPLE");
        if (configured.isEmpty()) throw new IllegalArgumentException("rainbow.colors must not be empty");
        List<ChatColor> colors = new ArrayList<>();
        for (String name : configured) {
            ChatColor color = ChatColor.valueOf(name.toUpperCase(Locale.ROOT));
            if (!color.isColor()) throw new IllegalArgumentException("Invalid rainbow color: " + name);
            colors.add(color);
        }
        return new PluginSettings(config.getBoolean("features.global-mode", true),
                config.getBoolean("rainbow.enabled", true) && config.getBoolean("features.rainbow-animation", true),
                config.getBoolean("features.npc-glow", true), config.getBoolean("features.command-aliases", true),
                visibility.equalsIgnoreCase("global"), bounded(config, "defaults.raycast-range", 5, 1, 128),
                bounded(config, "performance.max-blocks-per-player", 1000, 0, 100000),
                bounded(config, "performance.max-global-blocks", 500, 0, 100000),
                bounded(config, "performance.player-join-delay", 1, 0, 1200),
                bounded(config, "performance.chunk-load-delay", 1, 0, 1200),
                bounded(config, "rainbow.interval-ticks", 20, 1, 72000), List.copyOf(colors));
    }
    private static int bounded(FileConfiguration c, String key, int fallback, int min, int max) {
        if (c.contains(key) && !c.isInt(key)) throw new IllegalArgumentException(key + " must be an integer");
        int value = c.getInt(key, fallback);
        if (value < min || value > max) throw new IllegalArgumentException(key + " must be between " + min + " and " + max);
        return value;
    }
}
