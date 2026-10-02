package com.tremeq.glowingblocks.libs.glowingentities;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.tremeq.glowingblocks.libs.glowingentities.reflection.MappedReflectionAccessor;
import com.tremeq.glowingblocks.libs.glowingentities.reflection.ReflectionAccessor;
import com.tremeq.glowingblocks.libs.glowingentities.reflection.TransparentReflectionAccessor;
import com.tremeq.glowingblocks.libs.glowingentities.reflection.Version;
import com.tremeq.glowingblocks.libs.glowingentities.reflection.mappings.Mappings;
import com.tremeq.glowingblocks.libs.glowingentities.reflection.mappings.files.MappingFileReader;
import com.tremeq.glowingblocks.libs.glowingentities.reflection.mappings.files.ProguardMapping;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public class GlowingEntities implements Listener {
   @NotNull
   protected final Plugin plugin;
   private Map<Player, GlowingEntities.PlayerData> glowing;
   boolean enabled = false;
   private int uid;

   public GlowingEntities(@NotNull Plugin plugin) {
      GlowingEntities.Packets.ensureInitialized();
      this.plugin = Objects.requireNonNull(plugin);
      this.enable();
   }

   public void enable() {
      if (this.enabled) {
         throw new IllegalStateException("The Glowing Entities API has already been enabled.");
      } else {
         this.plugin.getServer().getPluginManager().registerEvents(this, this.plugin);
         this.glowing = new java.util.concurrent.ConcurrentHashMap<>();
         this.uid = ThreadLocalRandom.current().nextInt(Integer.MAX_VALUE);
         this.enabled = true;
      }
   }

   public void disable() {
      if (!this.enabled) return;
      HandlerList.unregisterAll(this);
      for (Player player : new ArrayList<>(this.glowing.keySet())) clearPlayer(player, player.isOnline());
      this.glowing.clear();
      this.enabled = false;
   }
   private void ensureEnabled() {
      if (!this.enabled) {
         throw new IllegalStateException("The Glowing Entities API is not enabled.");
      }
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      clearPlayer(event.getPlayer(), false);
   }

   public void clearPlayer(Player player, boolean restore) {
      PlayerData data = this.glowing.get(player);
      if (data == null) return;
      if (restore) {
         for (int id : new ArrayList<>(data.glowingDatas.keySet())) {
            try { unsetGlowing(id, player); }
            catch (ReflectiveOperationException e) { plugin.getLogger().log(Level.WARNING, "Could not restore entity glow", e); }
         }
         if (data.sentColors != null) for (ChatColor color : data.sentColors) {
            try {
               Packets.TeamData team = Packets.teams.get(color);
               if (team != null) Packets.sendPackets(player, Packets.createTeamPacket.newInstance(team.id, 1, Optional.empty(), List.of()));
            } catch (ReflectiveOperationException e) { plugin.getLogger().log(Level.WARNING, "Could not remove glow team", e); }
         }
      }
      data.glowingDatas.clear();
      try { Packets.removePacketsHandler(data); }
      catch (ReflectiveOperationException e) { plugin.getLogger().log(Level.WARNING, "Could not remove packet handler", e); }
      this.glowing.remove(player);
   }

   public void setGlowing(Entity entity, Player receiver) throws ReflectiveOperationException {
      this.setGlowing(entity, receiver, null);
   }

   public void setGlowing(Entity entity, Player receiver, ChatColor color) throws ReflectiveOperationException {
      String teamID = entity instanceof Player ? entity.getName() : entity.getUniqueId().toString();
      this.setGlowing(entity.getEntityId(), teamID, receiver, color, GlowingEntities.Packets.getEntityFlags(entity));
   }

   public void setGlowing(int entityID, String teamID, Player receiver) throws ReflectiveOperationException {
      this.setGlowing(entityID, teamID, receiver, null, (byte)0);
   }

   public void setGlowing(int entityID, String teamID, Player receiver, ChatColor color) throws ReflectiveOperationException {
      this.setGlowing(entityID, teamID, receiver, color, (byte)0);
   }

   public void setGlowing(int entityID, String teamID, Player receiver, ChatColor color, byte otherFlags) throws ReflectiveOperationException {
      this.ensureEnabled();
      if (color != null && !color.isColor()) {
         throw new IllegalArgumentException("ChatColor must be a color format");
      } else {
         GlowingEntities.PlayerData playerData = this.glowing.computeIfAbsent(receiver, (p) -> {
            try {
               GlowingEntities.PlayerData data = new GlowingEntities.PlayerData(this, p);
               GlowingEntities.Packets.addPacketsHandler(data);
               return data;
            } catch (ReflectiveOperationException e) {
               throw new RuntimeException(e);
            }
         });

         GlowingEntities.GlowingData glowingData = playerData.glowingDatas.get(entityID);
         if (glowingData == null) {
            glowingData = new GlowingEntities.GlowingData(playerData, entityID, teamID, color, otherFlags);
            playerData.glowingDatas.put(entityID, glowingData);
            GlowingEntities.Packets.createGlowing(glowingData);
            if (color != null) {
               GlowingEntities.Packets.setGlowingColor(glowingData);
            }
         } else {
            if (Objects.equals(glowingData.color, color)) {
               return;
            }

            if (color == null) {
               GlowingEntities.Packets.removeGlowingColor(glowingData);
               glowingData.color = null;
            } else {
               glowingData.color = color;
               GlowingEntities.Packets.setGlowingColor(glowingData);
            }
         }

      }
   }

   public void unsetGlowing(Entity entity, Player receiver) throws ReflectiveOperationException {
      PlayerData data = this.glowing.get(receiver);
      if (data != null) {
         GlowingData glow = data.glowingDatas.get(entity.getEntityId());
         if (glow != null && entity.isValid()) glow.otherFlags = Packets.getEntityFlags(entity);
      }
      this.unsetGlowing(entity.getEntityId(), receiver);
   }

   public byte getEntityFlags(Entity entity) throws ReflectiveOperationException {
      return GlowingEntities.Packets.getEntityFlags(entity);
   }

   public void unsetGlowing(int entityID, Player receiver) throws ReflectiveOperationException {
      this.ensureEnabled();
      GlowingEntities.PlayerData playerData = this.glowing.get(receiver);
      if (playerData != null) {
         GlowingEntities.GlowingData glowingData = playerData.glowingDatas.remove(entityID);
          if (glowingData != null) {
             glowingData.enabled = false;
             GlowingEntities.Packets.removeGlowing(glowingData);
            if (glowingData.color != null) {
                GlowingEntities.Packets.removeGlowingColor(glowingData);
             }

             org.bukkit.scoreboard.Team original = receiver.getScoreboard().getEntryTeam(glowingData.teamID);
             if (original != null) {
                Packets.sendPackets(receiver, Packets.createTeamPacket.newInstance(original.getName(), 3, Optional.empty(), List.of(glowingData.teamID)));
             }

         }
      }
   }

   protected static class Packets {
      private static final byte GLOWING_FLAG = 64;
      private static Cache<Object, Object> packets;
      private static Object dummy;
      private static Logger logger;
      private static String cpack;
      private static Version version;
      private static boolean isEnabled;
      private static boolean hasInitialized;
      private static Throwable initializationError;
      private static Method getHandle;
      private static Method getDataWatcher;
      private static Object watcherObjectFlags;
      private static Object watcherDummy;
      private static Method watcherGet;
      private static Constructor<?> watcherItemConstructor;
      private static Method watcherItemObject;
      private static Method watcherItemDataGet;
      private static Method watcherBCreator;
      private static Method watcherBId;
      private static Method watcherBSerializer;
      private static Method watcherSerializerObject;
      private static Field playerConnection;
      private static Method sendPacket;
      private static Field networkManager;
      private static Field channelField;
      private static ReflectionAccessor.ClassAccessor packetBundle;
      private static Method packetBundlePackets;
      private static ReflectionAccessor.ClassAccessor packetMetadata;
      private static Constructor<?> packetMetadataConstructor;
      private static Field packetMetadataEntity;
      private static Field packetMetadataItems;
      private static EnumMap<ChatColor, GlowingEntities.Packets.TeamData> teams;
      private static Constructor<?> createTeamPacket;
      private static Constructor<?> createTeamPacketData;
      private static Field teamHandle;
      protected static void ensureInitialized() {
         if (!hasInitialized) {
            initialize();
         }

         if (!isEnabled) {
            throw new IllegalStateException("The Glowing Entities API is disabled. An error has occured during first initialization.", initializationError);
         }
      }

      private static void initialize() {
         hasInitialized = true;

         try {
            logger = new Logger("GlowingEntities", null) {
               public void log(LogRecord logRecord) {
                  logRecord.setMessage("[GlowingEntities] " + logRecord.getMessage());
                  super.log(logRecord);
               }
            };
            logger.setParent(Bukkit.getServer().getLogger());
            logger.setLevel(Level.ALL);
            String versionString = Bukkit.getBukkitVersion().split("-R")[0];
            Version serverVersion = Version.parse(versionString);
            logger.info("Found server version " + serverVersion);
            cpack = Bukkit.getServer().getClass().getPackage().getName();
            boolean remapped = cpack.split("\\.").length == 3;
            ReflectionAccessor reflection;
            if (remapped) {
               version = serverVersion;
               reflection = new TransparentReflectionAccessor();
               logger.info("Loaded transparent mappings.");
            } else {
               String mappingsFile = new String(Objects.requireNonNull(GlowingEntities.class.getResourceAsStream("/fr/skytasul/glowingentities/mappings/spigot.txt")).readAllBytes());
               MappingFileReader mappingsReader = new MappingFileReader(new ProguardMapping(false), mappingsFile.lines().toList());
               Optional<Version> foundVersion = mappingsReader.keepBestMatchedVersion(serverVersion);
               if (foundVersion.isEmpty()) {
                  throw new UnsupportedOperationException("Cannot find mappings to match server version");
               }

               if (!foundVersion.get().is(serverVersion)) {
                  logger.warning("Loaded not matching version of the mappings for your server version");
               }

               version = foundVersion.get();
               mappingsReader.parseMappings();
               Mappings mappings = mappingsReader.getParsedMappings(foundVersion.get());
               logger.info("Loaded mappings for " + version);
               reflection = new MappedReflectionAccessor(mappings);
            }

            loadReflection(reflection, version);
            isEnabled = true;
         } catch (Exception var8) {
            initializationError = var8;
            String errorMsg = "Glowing Entities reflection failed to initialize. The util is disabled. Please ensure your version (" + Bukkit.getBukkitVersion() + ") is supported.";
            if (logger == null) {
               var8.printStackTrace();
               System.err.println(errorMsg);
            } else {
               logger.log(Level.SEVERE, errorMsg, var8);
            }
         }

      }

      protected static void loadReflection(@NotNull ReflectionAccessor reflection, @NotNull Version version) throws ReflectiveOperationException {
         ReflectionAccessor.ClassAccessor entityClass = getNMSClass(reflection, "world.entity", "Entity");
         ReflectionAccessor.ClassAccessor entityTypesClass = getNMSClass(reflection, "world.entity", "EntityType");
         Object worldInstance = version.isAfter(1, 21, 3) && cpack != null ? getCraftClass("", "CraftWorld").getDeclaredMethod("getHandle").invoke(Bukkit.getWorlds().get(0)) : null;
         Object markerEntity = getNMSClass(reflection, "world.entity", "Marker").getConstructor(entityTypesClass, getNMSClass(reflection, "world.level", "Level")).newInstance(entityTypesClass.getField("MARKER").get(null), worldInstance);
         getHandle = cpack == null ? null : getCraftClass("entity", "CraftEntity").getDeclaredMethod("getHandle");
         getDataWatcher = entityClass.getMethodInstance("getEntityData");
         ReflectionAccessor.ClassAccessor dataWatcherClass = getNMSClass(reflection, "network.syncher", "SynchedEntityData");
         ReflectionAccessor.ClassAccessor entityDataAccessorClass;
         if (version.isAfter(1, 20, 5)) {
            entityDataAccessorClass = getNMSClass(reflection, "network.syncher", "SynchedEntityData$Builder");
            Object watcherBuilder = entityDataAccessorClass.getConstructor(getNMSClass(reflection, "network.syncher", "SyncedDataHolder")).newInstance(markerEntity);
            ReflectionAccessor.ClassAccessor.FieldAccessor watcherBuilderItems = entityDataAccessorClass.getField("itemsById");
            watcherBuilderItems.set(watcherBuilder, Array.newInstance(getNMSClass(reflection, "network.syncher", "SynchedEntityData$DataItem").getClassInstance(), 0));
            watcherDummy = entityDataAccessorClass.getMethod("build").invoke(watcherBuilder);
         } else {
            watcherDummy = dataWatcherClass.getConstructor(entityClass).newInstance(markerEntity);
         }

         entityDataAccessorClass = getNMSClass(reflection, "network.syncher", "EntityDataAccessor");
         watcherObjectFlags = entityClass.getField("DATA_SHARED_FLAGS_ID").get(null);
         watcherGet = dataWatcherClass.getMethodInstance("get", entityDataAccessorClass);
         ReflectionAccessor.ClassAccessor packetListenerClass;
         if (!version.isAfter(1, 19, 3)) {
            packetListenerClass = getNMSClass(reflection, "network.syncher", "SynchedEntityData$DataItem");
            watcherItemConstructor = packetListenerClass.getConstructorInstance(entityDataAccessorClass, Object.class);
            watcherItemObject = packetListenerClass.getMethodInstance("getAccessor");
            watcherItemDataGet = packetListenerClass.getMethodInstance("getValue");
         } else {
            packetListenerClass = getNMSClass(reflection, "network.syncher", "SynchedEntityData$DataValue");
            watcherBCreator = packetListenerClass.getMethodInstance("create", entityDataAccessorClass, Object.class);
            watcherBId = packetListenerClass.getMethodInstance("id");
            watcherBSerializer = packetListenerClass.getMethodInstance("serializer");
            watcherItemDataGet = packetListenerClass.getMethodInstance("value");
            watcherSerializerObject = getNMSClass(reflection, "network.syncher", "EntityDataSerializer").getMethodInstance("createAccessor", Integer.TYPE);
         }

         playerConnection = getNMSClass(reflection, "server.level", "ServerPlayer").getFieldInstance("connection");
         packetListenerClass = getNMSClass(reflection, "server.network", version.isAfter(1, 20, 2) ? "ServerCommonPacketListenerImpl" : "ServerGamePacketListenerImpl");
         sendPacket = packetListenerClass.getMethodInstance("send", getNMSClass(reflection, "network.protocol", "Packet"));
         networkManager = packetListenerClass.getFieldInstance("connection");
         channelField = getNMSClass(reflection, "network", "Connection").getFieldInstance("channel");
         if (version.isAfter(1, 19, 4)) {
            packetBundle = getNMSClass(reflection, "network.protocol", "BundlePacket");
            packetBundlePackets = packetBundle.getMethodInstance("subPackets");
         }

         packetMetadata = getNMSClass(reflection, "network.protocol.game", "ClientboundSetEntityDataPacket");
         packetMetadataEntity = packetMetadata.getFieldInstance("id");
         packetMetadataItems = packetMetadata.getFieldInstance("packedItems");
         if (version.isAfter(1, 19, 3)) {
            packetMetadataConstructor = packetMetadata.getConstructorInstance(Integer.TYPE, List.class);
         } else {
            packetMetadataConstructor = packetMetadata.getConstructorInstance(Integer.TYPE, dataWatcherClass, Boolean.TYPE);
         }

         ReflectionAccessor.ClassAccessor teamClass = getNMSClass(reflection, "world.scores", "PlayerTeam");
         createTeamPacket = getNMSClass(reflection, "network.protocol.game", "ClientboundSetPlayerTeamPacket").getConstructorInstance(String.class, Integer.TYPE, Optional.class, Collection.class);
         createTeamPacketData = getNMSClass(reflection, "network.protocol.game", "ClientboundSetPlayerTeamPacket$Parameters").getConstructorInstance(teamClass);
         // Configure team options through Bukkit so obfuscated enum/method mappings are unnecessary.
         Class<?> handleType = teamClass.getClassInstance();
         teamHandle = Arrays.stream(getCraftClass("scoreboard", "CraftTeam").getDeclaredFields())
                 .filter(field -> field.getType().equals(handleType)).findFirst()
                 .orElseThrow(() -> new NoSuchFieldException("CraftTeam PlayerTeam handle"));
         teamHandle.setAccessible(true);
      }

      public static void sendPackets(Player p, Object... packets) throws ReflectiveOperationException {
         Object connection = playerConnection.get(getHandle.invoke(p));
         for (Object packet : packets) {
            if (packet != null) {
               sendPacket.invoke(connection, packet);
            }
         }
      }

      public static byte getEntityFlags(Entity entity) throws ReflectiveOperationException {
         Object nmsEntity = getHandle.invoke(entity);
         Object dataWatcher = getDataWatcher.invoke(nmsEntity);
         return (Byte)watcherGet.invoke(dataWatcher, watcherObjectFlags);
      }

      public static void createGlowing(GlowingEntities.GlowingData glowingData) throws ReflectiveOperationException {
         byte flags = computeFlags(glowingData);
         setMetadata(glowingData.player.player, glowingData.entityID, flags, true);
      }

      private static byte computeFlags(GlowingEntities.GlowingData glowingData) {
         byte newFlags = glowingData.otherFlags;
         if (glowingData.enabled) {
            newFlags |= 64;
         }

         return newFlags;
      }

      public static Object createFlagWatcherItem(byte newFlags) throws ReflectiveOperationException {
         return watcherItemConstructor != null ? watcherItemConstructor.newInstance(watcherObjectFlags, newFlags) : watcherBCreator.invoke(null, watcherObjectFlags, newFlags);
      }

      public static void removeGlowing(GlowingEntities.GlowingData glowingData) throws ReflectiveOperationException {
         setMetadata(glowingData.player.player, glowingData.entityID, glowingData.otherFlags, true);

      }

      public static void updateGlowingState(GlowingEntities.GlowingData glowingData) throws ReflectiveOperationException {
         if (glowingData.enabled) {
            createGlowing(glowingData);
         } else {
            removeGlowing(glowingData);
         }

      }

      public static void setMetadata(Player player, int entityId, byte flags, boolean ignore) throws ReflectiveOperationException {
         List<Object> dataItems = new ArrayList<>(1);
         dataItems.add(watcherItemConstructor != null ? watcherItemConstructor.newInstance(watcherObjectFlags, flags) : watcherBCreator.invoke(null, watcherObjectFlags, flags));
         Object packetMetadata;
         if (version.isBefore(1, 19, 3)) {
            packetMetadata = packetMetadataConstructor.newInstance(entityId, watcherDummy, false);
            packetMetadataItems.set(packetMetadata, dataItems);
         } else {
            packetMetadata = packetMetadataConstructor.newInstance(entityId, dataItems);
         }

         if (ignore) {
            packets.put(packetMetadata, dummy);
         }

         sendPackets(player, packetMetadata);
      }

      public static void setGlowingColor(GlowingEntities.GlowingData glowingData) throws ReflectiveOperationException {
         boolean sendCreation = false;
         if (glowingData.player.sentColors == null) {
            glowingData.player.sentColors = EnumSet.of(glowingData.color);
            sendCreation = true;
         } else if (glowingData.player.sentColors.add(glowingData.color)) {
            sendCreation = true;
         }

         GlowingEntities.Packets.TeamData teamData = teams.computeIfAbsent(glowingData.color, (color) -> {
            try {
               return new GlowingEntities.Packets.TeamData(glowingData.player.instance.uid, color);
            } catch (ReflectiveOperationException e) {
               throw new RuntimeException(e);
            }
         });

         Object entityAddPacket = teamData.getEntityAddPacket(glowingData.teamID);
         if (sendCreation) {
            sendPackets(glowingData.player.player, teamData.creationPacket, entityAddPacket);
         } else {
            sendPackets(glowingData.player.player, entityAddPacket);
         }
      }

      public static void removeGlowingColor(GlowingEntities.GlowingData glowingData) throws ReflectiveOperationException {
         GlowingEntities.Packets.TeamData teamData = teams.get(glowingData.color);
         if (teamData != null) {
            sendPackets(glowingData.player.player, teamData.getEntityRemovePacket(glowingData.teamID));
         }
      }

      private static Channel getChannel(Player player) throws ReflectiveOperationException {
         return (Channel)channelField.get(networkManager.get(playerConnection.get(getHandle.invoke(player))));
      }

      public static void addPacketsHandler(PlayerData playerData) throws ReflectiveOperationException {
         playerData.packetsHandler = new ChannelDuplexHandler() {
            @Override
            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
               super.write(ctx, transform(msg), promise);
            }

            private Object transform(Object msg) throws ReflectiveOperationException {
               if (packetBundle != null && packetBundle.getClassInstance().isInstance(msg)) {
                  List<Object> copy = new ArrayList<>();
                  boolean changed = false;
                  for (Object child : (Iterable<?>) packetBundlePackets.invoke(msg)) {
                     Object updated = transform(child);
                     copy.add(updated);
                     changed |= updated != child;
                  }
                  return changed ? msg.getClass().getConstructor(Iterable.class).newInstance(copy) : msg;
               }
               if (!packetMetadata.getClassInstance().isInstance(msg) || packets.asMap().remove(msg) != null) return msg;
               int entityID = packetMetadataEntity.getInt(msg);
               GlowingData glow = playerData.glowingDatas.get(entityID);
               if (glow == null || !glow.enabled) return msg;
               List<?> items = (List<?>) packetMetadataItems.get(msg);
               if (items == null) return msg;
               List<Object> copy = new ArrayList<>(items);
               boolean found = false;
               for (int i = 0; i < items.size(); i++) {
                  Object item = items.get(i);
                  Object accessor = watcherItemObject != null ? watcherItemObject.invoke(item)
                     : watcherSerializerObject.invoke(watcherBSerializer.invoke(item), watcherBId.invoke(item));
                  if (accessor.equals(watcherObjectFlags)) {
                     glow.otherFlags = (Byte) watcherItemDataGet.invoke(item);
                     copy.set(i, createFlagWatcherItem(computeFlags(glow)));
                     found = true;
                     break;
                  }
               }
               if (!found) copy.add(createFlagWatcherItem(computeFlags(glow)));
               return packetMetadataConstructor.newInstance(entityID, copy);
            }
         };
         getChannel(playerData.player).pipeline().addBefore("packet_handler", null, playerData.packetsHandler);
      }

      public static void removePacketsHandler(PlayerData playerData) throws ReflectiveOperationException {
         if (playerData.packetsHandler == null) return;
         Channel channel = getChannel(playerData.player);
         channel.eventLoop().execute(() -> {
            if (channel.pipeline().context(playerData.packetsHandler) != null) channel.pipeline().remove(playerData.packetsHandler);
         });
      }

      private static Class<?> getCraftClass(String craftPackage, String className) throws ClassNotFoundException {
         return Class.forName(cpack + "." + (craftPackage.isBlank() ? className : craftPackage + "." + className));
      }

      @NotNull
      private static ReflectionAccessor.ClassAccessor getNMSClass(@NotNull ReflectionAccessor reflection, @NotNull String className) throws ClassNotFoundException {
         return reflection.getClass("net.minecraft." + className);
      }

      @NotNull
      private static ReflectionAccessor.ClassAccessor getNMSClass(@NotNull ReflectionAccessor reflection, @NotNull String nmPackage, @NotNull String className) throws ClassNotFoundException {
         return reflection.getClass("net.minecraft." + nmPackage + "." + className);
      }

      static {
         packets = CacheBuilder.newBuilder().expireAfterWrite(5L, TimeUnit.SECONDS).build();
         dummy = new Object();
         isEnabled = false;
         hasInitialized = false;
         initializationError = null;
         teams = new EnumMap<>(ChatColor.class);
      }

      private static class TeamData {
         private final String id;
         private final Object creationPacket;
         private final Cache<String, Object> addPackets;
         private final Cache<String, Object> removePackets;

         public TeamData(int uid, ChatColor color) throws ReflectiveOperationException {
            this.addPackets = CacheBuilder.newBuilder().expireAfterAccess(3L, TimeUnit.MINUTES).build();
            this.removePackets = CacheBuilder.newBuilder().expireAfterAccess(3L, TimeUnit.MINUTES).build();
            if (!color.isColor()) {
               throw new IllegalArgumentException();
            } else {
               this.id = "glow-" + uid + color.getChar();
               org.bukkit.scoreboard.Team team = Objects.requireNonNull(Bukkit.getScoreboardManager())
                       .getNewScoreboard().registerNewTeam(this.id);
               try {
                  team.setColor(color);
                  team.setOption(org.bukkit.scoreboard.Team.Option.COLLISION_RULE, org.bukkit.scoreboard.Team.OptionStatus.NEVER);
                  // Moving an NPC out of its Citizens team must not expose its name tag.
                  team.setOption(org.bukkit.scoreboard.Team.Option.NAME_TAG_VISIBILITY, org.bukkit.scoreboard.Team.OptionStatus.NEVER);
                  Object packetData = createTeamPacketData.newInstance(teamHandle.get(team));
                  this.creationPacket = createTeamPacket.newInstance(this.id, 0, Optional.of(packetData), Collections.emptyList());
               } finally {
                  team.unregister();
               }
            }
         }

         public Object getEntityAddPacket(String teamID) throws ReflectiveOperationException {
            try {
               return this.addPackets.get(teamID, () -> GlowingEntities.Packets.createTeamPacket.newInstance(this.id, 3, Optional.empty(), Arrays.asList(teamID)));
            } catch (ExecutionException e) {
               throw new ReflectiveOperationException(e);
            }
         }

         public Object getEntityRemovePacket(String teamID) throws ReflectiveOperationException {
            try {
               return this.removePackets.get(teamID, () -> GlowingEntities.Packets.createTeamPacket.newInstance(this.id, 4, Optional.empty(), Arrays.asList(teamID)));
            } catch (ExecutionException e) {
               throw new ReflectiveOperationException(e);
            }
         }
      }
   }

   private static class PlayerData {
      final GlowingEntities instance;
      final Player player;
      final Map<Integer, GlowingEntities.GlowingData> glowingDatas;
      ChannelHandler packetsHandler;
      EnumSet<ChatColor> sentColors;

      PlayerData(GlowingEntities instance, Player player) {
         this.instance = instance;
         this.player = player;
         this.glowingDatas = new java.util.concurrent.ConcurrentHashMap<>();
      }
   }

   private static class GlowingData {
      final GlowingEntities.PlayerData player;
      final int entityID;
      final String teamID;
      volatile ChatColor color;
      volatile byte otherFlags;
      volatile boolean enabled;

      GlowingData(GlowingEntities.PlayerData player, int entityID, String teamID, ChatColor color, byte otherFlags) {
         this.player = player;
         this.entityID = entityID;
         this.teamID = teamID;
         this.color = color;
         this.otherFlags = otherFlags;
         this.enabled = true;
      }
   }
}
