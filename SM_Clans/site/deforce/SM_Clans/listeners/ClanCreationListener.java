package site.deforce.SM_Clans.listeners;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.debug.DebugSystem;
import net.schalker.DoAPI.core.listener.BaseListener;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerQuitEvent;
import site.deforce.SM_Clans.SM_Clans;
import site.deforce.SM_Clans.managers.ClanEconomyManager;
import site.deforce.SM_Clans.managers.ClanManager;
import site.deforce.SM_Clans.models.Clan;
import site.deforce.SM_Clans.models.ClanCreationProcess;
import site.deforce.SM_Clans.models.PendingPurchase;
import site.deforce.SM_Clans.util.StyleInput;

public class ClanCreationListener extends BaseListener {
   private final ClanManager clanManager;
   private SM_Clans module;
   private final Map<UUID, ClanCreationProcess> creationProcesses = new ConcurrentHashMap();
   private final Set<UUID> editingName = ConcurrentHashMap.newKeySet();
   private final Set<UUID> editingTag = ConcurrentHashMap.newKeySet();
   private final Set<UUID> editingDescription = ConcurrentHashMap.newKeySet();

   public ClanCreationListener(DoAPI plugin, ClanManager clanManager) {
      super(plugin);
      this.clanManager = clanManager;
   }

   public void setModule(SM_Clans module) {
      this.module = module;
   }

   public void startClanCreation(Player player) {
      if (this.clanManager.hasPlayerClan(player.getUniqueId())) {
         this.sendMessage(player, this.getMessage("already-in-clan"));
      } else {
         ClanEconomyManager econ = this.module != null ? this.module.getClanEconomyManager() : null;
         int creationCost = econ != null ? econ.getCreationCost() : 0;
         if (econ != null && econ.isEnabled() && creationCost > 0 && econ.countCurrency(player) < creationCost) {
            this.sendMessage(player, this.getMessage("economy.creation-not-enough").replace("{cost}", String.valueOf(creationCost)));
         } else {
            FileConfiguration config = this.module != null ? this.module.getConfig() : null;
            int minTag = config != null ? config.getInt("clans.min-tag-length", 2) : 2;
            int maxTag = config != null ? config.getInt("clans.max-tag-length", 10) : 10;
            int minName = config != null ? config.getInt("clans.min-name-length", 1) : 1;
            int maxName = config != null ? config.getInt("clans.max-name-length", 24) : 24;
            String priceLine = econ != null && econ.isEnabled() && creationCost > 0 ? "Стоимость: " + creationCost + " ар (из инвентаря)" : "";
            this.module.getDialogManager().openCreationForm(player, priceLine, minTag, maxTag, minName, maxName, (tag, name) -> this.createFromForm(player, tag, name));
         }
      }
   }

