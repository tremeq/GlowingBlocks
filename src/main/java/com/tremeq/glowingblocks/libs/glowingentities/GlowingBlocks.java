package com.tremeq.glowingblocks.libs.glowingentities;

import com.tremeq.glowingblocks.GlowingEntitiesPlugin;
import com.tremeq.glowingblocks.data.BlockPosition;
import com.tremeq.glowingblocks.data.GlowingBlockData;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Skull;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Rotatable;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;

/** Hitbox-free displays. One entity per visible data layer, shared by its viewers. */
public final class GlowingBlocks {
    private final GlowingEntitiesPlugin plugin;
    private final NamespacedKey marker;
    private final Map<GlowingBlockData.Key, Visual> visuals = new HashMap<>();
    private final Map<UUID, Map<BlockPosition, GlowingBlockData.Key>> viewers = new HashMap<>();

    public GlowingBlocks(GlowingEntitiesPlugin plugin) {
        this.plugin = plugin;
        this.marker = new NamespacedKey(plugin, "outline");
        for (World world : Bukkit.getWorlds()) for (Entity entity : world.getEntities()) cleanupOrphan(entity);
    }

    public void show(Block block, Player player, GlowingBlockData data, ChatColor color) {
        if (!player.isOnline() || !block.getWorld().equals(player.getWorld()) || !block.getChunk().isLoaded()) return;
        Map<BlockPosition, GlowingBlockData.Key> active = viewers.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
        GlowingBlockData.Key previous = active.get(data.position());
        if (previous != null && !previous.equals(data.key())) hide(player, data.position());
        Visual visual = visuals.get(data.key());
        if (visual != null && !visual.entity.isValid()) {
            removeVisual(data.key());
            visual = null;
        }
        if (visual == null) {
            visual = new Visual(create(block, color));
            visuals.put(data.key(), visual);
        }
        visual.entity.setGlowColorOverride(rgb(color));
        if (visual.viewers.add(player.getUniqueId())) player.showEntity(plugin, visual.entity);
        viewers.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>()).put(data.position(), data.key());
        if (plugin.isDebugEnabled() && plugin.getConfig().getBoolean("debug.log-api-calls"))
            plugin.getLogger().info("Glow " + data.position() + " -> " + player.getName() + " " + color.name());
    }

    private Display create(Block block, ChatColor color) {
        boolean head = block.getState() instanceof Skull;
        if (head) {
            Skull skull = (Skull) block.getState();
            String itemName = block.getType().name().replace("WALL_", "");
            ItemStack stack = new ItemStack(Objects.requireNonNull(Material.matchMaterial(itemName)));
            if (stack.getItemMeta() instanceof SkullMeta meta && skull.getPlayerProfile() != null) {
                meta.setPlayerProfile(skull.getPlayerProfile());
                stack.setItemMeta(meta);
            }
            // Use the actual block's bounding box: wall heads have a different center and height.
            org.bukkit.util.BoundingBox bounds = block.getBoundingBox();
            Location center = new Location(block.getWorld(), bounds.getCenterX(), bounds.getCenterY(), bounds.getCenterZ());
            float yaw = 0;
            if (block.getBlockData() instanceof Rotatable r) yaw = faceYaw(r.getRotation());
            if (block.getBlockData() instanceof Directional d) yaw = faceYaw(d.getFacing());
            final float rotation = yaw;
            return block.getWorld().spawn(center, ItemDisplay.class, entity -> {
                prepare(entity, color);
                entity.setItemStack(stack);
                entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                entity.setTransformation(new Transformation(new Vector3f(),
                        new Quaternionf().rotateY((float) Math.toRadians(-rotation)),
                        new Vector3f(0.998f), new Quaternionf()));
            });
        }
        // Block entities such as chests have no baked block model. Their item renderer does.
        if (block.getState() instanceof org.bukkit.block.TileState && block.getType().isItem()) {
            return block.getWorld().spawn(block.getLocation().add(0.5, 0.5, 0.5), ItemDisplay.class, entity -> {
                prepare(entity, color);
                entity.setItemStack(new ItemStack(block.getType()));
                entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                entity.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.998f), new Quaternionf()));
            });
        }
        return block.getWorld().spawn(block.getLocation(), BlockDisplay.class, entity -> {
            prepare(entity, color);
            entity.setBlock(block.getBlockData());
            entity.setTransformation(new Transformation(new Vector3f(0.001f), new Quaternionf(),
                    new Vector3f(0.998f), new Quaternionf()));
        });
    }

    private static float faceYaw(org.bukkit.block.BlockFace face) {
        return (float) Math.toDegrees(Math.atan2(-face.getModX(), face.getModZ()));
    }
    private void prepare(Display entity, ChatColor color) {
        entity.setVisibleByDefault(false);
        entity.setPersistent(false);
        entity.setInvulnerable(true);
        entity.setGravity(false);
        entity.setSilent(true);
        entity.setGlowing(true);
        entity.setGlowColorOverride(rgb(color));
        entity.setShadowRadius(0);
        entity.setViewRange(16);
        entity.getPersistentDataContainer().set(marker, PersistentDataType.BYTE, (byte) 1);
    }
    public void hide(Player player, BlockPosition position) {
        Map<BlockPosition, GlowingBlockData.Key> active = viewers.get(player.getUniqueId());
        if (active == null) return;
        GlowingBlockData.Key key = active.remove(position);
        if (key != null) {
            Visual visual = visuals.get(key);
            if (visual != null) {
                if (player.isOnline()) player.hideEntity(plugin, visual.entity);
                visual.viewers.remove(player.getUniqueId());
                if (visual.viewers.isEmpty()) { visual.entity.remove(); visuals.remove(key); }
            }
        }
        if (active.isEmpty()) viewers.remove(player.getUniqueId());
    }
    public Set<BlockPosition> activePositions(Player player) {
        return Set.copyOf(viewers.getOrDefault(player.getUniqueId(), Map.of()).keySet());
    }
    public void clearPlayer(Player player) { for (BlockPosition pos : activePositions(player)) hide(player, pos); }
    public void invalidate(BlockPosition pos) {
        for (GlowingBlockData.Key key : new ArrayList<>(visuals.keySet())) if (key.position().equals(pos)) removeVisual(key);
    }
    private void removeVisual(GlowingBlockData.Key key) {
        Visual visual = visuals.remove(key);
        if (visual == null) return;
        for (UUID id : visual.viewers) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) player.hideEntity(plugin, visual.entity);
            Map<BlockPosition, GlowingBlockData.Key> active = viewers.get(id);
            if (active != null) { active.remove(key.position()); if (active.isEmpty()) viewers.remove(id); }
        }
        visual.entity.remove();
    }
    public void unloadChunk(BlockPosition.ChunkKey chunk) {
        for (GlowingBlockData.Key key : new ArrayList<>(visuals.keySet())) if (key.position().chunk().equals(chunk)) removeVisual(key);
    }
    public void cleanupOrphan(Entity entity) {
        if (entity.getPersistentDataContainer().has(marker, PersistentDataType.BYTE)
                && visuals.values().stream().noneMatch(v -> v.entity.getUniqueId().equals(entity.getUniqueId()))) entity.remove();
    }
    public void disable() {
        for (GlowingBlockData.Key key : new ArrayList<>(visuals.keySet())) removeVisual(key);
        viewers.clear();
    }
    public static Color rgb(ChatColor color) {
        return Color.fromRGB(switch (color) {
            case BLACK -> 0x000000; case DARK_BLUE -> 0x0000AA; case DARK_GREEN -> 0x00AA00;
            case DARK_AQUA -> 0x00AAAA; case DARK_RED -> 0xAA0000; case DARK_PURPLE -> 0xAA00AA;
            case GOLD -> 0xFFAA00; case GRAY -> 0xAAAAAA; case DARK_GRAY -> 0x555555;
            case BLUE -> 0x5555FF; case GREEN -> 0x55FF55; case AQUA -> 0x55FFFF;
            case RED -> 0xFF5555; case LIGHT_PURPLE -> 0xFF55FF; case YELLOW -> 0xFFFF55;
            case WHITE -> 0xFFFFFF; default -> throw new IllegalArgumentException("Not a color");
        });
    }
    private static final class Visual {
        final Display entity;
        final Set<UUID> viewers = new HashSet<>();
        Visual(Display entity) { this.entity = entity; }
    }
}
