package net.schalker.SMPS.modules.phaseguard.listeners;

import net.schalker.SMPS.modules.phaseguard.PhaseGuardModule;
import net.schalker.SMPS.modules.phaseguard.PhaseGuardSettings;
import net.schalker.SMPS.modules.phaseguard.TrackedPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;

public class SessionListener implements Listener {

    private final PhaseGuardModule module;

    public SessionListener(PhaseGuardModule module) {
        this.module = module;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        PhaseGuardSettings settings = module.getSettings();
        if (settings == null) {
            return;
        }
        grant(event.getPlayer(), settings.getGraceJoinMillis());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        module.forget(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        PhaseGuardSettings settings = module.getSettings();
        if (settings == null) {
            return;
        }
        grant(event.getPlayer(), settings.getGraceTeleportMillis());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        PhaseGuardSettings settings = module.getSettings();
        if (settings == null) {
            return;
        }
        grant(event.getPlayer(), settings.getGraceRespawnMillis());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        PhaseGuardSettings settings = module.getSettings();
        if (settings == null) {
            return;
        }
        grant(event.getPlayer(), settings.getGraceWorldChangeMillis());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        PhaseGuardSettings settings = module.getSettings();
        if (settings == null) {
            return;
        }
        grant(event.getPlayer(), settings.getGraceTeleportMillis());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVehicleExit(VehicleExitEvent event) {
        PhaseGuardSettings settings = module.getSettings();
        if (settings == null) {
            return;
        }
        if (event.getExited() instanceof Player player) {
            grant(player, settings.getGraceTeleportMillis());
        }
    }

    private void grant(Player player, long durationMillis) {
        if (durationMillis <= 0L) {
            return;
        }
        TrackedPlayer tracked = module.getTracked(player.getUniqueId());
        tracked.grantGrace(System.currentTimeMillis(), durationMillis);
    }
}
