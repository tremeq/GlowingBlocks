package com.tremeq.glowingblocks;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.Map;

public final class Messages {
    private final GlowingEntitiesPlugin plugin;
    private static final Map<String, String> EXTRA = Map.ofEntries(
            Map.entry("feature-disabled", "&cThis feature is disabled in config.yml."),
            Map.entry("aliases-disabled", "&cCommand aliases are disabled; use /{command}."),
            Map.entry("save-failed", "&cCould not save data. Check the server log."),
            Map.entry("reload-failed", "&cReload failed: {error}. Active settings and data were retained."),
            Map.entry("operation-failed", "&cCould not apply glow. Check the server log."),
            Map.entry("cooldown", "&cPlease wait before using this command again."),
            Map.entry("global-permission", "&cYou need glowingblocks.global to edit global effects."),
            Map.entry("list-npcs", "&7NPC effects: &e{total}"),
            Map.entry("headless-console", "&cThe console must specify global for NPC commands."),
            Map.entry("usage", "&cUsage: {usage}"));

    public Messages(GlowingEntitiesPlugin plugin) { this.plugin = plugin; }
    public String text(String key, Object... variables) {
        String text = plugin.getConfig().getString("messages." + key, EXTRA.getOrDefault(key, key));
        for (int i = 0; i + 1 < variables.length; i += 2)
            text = text.replace("{" + variables[i] + "}", String.valueOf(variables[i + 1]));
        return ChatColor.translateAlternateColorCodes('&', text);
    }
    public void send(CommandSender sender, String key, Object... variables) {
        sender.sendMessage(text("prefix") + text(key, variables));
    }
}
