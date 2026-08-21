package site.deforce.SMPS.modules.vanish.integration;

import java.lang.reflect.Method;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import site.deforce.SMPS.modules.vanish.SM_Vanish;

public class SquaremapIntegration {
   private final SM_Vanish module;
   private boolean available = false;
   private Plugin squaremapPlugin = null;
   private Object playerManagerInstance;
   private Method hideMethod;
   private Method showMethod;
   private Method hiddenMethod;
   private static final String[] PLUGIN_NAMES = new String[]{"squaremap", "Squaremap", "SquareMap"};
   private static final String[] PROVIDER_CLASSES = new String[]{"xyz.jpenilla.squaremap.api.SquaremapProvider", "xyz.jpenilla.squaremap.api.Squaremap"};

   public SquaremapIntegration(SM_Vanish module) {
      super();
      this.module = module;
      this.setupReflection();
   }

   private void setupReflection() {
      for(String name : PLUGIN_NAMES) {
         this.squaremapPlugin = Bukkit.getPluginManager().getPlugin(name);
         if (this.squaremapPlugin != null) {
            this.module.log("Found Squaremap plugin as '" + name + "' (version: " + this.squaremapPlugin.getDescription().getVersion() + ")");
            break;
         }
      }

      if (this.squaremapPlugin != null && this.squaremapPlugin.isEnabled()) {
         ClassLoader sqClassLoader = this.squaremapPlugin.getClass().getClassLoader();
         this.module.log("Squaremap classloader: " + sqClassLoader.getClass().getName());
         if (this.tryApiApproach(sqClassLoader)) {
            this.available = true;
            this.module.log("Squaremap integration enabled via API!");
         } else {
            this.module.log("Squaremap API not accessible via reflection. Metadata-based hiding may still work if 'hide.vanished: true' is set in Squaremap's config.");
            this.available = true;
         }
      } else {
         this.module.log("Squaremap not found, integration disabled.");
      }
   }

   private boolean tryApiApproach(ClassLoader classLoader) {
      for(String providerClassName : PROVIDER_CLASSES) {
         try {
            Class<?> providerClass = Class.forName(providerClassName, true, classLoader);
            this.module.log("Loaded provider class: " + providerClassName);
            Object squaremapInstance = this.tryStaticMethod(providerClass, "get");
            if (squaremapInstance == null) {
               this.module.log("Provider " + providerClassName + ".get() returned null.");
            } else {
               this.module.log("Got Squaremap API instance (type: " + squaremapInstance.getClass().getName() + ")");
               Object pm = this.tryMethod(squaremapInstance, "playerManager");
               if (pm == null) {
                  pm = this.tryMethod(squaremapInstance, "getPlayerManager");
               }

               if (pm == null) {
                  this.logAvailableMethods("Squaremap API instance", squaremapInstance);
                  this.module.log("Could not find playerManager on Squaremap API instance.");
               } else {
                  this.playerManagerInstance = pm;
                  this.module.log("Got PlayerManager (type: " + pm.getClass().getName() + ")");
                  this.hideMethod = this.findMethod(pm, "hide", UUID.class);
                  this.showMethod = this.findMethod(pm, "show", UUID.class);
                  this.hiddenMethod = this.findMethod(pm, "hidden", UUID.class);
                  if (this.hiddenMethod == null) {
                     this.hiddenMethod = this.findMethod(pm, "isHidden", UUID.class);
                  }

                  if (this.hideMethod != null && this.showMethod != null) {
                     SM_Vanish var10000 = this.module;
                     String var10001 = this.hideMethod.getName();
                     var10000.log("Squaremap PlayerManager methods resolved: hide=" + var10001 + ", show=" + this.showMethod.getName() + ", hidden=" + (this.hiddenMethod != null ? this.hiddenMethod.getName() : "N/A"));
                     return true;
                  }

                  this.logAvailableMethods("PlayerManager", pm);
                  this.module.log("Could not find hide/show methods on PlayerManager.");
               }
            }
         } catch (ClassNotFoundException var9) {
            this.module.log("Squaremap provider class not found via plugin classloader: " + providerClassName);
         } catch (Exception e) {
            this.module.log("Error trying provider " + providerClassName + ": " + e.getClass().getSimpleName() + " - " + e.getMessage());
         }
      }

      return false;
   }

