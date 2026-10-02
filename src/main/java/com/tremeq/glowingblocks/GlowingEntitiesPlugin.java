package com.tremeq.glowingblocks;

import com.tremeq.glowingblocks.animation.RainbowAnimator;
import com.tremeq.glowingblocks.citizens.CitizensIntegration;
import com.tremeq.glowingblocks.data.DataManager;
import com.tremeq.glowingblocks.libs.glowingentities.GlowingBlocks;
import com.tremeq.glowingblocks.listeners.PlayerJoinListener;
import org.bukkit.command.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;

public final class GlowingEntitiesPlugin extends JavaPlugin {
    private FileConfiguration activeConfig;
    private PluginSettings settings;
    private Messages messages;
    private DataManager data;
    private GlowingBlocks renderer;
    private BlockManager blocks;
    private CitizensIntegration citizens;
    private RainbowAnimator rainbow;
    private CommandHandler commands;
    private BukkitTask maintenance;

    @Override public void onEnable() {
        try {
            try { Class.forName("org.bukkit.entity.BlockDisplay"); }
            catch (ClassNotFoundException e) { throw new IllegalStateException("Paper 1.19.4 or newer is required for hitbox-free outlines", e); }
            saveDefaultConfig();
            activeConfig = readConfig();
            settings = PluginSettings.read(activeConfig);
            messages = new Messages(this);
            data = new DataManager(this);
            renderer = new GlowingBlocks(this);
            blocks = new BlockManager(this, renderer);
            citizens = new CitizensIntegration(this);
            rainbow = new RainbowAnimator(this);
            commands = new CommandHandler(this);
            CommandTabCompleter completer = new CommandTabCompleter(this);
            for (String name : List.of("glowblock", "unglowblock", "glownpc", "unglownpc", "glowingentities")) {
                PluginCommand command = Objects.requireNonNull(getCommand(name), "Missing command " + name);
                command.setExecutor(this);
                command.setTabCompleter(completer);
            }
            getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);
            citizens.initialize();
            rainbow.start();
            blocks.refreshAll();
            citizens.refreshAll();
            maintenance = getServer().getScheduler().runTaskTimer(this, () -> {
                blocks.maintain();
                citizens.refreshAll();
            }, 20L, 20L);
            getLogger().info("Enabled with " + data.getAllBlocks().size() + " block effects and " + data.getAllNPCs().size() + " NPC effects.");
        } catch (Exception | LinkageError e) {
            getLogger().log(Level.SEVERE, "Could not initialize GlowingBlocks", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private FileConfiguration readConfig() throws Exception {
        YamlConfiguration candidate = new YamlConfiguration();
        candidate.load(new java.io.File(getDataFolder(), "config.yml"));
        try (var input = Objects.requireNonNull(getResource("config.yml"));
             var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            YamlConfiguration defaults = new YamlConfiguration();
            defaults.load(reader);
            candidate.setDefaults(defaults);
        }
        return candidate;
    }
    public void reloadPlugin() throws Exception {
        FileConfiguration candidate = readConfig();
        PluginSettings next = PluginSettings.read(candidate);
        data.reload(); // Throws before replacing active config/effects if the saves file is invalid.
        activeConfig = candidate;
        settings = next;
        rainbow.start();
        citizens.clearAll();
        citizens.reconfigure();
        blocks.refreshAll();
    }
    @Override public FileConfiguration getConfig() { return activeConfig == null ? super.getConfig() : activeConfig; }
    @Override public void onDisable() {
        if (maintenance != null) maintenance.cancel();
        if (rainbow != null) rainbow.stop();
        cleanup("NPC effects", () -> { if (citizens != null) citizens.shutdown(); });
        cleanup("block effects", () -> { if (blocks != null) blocks.close(); else if (renderer != null) renderer.disable(); });
        cleanup("saved data", () -> { if (data != null) data.close(); });
    }
    private void cleanup(String name, Runnable action) {
        try { action.run(); }
        catch (Exception | LinkageError e) { getLogger().log(Level.SEVERE, "Could not clean up " + name, e); }
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return commands != null && commands.handleCommand(sender, command, label, args);
    }
    public boolean isDebugEnabled() { return getConfig().getBoolean("debug.enabled"); }
    public PluginSettings settings() { return settings; }
    public Messages messages() { return messages; }
    public DataManager getDataManager() { return data; }
    public GlowingBlocks getGlowingBlocks() { return renderer; }
    public BlockManager getBlockManager() { return blocks; }
    public CitizensIntegration getCitizensIntegration() { return citizens; }
    public RainbowAnimator getRainbowAnimator() { return rainbow; }
    public CommandHandler getCommandHandler() { return commands; }
}
