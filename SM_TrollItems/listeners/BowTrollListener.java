package net.schalker.SMPS.modules.trollitems.listeners;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.listener.BaseListener;
import net.schalker.SMPS.modules.trollitems.TrollItemsModule;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * Drives the troll bow: while a player draws it, whoever is on their crosshair gets
 * "hooked" and dragged to follow their aim (no separate draw-start event exists, so this
 * is polled once per tick via a per-player entity-scheduler task). Releasing the bow
 * (EntityShootBowEvent) launches the hooked player along the shooter's look direction;
 * a second per-tick check detects when a launched player's actual movement falls far
 * short of their velocity (i.e. they slammed into something) and deals the impact damage.
 *
 * <p>Every trigger point re-checks the shooter's permission (not just give-time), since the
 * item can in principle end up in a non-admin's hands (drop, trade, another plugin) - without
 * the permission it behaves like a plain bow.
 */
public class BowTrollListener extends BaseListener {

    private final TrollItemsModule module;

    private final Map<UUID, UUID> hookedTargetByShooter = new ConcurrentHashMap<>();
    private final Map<UUID, Location> lastLocationByLaunched = new ConcurrentHashMap<>();

    public BowTrollListener(DoAPI plugin, TrollItemsModule module) {
        super(plugin);
        this.module = module;
        for (Player player : Bukkit.getOnlinePlayers()) {
            startTracking(player);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        startTracking(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        this.plugin.getSchedulerManager().cancelTask(taskName(id));
        clearShooterState(id);
        this.lastLocationByLaunched.remove(id);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onShootBow(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player shooter)) {
            return;
        }
        if (!this.module.isTrollBow(event.getBow())) {
            return;
        }
        if (!this.module.hasPermission(shooter)) {
            return;
        }

        event.setCancelled(true);

        UUID shooterId = shooter.getUniqueId();
        UUID targetId = this.hookedTargetByShooter.remove(shooterId);
        if (targetId == null) {
            return;
        }

        Player target = Bukkit.getPlayer(targetId);
        if (target == null || !target.isOnline()) {
            return;
        }

        Vector direction = shooter.getEyeLocation().getDirection().normalize();
        double power = this.module.getLaunchPower() * Math.max(0.2f, event.getForce());
        Vector velocity = direction.multiply(power);

        this.plugin.getSchedulerManager().runEntityTask(target, "trollitems-bow-launch-" + targetId, () -> {
            if (!target.isOnline()) {
                return;
            }
            target.setVelocity(velocity);
            this.lastLocationByLaunched.put(targetId, target.getLocation().clone());
        });
    }

    private void startTracking(Player player) {
        UUID id = player.getUniqueId();
        this.plugin.getSchedulerManager().runEntityTaskTimer(player, taskName(id), () -> tick(player), 1L, 1L);
    }

    private String taskName(UUID id) {
        return "trollitems-bow-tick-" + id;
    }

    private void tick(Player player) {
        if (!player.isOnline()) {
            return;
        }
        UUID id = player.getUniqueId();

        tickShooter(player, id);
        tickLaunched(player, id);
    }

    private void tickShooter(Player player, UUID id) {
        if (!isDrawingTrollBow(player) || !this.module.hasPermission(player)) {
            clearShooterState(id);
            return;
        }

        UUID targetId = this.hookedTargetByShooter.get(id);
        if (targetId == null) {
            Player found = findHookTarget(player);
            if (found != null) {
                this.hookedTargetByShooter.put(id, found.getUniqueId());
            }
            return;
        }

        Player target = Bukkit.getPlayer(targetId);
        if (target == null || !target.isOnline()) {
            clearShooterState(id);
            return;
        }

        double distance = this.module.getHookDistance();
        Location eye = player.getEyeLocation();
        Vector desired = eye.toVector().add(eye.getDirection().multiply(distance));

        this.plugin.getSchedulerManager().runEntityTask(target, "trollitems-bow-drag-" + targetId, () -> {
            if (!target.isOnline()) {
                return;
            }
            Location current = target.getLocation();
            // Some Folia forks (e.g. Canvas) reject the synchronous teleport() outright
            // while region threading is active, even from the thread that owns the
            // entity - teleportAsync() is the only call that's safe here.
            target.teleportAsync(new Location(current.getWorld(), desired.getX(), desired.getY(), desired.getZ(),
                    current.getYaw(), current.getPitch()));
        });
    }

    private void tickLaunched(Player player, UUID id) {
        Location last = this.lastLocationByLaunched.get(id);
        if (last == null) {
            return;
        }

        Vector velocity = player.getVelocity();
        double expectedMove = velocity.length();
        double actualMove = last.distance(player.getLocation());

        if (expectedMove > 0.3 && actualMove < expectedMove * 0.35) {
            player.damage(this.module.getCollisionDamage());
            this.lastLocationByLaunched.remove(id);
        } else if (expectedMove < 0.05) {
            this.lastLocationByLaunched.remove(id);
        } else {
            this.lastLocationByLaunched.put(id, player.getLocation().clone());
        }
    }

    private boolean isDrawingTrollBow(Player player) {
        if (!player.isHandRaised()) {
            return false;
        }
        EquipmentSlot hand = player.getHandRaised();
        ItemStack raised = hand == EquipmentSlot.OFF_HAND
                ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItemInMainHand();
        return this.module.isTrollBow(raised);
    }

    private Player findHookTarget(Player shooter) {
        RayTraceResult result = shooter.rayTraceEntities((int) this.module.getHookRange(), false);
        if (result == null || !(result.getHitEntity() instanceof Player hit)) {
            return null;
        }
        if (hit.equals(shooter)) {
            return null;
        }
        return hit;
    }

    private void clearShooterState(UUID shooterId) {
        this.hookedTargetByShooter.remove(shooterId);
    }

    public void cleanup() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            this.plugin.getSchedulerManager().cancelTask(taskName(player.getUniqueId()));
        }
        this.hookedTargetByShooter.clear();
        this.lastLocationByLaunched.clear();
    }
}
