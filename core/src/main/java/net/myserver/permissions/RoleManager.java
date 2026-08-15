package net.myserver.permissions;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minestom.server.entity.Player;
import net.minestom.server.permission.Permission;

import java.io.*;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RoleManager {
    private static final File ROLES_FILE = new File("world_data", "roles.json");
    private static final Gson gson = new Gson();
    private static Map<UUID, String> roles = new HashMap<>();

    public static void init() {
        if (ROLES_FILE.exists()) {
            try (FileReader reader = new FileReader(ROLES_FILE)) {
                Type type = new TypeToken<Map<UUID, String>>(){}.getType();
                roles = gson.fromJson(reader, type);
                if (roles == null) roles = new HashMap<>();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            ROLES_FILE.getParentFile().mkdirs();
        }
    }

    public static void save() {
        try (FileWriter writer = new FileWriter(ROLES_FILE)) {
            gson.toJson(roles, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void assignRole(Player player) {
        if (roles.isEmpty()) {
            // Первый игрок на сервере автоматически становится админом
            roles.put(player.getUuid(), "admin");
            save();
        }
        
        String role = roles.getOrDefault(player.getUuid(), "player");
        
        if (role.equals("admin")) {
            player.addPermission(new Permission("command.gamemode"));
            player.addPermission(new Permission("command.give"));
            player.addPermission(new Permission("command.tp"));
            player.addPermission(new Permission("command.kick"));
            player.addPermission(new Permission("command.ban"));
        } else if (role.equals("moderator")) {
            player.addPermission(new Permission("command.tp"));
            player.addPermission(new Permission("command.kick"));
            player.addPermission(new Permission("command.ban"));
        }
    }

    public static String getRole(UUID uuid) {
        return roles.getOrDefault(uuid, "player");
    }
}