   private Object tryStaticMethod(Class<?> clazz, String methodName) {
      try {
         Method m = clazz.getMethod(methodName);
         return m.invoke((Object)null);
      } catch (Exception e) {
         SM_Vanish var10000 = this.module;
         String var10001 = clazz.getSimpleName();
         var10000.log("  tryStaticMethod(" + var10001 + "." + methodName + ") failed: " + e.getClass().getSimpleName() + " - " + e.getMessage());
         return null;
      }
   }

   private Object tryMethod(Object instance, String methodName) {
      try {
         Method m = instance.getClass().getMethod(methodName);
         return m.invoke(instance);
      } catch (Exception var4) {
         return null;
      }
   }

   private Method findMethod(Object instance, String methodName, Class<?>... paramTypes) {
      try {
         return instance.getClass().getMethod(methodName, paramTypes);
      } catch (NoSuchMethodException var5) {
         return null;
      }
   }

   private void logAvailableMethods(String label, Object instance) {
      try {
         StringBuilder sb = new StringBuilder(label + " available methods: ");

         for(Method m : instance.getClass().getMethods()) {
            if (m.getDeclaringClass() != Object.class) {
               sb.append(m.getName()).append("(");
               Class<?>[] params = m.getParameterTypes();

               for(int i = 0; i < params.length; ++i) {
                  if (i > 0) {
                     sb.append(", ");
                  }

                  sb.append(params[i].getSimpleName());
               }

               sb.append("), ");
            }
         }

         this.module.log(sb.toString());
      } catch (Exception var10) {
      }

   }

   public void hidePlayer(Player player) {
      if (this.available) {
         if (this.playerManagerInstance != null && this.hideMethod != null) {
            try {
               if (this.hiddenMethod != null) {
                  boolean alreadyHidden = (Boolean)this.hiddenMethod.invoke(this.playerManagerInstance, player.getUniqueId());
                  if (alreadyHidden) {
                     this.module.log(player.getName() + " already hidden on Squaremap.");
                     return;
                  }
               }

               this.hideMethod.invoke(this.playerManagerInstance, player.getUniqueId());
               this.module.log("Hid " + player.getName() + " from Squaremap via API.");
            } catch (Exception e) {
               SM_Vanish var10000 = this.module;
               String var10001 = e.getClass().getSimpleName();
               var10000.log("Failed to hide player on Squaremap via API: " + var10001 + " - " + e.getMessage());
            }
         } else {
            this.module.log(player.getName() + " hidden on Squaremap via metadata (vanished key).");
         }

      }
   }

   public void showPlayer(Player player) {
      if (this.available) {
         if (this.playerManagerInstance != null && this.showMethod != null) {
            try {
               if (this.hiddenMethod != null) {
                  boolean isHidden = (Boolean)this.hiddenMethod.invoke(this.playerManagerInstance, player.getUniqueId());
                  if (!isHidden) {
                     this.module.log(player.getName() + " already visible on Squaremap.");
                     return;
                  }
               }

               this.showMethod.invoke(this.playerManagerInstance, player.getUniqueId());
               this.module.log("Showed " + player.getName() + " on Squaremap via API.");
            } catch (Exception e) {
               SM_Vanish var10000 = this.module;
               String var10001 = e.getClass().getSimpleName();
               var10000.log("Failed to show player on Squaremap via API: " + var10001 + " - " + e.getMessage());
            }
         } else {
            this.module.log(player.getName() + " shown on Squaremap via metadata (vanished key removed).");
         }

      }
   }

   public boolean isAvailable() {
      return this.available;
   }
}
