package net.schalker.SMPS.modules.marry.managers;

import net.schalker.DoAPI.DoAPI;
import net.schalker.SMPS.modules.marry.MarryModule;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manager for handling marriage/divorce requests and confirmations.
 */
public class MarryManager {
    
    private final DoAPI plugin;
    private final MarryModule module;
    
    // Pending requests: player UUID -> PendingRequest
    private final Map<UUID, PendingRequest> pendingRequests = new ConcurrentHashMap<>();
    
    public MarryManager(DoAPI plugin, MarryModule module) {
        this.plugin = plugin;
        this.module = module;
    }
    
    /**
     * Create a marriage request between two players.
     */
    public void createMarriageRequest(String priestName, Player player1, Player player2, long timeoutSeconds) {
        UUID uuid1 = player1.getUniqueId();
        UUID uuid2 = player2.getUniqueId();
        
        // Cancel any existing requests for these players
        cancelRequest(uuid1);
        cancelRequest(uuid2);
        
        // Create new request
        PendingRequest request = new PendingRequest(
            RequestType.MARRIAGE,
            priestName,
            uuid1,
            player1.getName(),
            uuid2,
            player2.getName()
        );
        
        pendingRequests.put(uuid1, request);
        pendingRequests.put(uuid2, request);
        
        // Send messages to players
        String timeout = String.valueOf(timeoutSeconds);
        player1.sendMessage(module.getMessage("marriage-request-received")
            .replace("{priest}", priestName)
            .replace("{partner}", player2.getName())
            .replace("{time}", timeout));
            
        player2.sendMessage(module.getMessage("marriage-request-received")
            .replace("{priest}", priestName)
            .replace("{partner}", player1.getName())
            .replace("{time}", timeout));
        
        // Schedule timeout
        scheduleTimeout(request, timeoutSeconds);
    }
    
    /**
     * Create a divorce request between two players.
     */
    public void createDivorceRequest(String priestName, Player player1, Player player2, long timeoutSeconds) {
        UUID uuid1 = player1.getUniqueId();
        UUID uuid2 = player2.getUniqueId();
        
        // Cancel any existing requests for these players
        cancelRequest(uuid1);
        cancelRequest(uuid2);
        
        // Create new request
        PendingRequest request = new PendingRequest(
            RequestType.DIVORCE,
            priestName,
            uuid1,
            player1.getName(),
            uuid2,
            player2.getName()
        );
        
        pendingRequests.put(uuid1, request);
        pendingRequests.put(uuid2, request);
        
        // Send messages to players
        String timeout = String.valueOf(timeoutSeconds);
        player1.sendMessage(module.getMessage("divorce-request-received")
            .replace("{priest}", priestName)
            .replace("{partner}", player2.getName())
            .replace("{time}", timeout));
            
        player2.sendMessage(module.getMessage("divorce-request-received")
            .replace("{priest}", priestName)
            .replace("{partner}", player1.getName())
            .replace("{time}", timeout));
        
        // Schedule timeout
        scheduleTimeout(request, timeoutSeconds);
    }
    
    /**
     * Player confirms a pending request.
     * Only confirms if the pending request matches the expected type.
     */
    public void confirmRequest(Player player, RequestType expectedType) {
        UUID uuid = player.getUniqueId();
        PendingRequest request = pendingRequests.get(uuid);
        
        if (request == null || request.getType() != expectedType) {
            player.sendMessage(module.getMessage("no-pending-request"));
            return;
        }
        
        // Check if already confirmed
        if (request.hasConfirmed(uuid)) {
            player.sendMessage(module.getMessage("already-confirmed"));
            return;
        }
        
        // Mark as confirmed
        request.addConfirmation(uuid);
        
        // Get partner info
        UUID partnerUuid = request.getOtherPlayer(uuid);
        Player partner = plugin.getServer().getPlayer(partnerUuid);
        String partnerName = request.getPlayerName(partnerUuid);
        String playerName = player.getName();
        
        if (request.getType() == RequestType.MARRIAGE) {
            player.sendMessage(module.getMessage("marriage-confirmed-player"));
        } else {
            player.sendMessage(module.getMessage("divorce-confirmed-player"));
        }
        
        // Notify partner about confirmation
        if (partner != null && partner.isOnline()) {
            String partnerMsg;
            if (request.getType() == RequestType.MARRIAGE) {
                partnerMsg = module.getMessage("marriage-partner-confirmed")
                    .replace("{player}", playerName);
            } else {
                partnerMsg = module.getMessage("divorce-partner-confirmed")
                    .replace("{player}", playerName);
            }
            partner.sendMessage(partnerMsg);
        }
        
        // Notify priest about confirmation
        String priestConfirmMsg = module.getMessage("priest-player-confirmed")
            .replace("{player}", playerName);
        notifyPriest(request.getPriestName(), priestConfirmMsg);
        
        // Check if both confirmed
        if (request.isBothConfirmed()) {
            processConfirmedRequest(request);
        } else {
            // Notify that waiting for partner
            if (request.getType() == RequestType.MARRIAGE) {
                player.sendMessage(module.getMessage("marriage-confirmed-waiting")
                    .replace("{partner}", partnerName));
            } else {
                player.sendMessage(module.getMessage("divorce-confirmed-waiting")
                    .replace("{partner}", partnerName));
            }
        }
    }
    
