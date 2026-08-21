package site.deforce.SMPS.modules.vanish.integration;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import site.deforce.SMPS.modules.vanish.SM_Vanish;

public class TabIntegration {
   private final SM_Vanish plugin;
   private boolean available = false;
   private ClassLoader tabCL;
   private Class<?> tabAPIClass;
   private Class<?> tabPlayerClass;
   private Class<?> nameTagManagerClass;
   private Class<?> sortingManagerClass;
   private Class<?> tabListFormatManagerClass;
   private Class<?> eventBusClass;
   private Class<?> playerLoadEventClass;
   private Class<?> eventHandlerClass;
   private Method getInstanceMethod;
   private Method getPlayerMethod;
   private Method getOnlinePlayersMethod;
   private Method getNameTagManagerMethod;
   private Method getSortingManagerMethod;
   private Method getTabListFormatManagerMethod;
   private Method getEventBusMethod;
   private Method hideNameTagMethod;
   private Method showNameTagMethod;
   private Method forceTeamNameMethod;
   private Method setPrefixMethod;
   private Method setSuffixMethod;
   private Method setNameMethod;
   private Method registerEventMethod;
   private Method unregisterEventMethod;
   private Method eventGetPlayerMethod;
   private Method tabPlayerGetPlayerMethod;
   private Object eventHandlerProxy;

   public TabIntegration(SM_Vanish plugin) {
      super();
      this.plugin = plugin;
      this.setup();
   }

   private void setup() {
      Plugin tabPlugin = Bukkit.getPluginManager().getPlugin("TAB");
      if (tabPlugin != null && tabPlugin.isEnabled()) {
         this.tabCL = tabPlugin.getClass().getClassLoader();

         try {
            this.tabAPIClass = this.cls("me.neznamy.tab.api.TabAPI");
            this.tabPlayerClass = this.cls("me.neznamy.tab.api.TabPlayer");
            this.nameTagManagerClass = this.cls("me.neznamy.tab.api.nametag.NameTagManager");
            this.sortingManagerClass = this.cls("me.neznamy.tab.api.tablist.SortingManager");
            this.tabListFormatManagerClass = this.cls("me.neznamy.tab.api.tablist.TabListFormatManager");
            this.eventBusClass = this.cls("me.neznamy.tab.api.event.EventBus");
            this.playerLoadEventClass = this.cls("me.neznamy.tab.api.event.player.PlayerLoadEvent");
            this.eventHandlerClass = this.cls("me.neznamy.tab.api.event.EventHandler");
            this.getInstanceMethod = this.tabAPIClass.getMethod("getInstance");
            this.getPlayerMethod = this.tabAPIClass.getMethod("getPlayer", UUID.class);
            this.getOnlinePlayersMethod = this.tabAPIClass.getMethod("getOnlinePlayers");
            this.getNameTagManagerMethod = this.tabAPIClass.getMethod("getNameTagManager");
            this.getSortingManagerMethod = this.tabAPIClass.getMethod("getSortingManager");
            this.getTabListFormatManagerMethod = this.tabAPIClass.getMethod("getTabListFormatManager");
            this.getEventBusMethod = this.tabAPIClass.getMethod("getEventBus");
            this.hideNameTagMethod = this.nameTagManagerClass.getMethod("hideNameTag", this.tabPlayerClass, this.tabPlayerClass);
            this.showNameTagMethod = this.nameTagManagerClass.getMethod("showNameTag", this.tabPlayerClass, this.tabPlayerClass);
            this.forceTeamNameMethod = this.sortingManagerClass.getMethod("forceTeamName", this.tabPlayerClass, String.class);
            this.setPrefixMethod = this.tabListFormatManagerClass.getMethod("setPrefix", this.tabPlayerClass, String.class);
            this.setSuffixMethod = this.tabListFormatManagerClass.getMethod("setSuffix", this.tabPlayerClass, String.class);

            try {
               this.setNameMethod = this.tabListFormatManagerClass.getMethod("setName", this.tabPlayerClass, String.class);
            } catch (NoSuchMethodException var3) {
               this.setNameMethod = null;
            }

            this.registerEventMethod = this.eventBusClass.getMethod("register", Class.class, this.eventHandlerClass);
            this.unregisterEventMethod = this.eventBusClass.getMethod("unregister", this.eventHandlerClass);
            this.eventGetPlayerMethod = this.playerLoadEventClass.getMethod("getPlayer");
            this.tabPlayerGetPlayerMethod = this.tabPlayerClass.getMethod("getPlayer");
            Object api = this.getInstanceMethod.invoke((Object)null);
            if (api == null) {
               this.plugin.log("TAB API instance is null");
               return;
            }

            this.registerPlayerLoadHandler(api);
            this.available = true;
            this.plugin.log("TAB 5.x integration enabled (reflection)");
         } catch (Throwable e) {
            SM_Vanish var10000 = this.plugin;
            String var10001 = e.getClass().getSimpleName();
            var10000.log("Failed to initialize TAB integration: " + var10001 + " - " + e.getMessage());
            this.available = false;
         }

      }
   }

