package net.schalker.SMPS.modules.itemdespawn.listeners;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.listener.BaseListener;
import net.schalker.SMPS.modules.itemdespawn.ItemDespawnModule;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.ItemSpawnEvent;

/**
 * Marks tracked-material items as unlimited-lifetime the instant they spawn (so vanilla's own
 * 5-minute despawn never fires early or interferes), then schedules a single one-shot entity
 * task per item that force-removes it after the configured duration - which is normally much
 * shorter than 5 minutes, e.g. the default 30 seconds.
 */
public class ItemDespawnListener extends BaseListener {

    private final ItemDespawnModule module;
    private final Set<UUID> pendingTasks = ConcurrentHashMap.newKeySet();

    public ItemDespawnListener(DoAPI plugin, ItemDespawnModule module) {
        super(plugin);
        this.module = module;
    }

    @EventHandler
    public void onItemSpawn(ItemSpawnEvent event) {
        Item item = event.getEntity();
        if (!this.module.getTrackedMaterials().contains(item.getItemStack().getType())) {
            return;
        }

        item.setUnlimitedLifetime(true);

        UUID id = item.getUniqueId();
        this.pendingTasks.add(id);
        this.plugin.getSchedulerManager().runEntityTaskLater(item, taskName(id), () -> {
            this.pendingTasks.remove(id);
            if (item.isValid()) {
                item.remove();
            }
        }, this.module.getDespawnTicks());
    }

    private String taskName(UUID id) {
        return "itemdespawn-" + id;
    }

    public void cleanup() {
        for (UUID id : this.pendingTasks) {
            this.plugin.getSchedulerManager().cancelTask(taskName(id));
        }
        this.pendingTasks.clear();
    }
}
