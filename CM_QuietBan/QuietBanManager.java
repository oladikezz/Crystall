package net.schalker.SMPS.modules.quietban;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.schalker.DoAPI.DoAPI;
import net.schalker.SMPS.modules.quietban.transport.ChannelResolver;
import net.schalker.SMPS.modules.quietban.transport.LagProfile;
import net.schalker.SMPS.modules.quietban.transport.ShadowLagHandler;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

public class QuietBanManager {

   private static final String PIPELINE_ANCHOR = "packet_handler";

   public record Target(UUID uuid, String name, Player online) {
   }

   private final DoAPI plugin;
   private final QuietBanModule module;
   private final QuietBanDatabase database;

   private final Map<String, QuietBanEntry> active = new ConcurrentHashMap<>();
   private final Map<UUID, Channel> attached = new ConcurrentHashMap<>();

   public QuietBanManager(DoAPI plugin, QuietBanModule module, QuietBanDatabase database) {
      this.plugin = plugin;
      this.module = module;
      this.database = database;
   }

   public void loadFromDatabase() {
      List<QuietBanEntry> entries = this.database.loadActive();
      long now = System.currentTimeMillis();

      this.active.clear();
      for (QuietBanEntry entry : entries) {
         if (entry.isExpired(now)) {
            this.database.deactivate(entry.id(), "SYSTEM", "expired", now);
            continue;
         }
         this.active.put(entry.id(), entry);
      }
   }

   public int getActiveCount() {
      return this.active.size();
   }

   public int getAttachedCount() {
      return this.attached.size();
   }

   public QuietBanEntry findByPlayer(UUID uuid, String name) {
      String lower = name == null ? null : name.toLowerCase(Locale.ROOT);
      for (QuietBanEntry entry : this.active.values()) {
         if (uuid != null && uuid.equals(entry.uuid())) {
            return entry;
         }
         if (lower != null && entry.uuid() == null && lower.equals(entry.playerNameLower())) {
            return entry;
         }
      }
      return null;
   }

   public Collection<QuietBanEntry> snapshot() {
      return List.copyOf(this.active.values());
   }

   public QuietBanEntry findById(String id) {
      return id == null ? null : this.active.get(id);
   }

   public QuietBanEntry findByName(String name) {
      if (name == null) {
         return null;
      }
      String lower = name.toLowerCase(Locale.ROOT);
      for (QuietBanEntry entry : this.active.values()) {
         if (lower.equals(entry.playerNameLower())) {
            return entry;
         }
      }
      return null;
   }

   public QuietBanEntry findIpLock(String ip) {
      if (ip == null || ip.isEmpty()) {
         return null;
      }
      for (QuietBanEntry entry : this.active.values()) {
         if (entry.ipLock() && ip.equals(entry.ip())) {
            return entry;
         }
      }
      return null;
   }

   public Target resolveTarget(String name) {
      Player exact = this.plugin.getServer().getPlayerExact(name);
      if (exact != null && exact.isOnline()) {
         return new Target(exact.getUniqueId(), exact.getName(), exact);
      }

      OfflinePlayer cached = this.plugin.getServer().getOfflinePlayerIfCached(name);
      if (cached != null) {
         String resolved = cached.getName() == null ? name : cached.getName();
         return new Target(cached.getUniqueId(), resolved, null);
      }
      return new Target(null, name, null);
   }

   public QuietBanEntry issue(Target target, QuietBanLevel level, boolean ipLock,
                              long durationMillis, String reason, String issuedBy) {
      long now = System.currentTimeMillis();
      long expiresAt = durationMillis <= 0L ? 0L : now + durationMillis;

      lift(target.uuid(), target.name(), issuedBy, "replaced", now, true);

      String ip = target.online() == null ? null : addressOf(target.online());
      QuietBanEntry entry = QuietBanEntry.create(target.uuid(), target.name(), level, ipLock, ip,
         reason, issuedBy, now, expiresAt);

      this.active.put(entry.id(), entry);
      this.database.insert(entry);

      if (target.online() != null && target.online().isOnline()) {
         attach(target.online(), entry);
      }
      return entry;
   }

   public int lift(UUID uuid, String name, String liftedBy, String reason, long now, boolean silentReplace) {
      List<QuietBanEntry> matched = new ArrayList<>();
      String lower = name == null ? null : name.toLowerCase(Locale.ROOT);

      for (QuietBanEntry entry : this.active.values()) {
         boolean sameUuid = uuid != null && uuid.equals(entry.uuid());
         boolean sameName = lower != null && lower.equals(entry.playerNameLower());
         if (sameUuid || sameName) {
            matched.add(entry);
         }
      }

      if (matched.isEmpty()) {
         return 0;
      }

      if (this.module.isUnbanRemovingIpLinked()) {
         List<QuietBanEntry> linked = new ArrayList<>();
         for (QuietBanEntry entry : this.active.values()) {
            for (QuietBanEntry root : matched) {
               if (root.id().equals(entry.source())) {
                  linked.add(entry);
                  break;
               }
            }
         }
         matched.addAll(linked);
      }

      for (QuietBanEntry entry : matched) {
         this.active.remove(entry.id());
         this.database.deactivate(entry.id(), liftedBy, reason, now);
         if (entry.uuid() != null) {
            detach(entry.uuid());
         }
      }

      if (!silentReplace) {
         this.module.logAction("Lifted " + matched.size() + " quiet ban(s) for " + name + " by " + liftedBy);
      }
      return matched.size();
   }

