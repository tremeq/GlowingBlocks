package com.tremeq.glowingblocks.data;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.Objects;

/** A durable position: does not retain or require a loaded World. */
public record BlockPosition(String world, int x, int y, int z) {
    public BlockPosition {
        Objects.requireNonNull(world, "world");
        if (world.isBlank()) throw new IllegalArgumentException("Empty world name");
    }

    public static BlockPosition of(Block block) {
        return new BlockPosition(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }

    public static BlockPosition of(Location location) {
        return new BlockPosition(Objects.requireNonNull(location.getWorld()).getName(),
                location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public Location location() {
        World loaded = Bukkit.getWorld(world);
        return loaded == null ? null : new Location(loaded, x, y, z);
    }

    public ChunkKey chunk() { return new ChunkKey(world, x >> 4, z >> 4); }

    public record ChunkKey(String world, int x, int z) {}
}
