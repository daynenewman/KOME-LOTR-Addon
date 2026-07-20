package kome.common.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import kome.common.data.KOMEWaypointAccessService;
import lotr.common.LOTRConfig;
import lotr.common.LOTRLevelData;
import lotr.common.LOTRPlayerData;
import lotr.common.world.map.LOTRWaypoint;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;

/** Server-authoritative request used only when KOME replaces LOTR's alignment gate. */
public final class KOMEPacketWaypointTravelRequest implements IMessage {
    private int waypointId;

    public KOMEPacketWaypointTravelRequest() {
    }

    public KOMEPacketWaypointTravelRequest(LOTRWaypoint waypoint) {
        waypointId = waypoint == null ? -1 : waypoint.getID();
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        waypointId = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(waypointId);
    }

    public static final class Handler implements IMessageHandler<KOMEPacketWaypointTravelRequest, IMessage> {
        @Override
        public IMessage onMessage(KOMEPacketWaypointTravelRequest message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().playerEntity;
            if (!LOTRConfig.enableFastTravel) {
                player.addChatMessage(new ChatComponentTranslation("chat.lotr.ftDisabled"));
                return null;
            }
            LOTRWaypoint[] waypoints = LOTRWaypoint.values();
            if (message.waypointId < 0 || message.waypointId >= waypoints.length) {
                player.addChatMessage(new ChatComponentText("Fast travel denied: invalid waypoint."));
                return null;
            }
            LOTRWaypoint waypoint = waypoints[message.waypointId];
            boolean nativeProgression = KOMEWaypointAccessService.hasNativeProgression(player, waypoint);
            KOMEWaypointAccessService.Decision decision = KOMEWaypointAccessService.evaluatePlayer(player, waypoint, nativeProgression);
            if (!decision.finalAllowed) {
                player.addChatMessage(new ChatComponentText("Fast travel denied: " + decision.reason));
                return null;
            }

            LOTRPlayerData playerData = LOTRLevelData.getData(player);
            if (playerData.getTimeSinceFT() < playerData.getWaypointFTTime(waypoint, player)) {
                player.closeScreen();
                player.addChatMessage(new ChatComponentTranslation("lotr.fastTravel.moreTime", waypoint.getDisplayName()));
            } else if (!playerData.canFastTravel()) {
                player.closeScreen();
                player.addChatMessage(new ChatComponentTranslation("lotr.fastTravel.underAttack"));
            } else if (player.isPlayerSleeping()) {
                player.closeScreen();
                player.addChatMessage(new ChatComponentTranslation("lotr.fastTravel.inBed"));
            } else {
                playerData.setTargetFTWaypoint(waypoint);
            }
            return null;
        }
    }
}