   public void handleJoin(Player player, String ip) {
      UUID uuid = player.getUniqueId();
      String name = player.getName();
      long now = System.currentTimeMillis();

      QuietBanEntry entry = findByPlayer(uuid, name);
      if (entry != null) {
         if (entry.isExpired(now)) {
            expire(entry, now);
            return;
         }

         QuietBanEntry updated = entry;
         if (updated.uuid() == null) {
            updated = updated.withUuid(uuid);
         }
         if (!name.equals(updated.playerName())) {
            updated = updated.withPlayerName(name);
         }
         if (updated.ipLock() && (updated.ip() == null || updated.ip().isEmpty()) && ip != null) {
            updated = updated.withIp(ip);
         }
         if (updated != entry) {
            this.active.put(updated.id(), updated);
            this.database.updateIdentity(updated);
         }

         attach(player, updated);
         return;
      }

      QuietBanEntry origin = findIpLock(ip);
      if (origin == null || origin.isExpired(now) || this.module.isImmune(player)) {
         return;
      }

      QuietBanEntry derived = QuietBanEntry.derive(origin, uuid, name, ip, now);
      this.active.put(derived.id(), derived);
      this.database.insert(derived);
      attach(player, derived);

      this.module.logAction("Auto quiet ban by IP: " + name + " matches " + origin.playerName()
         + " (level " + derived.level().getKey() + ")");
   }

   public void handleQuit(UUID uuid) {
      this.attached.remove(uuid);
   }

   public void tickExpiry() {
      long now = System.currentTimeMillis();
      for (QuietBanEntry entry : this.active.values()) {
         if (entry.isExpired(now)) {
            expire(entry, now);
         }
      }
   }

   public void attachOnline() {
      long now = System.currentTimeMillis();
      for (Player player : this.plugin.getServer().getOnlinePlayers()) {
         if (this.module.isImmune(player)) {
            continue;
         }
         QuietBanEntry entry = findByPlayer(player.getUniqueId(), player.getName());
         if (entry != null && !entry.isExpired(now)) {
            attach(player, entry);
         }
      }
   }

   public void refreshProfiles() {
      for (Map.Entry<UUID, Channel> mapping : this.attached.entrySet()) {
         QuietBanEntry entry = findByPlayer(mapping.getKey(), null);
         if (entry == null) {
            detach(mapping.getKey());
            continue;
         }

         Channel channel = mapping.getValue();
         LagProfile profile = this.module.profileFor(entry.level());
         List<String> droppable = this.module.getDroppablePackets();
         channel.eventLoop().execute(() -> {
            ChannelHandler handler = channel.pipeline().get(ShadowLagHandler.NAME);
            if (handler instanceof ShadowLagHandler shadow) {
               shadow.update(profile, droppable);
            }
         });
      }
   }

   public void detachAll() {
      for (UUID uuid : List.copyOf(this.attached.keySet())) {
         detach(uuid);
      }
      this.attached.clear();
   }

   public void clearCache() {
      this.active.clear();
   }

   public String addressOf(Player player) {
      InetSocketAddress address = player.getAddress();
      if (address == null || address.getAddress() == null) {
         return null;
      }
      return address.getAddress().getHostAddress();
   }

   private void expire(QuietBanEntry entry, long now) {
      this.active.remove(entry.id());
      this.database.deactivate(entry.id(), "SYSTEM", "expired", now);
      if (entry.uuid() != null) {
         detach(entry.uuid());
      }
      this.module.logAction("Quiet ban expired for " + entry.playerName());
   }

   private void attach(Player player, QuietBanEntry entry) {
      UUID uuid = player.getUniqueId();
      Channel channel = ChannelResolver.resolve(player);
      if (channel == null) {
         this.plugin.getDebugSystem().logWarning("QuietBan",
            "Could not resolve network channel for " + player.getName() + ", quiet ban not applied");
         return;
      }

      this.attached.put(uuid, channel);
      LagProfile profile = this.module.profileFor(entry.level());
      List<String> droppable = this.module.getDroppablePackets();

      channel.eventLoop().execute(() -> {
         ChannelPipeline pipeline = channel.pipeline();
         ChannelHandler existing = pipeline.get(ShadowLagHandler.NAME);
         if (existing instanceof ShadowLagHandler shadow) {
            shadow.update(profile, droppable);
            return;
         }

         try {
            pipeline.addBefore(PIPELINE_ANCHOR, ShadowLagHandler.NAME,
               new ShadowLagHandler(uuid, profile, droppable, true));
            return;
         } catch (Throwable throwable) {
            this.plugin.getDebugSystem().log("QuietBan",
               "Pipeline anchor " + PIPELINE_ANCHOR + " missing, falling back to head injection");
         }

         try {
            pipeline.addFirst(ShadowLagHandler.NAME,
               new ShadowLagHandler(uuid, profile, droppable, false));
         } catch (Throwable throwable) {
            this.plugin.getDebugSystem().logError("QuietBan",
               "Failed to inject quiet ban handler for " + uuid, throwable);
         }
      });
   }

   private void detach(UUID uuid) {
      Channel channel = this.attached.remove(uuid);
      if (channel == null) {
         return;
      }

      channel.eventLoop().execute(() -> {
         try {
            if (channel.pipeline().get(ShadowLagHandler.NAME) != null) {
               channel.pipeline().remove(ShadowLagHandler.NAME);
            }
         } catch (Throwable throwable) {
            this.plugin.getDebugSystem().logError("QuietBan",
               "Failed to remove quiet ban handler for " + uuid, throwable);
         }
      });
   }
}
