package net.schalker.SMPS.modules.flags.managers;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.schalker.DoAPI.DoAPI;
import net.schalker.SMPS.modules.flags.FlagsModule;
import org.bukkit.entity.Player;

public class LowPlaytimeReminderManager {
   private static final String TASK_ID = "flags-low-playtime-reminder";
   private static final Pattern HEX_AMPERSAND = Pattern.compile("&#([A-Fa-f0-9]{6})");
   private static final Pattern LEGACY_AMP = Pattern.compile("&([0-9a-fk-orA-FK-OR])");

   private static final Map<Character, String> CODE_TO_TAG = new LinkedHashMap<>();

   static {
      CODE_TO_TAG.put('0', "black");
      CODE_TO_TAG.put('1', "dark_blue");
      CODE_TO_TAG.put('2', "dark_green");
      CODE_TO_TAG.put('3', "dark_aqua");
      CODE_TO_TAG.put('4', "dark_red");
      CODE_TO_TAG.put('5', "dark_purple");
      CODE_TO_TAG.put('6', "gold");
      CODE_TO_TAG.put('7', "gray");
      CODE_TO_TAG.put('8', "dark_gray");
      CODE_TO_TAG.put('9', "blue");
      CODE_TO_TAG.put('a', "green");
      CODE_TO_TAG.put('b', "aqua");
      CODE_TO_TAG.put('c', "red");
      CODE_TO_TAG.put('d', "light_purple");
      CODE_TO_TAG.put('e', "yellow");
      CODE_TO_TAG.put('f', "white");
      CODE_TO_TAG.put('k', "obfuscated");
      CODE_TO_TAG.put('l', "bold");
      CODE_TO_TAG.put('m', "strikethrough");
      CODE_TO_TAG.put('n', "underlined");
      CODE_TO_TAG.put('o', "italic");
      CODE_TO_TAG.put('r', "reset");
   }

   private final DoAPI plugin;
   private final FlagsModule module;
   private final FlagsManager flagsManager;
   private final MiniMessage miniMessage;

   public LowPlaytimeReminderManager(DoAPI plugin, FlagsModule module, FlagsManager flagsManager) {
      this.plugin = plugin;
      this.module = module;
      this.flagsManager = flagsManager;
      this.miniMessage = MiniMessage.miniMessage();
   }

   public void restart() {
      stop();
      if (!isEnabled()) {
         return;
      }

      long intervalTicks = Math.max(20L, getIntervalSeconds() * 20L);
      this.plugin.getSchedulerManager().runAsyncTimer(TASK_ID, this::tick, intervalTicks, intervalTicks);
   }

   public void stop() {
      this.plugin.getSchedulerManager().cancelTask(TASK_ID);
   }

   private boolean isEnabled() {
      return this.module.getConfig().getBoolean("playtime-reminder.enabled", false)
         && this.flagsManager != null
         && this.flagsManager.getPlaytimeSensitivity() != null
         && this.flagsManager.getPlaytimeSensitivity().isEnabled();
   }

   private long getIntervalSeconds() {
      long seconds = this.module.getConfig().getLong("playtime-reminder.interval-seconds", -1L);
      if (seconds > 0L) {
         return seconds;
      }

      long minutesFallback = Math.max(1L, this.module.getConfig().getLong("playtime-reminder.interval-minutes", 15L));
      return minutesFallback * 60L;
   }

   private Set<String> getTargetTiers() {
      return this.module.getConfig().getStringList("playtime-reminder.target-tiers").stream()
         .map(v -> v.toLowerCase(Locale.ROOT))
         .collect(java.util.stream.Collectors.toSet());
   }

   private void tick() {
      Set<String> targetTiers = getTargetTiers();
      if (targetTiers.isEmpty()) {
         return;
      }

      PlaytimeSensitivityManager sensitivity = this.flagsManager.getPlaytimeSensitivity();
      if (sensitivity == null) {
         return;
      }

      int maxMinutes = this.module.getConfig().getInt("playtime-reminder.max-minutes", sensitivity.getBeginnerMaxMinutes());
      String template = getReminderTemplate();

      for (Player player : this.plugin.getServer().getOnlinePlayers()) {
         String tier = this.flagsManager.getTierKey(player.getUniqueId());
         if (tier == null || !targetTiers.contains(tier.toLowerCase(Locale.ROOT))) {
            continue;
         }

         long minutes = sensitivity.getTotalMinutes(player.getUniqueId());
         long minutesLeft = Math.max(0L, maxMinutes - Math.max(0L, minutes));
         String message = template
            .replace("{player}", player.getName())
            .replace("{tier}", tier)
            .replace("{minutes}", String.valueOf(Math.max(0L, minutes)))
            .replace("{hours_left}", String.valueOf(Math.max(0L, minutesLeft / 60L)));

         Component component = format(message);
         this.plugin.getSchedulerManager().runEntityTask(player,
            "flags-playtime-reminder-" + player.getUniqueId() + "-" + System.nanoTime(),
            () -> {
               if (player.isOnline()) {
                  player.sendMessage(component);
               }
            }
         );
      }
   }

   private String getReminderTemplate() {
      Object rawMessage = this.module.getConfig().get("playtime-reminder.message");
      if (rawMessage instanceof java.util.List<?> lines && !lines.isEmpty()) {
         return lines.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining("\n"));
      }

      return this.module.getConfig().getString(
         "playtime-reminder.message",
         "&[SECONDARY]Tip: complete tutorial commands. You have &[MAIN]{hours_left}h &[SECONDARY]left in newcomer phase."
      );
   }

   private Component format(String input) {
      if (input == null || input.isBlank()) {
         return Component.empty();
      }

      try {
         String text = replaceThemePlaceholders(input);
         text = convertHexAmpersand(text);
         text = convertLegacyCodes(text);
         return this.miniMessage.deserialize(text);
      } catch (Exception ex) {
         this.plugin.getDebugSystem().logWarning("Flags", "Failed to parse playtime reminder MiniMessage", ex);
         return Component.text(this.plugin.applyColors(input));
      }
   }

   private String replaceThemePlaceholders(String text) {
      String main = normalizeThemeColor(this.plugin.getMainColor(), "&6");
      String secondary = normalizeThemeColor(this.plugin.getSecondaryColor(), "&e");

      return text
         .replace("&[MAIN]", main)
         .replace("&[main]", main)
         .replace("&[SECONDARY]", secondary)
         .replace("&[secondary]", secondary);
   }

   private String normalizeThemeColor(String value, String fallback) {
      if (value == null || value.isBlank()) {
         return fallback;
      }

      String color = value.trim();
      if (color.startsWith("#") && color.length() == 7) {
         return "&" + color;
      }
      return color;
   }

   private String convertHexAmpersand(String text) {
      Matcher withAmp = HEX_AMPERSAND.matcher(text);
      StringBuilder out = new StringBuilder();
      while (withAmp.find()) {
         withAmp.appendReplacement(out, Matcher.quoteReplacement("<#" + withAmp.group(1) + ">"));
      }
      withAmp.appendTail(out);
      return out.toString();
   }

   private String convertLegacyCodes(String text) {
      Matcher matcher = LEGACY_AMP.matcher(text);
      StringBuilder out = new StringBuilder();

      while (matcher.find()) {
         char code = Character.toLowerCase(matcher.group(1).charAt(0));
         String tag = CODE_TO_TAG.get(code);
         if (tag == null) {
            continue;
         }
         matcher.appendReplacement(out, Matcher.quoteReplacement("<" + tag + ">"));
      }

      matcher.appendTail(out);
      return out.toString();
   }
}

