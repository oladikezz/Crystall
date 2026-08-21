//noinspection PackageDirectoryMismatch
package net.schalker.SMPS.modules.marry.listeners;

import net.schalker.DoAPI.DoAPI;
import net.schalker.SMPS.modules.marry.MarryModule;
import net.schalker.SMPS.modules.marry.managers.MarryManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.GameMode;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listener for SM_Marry module events.
 * Handles:
 * - Cleanup when players leave
 * - Shared damage between married partners
 * - PvP sound between married partners
 * - Shift+RMB on partner → Pat (heart particles, private messages)
 * - Shift+LMB on partner → Spank (private messages)
 */
public class MarryListener implements Listener {

    private final DoAPI plugin;
    private final MarryModule module;
    private final Object database;

    // Prevent infinite damage loop when applying shared damage (thread-safe for Folia)
    private final Set<UUID> processingDamage = ConcurrentHashMap.newKeySet();

    public MarryListener(DoAPI plugin, MarryModule module, MarryManager marryManager) {
        this.plugin = plugin;
        this.module = module;
        this.database = module.getMarryDatabase();
    }

    /**
     * Resolve a Sound from config name using Registry (non-deprecated for 1.21.3+).
     * Accepts ENUM_STYLE names like "ENTITY_CAT_PURREOW" and converts to
     * minecraft:entity.cat.purreow for registry lookup.
     *
     * @param name     the enum-style sound name from config
     * @param fallback fallback Sound if lookup fails
     * @return resolved Sound, never null
     */
    private Sound resolveSound(String name, Sound fallback) {
        try {
            String key = name.toLowerCase().replace('_', '.');
            Sound sound = Registry.SOUNDS.get(NamespacedKey.minecraft(key));
            return sound != null ? sound : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    /**
     * Cancel any pending marriage/divorce requests when player leaves.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        var player = event.getPlayer();
        var uuid = player.getUniqueId();

        // Clean up damage processing set
        processingDamage.remove(uuid);
    }

    // ═══════════════════════════════════════════════════════════════
    //  Shift + RMB on partner → PAT (heart particles + messages)
    // ═══════════════════════════════════════════════════════════════

    /**
     * When a sneaking player right-clicks their married partner,
     * spawn heart particles and send private chat messages to both.
     * No cooldown.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPatPartner(PlayerInteractEntityEvent event) {
        // Only main hand to avoid double-fire
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player actor = event.getPlayer();

        // Must be sneaking (Shift held)
        if (!actor.isSneaking()) {
            return;
        }

        // Target must be a player
        if (!(event.getRightClicked() instanceof Player target)) {
            return;
        }

        // Check if pat-interaction is enabled
        if (!module.getConfig().getBoolean("settings.interactions.pat.enabled", true)) {
            return;
        }

        UUID actorUuid = actor.getUniqueId();
        UUID targetUuid = target.getUniqueId();

        // Can't pat yourself
        if (actorUuid.equals(targetUuid)) {
            return;
        }

        // Check marriage async, then act
        plugin.getSchedulerManager().runAsync("marry-pat-check-" + actorUuid, () -> {
            if (database == null) {
                return;
            }
            boolean married = arePlayersMarried(actorUuid, targetUuid);
            if (!married) {
                return;
            }

            // Back to entity context for particles & messages
            plugin.getSchedulerManager().runEntityTask(actor, "marry-pat-act-" + actorUuid, () -> {
                if (!actor.isOnline() || !target.isOnline()) return;

                // --- Heart particles above the target ---
                spawnHeartParticles(target);

                // --- Sound ---
                playPatSound(actor);
                playPatSound(target);

                // --- Private messages (only the couple sees them) ---
                String actorMsg = module.getMessage("pat-actor")
                        .replace("{partner}", target.getName());
                String targetMsg = module.getMessage("pat-target")
                        .replace("{partner}", actor.getName());

                actor.sendMessage(actorMsg);
                target.sendMessage(targetMsg);
            });
        });
    }

    /**
     * Spawn heart particles above a player, visible to everyone nearby.
     */
    private void spawnHeartParticles(Player target) {
        Location loc = target.getLocation().add(0, 2.2, 0);
        int count = module.getConfig().getInt("settings.interactions.pat.particle-count", 8);
        double spread = module.getConfig().getDouble("settings.interactions.pat.particle-spread", 0.35);

        target.getWorld().spawnParticle(
                Particle.HEART,
                loc,
                count,       // count
                spread,      // offsetX
                spread,      // offsetY
                spread,      // offsetZ
                0            // speed
        );
    }

    /**
     * Play pat sound to a player.
     */
    private void playPatSound(Player player) {
        String soundName = module.getConfig().getString(
                "settings.interactions.pat.sound", "ENTITY_CAT_PURREOW");
        Sound sound = resolveSound(soundName, Sound.ENTITY_CAT_PURREOW);
        float volume = (float) module.getConfig().getDouble(
                "settings.interactions.pat.sound-volume", 1.0);
        float pitch = (float) module.getConfig().getDouble(
                "settings.interactions.pat.sound-pitch", 1.2);
        player.playSound(player.getLocation(), sound, volume, pitch);
    }

    // ═══════════════════════════════════════════════════════════════
    //  Shift + LMB on partner → SPANK (messages only)
    // ═══════════════════════════════════════════════════════════════

    /**
     * When a sneaking player left-clicks (hits) their married partner,
     * send private "spank" chat messages to both sides.
     * The actual damage event is NOT cancelled — vanilla damage still applies.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpankPartner(EntityDamageByEntityEvent event) {
        // Only player-on-player
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        // Must be sneaking
        if (!attacker.isSneaking()) return;

        // Check if spank-interaction is enabled
        if (!module.getConfig().getBoolean("settings.interactions.spank.enabled", true)) {
            return;
        }

        UUID attackerUuid = attacker.getUniqueId();
        UUID victimUuid = victim.getUniqueId();
        if (attackerUuid.equals(victimUuid)) return;

        // Check marriage async, then send messages
        plugin.getSchedulerManager().runAsync("marry-spank-check-" + attackerUuid, () -> {
            if (database == null) {
                return;
            }
            boolean married = arePlayersMarried(attackerUuid, victimUuid);
            if (!married) return;

            plugin.getSchedulerManager().runEntityTask(attacker, "marry-spank-act-" + attackerUuid, () -> {
                if (!attacker.isOnline() || !victim.isOnline()) return;

                // --- Particles above the victim ---
                spawnSpankParticles(victim);

                // --- Sound ---
                playSpankSound(attacker);
                playSpankSound(victim);

                // --- Private messages ---
                String attackerMsg = module.getMessage("spank-actor")
                        .replace("{partner}", victim.getName());
                String victimMsg = module.getMessage("spank-target")
                        .replace("{partner}", attacker.getName());

                attacker.sendMessage(attackerMsg);
                victim.sendMessage(victimMsg);
            });
        });
    }

    /**
     * Play spank sound to a player.
     */
    private void playSpankSound(Player player) {
        String soundName = module.getConfig().getString(
                "settings.interactions.spank.sound", "ENTITY_PLAYER_ATTACK_SWEEP");
        Sound sound = resolveSound(soundName, Sound.ENTITY_PLAYER_ATTACK_SWEEP);
        float volume = (float) module.getConfig().getDouble(
                "settings.interactions.spank.sound-volume", 1.0);
        float pitch = (float) module.getConfig().getDouble(
                "settings.interactions.spank.sound-pitch", 1.5);
        player.playSound(player.getLocation(), sound, volume, pitch);
    }

    /**
     * Spawn spank particles behind/around the target player.
     */
    private void spawnSpankParticles(Player target) {
        Location loc = target.getLocation().add(0, 1.0, 0);
        int count = module.getConfig().getInt("settings.interactions.spank.particle-count", 5);
        double spread = module.getConfig().getDouble("settings.interactions.spank.particle-spread", 0.3);

        target.getWorld().spawnParticle(
                Particle.CLOUD,
                loc,
                count,
                spread,
                spread,
                spread,
                0.02
        );
    }

    // ═══════════════════════════════════════════════════════════════
    //  Shared damage between married partners (phantom — visual only)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Phantom shared damage between married partners.
     * When one partner takes damage, the other sees the hurt animation
     * and hears the hurt sound, but takes NO real damage.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.isCancelled()) {
            return;
        }

        // Check if feature is enabled
        if (!module.getConfig().getBoolean("settings.shared-damage.enabled", true)) {
            return;
        }

        // Only for players
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        // Prevent damage if player is in creative, spectator, or vanished
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR ||
            isVanished(player)) {
            return;
        }

