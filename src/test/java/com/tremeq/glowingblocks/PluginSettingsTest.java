package com.tremeq.glowingblocks;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PluginSettingsTest {
    @Test void usesConfiguredColorsAndFeatureToggles() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("rainbow.colors", List.of("BLUE", "RED")); config.set("rainbow.interval-ticks", 7);
        config.set("features.rainbow-animation", false); config.set("features.global-mode", false);
        config.set("defaults.raycast-range", 12); config.set("defaults.visibility", "global");
        PluginSettings settings = PluginSettings.read(config);
        assertEquals(List.of(ChatColor.BLUE, ChatColor.RED), settings.colors());
        assertEquals(7, settings.rainbowInterval()); assertEquals(12, settings.range());
        assertFalse(settings.rainbow()); assertFalse(settings.globalMode()); assertTrue(settings.defaultGlobal());
    }
    @Test void rejectsInvalidColorAndTiming() {
        YamlConfiguration c = new YamlConfiguration(); c.set("rainbow.colors", List.of("BOLD"));
        assertThrows(IllegalArgumentException.class, () -> PluginSettings.read(c));
        c.set("rainbow.colors", List.of()); assertThrows(IllegalArgumentException.class, () -> PluginSettings.read(c));
        c.set("rainbow.colors", List.of("RED")); c.set("rainbow.interval-ticks", 0);
        assertThrows(IllegalArgumentException.class, () -> PluginSettings.read(c));
    }
    @Test void validatesLimitsAndSupportsZero() {
        YamlConfiguration c = new YamlConfiguration(); c.set("performance.max-global-blocks", 0);
        assertEquals(0, PluginSettings.read(c).globalLimit());
        c.set("performance.max-global-blocks", -1);
        assertThrows(IllegalArgumentException.class, () -> PluginSettings.read(c));
    }
}
