package net.schalker.SMPS.modules.invsee;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.schalker.DoAPI.DoAPI;
import net.schalker.SMPS.modules.invsee.nbt.NbtIo;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;

final class PlayerDataInventoryStore {
   private static final String INVENTORY_KEY = "Inventory";
   private static final String ENDER_KEY = "EnderItems";
   private static final String SLOT_KEY = "Slot";
   private static final String DATA_VERSION_KEY = "DataVersion";

   private final DoAPI plugin;

   PlayerDataInventoryStore(DoAPI plugin) {
      this.plugin = plugin;
   }

   Path getPlayerDataFile(UUID playerId) {
      return getPlayerDataDirectory().resolve(playerId + ".dat");
   }

   boolean hasPlayerData(UUID playerId) {
      return Files.isRegularFile(getPlayerDataFile(playerId));
   }

   InventorySnapshot loadInventory(UUID playerId) throws IOException {
      NbtIo.Compound root = readPlayerData(playerId);
      ItemStack[] items = readItemList(root, INVENTORY_KEY, InventorySnapshot.MAIN_SIZE);
      InventorySnapshot snapshot = new InventorySnapshot();
      for (int i = 0; i < InventorySnapshot.MAIN_SIZE; i++) {
         snapshot.main[i] = cloneItem(items[i]);
      }
      return snapshot;
   }

   ItemStack[] loadEnderChest(UUID playerId) throws IOException {
      NbtIo.Compound root = readPlayerData(playerId);
      return readItemList(root, ENDER_KEY, InvseeModule.ENDER_SIZE);
   }

   void saveInventory(UUID playerId, InventorySnapshot snapshot) throws IOException {
      NbtIo.Compound root = readPlayerData(playerId);
      writeItemList(root, INVENTORY_KEY, snapshot.main, InventorySnapshot.MAIN_SIZE);
      writePlayerData(playerId, root);
   }

   void saveEnderChest(UUID playerId, ItemStack[] contents) throws IOException {
      NbtIo.Compound root = readPlayerData(playerId);
      writeItemList(root, ENDER_KEY, contents, InvseeModule.ENDER_SIZE);
      writePlayerData(playerId, root);
   }

   private Path getPlayerDataDirectory() {
      World normalWorld = null;
      for (World world : Bukkit.getWorlds()) {
         if (world.getEnvironment() == World.Environment.NORMAL) {
            normalWorld = world;
            break;
         }
      }
      if (normalWorld == null && !Bukkit.getWorlds().isEmpty()) {
         normalWorld = Bukkit.getWorlds().get(0);
      }
      if (normalWorld != null) {
         return normalWorld.getWorldFolder().toPath().resolve("playerdata");
      }
      return Bukkit.getWorldContainer().toPath()
         .resolve(Bukkit.getUnsafe().getMainLevelName())
         .resolve("playerdata");
   }

   private NbtIo.Compound readPlayerData(UUID playerId) throws IOException {
      Path file = getPlayerDataFile(playerId);
      if (!Files.isRegularFile(file)) {
         throw new NoSuchFileException(file.toString());
      }
      return NbtIo.readCompressed(file);
   }

   private void writePlayerData(UUID playerId, NbtIo.Compound root) throws IOException {
      root.put(DATA_VERSION_KEY, new NbtIo.IntTag(Bukkit.getUnsafe().getDataVersion()));

      Path file = getPlayerDataFile(playerId);
      Files.createDirectories(file.getParent());
      Path temp = file.resolveSibling(file.getFileName() + ".smps.tmp");
      NbtIo.writeCompressed(temp, root);
      try {
         Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException ignored) {
         Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
      }
   }

   private ItemStack[] readItemList(NbtIo.Compound root, String key, int size) {
      ItemStack[] contents = new ItemStack[size];
      int dataVersion = root.getInt(DATA_VERSION_KEY, Bukkit.getUnsafe().getDataVersion());
      NbtIo.ListTag list = root.getList(key);
      if (list == null || list.elementType() != NbtIo.TAG_COMPOUND) {
         return contents;
      }

      for (NbtIo.Tag tag : list.value()) {
         if (!(tag instanceof NbtIo.Compound compound)) {
            continue;
         }
         int slot = readSlot(compound);
         if (slot < 0 || slot >= size) {
            continue;
         }
         try {
            contents[slot] = compoundToItem(compound, dataVersion);
         } catch (Exception ex) {
            this.plugin.getDebugSystem().log("Invsee", "Cannot read offline item in slot " + slot + ": " + ex.getMessage());
         }
      }
      return contents;
   }

   private void writeItemList(NbtIo.Compound root, String key, ItemStack[] contents, int size) throws IOException {
      NbtIo.ListTag existing = root.getList(key);
      List<NbtIo.Tag> updated = new ArrayList<>();
      if (existing != null && existing.elementType() == NbtIo.TAG_COMPOUND) {
         for (NbtIo.Tag tag : existing.value()) {
            if (!(tag instanceof NbtIo.Compound compound)) {
               continue;
            }
            int slot = readSlot(compound);
            if (slot >= 0 && slot < size) {
               continue;
            }
            updated.add(compound.copy());
         }
      }

      for (int slot = 0; slot < size; slot++) {
         ItemStack item = slot < contents.length ? contents[slot] : null;
         if (isEmpty(item)) {
            continue;
         }
         updated.add(itemToCompound(item, slot));
      }
      root.put(key, new NbtIo.ListTag(NbtIo.TAG_COMPOUND, updated));
   }

   private ItemStack compoundToItem(NbtIo.Compound source, int dataVersion) throws IOException {
      NbtIo.Compound item = source.copy();
      item.remove(SLOT_KEY);
      if (!item.contains(DATA_VERSION_KEY)) {
         item.put(DATA_VERSION_KEY, new NbtIo.IntTag(dataVersion));
      }
      ItemStack stack = ItemStack.deserializeBytes(NbtIo.write(item));
      return isEmpty(stack) ? null : stack;
   }

   private NbtIo.Compound itemToCompound(ItemStack source, int slot) throws IOException {
      NbtIo.Compound item = NbtIo.read(source.serializeAsBytes());
      item.remove(DATA_VERSION_KEY);
      item.put(SLOT_KEY, new NbtIo.ByteTag((byte) slot));
      return item;
   }

   private int readSlot(NbtIo.Compound compound) {
      NbtIo.Tag tag = compound.get(SLOT_KEY);
      if (tag instanceof NbtIo.ByteTag byteTag) {
         return byteTag.value();
      }
      if (tag instanceof NbtIo.ShortTag shortTag) {
         return shortTag.value();
      }
      if (tag instanceof NbtIo.IntTag intTag) {
         return intTag.value();
      }
      return -1;
   }

   private static boolean isEmpty(ItemStack item) {
      return item == null || item.getType() == Material.AIR || item.getAmount() <= 0 || item.isEmpty();
   }

   private static ItemStack cloneItem(ItemStack item) {
      return item == null ? null : item.clone();
   }
}