    /**
     * Player denies a pending request.
     * Only denies if the pending request matches the expected type.
     */
    public void denyRequest(Player player, RequestType expectedType) {
        UUID uuid = player.getUniqueId();
        PendingRequest request = pendingRequests.get(uuid);
        
        if (request == null || request.getType() != expectedType) {
            player.sendMessage(module.getMessage("no-pending-request"));
            return;
        }
        
        // Notify both players and priest
        UUID otherUuid = request.getOtherPlayer(uuid);
        Player otherPlayer = plugin.getServer().getPlayer(otherUuid);
        
        String denierName = player.getName();
        String message;
        String partnerMessage;
        String priestMessage;
        
        if (request.getType() == RequestType.MARRIAGE) {
            message = module.getMessage("marriage-denied").replace("{player}", denierName);
            partnerMessage = module.getMessage("marriage-partner-denied").replace("{player}", denierName);
            priestMessage = module.getMessage("priest-marriage-denied").replace("{player}", denierName);
        } else {
            message = module.getMessage("divorce-denied").replace("{player}", denierName);
            partnerMessage = module.getMessage("divorce-partner-denied").replace("{player}", denierName);
            priestMessage = module.getMessage("priest-divorce-denied").replace("{player}", denierName);
        }
        
        // Send to denier
        player.sendMessage(message);
        
        // Send to partner
        if (otherPlayer != null && otherPlayer.isOnline()) {
            otherPlayer.sendMessage(partnerMessage);
        }
        
        // Notify priest about denial
        notifyPriest(request.getPriestName(), priestMessage);
        
        // Clean up
        pendingRequests.remove(request.getPlayer1Uuid());
        pendingRequests.remove(request.getPlayer2Uuid());
        request.cancelTimeout();
    }
    
