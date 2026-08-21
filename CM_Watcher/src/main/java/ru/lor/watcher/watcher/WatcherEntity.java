package ru.lor.watcher.watcher;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.TextureProperty;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.protocol.world.Location;
import com.github.retrooper.packetevents.wrapper.play.server.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import ru.lor.watcher.WatcherPlugin;
import ru.lor.watcher.events.WatcherDespawnEvent;
import ru.lor.watcher.model.WatcherBehaviorType;
import ru.lor.watcher.model.WatcherSpawnSettings;
import ru.lor.watcher.utils.ColorUtil;
import ru.lor.watcher.utils.HeadUtil;
import ru.lor.watcher.utils.LocationUtil;
import ru.lor.watcher.utils.WatcherPacketUtil;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class WatcherEntity {

    private final WatcherPlugin plugin;
    private final Player targetPlayer;
    private final WatcherSpawnSettings settings;

    private final String mode; // "PACKET_NPC" or "ARMOR_STAND"
    private ArmorStand armorStand;
    private ArmorStand nameTagStand; // Floating nametag stand for PACKET_NPC

    private final int entityId;
    private final UUID npcUuid;
    private final String teamName;
    private org.bukkit.Location currentLocation;

    private final long spawnTimestamp;
    private final long expireTimestamp;

    private int tickCount = 0;
    private boolean blinkingHidden = false;
    private int blinkingTimer = 0;
    private int headTurnTimer = 0;
    private boolean reacted = false;

    public WatcherEntity(WatcherPlugin plugin, Player targetPlayer, WatcherSpawnSettings settings) {
        this.plugin = plugin;
        this.targetPlayer = targetPlayer;
        this.settings = settings;

        this.mode = plugin.getConfigManager().getConfig().getString("watcher.entity-mode", "PACKET_NPC").toUpperCase();

        this.entityId = ThreadLocalRandom.current().nextInt(1000000, 9999999);
        this.teamName = "w_team_" + entityId;
        // Use real Mojang ShaderCoder account UUID so SkinsRestorer & client lock skin permanently
        this.npcUuid = UUID.fromString("ec29d384-6b64-4367-9156-74f3c0294149");

        this.spawnTimestamp = System.currentTimeMillis();
        this.expireTimestamp = spawnTimestamp + (settings.getDurationSeconds() * 1000L);

        this.currentLocation = LocationUtil.calculateLocation(targetPlayer, settings);

        if (mode.equals("ARMOR_STAND")) {
            spawnArmorStandEntity();
        } else {
            spawnFakePlayerNpc();
        }
    }

    private void spawnArmorStandEntity() {
        this.armorStand = (ArmorStand) currentLocation.getWorld().spawnEntity(currentLocation, EntityType.ARMOR_STAND);
        armorStand.setInvisible(true);
        armorStand.setInvulnerable(true);
        armorStand.setGravity(false);
        armorStand.setSilent(true);
        armorStand.setBasePlate(false);
        armorStand.setArms(true);
        armorStand.setCanPickupItems(false);

        String nameStr = plugin.getConfigManager().getConfig().getString("watcher.name", "<#800080><b>Наблюдатель</b>");
        armorStand.customName(ColorUtil.parse(nameStr));
        armorStand.setCustomNameVisible(true);

        String textureVal = plugin.getConfigManager().getConfig().getString("watcher.head-texture");
        String textureSig = plugin.getConfigManager().getConfig().getString("watcher.head-signature");
        ItemStack skull = HeadUtil.getCustomHead(textureVal, textureSig);

        ItemStack chest = new ItemStack(Material.LEATHER_CHESTPLATE);
        LeatherArmorMeta chestMeta = (LeatherArmorMeta) chest.getItemMeta();
        if (chestMeta != null) {
            chestMeta.setColor(Color.fromRGB(8, 8, 12));
            chest.setItemMeta(chestMeta);
        }

        ItemStack legs = new ItemStack(Material.NETHERITE_LEGGINGS);
        ItemStack boots = new ItemStack(Material.NETHERITE_BOOTS);

        EntityEquipment equipment = armorStand.getEquipment();
        if (equipment != null) {
            equipment.setHelmet(skull);
            equipment.setChestplate(chest);
            equipment.setLeggings(legs);
            equipment.setBoots(boots);
        }

        if (plugin.getConfigManager().getConfig().getBoolean("watcher.glowing", true)) {
            armorStand.setGlowing(true);
        }
    }

    private void spawnFakePlayerNpc() {
        String nameStr = plugin.getConfigManager().getConfig().getString("watcher.name", "<#800080><b>Наблюдатель</b>");
        String headTexture = plugin.getConfigManager().getConfig().getString("watcher.head-texture");
        String headSignature = plugin.getConfigManager().getConfig().getString("watcher.head-signature");

        Component displayNameComp = ColorUtil.parse(nameStr);

        // Send packets to target player
        WatcherPacketUtil.sendAddPlayerInfo(targetPlayer, npcUuid, "ShaderCoder", headTexture, headSignature);
        WatcherPacketUtil.sendScoreboardTeamHideNametag(targetPlayer, teamName, npcUuid);
        WatcherPacketUtil.sendSpawnPlayerNpc(targetPlayer, entityId, npcUuid, currentLocation);
        WatcherPacketUtil.sendHeadLook(targetPlayer, entityId, currentLocation.getYaw());

        // Broadcast to all nearby players
        for (Player p : currentLocation.getWorld().getPlayers()) {
            if (!p.equals(targetPlayer) && p.getLocation().distance(currentLocation) <= 48) {
                WatcherPacketUtil.sendAddPlayerInfo(p, npcUuid, "ShaderCoder", headTexture, headSignature);
                WatcherPacketUtil.sendScoreboardTeamHideNametag(p, teamName, npcUuid);
                WatcherPacketUtil.sendSpawnPlayerNpc(p, entityId, npcUuid, currentLocation);
                WatcherPacketUtil.sendHeadLook(p, entityId, currentLocation.getYaw());
            }
        }

        // Spawn custom floating nametag ArmorStand ("Наблюдатель") above NPC head
        org.bukkit.Location nameTagLoc = currentLocation.clone().add(0, 1.85, 0);
        this.nameTagStand = (ArmorStand) currentLocation.getWorld().spawnEntity(nameTagLoc, EntityType.ARMOR_STAND);
        nameTagStand.setInvisible(true);
        nameTagStand.setInvulnerable(true);
        nameTagStand.setGravity(false);
        nameTagStand.setSilent(true);
        nameTagStand.setMarker(true);
        nameTagStand.customName(displayNameComp);
        nameTagStand.setCustomNameVisible(true);
    }

    public boolean tick() {
        tickCount++;

        if (reacted || !targetPlayer.isOnline() || targetPlayer.isDead()) {
            return true;
        }

        if (!settings.isInfiniteDuration() && System.currentTimeMillis() >= expireTimestamp) {
            return true;
        }

        org.bukkit.Location playerLoc = targetPlayer.getLocation();
        double currentDistance = currentLocation.distance(playerLoc);
        if (currentDistance > settings.getDespawnDistance()) {
            return true;
        }

        // Proximity Reaction: If player approaches within 2.0 blocks, Watcher vanishes and applies debuffs!
        if (currentDistance <= 2.0) {
            triggerApproachReaction(targetPlayer);
            return true;
        }

        // Dynamic Distance-Dependent Freezing Effect
        if (settings.isFreezingEnabled() && plugin.getConfigManager().getConfig().getBoolean("freezing.enabled", true)) {
            double maxFreezingDistance = plugin.getConfigManager().getConfig().getDouble("freezing.max-distance", 12.0);
            if (currentDistance <= maxFreezingDistance) {
                double intensity = 1.0 - (currentDistance / maxFreezingDistance);

                int freezeTicksToAdd = (int) (intensity * 24) + 2;
                int currentFreeze = targetPlayer.getFreezeTicks();
                int maxFreeze = targetPlayer.getMaxFreezeTicks();
                targetPlayer.setFreezeTicks(Math.min(maxFreeze, currentFreeze + freezeTicksToAdd));

                if (intensity > 0.65) {
                    targetPlayer.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 15, 2, false, false));
                    targetPlayer.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 15, 0, false, false));
                } else if (intensity > 0.35) {
                    targetPlayer.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 15, 1, false, false));
                } else if (intensity > 0.1) {
                    targetPlayer.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 15, 0, false, false));
                }

                if (tickCount % 2 == 0) {
                    int pCount = (int) (intensity * 5) + 1;
                    playerLoc.getWorld().spawnParticle(Particle.REVERSE_PORTAL, playerLoc.clone().add(0, 1.0, 0), pCount, 0.3, 0.5, 0.3, 0.01);
                    currentLocation.getWorld().spawnParticle(Particle.REVERSE_PORTAL, currentLocation.clone().add(0, 1.2, 0), pCount, 0.3, 0.5, 0.3, 0.01);
                }
            }
        }

        if (tickCount % 2 == 0 && plugin.getConfigManager().getConfig().getBoolean("particles.enabled", true)) {
            // Shadow Aura around body
            currentLocation.getWorld().spawnParticle(Particle.SQUID_INK, currentLocation.clone().add(0, 0.9, 0), 2, 0.25, 0.4, 0.25, 0.01);
            currentLocation.getWorld().spawnParticle(Particle.REVERSE_PORTAL, currentLocation.clone().add(0, 1.0, 0), 2, 0.2, 0.5, 0.2, 0.01);

            // Glowing Eyes effect at eye height (Y + 1.62) staring in dark
            org.bukkit.Location headLoc = currentLocation.clone().add(0, 1.62, 0);
            double yawRad = Math.toRadians(currentLocation.getYaw());
            double rightX = -Math.cos(yawRad) * 0.11;
            double rightZ = -Math.sin(yawRad) * 0.11;

            org.bukkit.Location rightEye = headLoc.clone().add(rightX, 0, rightZ);
            org.bukkit.Location leftEye = headLoc.clone().subtract(rightX, 0, rightZ);

            currentLocation.getWorld().spawnParticle(Particle.END_ROD, rightEye, 1, 0.002, 0.002, 0.002, 0.0);
            currentLocation.getWorld().spawnParticle(Particle.END_ROD, leftEye, 1, 0.002, 0.002, 0.002, 0.0);
            currentLocation.getWorld().spawnParticle(Particle.WITCH, headLoc, 1, 0.08, 0.08, 0.08, 0.01);
        }

        handleBehavior(currentDistance);

        return false;
    }

    public synchronized void triggerApproachReaction(Player player) {
        if (reacted) return;
        reacted = true;

        if (player == null || !player.isOnline()) {
            plugin.getWatcherManager().despawnWatcher(targetPlayer.getUniqueId(), WatcherDespawnEvent.DespawnReason.APPROACHED_OR_ATTACKED);
            return;
        }

        // Jumpscare Camera Snap towards Watcher
        if (settings.isJumpscareEnabled()) {
            org.bukkit.Location faceWatcherLoc = LocationUtil.faceLocation(player.getLocation(), currentLocation);
            player.teleportAsync(faceWatcherLoc);
        }

        // Dark particles at Watcher location
        if (currentLocation != null && currentLocation.getWorld() != null) {
            currentLocation.getWorld().spawnParticle(Particle.SQUID_INK, currentLocation.clone().add(0, 1.0, 0), 30, 0.5, 0.8, 0.5, 0.05);
            currentLocation.getWorld().spawnParticle(Particle.REVERSE_PORTAL, currentLocation.clone().add(0, 1.0, 0), 40, 0.5, 0.8, 0.5, 0.05);
            currentLocation.getWorld().spawnParticle(Particle.LARGE_SMOKE, currentLocation.clone().add(0, 1.0, 0), 25, 0.5, 0.8, 0.5, 0.02);
        }

        // Dramatic scary sounds & screamer
        try {
            if (settings.isJumpscareEnabled()) {
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_GHAST_SCREAM, 1.5f, 0.5f);
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ENDER_DRAGON_GROWL, 1.2f, 0.5f);
            }
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_WARDEN_HEARTBEAT, 1.2f, 0.6f);
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ENDERMAN_SCREAM, 1.0f, 0.5f);
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_WITHER_SPAWN, 0.7f, 0.5f);
        } catch (Throwable ignored) {}

        // Red Title warning
        Title title = Title.title(
                ColorUtil.parse("<dark_red><b>ОН ЗАМЕТИЛ ТЕБЯ!</b></dark_red>"),
                ColorUtil.parse("<dark_purple>Не подходи слишком близко...</dark_purple>"),
                Title.Times.times(Duration.ofMillis(100), Duration.ofSeconds(3), Duration.ofMillis(300))
        );
        player.showTitle(title);

        // Safe horror effects without damaging or reducing player health
        player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 160, 0, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 100, 0, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 120, 1, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 140, 0, false, true));

        // Telegram Jumpscare Log
        if (plugin.getTelegramBotManager() != null) {
            plugin.getTelegramBotManager().logJumpscare(player, "Игрок приблизился к сущности или атаковал её", currentLocation != null ? currentLocation : player.getLocation());
        }

        // Despawn Watcher instantly
        plugin.getWatcherManager().despawnWatcher(player.getUniqueId(), WatcherDespawnEvent.DespawnReason.APPROACHED_OR_ATTACKED);
    }

    private void handleBehavior(double currentDistance) {
        WatcherBehaviorType behavior = settings.getBehaviorType();
        org.bukkit.Location playerEye = targetPlayer.getEyeLocation();

        switch (behavior) {
            case STATIC -> {
                currentLocation = LocationUtil.faceLocation(currentLocation, playerEye);
                updatePosition(currentLocation);
            }
            case STALKER -> {
                currentLocation = LocationUtil.faceLocation(currentLocation, playerEye);
                if (currentDistance > 1.5) {
                    org.bukkit.util.Vector moveDir = playerEye.toVector().subtract(currentLocation.toVector()).setY(0).normalize().multiply(0.04);
                    currentLocation.add(moveDir);
                    currentLocation = LocationUtil.findSafeGroundY(currentLocation, targetPlayer.getLocation());
                    currentLocation = LocationUtil.faceLocation(currentLocation, playerEye);
                }
                updatePosition(currentLocation);
            }
            case BLINKING -> {
                blinkingTimer++;
                if (blinkingTimer >= 100) {
                    blinkingHidden = !blinkingHidden;
                    blinkingTimer = 0;

                    if (blinkingHidden) {
                        currentLocation.getWorld().spawnParticle(Particle.SNOWFLAKE, currentLocation.clone().add(0, 1.0, 0), 25, 0.4, 0.7, 0.4, 0.05);
                        hideEntity();
                    } else {
                        currentLocation = LocationUtil.calculateLocation(targetPlayer, settings);
                        showEntity();
                        currentLocation.getWorld().spawnParticle(Particle.SNOWFLAKE, currentLocation.clone().add(0, 1.0, 0), 25, 0.4, 0.7, 0.4, 0.05);
                    }
                } else if (!blinkingHidden) {
                    currentLocation = LocationUtil.faceLocation(currentLocation, playerEye);
                    updatePosition(currentLocation);
                }
            }
            case HEAD_TURN -> {
                headTurnTimer++;
                if (headTurnTimer >= 80) {
                    int turnPhase = headTurnTimer % 40;
                    if (turnPhase < 20) {
                        currentLocation.setYaw(currentLocation.getYaw() + 70.0f);
                        updatePosition(currentLocation);
                    } else {
                        headTurnTimer = 0;
                    }
                } else {
                    currentLocation = LocationUtil.faceLocation(currentLocation, playerEye);
                    updatePosition(currentLocation);
                }
            }
            case MIRROR_STARE -> {
                org.bukkit.util.Vector playerLook = playerEye.getDirection().normalize();
                org.bukkit.util.Vector playerToWatcher = currentLocation.toVector().subtract(playerEye.toVector()).normalize();

                double dot = playerLook.dot(playerToWatcher);
                if (dot > 0.5) {
                    currentLocation = LocationUtil.faceLocation(currentLocation, playerEye);
                    updatePosition(currentLocation);
                }
            }
        }
    }

    private void updatePosition(org.bukkit.Location loc) {
        if (mode.equals("PACKET_NPC")) {
            WatcherPacketUtil.sendTeleport(targetPlayer, entityId, loc);
            WatcherPacketUtil.sendHeadLook(targetPlayer, entityId, loc.getYaw());

            for (Player p : loc.getWorld().getPlayers()) {
                if (!p.equals(targetPlayer) && p.getLocation().distance(loc) <= 48) {
                    WatcherPacketUtil.sendTeleport(p, entityId, loc);
                    WatcherPacketUtil.sendHeadLook(p, entityId, loc.getYaw());
                }
            }

            if (nameTagStand != null && nameTagStand.isValid()) {
                org.bukkit.Location targetStandLoc = loc.clone().add(0, 1.85, 0);
                if (targetStandLoc.distanceSquared(nameTagStand.getLocation()) > 0.05) {
                    nameTagStand.teleportAsync(targetStandLoc);
                }
            }
        } else if (armorStand != null && armorStand.isValid()) {
            armorStand.teleportAsync(loc);
        }
    }

    private void hideEntity() {
        if (mode.equals("PACKET_NPC")) {
            destroyNpcPacket();
            if (nameTagStand != null && nameTagStand.isValid()) {
                nameTagStand.setInvisible(true);
            }
        } else if (armorStand != null && armorStand.isValid()) {
            armorStand.setInvisible(true);
        }
    }

    private void showEntity() {
        if (mode.equals("PACKET_NPC")) {
            spawnFakePlayerNpc();
        } else if (armorStand != null && armorStand.isValid()) {
            armorStand.teleportAsync(currentLocation);
        }
    }

    private void destroyNpcPacket() {
        WatcherPacketUtil.sendDestroyNpc(targetPlayer, entityId, npcUuid, teamName);

        for (Player p : currentLocation.getWorld().getPlayers()) {
            if (!p.equals(targetPlayer) && p.getLocation().distance(currentLocation) <= 48) {
                WatcherPacketUtil.sendDestroyNpc(p, entityId, npcUuid, teamName);
            }
        }

        if (nameTagStand != null && nameTagStand.isValid()) {
            nameTagStand.remove();
        }
    }

    public void remove() {
        if (currentLocation != null && currentLocation.getWorld() != null) {
            org.bukkit.World world = currentLocation.getWorld();
            org.bukkit.Location particleLoc = currentLocation.clone().add(0, 1.0, 0);

            // Dark soul & portal particle blast
            world.spawnParticle(Particle.REVERSE_PORTAL, particleLoc, 40, 0.4, 0.8, 0.4, 0.05);
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, particleLoc, 15, 0.3, 0.6, 0.3, 0.03);
            world.spawnParticle(Particle.LARGE_SMOKE, particleLoc, 20, 0.4, 0.7, 0.4, 0.02);

            // Scary atmospheric despawn sounds
            try {
                if (targetPlayer != null && targetPlayer.isOnline()) {
                    targetPlayer.playSound(targetPlayer.getLocation(), org.bukkit.Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.5f);
                    targetPlayer.playSound(targetPlayer.getLocation(), org.bukkit.Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD, 1.5f, 0.5f);
                    targetPlayer.playSound(targetPlayer.getLocation(), org.bukkit.Sound.ENTITY_WARDEN_HEARTBEAT, 1.2f, 0.4f);
                    targetPlayer.playSound(targetPlayer.getLocation(), org.bukkit.Sound.ENTITY_WITHER_DEATH, 0.6f, 0.5f);
                } else {
                    world.playSound(particleLoc, org.bukkit.Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.5f);
                    world.playSound(particleLoc, org.bukkit.Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD, 1.5f, 0.5f);
                }
            } catch (Throwable ignored) {}
        }
        if (mode.equals("PACKET_NPC")) {
            destroyNpcPacket();
        } else if (armorStand != null && armorStand.isValid()) {
            armorStand.remove();
        }
        if (nameTagStand != null && nameTagStand.isValid()) {
            nameTagStand.remove();
        }
    }

    public Player getTargetPlayer() {
        return targetPlayer;
    }

    public WatcherSpawnSettings getSettings() {
        return settings;
    }

    public int getEntityId() {
        return entityId;
    }

    public ArmorStand getArmorStand() {
        return armorStand;
    }

    public ArmorStand getNameTagStand() {
        return nameTagStand;
    }

    public long getSpawnTimestamp() {
        return spawnTimestamp;
    }

    public org.bukkit.Location getCurrentLocation() {
        return currentLocation;
    }
}
