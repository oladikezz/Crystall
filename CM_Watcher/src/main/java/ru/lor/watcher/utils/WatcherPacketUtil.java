package ru.lor.watcher.utils;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.TextureProperty;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.protocol.world.Location;
import com.github.retrooper.packetevents.wrapper.play.server.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.EnumSet;
import java.util.UUID;

public class WatcherPacketUtil {

    public static void sendAddPlayerInfo(Player target, UUID npcUuid, String name, String texture, String signature) {
        UserProfile profile = new UserProfile(npcUuid, name);
        if (texture != null && signature != null) {
            profile.setTextureProperties(Collections.singletonList(new TextureProperty("textures", texture, signature)));
        }

        WrapperPlayServerPlayerInfoUpdate.PlayerInfo info = new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
                profile,
                true,
                0,
                GameMode.SURVIVAL,
                Component.text(name),
                null
        );

        WrapperPlayServerPlayerInfoUpdate addPlayerPacket = new WrapperPlayServerPlayerInfoUpdate(
                EnumSet.of(WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER, WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LISTED, WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_DISPLAY_NAME),
                info
        );

        PacketEvents.getAPI().getPlayerManager().sendPacket(target, addPlayerPacket);
    }

    public static void sendScoreboardTeamHideNametag(Player target, String teamName, UUID npcUuid) {
        WrapperPlayServerTeams.ScoreBoardTeamInfo teamInfo = new WrapperPlayServerTeams.ScoreBoardTeamInfo(
                Component.text("watcher"),
                Component.empty(),
                Component.empty(),
                WrapperPlayServerTeams.NameTagVisibility.NEVER,
                WrapperPlayServerTeams.CollisionRule.NEVER,
                NamedTextColor.DARK_PURPLE,
                WrapperPlayServerTeams.OptionData.NONE
        );

        WrapperPlayServerTeams createTeamPacket = new WrapperPlayServerTeams(
                teamName,
                WrapperPlayServerTeams.TeamMode.CREATE,
                teamInfo,
                Collections.singletonList("ShaderCoder")
        );

        PacketEvents.getAPI().getPlayerManager().sendPacket(target, createTeamPacket);
    }

    public static void sendSpawnPlayerNpc(Player target, int entityId, UUID npcUuid, org.bukkit.Location bukkitLoc) {
        Location packetLoc = new Location(bukkitLoc.getX(), bukkitLoc.getY(), bukkitLoc.getZ(), bukkitLoc.getYaw(), bukkitLoc.getPitch());
        WrapperPlayServerSpawnEntity spawnPacket = new WrapperPlayServerSpawnEntity(
                entityId,
                npcUuid,
                EntityTypes.PLAYER,
                packetLoc,
                bukkitLoc.getYaw(),
                0,
                null
        );

        PacketEvents.getAPI().getPlayerManager().sendPacket(target, spawnPacket);
    }

    public static void sendHeadLook(Player target, int entityId, float yaw) {
        WrapperPlayServerEntityHeadLook headLookPacket = new WrapperPlayServerEntityHeadLook(entityId, yaw);
        PacketEvents.getAPI().getPlayerManager().sendPacket(target, headLookPacket);
    }

    public static void sendTeleport(Player target, int entityId, org.bukkit.Location bukkitLoc) {
        Location packetLoc = new Location(bukkitLoc.getX(), bukkitLoc.getY(), bukkitLoc.getZ(), bukkitLoc.getYaw(), bukkitLoc.getPitch());
        WrapperPlayServerEntityTeleport tpPacket = new WrapperPlayServerEntityTeleport(entityId, packetLoc, true);
        PacketEvents.getAPI().getPlayerManager().sendPacket(target, tpPacket);
    }

    public static void sendDestroyNpc(Player target, int entityId, UUID npcUuid, String teamName) {
        WrapperPlayServerDestroyEntities destroyPacket = new WrapperPlayServerDestroyEntities(entityId);
        WrapperPlayServerPlayerInfoRemove removeTabPacket = new WrapperPlayServerPlayerInfoRemove(npcUuid);
        WrapperPlayServerTeams removeTeamPacket = new WrapperPlayServerTeams(
                teamName,
                WrapperPlayServerTeams.TeamMode.REMOVE,
                (WrapperPlayServerTeams.ScoreBoardTeamInfo) null,
                Collections.emptyList()
        );

        PacketEvents.getAPI().getPlayerManager().sendPacket(target, destroyPacket);
        PacketEvents.getAPI().getPlayerManager().sendPacket(target, removeTabPacket);
        PacketEvents.getAPI().getPlayerManager().sendPacket(target, removeTeamPacket);
    }
}
