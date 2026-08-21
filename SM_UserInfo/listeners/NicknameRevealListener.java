package net.schalker.SMPS.modules.userinfo.listeners;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.listener.BaseListener;
import net.schalker.SMPS.modules.userinfo.UserinfoModule;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

public class NicknameRevealListener extends BaseListener {

    private final UserinfoModule module;
    private final Map<UUID, Long> lastReveal = new ConcurrentHashMap<>();

    public NicknameRevealListener(DoAPI plugin, UserinfoModule module) {
        super(plugin);
        this.module = module;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!(event.getRightClicked() instanceof Player target)) {
            return;
        }

        Player viewer = event.getPlayer();
        if (!viewer.isSneaking()) {
            return;
        }
        if (!this.module.isNicknameRevealEnabled()) {
            return;
        }

        String permission = this.module.getNicknameRevealPermission();
        if (permission != null && !permission.isBlank() && !viewer.hasPermission(permission)) {
            return;
        }
        if (isOnCooldown(viewer.getUniqueId())) {
            return;
        }

        String text = this.module.getNicknameRevealFormat()
                .replace("{player}", target.getName())
                .replace("{displayname}", stripToPlain(target));

        viewer.sendActionBar(LegacyComponentSerializer.legacySection()
                .deserialize(this.plugin.applyColors(text)));
    }

    private boolean isOnCooldown(UUID viewerId) {
        long cooldown = this.module.getNicknameRevealCooldownMillis();
        if (cooldown <= 0L) {
            return false;
        }

        long now = System.currentTimeMillis();
        Long previous = this.lastReveal.get(viewerId);
        if (previous != null && now - previous < cooldown) {
            return true;
        }

        this.lastReveal.put(viewerId, now);
        return false;
    }

    private String stripToPlain(Player target) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(target.displayName());
    }
}
