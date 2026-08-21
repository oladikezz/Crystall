package net.schalker.SMPS.modules.itemdespawn;

import java.util.EnumSet;
import java.util.Set;
import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.module.BaseModule;
import net.schalker.DoAPI.core.module.ModuleInfo;
import net.schalker.SMPS.modules.itemdespawn.listeners.ItemDespawnListener;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public class ItemDespawnModule extends BaseModule {
    private static final String MODULE_NAME = "SM_ItemDespawn";
    private static final long TICKS_PER_SECOND = 20L;

    private FileConfiguration config;
    private ItemDespawnListener listener;
    private Set<Material> trackedMaterials = EnumSet.noneOf(Material.class);

    public ItemDespawnModule(DoAPI plugin) {
        super(plugin, new ModuleInfo(MODULE_NAME, "1.0.0", "MeXaNoBoP",
                "Ускоряет исчезновение выбранных предметов на земле"));
    }

    @Override
    public void onEnable() {
        super.onEnable();
        this.loadConfigs();

        this.listener = new ItemDespawnListener(this.plugin, this);
        this.plugin.getListenerManager().registerListener(this.listener);

        this.plugin.getDebugSystem().log(MODULE_NAME, "Модуль включен");
    }

    @Override
    public void onDisable() {
        super.onDisable();
        if (this.listener != null) {
            this.plugin.getListenerManager().unregisterListener(this.listener);
            this.listener.cleanup();
            this.listener = null;
        }
        this.plugin.getDebugSystem().log(MODULE_NAME, "Модуль выключен");
    }

    @Override
    public void reload() {
        super.reload();
        this.loadConfigs();
    }

    private void loadConfigs() {
        this.config = this.plugin.getModuleManager().loadModuleConfig(MODULE_NAME);
        if (this.config == null) {
            this.config = new YamlConfiguration();
        }
        this.trackedMaterials = parseTrackedMaterials();
    }

    private Set<Material> parseTrackedMaterials() {
        Set<Material> materials = EnumSet.noneOf(Material.class);
        for (String raw : this.config.getStringList("settings.items")) {
            Material material = Material.matchMaterial(raw);
            if (material != null) {
                materials.add(material);
            } else {
                this.plugin.getDebugSystem().log(MODULE_NAME, "Неизвестный материал в конфиге: " + raw);
            }
        }
        return materials;
    }

    public Set<Material> getTrackedMaterials() {
        return this.trackedMaterials;
    }

    /** Сколько тиков предмет из списка проживёт на земле, прежде чем его принудительно уберут. */
    public long getDespawnTicks() {
        double seconds = this.config.getDouble("settings.despawn-seconds", 30.0);
        return Math.max(1L, (long) (seconds * TICKS_PER_SECOND));
    }
}
