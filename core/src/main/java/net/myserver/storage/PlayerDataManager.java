package net.myserver.storage;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.minestom.server.component.DataComponents;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.inventory.PlayerInventory;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.utils.Unit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Сохранение и загрузка состояния игрока (позиция, здоровье, голод, инвентарь).
 */
public class PlayerDataManager {
    private static final Logger log = LoggerFactory.getLogger(PlayerDataManager.class);
    private static final File DATA_DIR = new File("world_data", "players");
    private static final Gson gson = new Gson();
    private static final GsonComponentSerializer componentSerializer = GsonComponentSerializer.gson();

    public static void init() {
        if (!DATA_DIR.exists()) {
            DATA_DIR.mkdirs();
        }
    }

    public static void savePlayer(Player player) {
        if (player == null) return;
        File file = new File(DATA_DIR, player.getUuid().toString() + ".json");

        try (FileWriter writer = new FileWriter(file)) {
            JsonObject json = new JsonObject();
            json.addProperty("username", player.getUsername());
            json.addProperty("gamemode", player.getGameMode().name());
            json.addProperty("health", player.getHealth());
            json.addProperty("food", player.getFood());

            // Сохранение координат
            Pos pos = player.getPosition();
            JsonObject posObj = new JsonObject();
            posObj.addProperty("x", pos.x());
            posObj.addProperty("y", pos.y());
            posObj.addProperty("z", pos.z());
            posObj.addProperty("yaw", pos.yaw());
            posObj.addProperty("pitch", pos.pitch());
            json.add("position", posObj);

            // Сохранение инвентаря
            JsonArray invArray = new JsonArray();
            PlayerInventory inv = player.getInventory();
            for (int i = 0; i < inv.getSize(); i++) {
                ItemStack item = inv.getItemStack(i);
                if (!item.isAir()) {
                    JsonObject itemObj = serializeItemStack(item);
                    itemObj.addProperty("slot", i);
                    invArray.add(itemObj);
                }
            }
            json.add("inventory", invArray);

            gson.toJson(json, writer);
        } catch (Exception e) {
            log.error("[PlayerDataManager] Ошибка сохранения игрока {}: {}", player.getUsername(), e.getMessage());
        }
    }

    public static boolean loadPlayer(Player player) {
        if (player == null) return false;
        File file = new File(DATA_DIR, player.getUuid().toString() + ".json");
        if (!file.exists()) return false;

        try (FileReader reader = new FileReader(file)) {
            JsonObject json = gson.fromJson(reader, JsonObject.class);
            if (json == null) return false;

            if (json.has("gamemode")) {
                player.setGameMode(GameMode.valueOf(json.get("gamemode").getAsString()));
            }
            if (json.has("health")) {
                player.setHealth(json.get("health").getAsFloat());
            }
            if (json.has("food")) {
                player.setFood(json.get("food").getAsInt());
            }

            if (json.has("position")) {
                JsonObject posObj = json.getAsJsonObject("position");
                double x = posObj.get("x").getAsDouble();
                double y = posObj.get("y").getAsDouble();
                double z = posObj.get("z").getAsDouble();
                float yaw = posObj.get("yaw").getAsFloat();
                float pitch = posObj.get("pitch").getAsFloat();
                player.setRespawnPoint(new Pos(x, y, z, yaw, pitch));
            }

            if (json.has("inventory")) {
                player.getInventory().clear();
                JsonArray invArray = json.getAsJsonArray("inventory");
                for (JsonElement el : invArray) {
                    JsonObject itemObj = el.getAsJsonObject();
                    int slot = itemObj.get("slot").getAsInt();
                    ItemStack item = deserializeItemStack(itemObj);
                    player.getInventory().setItemStack(slot, item);
                }
            }

            return true;
        } catch (Exception e) {
            log.error("[PlayerDataManager] Ошибка загрузки игрока {}: {}", player.getUsername(), e.getMessage());
            return false;
        }
    }

    private static JsonObject serializeItemStack(ItemStack item) {
        JsonObject obj = new JsonObject();
        obj.addProperty("material", item.material().name());
        obj.addProperty("amount", item.amount());

        if (item.has(DataComponents.DAMAGE)) {
            obj.addProperty("damage", item.get(DataComponents.DAMAGE));
        }
        if (item.has(DataComponents.UNBREAKABLE)) {
            obj.addProperty("unbreakable", true);
        }
        if (item.has(DataComponents.CUSTOM_NAME)) {
            Component customName = item.get(DataComponents.CUSTOM_NAME);
            if (customName != null) {
                obj.addProperty("customName", componentSerializer.serialize(customName));
            }
        }
        if (item.has(DataComponents.LORE)) {
            List<Component> lore = item.get(DataComponents.LORE);
            if (lore != null && !lore.isEmpty()) {
                JsonArray loreArr = new JsonArray();
                for (Component line : lore) {
                    loreArr.add(componentSerializer.serialize(line));
                }
                obj.add("lore", loreArr);
            }
        }

        return obj;
    }

    private static ItemStack deserializeItemStack(JsonObject obj) {
        if (obj == null || !obj.has("material")) return ItemStack.AIR;

        String matStr = obj.get("material").getAsString().toLowerCase();
        if (!matStr.contains(":")) matStr = "minecraft:" + matStr;
        Material mat = Material.fromKey(matStr);
        if (mat == null) return ItemStack.AIR;

        int amount = obj.has("amount") ? obj.get("amount").getAsInt() : 1;
        ItemStack item = ItemStack.of(mat, amount);

        if (obj.has("damage")) {
            item = item.with(DataComponents.DAMAGE, obj.get("damage").getAsInt());
        }
        if (obj.has("unbreakable") && obj.get("unbreakable").getAsBoolean()) {
            item = item.with(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        }
        if (obj.has("customName")) {
            try {
                Component name = componentSerializer.deserialize(obj.get("customName").getAsString());
                item = item.with(DataComponents.CUSTOM_NAME, name);
            } catch (Exception ignored) {}
        }
        if (obj.has("lore")) {
            try {
                JsonArray loreArr = obj.getAsJsonArray("lore");
                List<Component> lore = new ArrayList<>();
                for (JsonElement el : loreArr) {
                    lore.add(componentSerializer.deserialize(el.getAsString()));
                }
                item = item.with(DataComponents.LORE, lore);
            } catch (Exception ignored) {}
        }

        return item;
    }
}
