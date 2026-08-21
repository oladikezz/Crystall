package net.schalker.SMPS.modules.voodoo;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.schalker.DoAPI.DoAPI;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for creating and identifying Voodoo totem items.
 *
 * Uses the {@code custom_model_data} strings component (1.21.4+) to tag
 * each totem with a target nickname.  The resource-pack overrides the
 * {@code totem_of_undying} item definition with a {@code select} on
 * {@code custom_model_data} strings[0] to swap in a per-player model.
 * Clients without the RP use the vanilla item definition and render a
 * normal Totem of Undying (no purple/black cube).
 */
public final class VoodooItem {
   // PDC keys kept as "woodoo_*" for backward compatibility with existing items
   /** PDC key for the target player name */
   public static final NamespacedKey KEY_TARGET = new NamespacedKey("smps", "woodoo_target");
   /** PDC key for the owner player name */
   public static final NamespacedKey KEY_OWNER = new NamespacedKey("smps", "woodoo_owner");
   /** PDC key marking this as a voodoo item */
   public static final NamespacedKey KEY_MARKER = new NamespacedKey("smps", "woodoo_item");

   /** Gradient tag patterns (supports 2+ color stops) */
   private static final Pattern MINI_GRADIENT_PATTERN =
      Pattern.compile("(?i)<gradient:(#?[a-f0-9]{6}(?::#?[a-f0-9]{6})+)>(.*?)</gradient>");
   private static final Pattern SHORT_GRADIENT_PATTERN =
      Pattern.compile("(?i)<(#?[a-f0-9]{6}):(#?[a-f0-9]{6})>(.*?)</>");
   private static final Pattern HEX_PATTERN =
      Pattern.compile("(?i)#([a-f0-9]{6})");

   private VoodooItem() {}

   /**
    * Create a Voodoo totem item.
    *
    * @param targetName  target player nickname
    * @param ownerName   owner player nickname
    * @param config      the full module config (reads "models" and "item" sections).
    *                    If null, uses hardcoded defaults.
    * @param plugin      the SMPS plugin instance for color processing.
    */
   public static ItemStack create(String targetName, String ownerName, FileConfiguration config, DoAPI plugin) {
      ItemStack item = new ItemStack(Material.TOTEM_OF_UNDYING, 1);

      // Check models config — supports both old (boolean) and new (object with enabled/prefix) format
      ConfigurationSection modelsSection = config != null ? config.getConfigurationSection("models") : null;
      boolean hasCustomModel = isModelEnabled(modelsSection, targetName);
      if (hasCustomModel) {
         item.setData(DataComponentTypes.CUSTOM_MODEL_DATA,
            CustomModelData.customModelData()
               .addString(targetName.toLowerCase())
               .build());
      }

      // Read item appearance from config — name is always from item.name
      String displayName = config != null
         ? config.getString("item.name", "&5Вуду-кукла: &c{target}")
         : "&5Вуду-кукла: &c{target}";

      // Resolve styled names: if a player has a custom prefix in models, use it for {target}/{owner}
      String styledTarget = resolveStyledName(modelsSection, targetName);
      String styledOwner = resolveStyledName(modelsSection, ownerName);

      List<String> loreLines = config != null
         ? config.getStringList("item.lore")
         : List.of();
      boolean glow = config == null || config.getBoolean("item.glow", true);

      // If lore is empty (not configured), use defaults
      if (loreLines.isEmpty()) {
         loreLines = List.of(
            "", "&7Цель: &e{target}", "&7Владелец: &a{owner}", "",
            "&cЛКМ &7— негативный эффект", "&aПКМ &7— позитивный эффект", "",
            "&4⚠ Нельзя выбросить или положить в контейнер"
         );
      }

      // Apply placeholders — use styled names (custom prefix if set, otherwise plain nick)
      String finalName = displayName.replace("{target}", styledTarget).replace("{owner}", styledOwner);
      List<String> finalLore = new ArrayList<>();
      for (String line : loreLines) {
         finalLore.add(line.replace("{target}", styledTarget).replace("{owner}", styledOwner));
      }

      item.editMeta(meta -> {
         // Display name — use SMPS color processing (supports &[MAIN], &[SECONDARY], &#RRGGBB, &x)
         meta.displayName(colorize(finalName, plugin).decoration(TextDecoration.ITALIC, false));

         // Lore
         List<Component> loreComponents = new ArrayList<>();
         for (String line : finalLore) {
            if (line.isEmpty()) {
               loreComponents.add(Component.empty());
            } else {
               loreComponents.add(colorize(line, plugin).decoration(TextDecoration.ITALIC, false));
            }
         }
         meta.lore(loreComponents);

         // Enchant glow effect
         meta.setEnchantmentGlintOverride(glow);

         // PDC markers
         PersistentDataContainer pdc = meta.getPersistentDataContainer();
         pdc.set(KEY_MARKER, PersistentDataType.BYTE, (byte) 1);
         pdc.set(KEY_TARGET, PersistentDataType.STRING, targetName);
         pdc.set(KEY_OWNER, PersistentDataType.STRING, ownerName);
      });
      return item;
   }

