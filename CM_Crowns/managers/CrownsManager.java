package net.schalker.SMPS.modules.crowns.managers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.schalker.DoAPI.DoAPI;
import net.schalker.SMPS.modules.crowns.CrownsModule;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

public class CrownsManager {
   public static final String CROWN_TAG = "SMPS_CROWN";

   private final DoAPI plugin;
   private final CrownsModule module;
   private final Map<UUID, UUID> crownEntities = new ConcurrentHashMap<>();
   private final Map<UUID, String> playerRoles = new ConcurrentHashMap<>();
   private final List<RoleConfig> roles = new ArrayList<>();
   private float crownHeight = 0.3f;

   public CrownsManager(DoAPI plugin, CrownsModule module) {
      this.plugin = plugin;
      this.module = module;
   }

   public void loadRoles(FileConfiguration config) {
      this.roles.clear();
      this.crownHeight = (float) config.getDouble("settings.crown-height", 0.3);

      ConfigurationSection rolesSection = config.getConfigurationSection("roles");
      if (rolesSection == null) return;

      for (String key : rolesSection.getKeys(false)) {
         ConfigurationSection roleSection = rolesSection.getConfigurationSection(key);
         if (roleSection == null) continue;

         RoleConfig role = new RoleConfig();
         role.key = key;
         role.prefix = roleSection.getString("prefix", "&f\ud83d\udc51");
         role.permission = roleSection.getString("permission", "smcrowns.group." + key);
         this.roles.add(role);
      }
   }

   public RoleConfig determineRole(Player player) {
      for (RoleConfig role : this.roles) {
         if (player.hasPermission(role.permission)) {
            return role;
         }
      }
      return null;
   }

   // ===== Player lifecycle =====

   public void handlePlayerJoin(Player player) {
      cleanupOrphans(player);

      RoleConfig role = determineRole(player);
      if (role == null) return;

      spawnCrown(player, role);
      this.playerRoles.put(player.getUniqueId(), role.key);
   }

   public void handlePlayerQuit(Player player) {
      removeCrown(player);
      this.playerRoles.remove(player.getUniqueId());
   }

   public void handlePlayerDeath(Player player) {
      removeCrown(player);
   }

   public void handlePlayerRespawn(Player player) {
      cleanupOrphans(player);

      RoleConfig role = determineRole(player);
      if (role == null) return;

      spawnCrown(player, role);
      this.playerRoles.put(player.getUniqueId(), role.key);
   }

   public void handlePreTeleport(Player player) {
      removeCrown(player);
   }

   public void handlePostTeleport(Player player) {
      cleanupOrphans(player);

      String roleKey = this.playerRoles.get(player.getUniqueId());
      if (roleKey == null) {
         RoleConfig role = determineRole(player);
         if (role == null) return;
         spawnCrown(player, role);
         this.playerRoles.put(player.getUniqueId(), role.key);
      } else {
         RoleConfig role = findRole(roleKey);
         if (role != null) {
            spawnCrown(player, role);
         }
      }
   }

   public void updatePlayer(Player player) {
      RoleConfig newRole = determineRole(player);
      String newRoleKey = newRole != null ? newRole.key : null;
      String oldRoleKey = this.playerRoles.get(player.getUniqueId());

      boolean crownExists = crownEntities.containsKey(player.getUniqueId());

      if (Objects.equals(oldRoleKey, newRoleKey) && (crownExists || newRole == null)) return;

      removeCrown(player);

      if (newRole != null) {
         spawnCrown(player, newRole);
         this.playerRoles.put(player.getUniqueId(), newRoleKey);
      } else {
         this.playerRoles.remove(player.getUniqueId());
      }
   }

   // ===== Internal =====

   /**
    * Spawn a TextDisplay as passenger on the player.
    * Uses Transformation to offset Y for precise crown-height control from config.
    */
   private void spawnCrown(Player player, RoleConfig role) {
      String colorized = this.plugin.applyColors(role.prefix);

      try {
         TextDisplay crown = player.getWorld().spawn(player.getLocation(), TextDisplay.class, display -> {
            display.setText(colorized);
            display.setBillboard(Display.Billboard.CENTER);
            display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            display.setSeeThrough(false);
            display.setShadowed(false);
            display.setDefaultBackground(false);
            display.setPersistent(false);
            display.addScoreboardTag(CROWN_TAG);

            // Y offset from config: crown-height
            Transformation transform = new Transformation(
               new Vector3f(0f, this.crownHeight, 0f),
               new AxisAngle4f(0f, 0f, 0f, 1f),
               new Vector3f(1f, 1f, 1f),
               new AxisAngle4f(0f, 0f, 0f, 1f)
            );
            display.setTransformation(transform);
         });

         player.addPassenger(crown);
         this.crownEntities.put(player.getUniqueId(), crown.getUniqueId());
      } catch (Exception e) {
         this.plugin.getDebugSystem().logError("Crowns: Failed to spawn crown for " + player.getName(), e);
      }
   }

   private void removeCrown(Player player) {
      UUID crownId = this.crownEntities.remove(player.getUniqueId());

      for (Entity entity : player.getPassengers()) {
         if (entity instanceof TextDisplay && entity.getScoreboardTags().contains(CROWN_TAG)) {
            player.removePassenger(entity);
            entity.remove();
         }
      }

      if (crownId != null) {
         try {
            Entity entity = Bukkit.getEntity(crownId);
            if (entity != null && !entity.isDead()) {
               entity.remove();
            }
         } catch (Exception ignored) {}
      }

      cleanupOrphans(player);
   }

   private void cleanupOrphans(Player player) {
      try {
         for (Entity entity : player.getWorld().getNearbyEntities(player.getLocation(), 10, 10, 10)) {
            if (entity instanceof TextDisplay && entity.getScoreboardTags().contains(CROWN_TAG)) {
               entity.remove();
            }
         }
      } catch (Exception ignored) {}
   }

   private RoleConfig findRole(String key) {
      for (RoleConfig role : this.roles) {
         if (role.key.equals(key)) return role;
      }
      return null;
   }

   public void rebuildAll() {
      this.playerRoles.clear();
      for (Player player : Bukkit.getOnlinePlayers()) {
         this.plugin.getSchedulerManager().runEntityTask(player,
            "crowns-rebuild-" + player.getUniqueId().toString().substring(0, 8), () -> {
            if (player.isOnline()) {
               removeCrown(player);
               handlePlayerJoin(player);
            }
         });
      }
   }

   public void cleanup() {
      for (Player player : Bukkit.getOnlinePlayers()) {
         try {
            this.plugin.getSchedulerManager().runEntityTask(player,
               "crowns-cleanup-" + player.getUniqueId().toString().substring(0, 8), () -> {
               if (player.isOnline()) {
                  removeCrown(player);
               }
            });
         } catch (Exception ignored) {}
      }

      for (org.bukkit.World world : Bukkit.getWorlds()) {
         try {
            for (Entity entity : world.getEntities()) {
               if (entity instanceof TextDisplay && entity.getScoreboardTags().contains(CROWN_TAG)) {
                  try { entity.remove(); } catch (Exception ignored) {}
               }
            }
         } catch (Exception ignored) {}
      }

      this.crownEntities.clear();
      this.playerRoles.clear();
   }

   public static class RoleConfig {
      String key;
      String prefix;
      String permission;

      public String getKey() { return key; }
      public String getPrefix() { return prefix; }
      public String getPermission() { return permission; }
   }
}
