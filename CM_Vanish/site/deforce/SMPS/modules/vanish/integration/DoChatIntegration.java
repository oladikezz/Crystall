package site.deforce.SMPS.modules.vanish.integration;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredListener;
import site.deforce.SMPS.modules.vanish.SM_Vanish;

public class DoChatIntegration implements Listener {
   private final SM_Vanish module;
   private final AtomicBoolean injected = new AtomicBoolean(false);

   public DoChatIntegration(SM_Vanish module) {
      super();
      this.module = module;
      Plugin doChatPlugin = Bukkit.getPluginManager().getPlugin("DoChat");
      if (doChatPlugin != null && doChatPlugin.isEnabled()) {
         module.log("DoChat detected (already enabled). Scheduling interceptor injection...");
         module.scheduleDelayedTask(() -> this.tryInject("immediate-delayed"), 1L);
      } else {
         module.log("DoChat not yet enabled. Waiting for PluginEnableEvent...");
      }

      Bukkit.getPluginManager().registerEvents(this, module.getSMPS());
   }

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onPluginEnable(PluginEnableEvent event) {
      if (event.getPlugin().getName().equals("DoChat")) {
         if (!this.injected.get()) {
            this.module.log("DoChat just enabled. Scheduling interceptor injection...");
            this.module.scheduleDelayedTask(() -> this.tryInject("plugin-enable-delayed"), 1L);
         }
      }
   }

   private void tryInject(String source) {
      if (!this.injected.get()) {
         int joinCount = this.interceptEvent(PlayerJoinEvent.class);
         int quitCount = this.interceptEvent(PlayerQuitEvent.class);
         if (joinCount <= 0 && quitCount <= 0) {
            this.module.log("DoChat listeners not found yet (source=" + source + "). Will retry...");
            this.module.scheduleDelayedTask(() -> this.retryInject(source), 20L);
         } else {
            this.injected.set(true);
            this.module.log("DoChat interceptors active (source=" + source + ", join=" + joinCount + ", quit=" + quitCount + ")");
         }

      }
   }

   private void retryInject(String source) {
      if (!this.injected.get()) {
         int joinCount = this.interceptEvent(PlayerJoinEvent.class);
         int quitCount = this.interceptEvent(PlayerQuitEvent.class);
         if (joinCount <= 0 && quitCount <= 0) {
            this.module.log("WARNING: Could not find DoChat ChatListener to intercept! Join/quit messages for vanished players may leak.");
         } else {
            this.injected.set(true);
            this.module.log("DoChat interceptors active on retry (source=" + source + ", join=" + joinCount + ", quit=" + quitCount + ")");
         }

      }
   }

   private int interceptEvent(Class<? extends Event> eventClass) {
      int intercepted = 0;

      try {
         Method getHandlerList = eventClass.getMethod("getHandlerList");
         HandlerList handlerList = (HandlerList)getHandlerList.invoke((Object)null);
         List<RegisteredListener> toReplace = new ArrayList();

         for(RegisteredListener listener : handlerList.getRegisteredListeners()) {
            String pluginName = listener.getPlugin().getName();
            String listenerClass = listener.getListener().getClass().getName();
            boolean isDoChat = pluginName.equals("DoChat") || listenerClass.startsWith("org.doit.dochat.");
            if (isDoChat) {
               toReplace.add(listener);
            }
         }

         for(RegisteredListener original : toReplace) {
            handlerList.unregister(original);
            EventExecutor proxyExecutor = (listenerx, event) -> {
               if (event instanceof PlayerJoinEvent joinEvent) {
                  if (this.module.shouldSuppressPresenceMessages(joinEvent.getPlayer())) {
                     joinEvent.joinMessage((Component)null);
                     this.module.log("Suppressed DoChat join message for hidden player state: " + joinEvent.getPlayer().getName());
                     return;
                  }
               } else if (event instanceof PlayerQuitEvent quitEvent) {
                  if (this.module.shouldSuppressPresenceMessages(quitEvent.getPlayer())) {
                     quitEvent.quitMessage((Component)null);
                     this.module.log("Suppressed DoChat quit message for hidden player state: " + quitEvent.getPlayer().getName());
                     return;
                  }
               }

               try {
                  original.callEvent(event);
               } catch (EventException e) {
                  this.module.log("Error passing event to DoChat listener: " + e.getMessage());
               }

            };
            RegisteredListener proxyListener = new RegisteredListener(original.getListener(), proxyExecutor, original.getPriority(), this.module.getSMPS(), original.isIgnoringCancelled());
            handlerList.register(proxyListener);
            ++intercepted;
            SM_Vanish var18 = this.module;
            String var19 = original.getListener().getClass().getSimpleName();
            var18.log("Intercepted DoChat " + var19 + " (" + String.valueOf(original.getPriority()) + ") for " + eventClass.getSimpleName());
         }
      } catch (Exception e) {
         SM_Vanish var10000 = this.module;
         String var10001 = eventClass.getSimpleName();
         var10000.log("Failed to inject DoChat interceptor for " + var10001 + ": " + e.getMessage());
      }

      return intercepted;
   }

   public void shutdown() {
      HandlerList.unregisterAll(this);
   }
}
