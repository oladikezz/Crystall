package net.schalker.SMPS.modules.stats;

import java.util.UUID;

public class TopEntry {
   private final UUID uuid;
   private final String name;
   private final double value;

   public TopEntry(UUID uuid, String name, double value) {
      this.uuid = uuid;
      this.name = name;
      this.value = value;
   }

   public UUID getUuid() {
      return this.uuid;
   }

   public String getName() {
      return this.name;
   }

   public double getValue() {
      return this.value;
   }
}