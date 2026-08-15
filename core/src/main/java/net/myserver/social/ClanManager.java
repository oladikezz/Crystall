package net.myserver.social;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.*;

public class ClanManager {
    private static final File FILE = new File("world_data", "clans.json");
    private static final Gson gson = new Gson();
    
    public static Map<String, List<UUID>> clans = new HashMap<>();
    public static Map<UUID, String> playerClans = new HashMap<>();

    public static void init() {
        if (FILE.exists()) {
            try (FileReader reader = new FileReader(FILE)) {
                Type type = new TypeToken<Map<String, List<UUID>>>(){}.getType();
                clans = gson.fromJson(reader, type);
                if (clans == null) clans = new HashMap<>();
                
                for (Map.Entry<String, List<UUID>> entry : clans.entrySet()) {
                    for (UUID uuid : entry.getValue()) {
                        playerClans.put(uuid, entry.getKey());
                    }
                }
            } catch (Exception e) { e.printStackTrace(); }
        }
    }

    public static void save() {
        try (FileWriter writer = new FileWriter(FILE)) {
            gson.toJson(clans, writer);
        } catch (Exception e) { e.printStackTrace(); }
    }

    public static String getClan(UUID uuid) {
        return playerClans.get(uuid);
    }
    
    public static boolean createClan(UUID leader, String name) {
        if (clans.containsKey(name) || playerClans.containsKey(leader)) return false;
        
        List<UUID> members = new ArrayList<>();
        members.add(leader);
        clans.put(name, members);
        playerClans.put(leader, name);
        save();
        return true;
    }
    
    public static void addMember(UUID player, String clanName) {
        if (clans.containsKey(clanName)) {
            clans.get(clanName).add(player);
            playerClans.put(player, clanName);
            save();
        }
    }
}
