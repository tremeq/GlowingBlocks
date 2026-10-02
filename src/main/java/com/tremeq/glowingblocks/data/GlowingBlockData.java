package com.tremeq.glowingblocks.data;

import org.bukkit.ChatColor;
import org.bukkit.Material;

import java.util.Objects;
import java.util.UUID;

/** owner == null denotes the global layer. Entries are immutable. */
public record GlowingBlockData(BlockPosition position, Material blockType, ChatColor color,
                               boolean animated, UUID owner, long createdTime) {
    public GlowingBlockData {
        Objects.requireNonNull(position);
        Objects.requireNonNull(blockType);
        Objects.requireNonNull(color);
        if (!blockType.isBlock() || blockType.isAir()) throw new IllegalArgumentException("Invalid block type");
        if (!color.isColor()) throw new IllegalArgumentException("Invalid color");
    }

    public boolean isGlobal() { return owner == null; }
    public Key key() { return new Key(position, owner); }
    public record Key(BlockPosition position, UUID owner) {}
}
