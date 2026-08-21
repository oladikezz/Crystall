package net.schalker.SMPS.modules.fastleaves;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.module.BaseModule;
import net.schalker.DoAPI.core.module.ModuleInfo;
import net.schalker.SMPS.modules.fastleaves.listeners.LeafDecayListener;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Когда рядом с листвой пропадает бревно, модуль обходит листву блок за блоком -
 * только через соприкасающиеся друг с другом блоки листвы - и проверяет, есть ли
 * где-то в этой связной группе живое бревно. Если нет ни одного - вся группа опадает разом.
 * Листва, поставленная игроком вручную (persistent = true), никогда сама не опадает,
 * но участвует в проверке связности для остальной листвы - так же, как в ванили.
 */
public class FastLeavesModule extends BaseModule {
   private FileConfiguration config;
   private LeafDecayListener listener;

   public FastLeavesModule(DoAPI plugin) {
      super(plugin, loadModuleInfo());
   }

   private static ModuleInfo loadModuleInfo() {
      try (InputStream stream = FastLeavesModule.class.getClassLoader().getResourceAsStream("module.yml")) {
         if (stream != null) {
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(
               new InputStreamReader(stream, StandardCharsets.UTF_8));
            return new ModuleInfo(
               yml.getString("name", "SM_FastLeaves"),
               yml.getString("version", "1.0.0"),
               yml.getString("author", "Unknown"),
               yml.getString("description", "Быстрое опадание листвы без дерева рядом")
            );
         }
      } catch (Exception ignored) {}
      return new ModuleInfo("SM_FastLeaves", "1.0.0", "Unknown", "Быстрое опадание листвы без дерева рядом");
   }

   @Override
   public void onEnable() {
      super.onEnable();
      this.loadConfigs();

      this.listener = new LeafDecayListener(this.plugin, this);
      this.plugin.getListenerManager().registerListener(this.listener);

      this.plugin.getDebugSystem().log("FastLeaves", "Модуль FastLeaves включен");
   }

   @Override
   public void onDisable() {
      super.onDisable();
      if (this.listener != null) {
         this.plugin.getListenerManager().unregisterListener(this.listener);
      }
      this.plugin.getDebugSystem().log("FastLeaves", "Модуль FastLeaves выключен");
   }

   @Override
   public void reload() {
      super.reload();
      this.loadConfigs();
   }

   private void loadConfigs() {
      this.config = this.plugin.getModuleManager().loadModuleConfig("SM_FastLeaves");
      if (this.config == null) {
         this.config = new YamlConfiguration();
      }
   }

   public boolean isFeatureEnabled() {
      return this.config.getBoolean("enabled", true);
   }

   public long getCheckDelayTicks() {
      return Math.max(1L, this.config.getLong("check-delay-ticks", 3));
   }

   public int getMaxLeavesScanned() {
      return Math.max(1, this.config.getInt("max-leaves-scanned", 512));
   }
}
