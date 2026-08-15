package net.myserver.permissions;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

public class BanManager {
    private static final File BANS_FILE = new File("world_data", "banned_players.json");
    private static final Gson gson = new Gson();
    private static Map<String, String> bannedPlayers = new HashMap<>();

    public static void init() {
        if (BANS_FILE.exists()) {
            try (FileReader reader = new FileReader(BANS_FILE)) {
                Type type = new TypeToken<Map<String, String>>(){}.getType();
                bannedPlayers = gson.fromJson(reader, type);
                if (bannedPlayers == null) bannedPlayers = new HashMap<>();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static void save() {
        try (FileWriter writer = new FileWriter(BANS_FILE)) {
            gson.toJson(bannedPlayers, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void ban(String uuid, String reason) {
        bannedPlayers.put(uuid, reason);
        save();
    }

    public static boolean isBanned(String uuid) {
        return bannedPlayers.containsKey(uuid);
    }
    
    public static String getReason(String uuid) {
        return bannedPlayers.getOrDefault(uuid, "Вы были забанены на этом сервере.");
    }
}
