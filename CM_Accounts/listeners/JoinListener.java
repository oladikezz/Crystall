package site.deforce.SM_Accounts.listeners;

import net.schalker.DoAPI.DoAPI;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import site.deforce.SM_Accounts.SM_Accounts;
import site.deforce.SM_Accounts.database.AccountsDatabase;
import site.deforce.SM_Accounts.database.TwinksDatabase;
import site.deforce.SM_Accounts.hooks.DiscordBotManager;

/**
 * Listener for player join events.
 * Checks if player has linked their Discord account.
 */
public class JoinListener implements Listener {

    private final SM_Accounts module;
    private final DoAPI plugin;

    public JoinListener(SM_Accounts module) {
        this.module = module;
        this.plugin = module.getPlugin();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onAsyncPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        if (!module.isEnabled()) {
            return;
        }

        AccountsDatabase database = module.getDatabase();
        TwinksDatabase twinksDatabase = module.getTwinksDatabase();

        if (database == null) {
            plugin.getDebugSystem().log("SM_Accounts", "Database not available, allowing player to join");
            return;
        }

        // Check if player is a twink - twinks skip verification
        if (twinksDatabase != null && twinksDatabase.isTwink(event.getName())) {
            plugin.getDebugSystem().log("SM_Accounts",
                    "Player " + event.getName() + " is a twink account, skipping verification");
            return;
        }

        // AsyncPlayerPreLoginEvent already runs async, so we can safely do DB operations here
        boolean isLinked = database.isLinked(event.getUniqueId());

        plugin.getDebugSystem().log("SM_Accounts",
                "Player " + event.getName() + " linked status: " + isLinked);

        if (!isLinked) {
            // Always generate a new code on each join attempt
            String code = database.createOrUpdateCode(event.getUniqueId(), event.getName());

            // Disallow the player from joining with verification message
            String kickMessage = module.getKickMessage(code, event.getName());

            // Use LegacyComponentSerializer to parse the colors and extract URLs
            // We make the entire message clickable to ensure it works if the proxy relays it to chat
            net.kyori.adventure.text.Component component = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                    .deserialize(kickMessage)
                    .clickEvent(net.kyori.adventure.text.event.ClickEvent.openUrl("https://schalker.net/auth/link?code=" + code))
                    .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                            net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(module.getHoverText())
                    ));

            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, component);

            plugin.getDebugSystem().log("SM_Accounts",
                    "Blocked " + event.getName() + " - not linked. New code: " + code);
        } else {
            // User is linked, verify entry in DiscordSRV accounts.aof
            String discordId = database.getDiscordId(event.getUniqueId());
            if (discordId != null && !discordId.isBlank()) {
                new site.deforce.SM_Accounts.utils.DiscordSRVHelper(plugin)
                        .addEntryIfNotExists(discordId, event.getUniqueId());

                // Check if player is still in the Discord guild
                if (module.getConfig().getBoolean("guild-check.enabled", false)) {
                    DiscordBotManager botManager = module.getDiscordBotManager();
                    if (botManager != null && botManager.isReady()) {
                        boolean inGuild = botManager.isInGuild(discordId);

                        if (!inGuild) {
                            String inviteLink = module.getConfig().getString("guild-check.invite-link",
                                    "https://discord.gg/YOUR_INVITE_CODE");
                            String kickMsg = module.getConfig().getString("guild-check.kick-message",
                                    "&c&lNot in Discord Server!\n\n&7Join here: &b&n{invite_link}");
                            kickMsg = kickMsg.replace("{invite_link}", inviteLink);
                            kickMsg = kickMsg.replace("{player_name}", event.getName());
                            kickMsg = plugin.applyColors(kickMsg);

                            net.kyori.adventure.text.Component component = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                                    .deserialize(kickMsg)
                                    .clickEvent(net.kyori.adventure.text.event.ClickEvent.openUrl(inviteLink))
                                    .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                                            net.kyori.adventure.text.Component.text("Click to join our Discord server!")
                                    ));

                            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, component);

                            plugin.getDebugSystem().log("SM_Accounts",
                                    "Blocked " + event.getName() + " - not in Discord guild (Discord ID: " + discordId + ")");
                            return;
                        }
                    }
                }
            }
        }
    }
}
