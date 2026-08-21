package be.isach.ultracosmetics.util;

import be.isach.ultracosmetics.UCosmeticsModule;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Lightweight scheduler adapter that replaces FoliaLib's PlatformScheduler.
 * Delegates to Folia's region-aware scheduler APIs directly via Paper/Folia.
 * All methods return a {@link ScheduledTask} handle that can be cancelled.
 */
public class UCScheduler {
    private static final AtomicInteger COUNTER = new AtomicInteger(0);
    private final Plugin plugin;

    public UCScheduler(Plugin plugin) {
        this.plugin = plugin;
    }

    // ─── Entity-scoped tasks ────────────────────────────────────────
    public ScheduledTask runAtEntity(Entity entity, Consumer<Object> task) {
        io.papermc.paper.threadedregions.scheduler.ScheduledTask handle =
                entity.getScheduler().run(plugin, t -> task.accept(null), null);
        return new ScheduledTask(handle);
    }

    public ScheduledTask runAtEntityLater(Entity entity, Runnable task, long delayTicks) {
        io.papermc.paper.threadedregions.scheduler.ScheduledTask handle =
                entity.getScheduler().runDelayed(plugin, t -> task.run(), null, delayTicks);
        return new ScheduledTask(handle);
    }

    public ScheduledTask runAtEntityTimer(Entity entity, Runnable task, long delayTicks, long periodTicks) {
        io.papermc.paper.threadedregions.scheduler.ScheduledTask handle =
                entity.getScheduler().runAtFixedRate(plugin, t -> task.run(), null, delayTicks, periodTicks);
        return new ScheduledTask(handle);
    }

    public ScheduledTask runAtEntityTimer(Entity entity, Consumer<ScheduledTask> task, long delayTicks, long periodTicks) {
        final ScheduledTask[] wrapper = new ScheduledTask[1];
        io.papermc.paper.threadedregions.scheduler.ScheduledTask handle =
                entity.getScheduler().runAtFixedRate(plugin, t -> task.accept(wrapper[0]), null, delayTicks, periodTicks);
        wrapper[0] = new ScheduledTask(handle);
        return wrapper[0];
    }

    // ─── Location/region-scoped tasks ───────────────────────────────
    public ScheduledTask runAtLocation(Location location, Runnable task) {
        io.papermc.paper.threadedregions.scheduler.ScheduledTask handle =
                Bukkit.getRegionScheduler().run(plugin, location, t -> task.run());
        return new ScheduledTask(handle);
    }

    public ScheduledTask runAtLocationLater(Location location, Runnable task, long delayTicks) {
        io.papermc.paper.threadedregions.scheduler.ScheduledTask handle =
                Bukkit.getRegionScheduler().runDelayed(plugin, location, t -> task.run(), delayTicks);
        return new ScheduledTask(handle);
    }

    public ScheduledTask runAtLocationTimer(Location location, Runnable task, long delayTicks, long periodTicks) {
        io.papermc.paper.threadedregions.scheduler.ScheduledTask handle =
                Bukkit.getRegionScheduler().runAtFixedRate(plugin, location, t -> task.run(), delayTicks, periodTicks);
        return new ScheduledTask(handle);
    }

    // ─── Global tasks (not region-specific) ─────────────────────────
    public ScheduledTask runLater(Consumer<Object> task, long delayTicks) {
        io.papermc.paper.threadedregions.scheduler.ScheduledTask handle =
                Bukkit.getGlobalRegionScheduler().runDelayed(plugin, t -> task.accept(null), delayTicks);
        return new ScheduledTask(handle);
    }

    public ScheduledTask runLater(Runnable task, long delayTicks) {
        return runLater(t -> task.run(), delayTicks);
    }

    public ScheduledTask runTimer(Runnable task, long delayTicks, long periodTicks) {
        io.papermc.paper.threadedregions.scheduler.ScheduledTask handle =
                Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, t -> task.run(), delayTicks, periodTicks);
        return new ScheduledTask(handle);
    }

    public ScheduledTask runNextTick(Consumer<Object> task) {
        io.papermc.paper.threadedregions.scheduler.ScheduledTask handle =
                Bukkit.getGlobalRegionScheduler().run(plugin, t -> task.accept(null));
        return new ScheduledTask(handle);
    }

    // ─── Async tasks ────────────────────────────────────────────────
    public ScheduledTask runAsync(Consumer<Object> task) {
        io.papermc.paper.threadedregions.scheduler.ScheduledTask handle =
                Bukkit.getAsyncScheduler().runNow(plugin, t -> task.accept(null));
        return new ScheduledTask(handle);
    }

    public ScheduledTask runLaterAsync(Runnable task, long delayTicks) {
        // Convert ticks to ms for async scheduler (50ms per tick)
        io.papermc.paper.threadedregions.scheduler.ScheduledTask handle =
                Bukkit.getAsyncScheduler().runDelayed(plugin, t -> task.run(),
                        delayTicks * 50, java.util.concurrent.TimeUnit.MILLISECONDS);
        return new ScheduledTask(handle);
    }

    public ScheduledTask runTimerAsync(Runnable task, long delayTicks, long periodTicks) {
        io.papermc.paper.threadedregions.scheduler.ScheduledTask handle =
                Bukkit.getAsyncScheduler().runAtFixedRate(plugin, t -> task.run(),
                        delayTicks * 50, periodTicks * 50, java.util.concurrent.TimeUnit.MILLISECONDS);
        return new ScheduledTask(handle);
    }

    /**
     * Wraps Folia's native ScheduledTask for easy cancellation.
     */
    public static class ScheduledTask {
        private final io.papermc.paper.threadedregions.scheduler.ScheduledTask handle;

        public ScheduledTask(io.papermc.paper.threadedregions.scheduler.ScheduledTask handle) {
            this.handle = handle;
        }

        public void cancel() {
            if (handle != null) {
                handle.cancel();
            }
        }

        public boolean isCancelled() {
            return handle == null || handle.isCancelled();
        }
    }
}

