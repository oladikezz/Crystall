package net.schalker.SMPS.modules.flags;

import org.bukkit.Material;

public enum FlagType {
   // Low severity (green) - 0x2ECC71
   TNT_INVENTORY("tnt_inventory", "TNT в инвентаре", Material.TNT, FlagSeverity.LOW),
   LAVA_PLACEMENT("lava_placement", "Размещение лавы на высоте", Material.LAVA_BUCKET, FlagSeverity.LOW),
   WHITELIST_ADD("whitelist_add", "Добавление в вайтлист", Material.WRITABLE_BOOK, FlagSeverity.LOW),
   OP_GRANT("op_grant", "Выдача прав оператора", Material.COMMAND_BLOCK, FlagSeverity.LOW),
   RARE_ITEM("rare_item", "Редкий предмет в инвентаре", Material.NETHER_STAR, FlagSeverity.MEDIUM),
   
   // Medium severity (yellow) - 0xF39C12
   TNT_PLACEMENT_OVERWORLD("tnt_placement_overworld", "Размещение TNT (Обычный мир)", Material.TNT, FlagSeverity.MEDIUM),
   TNT_PLACEMENT_NETHER("tnt_placement_nether", "Размещение TNT (Незер)", Material.TNT, FlagSeverity.HIGH),
   FIRE_IGNITE("fire_ignite", "Поджог блоков", Material.FLINT_AND_STEEL, FlagSeverity.MEDIUM),
   VILLAGER_KILL("villager_kill", "Убийство жителей", Material.EMERALD, FlagSeverity.MEDIUM),
   PET_KILL("pet_kill", "Убийство питомца", Material.BONE, FlagSeverity.MEDIUM),
   END_CRYSTAL_EXPLODE("end_crystal_explode", "Разрушение блоков кристаллом", Material.END_CRYSTAL, FlagSeverity.MEDIUM),
   END_CRYSTAL_DAMAGE_ENTITY("end_crystal_damage_entity", "Урон сущностям кристаллом", Material.END_CRYSTAL, FlagSeverity.MEDIUM),
   END_CRYSTAL_DAMAGE_PLAYER("end_crystal_damage_player", "Урон игрокам кристаллом", Material.END_CRYSTAL, FlagSeverity.HIGH),
   CHAT_SPAM("chat_spam", "Спам в чате", Material.PAPER, FlagSeverity.MEDIUM),
   CHAT_REPEAT("chat_repeat", "Повторяющиеся сообщения", Material.PAPER, FlagSeverity.MEDIUM),
   ORE_PICKUP("ore_pickup", "Подбор руд", Material.DIAMOND_ORE, FlagSeverity.MEDIUM),
   ANCIENT_DEBRIS_PICKUP("ancient_debris_pickup", "Подбор древних обломков", Material.ANCIENT_DEBRIS, FlagSeverity.MEDIUM),
   WITHER_CONTAINER_DESTROY("wither_container_destroy", "Визер уничтожил хранилища", Material.WITHER_SKELETON_SKULL, FlagSeverity.MEDIUM),
   CONTAINER_BREAK("container_break", "Разрушение контейнеров с предметами", Material.BARREL, FlagSeverity.MEDIUM),
   SHULKER_BREAK("shulker_break", "Разрушение шалкеров с предметами", Material.SHULKER_BOX, FlagSeverity.LOW),

   // High severity (red) - 0xE74C3C
   PLAYER_KILL("player_kill", "Убийство игроков", Material.DIAMOND_SWORD, FlagSeverity.HIGH),
   CONTAINER_EXPLOSION("container_explosion", "Взрыв контейнеров", Material.BARREL, FlagSeverity.HIGH),
   CONTAINER_BURN("container_burn", "Горение контейнеров", Material.BARREL, FlagSeverity.HIGH),
   CONTAINER_DROP("container_drop", "Выброс предметов из хранилища", Material.DROPPER, FlagSeverity.MEDIUM),
   BIG_DATA_ITEM_INTERACT("big_data_item_interact", "Взаимодействие с большим NBT-предметом", Material.WRITTEN_BOOK, FlagSeverity.HIGH),
   WITHER_SUMMON_OVERWORLD("wither_summon_overworld", "Призыв визера (Обычный мир)", Material.WITHER_SKELETON_SKULL, FlagSeverity.HIGH),
   WITHER_SUMMON_NETHER("wither_summon_nether", "Призыв визера (Незер)", Material.WITHER_SKELETON_SKULL, FlagSeverity.HIGH),
   WITHER_SUMMON_END("wither_summon_end", "Призыв визера (Энд)", Material.WITHER_SKELETON_SKULL, FlagSeverity.HIGH),
   ADMIN_BAN("admin_ban", "Выдача банов", Material.BARRIER, FlagSeverity.HIGH),
   ADMIN_WARN("admin_warn", "Выдача варнов", Material.WOODEN_SWORD, FlagSeverity.HIGH),
   ADMIN_MUTE("admin_mute", "Выдача мутов", Material.IRON_BARS, FlagSeverity.HIGH),
   
