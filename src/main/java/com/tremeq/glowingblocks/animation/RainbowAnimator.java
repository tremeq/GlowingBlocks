package com.tremeq.glowingblocks.animation;

import com.tremeq.glowingblocks.GlowingEntitiesPlugin;
import org.bukkit.ChatColor;
import org.bukkit.scheduler.BukkitTask;

public final class RainbowAnimator {
    private final GlowingEntitiesPlugin plugin;
    private BukkitTask task;
    private int index;

    public RainbowAnimator(GlowingEntitiesPlugin plugin) { this.plugin = plugin; }
    public void start() {
        stop();
        index = 0;
        if (plugin.settings().rainbow()) task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            index = (index + 1) % plugin.settings().colors().size();
            plugin.getBlockManager().animate();
            plugin.getCitizensIntegration().refreshAll();
        }, plugin.settings().rainbowInterval(), plugin.settings().rainbowInterval());
    }
    public void stop() { if (task != null) { task.cancel(); task = null; } }
    public ChatColor getCurrentColor() { return plugin.settings().colors().get(index % plugin.settings().colors().size()); }
}
