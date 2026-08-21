package net.schalker.SMPS.modules.cosmetics.managers;

import net.schalker.SMPS.modules.cosmetics.models.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import net.schalker.DoAPI.DoAPI;
import net.schalker.SMPS.modules.cosmetics.CosmeticsModule;
import net.schalker.SMPS.modules.cosmetics.database.CosmeticsDatabase;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ÃƒÂÃ…â€œÃƒÂÃ‚ÂµÃƒÂÃ‚Â½ÃƒÂÃ‚ÂµÃƒÂÃ‚Â´ÃƒÂÃ‚Â¶ÃƒÂÃ‚ÂµÃƒâ€˜Ã¢â€šÂ¬ ÃƒÂÃ‚ÂºÃƒÂÃ‚Â¾Ãƒâ€˜Ã‚ÂÃƒÂÃ‚Â¼ÃƒÂÃ‚ÂµÃƒâ€˜Ã¢â‚¬Å¡ÃƒÂÃ‚Â¸ÃƒÂÃ‚ÂºÃƒÂÃ‚Â¸ ÃƒÂÃ‚Â¿ÃƒÂÃ‚Â¾ÃƒÂÃ‚Â»Ãƒâ€˜Ã…â€™ÃƒÂÃ‚Â·ÃƒÂÃ‚Â¾ÃƒÂÃ‚Â²ÃƒÂÃ‚Â°Ãƒâ€˜Ã¢â‚¬Å¡ÃƒÂÃ‚ÂµÃƒÂÃ‚Â»ÃƒÂÃ‚ÂµÃƒÂÃ‚Â¹
 * ÃƒÂÃ‚Â£ÃƒÂÃ‚Â¿Ãƒâ€˜Ã¢â€šÂ¬ÃƒÂÃ‚Â°ÃƒÂÃ‚Â²ÃƒÂÃ‚Â»Ãƒâ€˜Ã‚ÂÃƒÂÃ‚ÂµÃƒâ€˜Ã¢â‚¬Å¡ Ãƒâ€˜Ã‚ÂÃƒÂÃ‚ÂºÃƒÂÃ‚Â¸ÃƒÂÃ‚Â¿ÃƒÂÃ‚Â¸Ãƒâ€˜Ã¢â€šÂ¬ÃƒÂÃ‚Â¾ÃƒÂÃ‚Â²ÃƒÂÃ‚Â°ÃƒÂÃ‚Â½ÃƒÂÃ‚Â½ÃƒÂÃ‚Â¾ÃƒÂÃ‚Â¹ ÃƒÂÃ‚ÂºÃƒÂÃ‚Â¾Ãƒâ€˜Ã‚ÂÃƒÂÃ‚Â¼ÃƒÂÃ‚ÂµÃƒâ€˜Ã¢â‚¬Å¡ÃƒÂÃ‚Â¸ÃƒÂÃ‚ÂºÃƒÂÃ‚Â¾ÃƒÂÃ‚Â¹ ÃƒÂÃ‚Â¸ ÃƒÂÃ‚Â½ÃƒÂÃ‚Â°Ãƒâ€˜Ã‚ÂÃƒâ€˜Ã¢â‚¬Å¡Ãƒâ€˜Ã¢â€šÂ¬ÃƒÂÃ‚Â¾ÃƒÂÃ‚Â¹ÃƒÂÃ‚ÂºÃƒÂÃ‚Â°ÃƒÂÃ‚Â¼ÃƒÂÃ‚Â¸ ÃƒÂÃ‚Â¸ÃƒÂÃ‚Â³Ãƒâ€˜Ã¢â€šÂ¬ÃƒÂÃ‚Â¾ÃƒÂÃ‚ÂºÃƒÂÃ‚Â¾ÃƒÂÃ‚Â²
 */
public class UserCosmeticsManager {
    private final DoAPI plugin;
    private final CosmeticsModule module;
    private final CosmeticsDatabase database;
    
    // ÃƒÂÃ¢â‚¬ÂÃƒÂÃ‚Â°ÃƒÂÃ‚Â½ÃƒÂÃ‚Â½Ãƒâ€˜Ã¢â‚¬Â¹ÃƒÂÃ‚Âµ ÃƒÂÃ‚Â¿ÃƒÂÃ‚Â¾ÃƒÂÃ‚Â»Ãƒâ€˜Ã…â€™ÃƒÂÃ‚Â·ÃƒÂÃ‚Â¾ÃƒÂÃ‚Â²ÃƒÂÃ‚Â°Ãƒâ€˜Ã¢â‚¬Å¡ÃƒÂÃ‚ÂµÃƒÂÃ‚Â»ÃƒÂÃ‚ÂµÃƒÂÃ‚Â¹ (UUID ÃƒÂÃ‚Â¸ÃƒÂÃ‚Â³Ãƒâ€˜Ã¢â€šÂ¬ÃƒÂÃ‚Â¾ÃƒÂÃ‚ÂºÃƒÂÃ‚Â° -> UserCosmetics)
    private final Map<UUID, UserCosmetics> userCosmetics;
    
    // ÃƒÂÃ‚ÂÃƒÂÃ‚Â°Ãƒâ€˜Ã‚ÂÃƒâ€˜Ã¢â‚¬Å¡Ãƒâ€˜Ã¢â€šÂ¬ÃƒÂÃ‚Â¾ÃƒÂÃ‚Â¹ÃƒÂÃ‚ÂºÃƒÂÃ‚Â¸ ÃƒÂÃ‚Â¿ÃƒÂÃ‚Â¾ÃƒÂÃ‚Â»Ãƒâ€˜Ã…â€™ÃƒÂÃ‚Â·ÃƒÂÃ‚Â¾ÃƒÂÃ‚Â²ÃƒÂÃ‚Â°Ãƒâ€˜Ã¢â‚¬Å¡ÃƒÂÃ‚ÂµÃƒÂÃ‚Â»ÃƒÂÃ‚ÂµÃƒÂÃ‚Â¹ (UUID ÃƒÂÃ‚Â¸ÃƒÂÃ‚Â³Ãƒâ€˜Ã¢â€šÂ¬ÃƒÂÃ‚Â¾ÃƒÂÃ‚ÂºÃƒÂÃ‚Â° -> UserCosmeticSettings)
    private final Map<UUID, UserCosmeticSettings> userSettings;
    private final Map<UUID, TemporaryArrowEffects> temporaryArrowEffects;    private int auditPlayerIndex = 0;    
    // ÃƒÂÃ‹Å“ÃƒÂÃ‚Â¼ÃƒÂÃ‚ÂµÃƒÂÃ‚Â½ÃƒÂÃ‚Â° ÃƒÂÃ‚Â·ÃƒÂÃ‚Â°ÃƒÂÃ‚Â´ÃƒÂÃ‚Â°Ãƒâ€˜Ã¢â‚¬Â¡
    private static final String PARTICLE_TASK_NAME = "cosmetics-particle-update";
    private static final String PET_TASK_NAME = "cosmetics-pet-update";
    private static final String BALLOON_TASK_NAME = "cosmetics-balloon-update";
    private static final String PERMISSION_TASK_NAME = "cosmetics-permission-audit";

    public UserCosmeticsManager(DoAPI plugin, CosmeticsModule cosmeticsModule) {
        this.plugin = plugin;
        this.module = cosmeticsModule;
        this.userCosmetics = new ConcurrentHashMap<>();
        this.userSettings = new ConcurrentHashMap<>();
        this.temporaryArrowEffects = new ConcurrentHashMap<>();
        
        // ÃƒÂÃ‹Å“ÃƒÂÃ‚Â½ÃƒÂÃ‚Â¸Ãƒâ€˜Ã¢â‚¬Â ÃƒÂÃ‚Â¸ÃƒÂÃ‚Â°ÃƒÂÃ‚Â»ÃƒÂÃ‚Â¸ÃƒÂÃ‚Â·ÃƒÂÃ‚Â¸Ãƒâ€˜Ã¢â€šÂ¬Ãƒâ€˜Ã†â€™ÃƒÂÃ‚ÂµÃƒÂÃ‚Â¼ ÃƒÂÃ‚Â±ÃƒÂÃ‚Â°ÃƒÂÃ‚Â·Ãƒâ€˜Ã†â€™ ÃƒÂÃ‚Â´ÃƒÂÃ‚Â°ÃƒÂÃ‚Â½ÃƒÂÃ‚Â½Ãƒâ€˜Ã¢â‚¬Â¹Ãƒâ€˜Ã¢â‚¬Â¦
        this.database = new CosmeticsDatabase(plugin);
        this.database.initialize();
        
        this.startUpdateTasks();
    }

