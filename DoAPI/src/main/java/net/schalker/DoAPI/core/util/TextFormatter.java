package net.schalker.DoAPI.core.util;

import net.kyori.adventure.text.format.TextColor;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TextFormatter {

    private static final String LOWERCASE_CHARS = "abcdefghijklmnopqrstuvwxyz";
    private static final String TINY_CAPS_CHARS =
            "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍ"
            + "ɴᴏᴘϙʀꜱᴛᴜᴠᴡˣʏᴢ";

    private static final Pattern HEX_PATTERN = Pattern.compile("&?#([A-Fa-f0-9]{6})");
    private static final String LEGACY_CODES = "0123456789abcdefklmnorABCDEFKLMNOR";

    public static String colorize(String text) {
        if (text == null || text.isEmpty()) {
            return text == null ? "" : text;
        }

        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuilder builder = new StringBuilder(text.length() + 32);
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder(14).append('§').append('x');
            for (int i = 0; i < hex.length(); i++) {
                replacement.append('§').append(hex.charAt(i));
            }
            matcher.appendReplacement(builder, Matcher.quoteReplacement(replacement.toString()));
        }
        matcher.appendTail(builder);

        return translateLegacyCodes(builder);
    }

    private static String translateLegacyCodes(StringBuilder builder) {
        for (int i = 0; i < builder.length() - 1; i++) {
            if (builder.charAt(i) == '&' && LEGACY_CODES.indexOf(builder.charAt(i + 1)) > -1) {
                builder.setCharAt(i, '§');
                builder.setCharAt(i + 1, Character.toLowerCase(builder.charAt(i + 1)));
            }
        }
        return builder.toString();
    }

    public static String toTinyCaps(String text) {
        if (text == null || text.isEmpty()) {
            return text == null ? "" : text;
        }

        String lower = text.toLowerCase(Locale.ROOT);
        StringBuilder builder = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char current = lower.charAt(i);
            int index = LOWERCASE_CHARS.indexOf(current);
            builder.append(index > -1 ? TINY_CAPS_CHARS.charAt(index) : current);
        }
        return builder.toString();
    }

    public static TextColor hexToTextColor(String hex) {
        if (hex == null) {
            return null;
        }

        String normalized = hex.trim();
        if (normalized.startsWith("&")) {
            normalized = normalized.substring(1);
        }
        if (!normalized.startsWith("#")) {
            normalized = "#" + normalized;
        }
        if (normalized.length() != 7) {
            return null;
        }

        try {
            return TextColor.fromHexString(normalized);
        } catch (Exception e) {
            return null;
        }
    }

    public static String hexToLegacyColor(String hex) {
        TextColor color = hexToTextColor(hex);
        if (color == null) {
            return "§f";
        }
        return getClosestMinecraftColor(color.red(), color.green(), color.blue());
    }

    private static String getClosestMinecraftColor(int red, int green, int blue) {
        int[][] palette = {
                {0, 0, 0}, {0, 0, 170}, {0, 170, 0}, {0, 170, 170},
                {170, 0, 0}, {170, 0, 170}, {255, 170, 0}, {170, 170, 170},
                {85, 85, 85}, {85, 85, 255}, {85, 255, 85}, {85, 255, 255},
                {255, 85, 85}, {255, 85, 255}, {255, 255, 85}, {255, 255, 255}
        };
        String codes = "0123456789abcdef";

        int best = 0;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < palette.length; i++) {
            double deltaRed = red - palette[i][0];
            double deltaGreen = green - palette[i][1];
            double deltaBlue = blue - palette[i][2];
            double distance = deltaRed * deltaRed + deltaGreen * deltaGreen + deltaBlue * deltaBlue;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return "§" + codes.charAt(best);
    }
}
