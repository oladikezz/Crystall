package net.schalker.SMPS.modules.help;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.listener.BaseListener;
import net.schalker.DoAPI.core.module.BaseModule;
import net.schalker.DoAPI.core.module.ModuleInfo;
import net.schalker.SMPS.modules.help.format.HelpFormatter;
import net.schalker.SMPS.modules.help.listeners.HelpListener;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

public class HelpModule extends BaseModule {
   private static final String MODULE_NAME = "SM_Help";

   private FileConfiguration config;
   private HelpFormatter formatter;
   private BaseListener listener;

   public HelpModule(DoAPI plugin) {
      super(plugin, loadModuleInfo());
   }

   private static ModuleInfo loadModuleInfo() {
      try (InputStream stream = HelpModule.class.getClassLoader().getResourceAsStream("module.yml")) {
         if (stream != null) {
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(
               new InputStreamReader(stream, StandardCharsets.UTF_8));
            return new ModuleInfo(
               yml.getString("name", MODULE_NAME),
               yml.getString("version", "1.0.0"),
               yml.getString("author", "Unknown"),
               yml.getString("description", "Config-driven /help")
            );
         }
      } catch (Exception ignored) {
      }
      return new ModuleInfo(MODULE_NAME, "1.0.0", "Unknown", "Config-driven /help");
   }

   @Override
   public void onEnable() {
      super.onEnable();
      this.plugin.getModuleManager().saveModuleDefaultConfig(MODULE_NAME);
      loadConfig();
      this.formatter = new HelpFormatter(this.plugin);

      this.listener = new HelpListener(this.plugin, this);
      this.plugin.getListenerManager().registerListener(this.listener);

      this.plugin.getDebugSystem().log("Help", "SM_Help enabled");
   }

   @Override
   public void onDisable() {
      super.onDisable();
      if (this.listener != null) {
         this.plugin.getListenerManager().unregisterListener(this.listener);
      }
      this.plugin.getDebugSystem().log("Help", "SM_Help disabled");
   }

   @Override
   public void reload() {
      super.reload();
      loadConfig();
      if (this.formatter == null) {
         this.formatter = new HelpFormatter(this.plugin);
      }
   }

   public boolean isHelpEnabled() {
      return this.config != null && this.config.getBoolean("commands.help.enabled", true);
   }

   public void sendHelp(CommandSender sender) {
      if (sender == null) {
         return;
      }

      String text = resolvePlaceholders(sender, getConfiguredText());
      if (text.isBlank()) {
         return;
      }

      if (sender instanceof Player player) {
         player.sendMessage(this.formatter.format(text));
         return;
      }

      for (String line : text.split("\\n", -1)) {
         sender.sendMessage(this.formatter.toPlain(line));
      }
   }

   private void loadConfig() {
      this.config = this.plugin.getModuleManager().loadModuleConfig(MODULE_NAME);
      if (this.config == null) {
         this.config = new YamlConfiguration();
      }
   }

   private String getConfiguredText() {
      Object rawText = this.config.get("help.text");
      if (rawText instanceof List<?> lines && !lines.isEmpty()) {
         return lines.stream().map(String::valueOf).collect(Collectors.joining("\n"));
      }

      String directText = this.config.getString("help.text");
      if (directText != null && !directText.isBlank()) {
         return directText.replace("\\n", "\n");
      }

      List<String> lines = this.config.getStringList("help.lines");
      if (!lines.isEmpty()) {
         return String.join("\n", lines);
      }

      return "&[MAIN]Help is not configured.";
   }

   private String resolvePlaceholders(CommandSender sender, String text) {
      String playerName = sender instanceof Player player ? player.getName() : sender.getName();
      return text
         .replace("{player}", playerName)
         .replace("{online}", String.valueOf(this.plugin.getServer().getOnlinePlayers().size()))
         .replace("{max_players}", String.valueOf(this.plugin.getServer().getMaxPlayers()));
   }
}

