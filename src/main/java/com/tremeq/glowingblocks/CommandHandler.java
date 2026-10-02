package com.tremeq.glowingblocks;

import com.tremeq.glowingblocks.citizens.NPCGlowData;
import com.tremeq.glowingblocks.data.BlockPosition;
import com.tremeq.glowingblocks.data.GlowingBlockData;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.block.Skull;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.logging.Level;

public final class CommandHandler {
    private final GlowingEntitiesPlugin plugin;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    public CommandHandler(GlowingEntitiesPlugin plugin) { this.plugin = plugin; }
    public void forget(UUID id) { cooldowns.remove(id); }

    public boolean handleCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        String used = label.substring(label.indexOf(':') + 1);
        if (!plugin.settings().aliases() && !used.equalsIgnoreCase(name)) {
            plugin.messages().send(sender, "aliases-disabled", "command", name); return true;
        }
        String permission = "glowingblocks." + (name.equals("glowingentities") ? "admin" : name);
        if (!sender.hasPermission(permission)) { plugin.messages().send(sender, "no-permission"); return true; }
        try {
            switch (name) {
                case "glowblock" -> glowBlock(sender, args);
                case "unglowblock" -> unglowBlock(sender, args);
                case "glownpc" -> glowNPC(sender, args);
                case "unglownpc" -> unglowNPC(sender, args);
                case "glowingentities" -> main(sender, args);
                default -> { return false; }
            }
        } catch (CommandError e) {
            plugin.messages().send(sender, e.key, e.variables);
        } catch (Exception e) {
            plugin.messages().send(sender, "operation-failed");
            plugin.getLogger().log(Level.SEVERE, "Command failed: " + name, e);
        }
        return true;
    }
    private Player player(CommandSender sender) {
        if (!(sender instanceof Player player)) throw new CommandError("player-only");
        return player;
    }
    private Block target(Player player) {
        Block block = player.getTargetBlockExact(plugin.settings().range());
        if (block == null || block.getType().isAir()) throw new CommandError("must-look-at-block", "range", plugin.settings().range());
        return block;
    }
    private void cooldown(CommandSender sender) {
        if (!(sender instanceof Player p)) return;
        long now = System.nanoTime();
        Long previous = cooldowns.get(p.getUniqueId());
        if (previous != null && now - previous < 500_000_000L) throw new CommandError("cooldown");
        cooldowns.put(p.getUniqueId(), now);
    }
    private UUID owner(CommandSender sender, boolean global, boolean creating) {
        if (global) {
            if (creating && !plugin.settings().globalMode()) throw new CommandError("feature-disabled");
            if (!sender.hasPermission("glowingblocks.global") && !sender.hasPermission("glowingblocks.admin")) throw new CommandError("global-permission");
            return null;
        }
        if (!(sender instanceof Player p)) throw new CommandError("headless-console");
        return p.getUniqueId();
    }
    private Options options(String[] args, int offset) {
        boolean global = plugin.settings().defaultGlobal(), animated = false;
        ChatColor color = ChatColor.RED;
        int index = offset;
        if (index < args.length && !visibility(args[index])) {
            if (args[index].equalsIgnoreCase("rainbow")) {
                if (!plugin.settings().rainbow()) throw new CommandError("feature-disabled");
                animated = true;
                color = plugin.getRainbowAnimator().getCurrentColor();
            } else {
                try { color = ChatColor.valueOf(args[index].toUpperCase(Locale.ROOT)); }
                catch (IllegalArgumentException e) { throw new CommandError("invalid-color"); }
                if (!color.isColor()) throw new CommandError("invalid-color");
            }
            index++;
        }
        if (index < args.length) {
            if (!visibility(args[index])) throw new CommandError("usage", "usage", "[color|rainbow] [private|global]");
            global = args[index++].equalsIgnoreCase("global");
        }
        if (index != args.length) throw new CommandError("usage", "usage", "[color|rainbow] [private|global]");
        return new Options(color, animated, global);
    }
    private static boolean visibility(String value) { return value.equalsIgnoreCase("global") || value.equalsIgnoreCase("private"); }
    private boolean removalGlobal(String[] args, int offset, boolean hasPrivate) {
        if (args.length == offset) return !hasPrivate;
        if (args.length != offset + 1 || !visibility(args[offset])) throw new CommandError("usage", "usage", "[private|global]");
        return args[offset].equalsIgnoreCase("global");
    }
    private void glowBlock(CommandSender sender, String[] args) {
        Player player = player(sender);
        Block block = target(player);
        Options options = options(args, 0);
        UUID owner = owner(sender, options.global, true);
        BlockPosition pos = BlockPosition.of(block);
        int limit = options.global ? plugin.settings().globalLimit() : plugin.settings().playerLimit();
        if (plugin.getDataManager().getBlock(pos, owner) == null && plugin.getDataManager().countBlocks(owner) >= limit)
            throw new CommandError(options.global ? "max-global-blocks-reached" : "max-player-blocks-reached");
        cooldown(sender);
        plugin.getDataManager().addBlock(new GlowingBlockData(pos, block.getType(), options.color, options.animated, owner, System.currentTimeMillis()));
        plugin.getBlockManager().seed(player);
        plugin.getBlockManager().refresh(pos);
        success(sender, "block", options, "");
        plugin.messages().send(sender, "block-location", "x", pos.x(), "y", pos.y(), "z", pos.z());
        if (block.getState() instanceof Skull skull) {
            String name = skull.getOwningPlayer() == null ? null : skull.getOwningPlayer().getName();
            if (name == null && skull.getPlayerProfile() != null) name = skull.getPlayerProfile().getName();
            if (name != null) plugin.messages().send(sender, "head-owner", "owner", name);
        }
    }
    private void unglowBlock(CommandSender sender, String[] args) {
        Player player = player(sender);
        BlockPosition pos = BlockPosition.of(target(player));
        boolean global = removalGlobal(args, 0, plugin.getDataManager().getBlock(pos, player.getUniqueId()) != null);
        UUID owner = owner(sender, global, false);
        if (plugin.getDataManager().getBlock(pos, owner) == null) throw new CommandError("block-not-glowing");
        cooldown(sender);
        plugin.getDataManager().removeBlock(pos, owner);
        plugin.getBlockManager().refresh(pos);
        plugin.messages().send(sender, "block-unglowed");
    }
    private NPC npc(CommandSender sender, String[] args, String usage) {
        if (!plugin.getCitizensIntegration().isEnabled()) throw new CommandError("citizens-not-found");
        if (args.length == 0) throw new CommandError("usage", "usage", usage);
        NPC npc = plugin.getCitizensIntegration().getNPC(args[0]);
        if (npc == null) throw new CommandError("npc-not-found", "npc", args[0]);
        return npc;
    }
    private void glowNPC(CommandSender sender, String[] args) {
        NPC npc = npc(sender, args, "/glownpc <id|name> [color|rainbow] [private|global]");
        Options options = options(args, 1);
        UUID owner = owner(sender, options.global, true);
        cooldown(sender);
        plugin.getDataManager().addNPC(new NPCGlowData(npc.getId(), npc.getName(), options.color, options.animated, owner));
        plugin.getCitizensIntegration().refreshAll();
        success(sender, "npc", options, npc.getName());
    }
    private void unglowNPC(CommandSender sender, String[] args) {
        if (!plugin.getCitizensIntegration().isEnabled()) throw new CommandError("citizens-not-found");
        if (args.length == 0) throw new CommandError("usage", "usage", "/unglownpc <id|name> [private|global]");
        NPC npc = plugin.getCitizensIntegration().getNPC(args[0]);
        int id;
        try { id = npc != null ? npc.getId() : Integer.parseInt(args[0]); }
        catch (NumberFormatException e) { throw new CommandError("npc-not-found", "npc", args[0]); }
        UUID personal = sender instanceof Player p ? p.getUniqueId() : null;
        boolean global = removalGlobal(args, 1, personal != null && plugin.getDataManager().getNPC(id, personal) != null);
        UUID owner = owner(sender, global, false);
        if (plugin.getDataManager().getNPC(id, owner) == null) throw new CommandError("npc-not-glowing");
        cooldown(sender);
        plugin.getDataManager().removeNPC(id, owner);
        plugin.getCitizensIntegration().refreshAll();
        plugin.messages().send(sender, "npc-unglowed", "npc", npc != null ? npc.getName() : id);
    }
    private void success(CommandSender sender, String kind, Options options, String name) {
        plugin.messages().send(sender, kind + (options.animated ? "-glowing-animated" : "-glowing"),
                "color", options.animated ? "RAINBOW" : options.color.name(), "npc", name,
                "visibility", plugin.messages().text(options.global ? "visibility-global" : "visibility-private"));
    }
    private void main(CommandSender sender, String[] args) {
        if (args.length > 1) throw new CommandError("usage", "usage", "/glowingentities [help|version|list|save|reload]");
        switch (args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT)) {
            case "help" -> {
                plugin.messages().send(sender, "help-header");
                for (String key : List.of("glowblock", "unglowblock", "glownpc", "unglownpc", "list", "save", "reload", "version")) plugin.messages().send(sender, "help-" + key);
            }
            case "version" -> {
                plugin.messages().send(sender, "version-info", "version", plugin.getDescription().getVersion());
                plugin.messages().send(sender, "version-api", "api-version", "bundled (SkytAsul, patched)");
            }
            case "list" -> {
                plugin.messages().send(sender, "list-header");
                plugin.messages().send(sender, "list-total", "total", plugin.getDataManager().getAllBlocks().size());
                plugin.messages().send(sender, "list-animated", "animated", plugin.getDataManager().getAllBlocks().stream().filter(GlowingBlockData::animated).count());
                plugin.messages().send(sender, "list-npcs", "total", plugin.getDataManager().getAllNPCs().size());
            }
            case "save" -> plugin.messages().send(sender, plugin.getDataManager().save() ? "data-saved" : "save-failed");
            case "reload" -> {
                try { plugin.reloadPlugin(); plugin.messages().send(sender, "data-reloaded"); }
                catch (Exception e) { plugin.messages().send(sender, "reload-failed", "error", e.getMessage()); plugin.getLogger().log(Level.WARNING, "Reload rejected", e); }
            }
            default -> throw new CommandError("usage", "usage", "/glowingentities [help|version|list|save|reload]");
        }
    }
    private record Options(ChatColor color, boolean animated, boolean global) {}
    private static final class CommandError extends RuntimeException {
        final String key;
        final Object[] variables;
        CommandError(String key, Object... variables) { this.key = key; this.variables = variables; }
    }
}
