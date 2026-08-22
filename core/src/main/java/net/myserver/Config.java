package net.myserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Map;

public class Config {
    private static final Logger log = LoggerFactory.getLogger(Config.class);

    public int port = 25565;
    public String motd = "A Minestom Server (Crystall Core)";
    public int maxPlayers = 100;
    public String proxyMode = "none"; // "none", "velocity", "bungeecord"
    public boolean onlineMode = false; // Mojang online-mode auth if proxyMode == "none"
    public String velocitySecret = "";
    
    // Database
    public boolean dbEnabled = false;
    public String dbHost = "localhost";
    public int dbPort = 5432;
    public String dbName = "crystall";
    public String dbUser = "crystall";
    public String dbPassword = "crystall";

    // Resource Pack
    public String resourcePackUrl = "";
    public String resourcePackHash = "";
    public boolean resourcePackRequired = false;

    // Web Map Settings
    public boolean webMapEnabled = true;
    public int webMapPort = 8080;
    public boolean webMapHideCoordinates = false;

    // Modules Settings
    public Map<String, Object> moduleSettings = new java.util.HashMap<>();

    public boolean isModuleEnabled(String moduleId, boolean defaultValue) {
        if (moduleSettings.containsKey(moduleId)) {
            Object val = moduleSettings.get(moduleId);
            if (val instanceof Boolean b) return b;
            if (val instanceof Map<?, ?> map && map.containsKey("enabled")) {
                Object enabledVal = map.get("enabled");
                if (enabledVal instanceof Boolean b) return b;
                return Boolean.parseBoolean(String.valueOf(enabledVal));
            }
            return Boolean.parseBoolean(String.valueOf(val));
        }
        return defaultValue;
    }

    public static Config load(String path) {
        Config config = new Config();
        File file = new File(path);
        if (!file.exists()) {
            return config;
        }

        try (InputStream inputStream = new FileInputStream(file)) {
            Yaml yaml = new Yaml();
            Map<String, Object> data = yaml.load(inputStream);

            if (data != null) {
                if (data.containsKey("port")) {
                    config.port = ((Number) data.get("port")).intValue();
                }
                if (data.containsKey("motd")) {
                    config.motd = String.valueOf(data.get("motd"));
                }
                if (data.containsKey("max_players")) {
                    config.maxPlayers = ((Number) data.get("max_players")).intValue();
                }
                if (data.containsKey("proxy_mode")) {
                    config.proxyMode = String.valueOf(data.get("proxy_mode"));
                }
                if (data.containsKey("online_mode")) {
                    config.onlineMode = Boolean.parseBoolean(String.valueOf(data.get("online_mode")));
                }
                if (data.containsKey("velocity_secret")) {
                    config.velocitySecret = String.valueOf(data.get("velocity_secret"));
                }
                if (data.containsKey("db_enabled")) {
                    config.dbEnabled = Boolean.parseBoolean(String.valueOf(data.get("db_enabled")));
                }
                if (data.containsKey("db_host")) {
                    config.dbHost = String.valueOf(data.get("db_host"));
                }
                if (data.containsKey("db_port")) {
                    config.dbPort = ((Number) data.get("db_port")).intValue();
                }
                if (data.containsKey("db_name")) {
                    config.dbName = String.valueOf(data.get("db_name"));
                }
                if (data.containsKey("db_user")) {
                    config.dbUser = String.valueOf(data.get("db_user"));
                }
                if (data.containsKey("db_password")) {
                    config.dbPassword = String.valueOf(data.get("db_password"));
                }
                if (data.containsKey("resource_pack_url")) {
                    config.resourcePackUrl = String.valueOf(data.get("resource_pack_url"));
                }
                if (data.containsKey("resource_pack_hash")) {
                    config.resourcePackHash = String.valueOf(data.get("resource_pack_hash"));
                }
                if (data.containsKey("resource_pack_required")) {
                    config.resourcePackRequired = Boolean.parseBoolean(String.valueOf(data.get("resource_pack_required")));
                }
                if (data.containsKey("web_map_enabled")) {
                    config.webMapEnabled = Boolean.parseBoolean(String.valueOf(data.get("web_map_enabled")));
                }
                if (data.containsKey("web_map_port")) {
                    config.webMapPort = ((Number) data.get("web_map_port")).intValue();
                }
                if (data.containsKey("web_map_hide_coordinates")) {
                    config.webMapHideCoordinates = Boolean.parseBoolean(String.valueOf(data.get("web_map_hide_coordinates")));
                }
                if (data.containsKey("modules") && data.get("modules") instanceof Map<?, ?> mods) {
                    for (Map.Entry<?, ?> entry : mods.entrySet()) {
                        config.moduleSettings.put(String.valueOf(entry.getKey()).toLowerCase(), entry.getValue());
                    }
                }
            }
        } catch (Exception e) {
            log.error("[Config] Ошибка чтения конфигурации {}: {}", path, e.getMessage());
        }

        return config;
    }
}
