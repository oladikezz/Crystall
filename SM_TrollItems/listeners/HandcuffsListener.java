package net.schalker.SMPS.modules.trollitems.listeners;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.listener.BaseListener;
import net.schalker.SMPS.modules.trollitems.TrollItemsModule;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;

/**
 * Selection is driven entirely by melee hits with the wand (EntityDamageByEntityEvent), which is
 * what guarantees Folia-safety when a new link is attached: a hit only lands at melee range, so
 * the two players involved are provably close together (same region) at that moment.
 *
 * <p>Chains are an ordered list of UUIDs shared by every member (head first). Each consecutive
 * pair (puller, follower) gets its own independent repeating task, driven off the puller's own
 * thread - NOT one big task walking the whole chain from the head. That matters once a chain
 * gets long: consecutive links always stay within pull-distance of each other by construction,
 * but the tail of a long train can end up far from the head, so only ever reading an *immediate*
 * neighbour's location (never anything further down the chain) keeps every read close to the
 * same-region assumption this listener relies on throughout.
 *
 * <p>Wand-hit rules:
 * <ul>
 *   <li>Hit an unselected player -&gt; marks them as the attachment point ("puller").</li>
 *   <li>Hit that same player again -&gt; releases their whole chain if they're in one,
 *       otherwise just cancels the pending selection.</li>
 *   <li>Hit a different, unchained player -&gt; attaches them behind the puller, but only if
 *       the puller is currently the last link (no branching chains).</li>
 * </ul>
 *
 * <p>The wand only works for admins: {@code onHit} bails out before cancelling the event if the
 * wielder lacks the permission, so it just deals normal melee damage instead.
 */
public class HandcuffsListener extends BaseListener {

    private final TrollItemsModule module;

    // Every member of a chain maps to the SAME shared list instance (head-first order).
    private final Map<UUID, List<UUID>> chainByMember = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> particleTickCounter = new ConcurrentHashMap<>();
    private final Map<UUID, PendingSelection> pendingFirstTarget = new ConcurrentHashMap<>();

    public HandcuffsListener(DoAPI plugin, TrollItemsModule module) {
        super(plugin);
        this.module = module;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        this.pendingFirstTarget.remove(id);
        if (this.chainByMember.containsKey(id)) {
            releaseChain(id);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player admin)) {
            return;
        }
        if (!(event.getEntity() instanceof Player hit)) {
            return;
        }
        if (!this.module.isWand(admin.getInventory().getItemInMainHand())) {
            return;
        }
        if (!this.module.hasPermission(admin)) {
            return;
        }

        event.setCancelled(true);

        UUID adminId = admin.getUniqueId();
        UUID hitId = hit.getUniqueId();
        PendingSelection pending = this.pendingFirstTarget.get(adminId);

        // Hitting the same player selected a moment ago again: release their chain (if any),
        // otherwise it was just a no-op selection to begin with - cancel it.
        if (pending != null && !pending.isExpired() && pending.targetId().equals(hitId)) {
            this.pendingFirstTarget.remove(adminId);
            if (this.chainByMember.containsKey(hitId)) {
                releaseChain(hitId);
                admin.sendMessage(this.module.getMessage("uncuffed").replace("{player}", hit.getName()));
            } else {
                admin.sendMessage(this.module.getMessage("selection-cancelled"));
            }
            return;
        }

        if (pending == null || pending.isExpired()) {
            this.pendingFirstTarget.put(adminId, new PendingSelection(hitId,
                    System.currentTimeMillis() + this.module.getSelectionTimeoutMillis()));
            admin.sendMessage(this.module.getMessage("selected-first").replace("{player}", hit.getName()));
            return;
        }

        // Second hit, on a different player - attach it behind whoever was selected first.
        this.pendingFirstTarget.remove(adminId);
        Player puller = Bukkit.getPlayer(pending.targetId());
        if (puller == null || !puller.isOnline()) {
            admin.sendMessage(this.module.getMessage("first-target-offline"));
            return;
        }
        if (this.chainByMember.containsKey(hitId)) {
            admin.sendMessage(this.module.getMessage("already-cuffed").replace("{player}", hit.getName()));
            return;
        }

        List<UUID> pullerChain = this.chainByMember.get(puller.getUniqueId());
        if (pullerChain != null && !isTail(pullerChain, puller.getUniqueId())) {
            admin.sendMessage(this.module.getMessage("not-tail").replace("{player}", puller.getName()));
            return;
        }

        // Read-only proximity gate - both players just landed melee hits on/from the same
        // admin within the selection window, so this is expected to be a cheap same-region
        // read in the overwhelming majority of cases, not a cross-region mutation.
        double maxDistance = this.module.getPullDistance();
        if (!puller.getWorld().equals(hit.getWorld()) || puller.getLocation().distance(hit.getLocation()) > maxDistance) {
            admin.sendMessage(this.module.getMessage("too-far"));
            return;
        }

