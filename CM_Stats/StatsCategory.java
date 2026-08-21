package net.schalker.SMPS.modules.stats;

import java.util.List;
import org.bukkit.Material;

public class StatsCategory {
   private final String id;
   private final String title;
   private final String subtitle;
   private final Material icon;
   private final List<Entry> entries;

   public StatsCategory(String id, String title, Material icon, List<Entry> entries) {
      this(id, title, null, icon, entries);
   }

   public StatsCategory(String id, String title, String subtitle, Material icon, List<Entry> entries) {
      this.id = id;
      this.title = title;
      this.subtitle = subtitle;
      this.icon = icon;
      this.entries = entries;
   }

   public String getId() {
      return this.id;
   }

   public String getTitle() {
      return this.title;
   }

   public String getSubtitle() {
      return this.subtitle;
   }

   public Material getIcon() {
      return this.icon;
   }

   public List<Entry> getEntries() {
      return this.entries;
   }

   public boolean hasSubMenu() {
      return this.entries != null && this.entries.size() > 1;
   }

   public static class Entry {
      private final StatsMetric metric;
      private final String title;

      public Entry(StatsMetric metric, String title) {
         this.metric = metric;
         this.title = title;
      }

      public StatsMetric getMetric() {
         return this.metric;
      }

      public String getTitle() {
         return this.title;
      }
   }
}