    /**
     * ÃƒÂÃ¢â‚¬â€ÃƒÂÃ‚Â°ÃƒÂÃ‚Â¿Ãƒâ€˜Ã†â€™Ãƒâ€˜Ã‚ÂÃƒÂÃ‚ÂºÃƒÂÃ‚Â°ÃƒÂÃ‚ÂµÃƒâ€˜Ã¢â‚¬Å¡ ÃƒÂÃ‚Â²Ãƒâ€˜Ã‚ÂÃƒÂÃ‚Âµ ÃƒÂÃ‚Â·ÃƒÂÃ‚Â°ÃƒÂÃ‚Â´ÃƒÂÃ‚Â°Ãƒâ€˜Ã¢â‚¬Â¡ÃƒÂÃ‚Â¸ ÃƒÂÃ‚Â¾ÃƒÂÃ‚Â±ÃƒÂÃ‚Â½ÃƒÂÃ‚Â¾ÃƒÂÃ‚Â²ÃƒÂÃ‚Â»ÃƒÂÃ‚ÂµÃƒÂÃ‚Â½ÃƒÂÃ‚Â¸Ãƒâ€˜Ã‚Â
     */
    private void startUpdateTasks() {
        // ÃƒÂÃ…Â¾ÃƒÂÃ‚Â±ÃƒÂÃ‚Â½ÃƒÂÃ‚Â¾ÃƒÂÃ‚Â²ÃƒÂÃ‚Â»Ãƒâ€˜Ã‚ÂÃƒÂÃ‚ÂµÃƒÂÃ‚Â¼ Ãƒâ€˜Ã¢â‚¬Â¡ÃƒÂÃ‚Â°Ãƒâ€˜Ã‚ÂÃƒâ€˜Ã¢â‚¬Å¡ÃƒÂÃ‚Â¸Ãƒâ€˜Ã¢â‚¬Â Ãƒâ€˜Ã¢â‚¬Â¹ ÃƒÂÃ‚ÂºÃƒÂÃ‚Â°ÃƒÂÃ‚Â¶ÃƒÂÃ‚Â´Ãƒâ€˜Ã¢â‚¬Â¹ÃƒÂÃ‚Âµ 5 Ãƒâ€˜Ã¢â‚¬Å¡ÃƒÂÃ‚Â¸ÃƒÂÃ‚ÂºÃƒÂÃ‚Â¾ÃƒÂÃ‚Â² (4 Ãƒâ€˜Ã¢â€šÂ¬ÃƒÂÃ‚Â°ÃƒÂÃ‚Â·ÃƒÂÃ‚Â° ÃƒÂÃ‚Â² Ãƒâ€˜Ã‚ÂÃƒÂÃ‚ÂµÃƒÂÃ‚ÂºÃƒâ€˜Ã†â€™ÃƒÂÃ‚Â½ÃƒÂÃ‚Â´Ãƒâ€˜Ã†â€™)
        this.plugin.getSchedulerManager().runTaskTimer(
            PARTICLE_TASK_NAME,
            this::updateAllParticles,
            5L,
            5L
        );
        
        // ÃƒÂÃ…Â¾ÃƒÂÃ‚Â±ÃƒÂÃ‚Â½ÃƒÂÃ‚Â¾ÃƒÂÃ‚Â²ÃƒÂÃ‚Â»Ãƒâ€˜Ã‚ÂÃƒÂÃ‚ÂµÃƒÂÃ‚Â¼ ÃƒÂÃ‚Â¿ÃƒÂÃ‚Â¸Ãƒâ€˜Ã¢â‚¬Å¡ÃƒÂÃ‚Â¾ÃƒÂÃ‚Â¼Ãƒâ€˜Ã¢â‚¬Â ÃƒÂÃ‚ÂµÃƒÂÃ‚Â² ÃƒÂÃ‚ÂºÃƒÂÃ‚Â°ÃƒÂÃ‚Â¶ÃƒÂÃ‚Â´Ãƒâ€˜Ã¢â‚¬Â¹ÃƒÂÃ‚Âµ 2 Ãƒâ€˜Ã¢â‚¬Å¡ÃƒÂÃ‚Â¸ÃƒÂÃ‚ÂºÃƒÂÃ‚Â° (10 Ãƒâ€˜Ã¢â€šÂ¬ÃƒÂÃ‚Â°ÃƒÂÃ‚Â· ÃƒÂÃ‚Â² Ãƒâ€˜Ã‚ÂÃƒÂÃ‚ÂµÃƒÂÃ‚ÂºÃƒâ€˜Ã†â€™ÃƒÂÃ‚Â½ÃƒÂÃ‚Â´Ãƒâ€˜Ã†â€™)
        this.plugin.getSchedulerManager().runTaskTimer(
            PET_TASK_NAME,
            this::updateAllPets,
            2L,
            2L
        );
        
        // ÃƒÂÃ…Â¾ÃƒÂÃ‚Â±ÃƒÂÃ‚Â½ÃƒÂÃ‚Â¾ÃƒÂÃ‚Â²ÃƒÂÃ‚Â»Ãƒâ€˜Ã‚ÂÃƒÂÃ‚ÂµÃƒÂÃ‚Â¼ Ãƒâ€˜Ã‹â€ ÃƒÂÃ‚Â°Ãƒâ€˜Ã¢â€šÂ¬ÃƒÂÃ‚Â¸ÃƒÂÃ‚ÂºÃƒÂÃ‚Â¸ ÃƒÂÃ‚ÂºÃƒÂÃ‚Â°ÃƒÂÃ‚Â¶ÃƒÂÃ‚Â´Ãƒâ€˜Ã¢â‚¬Â¹ÃƒÂÃ‚Âµ 2 Ãƒâ€˜Ã¢â‚¬Å¡ÃƒÂÃ‚Â¸ÃƒÂÃ‚ÂºÃƒÂÃ‚Â°
        this.plugin.getSchedulerManager().runTaskTimer(
            BALLOON_TASK_NAME,
            this::updateAllBalloons,
            2L,
            2L
        );

        // Проверка прав: каждые 20 секунд (400 тиков) проверяем одного игрока
        this.plugin.getSchedulerManager().runTaskTimer(
            PERMISSION_TASK_NAME,
            this::auditOnlinePermissions,
            400L,
            400L
        );
    }

    /**
     * Выбирает одного игрока по round-robin и проверяет право smcosm
     * на потоке региона игрока (Folia-safe)
     */
    private void auditOnlinePermissions() {
        Collection<? extends Player> players = this.plugin.getServer().getOnlinePlayers();
        if (players.isEmpty()) {
            return;
        }

        Player[] playerArray = players.toArray(new Player[0]);
        if (this.auditPlayerIndex >= playerArray.length) {
            this.auditPlayerIndex = 0;
        }

        Player player = playerArray[this.auditPlayerIndex];
        this.auditPlayerIndex++;

        if (player == null || !player.isOnline()) {
            return;
        }

        // Запускаем проверку на потоке региона игрока (Folia-safe)
        this.plugin.getSchedulerManager().runEntityTask(player, "permission-audit-" + player.getUniqueId(), () -> {
            this.enforcePermissions(player);
        });
    }

    /**
     * Проверяет базовое право smcosm.
     * Если есть — скипаем, если нет — снимаем всю косметику.
     * ВАЖНО: вызывается на потоке региона игрока (runEntityTask).
     */
    private void enforcePermissions(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        if (player.hasPermission("smcosm")) {
            return;
        }

        this.unequipAllForPermissionLoss(player);
    }

    /**
     * Полностью снимает всю косметику у игрока, потерявшего право smcosm.
     * Вызывается на потоке региона игрока.
     */
    private void unequipAllForPermissionLoss(Player player) {
        UUID playerId = player.getUniqueId();
        boolean changed = false;

        // Снимаем все экипированные косметики
        UserCosmetics userCos = this.userCosmetics.get(playerId);
        if (userCos != null && !userCos.getAllEquipped().isEmpty()) {
            userCos.unequipAll(player);
            changed = true;
        }

        // Очищаем эффекты стрел
        ArrowEffectCosmetic.clearPlayerEffects(playerId);

        // Убираем активного питомца
        if (PetCosmetic.getActiveCosmeticId(playerId) != null) {
            PetCosmetic.unequipActivePet(player);
            changed = true;
        }

        // Убираем активный шарик
        if (BalloonCosmetic.getActiveCosmeticId(playerId) != null) {
            BalloonCosmetic.unequipActiveBalloon(player);
            changed = true;
        }

        if (changed) {
            this.savePlayerState(player);
        }
    }

    /**
     * ÃƒÂÃ…Â¾ÃƒÂÃ‚Â±ÃƒÂÃ‚Â½ÃƒÂÃ‚Â¾ÃƒÂÃ‚Â²ÃƒÂÃ‚Â»Ãƒâ€˜Ã‚ÂÃƒÂÃ‚ÂµÃƒâ€˜Ã¢â‚¬Å¡ Ãƒâ€˜Ã¢â‚¬Â¡ÃƒÂÃ‚Â°Ãƒâ€˜Ã‚ÂÃƒâ€˜Ã¢â‚¬Å¡ÃƒÂÃ‚Â¸Ãƒâ€˜Ã¢â‚¬Â Ãƒâ€˜Ã¢â‚¬Â¹ ÃƒÂÃ‚Â´ÃƒÂÃ‚Â»Ãƒâ€˜Ã‚Â ÃƒÂÃ‚Â²Ãƒâ€˜Ã‚ÂÃƒÂÃ‚ÂµÃƒâ€˜Ã¢â‚¬Â¦ ÃƒÂÃ‚Â¸ÃƒÂÃ‚Â³Ãƒâ€˜Ã¢â€šÂ¬ÃƒÂÃ‚Â¾ÃƒÂÃ‚ÂºÃƒÂÃ‚Â¾ÃƒÂÃ‚Â²
     */
    private void updateAllParticles() {
        for (UUID playerId : ParticleCosmetic.getAllActiveParticles()) {
            Player player = this.plugin.getServer().getPlayer(playerId);
            if (player != null && player.isOnline()) {
                UserCosmetics cosmetics = this.userCosmetics.get(playerId);
                if (cosmetics != null) {
                    Cosmetic equipped = cosmetics.getEquippedCosmetic(CosmeticCategory.PARTICLE_EFFECT);
                    if (equipped != null) {
                        UserCosmeticSettings settings = this.getOrCreateSettings(playerId);
                        if (!settings.isShowMyEffectsToOthers()) {
                            continue;
                        }
                        if (!this.canRestoreForSelf(settings, CosmeticCategory.PARTICLE_EFFECT)) {
                            continue;
                        }
                        this.plugin.getSchedulerManager().runEntityTask(player, "particle-update-" + playerId, () -> {
                            equipped.update(player);
                        });
                    }
                }
            }
        }
    }

