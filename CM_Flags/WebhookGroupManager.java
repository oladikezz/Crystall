package net.schalker.SMPS.modules.flags;

import java.util.EnumMap;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Manages webhook groups — each group has a webhook URL and a list of flag keys.
 * Resolves which webhook to use for a given FlagType.
 * Each flag can only belong to ONE group — the first group that claims it wins.
 */
public class WebhookGroupManager {
   private final Map<FlagType, DiscordWebhook> webhookMap = new EnumMap<>(FlagType.class);
   // Track which group name owns each flag, for debugging
   private final Map<FlagType, String> ownerGroup = new EnumMap<>(FlagType.class);
   private Logger logger;

   public WebhookGroupManager(FileConfiguration config) {
      this.reload(config);
   }

   public WebhookGroupManager(FileConfiguration config, Logger logger) {
      this.logger = logger;
      this.reload(config);
   }

   public void reload(FileConfiguration config) {
      this.webhookMap.clear();
      this.ownerGroup.clear();
      if (config == null) return;

      ConfigurationSection groups = config.getConfigurationSection("webhook_groups");
      if (groups == null) {
         // Fallback: try old-style single webhook config for backwards compatibility
         String oldUrl = config.getString("discord.webhook-url", "");
         String storageUrl = config.getString("discord.storage-webhook-url", oldUrl);
         if (!oldUrl.isEmpty() && !oldUrl.equals("your-webhook-url-here")) {
            DiscordWebhook mainWebhook = new DiscordWebhook(oldUrl);
            DiscordWebhook storageWebhook = new DiscordWebhook(storageUrl);
            for (FlagType flagType : FlagType.values()) {
               if (flagType.isStorageTransport()) {
                  this.webhookMap.put(flagType, storageWebhook);
               } else {
                  this.webhookMap.put(flagType, mainWebhook);
               }
            }
         }
         return;
      }

      for (String groupKey : groups.getKeys(false)) {
         ConfigurationSection group = groups.getConfigurationSection(groupKey);
         if (group == null) continue;

         String webhookUrl = group.getString("webhook", "");
         if (webhookUrl.isEmpty() || webhookUrl.equals("your-webhook-url-here")) continue;

         DiscordWebhook webhook = new DiscordWebhook(webhookUrl);

         var flagsList = group.getStringList("flags");
         for (String flagKey : flagsList) {
            // Use expanded resolution to support alias keys (e.g. "tnt_placement" → overworld+nether)
            java.util.List<FlagType> expandedTypes = FlagType.fromKeyExpanded(flagKey);
            if (expandedTypes.isEmpty() && this.logger != null) {
               this.logger.warning("[SM_Flags] Webhook group '" + groupKey + "' references unknown flag key: " + flagKey);
            }
            for (FlagType flagType : expandedTypes) {
               // First group to claim a flag wins — skip if already assigned
               if (this.webhookMap.containsKey(flagType)) {
                  if (this.logger != null) {
                     this.logger.info("[SM_Flags] Flag " + flagType.getKey() + " already assigned to group '"
                        + this.ownerGroup.get(flagType) + "', skipping group '" + groupKey + "'");
                  }
                  continue;
               }
               this.webhookMap.put(flagType, webhook);
               this.ownerGroup.put(flagType, groupKey);
               if (this.logger != null) {
                  this.logger.info("[SM_Flags] Flag " + flagType.getKey() + " → group '" + groupKey + "'");
               }
            }
         }
      }
   }

   /**
    * Get the webhook for a given flag type.
    * Returns null if no webhook is configured for this flag.
    */
   public DiscordWebhook getWebhookForFlag(FlagType flagType) {
      return this.webhookMap.get(flagType);
   }

   /**
    * Get which group owns the given flag, for debugging.
    */
   public String getGroupForFlag(FlagType flagType) {
      return this.ownerGroup.get(flagType);
   }
}

