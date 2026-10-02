package com.tremeq.glowingblocks;

import com.tremeq.glowingblocks.data.BlockPosition;
import com.tremeq.glowingblocks.data.GlowingBlockData;
import com.tremeq.glowingblocks.libs.glowingentities.GlowingBlocks;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.logging.Level;

/** Tracks only chunks observed by players; never loads chunks to render a saved effect. */
public final class BlockManager {
    private final GlowingEntitiesPlugin plugin;
    private final GlowingBlocks renderer;
    private final Map<UUID, Set<BlockPosition.ChunkKey>> observed = new HashMap<>();
    private final Map<BlockPosition, String> states = new HashMap<>();

    public BlockManager(GlowingEntitiesPlugin plugin, GlowingBlocks renderer) { this.plugin = plugin; this.renderer = renderer; }

    public void seed(Player player) {
        if (!player.isOnline()) return;
        // Needed when enabled/reloaded while players are online, before any new chunk packet event.
        int radius = Math.min(player.getViewDistance(), Bukkit.getViewDistance());
        int px = player.getLocation().getBlockX() >> 4, pz = player.getLocation().getBlockZ() >> 4;
        Set<BlockPosition.ChunkKey> chunks = observed.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>());
        for (Chunk chunk : player.getWorld().getLoadedChunks()) {
            if (Math.abs(chunk.getX() - px) <= radius && Math.abs(chunk.getZ() - pz) <= radius)
                chunks.add(new BlockPosition.ChunkKey(player.getWorld().getName(), chunk.getX(), chunk.getZ()));
        }
        for (BlockPosition.ChunkKey chunk : Set.copyOf(chunks)) refreshChunk(player, chunk);
    }
    public void loaded(Player player, Chunk chunk) {
        BlockPosition.ChunkKey key = new BlockPosition.ChunkKey(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
        observed.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>()).add(key);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && observed.getOrDefault(player.getUniqueId(), Set.of()).contains(key)) refreshChunk(player, key);
        }, plugin.settings().chunkDelay());
        if (plugin.isDebugEnabled() && plugin.getConfig().getBoolean("debug.log-chunk-loads")) plugin.getLogger().info(player.getName() + " loaded " + key);
    }
    public void unloaded(Player player, Chunk chunk) {
        BlockPosition.ChunkKey key = new BlockPosition.ChunkKey(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
        Set<BlockPosition.ChunkKey> chunks = observed.get(player.getUniqueId());
        if (chunks != null) chunks.remove(key);
        for (BlockPosition pos : renderer.activePositions(player)) if (pos.chunk().equals(key)) renderer.hide(player, pos);
        pruneStates();
    }
    private void refreshChunk(Player player, BlockPosition.ChunkKey chunk) {
        if (!player.getWorld().getName().equals(chunk.world())) return;
        for (BlockPosition pos : plugin.getDataManager().positions(chunk)) refresh(player, pos);
    }
    public void refresh(BlockPosition pos) {
        for (Player player : Bukkit.getOnlinePlayers()) refresh(player, pos);
    }
    public void refresh(Player player, BlockPosition pos) {
        if (!player.isOnline() || !player.getWorld().getName().equals(pos.world())
                || !observed.getOrDefault(player.getUniqueId(), Set.of()).contains(pos.chunk())) {
            renderer.hide(player, pos);
            return;
        }
        World world = player.getWorld();
        if (!world.isChunkLoaded(pos.x() >> 4, pos.z() >> 4)) { renderer.hide(player, pos); return; }
        GlowingBlockData data = plugin.getDataManager().visibleBlock(pos, player.getUniqueId(), plugin.settings().globalMode());
        if (data == null) { renderer.hide(player, pos); return; }
        Block block = world.getBlockAt(pos.x(), pos.y(), pos.z());
        if (block.getType() != data.blockType()) { remove(pos); return; }
        String state = block.getBlockData().getAsString();
        String old = states.put(pos, state);
        if (old != null && !old.equals(state)) renderer.invalidate(pos);
        ChatColor color = data.animated() && plugin.settings().rainbow() ? plugin.getRainbowAnimator().getCurrentColor() : data.color();
        try { renderer.show(block, player, data, color); }
        catch (RuntimeException e) { plugin.getLogger().log(Level.WARNING, "Cannot render " + pos, e); }
    }
    public void remove(BlockPosition pos) {
        plugin.getDataManager().removePosition(pos);
        renderer.invalidate(pos);
        states.remove(pos);
    }
    public void changed(BlockPosition pos) {
        if (plugin.getDataManager().positions(pos.chunk()).contains(pos)) {
            renderer.invalidate(pos);
            states.remove(pos);
            refresh(pos);
        }
    }
    public void animate() {
        for (Player player : Bukkit.getOnlinePlayers()) for (BlockPosition pos : renderer.activePositions(player)) {
            GlowingBlockData data = plugin.getDataManager().visibleBlock(pos, player.getUniqueId(), plugin.settings().globalMode());
            if (data != null && data.animated()) refresh(player, pos);
        }
    }
    public void maintain() {
        // Capture positions before refreshing: a state change invalidates every viewer's display.
        Set<BlockPosition> positions = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) positions.addAll(renderer.activePositions(player));
        for (BlockPosition pos : positions) refresh(pos);
        pruneStates();
    }
    public void refreshAll() {
        renderer.disable();
        states.clear();
        for (Player player : Bukkit.getOnlinePlayers()) seed(player);
    }
    public void clearPlayer(Player player) {
        renderer.clearPlayer(player);
        observed.remove(player.getUniqueId());
        pruneStates();
    }
    public void unloadChunk(Chunk chunk) {
        BlockPosition.ChunkKey key = new BlockPosition.ChunkKey(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
        renderer.unloadChunk(key);
        states.keySet().removeIf(pos -> pos.chunk().equals(key));
    }
    private void pruneStates() {
        Set<BlockPosition> active = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) active.addAll(renderer.activePositions(player));
        states.keySet().retainAll(active);
    }
    public void close() { renderer.disable(); observed.clear(); states.clear(); }
}
