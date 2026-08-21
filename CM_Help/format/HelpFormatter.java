package net.schalker.SMPS.modules.help.format;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.schalker.DoAPI.DoAPI;

public class HelpFormatter {
   private static final Pattern HEX_AMPERSAND = Pattern.compile("&#([A-Fa-f0-9]{6})");
   private static final Pattern LEGACY_AMP = Pattern.compile("&([0-9a-fk-orA-FK-OR])");
   private static final Pattern MINIMESSAGE_TAG = Pattern.compile("<[^>]+>");

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
   private final MiniMessage miniMessage;

   public HelpFormatter(DoAPI plugin) {
      this.plugin = plugin;
      this.miniMessage = MiniMessage.miniMessage();
   }

   public Component format(String input) {
      if (input == null || input.isBlank()) {
         return Component.empty();
      }

      try {
         String text = replaceThemePlaceholders(input);
         text = convertHexCodes(text);
         text = convertLegacyCodes(text);
         return this.miniMessage.deserialize(text);
      } catch (Exception ex) {
         this.plugin.getDebugSystem().logWarning("Help", "MiniMessage parse failed, using plain fallback", ex);
         return Component.text(toPlain(input));
      }
   }

   public String toPlain(String input) {
      if (input == null || input.isBlank()) {
         return "";
      }

      String text = replaceThemePlaceholders(input);
      text = text.replace("\\n", "\n");
      text = MINIMESSAGE_TAG.matcher(text).replaceAll("");
      return this.plugin.applyColors(text);
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

   private String convertHexCodes(String text) {
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

