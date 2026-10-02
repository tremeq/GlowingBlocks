package com.tremeq.glowingblocks.data;

import com.tremeq.glowingblocks.citizens.NPCGlowData;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;

/** Main-thread data model; only immutable snapshots are passed to the disk writer. */
public final class DataManager {
    private final Plugin plugin;
    private final Path file;
    private Map<GlowingBlockData.Key, GlowingBlockData> blocks = new LinkedHashMap<>();
    private Map<NPCGlowData.Key, NPCGlowData> npcs = new LinkedHashMap<>();
    private final Map<BlockPosition.ChunkKey, Set<BlockPosition>> chunks = new HashMap<>();
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "GlowingBlocks-save");
        t.setDaemon(true);
        return t;
    });
    private BukkitTask pendingSave;
    private long revision;
    private volatile long savedRevision;
    private boolean closed;

    public DataManager(Plugin plugin) throws IOException {
        this.plugin = plugin;
        file = plugin.getDataFolder().toPath().resolve("saves.yml");
        read();
    }

    /** Reload is transactional. Pending in-memory edits are flushed first. */
    public void reload() throws IOException {
        cancelPending();
        if (revision != savedRevision && !save()) throw new IOException("Cannot save pending changes");
        // A previous asynchronous write must finish before reading an administrator's file.
        awaitWriter();
        read();
    }

    private void read() throws IOException {
        if (!Files.exists(file)) return;
        YamlConfiguration yaml = new YamlConfiguration();
        Map<GlowingBlockData.Key, GlowingBlockData> newBlocks = new LinkedHashMap<>();
        Map<NPCGlowData.Key, NPCGlowData> newNPCs = new LinkedHashMap<>();
        try {
            yaml.load(file.toFile());
            if (yaml.contains("format-version") && !yaml.isInt("format-version"))
                throw new IllegalArgumentException("format-version must be an integer");
            int format = yaml.getInt("format-version", 1);
            if (format != 1 && format != 2) throw new IllegalArgumentException("Unsupported saves format " + format);
            if (format == 2) {
                for (Map<?, ?> row : rows(yaml, "blocks")) {
                    BlockPosition pos = new BlockPosition(string(row, "world"), integer(row, "x"), integer(row, "y"), integer(row, "z"));
                    GlowingBlockData data = new GlowingBlockData(pos, Material.valueOf(string(row, "type")),
                            color(row), bool(row, "animated"), owner(row), number(row, "createdTime", 0));
                    if (newBlocks.put(data.key(), data) != null) throw new IllegalArgumentException("Duplicate block " + pos);
                }
                for (Map<?, ?> row : rows(yaml, "npcs")) {
                    NPCGlowData data = new NPCGlowData(integer(row, "id"), string(row, "name"), color(row), bool(row, "animated"), owner(row));
                    if (newNPCs.put(data.key(), data) != null) throw new IllegalArgumentException("Duplicate NPC " + data.npcId());
                }
            } else {
                for (String key : List.of("blocks", "npcs")) {
                    if (yaml.contains(key) && !yaml.isConfigurationSection(key))
                        throw new IllegalArgumentException("Legacy " + key + " must be a mapping; lists require format-version: 2");
                }
                readLegacyBlocks(yaml.getConfigurationSection("blocks"), "", newBlocks);
                ConfigurationSection section = yaml.getConfigurationSection("npcs");
                if (section != null) for (String id : section.getKeys(false)) {
                    ConfigurationSection row = Objects.requireNonNull(section.getConfigurationSection(id));
                    // Legacy private NPC entries contain no owner and were broadcast to everybody.
                    NPCGlowData data = new NPCGlowData(Integer.parseInt(id), row.getString("name", "Unknown"),
                            ChatColor.valueOf(row.getString("color", "RED")), row.getBoolean("animated"), null);
                    newNPCs.put(data.key(), data);
                    if (!row.getBoolean("global", true)) plugin.getLogger().warning("Legacy NPC " + id + " has no owner; migrated as global.");
                }
                Path backup = file.resolveSibling("saves.yml.v1.bak");
                if (!Files.exists(backup)) Files.copy(file, backup);
            }
            blocks = newBlocks;
            npcs = newNPCs;
            rebuildIndex();
            revision++;
            savedRevision = format == 2 ? revision : revision - 1;
        } catch (Exception e) {
            throw new IOException("Invalid saves.yml; existing data preserved: " + e.getMessage(), e);
        }
    }

    private void readLegacyBlocks(ConfigurationSection section, String prefix, Map<GlowingBlockData.Key, GlowingBlockData> result) {
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            ConfigurationSection row = section.getConfigurationSection(key);
            if (row == null) throw new IllegalArgumentException("Invalid legacy block " + key);
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            if (!row.contains("type") && !row.contains("color")) {
                readLegacyBlocks(row, path, result); // Recover world names containing dots.
                continue;
            }
            String[] parts = path.split(",");
            if (parts.length < 4) throw new IllegalArgumentException("Invalid block position " + path);
            int n = parts.length;
            BlockPosition pos = new BlockPosition(String.join(",", Arrays.copyOf(parts, n - 3)),
                    Integer.parseInt(parts[n - 3]), Integer.parseInt(parts[n - 2]), Integer.parseInt(parts[n - 1]));
            UUID owner = row.getBoolean("global") ? null : UUID.fromString(row.getString("owner", ""));
            GlowingBlockData data = new GlowingBlockData(pos, Material.valueOf(row.getString("type", "PLAYER_HEAD")),
                    ChatColor.valueOf(row.getString("color", "RED")), row.getBoolean("animated"), owner,
                    row.getLong("createdTime", 0));
            result.put(data.key(), data);
        }
    }

    private static List<Map<?, ?>> rows(YamlConfiguration yaml, String key) {
        Object raw = yaml.get(key);
        if (raw == null) return List.of();
        if (!(raw instanceof List<?> list)) throw new IllegalArgumentException(key + " must be a list");
        List<Map<?, ?>> result = new ArrayList<>();
        for (Object row : list) {
            if (!(row instanceof Map<?, ?> map)) throw new IllegalArgumentException("Invalid " + key + " entry");
            result.add(map);
        }
        return result;
    }

    private static String string(Map<?, ?> row, String key) {
        Object value = row.get(key);
        if (!(value instanceof String text) || text.isBlank()) throw new IllegalArgumentException("Missing/invalid " + key);
        return text;
    }
    private static long number(Map<?, ?> row, String key, long fallback) {
        Object value = row.get(key);
        if (value == null) return fallback;
        if (!(value instanceof Number n) || n.doubleValue() != n.longValue()) throw new IllegalArgumentException("Invalid " + key);
        return n.longValue();
    }
    private static int integer(Map<?, ?> row, String key) {
        if (!row.containsKey(key)) throw new IllegalArgumentException("Missing " + key);
        return Math.toIntExact(number(row, key, 0));
    }
    private static boolean bool(Map<?, ?> row, String key) {
        Object value = row.get(key);
        if (!(value instanceof Boolean b)) throw new IllegalArgumentException("Invalid " + key);
        return b;
    }
    private static ChatColor color(Map<?, ?> row) { return ChatColor.valueOf(string(row, "color")); }
    private static UUID owner(Map<?, ?> row) { return bool(row, "global") ? null : UUID.fromString(string(row, "owner")); }

    public Collection<GlowingBlockData> getAllBlocks() { return List.copyOf(blocks.values()); }
    public Collection<NPCGlowData> getAllNPCs() { return List.copyOf(npcs.values()); }
    public GlowingBlockData getBlock(BlockPosition pos, UUID owner) { return blocks.get(new GlowingBlockData.Key(pos, owner)); }
    public NPCGlowData getNPC(int id, UUID owner) { return npcs.get(new NPCGlowData.Key(id, owner)); }
    public GlowingBlockData visibleBlock(BlockPosition pos, UUID player, boolean globalEnabled) {
        GlowingBlockData personal = getBlock(pos, player);
        return personal != null ? personal : globalEnabled ? getBlock(pos, null) : null;
    }
    public NPCGlowData visibleNPC(int id, UUID player, boolean globalEnabled) {
        NPCGlowData personal = getNPC(id, player);
        return personal != null ? personal : globalEnabled ? getNPC(id, null) : null;
    }
    public Set<BlockPosition> positions(BlockPosition.ChunkKey chunk) { return Set.copyOf(chunks.getOrDefault(chunk, Set.of())); }
    public long countBlocks(UUID owner) { return blocks.values().stream().filter(b -> Objects.equals(owner, b.owner())).count(); }

    public void addBlock(GlowingBlockData data) {
        blocks.put(data.key(), data);
        chunks.computeIfAbsent(data.position().chunk(), k -> new HashSet<>()).add(data.position());
        changed();
    }
    public boolean removeBlock(BlockPosition pos, UUID owner) {
        if (blocks.remove(new GlowingBlockData.Key(pos, owner)) == null) return false;
        reindexPosition(pos);
        changed();
        return true;
    }
    public boolean removePosition(BlockPosition pos) {
        if (!blocks.keySet().removeIf(k -> k.position().equals(pos))) return false;
        reindexPosition(pos);
        changed();
        return true;
    }
    public void addNPC(NPCGlowData data) { npcs.put(data.key(), data); changed(); }
    public boolean removeNPC(int id, UUID owner) {
        if (npcs.remove(new NPCGlowData.Key(id, owner)) == null) return false;
        changed();
        return true;
    }
    public void removeNPC(int id) { if (npcs.keySet().removeIf(k -> k.npcId() == id)) changed(); }

    private void rebuildIndex() {
        chunks.clear();
        for (GlowingBlockData b : blocks.values()) chunks.computeIfAbsent(b.position().chunk(), k -> new HashSet<>()).add(b.position());
    }
    private void reindexPosition(BlockPosition pos) {
        if (blocks.keySet().stream().anyMatch(k -> k.position().equals(pos))) return;
        Set<BlockPosition> positions = chunks.get(pos.chunk());
        if (positions != null) {
            positions.remove(pos);
            if (positions.isEmpty()) chunks.remove(pos.chunk());
        }
    }
    private void changed() {
        revision++;
        if (closed || !plugin.isEnabled() || !plugin.getConfig().getBoolean("features.auto-save", true) || pendingSave != null) return;
        pendingSave = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            pendingSave = null;
            submitSave();
        }, 100L);
    }
    private void cancelPending() { if (pendingSave != null) { pendingSave.cancel(); pendingSave = null; } }

    private Future<Boolean> submitSave() {
        List<GlowingBlockData> blockSnapshot = List.copyOf(blocks.values());
        List<NPCGlowData> npcSnapshot = List.copyOf(npcs.values());
        long savingRevision = revision;
        return writer.submit(() -> {
            Path temp = null;
            try {
                Files.createDirectories(file.getParent());
                YamlConfiguration yaml = new YamlConfiguration();
                yaml.set("format-version", 2);
                List<Map<String, Object>> blockRows = new ArrayList<>();
                for (GlowingBlockData b : blockSnapshot) {
                    Map<String, Object> row = base(b.color(), b.animated(), b.owner());
                    row.put("world", b.position().world()); row.put("x", b.position().x());
                    row.put("y", b.position().y()); row.put("z", b.position().z());
                    row.put("type", b.blockType().name()); row.put("createdTime", b.createdTime());
                    blockRows.add(row);
                }
                List<Map<String, Object>> npcRows = new ArrayList<>();
                for (NPCGlowData n : npcSnapshot) {
                    Map<String, Object> row = base(n.color(), n.animated(), n.owner());
                    row.put("id", n.npcId()); row.put("name", n.npcName()); npcRows.add(row);
                }
                yaml.set("blocks", blockRows); yaml.set("npcs", npcRows);
                temp = Files.createTempFile(file.getParent(), "saves-", ".tmp");
                Files.writeString(temp, yaml.saveToString());
                try { Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                catch (AtomicMoveNotSupportedException e) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
                savedRevision = savingRevision;
                return true;
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Could not save glowing data; previous file retained", e);
                return false;
            } finally {
                if (temp != null) try { Files.deleteIfExists(temp); } catch (IOException ignored) { }
            }
        });
    }
    private static Map<String, Object> base(ChatColor color, boolean animated, UUID owner) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("color", color.name()); row.put("animated", animated); row.put("global", owner == null);
        if (owner != null) row.put("owner", owner.toString());
        return row;
    }
    public boolean save() {
        cancelPending();
        try { return submitSave().get(); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; }
        catch (ExecutionException e) { plugin.getLogger().log(Level.SEVERE, "Save failed", e); return false; }
    }
    private void awaitWriter() throws IOException {
        try { writer.submit(() -> {}).get(); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(e); }
        catch (ExecutionException e) { throw new IOException(e); }
    }
    public void close() {
        closed = true;
        cancelPending();
        if (plugin.getConfig().getBoolean("features.auto-save", true)) save();
        writer.shutdown();
        try { if (!writer.awaitTermination(10, TimeUnit.SECONDS)) plugin.getLogger().severe("Save is still finishing after shutdown"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
