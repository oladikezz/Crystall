package net.schalker.SMPS.modules.cosmetics.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.schalker.DoAPI.DoAPI;
import net.schalker.SMPS.modules.cosmetics.CosmeticsModule;
import net.schalker.SMPS.modules.cosmetics.managers.CosmeticsManager;
import net.schalker.SMPS.modules.cosmetics.managers.UserCosmeticsManager;
import net.schalker.SMPS.modules.cosmetics.models.Cosmetic;
import net.schalker.SMPS.modules.cosmetics.models.CosmeticCategory;
import net.schalker.SMPS.modules.cosmetics.models.ArrowEffectCosmetic;
import net.schalker.SMPS.modules.cosmetics.models.UserCosmeticSettings;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

public class CosmeticsMenuManager {
    private final DoAPI plugin;
    private final CosmeticsModule module;
    public static final String MAIN_MENU_ID = "cosmetics_main";
    public static final String CATEGORY_MENU_ID = "cosmetics_category_";
    public static final String SETTINGS_MENU_ID = "cosmetics_settings";
    public static final String CAT_VARIANTS_MENU_ID = "cosmetics_cat_variants";
    public static final String PARROT_VARIANTS_MENU_ID = "cosmetics_parrot_variants";
    public static final String FROG_VARIANTS_MENU_ID = "cosmetics_frog_variants";
    public static final String BALLOON_VARIANTS_MENU_ID = "cosmetics_balloon_variants";
    public static final String WEAPON_EFFECTS_MENU_ID = "cosmetics_weapon_effects";
    public static final String BOW_EFFECTS_MENU_ID = "cosmetics_bow_effects";
    public static final String MACE_EFFECTS_MENU_ID = "cosmetics_mace_effects";
    public static final String TRIDENT_EFFECTS_MENU_ID = "cosmetics_trident_effects";
    public static final String TRIDENT_THROW_EFFECTS_MENU_ID = "cosmetics_trident_throw_effects";
    public static final String TRIDENT_RIPTIDE_EFFECTS_MENU_ID = "cosmetics_trident_riptide_effects";
    private static final int[] CATEGORY_SLOTS = new int[]{10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
        private static final int[] SETTINGS_CATEGORY_SLOTS = new int[]{29, 30, 31, 32, 33, 39, 40, 41};
        private static final CosmeticCategory[] SETTINGS_VISIBILITY_CATEGORIES = new CosmeticCategory[]{
            CosmeticCategory.PET,
            CosmeticCategory.PARTICLE_EFFECT,
            CosmeticCategory.DEATH_EFFECT,
            CosmeticCategory.BALLOON,
            CosmeticCategory.ARROW_EFFECT,
            CosmeticCategory.ARROW_EFFECT,
            CosmeticCategory.ARROW_EFFECT,
            CosmeticCategory.ARROW_EFFECT
        };
        private static final String[] SETTINGS_VISIBILITY_LABELS = new String[]{
            "Pet",
            "Particle Effect",
            "Death Effect",
            "Balloon",
            "Arrow Effects",
            "Mace Effects",
            "Trident",
            "Riptide"
        };
        private static final Material[] SETTINGS_VISIBILITY_ICONS = new Material[]{
            Material.BONE,
            Material.BLAZE_POWDER,
            Material.WITHER_SKELETON_SKULL,
            Material.LEAD,
            Material.SPECTRAL_ARROW,
            Material.MACE,
            Material.TRIDENT,
            Material.HEART_OF_THE_SEA
        };
    private final Map<UUID, String> openMenus = new HashMap<UUID, String>();
    private final Map<UUID, Inventory> openInventories = new HashMap<UUID, Inventory>();
    private final Map<UUID, Integer> menuPages = new HashMap<UUID, Integer>();
    private final Map<UUID, CosmeticCategory> selectedCategory = new HashMap<UUID, CosmeticCategory>();
    private final Map<UUID, String> selectedBalloonGroup = new HashMap<UUID, String>();

    public CosmeticsMenuManager(DoAPI plugin, CosmeticsModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    public String getMessage(String key) {
        YamlConfiguration gui = this.plugin.getModuleManager().loadModuleConfig("SM_cosmetics", "gui.yml");
        String fromGui = this.resolveNestedValue(gui, key);
        if (fromGui != null) {
            return fromGui;
        }
        YamlConfiguration config = this.plugin.getModuleManager().loadModuleConfig("SM_cosmetics", "messages.yml");
        String fromMessages = this.resolveNestedValue(config, key);
        if (fromMessages != null) {
            return fromMessages;
        }
        return key;
    }

    private String resolveNestedValue(YamlConfiguration config, String key) {
        if (config == null) {
            return null;
        }
        String[] parts = key.split("\\.");
        Object current = config.get(parts[0]);
        for (int i = 1; i < parts.length && current != null; ++i) {
            if (!(current instanceof ConfigurationSection)) {
                return null;
            }
            ConfigurationSection section = (ConfigurationSection)current;
            current = section.get(parts[i]);
        }
        return current != null ? current.toString() : null;
    }

    public List<String> getGuiList(String key) {
        YamlConfiguration config = this.plugin.getModuleManager().loadModuleConfig("SM_cosmetics", "gui.yml");
        if (config == null) return Collections.emptyList();
        String[] parts = key.split("\\.");
        Object current = config.get(parts[0]);
        for (int i = 1; i < parts.length && current != null; ++i) {
            if (!(current instanceof ConfigurationSection)) {
                break;
            }
            ConfigurationSection section = (ConfigurationSection)current;
            current = section.get(parts[i]);
        }
        if (current instanceof List) {
            List<?> raw = (List<?>)current;
            List<String> out = new ArrayList<>();
            for (Object o : raw) out.add(o == null ? "" : o.toString());
            return out;
        }
        if (current != null) return Collections.singletonList(current.toString());
        return Collections.emptyList();
    }

    private Component colorize(String text) {
        if (text == null) {
            return Component.empty();
        }
        // Support token replacement &[MAIN] and &[SECONDARY] from global SMPS config
        try {
            String main = this.resolveThemeColor("main-color", "colors.MAIN", "&6");
            String secondary = this.resolveThemeColor("secondary-color", "colors.SECONDARY", "&e");
            text = text
                .replace("&[MAIN]", main)
                .replace("&[main]", main)
                .replace("&[SECONDARY]", secondary)
                .replace("&[secondary]", secondary);
        } catch (Exception ignored) {
        }
        return LegacyComponentSerializer.legacyAmpersand().deserialize(text);
    }

    private String resolveThemeColor(String globalKey, String moduleLegacyKey, String fallback) {
        String value = null;
        try {
            if (this.plugin.getConfigManager() != null && this.plugin.getConfigManager().getConfig() != null) {
                value = this.plugin.getConfigManager().getConfig().getString(globalKey);
            }
        } catch (Exception ignored) {
        }
        if (value == null || value.isBlank()) {
            try {
                YamlConfiguration moduleCfg = this.plugin.getModuleManager().loadModuleConfig("SM_cosmetics", "config.yml");
                if (moduleCfg != null) {
                    value = moduleCfg.getString(moduleLegacyKey);
                }
            } catch (Exception ignored) {
            }
        }
        if (value == null || value.isBlank()) {
            value = fallback;
        }
        return this.normalizeColorCode(value);
    }

    private String normalizeColorCode(String value) {
        if (value == null) {
            return "";
        }
        String color = value.trim();
        if (color.matches("^#[0-9A-Fa-f]{6}$")) {
            return "&" + color;
        }
        return color;
    }

    public void openMainMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, (int)54, (Component)this.colorize(this.getMessage("menu.title")));
        inv.setItem(4, this.createPlayerHeadInfoItem(player));
        CosmeticsManager manager = this.module.getCosmeticsManager();
        int slotIndex = 0;
        for (CosmeticCategory category : CosmeticCategory.values()) {
            if (category == CosmeticCategory.ARROW_EFFECT || category == CosmeticCategory.PARTICLE_EFFECT || category == CosmeticCategory.BALLOON || category == CosmeticCategory.DEATH_EFFECT) continue;
            int count = manager.getCosmeticsCount(category);
            if (count == 0 || slotIndex >= CATEGORY_SLOTS.length) continue;
            ItemStack categoryItem = this.createMainCategoryItem(player, category, count);
            inv.setItem(CATEGORY_SLOTS[slotIndex], categoryItem);
            ++slotIndex;
        }

        int deathCount = manager.getCosmeticsCount(CosmeticCategory.DEATH_EFFECT);
        if (deathCount > 0) {
            inv.setItem(12, this.createMainCategoryItem(player, CosmeticCategory.DEATH_EFFECT, deathCount));
        }

        // Forced layout slots requested by user
        int particlesCount = manager.getCosmeticsCount(CosmeticCategory.PARTICLE_EFFECT);
        if (particlesCount > 0) {
            inv.setItem(29, this.createMainCategoryItem(player, CosmeticCategory.PARTICLE_EFFECT, particlesCount));
        }
        int balloonsCount = manager.getCosmeticsCount(CosmeticCategory.BALLOON);
        if (balloonsCount > 0) {
            inv.setItem(31, this.createMainCategoryItem(player, CosmeticCategory.BALLOON, balloonsCount));
        }

            // Weapon effect categories with configurable dynamic lore
            inv.setItem(16, this.createWeaponCategoryItem(player, "weapon_bow", Material.SPECTRAL_ARROW, countArrowEffectsByPrefix("bow_"),
                this.effectName(ArrowEffectCosmetic.getActiveBowTrailEffect(player.getUniqueId()))));
            inv.setItem(14, this.createWeaponCategoryItem(player, "weapon_mace", Material.MACE, countArrowEffectsByPrefix("mace_hit_"),
                this.effectName(ArrowEffectCosmetic.getActiveMaceEffect(player.getUniqueId()))));
            inv.setItem(33, this.createTridentCategoryItem(player, countArrowEffectsByPrefix("trident_"),
                this.effectName(ArrowEffectCosmetic.getActiveTridentThrowEffect(player.getUniqueId())),
                this.effectName(ArrowEffectCosmetic.getActiveTridentRiptideEffect(player.getUniqueId()))));

        String settingsName = this.getMessage("settings.menu.header.name");
        List<String> settingsLore = this.getGuiList("settings.menu.header.lore");
        ItemStack settings = this.createItem(Material.COMPARATOR, settingsName, settingsLore.toArray(new String[0]));
        inv.setItem(48, settings);
        String unequipName = this.getMessage("menu.buttons.unequip_all");
        String unequipDesc = this.getMessage("menu.buttons.unequip_all_desc");
        ItemStack clearAll = this.createItem(Material.BARRIER, unequipName, unequipDesc);
        inv.setItem(50, clearAll);
        String closeName = this.getMessage("menu.buttons.close");
        ItemStack close = this.createItem(Material.ARROW, closeName, new String[0]);
        inv.setItem(49, close);
        this.openMenus.put(player.getUniqueId(), MAIN_MENU_ID);
        this.openInventories.put(player.getUniqueId(), inv);
        player.openInventory(inv);
    }