   // High severity - storage transport
   BOAT_CHEST_PLACE("boat_chest_place", "Размещение транспорта с хранилищем", Material.CHEST, FlagSeverity.HIGH),
   BOAT_CHEST_ENTER("boat_chest_enter", "Вход в транспорт с хранилищем", Material.CHEST, FlagSeverity.HIGH),
   BOAT_CHEST_EXIT("boat_chest_exit", "Выход из транспорта с хранилищем", Material.CHEST, FlagSeverity.HIGH),
   BOAT_CHEST_INTERACT("boat_chest_interact", "Взаимодействие с транспортом с хранилищем", Material.CHEST, FlagSeverity.HIGH);

   private final String key;
   private final String displayName;
   private final Material icon;
   private final FlagSeverity severity;
   private final boolean noCooldown;

   FlagType(String key, String displayName, Material icon, FlagSeverity severity) {
      this(key, displayName, icon, severity, false);
   }

   FlagType(String key, String displayName, Material icon, FlagSeverity severity, boolean noCooldown) {
      this.key = key;
      this.displayName = displayName;
      this.icon = icon;
      this.severity = severity;
      this.noCooldown = noCooldown;
   }

   public String getKey() {
      return this.key;
   }

   public String getDisplayName() {
      return this.displayName;
   }

   public Material getIcon() {
      return this.icon;
   }

   public FlagSeverity getSeverity() {
      return this.severity;
   }

   public int getEmbedColor() {
      return this.severity.getColor();
   }

   public boolean isNoCooldown() {
      return this.noCooldown;
   }

   public boolean isStorageTransport() {
      return this == BOAT_CHEST_PLACE || this == BOAT_CHEST_ENTER
            || this == BOAT_CHEST_EXIT || this == BOAT_CHEST_INTERACT;
   }

   public static FlagType fromKey(String key) {
      if (key == null) return null;
      for (FlagType type : values()) {
         if (type.key.equalsIgnoreCase(key)) {
            return type;
         }
      }
      return null;
   }

   /**
    * Resolve a config key that may be an alias (e.g. "tnt_placement") to
    * all matching FlagType entries.  If the key matches a concrete enum it
    * returns a single-element list; otherwise it tries to find all enums
    * whose key starts with the alias followed by "_".
    * This allows old config keys like "tnt_placement" or "wither_summon"
    * to expand to "tnt_placement_overworld", "tnt_placement_nether" etc.
    */
   public static java.util.List<FlagType> fromKeyExpanded(String key) {
      if (key == null) return java.util.Collections.emptyList();
      // Exact match first
      FlagType exact = fromKey(key);
      if (exact != null) {
         return java.util.Collections.singletonList(exact);
      }
      // Try alias expansion: key is a prefix
      java.util.List<FlagType> result = new java.util.ArrayList<>();
      String prefix = key.toLowerCase() + "_";
      for (FlagType type : values()) {
         if (type.key.toLowerCase().startsWith(prefix)) {
            result.add(type);
         }
      }
      return result;
   }

   public enum FlagSeverity {
      LOW(0x2ECC71, "Низкий"),    // Green
      MEDIUM(0xF39C12, "Средний"), // Yellow
      HIGH(0xE74C3C, "Высокий");   // Red

      private final int color;
      private final String name;

      FlagSeverity(int color, String name) {
         this.color = color;
         this.name = name;
      }

      public int getColor() {
         return this.color;
      }

      public String getName() {
         return this.name;
      }
   }
}
