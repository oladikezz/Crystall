package net.myserver.social;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.myserver.storage.DatabaseManager;

import java.io.*;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class EconomyManager {
    private static final File ECO_FILE = new File("world_data", "economy.json");
    private static final Gson gson = new Gson();
    private static Map<UUID, Double> balances = new HashMap<>();

    public static void init() {
        if (!DatabaseManager.isEnabled()) {
            // File-based fallback
            if (ECO_FILE.exists()) {
                try (FileReader reader = new FileReader(ECO_FILE)) {
                    Type type = new TypeToken<Map<UUID, Double>>(){}.getType();
                    balances = gson.fromJson(reader, type);
                    if (balances == null) balances = new HashMap<>();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static void save() {
        if (DatabaseManager.isEnabled()) return; // DB saves inline
        try (FileWriter writer = new FileWriter(ECO_FILE)) {
            gson.toJson(balances, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static double getBalance(UUID uuid) {
        if (DatabaseManager.isEnabled()) {
            return DatabaseManager.getBalance(uuid.toString());
        }
        return balances.getOrDefault(uuid, 100.0);
    }

    public static void setBalance(UUID uuid, double amount) {
        if (DatabaseManager.isEnabled()) {
            DatabaseManager.setBalance(uuid.toString(), amount);
            return;
        }
        balances.put(uuid, amount);
        save();
    }

    public static void addBalance(UUID uuid, double amount) {
        setBalance(uuid, getBalance(uuid) + amount);
    }

    public static boolean removeBalance(UUID uuid, double amount) {
        double current = getBalance(uuid);
        if (current >= amount) {
            setBalance(uuid, current - amount);
            return true;
        }
        return false;
    }
}
