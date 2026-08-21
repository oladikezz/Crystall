package ru.lor.watcher.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public class ColorUtil {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacyAmpersand();
    private static final LegacyComponentSerializer SECTION_SERIALIZER = LegacyComponentSerializer.legacySection();
    private static final PlainTextComponentSerializer PLAIN_SERIALIZER = PlainTextComponentSerializer.plainText();

    /**
     * Parses a string into an Adventure Component supporting MiniMessage and Legacy color codes.
     */
    public static Component parse(String input) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }

        // Replace non-standard <purple> tags with <dark_purple>
        String sanitized = input.replace("<purple>", "<dark_purple>").replace("</purple>", "</dark_purple>");

        // Convert legacy § and & codes to MiniMessage tags so both can be mixed in 1 string
        String converted = convertLegacyToMiniMessage(sanitized);

        try {
            return MINI_MESSAGE.deserialize(converted);
        } catch (Exception e) {
            try {
                return LEGACY_SERIALIZER.deserialize(sanitized.replace("§", "&"));
            } catch (Exception ex) {
                return Component.text(input);
            }
        }
    }

    /**
     * Neutralises MiniMessage tags and legacy colour codes in untrusted text so it can be
     * safely embedded in a string that is later passed to {@link #parse(String)}.
     */
    public static String escape(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        String stripped = input.replaceAll("(?i)[§&]([0-9a-fk-or])", "$1");
        return MINI_MESSAGE.escapeTags(stripped);
    }

    private static String convertLegacyToMiniMessage(String input) {
        if (input == null) return "";
        String s = input.replace('§', '&');
        return s.replace("&0", "<black>")
                .replace("&1", "<dark_blue>")
                .replace("&2", "<dark_green>")
                .replace("&3", "<dark_aqua>")
                .replace("&4", "<dark_red>")
                .replace("&5", "<dark_purple>")
                .replace("&6", "<gold>")
                .replace("&7", "<gray>")
                .replace("&8", "<dark_gray>")
                .replace("&9", "<blue>")
                .replace("&a", "<green>")
                .replace("&b", "<aqua>")
                .replace("&c", "<red>")
                .replace("&d", "<light_purple>")
                .replace("&e", "<yellow>")
                .replace("&f", "<white>")
                .replace("&k", "<obfuscated>")
                .replace("&l", "<b>")
                .replace("&m", "<strikethrough>")
                .replace("&n", "<u>")
                .replace("&o", "<i>")
                .replace("&r", "<reset>");
    }

    /**
     * Serializes a Component to legacy section (§) formatted string.
     */
    public static String toLegacy(Component component) {
        if (component == null) return "";
        return SECTION_SERIALIZER.serialize(component);
    }

    /**
     * Sends an action bar message (displayed right under the player's crosshair / above hotbar)
     * and refreshes it for durationSeconds so it stays clearly visible on screen.
     */
    public static void sendActionBarPersistent(org.bukkit.plugin.Plugin plugin, org.bukkit.entity.Player player, String miniMessageText, int durationSeconds) {
        if (player == null || !player.isOnline()) return;

        Component comp = parse(miniMessageText);
        player.sendActionBar(comp);

        for (int i = 1; i < durationSeconds; i++) {
            final int delaySec = i;
            player.getScheduler().runDelayed(plugin, task -> {
                if (player.isOnline()) {
                    player.sendActionBar(comp);
                }
            }, null, delaySec * 20L);
        }
    }
}