   /**
    * Process text with gradient tags, hex colors, and SMPS applyColors,
    * then convert to Adventure Component.
    *
    * Supports:
    * - {@code <gradient:#RRGGBB:#RRGGBB>text</gradient>}
    * - {@code <#RRGGBB:#RRGGBB>text</>}
    * - {@code #RRGGBB} standalone hex
    * - {@code &[MAIN]}, {@code &[SECONDARY]}, legacy {@code &c}, {@code &#RRGGBB} via SMPS
    */
   private static Component colorize(String text, DoAPI plugin) {
      // 1. Process gradient tags → per-char §x§R§R§G§G§B§B sequences
      String processed = applyGradientTags(text);
      // 2. Process standalone #RRGGBB hex colors
      processed = applyHexColors(processed);
      // 3. Process &[MAIN], &[SECONDARY], &#RRGGBB, legacy &c via SMPS
      String colored = plugin.applyColors(processed);
      return LegacyComponentSerializer.legacySection().deserialize(colored);
   }

   // ── Gradient processing (mirrors SM_ItemMeta logic) ──────────────

   private static String applyGradientTags(String input) {
      // First handle <gradient:...>text</gradient> (supports 2+ stops)
      String result = replaceMultiStopGradient(input, MINI_GRADIENT_PATTERN);
      // Then handle <#hex:#hex>text</> (always 2 stops)
      result = replaceTwoStopGradient(result, SHORT_GRADIENT_PATTERN);
      return result;
   }

   /**
    * Replace multi-stop gradient tags: {@code <gradient:#c1:#c2:#c3>text</gradient>}
    * Group 1 = all color stops joined by ":", Group 2 = text
    */
   private static String replaceMultiStopGradient(String input, Pattern pattern) {
      String current = input;
      while (true) {
         Matcher matcher = pattern.matcher(current);
         if (!matcher.find()) return current;
         StringBuffer sb = new StringBuffer();
         do {
            String stopsStr = matcher.group(1); // e.g. "#696969:#ffffff:#8a8a8a"
            String text = matcher.group(2);
            String[] stopParts = stopsStr.split(":");
            matcher.appendReplacement(sb, Matcher.quoteReplacement(gradientText(text, stopParts)));
         } while (matcher.find());
         matcher.appendTail(sb);
         current = sb.toString();
      }
   }

