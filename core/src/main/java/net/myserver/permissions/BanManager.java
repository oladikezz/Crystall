package net.myserver.permissions;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BanManager {
    private static final Logger log = LoggerFactory.getLogger(BanManager.class);
    private static final File BANS_FILE = new File("world_data", "banned_players.json");
    private static final Gson gson = new Gson();
    private static final Map<String, String> bannedPlayers = new ConcurrentHashMap<>();

    public static void init() {
        if (BANS_FILE.exists()) {
            try (FileReader reader = new FileReader(BANS_FILE)) {
                Type type = new TypeToken<Map<String, String>>(){}.getType();
                Map<String, String> raw = gson.fromJson(reader, type);
                if (raw != null) {
                    bannedPlayers.clear();
                    bannedPlayers.putAll(raw);
                }
            } catch (Exception e) {
                log.error("[BanManager] Ошибка загрузки банов: {}", e.getMessage());
            }
        }
    }

    public static void save() {
        try {
            if (BANS_FILE.getParentFile() != null && !BANS_FILE.getParentFile().exists()) {
                BANS_FILE.getParentFile().mkdirs();
            }
            try (FileWriter writer = new FileWriter(BANS_FILE)) {
                gson.toJson(bannedPlayers, writer);
            }
        } catch (Exception e) {
            log.error("[BanManager] Ошибка сохранения банов: {}", e.getMessage());
        }
    }

    public static void ban(String uuid, String reason) {
        bannedPlayers.put(uuid, reason);
        save();
    }

    public static void unban(String uuid) {
        bannedPlayers.remove(uuid);
        save();
    }

    public static boolean isBanned(String uuid) {
        return bannedPlayers.containsKey(uuid);
    }
    
    public static String getReason(String uuid) {
        return bannedPlayers.getOrDefault(uuid, "Вы были забанены на этом сервере.");
    }
}
