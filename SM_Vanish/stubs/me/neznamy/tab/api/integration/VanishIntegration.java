package me.neznamy.tab.api.integration;

import me.neznamy.tab.api.TabPlayer;

public abstract class VanishIntegration {

   private final String name;

   protected VanishIntegration(String name) {
      this.name = name;
   }

   public String getName() {
      return this.name;
   }

   public boolean isVanished(TabPlayer player) {
      return false;
   }

   public boolean canSee(TabPlayer viewer, TabPlayer target) {
      return true;
   }
}