   private void registerPlayerLoadHandler(Object api) throws Exception {
      Object eventBus = this.getEventBusMethod.invoke(api);
      if (eventBus == null) {
         this.plugin.log("[TAB] EventBus is null");
      } else {
         this.eventHandlerProxy = Proxy.newProxyInstance(this.tabCL, new Class[]{this.eventHandlerClass}, (proxy, method, args) -> {
            if ("accept".equals(method.getName()) && args != null && args.length == 1) {
               this.handlePlayerLoadEvent(args[0]);
            }

            return null;
         });
         this.registerEventMethod.invoke(eventBus, this.playerLoadEventClass, this.eventHandlerProxy);
         this.plugin.log("[TAB] PlayerLoadEvent handler registered");
      }
   }

   private void handlePlayerLoadEvent(Object event) {
      try {
         Object tabPlayer = this.eventGetPlayerMethod.invoke(event);
         if (tabPlayer == null) {
            return;
         }

         Object platformPlayer = this.tabPlayerGetPlayerMethod.invoke(tabPlayer);
         if (!(platformPlayer instanceof Player)) {
            return;
         }

         Player player = (Player)platformPlayer;
         if (!player.isOnline()) {
            return;
         }

         this.applyNameTagVisibilityForPlayer(player);
      } catch (Exception e) {
         this.plugin.log("[TAB] Error in PlayerLoadEvent handler: " + e.getMessage());
      }

   }

   public void shutdown() {
      if (this.eventHandlerProxy != null) {
         try {
            Object api = this.getInstanceMethod.invoke((Object)null);
            if (api != null) {
               Object eventBus = this.getEventBusMethod.invoke(api);
               if (eventBus != null) {
                  this.unregisterEventMethod.invoke(eventBus, this.eventHandlerProxy);
               }
            }
         } catch (Exception var3) {
         }

         this.eventHandlerProxy = null;
      }

      this.available = false;
   }

   public boolean isAvailable() {
      return this.available;
   }

   private Class<?> cls(String name) throws ClassNotFoundException {
      return Class.forName(name, true, this.tabCL);
   }

   private Object getApi() throws Exception {
      return this.getInstanceMethod.invoke((Object)null);
   }

   private Object getTabPlayer(UUID uuid) throws Exception {
      Object api = this.getApi();
      return api == null ? null : this.getPlayerMethod.invoke(api, uuid);
   }

   private Object getTabPlayer(Player player) throws Exception {
      return this.getTabPlayer(player.getUniqueId());
   }

   private Object[] getOnlineTabPlayers() throws Exception {
      Object api = this.getApi();
      if (api == null) {
         return new Object[0];
      } else {
         Object result = this.getOnlinePlayersMethod.invoke(api);
         return result instanceof Object[] ? (Object[])result : new Object[0];
      }
   }

   private Object getNameTagMgr() throws Exception {
      Object api = this.getApi();
      return api == null ? null : this.getNameTagManagerMethod.invoke(api);
   }

   public void hidePlayer(Player player) {
   }

   public void showPlayer(Player player) {
   }