        UUID playerUuid = player.getUniqueId();

        // Prevent infinite loop (in case playHurtAnimation somehow re-triggers)
        if (processingDamage.contains(playerUuid)) {
            return;
        }

        double damage = event.getFinalDamage();
        if (damage <= 0) {
            return;
        }

        // Check if player is married (async)
        plugin.getSchedulerManager().runAsync("marry-check-damage", () -> {
            if (database == null) {
                return;
            }
            UUID partnerUuid = getPartnerUuid(playerUuid);
            if (partnerUuid == null) {
                return;
            }
            Player partner = Bukkit.getPlayer(partnerUuid);

            if (partner == null || !partner.isOnline()) {
                return;
            }

            // Never mirror to protected states
            if (partner.getGameMode() == GameMode.CREATIVE || partner.getGameMode() == GameMode.SPECTATOR ||
                isVanished(partner)) {
                return;
            }

            // Must run on partner's entity thread for Folia compatibility
            plugin.getSchedulerManager().runEntityTask(partner, "marry-phantom-damage-" + partnerUuid, () -> {
                if (!partner.isOnline() || partner.isDead()) {
                    return;
                }

                if (partner.getGameMode() == GameMode.CREATIVE || partner.getGameMode() == GameMode.SPECTATOR ||
                    isVanished(partner)) {
                    return;
                }

                processingDamage.add(partnerUuid);

                try {
                    // Phantom damage — visual hurt animation + sound, NO real damage
                    partner.playHurtAnimation(0);
                    partner.getWorld().playSound(
                        partner.getLocation(),
                        Sound.ENTITY_PLAYER_HURT,
                        0.5f, 1.0f
                    );
                } finally {
                    // Use entity task for cleanup to stay on the correct Folia thread
                    plugin.getSchedulerManager().runEntityTaskLater(partner, "marry-cleanup-damage-" + partnerUuid,
                        () -> processingDamage.remove(partnerUuid), 1L);
                }
            });
        });
    }

    // ═══════════════════════════════════════════════════════════════
    //  PvP sound between married partners (non-sneaking hit)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Play cat meow sound when partner hits partner (PvP between married couple).
     * Only fires for non-sneaking hits (sneaking triggers the spank interaction instead).
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPartnerHitPartner(EntityDamageByEntityEvent event) {
        if (!module.getConfig().getBoolean("settings.partner-pvp-sound.enabled", true)) {
            return;
        }

        if (!(event.getEntity() instanceof Player victim)) return;
        if (!(event.getDamager() instanceof Player attacker)) return;

        // Skip if sneaking — that's handled by the spank interaction
        if (attacker.isSneaking()) return;

        UUID victimUuid = victim.getUniqueId();
        UUID attackerUuid = attacker.getUniqueId();

        plugin.getSchedulerManager().runAsync("marry-check-pvp", () -> {
            if (database == null) {
                return;
            }
            boolean areMarried = arePlayersMarried(victimUuid, attackerUuid);

            if (!areMarried) return;

            // Use entity tasks for Folia — playSound needs entity thread
            if (victim.isOnline()) {
                plugin.getSchedulerManager().runEntityTask(victim, "marry-pvp-sound-victim-" + victimUuid, () -> {
                    if (victim.isOnline()) playPartnerHitSound(victim);
                });
            }
            if (attacker.isOnline()) {
                plugin.getSchedulerManager().runEntityTask(attacker, "marry-pvp-sound-attacker-" + attackerUuid, () -> {
                    if (attacker.isOnline()) playPartnerHitSound(attacker);
                });
            }
        });
    }

    /**
     * Play cat hurt sound when partner hits partner.
     */
    private void playPartnerHitSound(Player player) {
        String soundName = module.getConfig().getString("settings.partner-pvp-sound.sound", "ENTITY_CAT_HURT");
        Sound sound = resolveSound(soundName, Sound.ENTITY_CAT_HURT);
        float volume = (float) module.getConfig().getDouble("settings.partner-pvp-sound.volume", 1.0);
        float pitch = (float) module.getConfig().getDouble("settings.partner-pvp-sound.pitch", 1.0);
        player.playSound(player.getLocation(), sound, volume, pitch);
    }

    /**
     * Check if a player is vanished (invisible) using reflection to access SM_Vanish module.
     * Returns false if SM_Vanish is not present or an error occurs.
     */
    private boolean isVanished(Player player) {
        try {
            Object smVanishModule = plugin.getModuleManager().getModule("SM_Vanish");
            if (smVanishModule == null) return false;

            Method isVanishedMethod = smVanishModule.getClass().getMethod("isVanished", Player.class);
            return (boolean) isVanishedMethod.invoke(smVanishModule, player);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean arePlayersMarried(UUID player1, UUID player2) {
        try {
            Method method = database.getClass().getMethod("areMarried", UUID.class, UUID.class);
            Object result = method.invoke(database, player1, player2);
            return result instanceof Boolean married && married;
        } catch (Exception e) {
            return false;
        }
    }

    private UUID getPartnerUuid(UUID playerUuid) {
        try {
            Method getPartner = database.getClass().getMethod("getPartner", UUID.class);
            Object marriageInfo = getPartner.invoke(database, playerUuid);
            if (marriageInfo == null) {
                return null;
            }

            Method getPartnerUuid = marriageInfo.getClass().getMethod("getPartnerUuid");
            Object partnerUuid = getPartnerUuid.invoke(marriageInfo);
            return partnerUuid instanceof UUID uuid ? uuid : null;
        } catch (Exception e) {
            return null;
        }
    }
}