   /**
    * Replace 2-stop gradient tags: {@code <#hex:#hex>text</>}
    * Group 1 = start, Group 2 = end, Group 3 = text
    */
   private static String replaceTwoStopGradient(String input, Pattern pattern) {
      String current = input;
      while (true) {
         Matcher matcher = pattern.matcher(current);
         if (!matcher.find()) return current;
         StringBuffer sb = new StringBuffer();
         do {
            String start = matcher.group(1);
            String end = matcher.group(2);
            String text = matcher.group(3);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(gradientText(text, new String[]{start, end})));
         } while (matcher.find());
         matcher.appendTail(sb);
         current = sb.toString();
      }
   }

   private static String applyHexColors(String input) {
      Matcher matcher = HEX_PATTERN.matcher(input);
      StringBuffer sb = new StringBuffer();
      while (matcher.find()) {
         matcher.appendReplacement(sb, Matcher.quoteReplacement(toLegacyHex(matcher.group(1))));
      }
      matcher.appendTail(sb);
      return sb.toString();
   }

   /**
    * Apply a multi-stop gradient to text. Each character gets a color
    * interpolated across the given color stops.
    *
    * @param text  the text to colorize
    * @param stops array of hex color strings (2 or more)
    */
   private static String gradientText(String text, String[] stops) {
      if (text == null || text.isEmpty() || stops == null || stops.length < 2) return text != null ? text : "";
      int[] codePoints = text.codePoints().toArray();
      if (codePoints.length == 0) return "";

      // Parse all stops to RGB
      int[][] colors = new int[stops.length][];
      for (int i = 0; i < stops.length; i++) {
         colors[i] = hexToRgb(stops[i]);
      }

      int segments = stops.length - 1; // number of segments between stops
      StringBuilder builder = new StringBuilder();

      for (int i = 0; i < codePoints.length; i++) {
         // Position 0.0 to 1.0 across the entire text
         double pos = codePoints.length == 1 ? 0.0 : (double) i / (codePoints.length - 1);

         // Which segment are we in, and how far through it?
         double scaled = pos * segments;
         int seg = Math.min((int) scaled, segments - 1);
         double ratio = scaled - seg;

         int r = (int) Math.round(colors[seg][0] + (colors[seg + 1][0] - colors[seg][0]) * ratio);
         int g = (int) Math.round(colors[seg][1] + (colors[seg + 1][1] - colors[seg][1]) * ratio);
         int b = (int) Math.round(colors[seg][2] + (colors[seg + 1][2] - colors[seg][2]) * ratio);

         builder.append(toLegacyHex(String.format("%02X%02X%02X", r, g, b)));
         builder.append(Character.toChars(codePoints[i]));
      }
      return builder.toString();
   }

   private static int[] hexToRgb(String hex) {
      String n = normalizeHex(hex);
      return new int[] {
         Integer.parseInt(n.substring(0, 2), 16),
         Integer.parseInt(n.substring(2, 4), 16),
         Integer.parseInt(n.substring(4, 6), 16)
      };
   }

   private static String normalizeHex(String hex) {
      if (hex == null) return "FFFFFF";
      String n = hex.startsWith("#") ? hex.substring(1) : hex;
      return n.length() == 6 ? n.toUpperCase() : "FFFFFF";
   }

   private static String toLegacyHex(String hex) {
      String n = normalizeHex(hex);
      StringBuilder sb = new StringBuilder("§x");
      for (char c : n.toCharArray()) {
         sb.append('§').append(c);
      }
      return sb.toString();
   }

   /**
    * Check if the given ItemStack is a Voodoo item.
    */
   public static boolean isVoodoo(ItemStack item) {
      if (item == null || item.getType() != Material.TOTEM_OF_UNDYING) {
         return false;
      }
      ItemMeta meta = item.getItemMeta();
      if (meta == null) {
         return false;
      }
      return meta.getPersistentDataContainer().has(KEY_MARKER, PersistentDataType.BYTE);
   }

   /**
    * Get the target player name from a Voodoo item.
    */
   public static String getTarget(ItemStack item) {
      if (item == null) return null;
      ItemMeta meta = item.getItemMeta();
      if (meta == null) return null;
      return meta.getPersistentDataContainer().get(KEY_TARGET, PersistentDataType.STRING);
   }

   /**
    * Get the owner player name from a Voodoo item.
    */
   public static String getOwner(ItemStack item) {
      if (item == null) return null;
      ItemMeta meta = item.getItemMeta();
      if (meta == null) return null;
      return meta.getPersistentDataContainer().get(KEY_OWNER, PersistentDataType.STRING);
   }

   /**
    * Update an existing Voodoo item's custom_model_data component to match
    * the current config.  If the target has a model entry, ensures
    * custom_model_data strings[0] is set; if not, removes it.
    *
    * @param item           the Voodoo ItemStack (must pass {@link #isVoodoo})
    * @param modelsSection  the "models" section from config (nullable)
    * @return true if the item was modified
    */
   public static boolean updateCustomModelData(ItemStack item, ConfigurationSection modelsSection) {
      if (!isVoodoo(item)) return false;
      String targetName = getTarget(item);
      if (targetName == null) return false;

      boolean shouldHaveModel = isModelEnabled(modelsSection, targetName);

      // Check current custom_model_data
      CustomModelData current = item.getData(DataComponentTypes.CUSTOM_MODEL_DATA);
      boolean hasModel = current != null && !current.strings().isEmpty()
         && current.strings().get(0).equals(targetName.toLowerCase());

      if (shouldHaveModel && !hasModel) {
         item.setData(DataComponentTypes.CUSTOM_MODEL_DATA,
            CustomModelData.customModelData()
               .addString(targetName.toLowerCase())
               .build());
         return true;
      } else if (!shouldHaveModel && current != null && !current.strings().isEmpty()) {
         item.unsetData(DataComponentTypes.CUSTOM_MODEL_DATA);
         return true;
      }
      return false;
   }

   // ── Models config helpers ─────────────────────────────────────────

   /**
    * Check if a target has a custom model enabled.
    * Supports both old format (boolean: {@code target: true}) and
    * new format (object: {@code target: {enabled: true, prefix: "..."}}).
    */
   public static boolean isModelEnabled(ConfigurationSection modelsSection, String targetName) {
      if (modelsSection == null) return false;
      String key = targetName.toLowerCase();
      if (modelsSection.isConfigurationSection(key)) {
         ConfigurationSection sub = modelsSection.getConfigurationSection(key);
         return sub != null && sub.getBoolean("enabled", false);
      }
      return modelsSection.getBoolean(key, false);
   }

   /**
    * Get the custom prefix for a player from the models config.
    * Returns null if no prefix is configured.
    */
   public static String getModelPrefix(ConfigurationSection modelsSection, String targetName) {
      if (modelsSection == null) return null;
      String key = targetName.toLowerCase();
      if (modelsSection.isConfigurationSection(key)) {
         ConfigurationSection sub = modelsSection.getConfigurationSection(key);
         return sub != null ? sub.getString("prefix", null) : null;
      }
      return null;
   }

   /**
    * Resolve a styled display name for a player.
    * If the player has a custom prefix in models config, returns that prefix.
    * Otherwise returns the plain nickname.
    */
   private static String resolveStyledName(ConfigurationSection modelsSection, String playerName) {
      String prefix = getModelPrefix(modelsSection, playerName);
      return (prefix != null && !prefix.isEmpty()) ? prefix : playerName;
   }
}
