package net.schalker.SMPS.modules.trafficoptimizer;

import java.lang.reflect.Method;
import java.util.UUID;
import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.api.IModule;
import org.bukkit.entity.Player;

public class QuietBanBridge {

   public enum Status {
      NOT_INSTALLED,
      CLEAN,
      QUIET_BANNED,
      UNKNOWN
   }

   private static final String MODULE_NAME = "SM_QuietBan";

   private final DoAPI plugin;

   private volatile Class<?> cachedManagerClass;
   private volatile Method cachedFindByPlayer;
   private volatile boolean warned;

   public QuietBanBridge(DoAPI plugin) {
      this.plugin = plugin;
   }

   public Status check(Player player) {
      IModule module = this.plugin.getModuleManager().getModule(MODULE_NAME);
      if (module == null || !module.isEnabled()) {
         return Status.NOT_INSTALLED;
      }

      try {
         Method getManager = module.getClass().getMethod("getManager");
         Object manager = getManager.invoke(module);
         if (manager == null) {
            return Status.UNKNOWN;
         }

         Method findByPlayer = resolveFindByPlayer(manager.getClass());
         Object entry = findByPlayer.invoke(manager, player.getUniqueId(), player.getName());
         return entry == null ? Status.CLEAN : Status.QUIET_BANNED;
      } catch (Throwable throwable) {
         if (!this.warned) {
            this.warned = true;
            this.plugin.getDebugSystem().logWarning("TrafficOptimizer",
               "Не удалось опросить SM_QuietBan, оптимизация для тихо забаненных отключена: "
                  + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
         }
         return Status.UNKNOWN;
      }
   }

   public void invalidate() {
      this.cachedManagerClass = null;
      this.cachedFindByPlayer = null;
      this.warned = false;
   }

   private Method resolveFindByPlayer(Class<?> managerClass) throws NoSuchMethodException {
      Method cached = this.cachedFindByPlayer;
      if (cached != null && this.cachedManagerClass == managerClass) {
         return cached;
      }
      Method resolved = managerClass.getMethod("findByPlayer", UUID.class, String.class);
      this.cachedManagerClass = managerClass;
      this.cachedFindByPlayer = resolved;
      return resolved;
   }
}