    /**
     * Process a confirmed request (both players confirmed).
     */
    private void processConfirmedRequest(PendingRequest request) {
        UUID uuid1 = request.getPlayer1Uuid();
        UUID uuid2 = request.getPlayer2Uuid();
        String name1 = request.getPlayer1Name();
        String name2 = request.getPlayer2Name();
        
        Player player1 = plugin.getServer().getPlayer(uuid1);
        Player player2 = plugin.getServer().getPlayer(uuid2);
        
        String priestName = request.getPriestName();
        
        if (request.getType() == RequestType.MARRIAGE) {
            // Create marriage in database
            plugin.getSchedulerManager().runAsync("marry-create", () -> {
                boolean success = module.getMarryDatabase().createMarriage(uuid1, name1, uuid2, name2);
                
                plugin.getSchedulerManager().runGlobalTask("marry-notify", () -> {
                    if (success) {
                        String successMsg = module.getMessage("marriage-success")
                            .replace("{player1}", name1)
                            .replace("{player2}", name2);
                        
                        if (player1 != null && player1.isOnline()) {
                            player1.sendMessage(successMsg);
                            // Play sound on entity thread for Folia
                            plugin.getSchedulerManager().runEntityTask(player1, "marry-sound-p1", () -> {
                                if (player1.isOnline()) playCatSound(player1, true);
                            });
                        }
                        if (player2 != null && player2.isOnline()) {
                            player2.sendMessage(successMsg);
                            plugin.getSchedulerManager().runEntityTask(player2, "marry-sound-p2", () -> {
                                if (player2.isOnline()) playCatSound(player2, true);
                            });
                        }
                        
                        // Notify priest
                        String priestMsg = module.getMessage("priest-marriage-success")
                            .replace("{player1}", name1)
                            .replace("{player2}", name2);
                        notifyPriest(priestName, priestMsg);
                        
                        // Play sound to priest
                        Player priest = plugin.getServer().getPlayer(priestName);
                        if (priest != null && priest.isOnline()) {
                            plugin.getSchedulerManager().runEntityTask(priest, "marry-sound-priest", () -> {
                                if (priest.isOnline()) playCatSound(priest, true);
                            });
                        }
                        
                        // Broadcast to server if enabled
                        if (module.getConfig().getBoolean("settings.broadcast-marriages", true)) {
                            String broadcastMsg = module.getMessage("broadcast-marriage")
                                .replace("{player1}", name1)
                                .replace("{player2}", name2);
                            plugin.getServer().broadcast(net.kyori.adventure.text.Component.text(broadcastMsg));
                        }
                    }
                });
            });
        } else {
            // Delete marriage from database
            plugin.getSchedulerManager().runAsync("marry-divorce", () -> {
                boolean success = module.getMarryDatabase().deleteMarriage(uuid1, uuid2);
                
                plugin.getSchedulerManager().runGlobalTask("divorce-notify", () -> {
                    if (success) {
                        String successMsg = module.getMessage("divorce-success")
                            .replace("{player1}", name1)
                            .replace("{player2}", name2);
                        
                        if (player1 != null && player1.isOnline()) {
                            player1.sendMessage(successMsg);
                            plugin.getSchedulerManager().runEntityTask(player1, "divorce-sound-p1", () -> {
                                if (player1.isOnline()) playCatSound(player1, false);
                            });
                        }
                        if (player2 != null && player2.isOnline()) {
                            player2.sendMessage(successMsg);
                            plugin.getSchedulerManager().runEntityTask(player2, "divorce-sound-p2", () -> {
                                if (player2.isOnline()) playCatSound(player2, false);
                            });
                        }
                        
                        // Notify priest
                        String priestMsg = module.getMessage("priest-divorce-success")
                            .replace("{player1}", name1)
                            .replace("{player2}", name2);
                        notifyPriest(priestName, priestMsg);
                        
                        // Play sound to priest
                        Player priest = plugin.getServer().getPlayer(priestName);
                        if (priest != null && priest.isOnline()) {
                            plugin.getSchedulerManager().runEntityTask(priest, "divorce-sound-priest", () -> {
                                if (priest.isOnline()) playCatSound(priest, false);
                            });
                        }
                        
                        // Broadcast to server if enabled
                        if (module.getConfig().getBoolean("settings.broadcast-divorces", false)) {
                            String broadcastMsg = module.getMessage("broadcast-divorce")
                                .replace("{player1}", name1)
                                .replace("{player2}", name2);
                            plugin.getServer().broadcast(net.kyori.adventure.text.Component.text(broadcastMsg));
                        }
                    }
                });
            });
        }
        
        // Clean up
        pendingRequests.remove(uuid1);
        pendingRequests.remove(uuid2);
        request.cancelTimeout();
    }
    
    /**
     * Play cat sound to player.
     * @param player The player
     * @param isMarriage true for marriage (purr), false for divorce (hiss)
     */
    private void playCatSound(Player player, boolean isMarriage) {
        if (!module.getConfig().getBoolean("settings.sounds.enabled", true)) {
            return;
        }
        
        String soundName = isMarriage 
            ? module.getConfig().getString("settings.sounds.marriage-sound", "ENTITY_CAT_PURR")
            : module.getConfig().getString("settings.sounds.divorce-sound", "ENTITY_CAT_HISS");
        
        Sound fallback = isMarriage ? Sound.ENTITY_CAT_PURR : Sound.ENTITY_CAT_HISS;
        Sound sound = resolveSound(soundName, fallback);
        float volume = (float) module.getConfig().getDouble("settings.sounds.volume", 1.0);
        float pitch = (float) module.getConfig().getDouble("settings.sounds.pitch", 1.0);
        
        player.playSound(player.getLocation(), sound, volume, pitch);
    }
    
