package com.tremeq.glowingblocks.listeners;

import com.tremeq.glowingblocks.GlowingEntitiesPlugin;
import com.tremeq.glowingblocks.data.BlockPosition;
import io.papermc.paper.event.packet.PlayerChunkLoadEvent;
import io.papermc.paper.event.packet.PlayerChunkUnloadEvent;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import java.util.HashSet;
import java.util.Set;

public final class PlayerJoinListener implements Listener {
    private final GlowingEntitiesPlugin plugin;
    private final Set<BlockPosition> changed = new HashSet<>();
    private boolean queued;
    public PlayerJoinListener(GlowingEntitiesPlugin plugin) { this.plugin = plugin; }
    private void load(Player player) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            plugin.getBlockManager().seed(player);
            plugin.getCitizensIntegration().refresh(player);
        }, plugin.settings().joinDelay());
    }
    private void clear(Player player) {
        plugin.getBlockManager().clearPlayer(player);
        plugin.getCitizensIntegration().clearPlayer(player);
        plugin.getCommandHandler().forget(player.getUniqueId());
    }
    @EventHandler(priority = EventPriority.MONITOR) public void join(PlayerJoinEvent e) { load(e.getPlayer()); }
    @EventHandler(priority = EventPriority.MONITOR) public void quit(PlayerQuitEvent e) { clear(e.getPlayer()); }
    @EventHandler(priority = EventPriority.MONITOR) public void world(PlayerChangedWorldEvent e) { clear(e.getPlayer()); load(e.getPlayer()); }
    @EventHandler(priority = EventPriority.MONITOR) public void respawn(PlayerRespawnEvent e) { clear(e.getPlayer()); load(e.getPlayer()); }
    @EventHandler(priority = EventPriority.MONITOR) public void load(PlayerChunkLoadEvent e) { plugin.getBlockManager().loaded(e.getPlayer(), e.getChunk()); }
    @EventHandler(priority = EventPriority.MONITOR) public void unload(PlayerChunkUnloadEvent e) { plugin.getBlockManager().unloaded(e.getPlayer(), e.getChunk()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void unload(ChunkUnloadEvent e) { plugin.getBlockManager().unloadChunk(e.getChunk()); }
    @EventHandler(priority = EventPriority.MONITOR) public void load(ChunkLoadEvent e) {
        for (org.bukkit.entity.Entity entity : e.getChunk().getEntities()) plugin.getGlowingBlocks().cleanupOrphan(entity);
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void world(WorldUnloadEvent e) {
        for (org.bukkit.Chunk chunk : e.getWorld().getLoadedChunks()) plugin.getBlockManager().unloadChunk(chunk);
    }
    @EventHandler(priority = EventPriority.MONITOR) public void enabled(PluginEnableEvent e) {
        if (e.getPlugin().getName().equals("Citizens")) plugin.getCitizensIntegration().reconfigure();
    }
    @EventHandler(priority = EventPriority.MONITOR) public void disabled(PluginDisableEvent e) {
        if (e.getPlugin().getName().equals("Citizens")) plugin.getCitizensIntegration().shutdown();
    }
    private void changed(Block block) {
        BlockPosition pos = BlockPosition.of(block);
        if (!plugin.getDataManager().positions(pos.chunk()).contains(pos)) return;
        changed.add(pos);
        if (queued) return;
        queued = true;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            queued = false;
            for (BlockPosition p : Set.copyOf(changed)) plugin.getBlockManager().changed(p);
            changed.clear();
        });
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void broken(BlockBreakEvent e) { changed(e.getBlock()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void placed(BlockPlaceEvent e) { changed(e.getBlock()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void physics(BlockPhysicsEvent e) { changed(e.getBlock()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void burn(BlockBurnEvent e) { changed(e.getBlock()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void fade(BlockFadeEvent e) { changed(e.getBlock()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void entity(EntityChangeBlockEvent e) { changed(e.getBlock()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void explode(BlockExplodeEvent e) { e.blockList().forEach(this::changed); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void explode(EntityExplodeEvent e) { e.blockList().forEach(this::changed); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void extend(BlockPistonExtendEvent e) { e.getBlocks().forEach(b -> plugin.getBlockManager().remove(BlockPosition.of(b))); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void retract(BlockPistonRetractEvent e) { e.getBlocks().forEach(b -> plugin.getBlockManager().remove(BlockPosition.of(b))); }
}