   public void createFromForm(Player player, String rawTag, String rawName) {
      if (this.module != null) {
         if (this.clanManager.hasPlayerClan(player.getUniqueId())) {
            this.sendMessage(player, this.getMessage("already-in-clan"));
         } else {
            rawTag = StyleInput.miniToLegacy(rawTag);
            rawName = StyleInput.miniToLegacy(rawName);
            FileConfiguration config = this.module.getConfig();
            boolean allowColorsTag = config == null || config.getBoolean("clans.allow-colored-tags", true);
            String tag = (allowColorsTag ? rawTag : this.stripColorCodes(rawTag)).trim();
            tag = this.normalizeHexColors(tag);
            if (!allowColorsTag) {
               tag = this.stripColorCodes(tag).trim();
            }

            int minTag = config != null ? config.getInt("clans.min-tag-length", 2) : 2;
            int maxTag = config != null ? config.getInt("clans.max-tag-length", 10) : 10;
            String strippedTag = this.stripColorCodes(rawTag).trim();
            int tagLen = strippedTag.codePointCount(0, strippedTag.length());
            boolean tagOk = tagLen > 0 && (tagLen == 1 || tagLen >= minTag && tagLen <= maxTag);
            if (!tag.isEmpty() && tagOk) {
               if (this.isTagTaken(strippedTag)) {
                  this.sendMessage(player, this.getMessage("creation-tag-exists"));
               } else {
                  int minName = config != null ? config.getInt("clans.min-name-length", 1) : 1;
                  int maxName = config != null ? config.getInt("clans.max-name-length", 24) : 24;
                  boolean allowColoredNames = config != null && config.getBoolean("clans.allow-colored-names", false);
                  String name = rawName.trim();

                  String plainName;
                  try {
                     Component component = LegacyComponentSerializer.legacyAmpersand().deserialize(name);
                     plainName = PlainTextComponentSerializer.plainText().serialize(component);
                  } catch (Exception var23) {
                     this.sendMessage(player, this.getMessage("creation-invalid-name-format"));
                     return;
                  }

                  String strippedName = this.stripColorCodes(plainName).trim();
                  if (strippedName.isEmpty()) {
                     this.sendMessage(player, this.getMessage("creation-invalid-name-empty"));
                  } else if (strippedName.length() >= minName && strippedName.length() <= maxName) {
                     if (!allowColoredNames) {
                        name = strippedName;
                     }

                     if (this.clanManager.isClanNameTaken(name, (String)null)) {
                        this.sendMessage(player, this.getMessage("creation-name-exists"));
                     } else {
                        ClanEconomyManager econ = this.module.getClanEconomyManager();
                        int creationCost = econ != null ? econ.getCreationCost() : 0;
                        boolean chargeCreation = econ != null && econ.isEnabled() && creationCost > 0;
                        if (chargeCreation && econ.countCurrency(player) < creationCost) {
                           this.sendMessage(player, this.getMessage("economy.creation-not-enough").replace("{cost}", String.valueOf(creationCost)));
                        } else {
                           try {
                              Clan clan = this.clanManager.createClan(tag, name, player.getUniqueId());
                              if (chargeCreation) {
                                 econ.removeCurrency(player, creationCost);
                                 this.sendMessage(player, this.getMessage("economy.creation-paid").replace("{cost}", String.valueOf(creationCost)));
                                 if (this.module.getAuditLogger() != null) {
                                    this.module.getAuditLogger().logCreation(player, clan, (long)creationCost);
                                 }
                              }

                              this.sendMessage(player, this.getMessage("creation-success").replace("{tag}", clan.getTag()).replace("{name}", clan.getName()));
                              DebugSystem var10000 = this.plugin.getDebugSystem();
                              String var10002 = clan.getTag();
                              var10000.log("ClanCreation", "Clan created: " + var10002 + " by " + player.getName());
                              if (this.shouldBroadcast("clan-created")) {
                                 this.module.getClanInviteManager().broadcastClanEvent("broadcast.clan-created", "{player}", player.getName(), "{tag}", this.escapeLegacyCodes(clan.getTag()), "{name}", this.escapeLegacyCodes(clan.getName()));
                              }
                           } catch (Exception exception) {
                              this.sendMessage(player, this.getMessage("creation-error"));
                              this.plugin.getDebugSystem().logError("Failed to create clan", exception);
                           }

                        }
                     }
                  } else {
                     this.sendMessage(player, this.getMessage("creation-invalid-name-length"));
                  }
               }
            } else {
               this.sendMessage(player, this.getMessage("creation-invalid-tag-length").replace("{min}", String.valueOf(minTag)).replace("{max}", String.valueOf(maxTag)));
            }
         }
      }
   }

   public void startNameEdit(Player player) {
      this.editingTag.remove(player.getUniqueId());
      this.editingDescription.remove(player.getUniqueId());
      this.editingName.add(player.getUniqueId());
   }

   public void startTagEdit(Player player) {
      this.editingName.remove(player.getUniqueId());
      this.editingDescription.remove(player.getUniqueId());
      this.editingTag.add(player.getUniqueId());
   }

   public void startDescriptionEdit(Player player) {
      this.editingName.remove(player.getUniqueId());
      this.editingTag.remove(player.getUniqueId());
      this.editingDescription.add(player.getUniqueId());
   }