   private void applyNameTagVisibilityForPlayer(Player target) {
      try {
         Object nameTagMgr = this.getNameTagMgr();
         if (nameTagMgr == null) {
            this.plugin.log("[TAB-Debug] applyNameTagVisibility: NameTagManager is NULL");
            return;
         }

         Object targetTab = this.getTabPlayer(target);
         if (targetTab == null) {
            return;
         }

         boolean targetHidden = this.plugin.isDebugNameTagHidden(target) || this.plugin.isIncognito(target) || this.plugin.isVanished(target);

         for(Player online : Bukkit.getOnlinePlayers()) {
            if (!target.equals(online)) {
               Object onlineTab = this.getTabPlayer(online);
               if (onlineTab != null) {
                  boolean onlineHidden = this.plugin.isDebugNameTagHidden(online) || this.plugin.isIncognito(online) || this.plugin.isVanished(online);
                  if (targetHidden) {
                     this.hideNameTagMethod.invoke(nameTagMgr, targetTab, onlineTab);
                  } else {
                     this.showNameTagMethod.invoke(nameTagMgr, targetTab, onlineTab);
                  }

                  if (onlineHidden) {
                     this.hideNameTagMethod.invoke(nameTagMgr, onlineTab, targetTab);
                  } else {
                     this.showNameTagMethod.invoke(nameTagMgr, onlineTab, targetTab);
                  }
               }
            }
         }

         if (!this.plugin.isTabVisible(target)) {
            this.hideFromTab(target);
         }

         SM_Vanish var10 = this.plugin;
         String var11 = target.getName();
         var10.log("[TAB-Debug] applyNameTagVisibility for " + var11 + " hidden=" + targetHidden);
      } catch (Exception e) {
         SM_Vanish var10000 = this.plugin;
         String var10001 = target.getName();
         var10000.log("Error applying nametag visibility for " + var10001 + ": " + e.getMessage());
      }

   }

   public void hideNameTag(Player target) {
      if (!this.available) {
         this.plugin.log("[TAB-Debug] hideNameTag: not available");
      } else {
         try {
            Object nameTagMgr = this.getNameTagMgr();
            if (nameTagMgr == null) {
               this.plugin.log("[TAB-Debug] hideNameTag: NameTagManager is NULL — is nametag feature enabled in TAB config?");
               return;
            }

            Object tabTarget = this.getTabPlayer(target);
            if (tabTarget == null) {
               this.plugin.log("[TAB-Debug] hideNameTag: TabPlayer is null for " + target.getName());
               return;
            }

            int count = 0;

            for(Object viewer : this.getOnlineTabPlayers()) {
               if (viewer != null && !viewer.equals(tabTarget)) {
                  this.hideNameTagMethod.invoke(nameTagMgr, tabTarget, viewer);
                  ++count;
               }
            }

            SM_Vanish var10 = this.plugin;
            String var11 = target.getName();
            var10.log("[TAB-Debug] showNameTag: showed " + var11 + "'s nametag to " + count + " viewers");
         } catch (Exception e) {
            SM_Vanish var10000 = this.plugin;
            String var10001 = target.getName();
            var10000.log("Error showing TAB nametag for " + var10001 + ": " + e.getMessage());
         }

      }
   }

   public void hideFromTab(Player player) {
      if (this.available) {
         try {
            Object api = this.getApi();
            if (api == null) {
               return;
            }

            Object tabPlayer = this.getTabPlayer(player);
            if (tabPlayer == null) {
               return;
            }

            Object formatMgr = this.getTabListFormatManagerMethod.invoke(api);
            if (formatMgr != null) {
               this.setPrefixMethod.invoke(formatMgr, tabPlayer, "");
               this.setSuffixMethod.invoke(formatMgr, tabPlayer, "");
               if (this.setNameMethod != null) {
                  this.setNameMethod.invoke(formatMgr, tabPlayer, " ");
               }
            }

            this.plugin.log("[TAB-Debug] hideFromTab: set empty format for " + player.getName());
         } catch (Exception e) {
            SM_Vanish var10000 = this.plugin;
            String var10001 = player.getName();
            var10000.log("Error hiding " + var10001 + " from tab via TAB API: " + e.getMessage());
         }

      }
   }

   public void restoreInTab(Player player) {
      if (this.available) {
         try {
            Object api = this.getApi();
            if (api == null) {
               return;
            }

            Object tabPlayer = this.getTabPlayer(player);
            if (tabPlayer == null) {
               return;
            }

            Object formatMgr = this.getTabListFormatManagerMethod.invoke(api);
            if (formatMgr != null) {
               this.setPrefixMethod.invoke(formatMgr, tabPlayer, (String)null);
               this.setSuffixMethod.invoke(formatMgr, tabPlayer, (String)null);
               if (this.setNameMethod != null) {
                  this.setNameMethod.invoke(formatMgr, tabPlayer, (String)null);
               }
            }

            this.plugin.log("[TAB-Debug] restoreInTab: reset format for " + player.getName());
         } catch (Exception e) {
            SM_Vanish var10000 = this.plugin;
            String var10001 = player.getName();
            var10000.log("Error restoring " + var10001 + " in tab via TAB API: " + e.getMessage());
         }

      }
   }
}
