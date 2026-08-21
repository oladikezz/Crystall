package net.schalker.SMPS.modules.trollitems.listeners;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.listener.BaseListener;
import net.schalker.SMPS.modules.trollitems.TrollItemsModule;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

/**
 * Throwing the marked item launches a tagged Snowball (same trick SM_Spit uses for its
 * LlamaSpit). On impact: every nearby player gets yanked toward the impact point for a
 * fixed window (velocity overridden every tick - setVelocity() is fine cross-tick since it's
 * always dispatched via the player's own entity-scheduler task, unlike teleport() which needs
 * teleportAsync() on this Canvas/Folia setup), then whoever didn't quite make it gets a
 * finishing teleport, and a sealed quartz+bars box gets built around them. Block placement and
 * restoration are region-scoped (blocks belong to a region, not an entity), while every player
 * read/write stays entity-scoped - the two scheduler kinds this module needs are used exactly
 * where each one is the correct fit.
 *
 * <p>{@code onInteract} re-checks the thrower's permission at throw time (not just give-time):
 * without it, the snowball just throws normally with no marker attached.
 */
public class GravityBombListener extends BaseListener {

    private final TrollItemsModule module;

    private final Set<UUID> trappedPlayers = ConcurrentHashMap.newKeySet();
    private final Map<BlockPos, BlockState> cageBlocks = new ConcurrentHashMap<>();
    private final Set<String> activeRestoreTasks = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> lastThrowMillis = new ConcurrentHashMap<>();

    public GravityBombListener(DoAPI plugin, TrollItemsModule module) {
        super(plugin);
        this.module = module;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        this.trappedPlayers.remove(id);
        this.lastThrowMillis.remove(id);
        this.plugin.getSchedulerManager().cancelTask(pullTaskName(id));
    }