   @EventHandler(
      priority = EventPriority.LOWEST
   )
   public void onChat(AsyncChatEvent event) {
      Player player = event.getPlayer();
      UUID playerId = player.getUniqueId();
      Component message = event.message();
      String text = PlainTextComponentSerializer.plainText().serialize(message);
      if (this.module != null && this.module.handleConfirmChat(player, text)) {
         event.setCancelled(true);
      } else if (this.isHandlingChat(playerId)) {
         event.setCancelled(true);
         String legacyText = LegacyComponentSerializer.legacyAmpersand().serialize(message);
         this.plugin.getSchedulerManager().runTaskLater(player, () -> this.handleChatInput(player, text, legacyText), 1L);
      }
   }

   private boolean isHandlingChat(UUID playerId) {
      return this.editingName.contains(playerId) || this.editingDescription.contains(playerId) || this.editingTag.contains(playerId) || this.creationProcesses.containsKey(playerId);
   }

   private void handleChatInput(Player player, String text, String legacyText) {
      if (player.isOnline()) {
         UUID playerId = player.getUniqueId();
         if (this.editingName.remove(playerId)) {
            if (!text.equalsIgnoreCase("cancel")) {
               if (this.module != null) {
                  ClanEconomyManager econ = this.module.getClanEconomyManager();
                  long cost = econ == null ? 0L : (long)econ.getNameCost();
                  this.module.requestPurchase(player, new PendingPurchase(PendingPurchase.Type.CHANGE_NAME, legacyText, cost, "Название → " + legacyText));
               }
            } else {
               this.sendMessage(player, this.getMessage("edit-cancelled"));
            }

         } else if (this.editingDescription.remove(playerId)) {
            if (!text.equalsIgnoreCase("cancel")) {
               if (this.module != null) {
                  ClanEconomyManager econ = this.module.getClanEconomyManager();
                  long cost = econ == null ? 0L : (long)econ.getDescriptionCost();
                  this.module.requestPurchase(player, new PendingPurchase(PendingPurchase.Type.CHANGE_DESCRIPTION, legacyText, cost, "Изменение описания"));
               }
            } else {
               this.sendMessage(player, this.getMessage("edit-cancelled"));
            }

         } else if (this.editingTag.remove(playerId)) {
            if (!text.equalsIgnoreCase("cancel")) {
               String tagText = legacyText.split(" ")[0];
               if (this.module != null) {
                  ClanEconomyManager econ = this.module.getClanEconomyManager();
                  long cost = econ == null ? 0L : (long)econ.getTagCost();
                  this.module.requestPurchase(player, new PendingPurchase(PendingPurchase.Type.CHANGE_TAG, tagText, cost, "Тег → " + tagText));
               }
            } else {
               this.sendMessage(player, this.getMessage("edit-cancelled"));
            }

         } else {
            ClanCreationProcess process = (ClanCreationProcess)this.creationProcesses.get(playerId);
            if (process != null) {
               if (process.isExpired()) {
                  this.creationProcesses.remove(playerId);
                  this.sendMessage(player, this.getMessage("creation-expired"));
               } else if (text.equalsIgnoreCase("cancel")) {
                  this.creationProcesses.remove(playerId);
                  this.sendMessage(player, this.getMessage("creation-cancelled"));
               } else {
                  switch (process.getStep()) {
                     case WAITING_TAG -> this.handleTagInput(player, process, text, legacyText);
                     case WAITING_NAME -> this.handleNameInput(player, process, legacyText);
                     case COMPLETED -> this.creationProcesses.remove(playerId);
                  }

               }
            }
         }
      }
   }

