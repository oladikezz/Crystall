package ru.lor.watcher.events;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;

import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;

import ru.lor.watcher.WatcherPlugin;
import ru.lor.watcher.watcher.WatcherEntity;

public class WatcherInteractListener implements Listener, PacketListener {

    private final WatcherPlugin plugin;

    public WatcherInteractListener(WatcherPlugin plugin) {
        this.plugin = plugin;
    }

    private com.github.retrooper.packetevents.event.PacketListenerCommon packetHandle;

    public void register() {
        plugin.getCore().getListenerManager().registerListener(this);
        try {
            if (PacketEvents.getAPI() != null) {
                packetHandle = PacketEvents.getAPI().getEventManager()
                        .registerListener(this, PacketListenerPriority.HIGH);
            }
        } catch (Throwable throwable) {
            plugin.getLogger().warning("[Watcher] PacketEvents unavailable, packet interactions disabled: "
                    + throwable.getMessage());
        }
    }

    public void unregister() {
        plugin.getCore().getListenerManager().unregisterListener(this);
        if (packetHandle == null) {
            return;
        }
        try {
            PacketEvents.getAPI().getEventManager().unregisterListener(packetHandle);
        } catch (Throwable ignored) {
        } finally {
            packetHandle = null;
        }
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
            WrapperPlayClientInteractEntity packet = new WrapperPlayClientInteractEntity(event);
            int hitId = packet.getEntityId();
            Player player = (Player) event.getPlayer();

            if (player != null && player.isOnline()) {
                WatcherEntity watcher = plugin.getWatcherManager().getWatcher(player);
                if (watcher != null && (watcher.getEntityId() == hitId || (watcher.getNameTagStand() != null && watcher.getNameTagStand().getEntityId() == hitId))) {
                    player.getScheduler().run(plugin.getBukkitPlugin(), task -> watcher.triggerApproachReaction(player), null);
                }
            }
        }
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            Entity victim = event.getEntity();
            if (victim instanceof ArmorStand stand) {
                for (WatcherEntity watcher : plugin.getWatcherManager().getActiveWatchers().values()) {
                    if (stand.equals(watcher.getArmorStand()) || stand.equals(watcher.getNameTagStand())) {
                        event.setCancelled(true);
                        watcher.triggerApproachReaction(player);
                        return;
                    }
                }
            }
        }
    }

    @EventHandler
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        Entity victim = event.getRightClicked();
        if (victim instanceof ArmorStand stand) {
            for (WatcherEntity watcher : plugin.getWatcherManager().getActiveWatchers().values()) {
                if (stand.equals(watcher.getArmorStand()) || stand.equals(watcher.getNameTagStand())) {
                    event.setCancelled(true);
                    watcher.triggerApproachReaction(player);
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onEntityInteractAt(PlayerInteractAtEntityEvent event) {
        Player player = event.getPlayer();
        Entity victim = event.getRightClicked();
        if (victim instanceof ArmorStand stand) {
            for (WatcherEntity watcher : plugin.getWatcherManager().getActiveWatchers().values()) {
                if (stand.equals(watcher.getArmorStand()) || stand.equals(watcher.getNameTagStand())) {
                    event.setCancelled(true);
                    watcher.triggerApproachReaction(player);
                    return;
                }
            }
        }
    }
}
