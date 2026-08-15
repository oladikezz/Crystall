package net.myserver;

import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Map;

public class Config {
    public int port = 25565;
    public String motd = "A Minestom Server";
    public int maxPlayers = 100;
    public String proxyMode = "none";
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
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return config;
    }
}
