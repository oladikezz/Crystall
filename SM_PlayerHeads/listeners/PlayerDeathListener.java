package net.schalker.SMPS.modules.playerheads.listeners;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.debug.DebugSystem;
import net.schalker.DoAPI.core.listener.BaseListener;
import net.schalker.SMPS.modules.playerheads.PlayerHeadsModule;
import net.schalker.SMPS.modules.playerheads.managers.PlayerHeadsDatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Skull;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class PlayerDeathListener extends BaseListener {
    private static final String MODULE_FOLDER_NAME = "SM_PlayerHead";

    private final PlayerHeadsDatabaseManager databaseManager;
    private final NamespacedKey headNameKey;
    private final NamespacedKey headLoreKey;
    private final NamespacedKey headOwnerKey;
    private final NamespacedKey headSkinValueKey;
    private final NamespacedKey headSkinSignatureKey;
    private final String moduleName;

    public PlayerDeathListener(DoAPI plugin, PlayerHeadsDatabaseManager databaseManager, String moduleName) {
        super(plugin);
        this.databaseManager = databaseManager;
        this.moduleName = moduleName;
        this.headNameKey = new NamespacedKey(plugin, "playerheads-name");
        this.headLoreKey = new NamespacedKey(plugin, "playerheads-lore");
        this.headOwnerKey = new NamespacedKey(plugin, "playerheads-owner");
        this.headSkinValueKey = new NamespacedKey(plugin, "playerheads-skin-value");
        this.headSkinSignatureKey = new NamespacedKey(plugin, "playerheads-skin-sig");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        FileConfiguration config = PlayerHeadsModule.loadModuleConfig(plugin, MODULE_FOLDER_NAME);
        FileConfiguration messages = PlayerHeadsModule.loadModuleConfig(plugin, MODULE_FOLDER_NAME, "messages.yml");

        if (config == null || messages == null) {
            plugin.getLogger().warning("Failed to load PlayerHeads config files");
            return;
        }

        if (!config.getBoolean("enabled", true)) {
            return;
        }

        if (killer == null && config.getBoolean("settings.only-pvp", true)) {
            plugin.getDebugSystem().log("PlayerHeads", victim.getName() + " died not from PVP");
            return;
        }

        double dropChance = config.getDouble("settings.drop-chance", 1.0D);
        if (dropChance < 1.0D && Math.random() > dropChance) {
            return;
        }

        ItemStack head = createPlayerHead(victim, killer, config, messages);

        if (config.getBoolean("drop-on-death", true)) {
            event.getDrops().add(head);
            DebugSystem debugSystem = plugin.getDebugSystem();
            debugSystem.log("PlayerHeads", "Head of " + victim.getName() + " added to drops");
        } else if (killer != null) {
            killer.getInventory().addItem(head);
            plugin.getDebugSystem().log("PlayerHeads", "Head of " + victim.getName() + " given to " + killer.getName());
        } else {
            victim.getWorld().dropItemNaturally(victim.getLocation(), head);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onHeadPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (block.getType() != Material.PLAYER_HEAD && block.getType() != Material.PLAYER_WALL_HEAD) {
            return;
        }

        BlockState state = block.getState();
        if (!(state instanceof Skull skull)) {
            return;
        }

        ItemStack item = event.getItemInHand();
        if (!item.hasItemMeta() || !(item.getItemMeta() instanceof SkullMeta meta)) {
            return;
        }

        // Determine owner UUID
        UUID ownerUuid = null;
        if (meta.getOwningPlayer() != null) {
            ownerUuid = meta.getOwningPlayer().getUniqueId();
            skull.setOwningPlayer(meta.getOwningPlayer());
        }

        // Apply skin for offline players
        if (ownerUuid != null) {
            OfflinePlayer owner = plugin.getServer().getOfflinePlayer(ownerUuid);
            if (!owner.isOnline()) {
                applySkinToSkull(skull, meta, ownerUuid);
            }
        }

        PersistentDataContainer container = skull.getPersistentDataContainer();
        Component displayComponent = meta.displayName();
        if (displayComponent != null) {
            String serialized = LegacyComponentSerializer.legacySection().serialize(displayComponent);
            container.set(headNameKey, PersistentDataType.STRING, serialized);
        }

        List<Component> loreComponents = meta.lore();
        if (loreComponents != null && !loreComponents.isEmpty()) {
            List<String> serializedLines = new ArrayList<>();
            for (Component comp : loreComponents) {
                serializedLines.add(LegacyComponentSerializer.legacySection().serialize(comp));
            }
            container.set(headLoreKey, PersistentDataType.STRING, String.join("\n", serializedLines));
        }

        if (ownerUuid != null) {
            container.set(headOwnerKey, PersistentDataType.STRING, ownerUuid.toString());
        }

        // Also persist skin texture data on the skull block for later break/re-place
        PersistentDataContainer itemPdc = meta.getPersistentDataContainer();
        String skinVal = itemPdc.get(headSkinValueKey, PersistentDataType.STRING);
        String skinSig = itemPdc.get(headSkinSignatureKey, PersistentDataType.STRING);
        if (skinVal != null) {
            container.set(headSkinValueKey, PersistentDataType.STRING, skinVal);
            if (skinSig != null) {
                container.set(headSkinSignatureKey, PersistentDataType.STRING, skinSig);
            }
        }

        skull.update(true, false);
    }

    /**
     * Apply skin texture to a placed skull for an offline player.
     * Checks: 1) PDC-stored texture from item, 2) SkinsRestorer DB, 3) player's cached profile.
     */
    private void applySkinToSkull(Skull skull, SkullMeta meta, UUID ownerUuid) {
        // 1) Try texture stored in the item's PDC (captured at death time)
        PersistentDataContainer itemPdc = meta.getPersistentDataContainer();
        String skinValue = itemPdc.get(headSkinValueKey, PersistentDataType.STRING);
        String skinSignature = itemPdc.get(headSkinSignatureKey, PersistentDataType.STRING);

        // 2) Fallback: look up from SkinsRestorer DB
        if (skinValue == null || skinValue.isEmpty()) {
            PlayerHeadsDatabaseManager.SkinData srSkin = databaseManager.lookupSkin(ownerUuid);
            if (srSkin != null) {
                skinValue = srSkin.value();
                skinSignature = srSkin.signature();
                plugin.getDebugSystem().log("PlayerHeads",
                    "Resolved offline skin from SkinsRestorer for " + ownerUuid);
            }
        }

        // 3) Apply via PlayerProfile if we have texture data
        if (skinValue != null && !skinValue.isEmpty()) {
            try {
                PlayerProfile profile = Bukkit.createProfile(ownerUuid, null);
                profile.setProperty(new ProfileProperty("textures", skinValue,
                    skinSignature != null ? skinSignature : ""));
                skull.setPlayerProfile(profile);
                plugin.getDebugSystem().log("PlayerHeads",
                    "Applied skin profile to placed head for " + ownerUuid);
            } catch (Exception e) {
                plugin.getDebugSystem().logError("Failed to apply skin profile for " + ownerUuid, e);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onHeadBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.PLAYER_HEAD && block.getType() != Material.PLAYER_WALL_HEAD) {
            return;
        }

        BlockState state = block.getState();
        if (!(state instanceof Skull skull)) {
            return;
        }

        PersistentDataContainer container = skull.getPersistentDataContainer();
        String storedName = container.get(headNameKey, PersistentDataType.STRING);
        String storedLore = container.get(headLoreKey, PersistentDataType.STRING);
        if (storedName == null && storedLore == null) {
            return;
        }

        event.setDropItems(false);
        ItemStack head = new ItemStack(Material.PLAYER_HEAD, 1);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta == null) {
            return;
        }

        // Read skin texture from the skull block's PDC
        String skinVal = container.get(headSkinValueKey, PersistentDataType.STRING);
        String skinSig = container.get(headSkinSignatureKey, PersistentDataType.STRING);

        String owner = container.get(headOwnerKey, PersistentDataType.STRING);
        UUID ownerUuid = null;
        if (owner != null) {
            try { ownerUuid = UUID.fromString(owner); } catch (IllegalArgumentException ignored) {}
        }

        // If we don't have stored texture, try to get it from SkinsRestorer
        if (skinVal == null && ownerUuid != null) {
            PlayerHeadsDatabaseManager.SkinData srSkin = databaseManager.lookupSkin(ownerUuid);
            if (srSkin != null) {
                skinVal = srSkin.value();
                skinSig = srSkin.signature();
            }
        }

        // Apply skin via PlayerProfile (avoids Mojang lookup for offline players)
        if (skinVal != null && !skinVal.isEmpty()) {
            PlayerProfile profile = Bukkit.createProfile(
                ownerUuid != null ? ownerUuid : UUID.randomUUID(), null);
            profile.setProperty(new ProfileProperty("textures", skinVal,
                skinSig != null ? skinSig : ""));
            meta.setPlayerProfile(profile);
        } else if (ownerUuid != null) {
            // No texture data available — fall back to setOwningPlayer
            // (will work for online players, may show default skin for offline)
            meta.setOwningPlayer(plugin.getServer().getOfflinePlayer(ownerUuid));
        } else if (skull.hasOwner()) {
            meta.setOwningPlayer(skull.getOwningPlayer());
        }

        if (storedName != null) {
            meta.displayName(LegacyComponentSerializer.legacySection().deserialize(storedName));
        }

        if (storedLore != null) {
            List<Component> lore = new ArrayList<>();
            for (String line : storedLore.split("\n", -1)) {
                lore.add(LegacyComponentSerializer.legacySection().deserialize(line));
            }
            meta.lore(lore);
        }

        // Preserve skin texture in item PDC for future break/re-place cycles
        if (skinVal != null) {
            PersistentDataContainer itemPdc = meta.getPersistentDataContainer();
            itemPdc.set(headSkinValueKey, PersistentDataType.STRING, skinVal);
            if (skinSig != null) {
                itemPdc.set(headSkinSignatureKey, PersistentDataType.STRING, skinSig);
            }
        }

        head.setItemMeta(meta);
        block.getWorld().dropItemNaturally(block.getLocation(), head);
    }

    private ItemStack createPlayerHead(Player victim, Player killer, FileConfiguration config, FileConfiguration messages) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD, 1);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta == null) {
            return head;
        }

        meta.setOwningPlayer(victim);

        // Bake the victim's skin texture directly into the profile,
        // so the head never needs a Mojang lookup (works offline).
        captureSkinTexture(meta, victim);
        applySkinProfile(meta, victim);

        ItemStack weapon = killer != null ? killer.getInventory().getItemInMainHand() : null;
        String killerName = resolveKillerName(victim, killer, messages);
        String weaponName = resolveWeaponName(victim, weapon, messages);
        String deathCause = resolveDeathCause(victim, messages);
        int deathCount = databaseManager.incrementDeath(victim.getUniqueId(), victim.getName());
        String deathTime = formatDeathTime(config);

        String displayNameTemplate = messages.getString("head-name", "Head of {victim}");
        String displayName = applyTemplate(displayNameTemplate, victim, killerName, weaponName, deathCause, deathTime, deathCount);
        meta.displayName(LegacyComponentSerializer.legacyAmpersand().deserialize(displayName));

        List<String> loreTemplate = messages.getStringList("head-lore");
        if (loreTemplate == null || loreTemplate.isEmpty()) {
            loreTemplate = List.of(
                "&8Необычный",
                "",
                "&fУбит: &b{killer}",
                "&fОружие: &b{weapon}",
                "&fВремя: &b{death_time}",
                "&fСмерть: &b№{death_number}"
            );
        }

        List<String> lore = new ArrayList<>();
        for (String line : loreTemplate) {
            String formatted = applyTemplate(line, victim, killerName, weaponName, deathCause, deathTime, deathCount);
            lore.add(formatted);
        }

        // Always enforce key death lines, even with outdated messages.yml.
        ensureLine(lore, "&fУбит: &b" + killerName, "Убит:");
        ensureLine(lore, "&fОружие: &b" + weaponName, "Оружие:");
        ensureLine(lore, "&fВремя: &b" + deathTime, "Время:");

        List<Component> components = new ArrayList<>();
        for (String line : lore) {
            components.add(LegacyComponentSerializer.legacyAmpersand().deserialize(line));
        }
        meta.lore(components);
        head.setItemMeta(meta);

        plugin.getDebugSystem().log(
            "PlayerHeads",
            "Lore built -> killer=" + killerName + ", weapon=" + weaponName + ", time=" + deathTime + ", cause=" + deathCause
        );
        return head;
    }

    /**
     * Capture the player's current skin texture and store it in the item's PDC.
     * This allows the correct skin to be applied when the head is placed while
     * the player is offline.
     */
    private void captureSkinTexture(SkullMeta meta, Player player) {
        try {
            PlayerProfile profile = player.getPlayerProfile();
            if (profile == null) return;

            for (ProfileProperty property : profile.getProperties()) {
                if ("textures".equals(property.getName())) {
                    String value = property.getValue();
                    String signature = property.getSignature();
                    if (value != null && !value.isEmpty()) {
                        PersistentDataContainer pdc = meta.getPersistentDataContainer();
                        pdc.set(headSkinValueKey, PersistentDataType.STRING, value);
                        if (signature != null && !signature.isEmpty()) {
                            pdc.set(headSkinSignatureKey, PersistentDataType.STRING, signature);
                        }
                        plugin.getDebugSystem().log("PlayerHeads",
                            "Captured skin texture for " + player.getName());
                    }
                    break;
                }
            }
        } catch (Exception e) {
            plugin.getDebugSystem().logError("Failed to capture skin texture for " + player.getName(), e);
        }
    }

    /**
     * Apply a static PlayerProfile with baked-in textures to the SkullMeta.
     * This makes the head item completely independent of Mojang lookups —
     * the skin will display correctly even when the player is offline.
     */
    private void applySkinProfile(SkullMeta meta, Player player) {
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String skinVal = pdc.get(headSkinValueKey, PersistentDataType.STRING);
        String skinSig = pdc.get(headSkinSignatureKey, PersistentDataType.STRING);

        if (skinVal == null || skinVal.isEmpty()) return;

        try {
            PlayerProfile profile = Bukkit.createProfile(player.getUniqueId(), player.getName());
            profile.setProperty(new ProfileProperty("textures", skinVal,
                skinSig != null ? skinSig : ""));
            meta.setPlayerProfile(profile);
        } catch (Exception e) {
            plugin.getDebugSystem().logError("Failed to apply skin profile for " + player.getName(), e);
        }
    }

    private void ensureLine(List<String> lore, String line, String marker) {
        String plainMarker = marker.toLowerCase();
        for (String legacy : lore) {
            String plain = legacy.replace('§', '&').toLowerCase();
            if (plain.contains(plainMarker)) {
                return;
            }
        }
        lore.add(colorize(line));
    }

    private String applyTemplate(
        String template,
        Player victim,
        String killerName,
        String weaponName,
        String deathCause,
        String deathTime,
        int deathCount
    ) {
        String value = template
            .replace("{victim}", victim.getName())
            .replace("{killer}", killerName)
            .replace("{weapon}", weaponName)
            .replace("{cause}", deathCause)
            .replace("{death_time}", deathTime)
            .replace("{death_number}", String.valueOf(deathCount))
            .replace("{death_count}", String.valueOf(deathCount));

        return applyPlaceholderApi(victim, value);
    }

    private String applyPlaceholderApi(Player player, String text) {
        if (player == null || text == null || text.isEmpty()) {
            return text;
        }
        if (plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return text;
        }

        try {
            Class<?> papi = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            Method method = papi.getMethod("setPlaceholders", org.bukkit.OfflinePlayer.class, String.class);
            Object result = method.invoke(null, player, text);
            return result instanceof String s ? s : text;
        } catch (Exception exception) {
            plugin.getDebugSystem().logError("PlaceholderAPI format failed", exception);
            return text;
        }
    }

    private String colorize(String text) {
        return LegacyComponentSerializer.legacyAmpersand()
            .serialize(LegacyComponentSerializer.legacyAmpersand().deserialize(text));
    }

    private String stripLegacyToPlain(String input) {
        if (input == null) {
            return "";
        }
        return input.replaceAll("&[0-9a-fk-or]", "");
    }

    private String formatDeathTime(FileConfiguration config) {
        String pattern = config.getString("settings.death-time-format", "dd.MM.yyyy HH:mm");
        try {
            return new SimpleDateFormat(pattern).format(new Date());
        } catch (Exception exception) {
            return new SimpleDateFormat("dd.MM.yyyy HH:mm").format(new Date());
        }
    }

    private String getMessage(FileConfiguration messages, String key, String fallback) {
        String value = messages.getString(key, fallback);
        return value == null ? fallback : value;
    }

    private String getWeaponName(ItemStack weapon, FileConfiguration messages) {
        if (weapon != null && weapon.getType() != Material.AIR) {
            if (weapon.hasItemMeta() && weapon.getItemMeta().displayName() != null) {
                return LegacyComponentSerializer.legacyAmpersand().serialize(weapon.getItemMeta().displayName());
            }

            String materialKey = weapon.getType().name().toLowerCase();
            String weaponPath = "weapons." + materialKey;
            return messages.contains(weaponPath)
                ? messages.getString(weaponPath)
                : formatMaterialName(weapon.getType().name());
        }

        return messages.getString("weapons.hand", "Bare hands");
    }

    private String formatMaterialName(String materialName) {
        if ("NETHERITE_SWORD".equals(materialName)) return "Незеритовый меч";
        if ("DIAMOND_SWORD".equals(materialName)) return "Алмазный меч";
        if ("IRON_SWORD".equals(materialName)) return "Железный меч";
        if ("STONE_SWORD".equals(materialName)) return "Каменный меч";
        if ("WOODEN_SWORD".equals(materialName)) return "Деревянный меч";
        if ("GOLDEN_SWORD".equals(materialName)) return "Золотой меч";

        String[] words = materialName.toLowerCase().split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (result.length() > 0) {
                result.append(" ");
            }
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    private String resolveKillerName(Player victim, Player killer, FileConfiguration messages) {
        if (killer != null) {
            return killer.getName();
        }

        EntityDamageEvent lastDamage = victim.getLastDamageCause();
        if (lastDamage instanceof EntityDamageByEntityEvent byEntity) {
            Entity damager = byEntity.getDamager();
            return damager.getName();
        }

        return getMessage(messages, "killer-unknown", "Unknown");
    }

    private String resolveWeaponName(Player victim, ItemStack weapon, FileConfiguration messages) {
        if (weapon != null && weapon.getType() != Material.AIR) {
            return getWeaponName(weapon, messages);
        }

        EntityDamageEvent lastDamage = victim.getLastDamageCause();
        if (lastDamage != null) {
            String causeKey = "causes." + lastDamage.getCause().name().toLowerCase();
            if (messages.contains(causeKey)) {
                return messages.getString(causeKey, formatMaterialName(lastDamage.getCause().name()));
            }
            return formatMaterialName(lastDamage.getCause().name());
        }

        return messages.getString("weapons.hand", "Bare hands");
    }

    private String resolveDeathCause(Player victim, FileConfiguration messages) {
        EntityDamageEvent lastDamage = victim.getLastDamageCause();
        if (lastDamage == null) {
            return getMessage(messages, "cause-unknown", "Unknown");
        }

        String causeKey = "causes." + lastDamage.getCause().name().toLowerCase();
        if (messages.contains(causeKey)) {
            return messages.getString(causeKey, formatMaterialName(lastDamage.getCause().name()));
        }
        return formatMaterialName(lastDamage.getCause().name());
    }
}
