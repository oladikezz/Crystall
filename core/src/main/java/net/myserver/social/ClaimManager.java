package net.myserver.social;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.PlayerBlockBreakEvent;
import net.minestom.server.event.player.PlayerBlockPlaceEvent;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ClaimManager {
    private static final File FILE = new File("world_data", "claims.json");
    private static final Gson gson = new Gson();
    
    // ChunkIndex -> Clan Name OR Player UUID
    public static Map<Long, String> claims = new HashMap<>();

    public static long getChunkIndex(int chunkX, int chunkZ) {
        return (((long)chunkX) << 32) | (chunkZ & 0xffffffffL);
    }

    public static void init() {
        if (FILE.exists()) {
            try (FileReader reader = new FileReader(FILE)) {
                Type type = new TypeToken<Map<Long, String>>(){}.getType();
                claims = gson.fromJson(reader, type);
                if (claims == null) claims = new HashMap<>();
            } catch (Exception e) { e.printStackTrace(); }
        }
    }

    public static void save() {
        try (FileWriter writer = new FileWriter(FILE)) {
            gson.toJson(claims, writer);
        } catch (Exception e) { e.printStackTrace(); }
    }

    public static void register(GlobalEventHandler handler) {
        handler.addListener(PlayerBlockBreakEvent.class, event -> {
            if (!canModify(event.getPlayer(), event.getBlockPosition())) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(net.myserver.utils.LangManager.get(event.getPlayer(), "claim.denied"));
            }
        });

        handler.addListener(PlayerBlockPlaceEvent.class, event -> {
            if (!canModify(event.getPlayer(), event.getBlockPosition())) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(net.myserver.utils.LangManager.get(event.getPlayer(), "claim.denied"));
            }
        });
    }

    public static boolean canModify(Player player, Pos pos) {
        int chunkX = pos.chunkX();
        int chunkZ = pos.chunkZ();
        long index = getChunkIndex(chunkX, chunkZ);
        
        String owner = claims.get(index);
        if (owner == null) return true; // Свободная территория
        
        if (owner.equals(player.getUuid().toString())) return true; // Личный приват
        
        String clan = ClanManager.getClan(player.getUuid());
        if (clan != null && clan.equals(owner)) return true; // Приват клана
        
        return false;
    }
    
    public static boolean claimChunk(Player player) {
        Pos pos = player.getPosition();
        long index = getChunkIndex(pos.chunkX(), pos.chunkZ());
        
        if (claims.containsKey(index)) return false;
        
        String clan = ClanManager.getClan(player.getUuid());
        claims.put(index, clan != null ? clan : player.getUuid().toString());
        save();
        return true;
    }
}
