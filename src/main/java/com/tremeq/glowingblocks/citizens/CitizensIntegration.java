package com.tremeq.glowingblocks.citizens;

import com.tremeq.glowingblocks.GlowingEntitiesPlugin;
import com.tremeq.glowingblocks.libs.glowingentities.GlowingEntities;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.NPCDespawnEvent;
import net.citizensnpcs.api.event.NPCRemoveEvent;
import net.citizensnpcs.api.event.NPCSpawnEvent;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

import java.util.*;
import java.util.logging.Level;

/** No persistent Citizens metadata is changed by this integration. */
public final class CitizensIntegration implements Listener {
    private final GlowingEntitiesPlugin plugin;
    private final Map<UUID, Map<Integer, Applied>> applied = new HashMap<>();
    private GlowingEntities api;
    private boolean registered;
    private boolean failed;

    public CitizensIntegration(GlowingEntitiesPlugin plugin) { this.plugin = plugin; }
    public boolean isEnabled() {
        return api != null && plugin.settings().npcGlow() && Bukkit.getPluginManager().isPluginEnabled("Citizens");
    }
    public void initialize() {
        if (!plugin.settings().npcGlow() || !Bukkit.getPluginManager().isPluginEnabled("Citizens")) { shutdown(); return; }
        if (api != null || failed) return;
        try {
            api = new GlowingEntities(plugin);
            Bukkit.getPluginManager().registerEvents(this, plugin);
            registered = true;
        } catch (Exception | LinkageError e) {
            failed = true;
            plugin.getLogger().log(Level.SEVERE, "NPC glow could not initialize for this server version; block glow is still available", e);
        }
    }
    public void reconfigure() { failed = false; initialize(); refreshAll(); }

    public NPC getNPC(String identifier) {
        if (!isEnabled()) return null;
        try { return CitizensAPI.getNPCRegistry().getById(Integer.parseInt(identifier)); }
        catch (NumberFormatException ignored) {
            for (NPC npc : CitizensAPI.getNPCRegistry()) if (npc.getName().equalsIgnoreCase(identifier)) return npc;
            return null;
        }
    }
    public List<String> suggestions() {
        List<String> result = new ArrayList<>();
        if (isEnabled()) for (NPC npc : CitizensAPI.getNPCRegistry()) {
            result.add(String.valueOf(npc.getId()));
            if (!npc.getName().contains(" ")) result.add(npc.getName());
        }
        return result;
    }
    public void refreshAll() {
        if (!isEnabled()) return;
        for (Player player : Bukkit.getOnlinePlayers()) refresh(player);
    }
    public void refresh(Player player) {
        if (!isEnabled() || !player.isOnline()) return;
        Set<Integer> ids = new HashSet<>();
        for (NPCGlowData data : plugin.getDataManager().getAllNPCs()) ids.add(data.npcId());
        ids.addAll(applied.getOrDefault(player.getUniqueId(), Map.of()).keySet());
        for (int id : ids) {
            NPCGlowData desired = plugin.getDataManager().visibleNPC(id, player.getUniqueId(), plugin.settings().globalMode());
            NPC npc = CitizensAPI.getNPCRegistry().getById(id);
            Entity entity = npc != null && npc.isSpawned() ? npc.getEntity() : null;
            if (desired == null || entity == null || !entity.getWorld().equals(player.getWorld()) || !player.canSee(entity)) {
                clear(player, id);
                continue;
            }
            ChatColor color = desired.animated() && plugin.settings().rainbow() ? plugin.getRainbowAnimator().getCurrentColor() : desired.color();
            Applied old = applied.getOrDefault(player.getUniqueId(), Map.of()).get(id);
            if (old != null && !old.entity.getUniqueId().equals(entity.getUniqueId())) clear(player, id);
            try {
                api.setGlowing(entity, player, color);
                applied.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>()).put(id, new Applied(entity));
            } catch (ReflectiveOperationException | RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "Cannot apply glow to NPC " + id, e);
            }
        }
    }
    private void clear(Player player, int id) {
        Map<Integer, Applied> current = applied.get(player.getUniqueId());
        Applied old = current == null ? null : current.remove(id);
        if (old != null && api != null && player.isOnline()) {
            try { api.unsetGlowing(old.entity, player); }
            catch (ReflectiveOperationException | RuntimeException e) { plugin.getLogger().log(Level.WARNING, "Cannot clear NPC glow " + id, e); }
        }
        if (current != null && current.isEmpty()) applied.remove(player.getUniqueId());
    }
    public void clearPlayer(Player player) {
        for (int id : new ArrayList<>(applied.getOrDefault(player.getUniqueId(), Map.of()).keySet())) clear(player, id);
        applied.remove(player.getUniqueId());
        if (api != null) api.clearPlayer(player, player.isOnline());
    }
    public void clearAll() {
        for (UUID id : new ArrayList<>(applied.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) clearPlayer(player);
        }
        applied.clear();
    }
    public void shutdown() {
        clearAll();
        if (api != null) { api.disable(); api = null; }
        if (registered) HandlerList.unregisterAll(this);
        registered = false;
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(NPCSpawnEvent event) {
        Bukkit.getScheduler().runTask(plugin, this::refreshAll);
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDespawn(NPCDespawnEvent event) {
        for (Player player : Bukkit.getOnlinePlayers()) clear(player, event.getNPC().getId());
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void onRemove(NPCRemoveEvent event) {
        int id = event.getNPC().getId();
        for (Player player : Bukkit.getOnlinePlayers()) clear(player, id);
        plugin.getDataManager().removeNPC(id);
    }
    private record Applied(Entity entity) {}
}
