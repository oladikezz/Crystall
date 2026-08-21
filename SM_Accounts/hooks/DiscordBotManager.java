package site.deforce.SM_Accounts.hooks;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.exceptions.HierarchyException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import net.schalker.DoAPI.DoAPI;

import java.util.EnumSet;

/**
 * Manages the JDA Discord bot instance for guild membership checks
 * and nickname synchronization.
 */
public class DiscordBotManager {

    private final DoAPI plugin;
    private JDA jda;
    private final long guildId;

    /**
     * Create and connect the Discord bot.
     *
     * @param plugin  SMPS plugin instance
     * @param token   Bot token
     * @param guildId Discord guild (server) ID
     * @throws Exception if the bot fails to connect
     */
    public DiscordBotManager(DoAPI plugin, String token, String guildId) throws Exception {
        this.plugin = plugin;
        this.guildId = Long.parseLong(guildId);

        this.jda = JDABuilder.createLight(token, EnumSet.of(
                        GatewayIntent.GUILD_MEMBERS
                ))
                .setMemberCachePolicy(MemberCachePolicy.NONE)
                .disableCache(EnumSet.of(
                        CacheFlag.VOICE_STATE,
                        CacheFlag.EMOJI,
                        CacheFlag.STICKER,
                        CacheFlag.SCHEDULED_EVENTS
                ))
                .build();

        // Wait for the bot to be ready
        this.jda.awaitReady();
        plugin.getDebugSystem().log("SM_Accounts", "Discord bot connected as: " + jda.getSelfUser().getName());

        Guild guild = jda.getGuildById(this.guildId);
        if (guild == null) {
            plugin.getDebugSystem().log("SM_Accounts", "WARNING: Could not find guild with ID " + guildId
                    + ". Make sure the bot is invited to the server!");
        } else {
            plugin.getDebugSystem().log("SM_Accounts", "Discord bot connected to guild: " + guild.getName());
        }
    }

    /**
     * Check if the bot is ready and connected.
     */
    public boolean isReady() {
        return jda != null && jda.getStatus() == JDA.Status.CONNECTED;
    }

    /**
     * Get the Discord guild.
     *
     * @return Guild object or null if not found
     */
    public Guild getGuild() {
        if (jda == null) return null;
        return jda.getGuildById(guildId);
    }

    /**
     * Check if a Discord user is a member of the configured guild.
     * This is a BLOCKING call - only call from async context!
     *
     * @param discordId The Discord user ID
     * @return true if the user is in the guild, false otherwise
     */
    public boolean isInGuild(String discordId) {
        if (jda == null) return true; // Fail-open if bot is not connected

        Guild guild = getGuild();
        if (guild == null) {
            plugin.getDebugSystem().log("SM_Accounts", "Guild not found, allowing player (fail-open)");
            return true; // Fail-open
        }

        try {
            Member member = guild.retrieveMemberById(discordId).complete();
            return member != null;
        } catch (ErrorResponseException e) {
            if (e.getErrorResponse() == ErrorResponse.UNKNOWN_MEMBER
                    || e.getErrorResponse() == ErrorResponse.UNKNOWN_USER) {
                return false;
            }
            // For other errors (rate limit, network), fail-open
            plugin.getDebugSystem().logError("Error checking guild membership for " + discordId, e);
            return true;
        } catch (Exception e) {
            plugin.getDebugSystem().logError("Unexpected error checking guild membership for " + discordId, e);
            return true; // Fail-open on unexpected errors
        }
    }

    /**
     * Set a member's nickname in the Discord guild.
     * This is a BLOCKING call - only call from async context!
     *
     * @param discordId The Discord user ID
     * @param nickname  The new nickname to set
     * @return true if successful, false otherwise
     */
    public boolean setNickname(String discordId, String nickname) {
        if (jda == null) return false;

        Guild guild = getGuild();
        if (guild == null) {
            plugin.getDebugSystem().log("SM_Accounts", "Guild not found, cannot set nickname");
            return false;
        }

        try {
            Member member = guild.retrieveMemberById(discordId).complete();
            if (member == null) {
                return false;
            }

            // Skip if nickname already matches
            String currentNick = member.getNickname();
            if (nickname.equals(currentNick)) {
                return true; // Already set
            }

            // Cannot modify guild owner's nickname
            if (member.isOwner()) {
                plugin.getDebugSystem().log("SM_Accounts",
                        "Cannot change nickname for guild owner: " + member.getUser().getName());
                return false;
            }

            // Cannot modify members with higher or equal highest role
            if (!guild.getSelfMember().canInteract(member)) {
                plugin.getDebugSystem().log("SM_Accounts",
                        "Cannot change nickname for " + member.getUser().getName()
                                + " - member has higher or equal role than bot");
                return false;
            }

            guild.modifyNickname(member, nickname).complete();
            plugin.getDebugSystem().log("SM_Accounts",
                    "Set Discord nickname for " + member.getUser().getName() + " to: " + nickname);
            return true;
        } catch (HierarchyException e) {
            plugin.getDebugSystem().log("SM_Accounts",
                    "Cannot set nickname for " + discordId + " - member has higher or equal role than bot");
            return false;
        } catch (ErrorResponseException e) {
            if (e.getErrorResponse() == ErrorResponse.UNKNOWN_MEMBER
                    || e.getErrorResponse() == ErrorResponse.UNKNOWN_USER) {
                plugin.getDebugSystem().log("SM_Accounts",
                        "Cannot set nickname - user " + discordId + " not in guild");
            } else if (e.getErrorResponse() == ErrorResponse.MISSING_PERMISSIONS) {
                plugin.getDebugSystem().log("SM_Accounts",
                        "Cannot set nickname for " + discordId + " - missing permissions (role hierarchy issue?)");
            } else {
                plugin.getDebugSystem().logError("Error setting nickname for " + discordId, e);
            }
            return false;
        } catch (Exception e) {
            plugin.getDebugSystem().logError("Unexpected error setting nickname for " + discordId, e);
            return false;
        }
    }

    /**
     * Shutdown the JDA bot instance.
     */
    public void shutdown() {
        if (jda != null) {
            jda.shutdown();
            plugin.getDebugSystem().log("SM_Accounts", "Discord bot shut down");
            jda = null;
        }
    }
}