    /**
     * Resolve a Sound from config name using Registry (non-deprecated for 1.21.3+).
     */
    private Sound resolveSound(String name, Sound fallback) {
        try {
            String key = name.toLowerCase().replace('_', '.');
            Sound sound = Registry.SOUNDS.get(NamespacedKey.minecraft(key));
            return sound != null ? sound : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }
    
    /**
     * Schedule timeout for a request.
     */
    private void scheduleTimeout(PendingRequest request, long seconds) {
        String taskName = "marry-timeout-" + request.getPlayer1Uuid();
        
        plugin.getSchedulerManager().runTaskLater(taskName, () -> {
            // Check if request still exists and not confirmed by both
            if (pendingRequests.containsValue(request) && !request.isBothConfirmed()) {
                UUID uuid1 = request.getPlayer1Uuid();
                UUID uuid2 = request.getPlayer2Uuid();
                
                Player player1 = plugin.getServer().getPlayer(uuid1);
                Player player2 = plugin.getServer().getPlayer(uuid2);
                
                String timeoutMsg;
                String priestTimeoutMsg;
                if (request.getType() == RequestType.MARRIAGE) {
                    timeoutMsg = module.getMessage("marriage-timeout");
                    priestTimeoutMsg = module.getMessage("priest-marriage-timeout");
                } else {
                    timeoutMsg = module.getMessage("divorce-timeout");
                    priestTimeoutMsg = module.getMessage("priest-divorce-timeout");
                }
                
                if (player1 != null && player1.isOnline()) {
                    player1.sendMessage(timeoutMsg);
                }
                if (player2 != null && player2.isOnline()) {
                    player2.sendMessage(timeoutMsg);
                }
                
                // Notify priest about timeout
                notifyPriest(request.getPriestName(), priestTimeoutMsg);
                
                // Clean up
                pendingRequests.remove(uuid1);
                pendingRequests.remove(uuid2);
            }
        }, seconds * 20); // Convert seconds to ticks
        
        request.setTimeoutTaskName(taskName);
    }
    
    /**
     * Cancel a request for a player.
     */
    public void cancelRequest(UUID playerUuid) {
        PendingRequest request = pendingRequests.get(playerUuid);
        if (request != null) {
            pendingRequests.remove(request.getPlayer1Uuid());
            pendingRequests.remove(request.getPlayer2Uuid());
            request.cancelTimeout();
        }
    }
    
    /**
     * Check if player has a pending request.
     */
    public boolean hasPendingRequest(UUID playerUuid) {
        return pendingRequests.containsKey(playerUuid);
    }
    
    /**
     * Clear all pending requests (used on module disable).
     */
    public void clearAll() {
        Set<PendingRequest> uniqueRequests = new HashSet<>(pendingRequests.values());
        for (PendingRequest request : uniqueRequests) {
            request.cancelTimeout();
        }
        pendingRequests.clear();
    }
    
    /**
     * Notify priest about the result of their marriage/divorce request.
     */
    private void notifyPriest(String priestName, String message) {
        if (priestName == null || message == null) {
            return;
        }
        
        Player priest = plugin.getServer().getPlayer(priestName);
        if (priest != null && priest.isOnline()) {
            priest.sendMessage(message);
        }
    }
    
    /**
     * Enum for request types.
     */
    public enum RequestType {
        MARRIAGE,
        DIVORCE
    }
    
    /**
     * Class representing a pending marriage/divorce request.
     */
    private class PendingRequest {
        private final RequestType type;
        private final String priestName;
        private final UUID player1Uuid;
        private final String player1Name;
        private final UUID player2Uuid;
        private final String player2Name;
        private final Set<UUID> confirmations = new HashSet<>();
        private String timeoutTaskName;
        
        public PendingRequest(RequestType type, String priestName, UUID player1Uuid, String player1Name, 
                            UUID player2Uuid, String player2Name) {
            this.type = type;
            this.priestName = priestName;
            this.player1Uuid = player1Uuid;
            this.player1Name = player1Name;
            this.player2Uuid = player2Uuid;
            this.player2Name = player2Name;
        }
        
        public RequestType getType() { return type; }
        public String getPriestName() { return priestName; }
        public UUID getPlayer1Uuid() { return player1Uuid; }
        public String getPlayer1Name() { return player1Name; }
        public UUID getPlayer2Uuid() { return player2Uuid; }
        public String getPlayer2Name() { return player2Name; }
        
        public void addConfirmation(UUID uuid) {
            confirmations.add(uuid);
        }
        
        public boolean hasConfirmed(UUID uuid) {
            return confirmations.contains(uuid);
        }
        
        public boolean isBothConfirmed() {
            return confirmations.contains(player1Uuid) && confirmations.contains(player2Uuid);
        }
        
        public UUID getOtherPlayer(UUID player) {
            return player.equals(player1Uuid) ? player2Uuid : player1Uuid;
        }
        
        public String getPlayerName(UUID uuid) {
            return uuid.equals(player1Uuid) ? player1Name : player2Name;
        }
        
        public void setTimeoutTaskName(String taskName) {
            this.timeoutTaskName = taskName;
        }
        
        public void cancelTimeout() {
            if (timeoutTaskName != null) {
                plugin.getSchedulerManager().cancelTask(timeoutTaskName);
            }
        }
    }
}
