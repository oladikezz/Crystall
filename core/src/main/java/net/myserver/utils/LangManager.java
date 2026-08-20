package net.myserver.utils;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.minestom.server.entity.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

/**
 * Handles multi-language localization.
 * Reads JSON files from world_data/lang/ directory.
 */
public class LangManager {
    private static final Logger log = LoggerFactory.getLogger(LangManager.class);
    private static final File LANG_DIR = new File("world_data", "lang");
    private static final Gson gson = new Gson();
    
    private static final Map<String, Map<String, String>> languages = new HashMap<>();

    public static void init() {
        if (!LANG_DIR.exists()) {
            LANG_DIR.mkdirs();
        }
        
        File[] files = LANG_DIR.listFiles((dir, name) -> name.endsWith(".json"));
        if (files != null) {
            for (File file : files) {
                String localeName = file.getName().replace(".json", "").toLowerCase();
                try (FileReader reader = new FileReader(file)) {
                    Type type = new TypeToken<Map<String, String>>(){}.getType();
                    Map<String, String> translations = gson.fromJson(reader, type);
                    if (translations != null) {
                        languages.put(localeName, translations);
                    }
                } catch (Exception e) {
                    log.error("Failed to load language file {}: {}", file.getName(), e.getMessage());
                }
            }
        }
        
        log.info("Loaded {} language(s).", languages.size());
    }

    /**
     * Gets a translated string for a specific player based on their client locale.
     * Supports MiniMessage formatting (e.g. <green>Hello</green>).
     */
    public static Component get(Player player, String key, Object... args) {
        String locale = "en_us";
        if (player != null && player.getSettings() != null && player.getSettings().locale() != null) {
            locale = player.getSettings().locale().toString().toLowerCase();
        }
        
        Map<String, String> langMap = languages.get(locale);
        if (langMap == null || !langMap.containsKey(key)) {
            langMap = languages.get("en_us");
        }
        
        String rawText = (langMap != null && langMap.containsKey(key)) ? langMap.get(key) : "<red>Missing translation: " + key + "</red>";
        
        if (args.length > 0) {
            try {
                rawText = String.format(rawText, args);
            } catch (Exception e) {
                log.warn("Failed to format translation key {} with args", key);
            }
        }
        
        return MiniMessage.miniMessage().deserialize(rawText);
    }
}
