package net.schalker.SMPS.modules.lightcraft.listeners;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Light;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.inventory.ItemStack;
import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.debug.DebugSystem;
import net.schalker.DoAPI.core.listener.BaseListener;
import net.schalker.SMPS.modules.lightcraft.LightCraftModule;

public class LightBlockListener extends BaseListener {
   public LightBlockListener(DoAPI plugin) {
      super(plugin);
   }

   @EventHandler(
      priority = EventPriority.HIGHEST
   )
   public void onBlockDamage(BlockDamageEvent event) {
      Block block = event.getBlock();
      Player player = event.getPlayer();
      if (block.getType() == Material.LIGHT) {
         if (player.getGameMode() == GameMode.SURVIVAL) {
            int lightLevel = 15;
            BlockData blockData = block.getBlockData();
            if (blockData instanceof Light) {
               Light light = (Light)blockData;
               lightLevel = light.getLevel();
            }

            ItemStack lightItem = LightCraftModule.getInstance().createLightBlock(lightLevel);
            block.setType(Material.AIR);
            block.getWorld().dropItemNaturally(block.getLocation(), lightItem);
            DebugSystem debugSystem = this.plugin.getDebugSystem();
            String playerName = player.getName();
            debugSystem.log("LightBlockListener", playerName + " сломал блок света уровня " + lightLevel + " в выживании");
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST
   )
   public void onBlockBreak(BlockBreakEvent event) {
      Block block = event.getBlock();
      if (block.getType() == Material.LIGHT) {
         event.setDropItems(false);
      }
   }
}