    public void openCategoryMenu(Player player, CosmeticCategory category, int page) {
        Collection<Cosmetic> cosmetics = this.module.getCosmeticsManager().getCosmeticsByCategory(category);
        ArrayList<Cosmetic> cosmeticList = new ArrayList<Cosmetic>(cosmetics);
        cosmeticList.removeIf(cosmetic -> !cosmetic.isVisibleTo(player));
        if (category == CosmeticCategory.PET) {
            cosmeticList.removeIf(cosmetic ->
                (cosmetic.getId().startsWith("cat_") && !cosmetic.getId().equals("cat_pet")) ||
                (cosmetic.getId().startsWith("parrot_") && !cosmetic.getId().equals("parrot_pet")) ||
                (cosmetic.getId().startsWith("axolotl_") && !cosmetic.getId().equals("axolotl_pet")) ||
                (cosmetic.getId().startsWith("frog_") && !cosmetic.getId().equals("frog_pet"))
            );
        }
        boolean includeEmptyOption = true;

        int itemsPerPage = 28;
        int totalItems = cosmeticList.size() + (includeEmptyOption ? 1 : 0);
        int totalPages = Math.max(1, (int)Math.ceil((double) totalItems / (double)itemsPerPage));
        page = Math.max(0, Math.min(page, totalPages - 1));
        String categoryName = this.getCategoryDisplayName(category);
        Inventory inv = Bukkit.createInventory(null, (int)54, (Component)this.colorize(this.getMessage("menu.category-title").replace("{category}", categoryName)));
        Cosmetic equipped = this.module.getUserCosmeticsManager().getEquipped(player.getUniqueId(), category);
        String totalLine = this.getMessage("ui.templates.total").replace("{count}", String.valueOf(cosmeticList.size()));
        String equippedLine = equipped != null
            ? this.getMessage("ui.templates.equipped").replace("{name}", equipped.getName())
            : this.getMessage("ui.templates.equipped-none");
        inv.setItem(4, this.createPlayerHeadInfoItem(player));
        int[] slots = new int[]{10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
        int startIndex = page * itemsPerPage;
        for (int i = 0; i < slots.length && startIndex + i < totalItems; ++i) {
            int displayIndex = startIndex + i;
            if (includeEmptyOption && displayIndex == 0) {
                inv.setItem(slots[i], this.createItem(Material.BARRIER, this.getMessage("ui.item-labels.empty-name"), this.getMessage("ui.item-labels.empty-lore")));
                continue;
            }
            int cosmeticIndex = displayIndex - (includeEmptyOption ? 1 : 0);
            if (cosmeticIndex < 0 || cosmeticIndex >= cosmeticList.size()) continue;
            Cosmetic cosmetic = (Cosmetic)cosmeticList.get(cosmeticIndex);
            ItemStack item = this.createCosmeticItem(player, cosmetic, equipped);
            inv.setItem(slots[i], item);
        }
        if (page > 0) {
            ItemStack prev = this.createItem(Material.ARROW, this.getMessage("ui.text-overrides-exact.Previous Page"), new String[0]);
            inv.setItem(48, prev);
        }
        if (page < totalPages - 1) {
            ItemStack next = this.createItem(Material.ARROW, this.getMessage("ui.text-overrides-exact.Next Page"), new String[0]);
            inv.setItem(50, next);
        }
        String pageInfoText = this.getMessage("ui.templates.page")
                .replace("{current}", String.valueOf(page + 1))
                .replace("{total}", String.valueOf(totalPages));
        ItemStack pageInfo = this.createItem(Material.PAPER, pageInfoText, new String[0]);
        inv.setItem(45, pageInfo);
        ItemStack back = this.createItem(Material.DARK_OAK_DOOR, this.getMessage("ui.text-overrides-exact.Back"), new String[0]);
        inv.setItem(49, back);
        if (!includeEmptyOption && equipped != null) {
            String unequipName = this.getMessage("ui.buttons.unequip") + " " + categoryName;
            String removeLine = this.getMessage("ui.templates.remove-named").replace("{name}", equipped.getName());
            ItemStack unequip = this.createItem(Material.REDSTONE, unequipName, removeLine);
            inv.setItem(53, unequip);
        }
        this.openMenus.put(player.getUniqueId(), CATEGORY_MENU_ID + category.getId());
        this.menuPages.put(player.getUniqueId(), page);
        this.selectedCategory.put(player.getUniqueId(), category);
        this.openInventories.put(player.getUniqueId(), inv);
        player.openInventory(inv);
    }

    public void openSettingsMenu(Player player) {
        String settingsTitle = this.getMessage("settings.menu.title");
        if (settingsTitle.equals("settings.menu.title")) settingsTitle = "&8Cosmetics Settings";
        Inventory inv = Bukkit.createInventory(null, (int)54, (Component)this.colorize(settingsTitle));
        UserCosmeticsManager userManager = this.module.getUserCosmeticsManager();
        UserCosmeticSettings settings = userManager.getOrCreateSettings(player.getUniqueId());
        List<String> headerLore = this.getGuiList("settings.menu.header.lore");
        if (headerLore.isEmpty()) {
            headerLore = Collections.singletonList("&7Configure cosmetic visibility and behavior");
        }
        ItemStack header = this.createItem(Material.COMPARATOR, this.getMessage("settings.menu.header.name"), headerLore.toArray(new String[0]));
        inv.setItem(4, header);
        ItemStack showOthers = this.createToggleItem(settings.isShowOthersEffects(), this.getMessage("settings.menu.others.name"), this.getMessage("settings.menu.others.lore"), "", settings.isShowOthersEffects() ? "&aEnabled" : "&cDisabled", "", "&7Click to toggle");
        inv.setItem(11, showOthers);
        ItemStack showMine = this.createToggleItem(settings.isShowMyEffectsToOthers(), this.getMessage("settings.menu.mine.name"), this.getMessage("settings.menu.mine.lore"), "", settings.isShowMyEffectsToOthers() ? "&aEnabled" : "&cDisabled", "", "&7Click to toggle");
        inv.setItem(15, showMine);
        inv.setItem(22, this.createItem(Material.PAPER, this.getMessage("settings.menu.categories.name"), this.getMessage("settings.menu.categories.lore")));
        CosmeticCategory[] settingsCategories = this.getSettingsVisibilityCategories();
        for (int i = 0; i < settingsCategories.length && i < SETTINGS_CATEGORY_SLOTS.length; ++i) {
            // Permission-based visibility: skip category if player lacks permission
            String permKey = SETTINGS_VISIBILITY_LABELS[i].toLowerCase().replace(" ", "_");
            if (!player.hasPermission("smcosm.settings.visibility." + permKey)) continue;

            CosmeticCategory category = settingsCategories[i];
            UserCosmeticSettings.VisibilityMode mode = settings.getCategoryVisibility(category);
            String modeStr;
            if (mode == UserCosmeticSettings.VisibilityMode.ALL) {
                modeStr = "&a" + this.getMessage("settings.mode.all");
            } else if (mode == UserCosmeticSettings.VisibilityMode.SELF_ONLY) {
                modeStr = "&e" + this.getMessage("settings.mode.self_only");
            } else if (mode == UserCosmeticSettings.VisibilityMode.OTHERS_ONLY) {
                modeStr = "&b" + this.getMessage("settings.mode.others_only");
            } else if (mode == UserCosmeticSettings.VisibilityMode.NONE) {
                modeStr = "&c" + this.getMessage("settings.mode.none");
            } else {
                modeStr = "&7Unknown";
            }
            ItemStack catItem = this.createItem(this.getSettingsCategoryIcon(i), "&f" + this.getSettingsCategoryLabel(i), "&7Mode: " + modeStr, "", "&7Click to change");
            inv.setItem(SETTINGS_CATEGORY_SLOTS[i], catItem);
        }
        ItemStack enableAll = this.createItem(Material.LIME_DYE, this.getMessage("ui.text-overrides-exact.Enable All"), "&7Enable all effects");
        inv.setItem(46, enableAll);
        ItemStack disableAll = this.createItem(Material.RED_DYE, this.getMessage("ui.text-overrides-exact.Disable All"), "&7Disable all effects");
        inv.setItem(52, disableAll);
        ItemStack back = this.createItem(Material.ARROW, this.getMessage("ui.text-overrides-exact.Back"), new String[0]);
        inv.setItem(49, back);
        this.openMenus.put(player.getUniqueId(), SETTINGS_MENU_ID);
        this.openInventories.put(player.getUniqueId(), inv);
        player.openInventory(inv);
    }

    private ItemStack createCosmeticItem(Player player, Cosmetic cosmetic, Cosmetic equipped) {
        Material material;
        boolean unlocked = cosmetic.hasPermission(player);
        boolean isEquipped = equipped != null && equipped.getId().equals(cosmetic.getId());
        try {
            String matStr = cosmetic.getItemMaterial().replace("minecraft:", "").toUpperCase();
            material = Material.valueOf((String)matStr);
        }
        catch (Exception e) {
            material = Material.BARRIER;
        }
        if (material == Material.ENCHANTED_BOOK) {
            material = Material.BOOK;
        }
        ArrayList<Object> lore = new ArrayList<Object>();
        if (isEquipped) {
            lore.add(this.getMessage("ui.item-labels.equipped"));
        } else if (unlocked) {
            lore.add(this.getMessage("ui.item-labels.click-to-equip"));
        } else if (cosmetic.isPurchasable()) {
            lore.add(this.getMessage("ui.item-labels.purchase"));
            lore.add("");
            lore.add(this.getMessage("ui.item-labels.price-format").replace("{cost}", String.valueOf(cosmetic.getCost())));
            lore.add("");
            lore.add(this.getMessage("ui.item-labels.click-to-buy"));
        } else {
            lore.add(this.getMessage("ui.item-labels.unavailable"));
            lore.add("");
            lore.add(this.getMessage("ui.item-labels.requirement-label"));
            lore.add("&e" + cosmetic.getPermission());
        }

        String description = this.getCosmeticDescription(cosmetic);
        if (description != null && !description.isBlank()) {
            lore.add("");
            lore.add(description);
        }
        String nameColor = isEquipped ? "&a" : (unlocked ? "&f" : "&7");
        String displayName = this.getCosmeticConfigName(cosmetic);
        ItemStack item = this.createItem(material, nameColor + displayName, lore.toArray(new String[0]));
        if (isEquipped) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                item.setItemMeta(meta);
            }
        }
        return item;
    }

    private String getCosmeticConfigName(Cosmetic cosmetic) {
        if (cosmetic == null) return "";
        YamlConfiguration config = this.resolveCosmeticConfig(cosmetic);
        if (config != null) {
            String name = config.getString("cosmetics." + cosmetic.getId() + ".name", null);
            if (name != null && !name.isBlank()) {
                return name;
            }
        }
        return cosmetic.getName();
    }

    private String getCosmeticDescription(Cosmetic cosmetic) {
        if (cosmetic == null) return null;
        YamlConfiguration config = this.resolveCosmeticConfig(cosmetic);
        if (config == null) return null;
        return config.getString("cosmetics." + cosmetic.getId() + ".description", null);
    }

    private YamlConfiguration resolveCosmeticConfig(Cosmetic cosmetic) {
        if (cosmetic == null) return null;
        String id = cosmetic.getId();
        String fileName;
        if (cosmetic.getCategory() == CosmeticCategory.ARROW_EFFECT) {
            fileName = this.resolveWeaponConfigFile(id);
        } else if (cosmetic.getCategory() == CosmeticCategory.PET) {
            fileName = "pets.yml";
        } else if (cosmetic.getCategory() == CosmeticCategory.BALLOON) {
            fileName = "balloons.yml";
        } else if (cosmetic.getCategory() == CosmeticCategory.PARTICLE_EFFECT) {
            fileName = "particles.yml";
        } else if (cosmetic.getCategory() == CosmeticCategory.DEATH_EFFECT) {
            fileName = "death_effects.yml";
        } else {
            return null;
        }
        return this.plugin.getModuleManager().loadModuleConfig("SM_cosmetics", fileName);
    }

    private String resolveWeaponConfigFile(String id) {
        if (id.startsWith("mace_hit_")) {
            return "mace.yml";
        } else if (id.startsWith("trident_throw_")) {
            return "trident.yml";
        } else if (id.startsWith("trident_riptide_")) {
            return "riptide.yml";
        } else {
            return "arrows.yml";
        }
    }

    private ItemStack createWeaponCategoryItem(Player player, String configKey, Material icon, int total, String equipped) {
        String name = this.getMessage("category-descriptions." + configKey + ".name");
        List<String> rawLore = this.getGuiList("category-descriptions." + configKey + (equipped != null ? ".description-equipped" : ".description"));
        List<String> lore = new ArrayList<>();
        for (String line : rawLore) {
            lore.add(line
                    .replace("{total}", String.valueOf(total))
                    .replace("{equipped}", equipped != null ? equipped : "None"));
        }
        if (name == null || name.equals("category-descriptions." + configKey + ".name")) {
            name = "&6" + configKey;
        }
        return this.createItem(icon, name, lore.toArray(new String[0]));
    }

    private ItemStack createTridentCategoryItem(Player player, int total, String equippedThrow, String equippedRiptide) {
        String name = this.getMessage("category-descriptions.weapon_trident.name");
        boolean hasAny = equippedThrow != null || equippedRiptide != null;
        List<String> rawLore = this.getGuiList("category-descriptions.weapon_trident" + (hasAny ? ".description-equipped" : ".description"));
        List<String> lore = new ArrayList<>();
        for (String line : rawLore) {
            lore.add(line
                    .replace("{total}", String.valueOf(total))
                    .replace("{equipped1}", equippedThrow != null ? equippedThrow : "None")
                    .replace("{equipped2}", equippedRiptide != null ? equippedRiptide : "None"));
        }
        if (name == null || name.equals("category-descriptions.weapon_trident.name")) {
            name = "&6Trident Effects";
        }
        return this.createItem(Material.TRIDENT, name, lore.toArray(new String[0]));
    }

    private int countArrowEffectsByPrefix(String prefix) {
        int count = 0;
        for (Cosmetic cosmetic : this.module.getCosmeticsManager().getCosmeticsByCategory(CosmeticCategory.ARROW_EFFECT)) {
            if (cosmetic.getId().startsWith(prefix)) count++;
        }
        return count;
    }

    private String effectName(ArrowEffectCosmetic effect) {
        return effect == null ? null : this.getCosmeticConfigName(effect);
    }

    private ItemStack createMainCategoryItem(Player player, CosmeticCategory category, int count) {
        Material icon = this.getCategoryIcon(category);
        Cosmetic equipped = this.module.getUserCosmeticsManager().getEquipped(player.getUniqueId(), category);
        String name = this.getCategoryDescriptionName(category);
        if (name == null || name.isBlank()) {
            name = "&6" + this.getCategoryDisplayName(category);
        }
        List<String> rawLore = this.getCategoryDescriptionLore(category, equipped != null);
        List<String> lore = new ArrayList<>();
        if (!rawLore.isEmpty()) {
            for (String line : rawLore) {
                lore.add(line
                        .replace("{total}", String.valueOf(count))
                        .replace("{equipped}", equipped != null ? equipped.getName() : "None"));
            }
        } else {
            lore.add("&7" + this.getCategoryDisplayName(category));
            lore.add("");
            lore.add("&fAvailable: &b" + count);
            if (equipped != null) {
                lore.add("");
                lore.add("&fEquipped: " + equipped.getName());
                lore.add("");
                lore.add("&7Click to open");
            } else {
                lore.add("");
                lore.add("&7Click to open");
            }
        }
        return this.createItem(icon, name, lore.toArray(new String[0]));
    }

    private ItemStack createToggleItem(boolean enabled, String name, String ... lore) {
        Material mat = enabled ? Material.LIME_DYE : Material.GRAY_DYE;
        return this.createItem(mat, name, lore);
    }

    public ItemStack createItem(Material material, String name, String ... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (name != null && !name.equals(" ")) {
            meta.displayName(this.colorize(name).decoration(TextDecoration.ITALIC, false));
        } else {
            meta.displayName((Component)Component.empty());
        }
        if (lore.length > 0) {
            ArrayList<Component> loreComponents = new ArrayList<Component>();
            for (String line : lore) {
                loreComponents.add(this.colorize(line).decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(loreComponents);
        }
        item.setItemMeta(meta);
        return item;
    }

    private Material getCategoryIcon(CosmeticCategory category) {
        if (category == CosmeticCategory.PET) return Material.BONE;
        if (category == CosmeticCategory.PARTICLE_EFFECT) return Material.BLAZE_POWDER;
        if (category == CosmeticCategory.DEATH_EFFECT) return Material.WITHER_SKELETON_SKULL;
        if (category == CosmeticCategory.ARROW_EFFECT) return Material.SPECTRAL_ARROW;
        if (category == CosmeticCategory.BALLOON) return Material.LEAD;
        if (category == CosmeticCategory.EMOTE) return Material.ARMOR_STAND;
        if (category == CosmeticCategory.MORPH) return Material.ZOMBIE_HEAD;
        if (category == CosmeticCategory.MOUNT) return Material.SADDLE;
        if (category == CosmeticCategory.GADGET) return Material.SLIME_BALL;
        if (category == CosmeticCategory.STATUS) return Material.NAME_TAG;
        if (category == CosmeticCategory.BANNER) return Material.WHITE_BANNER;
        if (category == CosmeticCategory.MUSIC) return Material.JUKEBOX;
        return Material.BARRIER;
    }

    private int getEquippedCount(Player player) {
        int count = 0;
        for (CosmeticCategory category : CosmeticCategory.values()) {
            if (!this.module.getUserCosmeticsManager().hasEquipped(player.getUniqueId(), category)) continue;
            ++count;
        }
        return count;
    }

    private int getUnlockedCount(Player player) {
        int count = 0;
        for (Cosmetic cosmetic : this.module.getCosmeticsManager().getAllCosmetics()) {
            if (!cosmetic.hasPermission(player)) continue;
            ++count;
        }
        return count;
    }

    public boolean hasOpenMenu(UUID playerId) {
        return this.openMenus.containsKey(playerId);
    }

    public String getOpenMenuId(UUID playerId) {
        return this.openMenus.get(playerId);
    }

    public Inventory getOpenInventory(UUID playerId) {
        return this.openInventories.get(playerId);
    }

    public int getCurrentPage(UUID playerId) {
        return this.menuPages.getOrDefault(playerId, 0);
    }

    public CosmeticCategory getSelectedCategory(UUID playerId) {
        return this.selectedCategory.get(playerId);
    }

    public CosmeticCategory[] getSettingsVisibilityCategories() {
        return SETTINGS_VISIBILITY_CATEGORIES.clone();
    }

    public int[] getSettingsCategorySlots() {
        return SETTINGS_CATEGORY_SLOTS.clone();
    }

    public String getSettingsCategoryLabel(int index) {
        if (index < 0 || index >= SETTINGS_VISIBILITY_LABELS.length) {
            return "Unknown";
        }
        return switch (index) {
            case 0 -> this.getMessage("categories.pet");
            case 1 -> this.getMessage("categories.particle_effect");
            case 2 -> this.getMessage("categories.death_effect");
            case 3 -> this.getMessage("categories.balloon");
            case 4 -> this.getMessage("categories.weapon_bow");
            case 5 -> this.getMessage("categories.weapon_mace");
            case 6 -> this.getMessage("categories.weapon_trident");
            case 7 -> this.getMessage("categories.weapon_riptide");
            default -> SETTINGS_VISIBILITY_LABELS[index];
        };
    }

    public Material getSettingsCategoryIcon(int index) {
        if (index < 0 || index >= SETTINGS_VISIBILITY_ICONS.length) {
            return Material.BARRIER;
        }
        return SETTINGS_VISIBILITY_ICONS[index];
    }

    public String getGuiMessage(String key) {
        return this.getMessage(key);
    }

    public String translateCategory(CosmeticCategory category) {
        return this.getCategoryDisplayName(category);
    }

    private String getCategoryDisplayName(CosmeticCategory category) {
        if (category == null) {
            return "";
        }
        for (String alias : this.getCategoryKeyAliases(category)) {
            String key = "categories." + alias;
            String value = this.getMessage(key);
            if (value != null && !value.isBlank() && !value.equals(key)) {
                return value;
            }
        }
        return category.getDisplayName();
    }

    private String getCategoryDescriptionName(CosmeticCategory category) {
        for (String alias : this.getCategoryKeyAliases(category)) {
            String key = "category-descriptions." + alias + ".name";
            String value = this.getMessage(key);
            if (value != null && !value.isBlank() && !value.equals(key)) {
                return value;
            }
        }
        return null;
    }

    private List<String> getCategoryDescriptionLore(CosmeticCategory category, boolean equipped) {
        String suffix = equipped ? ".description-equipped" : ".description";
        for (String alias : this.getCategoryKeyAliases(category)) {
            List<String> lore = this.getGuiList("category-descriptions." + alias + suffix);
            if (!lore.isEmpty()) {
                return lore;
            }
        }
        return Collections.emptyList();
    }

    private String[] getCategoryKeyAliases(CosmeticCategory category) {
        if (category == CosmeticCategory.PET) {
            return new String[]{"pet", "pets"};
        }
        if (category == CosmeticCategory.DEATH_EFFECT) {
            return new String[]{"death_effect", "death", "death_effects"};
        }
        if (category == CosmeticCategory.BALLOON) {
            return new String[]{"balloon", "ballon", "balloons"};
        }
        return new String[]{category.getId()};
    }

    public String getCosmeticDisplayName(Cosmetic cosmetic) {
        return cosmetic == null ? "" : this.getCosmeticConfigName(cosmetic);
    }

    public void openBowEffectsMenu(Player player) {
        this.openEffectSelectionMenu(player, "bow_", BOW_EFFECTS_MENU_ID, "weapon-effects.menu.bow.title", Material.SPECTRAL_ARROW);
    }

    public void openMaceEffectsMenu(Player player) {
        this.openEffectSelectionMenu(player, "mace_hit_", MACE_EFFECTS_MENU_ID, "weapon-effects.menu.mace.title", Material.MACE);
    }

    public void openTridentEffectsMenu(Player player) {
        String title = this.getMessage("weapon-effects.menu.trident.title");
        if (title == null || title.equals("weapon-effects.menu.trident.title")) {
            title = "&8Trident Effects";
        }
        Inventory inv = Bukkit.createInventory(null, 54, this.colorize(title));
        inv.setItem(4, this.createPlayerHeadInfoItem(player));
        inv.setItem(20, this.createItem(Material.TRIDENT, this.getMessage("weapon-effects.menu.trident.throw.name"), this.getMessage("weapon-effects.menu.trident.throw.lore")));
        inv.setItem(24, this.createItem(Material.HEART_OF_THE_SEA, this.getMessage("weapon-effects.menu.trident.riptide.name"), this.getMessage("weapon-effects.menu.trident.riptide.lore")));
        inv.setItem(49, this.createItem(Material.ARROW, this.getMessage("weapon-effects.menu.back")));

        this.openMenus.put(player.getUniqueId(), TRIDENT_EFFECTS_MENU_ID);
        this.openInventories.put(player.getUniqueId(), inv);
        player.openInventory(inv);
    }

    public void openTridentThrowEffectsMenu(Player player) {
        this.openEffectSelectionMenu(player, "trident_throw_", TRIDENT_THROW_EFFECTS_MENU_ID, "weapon-effects.menu.trident.throw.title", Material.TRIDENT);
    }

    public void openTridentRiptideEffectsMenu(Player player) {
        this.openEffectSelectionMenu(player, "trident_riptide_", TRIDENT_RIPTIDE_EFFECTS_MENU_ID, "weapon-effects.menu.trident.riptide.title", Material.HEART_OF_THE_SEA);
    }

    public void openWeaponEffectsMenu(Player player) {
        String title = this.getMessage("weapon-effects.menu.title");
        if (title == null || title.equals("weapon-effects.menu.title")) {
            title = "&8Weapon Effects";
        }
        Inventory inv = Bukkit.createInventory(null, 54, this.colorize(title));
        inv.setItem(4, this.createPlayerHeadInfoItem(player));

        inv.setItem(29, this.createItem(Material.SPECTRAL_ARROW, this.getMessage("weapon-effects.menu.bow.name"), this.getMessage("weapon-effects.menu.bow.lore"), "", this.getMessage("weapon-effects.menu.open")));
        inv.setItem(31, this.createItem(Material.MACE, this.getMessage("weapon-effects.menu.mace.name"), this.getMessage("weapon-effects.menu.mace.lore"), "", this.getMessage("weapon-effects.menu.open")));
        inv.setItem(33, this.createItem(Material.TRIDENT, this.getMessage("weapon-effects.menu.trident.name"), this.getMessage("weapon-effects.menu.trident.lore"), "", this.getMessage("weapon-effects.menu.open")));
        inv.setItem(49, this.createItem(Material.ARROW, this.getMessage("weapon-effects.menu.back")));

        this.openMenus.put(player.getUniqueId(), WEAPON_EFFECTS_MENU_ID);
        this.openInventories.put(player.getUniqueId(), inv);
        player.openInventory(inv);
    }

    private void openEffectSelectionMenu(Player player, String prefix, String menuId, String titleKey, Material fallbackIcon) {
        String title = this.getMessage(titleKey);
        if (title == null || title.equals(titleKey)) {
            title = "&8Select Effect";
        }
        Inventory inv = Bukkit.createInventory(null, 54, this.colorize(title));
        inv.setItem(4, this.createPlayerHeadInfoItem(player));

        int[] slots = {
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                37, 38, 39, 40, 41, 42, 43
        };

        ArrowEffectCosmetic equipped = this.getEquippedEffectByPrefix(player.getUniqueId(), prefix);
        boolean isNoneEquipped = equipped == null;

        String noneName = isNoneEquipped
            ? this.getMessage("ui.item-labels.empty-name").replace("&c", "&a")
            : this.getMessage("ui.item-labels.empty-name");
        String noneLore = isNoneEquipped
            ? this.getMessage("ui.item-labels.selected")
            : this.getMessage("ui.item-labels.click-to-equip");
        inv.setItem(slots[0], this.createItem(Material.BARRIER, noneName, noneLore));

        List<Cosmetic> effects = this.getArrowEffectsByPrefix(player, prefix);
        for (int i = 1; i < slots.length && i - 1 < effects.size(); i++) {
            Cosmetic cosmetic = effects.get(i - 1);
            ItemStack item = this.createCosmeticItem(player, cosmetic, equipped);
            if (item.getType() == Material.AIR) {
                item = this.createItem(fallbackIcon, "&f" + this.getCosmeticConfigName(cosmetic));
            }
            inv.setItem(slots[i], item);
        }

        inv.setItem(49, this.createItem(Material.ARROW, this.getMessage("weapon-effects.menu.back")));

        this.openMenus.put(player.getUniqueId(), menuId);
        this.openInventories.put(player.getUniqueId(), inv);
        player.openInventory(inv);
    }

    private ArrowEffectCosmetic getEquippedEffectByPrefix(UUID playerId, String prefix) {
        return switch (prefix) {
            case "bow_" -> ArrowEffectCosmetic.getActiveBowTrailEffect(playerId);
            case "mace_hit_" -> ArrowEffectCosmetic.getActiveMaceEffect(playerId);
            case "trident_throw_" -> ArrowEffectCosmetic.getActiveTridentThrowEffect(playerId);
            case "trident_riptide_" -> ArrowEffectCosmetic.getActiveTridentRiptideEffect(playerId);
            default -> null;
        };
    }

    private List<Cosmetic> getArrowEffectsByPrefix(Player player, String prefix) {
        List<Cosmetic> effects = new ArrayList<>();
        for (Cosmetic cosmetic : this.module.getCosmeticsManager().getCosmeticsByCategory(CosmeticCategory.ARROW_EFFECT)) {
            if (!cosmetic.isVisibleTo(player)) {
                continue;
            }
            if (cosmetic.getId().startsWith(prefix)) {
                effects.add(cosmetic);
            }
        }
        return effects;
    }

    public void openCatVariantsMenu(Player player, int page) {
        Inventory inv = Bukkit.createInventory(null, 54, this.colorize("&8Cat Variants"));

        List<Cosmetic> catVariants = new ArrayList<>();
        Collection<Cosmetic> pets = this.module.getCosmeticsManager().getCosmeticsByCategory(CosmeticCategory.PET);
        for (Cosmetic cosmetic : pets) {
            if (!cosmetic.isVisibleTo(player)) {
                continue;
            }
            if (cosmetic.getId().startsWith("cat_") && !cosmetic.getId().equals("cat_pet")) {
                catVariants.add(cosmetic);
            }
        }

        Cosmetic equipped = this.module.getUserCosmeticsManager().getEquipped(player.getUniqueId(), CosmeticCategory.PET);
        inv.setItem(4, this.createPlayerHeadInfoItem(player));

        int[] slots = new int[]{
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
        };

        for (int i = 0; i < slots.length && i < catVariants.size(); i++) {
            inv.setItem(slots[i], this.createCosmeticItem(player, catVariants.get(i), equipped));
        }

        inv.setItem(48, this.createItem(Material.DARK_OAK_DOOR, this.getMessage("ui.text-overrides-exact.Back")));
        inv.setItem(50, this.createItem(Material.BARRIER, this.getMessage("ui.item-labels.empty-name"), this.getMessage("ui.item-labels.empty-lore")));

        this.openMenus.put(player.getUniqueId(), CAT_VARIANTS_MENU_ID);
        this.menuPages.put(player.getUniqueId(), Math.max(0, page));
        this.selectedCategory.put(player.getUniqueId(), CosmeticCategory.PET);
        this.openInventories.put(player.getUniqueId(), inv);
        player.openInventory(inv);
    }

    public void openParrotVariantsMenu(Player player, int page) {
        Inventory inv = Bukkit.createInventory(null, 54, this.colorize("&8Parrot Variants"));

        List<Cosmetic> parrotVariants = new ArrayList<>();
        Collection<Cosmetic> pets = this.module.getCosmeticsManager().getCosmeticsByCategory(CosmeticCategory.PET);
        for (Cosmetic cosmetic : pets) {
            if (!cosmetic.isVisibleTo(player)) {
                continue;
            }
            if (cosmetic.getId().startsWith("parrot_") && !cosmetic.getId().equals("parrot_pet")) {
                parrotVariants.add(cosmetic);
            }
        }

        Cosmetic equipped = this.module.getUserCosmeticsManager().getEquipped(player.getUniqueId(), CosmeticCategory.PET);
        inv.setItem(4, this.createPlayerHeadInfoItem(player));

        int[] slots = new int[]{
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
        };

        for (int i = 0; i < slots.length && i < parrotVariants.size(); i++) {
            inv.setItem(slots[i], this.createCosmeticItem(player, parrotVariants.get(i), equipped));
        }

        inv.setItem(48, this.createItem(Material.DARK_OAK_DOOR, this.getMessage("ui.text-overrides-exact.Back")));
        inv.setItem(50, this.createItem(Material.BARRIER, this.getMessage("ui.item-labels.empty-name"), this.getMessage("ui.item-labels.empty-lore")));

        this.openMenus.put(player.getUniqueId(), PARROT_VARIANTS_MENU_ID);
        this.menuPages.put(player.getUniqueId(), Math.max(0, page));
        this.selectedCategory.put(player.getUniqueId(), CosmeticCategory.PET);
        this.openInventories.put(player.getUniqueId(), inv);
        player.openInventory(inv);
    }

    public void openFrogVariantsMenu(Player player, int page) {
        Inventory inv = Bukkit.createInventory(null, 54, this.colorize("&8Frog Variants"));

        List<Cosmetic> variants = new ArrayList<>();
        Collection<Cosmetic> pets = this.module.getCosmeticsManager().getCosmeticsByCategory(CosmeticCategory.PET);
        for (Cosmetic cosmetic : pets) {
            if (!cosmetic.isVisibleTo(player)) {
                continue;
            }
            if (cosmetic.getId().startsWith("frog_") && !cosmetic.getId().equals("frog_pet")) {
                variants.add(cosmetic);
            }
        }

        Cosmetic equipped = this.module.getUserCosmeticsManager().getEquipped(player.getUniqueId(), CosmeticCategory.PET);
        inv.setItem(4, this.createPlayerHeadInfoItem(player));

        int[] slots = new int[]{
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
        };

        for (int i = 0; i < slots.length && i < variants.size(); i++) {
            inv.setItem(slots[i], this.createCosmeticItem(player, variants.get(i), equipped));
        }

        inv.setItem(48, this.createItem(Material.DARK_OAK_DOOR, this.getMessage("ui.text-overrides-exact.Back")));
        inv.setItem(50, this.createItem(Material.BARRIER, this.getMessage("ui.item-labels.empty-name"), this.getMessage("ui.item-labels.empty-lore")));

        this.openMenus.put(player.getUniqueId(), FROG_VARIANTS_MENU_ID);
        this.menuPages.put(player.getUniqueId(), Math.max(0, page));
        this.selectedCategory.put(player.getUniqueId(), CosmeticCategory.PET);
        this.openInventories.put(player.getUniqueId(), inv);
        player.openInventory(inv);
    }

    public String getSelectedBalloonGroup(UUID playerId) {
        return this.selectedBalloonGroup.get(playerId);
    }

    public void openBalloonVariantsMenu(Player player, int page, String groupId) {
        // Fallback: open balloon category menu
        this.openCategoryMenu(player, CosmeticCategory.BALLOON, page);
    }

    public java.util.List<Cosmetic> getBalloonVariants(Player player, String groupId) {
        return new ArrayList<>();
    }

    public void closeMenu(UUID playerId) {
        this.openMenus.remove(playerId);
        this.menuPages.remove(playerId);
        this.selectedCategory.remove(playerId);
        this.openInventories.remove(playerId);
    }

    private ItemStack createPlayerHeadInfoItem(Player player) {
        String rawName = this.getMessage("menu.player-head.name").replace("{player}", player.getName());
        List<String> rawLore = this.getGuiList("menu.player-head.lore");
        List<String> lore = new ArrayList<>();
        for (String line : rawLore) {
            lore.add(line
                .replace("{equipped}", String.valueOf(this.getEquippedCount(player)))
                .replace("{total}", String.valueOf(this.getUnlockedCount(player))));
        }

        ItemStack playerInfo = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta skullMeta = (SkullMeta) playerInfo.getItemMeta();
        if (skullMeta != null) {
            skullMeta.setOwningPlayer(player);
            if (rawName != null && !rawName.isBlank()) {
                skullMeta.displayName(this.colorize(rawName).decoration(TextDecoration.ITALIC, false));
            }
            if (!lore.isEmpty()) {
                List<Component> comps = new ArrayList<>();
                for (String ln : lore) {
                    comps.add(this.colorize(ln).decoration(TextDecoration.ITALIC, false));
                }
                skullMeta.lore(comps);
            }
            playerInfo.setItemMeta(skullMeta);
        }
        return playerInfo;
    }
}
