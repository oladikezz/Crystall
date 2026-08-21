package net.schalker.SMPS.modules.announces.format;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.schalker.DoAPI.DoAPI;
import net.schalker.SMPS.modules.announces.AnnouncesModule;

public class AnnounceFormatter {
    private static final Pattern HEX_AMPERSAND = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern LEGACY_AMP = Pattern.compile("&([0-9a-fk-orA-FK-OR])");
    private static final Pattern URL_PATTERN = Pattern.compile("(https?://[^\\s<]+)");

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
    private final AnnouncesModule module;
    private final MiniMessage miniMessage;

    public AnnounceFormatter(DoAPI plugin, AnnouncesModule module) {
        this.plugin = plugin;
        this.module = module;
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
            Component parsed = this.miniMessage.deserialize(text);
            return autoLink(parsed);
        } catch (Exception ex) {
            this.plugin.getDebugSystem().logWarning("Announces", "MiniMessage parse failed, using legacy fallback", ex);
            return LegacyComponentSerializer.legacySection().deserialize(this.plugin.applyColors(input));
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

    private String convertHexCodes(String text) {
        Matcher withAmp = HEX_AMPERSAND.matcher(text);
        StringBuilder ampBuilder = new StringBuilder();
        while (withAmp.find()) {
            withAmp.appendReplacement(ampBuilder, Matcher.quoteReplacement("<#" + withAmp.group(1) + ">"));
        }
        withAmp.appendTail(ampBuilder);

        // Keep raw #RRGGBB untouched so MiniMessage tags like <gradient:#a:#b> stay valid.
        return ampBuilder.toString();
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

    private Component autoLink(Component component) {
        return component.replaceText(TextReplacementConfig.builder()
            .match(URL_PATTERN)
            .replacement((matchResult, builder) -> {
                String url = matchResult.group(1);
                return Component.text(url)
                    .clickEvent(ClickEvent.openUrl(url))
                    .hoverEvent(HoverEvent.showText(Component.text(module.getMessage(
                        "alerts.link-hover",
                        "&eClick to open link"
                    ))));
            })
            .build());
    }
}

