package net.schalker.DoAPI.core.scheduler;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.schalker.DoAPI.DoAPI;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class SchedulerManager {

    private static final long MILLIS_PER_TICK = 50L;

    private final DoAPI plugin;
    private final Map<String, ScheduledTask> tasks = new ConcurrentHashMap<>();
    private volatile boolean globalTickStarted;

    public SchedulerManager(DoAPI plugin) {
        this.plugin = plugin;
        Bukkit.getGlobalRegionScheduler().run(plugin, task -> globalTickStarted = true);
    }

    public boolean isGlobalTickStarted() {
        return globalTickStarted;
    }

    public void runGlobalTask(String name, Runnable runnable) {
        schedule(name, runnable, false, body ->
                Bukkit.getGlobalRegionScheduler().run(plugin, body));
    }

    public void runTaskLater(String name, Runnable runnable, long delayTicks) {
        long delay = normalizeDelay(delayTicks);
        schedule(name, runnable, false, body ->
                Bukkit.getGlobalRegionScheduler().runDelayed(plugin, body, delay));
    }

    public void runTaskTimer(String name, Runnable runnable, long delayTicks, long periodTicks) {
        long delay = normalizeDelay(delayTicks);
        long period = normalizePeriod(periodTicks);
        schedule(name, runnable, true, body ->
                Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, body, delay, period));
    }

    public void runRegionTask(Location location, String name, Runnable runnable) {
        if (location == null || location.getWorld() == null) {
            runGlobalTask(name, runnable);
            return;
        }
        schedule(name, runnable, false, body ->
                Bukkit.getRegionScheduler().run(plugin, location, body));
    }

    public void runRegionTaskLater(Location location, String name, Runnable runnable, long delayTicks) {
        if (location == null || location.getWorld() == null) {
            runTaskLater(name, runnable, delayTicks);
            return;
        }
        long delay = normalizeDelay(delayTicks);
        schedule(name, runnable, false, body ->
                Bukkit.getRegionScheduler().runDelayed(plugin, location, body, delay));
    }

    public void runRegionTaskTimer(Location location, String name, Runnable runnable, long delayTicks, long periodTicks) {
        if (location == null || location.getWorld() == null) {
            runTaskTimer(name, runnable, delayTicks, periodTicks);
            return;
        }
        long delay = normalizeDelay(delayTicks);
        long period = normalizePeriod(periodTicks);
        schedule(name, runnable, true, body ->
                Bukkit.getRegionScheduler().runAtFixedRate(plugin, location, body, delay, period));
    }

    public void runRegionTask(World world, int chunkX, int chunkZ, String name, Runnable runnable) {
        if (world == null) {
            runGlobalTask(name, runnable);
            return;
        }
        schedule(name, runnable, false, body ->
                Bukkit.getRegionScheduler().run(plugin, world, chunkX, chunkZ, body));
    }

    public void runEntityTask(Entity entity, String name, Runnable runnable) {
        if (entity == null) {
            runGlobalTask(name, runnable);
            return;
        }
        schedule(name, runnable, false, body ->
                entity.getScheduler().run(plugin, body, null));
    }

    public void runEntityTaskLater(Entity entity, String name, Runnable runnable, long delayTicks) {
        if (entity == null) {
            runTaskLater(name, runnable, delayTicks);
            return;
        }
        long delay = normalizeDelay(delayTicks);
        schedule(name, runnable, false, body ->
                entity.getScheduler().runDelayed(plugin, body, null, delay));
    }

    public void runEntityTaskTimer(Entity entity, String name, Runnable runnable, long delayTicks, long periodTicks) {
        if (entity == null) {
            runTaskTimer(name, runnable, delayTicks, periodTicks);
            return;
        }
        long delay = normalizeDelay(delayTicks);
        long period = normalizePeriod(periodTicks);
        schedule(name, runnable, true, body ->
                entity.getScheduler().runAtFixedRate(plugin, body, null, delay, period));
    }

    public void runTaskLater(Entity entity, Runnable runnable, long delayTicks) {
        runEntityTaskLater(entity, "entity-task-" + entity.getUniqueId() + "-" + System.nanoTime(), runnable, delayTicks);
    }

    public void runAsync(String name, Runnable runnable) {
        schedule(name, runnable, false, body ->
                Bukkit.getAsyncScheduler().runNow(plugin, body));
    }

    public void runAsyncLater(String name, Runnable runnable, long delayTicks) {
        long delayMillis = Math.max(1L, delayTicks) * MILLIS_PER_TICK;
        schedule(name, runnable, false, body ->
                Bukkit.getAsyncScheduler().runDelayed(plugin, body, delayMillis, TimeUnit.MILLISECONDS));
    }

    public void runAsyncTimer(String name, Runnable runnable, long delayTicks, long periodTicks) {
        long delayMillis = Math.max(1L, delayTicks) * MILLIS_PER_TICK;
        long periodMillis = Math.max(1L, periodTicks) * MILLIS_PER_TICK;
        schedule(name, runnable, true, body ->
                Bukkit.getAsyncScheduler().runAtFixedRate(plugin, body, delayMillis, periodMillis, TimeUnit.MILLISECONDS));
    }

    public void runTaskLaterAsync(String name, Runnable runnable, long delayTicks) {
        runAsyncLater(name, runnable, delayTicks);
    }

    public void runTaskTimerAsync(String name, Runnable runnable, long delayTicks, long periodTicks) {
        runAsyncTimer(name, runnable, delayTicks, periodTicks);
    }

    public boolean cancelTask(String name) {
        ScheduledTask task = tasks.remove(name);
        if (task == null) {
            return false;
        }
        try {
            task.cancel();
        } catch (Throwable ignored) {
        }
        return true;
    }

    public boolean hasTask(String name) {
        return tasks.containsKey(name);
    }

    public void cancelAllTasks() {
        for (String name : tasks.keySet().toArray(new String[0])) {
            cancelTask(name);
        }
        tasks.clear();
    }

    public int getTaskCount() {
        return tasks.size();
    }

    public <T> T callGlobalSync(String name, Supplier<T> supplier, long timeoutMillis) {
        if (Bukkit.isGlobalTickThread()) {
            return supplier.get();
        }
        if (!globalTickStarted) {
            return null;
        }

        CompletableFuture<T> future = new CompletableFuture<>();
        Bukkit.getGlobalRegionScheduler().run(plugin, task -> complete(future, supplier));
        return await(future, timeoutMillis, name);
    }

    public <T> T callGlobalSync(Supplier<T> supplier, long timeoutMillis) {
        return callGlobalSync("global-sync-call", supplier, timeoutMillis);
    }

    public <T> T callRegionSync(Location location, Supplier<T> supplier, long timeoutMillis) {
        if (location == null || location.getWorld() == null) {
            return callGlobalSync(supplier, timeoutMillis);
        }
        if (Bukkit.isOwnedByCurrentRegion(location)) {
            return supplier.get();
        }

        CompletableFuture<T> future = new CompletableFuture<>();
        Bukkit.getRegionScheduler().execute(plugin, location, () -> complete(future, supplier));
        return await(future, timeoutMillis, "region-sync-call " + formatLocation(location));
    }

    public <T> T callEntitySync(Entity entity, Supplier<T> supplier, long timeoutMillis) {
        if (entity == null) {
            return callGlobalSync(supplier, timeoutMillis);
        }
        if (Bukkit.isOwnedByCurrentRegion(entity)) {
            return supplier.get();
        }

        CompletableFuture<T> future = new CompletableFuture<>();
        ScheduledTask scheduled = entity.getScheduler().run(plugin, task -> complete(future, supplier), null);
        if (scheduled == null) {
            return null;
        }
        return await(future, timeoutMillis, "entity-sync-call " + entity.getUniqueId());
    }

    private <T> void complete(CompletableFuture<T> future, Supplier<T> supplier) {
        try {
            future.complete(supplier.get());
        } catch (Throwable throwable) {
            future.completeExceptionally(throwable);
        }
    }

    private <T> T await(CompletableFuture<T> future, long timeoutMillis, String name) {
        try {
            return future.get(Math.max(1L, timeoutMillis), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Throwable throwable) {
            logTaskFailure(name, throwable);
            return null;
        }
    }

    private void schedule(String name, Runnable runnable, boolean repeating,
                          Function<Consumer<ScheduledTask>, ScheduledTask> launcher) {
        if (runnable == null) {
            return;
        }
        if (repeating) {
            cancelTask(name);
        }

        AtomicReference<ScheduledTask> reference = new AtomicReference<>();
        AtomicBoolean finished = new AtomicBoolean(false);

        Consumer<ScheduledTask> body = scheduled -> {
            try {
                runnable.run();
            } catch (Throwable throwable) {
                logTaskFailure(name, throwable);
            } finally {
                if (!repeating) {
                    finished.set(true);
                    ScheduledTask self = reference.get();
                    if (self != null) {
                        tasks.remove(name, self);
                    }
                }
            }
        };

        ScheduledTask task;
        try {
            task = launcher.apply(body);
        } catch (Throwable throwable) {
            logTaskFailure(name, throwable);
            return;
        }
        if (task == null) {
            return;
        }

        reference.set(task);
        if (repeating || !finished.get()) {
            tasks.put(name, task);
            if (!repeating && finished.get()) {
                tasks.remove(name, task);
            }
        }
    }

    private void logTaskFailure(String name, Throwable throwable) {
        if (plugin.getDebugSystem() != null) {
            plugin.getDebugSystem().logError("Scheduler", "Task '" + name + "' failed", throwable);
        } else {
            plugin.getLogger().severe("Task '" + name + "' failed: " + throwable.getMessage());
        }
    }

    private long normalizeDelay(long delayTicks) {
        return Math.max(1L, delayTicks);
    }

    private long normalizePeriod(long periodTicks) {
        return Math.max(1L, periodTicks);
    }

    private String formatLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            return "unknown";
        }
        return location.getWorld().getName()
                + "@" + location.getBlockX()
                + "," + location.getBlockY()
                + "," + location.getBlockZ();
    }
}
