package com.tremeq.glowingblocks;

import org.bukkit.ChatColor;
import org.bukkit.command.*;

import java.util.*;

public final class CommandTabCompleter implements TabCompleter {
    private final GlowingEntitiesPlugin plugin;
    public CommandTabCompleter(GlowingEntitiesPlugin plugin) { this.plugin = plugin; }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (!sender.hasPermission("glowingblocks." + (name.equals("glowingentities") ? "admin" : name))) return List.of();
        List<String> choices = new ArrayList<>();
        if (name.equals("glowingentities") && args.length == 1) choices.addAll(List.of("help", "version", "reload", "save", "list"));
        if ((name.equals("glownpc") || name.equals("unglownpc")) && args.length == 1) choices.addAll(plugin.getCitizensIntegration().suggestions());
        int colorIndex = name.equals("glownpc") ? 2 : name.equals("glowblock") ? 1 : -1;
        if (args.length == colorIndex) {
            for (ChatColor color : ChatColor.values()) if (color.isColor()) choices.add(color.name());
            if (plugin.settings().rainbow()) choices.add("RAINBOW");
        }
        boolean visibility = (colorIndex > 0 && (args.length == colorIndex || args.length == colorIndex + 1))
                || (name.equals("unglowblock") && args.length == 1) || (name.equals("unglownpc") && args.length == 2);
        if (visibility) {
            choices.add("private");
            if ((plugin.settings().globalMode() || name.startsWith("unglow")) && (sender.hasPermission("glowingblocks.global") || sender.hasPermission("glowingblocks.admin"))) choices.add("global");
        }
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return choices.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(prefix)).distinct().sorted().toList();
    }
}