    /**
     * ÃƒÂÃ…Â¾ÃƒÂÃ‚Â±ÃƒÂÃ‚Â½ÃƒÂÃ‚Â¾ÃƒÂÃ‚Â²ÃƒÂÃ‚Â»Ãƒâ€˜Ã‚ÂÃƒÂÃ‚ÂµÃƒâ€˜Ã¢â‚¬Å¡ ÃƒÂÃ‚Â²Ãƒâ€˜Ã‚ÂÃƒÂÃ‚ÂµÃƒâ€˜Ã¢â‚¬Â¦ ÃƒÂÃ‚Â¿ÃƒÂÃ‚Â¸Ãƒâ€˜Ã¢â‚¬Å¡ÃƒÂÃ‚Â¾ÃƒÂÃ‚Â¼Ãƒâ€˜Ã¢â‚¬Â ÃƒÂÃ‚ÂµÃƒÂÃ‚Â²
     */
    private void updateAllPets() {
        for (UUID playerId : PetCosmetic.getAllActivePets()) {
            Player player = this.plugin.getServer().getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                continue;
            }
            UserCosmetics cosmetics = this.userCosmetics.get(playerId);
            if (cosmetics == null) {
                continue;
            }
            Cosmetic equipped = cosmetics.getEquippedCosmetic(CosmeticCategory.PET);
            if (equipped == null) {
                continue;
            }
            this.plugin.getSchedulerManager().runEntityTask(player, "pet-update-" + playerId, () -> {
                if (!player.isOnline()) {
                    return;
                }
                UUID petId = PetCosmetic.getActivePetId(player);
                if (petId == null) {
                    return;
                }
                Entity pet = player.getWorld().getEntity(petId);
                if (pet == null) {
                    return;
                }
                if (!Bukkit.isOwnedByCurrentRegion(pet)) {
                    this.recallStrandedEntity(player, pet);
                    return;
                }
                equipped.update(player);
                this.applyPetVisibility(player, pet);
            });
        }
    }

    private void recallStrandedEntity(Player owner, Entity entity) {
        Location rescue = owner.getLocation().add(2.0, 0.0, 2.0);
        entity.getScheduler().run(this.plugin, task -> entity.teleportAsync(rescue), null);
    }

    private void applyPetVisibility(Player owner, Entity pet) {
        if (owner == null || pet == null || !owner.isOnline() || !pet.isValid()) {
            return;
        }

        UserCosmeticSettings ownerSettings = this.getOrCreateSettings(owner.getUniqueId());
        for (Player viewer : owner.getWorld().getPlayers()) {
            if (!Bukkit.isOwnedByCurrentRegion(viewer)) {
                continue;
            }
            UserCosmeticSettings viewerSettings = this.getOrCreateSettings(viewer.getUniqueId());
            boolean viewerAllows = viewerSettings.shouldSeeEffect(owner.getUniqueId(), CosmeticCategory.PET);
            boolean ownerAllows = ownerSettings.shouldViewerSeeMyEffect(viewer.getUniqueId(), CosmeticCategory.PET);
            if (viewerAllows && ownerAllows) {
                viewer.showEntity(this.plugin, pet);
            } else {
                viewer.hideEntity(this.plugin, pet);
            }
        }
    }

    /**
     * ÃƒÂÃ…Â¾ÃƒÂÃ‚Â±ÃƒÂÃ‚Â½ÃƒÂÃ‚Â¾ÃƒÂÃ‚Â²ÃƒÂÃ‚Â»Ãƒâ€˜Ã‚ÂÃƒÂÃ‚ÂµÃƒâ€˜Ã¢â‚¬Å¡ ÃƒÂÃ‚Â²Ãƒâ€˜Ã‚ÂÃƒÂÃ‚Âµ Ãƒâ€˜Ã‹â€ ÃƒÂÃ‚Â°Ãƒâ€˜Ã¢â€šÂ¬ÃƒÂÃ‚Â¸ÃƒÂÃ‚ÂºÃƒÂÃ‚Â¸
     */
    private void updateAllBalloons() {
        for (UUID playerId : BalloonCosmetic.getAllActiveBalloons()) {
            Player player = this.plugin.getServer().getPlayer(playerId);
            if (player != null && player.isOnline()) {
                UserCosmetics cosmetics = this.userCosmetics.get(playerId);
                if (cosmetics != null) {
                    Cosmetic equipped = cosmetics.getEquippedCosmetic(CosmeticCategory.BALLOON);
                    if (equipped instanceof BalloonCosmetic balloon) {
                        UserCosmeticSettings settings = this.getOrCreateSettings(playerId);
                        if (!this.canRestoreForSelf(settings, CosmeticCategory.BALLOON)
                                && settings.getCategoryVisibility(CosmeticCategory.BALLOON) != UserCosmeticSettings.VisibilityMode.OTHERS_ONLY) {
                            continue;
                        }
                        this.plugin.getSchedulerManager().runEntityTask(player, "balloon-update-" + playerId, () -> {
                            if (!player.isOnline()) {
                                return;
                            }
                            UUID balloonId = BalloonCosmetic.getActiveBalloonId(playerId);
                            if (balloonId == null) {
                                return;
                            }
                            Entity balloonEntity = player.getWorld().getEntity(balloonId);
                            if (balloonEntity == null) {
                                return;
                            }
                            if (!Bukkit.isOwnedByCurrentRegion(balloonEntity)) {
                                this.recallStrandedEntity(player, balloonEntity);
                                return;
                            }
                            UUID leashId = BalloonCosmetic.getLeashEntityId(playerId);
                            Entity leashEntity = leashId == null ? null : player.getWorld().getEntity(leashId);
                            if (leashEntity != null && !Bukkit.isOwnedByCurrentRegion(leashEntity)) {
                                leashEntity = null;
                            }
                            balloon.update(player);
                            this.applyBalloonVisibility(player, balloonEntity, leashEntity);
                        });
                    }
                }
            }
        }
    }

    private void applyBalloonVisibility(Player owner, Entity balloonEntity, Entity leashEntity) {
        if (owner == null || balloonEntity == null || !owner.isOnline() || !balloonEntity.isValid()) {
            return;
        }
        UserCosmeticSettings ownerSettings = this.getOrCreateSettings(owner.getUniqueId());
        for (Player viewer : owner.getWorld().getPlayers()) {
            if (!Bukkit.isOwnedByCurrentRegion(viewer)) {
                continue;
            }
            UserCosmeticSettings viewerSettings = this.getOrCreateSettings(viewer.getUniqueId());
            boolean viewerAllows = viewerSettings.shouldSeeEffect(owner.getUniqueId(), CosmeticCategory.BALLOON);
            boolean ownerAllows = ownerSettings.shouldViewerSeeMyEffect(viewer.getUniqueId(), CosmeticCategory.BALLOON);
            if (viewerAllows && ownerAllows) {
                viewer.showEntity(this.plugin, balloonEntity);
                if (leashEntity != null && leashEntity.isValid()) {
                    viewer.showEntity(this.plugin, leashEntity);
                }
            } else {
                viewer.hideEntity(this.plugin, balloonEntity);
                if (leashEntity != null && leashEntity.isValid()) {
                    viewer.hideEntity(this.plugin, leashEntity);
                }
            }
        }
    }



    /**
     * ÃƒÂÃ…Â¸ÃƒÂÃ‚Â¾ÃƒÂÃ‚Â»Ãƒâ€˜Ã†â€™Ãƒâ€˜Ã¢â‚¬Â¡ÃƒÂÃ‚Â°ÃƒÂÃ‚ÂµÃƒâ€˜Ã¢â‚¬Å¡ ÃƒÂÃ‚Â¸ÃƒÂÃ‚Â»ÃƒÂÃ‚Â¸ Ãƒâ€˜Ã‚ÂÃƒÂÃ‚Â¾ÃƒÂÃ‚Â·ÃƒÂÃ‚Â´ÃƒÂÃ‚Â°ÃƒÂÃ‚ÂµÃƒâ€˜Ã¢â‚¬Å¡ ÃƒÂÃ‚Â´ÃƒÂÃ‚Â°ÃƒÂÃ‚Â½ÃƒÂÃ‚Â½Ãƒâ€˜Ã¢â‚¬Â¹ÃƒÂÃ‚Âµ ÃƒÂÃ‚Â¿ÃƒÂÃ‚Â¾ÃƒÂÃ‚Â»Ãƒâ€˜Ã…â€™ÃƒÂÃ‚Â·ÃƒÂÃ‚Â¾ÃƒÂÃ‚Â²ÃƒÂÃ‚Â°Ãƒâ€˜Ã¢â‚¬Å¡ÃƒÂÃ‚ÂµÃƒÂÃ‚Â»Ãƒâ€˜Ã‚Â
     */
    public UserCosmetics getOrCreate(UUID playerId) {
        return this.userCosmetics.computeIfAbsent(playerId, UserCosmetics::new);
    }

    /**
     * ÃƒÂÃ…Â¸ÃƒÂÃ‚Â¾ÃƒÂÃ‚Â»Ãƒâ€˜Ã†â€™Ãƒâ€˜Ã¢â‚¬Â¡ÃƒÂÃ‚Â°ÃƒÂÃ‚ÂµÃƒâ€˜Ã¢â‚¬Å¡ ÃƒÂÃ‚Â´ÃƒÂÃ‚Â°ÃƒÂÃ‚Â½ÃƒÂÃ‚Â½Ãƒâ€˜Ã¢â‚¬Â¹ÃƒÂÃ‚Âµ ÃƒÂÃ‚Â¿ÃƒÂÃ‚Â¾ÃƒÂÃ‚Â»Ãƒâ€˜Ã…â€™ÃƒÂÃ‚Â·ÃƒÂÃ‚Â¾ÃƒÂÃ‚Â²ÃƒÂÃ‚Â°Ãƒâ€˜Ã¢â‚¬Å¡ÃƒÂÃ‚ÂµÃƒÂÃ‚Â»Ãƒâ€˜Ã‚Â
     */
    public UserCosmetics get(UUID playerId) {
        return this.userCosmetics.get(playerId);
    }

    /**
     * ÃƒÂÃ…Â¸ÃƒÂÃ‚Â¾ÃƒÂÃ‚Â»Ãƒâ€˜Ã†â€™Ãƒâ€˜Ã¢â‚¬Â¡ÃƒÂÃ‚Â°ÃƒÂÃ‚ÂµÃƒâ€˜Ã¢â‚¬Å¡ ÃƒÂÃ‚Â¸ÃƒÂÃ‚Â»ÃƒÂÃ‚Â¸ Ãƒâ€˜Ã‚ÂÃƒÂÃ‚Â¾ÃƒÂÃ‚Â·ÃƒÂÃ‚Â´ÃƒÂÃ‚Â°ÃƒÂÃ‚ÂµÃƒâ€˜Ã¢â‚¬Å¡ ÃƒÂÃ‚Â½ÃƒÂÃ‚Â°Ãƒâ€˜Ã‚ÂÃƒâ€˜Ã¢â‚¬Å¡Ãƒâ€˜Ã¢â€šÂ¬ÃƒÂÃ‚Â¾ÃƒÂÃ‚Â¹ÃƒÂÃ‚ÂºÃƒÂÃ‚Â¸ ÃƒÂÃ‚Â¿ÃƒÂÃ‚Â¾ÃƒÂÃ‚Â»Ãƒâ€˜Ã…â€™ÃƒÂÃ‚Â·ÃƒÂÃ‚Â¾ÃƒÂÃ‚Â²ÃƒÂÃ‚Â°Ãƒâ€˜Ã¢â‚¬Å¡ÃƒÂÃ‚ÂµÃƒÂÃ‚Â»Ãƒâ€˜Ã‚Â
     */
    public UserCosmeticSettings getOrCreateSettings(UUID playerId) {
        return this.userSettings.computeIfAbsent(playerId, UserCosmeticSettings::new);
    }

    /**
     * ÃƒÂÃ…Â¸ÃƒÂÃ‚Â¾ÃƒÂÃ‚Â»Ãƒâ€˜Ã†â€™Ãƒâ€˜Ã¢â‚¬Â¡ÃƒÂÃ‚Â°ÃƒÂÃ‚ÂµÃƒâ€˜Ã¢â‚¬Å¡ ÃƒÂÃ‚Â½ÃƒÂÃ‚Â°Ãƒâ€˜Ã‚ÂÃƒâ€˜Ã¢â‚¬Å¡Ãƒâ€˜Ã¢â€šÂ¬ÃƒÂÃ‚Â¾ÃƒÂÃ‚Â¹ÃƒÂÃ‚ÂºÃƒÂÃ‚Â¸ ÃƒÂÃ‚Â¿ÃƒÂÃ‚Â¾ÃƒÂÃ‚Â»Ãƒâ€˜Ã…â€™ÃƒÂÃ‚Â·ÃƒÂÃ‚Â¾ÃƒÂÃ‚Â²ÃƒÂÃ‚Â°Ãƒâ€˜Ã¢â‚¬Å¡ÃƒÂÃ‚ÂµÃƒÂÃ‚Â»Ãƒâ€˜Ã‚Â
     */
    public UserCosmeticSettings getSettings(UUID playerId) {
        return this.userSettings.get(playerId);
    }

    /**
     * ÃƒÂÃ‚Â­ÃƒÂÃ‚ÂºÃƒÂÃ‚Â¸ÃƒÂÃ‚Â¿ÃƒÂÃ‚Â¸Ãƒâ€˜Ã¢â€šÂ¬Ãƒâ€˜Ã†â€™ÃƒÂÃ‚ÂµÃƒâ€˜Ã¢â‚¬Å¡ ÃƒÂÃ‚ÂºÃƒÂÃ‚Â¾Ãƒâ€˜Ã‚ÂÃƒÂÃ‚Â¼ÃƒÂÃ‚ÂµÃƒâ€˜Ã¢â‚¬Å¡ÃƒÂÃ‚Â¸ÃƒÂÃ‚ÂºÃƒâ€˜Ã†â€™ ÃƒÂÃ‚Â¸ÃƒÂÃ‚Â³Ãƒâ€˜Ã¢â€šÂ¬ÃƒÂÃ‚Â¾ÃƒÂÃ‚ÂºÃƒâ€˜Ã†â€™
     */
    public boolean equip(Player player, Cosmetic cosmetic) {
        if (!cosmetic.isEnabled()) {
            return false;
        }
        
        if (!cosmetic.hasPermission(player)) {
            return false;
        }
        
        UserCosmetics userCos = this.getOrCreate(player.getUniqueId());
        userCos.equipCosmetic(cosmetic, player);
        userCos.clearSavedCosmetic(cosmetic.getCategory());
        
        // ÃƒÂÃ‚Â¡ÃƒÂÃ‚Â¾Ãƒâ€˜Ã¢â‚¬Â¦Ãƒâ€˜Ã¢â€šÂ¬ÃƒÂÃ‚Â°ÃƒÂÃ‚Â½Ãƒâ€˜Ã‚ÂÃƒÂÃ‚ÂµÃƒÂÃ‚Â¼ ÃƒÂÃ‚Â² ÃƒÂÃ‚Â±ÃƒÂÃ‚Â°ÃƒÂÃ‚Â·Ãƒâ€˜Ã†â€™ ÃƒÂÃ‚Â´ÃƒÂÃ‚Â°ÃƒÂÃ‚Â½ÃƒÂÃ‚Â½Ãƒâ€˜Ã¢â‚¬Â¹Ãƒâ€˜Ã¢â‚¬Â¦
        
        this.plugin.getDebugSystem().log("UserCosmeticsManager", 
            player.getName() + " Ãƒâ€˜Ã‚ÂÃƒÂÃ‚ÂºÃƒÂÃ‚Â¸ÃƒÂÃ‚Â¿ÃƒÂÃ‚Â¸Ãƒâ€˜Ã¢â€šÂ¬ÃƒÂÃ‚Â¾ÃƒÂÃ‚Â²ÃƒÂÃ‚Â°ÃƒÂÃ‚Â» " + cosmetic.getName() + " (" + cosmetic.getCategory().getDisplayName() + ")");
        
        this.savePlayerState(player);
        return true;
    }

    /**
     * ÃƒÂÃ‚Â¡ÃƒÂÃ‚Â½ÃƒÂÃ‚Â¸ÃƒÂÃ‚Â¼ÃƒÂÃ‚Â°ÃƒÂÃ‚ÂµÃƒâ€˜Ã¢â‚¬Å¡ ÃƒÂÃ‚ÂºÃƒÂÃ‚Â¾Ãƒâ€˜Ã‚ÂÃƒÂÃ‚Â¼ÃƒÂÃ‚ÂµÃƒâ€˜Ã¢â‚¬Å¡ÃƒÂÃ‚Â¸ÃƒÂÃ‚ÂºÃƒâ€˜Ã†â€™ ÃƒÂÃ‚ÂºÃƒÂÃ‚Â°Ãƒâ€˜Ã¢â‚¬Å¡ÃƒÂÃ‚ÂµÃƒÂÃ‚Â³ÃƒÂÃ‚Â¾Ãƒâ€˜Ã¢â€šÂ¬ÃƒÂÃ‚Â¸ÃƒÂÃ‚Â¸ Ãƒâ€˜Ã†â€™ ÃƒÂÃ‚Â¸ÃƒÂÃ‚Â³Ãƒâ€˜Ã¢â€šÂ¬ÃƒÂÃ‚Â¾ÃƒÂÃ‚ÂºÃƒÂÃ‚Â°
     */
    public boolean unequip(Player player, CosmeticCategory category) {
        UserCosmetics userCos = this.userCosmetics.get(player.getUniqueId());
        if (category == CosmeticCategory.ARROW_EFFECT) {
            ArrowEffectCosmetic.clearPlayerEffects(player.getUniqueId());
            this.savePlayerState(player);
            return true;
        }
        if (userCos == null) {
            return false;
        }
        
        Cosmetic equipped = userCos.getEquippedCosmetic(category);
        if (equipped == null) {
            return false;
        }
        
        userCos.unequipCosmetic(category, player);
        userCos.clearSavedCosmetic(category);
        
        // ÃƒÂÃ‚Â£ÃƒÂÃ‚Â´ÃƒÂÃ‚Â°ÃƒÂÃ‚Â»Ãƒâ€˜Ã‚ÂÃƒÂÃ‚ÂµÃƒÂÃ‚Â¼ ÃƒÂÃ‚Â¸ÃƒÂÃ‚Â· ÃƒÂÃ‚Â±ÃƒÂÃ‚Â°ÃƒÂÃ‚Â·Ãƒâ€˜Ã¢â‚¬Â¹ ÃƒÂÃ‚Â´ÃƒÂÃ‚Â°ÃƒÂÃ‚Â½ÃƒÂÃ‚Â½Ãƒâ€˜Ã¢â‚¬Â¹Ãƒâ€˜Ã¢â‚¬Â¦
        
        this.plugin.getDebugSystem().log("UserCosmeticsManager", 
            player.getName() + " Ãƒâ€˜Ã‚ÂÃƒÂÃ‚Â½Ãƒâ€˜Ã‚ÂÃƒÂÃ‚Â» " + equipped.getName() + " (" + category.getDisplayName() + ")");
        
        this.savePlayerState(player);
        return true;
    }

    /**
     * ÃƒÂÃ‚Â¡ÃƒÂÃ‚Â½ÃƒÂÃ‚Â¸ÃƒÂÃ‚Â¼ÃƒÂÃ‚Â°ÃƒÂÃ‚ÂµÃƒâ€˜Ã¢â‚¬Å¡ ÃƒÂÃ‚Â²Ãƒâ€˜Ã‚ÂÃƒâ€˜Ã…Â½ ÃƒÂÃ‚ÂºÃƒÂÃ‚Â¾Ãƒâ€˜Ã‚ÂÃƒÂÃ‚Â¼ÃƒÂÃ‚ÂµÃƒâ€˜Ã¢â‚¬Å¡ÃƒÂÃ‚Â¸ÃƒÂÃ‚ÂºÃƒâ€˜Ã†â€™ Ãƒâ€˜Ã†â€™ ÃƒÂÃ‚Â¸ÃƒÂÃ‚Â³Ãƒâ€˜Ã¢â€šÂ¬ÃƒÂÃ‚Â¾ÃƒÂÃ‚ÂºÃƒÂÃ‚Â°
     */
    public void unequipAll(Player player) {
        UserCosmetics userCos = this.userCosmetics.get(player.getUniqueId());
        if (userCos != null) {
            userCos.unequipAll(player);
            userCos.clearAllSavedCosmetics();
        }
        ArrowEffectCosmetic.clearPlayerEffects(player.getUniqueId());
        this.temporaryArrowEffects.remove(player.getUniqueId());
        
        this.plugin.getDebugSystem().log("UserCosmeticsManager", player.getName() + " unequipped all cosmetics");
        this.savePlayerState(player);
    }

    public boolean unequipTemporary(Player player, CosmeticCategory category) {
        UserCosmetics userCos = this.userCosmetics.get(player.getUniqueId());
        if (category == CosmeticCategory.ARROW_EFFECT) {
            return false;
        }
        if (userCos == null) {
            return false;
        }
        Cosmetic equipped = userCos.getEquippedCosmetic(category);
        if (equipped == null) {
            return false;
        }
        userCos.stashAndUnequipCosmetic(category, player);
        return true;
    }

    public void unequipAllTemporary(Player player) {
        UUID playerId = player.getUniqueId();
        UserCosmetics userCos = this.userCosmetics.get(player.getUniqueId());
        if (userCos != null) {
            userCos.stashAndUnequipAll(player);
        }
        TemporaryArrowEffects snapshot = TemporaryArrowEffects.capture(playerId);
        if (!snapshot.isEmpty()) {
            this.temporaryArrowEffects.put(playerId, snapshot);
        }
        ArrowEffectCosmetic.clearPlayerEffects(playerId);
    }

    public void hideEffectCategoriesTemporary(Player player) {
        UUID playerId = player.getUniqueId();
        UserCosmetics userCos = this.userCosmetics.get(playerId);
        if (userCos != null) {
            userCos.stashAndUnequipCosmetic(CosmeticCategory.PARTICLE_EFFECT, player);
        }
        TemporaryArrowEffects snapshot = TemporaryArrowEffects.capture(playerId);
        if (!snapshot.isEmpty()) {
            this.temporaryArrowEffects.put(playerId, snapshot);
        }
        ArrowEffectCosmetic.clearPlayerEffects(playerId);
    }

    public void restoreEffectCategoriesTemporary(Player player) {
        UUID playerId = player.getUniqueId();
        UserCosmetics userCos = this.userCosmetics.get(playerId);
        if (userCos != null) {
            Cosmetic particleCosmetic = userCos.getSavedCosmetic(CosmeticCategory.PARTICLE_EFFECT);
            if (particleCosmetic != null) {
                particleCosmetic.equip(player);
                userCos.setEquippedCosmetic(CosmeticCategory.PARTICLE_EFFECT, particleCosmetic);
            }
            userCos.clearSavedCosmetic(CosmeticCategory.PARTICLE_EFFECT);
        }

        TemporaryArrowEffects snapshot = this.temporaryArrowEffects.remove(playerId);
        if (snapshot != null) {
            snapshot.restore(playerId, player);
        }
    }

    /**
     *    
     */
    public Cosmetic getEquipped(UUID playerId, CosmeticCategory category) {
        UserCosmetics userCos = this.userCosmetics.get(playerId);
        if (userCos == null) {
            return null;
        }
        return userCos.getEquippedCosmetic(category);
    }

    /**
     * ÃƒÂÃ…Â¸Ãƒâ€˜Ã¢â€šÂ¬ÃƒÂÃ‚Â¾ÃƒÂÃ‚Â²ÃƒÂÃ‚ÂµÃƒâ€˜Ã¢â€šÂ¬Ãƒâ€˜Ã‚ÂÃƒÂÃ‚ÂµÃƒâ€˜Ã¢â‚¬Å¡, Ãƒâ€˜Ã‚ÂÃƒÂÃ‚ÂºÃƒÂÃ‚Â¸ÃƒÂÃ‚Â¿ÃƒÂÃ‚Â¸Ãƒâ€˜Ã¢â€šÂ¬ÃƒÂÃ‚Â¾ÃƒÂÃ‚Â²ÃƒÂÃ‚Â°ÃƒÂÃ‚Â½ÃƒÂÃ‚Â° ÃƒÂÃ‚Â»ÃƒÂÃ‚Â¸ ÃƒÂÃ‚ÂºÃƒÂÃ‚Â¾Ãƒâ€˜Ã‚ÂÃƒÂÃ‚Â¼ÃƒÂÃ‚ÂµÃƒâ€˜Ã¢â‚¬Å¡ÃƒÂÃ‚Â¸ÃƒÂÃ‚ÂºÃƒÂÃ‚Â° Ãƒâ€˜Ã†â€™ ÃƒÂÃ‚Â¸ÃƒÂÃ‚Â³Ãƒâ€˜Ã¢â€šÂ¬ÃƒÂÃ‚Â¾ÃƒÂÃ‚ÂºÃƒÂÃ‚Â°
     */
    public boolean hasEquipped(UUID playerId, CosmeticCategory category) {
        UserCosmetics userCos = this.userCosmetics.get(playerId);
        if (userCos == null) {
            return false;
        }
        return userCos.hasEquipped(category);
    }

    /**
     * ÃƒÂÃ…Â¾Ãƒâ€˜Ã¢â‚¬Â¡ÃƒÂÃ‚Â¸Ãƒâ€˜Ã¢â‚¬Â°ÃƒÂÃ‚Â°ÃƒÂÃ‚ÂµÃƒâ€˜Ã¢â‚¬Å¡ ÃƒÂÃ‚Â´ÃƒÂÃ‚Â°ÃƒÂÃ‚Â½ÃƒÂÃ‚Â½Ãƒâ€˜Ã¢â‚¬Â¹ÃƒÂÃ‚Âµ ÃƒÂÃ‚Â¸ÃƒÂÃ‚Â³Ãƒâ€˜Ã¢â€šÂ¬ÃƒÂÃ‚Â¾ÃƒÂÃ‚ÂºÃƒÂÃ‚Â° ÃƒÂÃ‚Â¿Ãƒâ€˜Ã¢â€šÂ¬ÃƒÂÃ‚Â¸ ÃƒÂÃ‚Â²Ãƒâ€˜Ã¢â‚¬Â¹Ãƒâ€˜Ã¢â‚¬Â¦ÃƒÂÃ‚Â¾ÃƒÂÃ‚Â´ÃƒÂÃ‚Âµ (ÃƒÂÃ‚ÂÃƒÂÃ¢â‚¬Â¢ Ãƒâ€˜Ã†â€™ÃƒÂÃ‚Â´ÃƒÂÃ‚Â°ÃƒÂÃ‚Â»Ãƒâ€˜Ã‚ÂÃƒÂÃ‚ÂµÃƒâ€˜Ã¢â‚¬Å¡ ÃƒÂÃ‚Â¸ÃƒÂÃ‚Â· ÃƒÂÃ‚Â±ÃƒÂÃ‚Â°ÃƒÂÃ‚Â·Ãƒâ€˜Ã¢â‚¬Â¹ - Ãƒâ€˜Ã‚ÂÃƒÂÃ‚Â¾Ãƒâ€˜Ã¢â‚¬Â¦Ãƒâ€˜Ã¢â€šÂ¬ÃƒÂÃ‚Â°ÃƒÂÃ‚Â½Ãƒâ€˜Ã‚ÂÃƒÂÃ‚ÂµÃƒÂÃ‚Â¼!)
     */
    public void handlePlayerQuit(Player player) {
        this.savePlayerState(player);
        // ÃƒÂÃ‚Â¡ÃƒÂÃ‚Â¾Ãƒâ€˜Ã¢â‚¬Â¦Ãƒâ€˜Ã¢â€šÂ¬ÃƒÂÃ‚Â°ÃƒÂÃ‚Â½Ãƒâ€˜Ã‚ÂÃƒÂÃ‚ÂµÃƒÂÃ‚Â¼ ÃƒÂÃ‚Â½ÃƒÂÃ‚Â°Ãƒâ€˜Ã‚ÂÃƒâ€˜Ã¢â‚¬Å¡Ãƒâ€˜Ã¢â€šÂ¬ÃƒÂÃ‚Â¾ÃƒÂÃ‚Â¹ÃƒÂÃ‚ÂºÃƒÂÃ‚Â¸ ÃƒÂÃ‚Â² ÃƒÂÃ‚Â±ÃƒÂÃ‚Â°ÃƒÂÃ‚Â·Ãƒâ€˜Ã†â€™ ÃƒÂÃ‚Â´ÃƒÂÃ‚Â°ÃƒÂÃ‚Â½ÃƒÂÃ‚Â½Ãƒâ€˜Ã¢â‚¬Â¹Ãƒâ€˜Ã¢â‚¬Â¦ ÃƒÂÃ‚Â¿ÃƒÂÃ‚ÂµÃƒâ€˜Ã¢â€šÂ¬ÃƒÂÃ‚ÂµÃƒÂÃ‚Â´ ÃƒÂÃ‚Â²Ãƒâ€˜Ã¢â‚¬Â¹Ãƒâ€˜Ã¢â‚¬Â¦ÃƒÂÃ‚Â¾ÃƒÂÃ‚Â´ÃƒÂÃ‚Â¾ÃƒÂÃ‚Â¼
        
        // ÃƒÂÃ‚Â¡ÃƒÂÃ‚Â½ÃƒÂÃ‚Â¸ÃƒÂÃ‚Â¼ÃƒÂÃ‚Â°ÃƒÂÃ‚ÂµÃƒÂÃ‚Â¼ ÃƒÂÃ‚Â°ÃƒÂÃ‚ÂºÃƒâ€˜Ã¢â‚¬Å¡ÃƒÂÃ‚Â¸ÃƒÂÃ‚Â²ÃƒÂÃ‚Â½Ãƒâ€˜Ã¢â‚¬Â¹ÃƒÂÃ‚Âµ Ãƒâ€˜Ã‚ÂÃƒâ€˜Ã†â€™Ãƒâ€˜Ã¢â‚¬Â°ÃƒÂÃ‚Â½ÃƒÂÃ‚Â¾Ãƒâ€˜Ã‚ÂÃƒâ€˜Ã¢â‚¬Å¡ÃƒÂÃ‚Â¸ (ÃƒÂÃ‚Â¿ÃƒÂÃ‚Â¸Ãƒâ€˜Ã¢â‚¬Å¡ÃƒÂÃ‚Â¾ÃƒÂÃ‚Â¼Ãƒâ€˜Ã¢â‚¬Â ÃƒÂÃ‚ÂµÃƒÂÃ‚Â², Ãƒâ€˜Ã‹â€ ÃƒÂÃ‚Â°Ãƒâ€˜Ã¢â€šÂ¬ÃƒÂÃ‚Â¸ÃƒÂÃ‚ÂºÃƒÂÃ‚Â¸) ÃƒÂÃ‚Â½ÃƒÂÃ‚Â¾ ÃƒÂÃ‚ÂÃƒÂÃ¢â‚¬Â¢ Ãƒâ€˜Ã†â€™ÃƒÂÃ‚Â´ÃƒÂÃ‚Â°ÃƒÂÃ‚Â»Ãƒâ€˜Ã‚ÂÃƒÂÃ‚ÂµÃƒÂÃ‚Â¼ ÃƒÂÃ‚Â¸ÃƒÂÃ‚Â· ÃƒÂÃ‚Â±ÃƒÂÃ‚Â°ÃƒÂÃ‚Â·Ãƒâ€˜Ã¢â‚¬Â¹
        UserCosmetics userCos = this.userCosmetics.remove(player.getUniqueId());
        if (userCos != null) {
            userCos.unequipAll(player);
        }
        this.userSettings.remove(player.getUniqueId());
    }

    /**
     * ÃƒÂÃ…Â¾Ãƒâ€˜Ã¢â‚¬Â¡ÃƒÂÃ‚Â¸Ãƒâ€˜Ã¢â‚¬Â°ÃƒÂÃ‚Â°ÃƒÂÃ‚ÂµÃƒâ€˜Ã¢â‚¬Å¡ ÃƒÂÃ‚Â²Ãƒâ€˜Ã‚ÂÃƒâ€˜Ã…Â½ ÃƒÂÃ‚ÂºÃƒÂÃ‚Â¾Ãƒâ€˜Ã‚ÂÃƒÂÃ‚Â¼ÃƒÂÃ‚ÂµÃƒâ€˜Ã¢â‚¬Å¡ÃƒÂÃ‚Â¸ÃƒÂÃ‚ÂºÃƒâ€˜Ã†â€™ ÃƒÂÃ‚Â²Ãƒâ€˜Ã‚ÂÃƒÂÃ‚ÂµÃƒâ€˜Ã¢â‚¬Â¦ ÃƒÂÃ‚Â¸ÃƒÂÃ‚Â³Ãƒâ€˜Ã¢â€šÂ¬ÃƒÂÃ‚Â¾ÃƒÂÃ‚ÂºÃƒÂÃ‚Â¾ÃƒÂÃ‚Â²
     */
    public void clearAllCosmetics() {
        for (Player player : this.plugin.getServer().getOnlinePlayers()) {
            UserCosmetics userCos = this.userCosmetics.get(player.getUniqueId());
            if (userCos != null) {
                userCos.unequipAll(player);
            }
            ArrowEffectCosmetic.clearPlayerEffects(player.getUniqueId());
        }
        this.userCosmetics.clear();
        
        // ÃƒÂÃ…Â¾Ãƒâ€˜Ã¢â‚¬Â¡ÃƒÂÃ‚Â¸Ãƒâ€˜Ã¢â‚¬Â°ÃƒÂÃ‚Â°ÃƒÂÃ‚ÂµÃƒÂÃ‚Â¼ Ãƒâ€˜Ã‚ÂÃƒâ€˜Ã¢â‚¬Å¡ÃƒÂÃ‚Â°Ãƒâ€˜Ã¢â‚¬Å¡ÃƒÂÃ‚Â¸Ãƒâ€˜Ã¢â‚¬Â¡ÃƒÂÃ‚ÂµÃƒâ€˜Ã‚ÂÃƒÂÃ‚ÂºÃƒÂÃ‚Â¸ÃƒÂÃ‚Âµ ÃƒÂÃ‚Â´ÃƒÂÃ‚Â°ÃƒÂÃ‚Â½ÃƒÂÃ‚Â½Ãƒâ€˜Ã¢â‚¬Â¹ÃƒÂÃ‚Âµ
        PetCosmetic.removeAllPets();
        ParticleCosmetic.removeAllParticles();
        BalloonCosmetic.removeAllBalloons();
        ArrowEffectCosmetic.removeAllEffects();
        DeathEffectCosmetic.removeAllEffects();
        
        // ÃƒÂÃ…Â¾Ãƒâ€˜Ã¢â‚¬Å¡ÃƒÂÃ‚Â¼ÃƒÂÃ‚ÂµÃƒÂÃ‚Â½Ãƒâ€˜Ã‚ÂÃƒÂÃ‚ÂµÃƒÂÃ‚Â¼ ÃƒÂÃ‚Â·ÃƒÂÃ‚Â°ÃƒÂÃ‚Â´ÃƒÂÃ‚Â°Ãƒâ€˜Ã¢â‚¬Â¡ÃƒÂÃ‚Â¸ ÃƒÂÃ‚Â¾ÃƒÂÃ‚Â±ÃƒÂÃ‚Â½ÃƒÂÃ‚Â¾ÃƒÂÃ‚Â²ÃƒÂÃ‚Â»ÃƒÂÃ‚ÂµÃƒÂÃ‚Â½ÃƒÂÃ‚Â¸Ãƒâ€˜Ã‚Â
        this.plugin.getSchedulerManager().cancelTask(PARTICLE_TASK_NAME);
        this.plugin.getSchedulerManager().cancelTask(PET_TASK_NAME);
        this.plugin.getSchedulerManager().cancelTask(BALLOON_TASK_NAME);
        this.plugin.getSchedulerManager().cancelTask(PERMISSION_TASK_NAME);
    }

    /**
     * ÃƒÂÃ…Â¸ÃƒÂÃ‚Â¾ÃƒÂÃ‚Â»Ãƒâ€˜Ã†â€™Ãƒâ€˜Ã¢â‚¬Â¡ÃƒÂÃ‚Â°ÃƒÂÃ‚ÂµÃƒâ€˜Ã¢â‚¬Å¡ ÃƒÂÃ‚Â²Ãƒâ€˜Ã‚ÂÃƒÂÃ‚ÂµÃƒâ€˜Ã¢â‚¬Â¦ ÃƒÂÃ‚Â¿ÃƒÂÃ‚Â¾ÃƒÂÃ‚Â»Ãƒâ€˜Ã…â€™ÃƒÂÃ‚Â·ÃƒÂÃ‚Â¾ÃƒÂÃ‚Â²ÃƒÂÃ‚Â°Ãƒâ€˜Ã¢â‚¬Å¡ÃƒÂÃ‚ÂµÃƒÂÃ‚Â»ÃƒÂÃ‚ÂµÃƒÂÃ‚Â¹ Ãƒâ€˜Ã‚Â ÃƒÂÃ‚ÂºÃƒÂÃ‚Â¾Ãƒâ€˜Ã‚ÂÃƒÂÃ‚Â¼ÃƒÂÃ‚ÂµÃƒâ€˜Ã¢â‚¬Å¡ÃƒÂÃ‚Â¸ÃƒÂÃ‚ÂºÃƒÂÃ‚Â¾ÃƒÂÃ‚Â¹
     */
    public Collection<UUID> getAllUsers() {
        return this.userCosmetics.keySet();
    }

    /**
     * ÃƒÂÃ…Â¸ÃƒÂÃ‚Â¾ÃƒÂÃ‚Â»Ãƒâ€˜Ã†â€™Ãƒâ€˜Ã¢â‚¬Â¡ÃƒÂÃ‚Â°ÃƒÂÃ‚ÂµÃƒâ€˜Ã¢â‚¬Å¡ ÃƒÂÃ‚ÂºÃƒÂÃ‚Â¾ÃƒÂÃ‚Â»ÃƒÂÃ‚Â¸Ãƒâ€˜Ã¢â‚¬Â¡ÃƒÂÃ‚ÂµÃƒâ€˜Ã‚ÂÃƒâ€˜Ã¢â‚¬Å¡ÃƒÂÃ‚Â²ÃƒÂÃ‚Â¾ ÃƒÂÃ‚Â¿ÃƒÂÃ‚Â¾ÃƒÂÃ‚Â»Ãƒâ€˜Ã…â€™ÃƒÂÃ‚Â·ÃƒÂÃ‚Â¾ÃƒÂÃ‚Â²ÃƒÂÃ‚Â°Ãƒâ€˜Ã¢â‚¬Å¡ÃƒÂÃ‚ÂµÃƒÂÃ‚Â»ÃƒÂÃ‚ÂµÃƒÂÃ‚Â¹ Ãƒâ€˜Ã‚Â ÃƒÂÃ‚ÂºÃƒÂÃ‚Â¾Ãƒâ€˜Ã‚ÂÃƒÂÃ‚Â¼ÃƒÂÃ‚ÂµÃƒâ€˜Ã¢â‚¬Å¡ÃƒÂÃ‚Â¸ÃƒÂÃ‚ÂºÃƒÂÃ‚Â¾ÃƒÂÃ‚Â¹
     */
    public int getUserCount() {
        return this.userCosmetics.size();
    }
    
    /**
     * ÃƒÂÃ…Â¸ÃƒÂÃ‚ÂµÃƒâ€˜Ã¢â€šÂ¬ÃƒÂÃ‚ÂµÃƒÂÃ‚Â²Ãƒâ€˜Ã¢â‚¬Â¹ÃƒÂÃ‚Â´ÃƒÂÃ‚Â°Ãƒâ€˜Ã¢â‚¬ËœÃƒâ€˜Ã¢â‚¬Å¡ Ãƒâ€˜Ã‚ÂÃƒÂÃ‚Â¾Ãƒâ€˜Ã¢â‚¬Â¦Ãƒâ€˜Ã¢â€šÂ¬ÃƒÂÃ‚Â°ÃƒÂÃ‚Â½Ãƒâ€˜Ã¢â‚¬ËœÃƒÂÃ‚Â½ÃƒÂÃ‚Â½Ãƒâ€˜Ã¢â‚¬Â¹ÃƒÂÃ‚Âµ ÃƒÂÃ‚ÂºÃƒÂÃ‚Â¾Ãƒâ€˜Ã‚ÂÃƒÂÃ‚Â¼ÃƒÂÃ‚ÂµÃƒâ€˜Ã¢â‚¬Å¡ÃƒÂÃ‚Â¸ÃƒÂÃ‚ÂºÃƒÂÃ‚Â¸ ÃƒÂÃ‚Â¿Ãƒâ€˜Ã¢â€šÂ¬ÃƒÂÃ‚Â¸ Ãƒâ€˜Ã¢â€šÂ¬ÃƒÂÃ‚ÂµÃƒâ€˜Ã‚ÂÃƒÂÃ‚Â¿ÃƒÂÃ‚Â°ÃƒÂÃ‚Â²ÃƒÂÃ‚Â½ÃƒÂÃ‚Âµ (Ãƒâ€˜Ã¢â‚¬Å¡ÃƒÂÃ‚Â¸Ãƒâ€˜Ã¢â‚¬Â¦ÃƒÂÃ‚Â¾, ÃƒÂÃ‚Â±ÃƒÂÃ‚ÂµÃƒÂÃ‚Â· Ãƒâ€˜Ã‚ÂÃƒÂÃ‚Â¾ÃƒÂÃ‚Â¾ÃƒÂÃ‚Â±Ãƒâ€˜Ã¢â‚¬Â°ÃƒÂÃ‚ÂµÃƒÂÃ‚Â½ÃƒÂÃ‚Â¸ÃƒÂÃ‚Â¹)
     * ÃƒÂÃ¢â‚¬ÂºÃƒÂÃ‚Â¾ÃƒÂÃ‚Â³ÃƒÂÃ‚Â¸ÃƒÂÃ‚ÂºÃƒÂÃ‚Â° ÃƒÂÃ‚Â¸ÃƒÂÃ‚Â· ProCosmetics
     */
    public void reequipSavedCosmetics(Player player, boolean silent) {
        this.reequipSavedCosmeticsInternal(player, true);
    }

    public void reequipSavedCosmeticsForce(Player player) {
        this.reequipSavedCosmeticsInternal(player, false);
    }

    private void reequipSavedCosmeticsInternal(Player player, boolean respectVisibilityModes) {
        UUID playerId = player.getUniqueId();
        UserCosmetics userCos = this.userCosmetics.get(playerId);
        if (userCos == null) return;

        UserCosmeticSettings settings = this.getOrCreateSettings(playerId);

        for (Map.Entry<CosmeticCategory, Cosmetic> entry : userCos.getAllSavedCosmetics().entrySet()) {
            CosmeticCategory category = entry.getKey();
            Cosmetic cosmetic = entry.getValue();
            if (cosmetic != null
                    && cosmetic.hasPermission(player)
                    && (!respectVisibilityModes || this.canRestoreForSelf(settings, category))) {
                cosmetic.equip(player);
                userCos.setEquippedCosmetic(category, cosmetic);
            }
            userCos.clearSavedCosmetic(category);
        }

        TemporaryArrowEffects snapshot = this.temporaryArrowEffects.remove(playerId);
        if (snapshot != null
                && (!respectVisibilityModes || this.canRestoreForSelf(settings, CosmeticCategory.ARROW_EFFECT))) {
            snapshot.restore(playerId, player);
        }
    }

    private record TemporaryArrowEffects(
            ArrowEffectCosmetic bowTrail,
            ArrowEffectCosmetic bowHit,
            ArrowEffectCosmetic mace,
            ArrowEffectCosmetic tridentLegacy,
            ArrowEffectCosmetic tridentTrail,
            ArrowEffectCosmetic tridentHit,
            ArrowEffectCosmetic riptide
    ) {
        static TemporaryArrowEffects capture(UUID playerId) {
            return new TemporaryArrowEffects(
                    ArrowEffectCosmetic.getActiveBowTrailEffect(playerId),
                    ArrowEffectCosmetic.getActiveBowHitEffect(playerId),
                    ArrowEffectCosmetic.getActiveMaceEffect(playerId),
                ArrowEffectCosmetic.getActiveTridentThrowEffect(playerId),
                    ArrowEffectCosmetic.getActiveTridentThrowTrailEffect(playerId),
                    ArrowEffectCosmetic.getActiveTridentThrowHitEffect(playerId),
                    ArrowEffectCosmetic.getActiveTridentRiptideEffect(playerId)
            );
        }

        boolean isEmpty() {
            return bowTrail == null
                    && bowHit == null
                    && mace == null
                    && tridentLegacy == null
                    && tridentTrail == null
                    && tridentHit == null
                    && riptide == null;
        }

        void restore(UUID playerId, Player player) {
            ArrowEffectCosmetic.setActiveBowTrailEffect(playerId, permitted(bowTrail, player));
            ArrowEffectCosmetic.setActiveBowHitEffect(playerId, permitted(bowHit, player));
            ArrowEffectCosmetic.setActiveMaceEffect(playerId, permitted(mace, player));
            ArrowEffectCosmetic.setActiveTridentThrowEffect(playerId, permitted(tridentLegacy, player));
            ArrowEffectCosmetic.setActiveTridentThrowTrailEffect(playerId, permitted(tridentTrail, player));
            ArrowEffectCosmetic.setActiveTridentThrowHitEffect(playerId, permitted(tridentHit, player));
            ArrowEffectCosmetic.setActiveTridentRiptideEffect(playerId, permitted(riptide, player));
        }

        private ArrowEffectCosmetic permitted(ArrowEffectCosmetic effect, Player player) {
            if (effect == null || player == null || !player.isOnline()) {
                return null;
            }
            return effect.hasPermission(player) ? effect : null;
        }
    }

    private boolean canRestoreForSelf(UserCosmeticSettings settings, CosmeticCategory category) {
        if (settings == null || category == null) {
            return false;
        }
        UserCosmeticSettings.VisibilityMode mode = settings.getCategoryVisibility(category);
        return mode == UserCosmeticSettings.VisibilityMode.ALL
                || mode == UserCosmeticSettings.VisibilityMode.SELF_ONLY;
    }

    /**
     *        
     */
    public void loadPlayerCosmetics(Player player) {
        if (!this.database.isConnected()) {
            this.plugin.getDebugSystem().log("UserCosmeticsManager", "ÃƒÂÃ¢â‚¬ËœÃƒÂÃ‚Â°ÃƒÂÃ‚Â·ÃƒÂÃ‚Â° ÃƒÂÃ‚Â´ÃƒÂÃ‚Â°ÃƒÂÃ‚Â½ÃƒÂÃ‚Â½Ãƒâ€˜Ã¢â‚¬Â¹Ãƒâ€˜Ã¢â‚¬Â¦ ÃƒÂÃ‚Â½ÃƒÂÃ‚ÂµÃƒÂÃ‚Â´ÃƒÂÃ‚Â¾Ãƒâ€˜Ã‚ÂÃƒâ€˜Ã¢â‚¬Å¡Ãƒâ€˜Ã†â€™ÃƒÂÃ‚Â¿ÃƒÂÃ‚Â½ÃƒÂÃ‚Â° ÃƒÂÃ‚Â´ÃƒÂÃ‚Â»Ãƒâ€˜Ã‚Â ÃƒÂÃ‚Â·ÃƒÂÃ‚Â°ÃƒÂÃ‚Â³Ãƒâ€˜Ã¢â€šÂ¬Ãƒâ€˜Ã†â€™ÃƒÂÃ‚Â·ÃƒÂÃ‚ÂºÃƒÂÃ‚Â¸");
            return;
        }
        
        UUID playerId = player.getUniqueId();
        String state = this.database.loadPlayerState(playerId);
        if (state != null && !state.isBlank()) {
            this.applyPlayerState(player, state);
            return;
        }
        this.getOrCreate(playerId);
        this.getOrCreateSettings(playerId);
    }

    public void savePlayerState(Player player) {
        if (player == null || !this.database.isConnected()) {
            return;
        }
        UUID playerId = player.getUniqueId();
        UserCosmetics userCos = this.getOrCreate(playerId);
        UserCosmeticSettings settings = this.getOrCreateSettings(playerId);

        StringBuilder state = new StringBuilder(256);
        state.append("eq=");
        boolean first = true;
        for (Map.Entry<CosmeticCategory, Cosmetic> entry : userCos.getAllEquipped().entrySet()) {
            Cosmetic cosmetic = entry.getValue();
            if (cosmetic == null) continue;
            if (!first) state.append(",");
            state.append(entry.getKey().getId()).append("=").append(cosmetic.getId());
            first = false;
        }

        state.append(";settings=")
            .append(settings.isShowOthersEffects() ? "1" : "0").append("|")
            .append(settings.isShowMyEffectsToOthers() ? "1" : "0").append("|")
            .append(settings.isSilentMode() ? "1" : "0").append("|")
            .append(settings.isReducedEffects() ? "1" : "0");

        state.append(";visibility=");
        first = true;
        for (CosmeticCategory category : CosmeticCategory.values()) {
            if (!first) state.append(",");
            state.append(category.getId()).append(":").append(settings.getCategoryVisibility(category).name());
            first = false;
        }

        state.append(";bowTrail=").append(this.effectId(ArrowEffectCosmetic.getActiveBowTrailEffect(playerId)));
        state.append(";bowHit=").append(this.effectId(ArrowEffectCosmetic.getActiveBowHitEffect(playerId)));
        state.append(";mace=").append(this.effectId(ArrowEffectCosmetic.getActiveMaceEffect(playerId)));
        state.append(";tridentTrail=").append(this.effectId(ArrowEffectCosmetic.getActiveTridentThrowTrailEffect(playerId)));
        state.append(";tridentHit=").append(this.effectId(ArrowEffectCosmetic.getActiveTridentThrowHitEffect(playerId)));
        state.append(";riptide=").append(this.effectId(ArrowEffectCosmetic.getActiveTridentRiptideEffect(playerId)));
        state.append(";petName=").append(this.encodeStateValue(PetCosmetic.getCustomPetName(playerId)));

        this.database.savePlayerState(playerId, state.toString());
    }

    private void applyPlayerState(Player player, String rawState) {
        UUID playerId = player.getUniqueId();
        java.util.Map<String, String> tokens = this.parseState(rawState);
        UserCosmetics userCos = this.getOrCreate(playerId);
        UserCosmeticSettings settings = this.getOrCreateSettings(playerId);
        CosmeticsManager cosmeticsManager = this.module.getCosmeticsManager();

        String settingsToken = tokens.get("settings");
        if (settingsToken != null) {
            String[] parts = settingsToken.split("\\|");
            if (parts.length >= 4) {
                settings.setShowOthersEffects("1".equals(parts[0]));
                settings.setShowMyEffectsToOthers("1".equals(parts[1]));
                settings.setSilentMode("1".equals(parts[2]));
                settings.setReducedEffects("1".equals(parts[3]));
            }
        }
        this.applyVisibility(settings, tokens.get("visibility"));

        String equippedToken = tokens.get("eq");
        if (equippedToken != null && !equippedToken.isBlank()) {
            for (String entry : equippedToken.split(",")) {
                String[] kv = entry.split("=", 2);
                if (kv.length != 2) continue;
                CosmeticCategory category = CosmeticCategory.fromId(kv[0]);
                if (category == null) continue;
                Cosmetic cosmetic = cosmeticsManager.getCosmetic(kv[1]);
                if (cosmetic == null || !cosmetic.hasPermission(player)) continue;
                userCos.equipCosmetic(cosmetic, player);
            }
        }

        ArrowEffectCosmetic.setActiveBowTrailEffect(playerId, this.resolveArrowEffect(player, tokens.get("bowTrail")));
        ArrowEffectCosmetic.setActiveBowHitEffect(playerId, this.resolveArrowEffect(player, tokens.get("bowHit")));
        ArrowEffectCosmetic.setActiveMaceEffect(playerId, this.resolveArrowEffect(player, tokens.get("mace")));
        ArrowEffectCosmetic.setActiveTridentThrowTrailEffect(playerId, this.resolveArrowEffect(player, tokens.get("tridentTrail")));
        ArrowEffectCosmetic.setActiveTridentThrowHitEffect(playerId, this.resolveArrowEffect(player, tokens.get("tridentHit")));
        ArrowEffectCosmetic.setActiveTridentRiptideEffect(playerId, this.resolveArrowEffect(player, tokens.get("riptide")));
        PetCosmetic.setCustomPetName(playerId, this.decodeStateValue(tokens.get("petName")));
    }

    private java.util.Map<String, String> parseState(String rawState) {
        java.util.Map<String, String> result = new java.util.HashMap<>();
        for (String token : rawState.split(";")) {
            String[] kv = token.split("=", 2);
            if (kv.length == 2) {
                result.put(kv[0], kv[1]);
            }
        }
        return result;
    }

    private void applyVisibility(UserCosmeticSettings settings, String visibilityToken) {
        if (visibilityToken == null || visibilityToken.isBlank()) {
            return;
        }
        for (String part : visibilityToken.split(",")) {
            String[] kv = part.split(":");
            if (kv.length != 2) continue;
            CosmeticCategory category = CosmeticCategory.fromId(kv[0]);
            if (category == null) continue;
            try {
                settings.setCategoryVisibility(category, UserCosmeticSettings.VisibilityMode.valueOf(kv[1]));
            } catch (Exception ignored) {
            }
        }
    }

    private ArrowEffectCosmetic resolveArrowEffect(Player player, String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        Cosmetic cosmetic = this.module.getCosmeticsManager().getCosmetic(id);
        if (cosmetic instanceof ArrowEffectCosmetic arrow && arrow.hasPermission(player)) {
            return arrow;
        }
        return null;
    }

    private String effectId(ArrowEffectCosmetic effect) {
        return effect == null ? "" : effect.getId();
    }

    private String encodeStateValue(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return java.util.Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private String decodeStateValue(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        try {
            byte[] decoded = java.util.Base64.getUrlDecoder().decode(value);
            return new String(decoded, java.nio.charset.StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            return "";
        }
    }
    
    /**
     * ÃƒÂÃ…Â¸ÃƒÂÃ‚Â¾ÃƒÂÃ‚Â»Ãƒâ€˜Ã†â€™Ãƒâ€˜Ã¢â‚¬Â¡ÃƒÂÃ‚Â°ÃƒÂÃ‚ÂµÃƒâ€˜Ã¢â‚¬Å¡ ÃƒÂÃ‚Â±ÃƒÂÃ‚Â°ÃƒÂÃ‚Â·Ãƒâ€˜Ã†â€™ ÃƒÂÃ‚Â´ÃƒÂÃ‚Â°ÃƒÂÃ‚Â½ÃƒÂÃ‚Â½Ãƒâ€˜Ã¢â‚¬Â¹Ãƒâ€˜Ã¢â‚¬Â¦
     */
    public CosmeticsDatabase getDatabase() {
        return this.database;
    }
}




