package ru.lor.watcher.events;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import ru.lor.watcher.WatcherPlugin;
import ru.lor.watcher.model.WatcherPositionType;
import ru.lor.watcher.model.WatcherSpawnSettings;
import ru.lor.watcher.utils.ColorUtil;

public class WatcherRitualListener implements Listener {

    private final WatcherPlugin plugin;
    private long lastRitualTime = 0;

    public WatcherRitualListener(WatcherPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block clicked = event.getClickedBlock();
        if (clicked == null) return;

        // Ritual central block MUST be CRYING_OBSIDIAN or SCULK_CATALYST
        Material clickedType = clicked.getType();
        if (clickedType != Material.CRYING_OBSIDIAN && clickedType != Material.SCULK_CATALYST) return;

        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.ENDER_EYE) return;

        Player player = event.getPlayer();

        // Check ritual surroundings (Must have at least 2 Soul Torches / Soul Lanterns / Soul Candles / Sculk near central block)
        int soulCount = 0;
        Block center = clicked;
        int[][] offsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}};
        for (int[] off : offsets) {
            Material type = center.getRelative(off[0], 0, off[1]).getType();
            if (type.name().contains("SOUL") || type.name().contains("SCULK") || type.name().contains("CANDLE")) {
                soulCount++;
            }
        }

        if (soulCount < 2) {
            return; // Not a valid ritual setup
        }

        // Cooldown of 15 seconds per ritual
        long now = System.currentTimeMillis();
        if (now - lastRitualTime < 15000) {
            return;
        }
        lastRitualTime = now;

        event.setCancelled(true);

        // Consume 1 Ender Eye from hand if not in creative
        if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            item.setAmount(item.getAmount() - 1);
        }

        // Ritual Particle Effects & Sounds
        org.bukkit.Location loc = clicked.getLocation().add(0.5, 1.2, 0.5);
        loc.getWorld().spawnParticle(Particle.REVERSE_PORTAL, loc, 60, 0.5, 0.8, 0.5, 0.08);
        loc.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, loc, 40, 0.4, 0.6, 0.4, 0.05);
        loc.getWorld().spawnParticle(Particle.SCULK_SOUL, loc, 35, 0.5, 0.7, 0.5, 0.04);
        loc.getWorld().spawnParticle(Particle.LARGE_SMOKE, loc, 30, 0.5, 0.8, 0.5, 0.02);

        try {
            loc.getWorld().playSound(loc, Sound.ENTITY_WARDEN_EMERGE, 1.2f, 0.6f);
            loc.getWorld().playSound(loc, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1.0f, 0.5f);
            loc.getWorld().playSound(loc, Sound.ENTITY_WITHER_SPAWN, 0.8f, 0.5f);
        } catch (Throwable ignored) {}

        final int finalSoulCount = soulCount;

        // Spawn Watcher behind player
        player.getScheduler().run(plugin.getBukkitPlugin(), task -> {
            // Despawn existing if any
            if (plugin.getWatcherManager().hasWatcher(player)) {
                plugin.getWatcherManager().despawnWatcher(player.getUniqueId(), WatcherDespawnEvent.DespawnReason.MANUAL_DESPAWN);
            }

            WatcherSpawnSettings settings = new WatcherSpawnSettings();
            settings.setSpawnDistance(4.0);
            settings.setDurationSeconds(35);
            settings.setInfiniteDuration(false);
            settings.setPositionType(WatcherPositionType.BEHIND);
            settings.setFreezingEnabled(true);
            settings.setJumpscareEnabled(true);
            settings.setAiMessageEnabled(true);
            settings.setSoundPreset("ANCIENT_HORROR");

            boolean ok = plugin.getWatcherManager().spawnWatcher(player, settings, "SummoningRitual");

            if (ok && plugin.getTelegramBotManager() != null) {
                plugin.getTelegramBotManager().logRitual(player, loc, clickedType.name(), finalSoulCount);
            }

            if (ok && plugin.getAiBrainManager() != null) {
                String ritualContext = "Игрок провел древний мистический ритуал призыва с помощью Ока Эндера.";
                plugin.getAiBrainManager().generateWatcherAction(player, ritualContext).thenAccept(aiMsg -> {
                    if (aiMsg != null && !aiMsg.isBlank()) {
                        String safeMsg = ColorUtil.escape(aiMsg);
                        player.getScheduler().run(plugin.getBukkitPlugin(), t -> {
                            String broadcastFormat = plugin.getConfigManager().getBroadcastFormat();
                            String formatted = broadcastFormat.replace("{message}", safeMsg);
                            player.sendMessage(ColorUtil.parse(formatted));

                            String actionBarText = "<#a855f7><b>[Смотрящий]</b></#a855f7> <white>" + safeMsg + "</white>";
                            ColorUtil.sendActionBarPersistent(plugin.getBukkitPlugin(), player, actionBarText, 6);
                        }, null);
                    }
                });
            }
        }, null);
    }
}
