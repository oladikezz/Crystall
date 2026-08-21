package net.schalker.SMPS.modules.adminlist;

import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.module.BaseModule;
import net.schalker.DoAPI.core.module.ModuleInfo;
import net.schalker.SMPS.modules.adminlist.listeners.AdminListListener;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public class AdminListModule extends BaseModule {
   private FileConfiguration config;
   private AdminListWebhook webhook;
   private AdminListListener listener;

   public AdminListModule(DoAPI plugin) {
      super(plugin, new ModuleInfo("SM_AdminList", "1.0.0", "MeXaNoBoP", "Отправка списка админов в Discord webhook"));
   }

   @Override
   public void onEnable() {
      super.onEnable();
      this.loadConfig();

      String webhookUrl = this.config.getString("discord.webhook-url", "");
      this.webhook = new AdminListWebhook(webhookUrl);

      this.listener = new AdminListListener(this.plugin, this);
      this.plugin.getListenerManager().registerListener(this.listener);

      this.plugin.getDebugSystem().log("AdminList", "Модуль AdminList включен");
   }

   @Override
   public void onDisable() {
      super.onDisable();
      if (this.listener != null) {
         this.plugin.getListenerManager().unregisterListener(this.listener);
      }
      this.plugin.getDebugSystem().log("AdminList", "Модуль AdminList выключен");
   }

   @Override
   public void reload() {
      super.reload();
      this.loadConfig();
      if (this.webhook != null) {
         this.webhook.setWebhookUrl(this.config.getString("discord.webhook-url", ""));
      }
   }

   private void loadConfig() {
      this.config = this.plugin.getModuleManager().loadModuleConfig("SM_AdminList");
      if (this.config == null) {
         this.config = new YamlConfiguration();
      }
   }

   public FileConfiguration getConfig() {
      return this.config;
   }

   public AdminListWebhook getWebhook() {
      return this.webhook;
   }

   public String getPermissionNode() {
      return this.config.getString("settings.permission", "smadminlist.set");
   }

   public int getJoinEmbedColor() {
      return this.config.getInt("discord.embed.join-color", 3066993);
   }

   public int getQuitEmbedColor() {
      return this.config.getInt("discord.embed.quit-color", 15158332);
   }

   public String getEmbedTitle() {
      return this.config.getString("discord.embed.title", "Список админов онлайн");
   }

   public String getEmbedFooter() {
      return this.config.getString("discord.embed.footer", "SM_AdminList");
   }


   public String getOnlineFormat() {
      return this.config.getString("discord.embed.online-format", "• {name}");
   }

   public String getEmptyText() {
      return this.config.getString("discord.embed.empty-text", "Сейчас нет админов онлайн.");
   }

   public String getJoinActionText() {
      return this.config.getString("discord.embed.join-action", "Вход");
   }

   public String getQuitActionText() {
      return this.config.getString("discord.embed.quit-action", "Выход");
   }

   public String getActionFieldName() {
      return this.config.getString("discord.embed.action-field-name", "Триггер");
   }

   public String getActionFieldValueFormat() {
      return this.config.getString("discord.embed.action-field-value", "{action}: {player}");
   }

   public String getListFieldName() {
      return this.config.getString("discord.embed.list-field-name", "Админы онлайн");
   }

   public String getImageTitle() {
      return this.config.getString("discord.embed.image-title", "Игроки онлайн ({online}/{max})");
   }

   public String getFontZipPath() {
      return this.config.getString("settings.font.zip-path", "minecraft.zip");
   }

   public String getFontEntryPath() {
      return this.config.getString("settings.font.entry-path", "");
   }
}
