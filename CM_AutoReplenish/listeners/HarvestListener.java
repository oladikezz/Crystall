package net.schalker.SMPS.modules.autoreplenish.listeners;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.listener.BaseListener;
import net.schalker.SMPS.modules.autoreplenish.AutoReplenishModule;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * ПКМ мотыгой по полностью выросшему урожаю - урожай собирается (дроп считается
 * через getDrops(мотыга), поэтому Удача на мотыге учитывается сама собой) и грядка
 * сразу засаживается заново. Мотыга теряет прочность с учётом зачарования "Прочность".
 */
public class HarvestListener extends BaseListener {

   private static final Set<Material> CROPS = EnumSet.of(
      Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS);

   private final AutoReplenishModule module;

   public HarvestListener(DoAPI plugin, AutoReplenishModule module) {
      super(plugin);
      this.module = module;
   }

   @EventHandler
   public void onInteract(PlayerInteractEvent event) {
      if (!this.module.isFeatureEnabled()) {
         return;
      }
      if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
         return;
      }

      Block block = event.getClickedBlock();
      if (block == null || !CROPS.contains(block.getType())) {
         return;
      }
      if (!(block.getBlockData() instanceof Ageable ageable) || ageable.getAge() < ageable.getMaximumAge()) {
         return;
      }

      Player player = event.getPlayer();
      if (!player.hasPermission("smautoreplenish.use")) {
         return;
      }

      ItemStack hoe = player.getInventory().getItemInMainHand();
      if (!isHoe(hoe.getType())) {
         return;
      }

      event.setCancelled(true);

      for (ItemStack drop : block.getDrops(hoe)) {
         block.getWorld().dropItemNaturally(block.getLocation(), drop);
      }

      ageable.setAge(0);
      block.setBlockData(ageable);

      damageHoe(player, hoe, this.module.getDurabilityCost());
   }

   private boolean isHoe(Material material) {
      return material.name().endsWith("_HOE");
   }

   private void damageHoe(Player player, ItemStack hoe, int points) {
      if (points <= 0) {
         return;
      }
      ItemMeta meta = hoe.getItemMeta();
      if (!(meta instanceof Damageable damageable)) {
         return;
      }

      int unbreakingLevel = hoe.getEnchantmentLevel(Enchantment.UNBREAKING);
      int actualDamage = 0;
      for (int i = 0; i < points; i++) {
         // Ванильная формула: шанс реально потратить каждую единицу прочности - 1 / (уровень + 1).
         if (unbreakingLevel == 0 || ThreadLocalRandom.current().nextInt(unbreakingLevel + 1) == 0) {
            actualDamage++;
         }
      }
      if (actualDamage == 0) {
         return;
      }

      int newDamage = damageable.getDamage() + actualDamage;
      if (newDamage >= hoe.getType().getMaxDurability()) {
         player.getInventory().setItemInMainHand(null);
         player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
         player.sendMessage(this.module.getMessage("autoreplenish.hoe-broken",
            "&[SECONDARY]Ваша мотыга сломалась от автосбора урожая!"));
         return;
      }

      damageable.setDamage(newDamage);
      hoe.setItemMeta(meta);
      player.getInventory().setItemInMainHand(hoe);
   }
}