        attachToChain(puller, hit);
        admin.sendMessage(this.module.getMessage("cuffed")
                .replace("{player1}", puller.getName())
                .replace("{player2}", hit.getName()));
    }

    private boolean isTail(List<UUID> chain, UUID memberId) {
        return !chain.isEmpty() && chain.get(chain.size() - 1).equals(memberId);
    }

    private void attachToChain(Player puller, Player follower) {
        UUID pullerId = puller.getUniqueId();
        UUID followerId = follower.getUniqueId();

        List<UUID> chain = this.chainByMember.get(pullerId);
        if (chain == null) {
            chain = new CopyOnWriteArrayList<>();
            chain.add(pullerId);
            this.chainByMember.put(pullerId, chain);
        }
        chain.add(followerId);
        this.chainByMember.put(followerId, chain);
        this.particleTickCounter.put(pullerId, 0);

        this.plugin.getSchedulerManager().runEntityTaskTimer(puller, linkTaskName(pullerId),
                () -> linkTick(pullerId, followerId), 1L, 1L);
    }

    private void releaseChain(UUID anyMemberId) {
        List<UUID> chain = this.chainByMember.get(anyMemberId);
        if (chain == null) {
            return;
        }
        for (UUID member : chain) {
            this.chainByMember.remove(member);
        }
        for (int i = 0; i < chain.size() - 1; i++) {
            UUID pullerId = chain.get(i);
            this.plugin.getSchedulerManager().cancelTask(linkTaskName(pullerId));
            this.particleTickCounter.remove(pullerId);
        }
    }

    /**
     * Drives one link of the chain: puller and follower are fixed for the lifetime of this
     * task (a link never gets reassigned), so both are captured directly instead of being
     * re-derived from the shared chain list every tick.
     */
    private void linkTick(UUID pullerId, UUID followerId) {
        Player puller = Bukkit.getPlayer(pullerId);
        Player follower = Bukkit.getPlayer(followerId);
        if (puller == null || !puller.isOnline() || follower == null || !follower.isOnline()) {
            return;
        }

        Location pullerLoc = puller.getLocation();
        Location followerLoc = follower.getLocation();
        if (!pullerLoc.getWorld().equals(followerLoc.getWorld())) {
            return;
        }

        int interval = this.module.getLeashParticleIntervalTicks();
        int tick = this.particleTickCounter.merge(pullerId, 1, Integer::sum);
        if (interval <= 1 || tick % interval == 0) {
            drawLeashParticles(pullerLoc, followerLoc);
        }

        double maxDistance = this.module.getPullDistance();
        double distance = pullerLoc.distance(followerLoc);
        if (distance <= maxDistance) {
            return;
        }

        Vector direction = followerLoc.toVector().subtract(pullerLoc.toVector()).normalize();
        Vector snapPoint = pullerLoc.toVector().add(direction.multiply(maxDistance));

        this.plugin.getSchedulerManager().runEntityTask(follower, "trollitems-handcuffs-drag-" + followerId, () -> {
            if (!follower.isOnline()) {
                return;
            }
            Location current = follower.getLocation();
            // Same Canvas/Folia restriction we already hit on ArmorPoser and BowTroll -
            // the synchronous teleport() throws while region threading is active.
            follower.teleportAsync(new Location(current.getWorld(), snapPoint.getX(), snapPoint.getY(), snapPoint.getZ(),
                    current.getYaw(), current.getPitch()));
        });
    }

    /**
     * Draws the "leash" as a line of dust particles between two players' chest height.
     * Called from the puller's own tick task, so this executes on the thread owning
     * pullerLoc - same same-region assumption as everywhere else in this listener.
     */
    private void drawLeashParticles(Location pullerLoc, Location followerLoc) {
        double spacing = this.module.getLeashParticleSpacing();
        Particle.DustOptions dust = new Particle.DustOptions(this.module.getLeashParticleColor(), 1.0f);

        Vector start = pullerLoc.toVector().add(new Vector(0, 1.2, 0));
        Vector end = followerLoc.toVector().add(new Vector(0, 1.2, 0));
        double distance = start.distance(end);
        if (distance < 0.01) {
            return;
        }

        int steps = Math.max(1, (int) (distance / spacing));
        Vector delta = end.clone().subtract(start);

        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            Vector point = start.clone().add(delta.clone().multiply(t));
            pullerLoc.getWorld().spawnParticle(Particle.DUST, point.getX(), point.getY(), point.getZ(),
                    1, 0.0, 0.0, 0.0, 0.0, dust);
        }
    }

    private String linkTaskName(UUID pullerId) {
        return "trollitems-handcuffs-pull-" + pullerId;
    }

    public void cleanup() {
        for (UUID memberId : this.chainByMember.keySet().toArray(new UUID[0])) {
            // No-op if this member was never a puller for a link - cancelTask() is a safe miss.
            this.plugin.getSchedulerManager().cancelTask(linkTaskName(memberId));
        }
        this.chainByMember.clear();
        this.particleTickCounter.clear();
        this.pendingFirstTarget.clear();
    }

    private record PendingSelection(UUID targetId, long expiresAtMillis) {
        boolean isExpired() {
            return System.currentTimeMillis() > expiresAtMillis;
        }
    }
}
