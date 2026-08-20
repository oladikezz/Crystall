package net.myserver.permissions;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minestom.server.entity.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RoleManager {
    private static final Logger log = LoggerFactory.getLogger(RoleManager.class);
    private static final File ROLES_FILE = new File("world_data", "roles.json");
    private static final Gson gson = new Gson();
    private static final Map<UUID, String> roles = new ConcurrentHashMap<>();

    public static void init() {
        if (ROLES_FILE.exists()) {
            try (FileReader reader = new FileReader(ROLES_FILE)) {
                Type type = new TypeToken<Map<String, String>>(){}.getType();
                Map<String, String> raw = gson.fromJson(reader, type);
                if (raw != null) {
                    roles.clear();
                    raw.forEach((k, v) -> roles.put(UUID.fromString(k), v));
                }
            } catch (Exception e) {
                log.error("[RoleManager] Ошибка загрузки ролей: {}", e.getMessage());
            }
        } else {
            if (ROLES_FILE.getParentFile() != null) {
                ROLES_FILE.getParentFile().mkdirs();
            }
        }
    }

    public static void save() {
        try {
            if (ROLES_FILE.getParentFile() != null && !ROLES_FILE.getParentFile().exists()) {
                ROLES_FILE.getParentFile().mkdirs();
            }
            try (FileWriter writer = new FileWriter(ROLES_FILE)) {
                Map<String, String> raw = new ConcurrentHashMap<>();
                roles.forEach((k, v) -> raw.put(k.toString(), v));
                gson.toJson(raw, writer);
            }
        } catch (Exception e) {
            log.error("[RoleManager] Ошибка сохранения ролей: {}", e.getMessage());
        }
    }

    public static void assignRole(Player player) {
        // Роли управляются через файл или консоль
    }

    public static String getRole(UUID uuid) {
        return roles.getOrDefault(uuid, "player");
    }

    public static void setRole(UUID uuid, String role) {
        roles.put(uuid, role.toLowerCase());
        save();
    }

    public static boolean isAdmin(Player player) {
        if (player == null) return false;
        String role = getRole(player.getUuid());
        return "admin".equalsIgnoreCase(role);
    }

    public static boolean isStaff(Player player) {
        if (player == null) return false;
        String role = getRole(player.getUuid());
        return "admin".equalsIgnoreCase(role) || "moderator".equalsIgnoreCase(role);
    }

    public static boolean checkPermission(Player player, String permission) {
        if (isAdmin(player)) return true;
        if ("moderator".equalsIgnoreCase(permission) && isStaff(player)) return true;
        return false;
    }
}
