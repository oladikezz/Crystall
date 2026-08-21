package net.schalker.SMPS.modules.cosmetics.managers;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.util.TextFormatter;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

public class MessageManager {
    private final DoAPI plugin;
    private String prefix;
    private static final Pattern NAMED_COLOR_PATTERN = Pattern.compile("&\\[([A-Za-z0-9_.-]+)]");

    public MessageManager(DoAPI plugin) {
        this.plugin = plugin;
        this.reload();
    }

    public void reload() {
        YamlConfiguration config = this.plugin.getModuleManager().loadModuleConfig("SM_cosmetics", "messages.yml");
        this.prefix = config != null ? config.getString("prefix", "&6[&eCosmetics&6]&r ") : "&6[&eCosmetics&6]&r ";
    }

    public String getRaw(String key) {
        YamlConfiguration config = this.plugin.getModuleManager().loadModuleConfig("SM_cosmetics", "messages.yml");
        if (config == null) {
            return key;
        }
        String value = config.getString(key);
        if (value != null) {
            return value;
        }

        String[] parts = key.split("\\.");
        Object current = config.get(parts[0]);
        for (int i = 1; i < parts.length && current != null; i++) {
            if (current instanceof ConfigurationSection section) {
                current = section.get(parts[i]);
            } else {
                return key;
            }
        }
        return current != null ? current.toString() : key;
    }

    public String getRaw(String key, String... replacements) {
        String message = this.getRaw(key);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            message = message.replace("{" + replacements[i] + "}", replacements[i + 1]);
        }
        return message;
    }

    public Component get(String key) {
        return this.colorize(this.getRaw(key));
    }

    public Component get(String key, String... replacements) {
        return this.colorize(this.getRaw(key, replacements));
    }

    public void send(Player player, String key) {
        player.sendMessage(this.colorize(this.prefix + this.getRaw(key)));
    }

    public void send(Player player, String key, String... replacements) {
        player.sendMessage(this.colorize(this.prefix + this.getRaw(key, replacements)));
    }

    public void send(CommandSender sender, String key) {
        sender.sendMessage(this.colorize(this.prefix + this.getRaw(key)));
    }

    public void send(CommandSender sender, String key, String... replacements) {
        sender.sendMessage(this.colorize(this.prefix + this.getRaw(key, replacements)));
    }

    public void sendRaw(Player player, String key) {
        player.sendMessage(this.colorize(this.getRaw(key)));
    }

    public void sendRaw(Player player, String key, String... replacements) {
        player.sendMessage(this.colorize(this.getRaw(key, replacements)));
    }

    public Component colorize(String text) {
        if (text == null) {
            return Component.empty();
        }
        String normalized = this.decodeMojibake(text);
        String withNamedColors = this.resolveNamedColors(normalized);
        String formatted = TextFormatter.colorize(withNamedColors);
        return LegacyComponentSerializer.legacySection().deserialize(formatted);
    }

    public String getPrefix() {
        return this.prefix;
    }

    public String getCategoryName(String categoryId) {
        return this.getRaw("categories." + categoryId);
    }

    public String getRarityName(String rarityId) {
        return this.getRaw("rarity." + rarityId);
    }

    private String decodeMojibake(String text) {
        if (text == null || !(text.contains("Ã") || text.contains("Ã‘") || text.contains("Ã¢") || text.contains("Ãƒ"))) {
            return this.sanitize(text);
        }
        try {
            byte[] bytes = text.getBytes(Charset.forName("Windows-1252"));
            return this.sanitize(new String(bytes, StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            return this.sanitize(text);
        }
    }

    private String sanitize(String text) {
        if (text == null) {
            return null;
        }
        return text
            .replace("\uFFFD", "")
            .replace("âš™ ", "")
            .replace("âœ– ", "")
            .replace("âœ“ ", "")
            .replace("â—‰ ", "")
            .replace("â—Ž ", "")
            .replace("â—ˆ ", "")
            .replace("â† ", "")
            .replace(" â†’", "")
            .replace("â†’ ", "");
    }

    private String resolveNamedColors(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        YamlConfiguration moduleConfig = this.plugin.getModuleManager().loadModuleConfig("SM_cosmetics", "config.yml");
        FileConfiguration config = this.plugin.getConfigManager().getConfig();
        if (moduleConfig == null && config == null) {
            return text;
        }

        Matcher matcher = NAMED_COLOR_PATTERN.matcher(text);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String token = matcher.group(1);
            String replacement = this.findNamedColor(moduleConfig, config, token);
            if (replacement == null || replacement.isBlank()) {
                matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(0)));
                continue;
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private String findNamedColor(FileConfiguration moduleConfig, FileConfiguration config, String token) {
        if (config != null) {
            if (token.equalsIgnoreCase("MAIN")) {
                String main = config.getString("main-color");
                if (main != null && !main.isBlank()) {
                    return this.normalizeColor(main);
                }
            }
            if (token.equalsIgnoreCase("SECONDARY")) {
                String secondary = config.getString("secondary-color");
                if (secondary != null && !secondary.isBlank()) {
                    return this.normalizeColor(secondary);
                }
            }
        }

        String[] keys = {token, token.toUpperCase(), token.toLowerCase()};
        String[] prefixes = {"", "colors.", "palette.", "theme.colors.", "format.colors.", "chat.colors."};

        for (String prefix : prefixes) {
            for (String key : keys) {
                if (moduleConfig != null) {
                    String localValue = moduleConfig.getString(prefix + key);
                    if (localValue != null && !localValue.isBlank()) {
                        return this.normalizeColor(localValue);
                    }
                }
                if (config != null) {
                    String globalValue = config.getString(prefix + key);
                    if (globalValue != null && !globalValue.isBlank()) {
                        return this.normalizeColor(globalValue);
                    }
                }
            }
        }
        return null;
    }

    private String normalizeColor(String value) {
        String color = value == null ? "" : value.trim();
        if (color.matches("^#[0-9A-Fa-f]{6}$")) {
            return "&" + color;
        }
        return color;
    }
}
