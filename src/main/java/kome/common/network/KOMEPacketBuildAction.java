package kome.common.network;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import kome.common.KOMEReflection;
import kome.common.data.KOMEAlliance;
import kome.common.data.KOMEAllianceAuthority;
import kome.common.data.KOMEBuildService;
import kome.common.data.KOMEPlayerBuild;
import kome.common.data.KOMEWorldData;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;

import java.util.UUID;

/** Server-authoritative Build GUI intent. No client permission flag or total is trusted. */
public class KOMEPacketBuildAction implements IMessage {
    public String action = "";
    public String tileId = "";
    public String buildId = "";
    public String contributionId = "";
    public String text = "";
    public String populationFaction = "";
    public int offensiveHalfHours;
    public int defensiveHalfHours;
    public int dimension;
    public double x;
    public double y;
    public double z;

    public KOMEPacketBuildAction() {
    }

    public KOMEPacketBuildAction(String action, String tileId, String buildId, String contributionId,
            String text, String populationFaction, int offensiveHalfHours, int defensiveHalfHours,
            int dimension, double x, double y, double z) {
        this.action = safe(action);
        this.tileId = safe(tileId);
        this.buildId = safe(buildId);
        this.contributionId = safe(contributionId);
        this.text = safe(text);
        this.populationFaction = safe(populationFaction);
        this.offensiveHalfHours = offensiveHalfHours;
        this.defensiveHalfHours = defensiveHalfHours;
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        action = ByteBufUtils.readUTF8String(buf);
        tileId = ByteBufUtils.readUTF8String(buf);
        buildId = ByteBufUtils.readUTF8String(buf);
        contributionId = ByteBufUtils.readUTF8String(buf);
        text = ByteBufUtils.readUTF8String(buf);
        populationFaction = ByteBufUtils.readUTF8String(buf);
        offensiveHalfHours = buf.readInt();
        defensiveHalfHours = buf.readInt();
        dimension = buf.readInt();
        x = buf.readDouble();
        y = buf.readDouble();
        z = buf.readDouble();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, safe(action));
        ByteBufUtils.writeUTF8String(buf, safe(tileId));
        ByteBufUtils.writeUTF8String(buf, safe(buildId));
        ByteBufUtils.writeUTF8String(buf, safe(contributionId));
        ByteBufUtils.writeUTF8String(buf, safe(text));
        ByteBufUtils.writeUTF8String(buf, safe(populationFaction));
        buf.writeInt(offensiveHalfHours);
        buf.writeInt(defensiveHalfHours);
        buf.writeInt(dimension);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
    }

    public static class Handler implements IMessageHandler<KOMEPacketBuildAction, IMessage> {
        @Override
        public IMessage onMessage(KOMEPacketBuildAction message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            KOMEWorldData data = KOMEWorldData.get(KOMEReflection.getWorld(player));
            UUID actorId = KOMEReflection.getEntityUUID(player);
            String actorName = player.getCommandSenderName();
            String actorFaction = new KOMEAllianceAuthority(data).getPlayerFaction(player);
            boolean admin = player.canCommandSenderUseCommand(2, "build");
            String action = safe(message.action).toLowerCase(java.util.Locale.ROOT);
            String tile = kome.common.data.KOMEConquestTile.normalizeId(message.tileId);
            try {
                if ("create".equals(action)) {
                    KOMEBuildService.Decision coordinates = KOMEBuildService.validateCoordinates(
                        tile, message.x, message.y, message.z);
                    if (!coordinates.allowed) throw new IllegalArgumentException(coordinates.reason);
                    if (message.dimension != player.worldObj.provider.dimensionId) {
                        throw new IllegalArgumentException("The selected Build dimension is not the player's current dimension.");
                    }
                    KOMEBuildService.create(data, message.text, tile, message.dimension, message.x, message.y,
                        message.z, actorId, actorName, actorFaction, message.populationFaction,
                        message.offensiveHalfHours, message.defensiveHalfHours, System.currentTimeMillis());
                } else {
                    KOMEPlayerBuild build = data.getBuild(message.buildId);
                    if (build == null || !tile.equals(build.tileId)) throw new IllegalArgumentException("Unknown Build in this tile.");
                    if ("contribute".equals(action)) {
                        KOMEBuildService.addSubmission(data, build, actorId, actorName, actorFaction,
                            message.offensiveHalfHours, message.defensiveHalfHours,
                            KOMEBuildService.isManager(build, actorId), System.currentTimeMillis());
                    } else if ("approve".equals(action) || "reject".equals(action)) {
                        require(KOMEBuildService.decideSubmission(data, build, message.contributionId,
                            actorId, actorName, admin, "approve".equals(action), message.text, System.currentTimeMillis()));
                    } else if ("remove".equals(action)) {
                        require(KOMEBuildService.removeApprovedContribution(data, build, message.contributionId,
                            actorId, actorName, admin, message.text, System.currentTimeMillis()));
                    } else if ("rename".equals(action)) {
                        require(KOMEBuildService.rename(data, build, actorId, admin, message.text, System.currentTimeMillis()));
                    } else if ("delete".equals(action)) {
                        require(KOMEBuildService.deleteBuild(data, build, actorId, actorName, admin,
                            message.text, System.currentTimeMillis()));
                    } else if ("destroy".equals(action)) {
                        require(KOMEBuildService.destroyEnemyBuild(data, build, actorId, actorName,
                            actorFaction, admin, System.currentTimeMillis()));
                    } else {
                        throw new IllegalArgumentException("Unknown Build action.");
                    }
                }
            } catch (RuntimeException error) {
                player.addChatMessage(new ChatComponentText("Build action rejected: " + error.getMessage()));
            }
            data.syncConquestTiles(player);
            String focus = "delete".equals(action) || "destroy".equals(action) ? "" : safe(message.buildId);
            KOMEPacketConquestOpenCapture.sendTileCommand(player, tile, focus);
            return null;
        }

        private static void require(KOMEBuildService.Decision decision) {
            if (decision == null || !decision.allowed) {
                throw new IllegalArgumentException(decision == null ? "Build action failed." : decision.reason);
            }
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
