package net.myserver.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.item.ItemComponent;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

public class PlayerDataManager {
    private static final File playerFolder = new File("world_data", "players");
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static void init() {
        if (!playerFolder.exists()) playerFolder.mkdirs();
    }

    public static void savePlayer(Player player) {
        // Serialize inventory to JSON string
        JsonArray inv = new JsonArray();
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItemStack(i);
            if (!item.isAir()) {
                JsonObject itemObj = new JsonObject();
                itemObj.addProperty("slot", i);
                itemObj.addProperty("material", item.material().name());
                itemObj.addProperty("amount", item.amount());
                if (item.has(ItemComponent.DAMAGE)) {
                    itemObj.addProperty("damage", item.get(ItemComponent.DAMAGE, 0));
                }
                inv.add(itemObj);
            }
        }

        // Try database first
        if (DatabaseManager.isEnabled()) {
            DatabaseManager.savePlayerData(
                    player.getUuid().toString(),
                    player.getUsername(),
                    player.getHealth(),
                    player.getPosition().x(), player.getPosition().y(), player.getPosition().z(),
                    player.getPosition().yaw(), player.getPosition().pitch(),
                    gson.toJson(inv)
            );
            return;
        }

        // Fallback to file
        JsonObject json = new JsonObject();
        json.addProperty("health", player.getHealth());
        
        JsonObject pos = new JsonObject();
        pos.addProperty("x", player.getPosition().x());
        pos.addProperty("y", player.getPosition().y());
        pos.addProperty("z", player.getPosition().z());
        pos.addProperty("yaw", player.getPosition().yaw());
        pos.addProperty("pitch", player.getPosition().pitch());
        json.add("position", pos);
        json.add("inventory", inv);
        
        try (FileWriter writer = new FileWriter(new File(playerFolder, player.getUuid().toString() + ".json"))) {
            gson.toJson(json, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static boolean loadPlayer(Player player) {
        // Try database first
        if (DatabaseManager.isEnabled()) {
            JsonObject json = DatabaseManager.loadPlayerData(player.getUuid().toString());
            if (json != null) {
                applyPlayerData(player, json);
                return true;
            }
            return false;
        }

        // Fallback to file
        File file = new File(playerFolder, player.getUuid().toString() + ".json");
        if (!file.exists()) return false;
        
        try (FileReader reader = new FileReader(file)) {
            JsonObject json = gson.fromJson(reader, JsonObject.class);
            applyPlayerData(player, json);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    private static void applyPlayerData(Player player, JsonObject json) {
        if (json.has("health")) {
            player.setHealth(json.get("health").getAsFloat());
        }
        
        if (json.has("position")) {
            JsonObject pos = json.getAsJsonObject("position");
            player.setRespawnPoint(new Pos(
                pos.get("x").getAsDouble(), pos.get("y").getAsDouble(), pos.get("z").getAsDouble(),
                pos.get("yaw").getAsFloat(), pos.get("pitch").getAsFloat()
            ));
        }
        
        if (json.has("inventory")) {
            JsonArray inv = json.getAsJsonArray("inventory");
            player.getInventory().clear();
            for (int i = 0; i < inv.size(); i++) {
                JsonObject itemObj = inv.get(i).getAsJsonObject();
                Material mat = Material.fromNamespaceId(itemObj.get("material").getAsString());
                if (mat != null) {
                    ItemStack item = ItemStack.of(mat, itemObj.get("amount").getAsInt());
                    if (itemObj.has("damage")) {
                        item = item.with(ItemComponent.DAMAGE, itemObj.get("damage").getAsInt());
                    }
                    player.getInventory().setItemStack(itemObj.get("slot").getAsInt(), item);
                }
            }
        }
    }
}
