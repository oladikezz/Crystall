package net.schalker.SMPS.modules.stats;

import org.bukkit.Material;

public enum StatsMetric {
   PLAYTIME("total_minutes", "Время в игре", Material.CLOCK, false),
   DAILY_MINUTES("daily_minutes", "За день", Material.SUNFLOWER, false),
   WEEKLY_MINUTES("weekly_minutes", "За неделю", Material.CLOCK, false),
   MONTHLY_MINUTES("monthly_minutes", "За месяц", Material.CLOCK, false),
   DEATHS("deaths", "Смерти", Material.SKELETON_SKULL, false),
   PLAYER_KILLS("player_kills", "Убийства игроков", Material.DIAMOND_SWORD, false),
   MOB_KILLS("mob_kills", "Убийства мобов", Material.IRON_SWORD, false),
   BLOCKS_BROKEN("blocks_broken", "Сломано блоков", Material.DIAMOND_PICKAXE, false),
   BLOCKS_PLACED("blocks_placed", "Поставлено блоков", Material.GRASS_BLOCK, false),
   ITEMS_CRAFTED("items_crafted", "Скрафчено", Material.CRAFTING_TABLE, false),
   CHAT_MESSAGES("chat_messages", "Сообщения", Material.PAPER, false),
   DIST_WALK("dist_walk", "Дистанция пешком", Material.LEATHER_BOOTS, true),
   DIST_SWIM("dist_swim", "Дистанция вплавь", Material.WATER_BUCKET, true),
   DIST_FLY("dist_fly", "Дистанция в полете", Material.ELYTRA, true),
   TOTAL_DISTANCE("total_distance", "Общая дистанция", Material.COMPASS, true),
   ACHIEVEMENTS("achievements", "Достижения", Material.NETHER_STAR, false);

   private final String key;
   private final String title;
   private final Material material;
   private final boolean distance;

   StatsMetric(String key, String title, Material material, boolean distance) {
      this.key = key;
      this.title = title;
      this.material = material;
      this.distance = distance;
   }

   public String getKey() {
      return this.key;
   }

   public String getTitle() {
      return this.title;
   }

   public Material getMaterial() {
      return this.material;
   }

   public boolean isDistance() {
      return this.distance;
   }

   public boolean isTime() {
      return this == PLAYTIME || this == DAILY_MINUTES || this == WEEKLY_MINUTES || this == MONTHLY_MINUTES;
   }

   public static StatsMetric fromKey(String key) {
      if (key == null) {
         return null;
      }
      String normalized = key.toLowerCase();
      for (StatsMetric metric : values()) {
         if (metric.key.equals(normalized)) {
            return metric;
         }
      }
      return null;
   }
}