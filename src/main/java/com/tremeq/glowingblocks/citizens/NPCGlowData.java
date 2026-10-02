package com.tremeq.glowingblocks.citizens;

import org.bukkit.ChatColor;

import java.util.Objects;
import java.util.UUID;

public record NPCGlowData(int npcId, String npcName, ChatColor color, boolean animated, UUID owner) {
    public NPCGlowData {
        if (npcId < 0) throw new IllegalArgumentException("Invalid NPC ID");
        Objects.requireNonNull(npcName);
        if (color == null || !color.isColor()) throw new IllegalArgumentException("Invalid color");
    }
    public boolean isGlobal() { return owner == null; }
    public Key key() { return new Key(npcId, owner); }
    public record Key(int npcId, UUID owner) {}
}