    // No ignoreCancelled here on purpose: some other plugin on the server (region
    // protection, anti-cheat, etc.) may cancel PlayerInteractEvent before we see it, and
    // this trigger doesn't depend on the vanilla interaction succeeding anyway - HIGHEST
    // priority so we still get to react even if something later would also cancel it.
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!this.module.isBomb(hand)) {
            return;
        }
        if (!this.module.hasPermission(player)) {
            return;
        }

        this.plugin.getDebugSystem().log("GravityBomb", "onInteract fired for " + player.getName()
                + ", action=" + event.getAction());

        // Always cancel and launch it ourselves - letting vanilla's own throw fire
        // instead (tagging whatever it spawns via ProjectileLaunchEvent) turned out to be
        // unreliable: that event doesn't dependably line up with this one for a plain
        // single-click throw, so the snowball would sometimes fly untagged and do nothing.
        // Launching it directly guarantees the entity carries our marker from the instant
        // it exists.
        event.setCancelled(true);

        UUID playerId = player.getUniqueId();
        long cooldownMillis = this.module.getThrowCooldownMillis();
        long now = System.currentTimeMillis();
        Long lastThrow = this.lastThrowMillis.get(playerId);
        if (lastThrow != null && now - lastThrow < cooldownMillis) {
            long remainingMillis = cooldownMillis - (now - lastThrow);
            player.sendMessage(this.module.getMessage("on-cooldown")
                    .replace("{seconds}", String.format("%.1f", remainingMillis / 1000.0)));
            return;
        }
        this.lastThrowMillis.put(playerId, now);

        // A little extra loft on top of the raw look direction, so this always arcs and
        // travels like an actual toss instead of embedding point-blank when aiming down
        // at nearby ground - the pull point is wherever it lands, not your feet.
        Vector direction = player.getEyeLocation().getDirection().clone();
        direction.setY(direction.getY() + 0.15);
        direction.normalize();

        Snowball bomb = player.launchProjectile(Snowball.class, direction.multiply(1.3));
        bomb.getPersistentDataContainer().set(this.module.getBombKey(), PersistentDataType.BYTE, (byte) 1);
        this.plugin.getDebugSystem().log("GravityBomb", "Bomb snowball launched for " + player.getName()
                + " id=" + bomb.getUniqueId());

        // Cancelling the interact event also stops vanilla's own item consumption, so
        // this stack is worked down by hand instead.
        if (hand.getAmount() <= 1) {
            player.getInventory().setItemInMainHand(null);
        } else {
            hand.setAmount(hand.getAmount() - 1);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Snowball snowball)) {
            return;
        }
        Byte marker = snowball.getPersistentDataContainer().get(this.module.getBombKey(), PersistentDataType.BYTE);
        if (marker == null || marker != (byte) 1) {
            return;
        }
        this.plugin.getDebugSystem().log("GravityBomb", "Marked snowball " + snowball.getUniqueId() + " hit, handling impact");

        Location impact = snowball.getLocation().clone();
        UUID shooterId = snowball.getShooter() instanceof Player shooter ? shooter.getUniqueId() : null;
        snowball.remove();

        handleImpact(impact, shooterId);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        BlockPos pos = new BlockPos(block.getWorld(), block.getX(), block.getY(), block.getZ());
        if (this.cageBlocks.containsKey(pos)) {
            event.setCancelled(true);
        }
    }

    // Blocks the throw itself while trapped, rather than letting the pearl fly and only
    // failing to teleport on landing (belt-and-braces with onTeleport below).
    @EventHandler(ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof EnderPearl pearl)) {
            return;
        }
        if (pearl.getShooter() instanceof Player shooter && this.trappedPlayers.contains(shooter.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL
                && this.trappedPlayers.contains(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    private void handleImpact(Location impact, UUID shooterId) {
        World world = impact.getWorld();
        List<Player> caught = new ArrayList<>();
        double radius = this.module.getPullRadius();
        boolean excludeThrower = this.module.isThrowerExcluded();
        for (Player player : world.getPlayers()) {
            if (excludeThrower && player.getUniqueId().equals(shooterId)) {
                continue;
            }
            if (player.getLocation().distance(impact) <= radius) {
                caught.add(player);
            }
        }
        // Always show the impact itself, even if nobody was close enough to get caught
        // (e.g. testing solo, with the thrower excluded) - only the pull/cage part below
        // actually needs a target.
        world.spawnParticle(Particle.REVERSE_PORTAL, impact, 80, 1.5, 1.5, 1.5, 0.05);
        world.playSound(impact, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.6f);
        this.plugin.getDebugSystem().log("GravityBomb", "Impact at " + impact + ", caught=" + caught.size());

        if (caught.isEmpty()) {
            return;
        }

        for (Player player : caught) {
            startPull(player, impact);
        }

        long finalizeDelay = this.module.getPullDurationTicks() + 2L;
        this.plugin.getSchedulerManager().runRegionTaskLater(impact, "gravitybomb-finalize-" + System.nanoTime(),
                () -> finalizeCage(impact, caught), finalizeDelay);
    }

    private void startPull(Player player, Location center) {
        UUID id = player.getUniqueId();
        double speed = this.module.getPullSpeed();
        int[] ticksLeft = {this.module.getPullDurationTicks()};
        String taskName = pullTaskName(id);

        this.plugin.getSchedulerManager().runEntityTaskTimer(player, taskName, () -> {
            if (!player.isOnline() || ticksLeft[0] <= 0) {
                this.plugin.getSchedulerManager().cancelTask(taskName);
                return;
            }
            Vector toCenter = center.toVector().subtract(player.getLocation().toVector());
            if (toCenter.lengthSquared() > 0.01) {
                player.setVelocity(toCenter.normalize().multiply(speed));
            }
            ticksLeft[0]--;
        }, 1L, 1L);
    }

    private void finalizeCage(Location center, List<Player> caught) {
        double threshold = this.module.getTeleportThreshold();

        for (Player player : caught) {
            if (player == null || !player.isOnline()) {
                continue;
            }
            this.plugin.getSchedulerManager().cancelTask(pullTaskName(player.getUniqueId()));

            // Read-only distance check, same reasoning as everywhere else in this session's
            // troll modules: informational, and the pull already put them close to center.
            if (player.getLocation().distance(center) > threshold) {
                Location destination = randomInteriorPoint(center);
                this.plugin.getSchedulerManager().runEntityTask(player, "gravitybomb-finish-" + player.getUniqueId(), () -> {
                    if (player.isOnline()) {
                        player.teleportAsync(destination);
                    }
                });
            }
            this.trappedPlayers.add(player.getUniqueId());
        }

        buildCage(center);

        String restoreTaskName = "gravitybomb-restore-" + System.nanoTime();
        this.activeRestoreTasks.add(restoreTaskName);
        this.plugin.getSchedulerManager().runRegionTaskLater(center, restoreTaskName, () -> {
            this.activeRestoreTasks.remove(restoreTaskName);
            restoreCage(center, caught);
        }, this.module.getCageDurationTicks());
    }

    /** Floor sits one block below the impact point, so the cage rests on the ground
     * the explosion happened at instead of floating at the projectile's own height. */
    private int cageBaseY(Location center) {
        return center.getBlockY() - 1;
    }

    private Location randomInteriorPoint(Location center) {
        double offsetX = 0.3 + Math.random() * 0.4;
        double offsetZ = 0.3 + Math.random() * 0.4;
        return new Location(center.getWorld(), center.getBlockX() + offsetX, cageBaseY(center) + 1,
                center.getBlockZ() + offsetZ, center.getYaw(), center.getPitch());
    }

    private void buildCage(Location center) {
        World world = center.getWorld();
        int baseX = center.getBlockX() - 1;
        int baseZ = center.getBlockZ() - 1;
        int baseY = cageBaseY(center);
        int height = this.module.getCageWallHeight();
        int ceilingY = baseY + height + 1;

        for (int dx = 0; dx < 4; dx++) {
            for (int dz = 0; dz < 4; dz++) {
                placeAndSave(world, baseX + dx, baseY, baseZ + dz, Material.QUARTZ_BLOCK);
                placeAndSave(world, baseX + dx, ceilingY, baseZ + dz, Material.QUARTZ_BLOCK);
            }
        }

        for (int layer = 1; layer <= height; layer++) {
            for (int dx = 0; dx < 4; dx++) {
                for (int dz = 0; dz < 4; dz++) {
                    boolean onEdge = dx == 0 || dx == 3 || dz == 0 || dz == 3;
                    if (onEdge) {
                        placeAndSave(world, baseX + dx, baseY + layer, baseZ + dz, Material.IRON_BARS);
                    }
                }
            }
        }
    }

    private void placeAndSave(World world, int x, int y, int z, Material material) {
        Block block = world.getBlockAt(x, y, z);
        BlockState originalState = block.getState();
        this.cageBlocks.put(new BlockPos(world, x, y, z), originalState);
        // applyPhysics=true so IRON_BARS gets its north/south/east/west connection state
        // (re)computed and also notifies already-placed neighbours to refresh their own
        // shape - otherwise bars placed earlier never learn a neighbour showed up later.
        block.setType(material, true);
    }

    private void restoreCage(Location center, List<Player> caught) {
        int baseX = center.getBlockX() - 1;
        int baseZ = center.getBlockZ() - 1;
        int baseY = cageBaseY(center);
        int ceilingY = baseY + this.module.getCageWallHeight() + 1;
        World world = center.getWorld();

        for (int y = baseY; y <= ceilingY; y++) {
            for (int dx = 0; dx < 4; dx++) {
                for (int dz = 0; dz < 4; dz++) {
                    BlockState state = this.cageBlocks.remove(new BlockPos(world, baseX + dx, y, baseZ + dz));
                    if (state != null) {
                        state.update(true, false);
                    }
                }
            }
        }

        for (Player player : caught) {
            if (player != null) {
                this.trappedPlayers.remove(player.getUniqueId());
            }
        }
    }

    private String pullTaskName(UUID playerId) {
        return "gravitybomb-pull-" + playerId;
    }

    public void cleanup() {
        for (String taskName : this.activeRestoreTasks) {
            this.plugin.getSchedulerManager().cancelTask(taskName);
        }
        this.activeRestoreTasks.clear();

        for (BlockState state : this.cageBlocks.values()) {
            try {
                state.update(true, false);
            } catch (Throwable ignored) {
            }
        }
        this.cageBlocks.clear();
        this.trappedPlayers.clear();
        this.lastThrowMillis.clear();
    }

    private record BlockPos(World world, int x, int y, int z) {
    }
}