   private void handleTagInput(Player player, ClanCreationProcess process, String textTag, String legacyTag) {
      FileConfiguration config = this.module != null ? this.module.getConfig() : null;
      boolean allowColors = config == null || config.getBoolean("clans.allow-colored-tags", true);
      String tag = allowColors ? legacyTag : textTag;
      tag = tag.trim();
      tag = this.normalizeHexColors(tag);
      if (!allowColors) {
         tag = this.stripColorCodes(tag).trim();
      }

      int minLength = config != null ? config.getInt("clans.min-tag-length", 2) : 2;
      int maxLength = config != null ? config.getInt("clans.max-tag-length", 10) : 10;
      String strippedTag = this.stripColorCodes(textTag).trim();
      int visibleLength = strippedTag.codePointCount(0, strippedTag.length());
      boolean lengthOk = visibleLength > 0 && (visibleLength == 1 || visibleLength >= minLength && visibleLength <= maxLength);
      if (!tag.isEmpty() && lengthOk) {
         if (this.isTagTaken(strippedTag)) {
            this.sendMessage(player, this.getMessage("creation-tag-exists"));
         } else {
            process.setTag(tag);
            process.setStep(ClanCreationProcess.ClanCreationStep.WAITING_NAME);
            this.sendMessage(player, this.getMessage("creation-tag-accepted").replace("{tag}", tag));
            this.sendMessage(player, this.getMessage("creation-enter-name"));
            if (config != null && config.getBoolean("clans.allow-colored-names", false)) {
               this.sendMessage(player, this.getMessage("creation-colors-hint"));
            }
         }
      } else {
         this.sendMessage(player, this.getMessage("creation-invalid-tag-length").replace("{min}", String.valueOf(minLength)).replace("{max}", String.valueOf(maxLength)));
      }

   }

   private boolean isTagTaken(String strippedTag) {
      for(Clan clan : this.clanManager.getAllClans()) {
         String existingStripped = this.stripColorCodes(clan.getTag()).trim();
         if (existingStripped.equalsIgnoreCase(strippedTag)) {
            return true;
         }
      }

      return false;
   }

   private void handleNameInput(Player player, ClanCreationProcess process, String name) {
      name = name.trim();
      FileConfiguration config = this.module != null ? this.module.getConfig() : null;
      int minLength = config != null ? config.getInt("clans.min-name-length", 1) : 1;
      int maxLength = config != null ? config.getInt("clans.max-name-length", 24) : 24;
      boolean allowColoredNames = config != null && config.getBoolean("clans.allow-colored-names", false);
      if (!name.isEmpty()) {
         try {
            Component testComponent = LegacyComponentSerializer.legacyAmpersand().deserialize(name);
            String plainText = PlainTextComponentSerializer.plainText().serialize(testComponent);
            String strippedPlain = this.stripColorCodes(plainText).trim();
            if (strippedPlain.isEmpty()) {
               this.sendMessage(player, this.getMessage("creation-invalid-name-empty"));
               return;
            }

            if (strippedPlain.length() < minLength || strippedPlain.length() > maxLength) {
               this.sendMessage(player, this.getMessage("creation-invalid-name-length"));
               return;
            }

            if (!allowColoredNames) {
               name = strippedPlain;
            }
         } catch (Exception var14) {
            this.sendMessage(player, this.getMessage("creation-invalid-name-format"));
            this.plugin.getDebugSystem().log("ClanCreation", "Invalid format: " + name + " by " + player.getName());
            return;
         }

         if (this.clanManager.isClanNameTaken(name, (String)null)) {
            this.sendMessage(player, this.getMessage("creation-name-exists"));
            return;
         }

         ClanEconomyManager econ = this.module != null ? this.module.getClanEconomyManager() : null;
         int creationCost = econ != null ? econ.getCreationCost() : 0;
         boolean chargeCreation = econ != null && econ.isEnabled() && creationCost > 0;
         if (chargeCreation && econ.countCurrency(player) < creationCost) {
            this.sendMessage(player, this.getMessage("economy.creation-not-enough").replace("{cost}", String.valueOf(creationCost)));
            this.creationProcesses.remove(player.getUniqueId());
            return;
         }

         process.setName(name);
         process.setStep(ClanCreationProcess.ClanCreationStep.COMPLETED);

         try {
            Clan clan = this.clanManager.createClan(process.getTag(), name, player.getUniqueId());
            if (chargeCreation) {
               econ.removeCurrency(player, creationCost);
               this.sendMessage(player, this.getMessage("economy.creation-paid").replace("{cost}", String.valueOf(creationCost)));
               if (this.module.getAuditLogger() != null) {
                  this.module.getAuditLogger().logCreation(player, clan, (long)creationCost);
               }
            }

            this.creationProcesses.remove(player.getUniqueId());
            this.sendMessage(player, this.getMessage("creation-success").replace("{tag}", clan.getTag()).replace("{name}", clan.getName()));
            String clanTag = clan.getTag();
            this.plugin.getDebugSystem().log("ClanCreation", "Clan created: " + clanTag + " by " + player.getName());
            if (this.module != null && this.shouldBroadcast("clan-created")) {
               this.module.getClanInviteManager().broadcastClanEvent("broadcast.clan-created", "{player}", player.getName(), "{tag}", this.escapeLegacyCodes(clan.getTag()), "{name}", this.escapeLegacyCodes(clan.getName()));
            }
         } catch (Exception exception) {
            this.creationProcesses.remove(player.getUniqueId());
            this.sendMessage(player, this.getMessage("creation-error"));
            this.plugin.getDebugSystem().logError("Failed to create clan", exception);
         }
      }

   }

