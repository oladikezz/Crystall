package net.schalker.SMPS.modules.fastleaves.listeners;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.listener.BaseListener;
import net.schalker.SMPS.modules.fastleaves.FastLeavesModule;
import org.bukkit.Location;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

/** Ловит исчезновение бревна (топором, огнём, взрывом) и перепроверяет листву рядом. */
public class LeafDecayListener extends BaseListener {

   private static final BlockFace[] NEIGHBORS = {
      BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN
   };

   private final FastLeavesModule module;
   // Уникальное имя на каждую отложенную проверку - региональный шедулер Folia
   // трекает задачи по имени, а проверки в разных точках карты не должны конфликтовать.
   private final AtomicLong taskCounter = new AtomicLong();

   public LeafDecayListener(DoAPI plugin, FastLeavesModule module) {
      super(plugin);
      this.module = module;
   }

   @EventHandler
   public void onBreak(BlockBreakEvent event) {
      handle(event.getBlock());
   }

   @EventHandler
   public void onBurn(BlockBurnEvent event) {
      handle(event.getBlock());
   }

   @EventHandler
   public void onBlockExplode(BlockExplodeEvent event) {
      for (Block block : event.blockList()) {
         handle(block);
      }
   }

   @EventHandler
   public void onEntityExplode(EntityExplodeEvent event) {
      for (Block block : event.blockList()) {
         handle(block);
      }
   }

   private void handle(Block block) {
      if (!this.module.isFeatureEnabled()) {
         return;
      }
      if (!Tag.LOGS.isTagged(block.getType())) {
         return;
      }
      scheduleLeafCheck(block.getLocation());
   }

   private void scheduleLeafCheck(Location logLocation) {
      String taskName = "fastleaves-check-" + this.taskCounter.incrementAndGet();
      // Регионный (не глобальный) шедулер - проверка листвы должна выполняться
      // на потоке того же региона Folia, которому принадлежит блок бревна.
      this.plugin.getSchedulerManager().runRegionTaskLater(
         logLocation, taskName, () -> checkFromLog(logLocation), this.module.getCheckDelayTicks());
   }

   private void checkFromLog(Location logLocation) {
      World world = logLocation.getWorld();
      if (world == null) {
         return;
      }

      int maxScanned = this.module.getMaxLeavesScanned();
      Block logBlock = world.getBlockAt(logLocation);

      Set<Block> visited = new HashSet<>();
      Deque<Block> queue = new ArrayDeque<>();
      List<Block> decayable = new ArrayList<>();
      boolean connectedToLog = false;
      boolean capped = false;

      for (BlockFace face : NEIGHBORS) {
         Block neighbor = logBlock.getRelative(face);
         if (neighbor.getBlockData() instanceof Leaves && visited.add(neighbor)) {
            queue.add(neighbor);
         }
      }

      while (!queue.isEmpty()) {
         if (visited.size() > maxScanned) {
            capped = true;
            break;
         }

         Block current = queue.poll();
         Leaves currentLeaves = (Leaves) current.getBlockData();
         if (!currentLeaves.isPersistent()) {
            decayable.add(current);
         }

         for (BlockFace face : NEIGHBORS) {
            Block neighbor = current.getRelative(face);
            if (Tag.LOGS.isTagged(neighbor.getType())) {
               connectedToLog = true;
               break;
            }
            if (neighbor.getBlockData() instanceof Leaves && visited.add(neighbor)) {
               queue.add(neighbor);
            }
         }
         if (connectedToLog) {
            break;
         }
      }

      // Если где-то в связной группе нашлось живое бревно - вся группа в безопасности.
      // Если упёрлись в предохранитель max-leaves-scanned - тоже ничего не трогаем,
      // мы не смогли доказать, что бревна рядом действительно нет.
      if (!connectedToLog && !capped) {
         for (Block leaf : decayable) {
            leaf.breakNaturally();
         }
      }
   }
}
