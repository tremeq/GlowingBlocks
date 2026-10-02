package com.tremeq.glowingblocks.data;

import com.tremeq.glowingblocks.citizens.NPCGlowData;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class DataManagerTest {
    @TempDir Path folder;
    private DataManager data;
    private YamlConfiguration config;
    private final AtomicBoolean enabled = new AtomicBoolean(true);
    private final AtomicInteger scheduled = new AtomicInteger();
    private static final UUID ALICE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BOB = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final BlockPosition POS = new BlockPosition("unloaded.world,with,commas", -30, 64, 17);

    @BeforeEach void setup() throws IOException {
        config = new YamlConfiguration();
        BukkitTask task = proxy(BukkitTask.class, (method, args) -> null);
        BukkitScheduler scheduler = proxy(BukkitScheduler.class, (method, args) -> {
            if (method.equals("runTaskLater")) {
                if (!enabled.get()) throw new IllegalStateException("Scheduling while disabled");
                scheduled.incrementAndGet();
                return task;
            }
            return null;
        });
        Server server = proxy(Server.class, (method, args) -> method.equals("getScheduler") ? scheduler : null);
        Plugin plugin = proxy(Plugin.class, (method, args) -> switch (method) {
            case "getDataFolder" -> folder.toFile();
            case "getConfig" -> config;
            case "getLogger" -> Logger.getLogger("DataManagerTest");
            case "getServer" -> server;
            case "isEnabled" -> enabled.get();
            default -> null;
        });
        data = new DataManager(plugin);
    }
    @AfterEach void close() { if (data != null) data.close(); }
    private GlowingBlockData block(UUID owner, ChatColor color) {
        return new GlowingBlockData(POS, Material.STONE, color, false, owner, 1234);
    }
    @Test void preservesTwoPrivateOwnersAndGlobalAcrossReload() throws IOException {
        data.addBlock(block(ALICE, ChatColor.RED));
        data.addBlock(block(BOB, ChatColor.BLUE));
        data.addBlock(block(null, ChatColor.GOLD));
        assertTrue(data.save());
        data.reload();
        assertEquals(3, data.getAllBlocks().size());
        assertEquals(ChatColor.RED, data.visibleBlock(POS, ALICE, true).color());
        assertEquals(ChatColor.BLUE, data.visibleBlock(POS, BOB, true).color());
        assertEquals(ChatColor.GOLD, data.visibleBlock(POS, UUID.randomUUID(), true).color());
    }
    @Test void removingPrivateRestoresGlobalWithoutTouchingOtherOwner() {
        data.addBlock(block(ALICE, ChatColor.RED)); data.addBlock(block(BOB, ChatColor.BLUE)); data.addBlock(block(null, ChatColor.GOLD));
        assertTrue(data.removeBlock(POS, ALICE));
        assertEquals(ChatColor.GOLD, data.visibleBlock(POS, ALICE, true).color());
        assertEquals(ChatColor.BLUE, data.visibleBlock(POS, BOB, true).color());
        assertFalse(data.removeBlock(POS, ALICE));
    }
    @Test void reloadFlushesPendingChangesInsteadOfLosingThem() throws IOException {
        data.addBlock(block(ALICE, ChatColor.RED));
        assertEquals(1, scheduled.get());
        data.reload();
        assertNotNull(data.getBlock(POS, ALICE));
        assertTrue(Files.readString(folder.resolve("saves.yml")).contains(ALICE.toString()));
    }
    @Test void malformedYamlDoesNotReplaceDataOrFile() throws IOException {
        data.addBlock(block(ALICE, ChatColor.RED)); data.save();
        Path file = folder.resolve("saves.yml");
        Files.writeString(file, "blocks: [broken");
        assertThrows(IOException.class, data::reload);
        assertNotNull(data.getBlock(POS, ALICE));
        assertEquals("blocks: [broken", Files.readString(file));
    }
    @Test void invalidEntryRejectsEntireSnapshot() throws IOException {
        data.addBlock(block(ALICE, ChatColor.RED)); data.save();
        Path file = folder.resolve("saves.yml");
        Files.writeString(file, Files.readString(file).replace("color: RED", "color: BOLD"));
        assertThrows(IOException.class, data::reload);
        assertEquals(ChatColor.RED, data.getBlock(POS, ALICE).color());
    }
    @Test void worldNamesAndMissingWorldsNeedNoBukkitWorldLookup() throws IOException {
        data.addBlock(block(ALICE, ChatColor.WHITE)); data.save(); data.reload();
        assertEquals(POS, data.getAllBlocks().iterator().next().position());
        assertEquals(Set.of(POS), data.positions(POS.chunk()));
    }
    @Test void disablingWithNpcEntriesDoesNotDeleteOrScheduleAnything() throws IOException {
        data.addNPC(new NPCGlowData(7, "Test NPC", ChatColor.AQUA, true, null));
        data.addNPC(new NPCGlowData(7, "Test NPC", ChatColor.RED, false, ALICE));
        data.save();
        int before = scheduled.get();
        enabled.set(false);
        data.close(); data = null;
        assertEquals(before, scheduled.get());
        YamlConfiguration saved = YamlConfiguration.loadConfiguration(folder.resolve("saves.yml").toFile());
        assertEquals(2, saved.getMapList("npcs").size());
    }
    @Test void globalToggleHidesGlobalButKeepsPrivate() {
        data.addBlock(block(null, ChatColor.GOLD)); data.addBlock(block(ALICE, ChatColor.RED));
        assertNull(data.visibleBlock(POS, BOB, false));
        assertEquals(ChatColor.RED, data.visibleBlock(POS, ALICE, false).color());
    }
    @Test void npcPrivateOverridesGlobalAndSurvivesSave() throws IOException {
        data.addNPC(new NPCGlowData(9, "NPC", ChatColor.GOLD, true, null));
        data.addNPC(new NPCGlowData(9, "NPC", ChatColor.BLUE, false, ALICE));
        data.save(); data.reload();
        assertEquals(ChatColor.BLUE, data.visibleNPC(9, ALICE, true).color());
        assertEquals(ChatColor.GOLD, data.visibleNPC(9, BOB, true).color());
        assertTrue(data.removeNPC(9, ALICE));
        assertEquals(ChatColor.GOLD, data.visibleNPC(9, ALICE, true).color());
    }
    @Test void removesAllLayersWhenBlockIsDestroyed() {
        data.addBlock(block(null, ChatColor.GOLD)); data.addBlock(block(ALICE, ChatColor.RED));
        assertTrue(data.removePosition(POS));
        assertTrue(data.getAllBlocks().isEmpty()); assertTrue(data.positions(POS.chunk()).isEmpty());
    }
    @Test void autoSaveToggleStillAllowsManualSave() throws IOException {
        config.set("features.auto-save", false);
        data.addBlock(block(ALICE, ChatColor.WHITE));
        assertEquals(0, scheduled.get());
        assertTrue(data.save());
        data.reload(); assertNotNull(data.getBlock(POS, ALICE));
    }
    @Test void legacyDataMigratesWithBackupAndRecoversDottedWorld() throws IOException {
        YamlConfiguration legacy = new YamlConfiguration();
        String path = "blocks.world.test,1,64,2";
        legacy.set(path + ".type", "PLAYER_HEAD"); legacy.set(path + ".color", "RED");
        legacy.set(path + ".global", false); legacy.set(path + ".owner", ALICE.toString());
        legacy.save(folder.resolve("saves.yml").toFile());
        String original = Files.readString(folder.resolve("saves.yml"));
        data.reload();
        assertNotNull(data.getBlock(new BlockPosition("world.test", 1, 64, 2), ALICE));
        assertEquals(original, Files.readString(folder.resolve("saves.yml.v1.bak")));
        assertTrue(data.save());
        assertTrue(Files.readString(folder.resolve("saves.yml")).contains("format-version: 2"));
    }
    @Test void missingFormatVersionCannotSilentlyEraseListEntries() throws IOException {
        data.addBlock(block(ALICE, ChatColor.RED)); data.save();
        Path file = folder.resolve("saves.yml");
        Files.writeString(file, Files.readString(file).replace("format-version: 2", ""));
        assertThrows(IOException.class, data::reload);
        assertNotNull(data.getBlock(POS, ALICE));
    }
    @Test void malformedFormatVersionCannotFallBackToLegacy() throws IOException {
        data.addBlock(block(ALICE, ChatColor.RED)); data.save();
        Path file = folder.resolve("saves.yml");
        Files.writeString(file, Files.readString(file).replace("format-version: 2", "format-version: invalid"));
        assertThrows(IOException.class, data::reload);
        assertNotNull(data.getBlock(POS, ALICE));
    }
    @Test void manualSaveRecreatesDeletedFileEvenWithoutChanges() throws IOException {
        data.addBlock(block(ALICE, ChatColor.WHITE)); data.save();
        Files.delete(folder.resolve("saves.yml"));
        assertTrue(data.save()); assertTrue(Files.exists(folder.resolve("saves.yml")));
    }
    private interface Call { Object invoke(String method, Object[] args); }
    @SuppressWarnings("unchecked") private static <T> T proxy(Class<T> type, Call call) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (p, m, a) -> {
            if (m.getName().equals("hashCode")) return System.identityHashCode(p);
            if (m.getName().equals("equals")) return p == a[0];
            return call.invoke(m.getName(), a);
        });
    }
}