   private String stripColorCodes(String text) {
      if (text == null) {
         return "";
      } else {
         String noLegacy = text.replaceAll("(?i)[&Â§][0-9a-fk-orx]", "");
         return noLegacy.replaceAll("(?i)(?:&#|#)[0-9a-f]{6}", "");
      }
   }

   private String normalizeHexColors(String text) {
      return text == null ? "" : text.replaceAll("(?<!&)(#[0-9a-fA-F]{6})", "&$1");
   }

   private boolean shouldBroadcast(String eventType) {
      FileConfiguration config = this.module != null ? this.module.getConfig() : null;
      return config == null || config.getBoolean("broadcast." + eventType, true);
   }

   private String escapeLegacyCodes(String text) {
      if (text == null) {
         return "";
      } else {
         String normalized = text.replaceAll("(?<![&§])(#[0-9a-fA-F]{6})", "&$1");
         return normalized.replace("§", "&") + "&r";
      }
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      UUID playerId = event.getPlayer().getUniqueId();
      this.creationProcesses.remove(playerId);
      this.editingName.remove(playerId);
      this.editingTag.remove(playerId);
      this.editingDescription.remove(playerId);
      if (this.module != null) {
         this.module.clearConfirm(playerId);
         this.module.getClanInviteManager().clearPlayerInvites(playerId);
      }

   }

   private Clan findClanByTag(String tag) {
      if (tag != null && !tag.isEmpty() && this.module != null) {
         Clan c = this.module.getClanManager().getClanByTag(tag);
         if (c != null) {
            return c;
         } else {
            String stripped = this.stripColorCodes(tag).trim();

            for(Clan clan : this.module.getClanManager().getAllClans()) {
               if (this.stripColorCodes(clan.getTag()).trim().equalsIgnoreCase(stripped)) {
                  return clan;
               }
            }

            return null;
         }
      } else {
         return null;
      }
   }

   private String getMessage(String key) {
      FileConfiguration config = this.module != null ? this.module.getMessages() : null;
      if (config == null) {
         return "§cMessage not found: " + key;
      } else {
         String message = config.getString(key, "§cMessage not found: " + key);
         String prefix = config.getString("prefix", "");
         FileConfiguration mainConfig = this.module.getConfig();
         if (mainConfig != null) {
            boolean prefixEnabled = mainConfig.getBoolean("prefix.enabled", true);
            if (!prefixEnabled) {
               prefix = "";
            }
         }

         String combined = message.replace("<prefix>", prefix);
         return this.module.getPlugin().applyColors(combined);
      }
   }

   private void sendMessage(Player player, String message) {
      if (message != null && !message.isEmpty()) {
         this.plugin.getSchedulerManager().runEntityTask(player, "clan-creation-message", () -> {
            if (player.isOnline()) {
               player.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(message.replace("§", "&")));
            }

         });
      }

   }
}
