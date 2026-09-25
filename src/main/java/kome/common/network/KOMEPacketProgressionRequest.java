package kome.common.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import kome.common.KOMEReflection;
import kome.common.data.KOMEPlayerProgression;
import kome.common.data.KOMEProgressionAchievement;
import kome.common.data.KOMEProgressionAutoCompleter;
import kome.common.data.KOMEProgressionSummary;
import kome.common.data.KOMEProgressionRankSummary;
import kome.common.data.KOMEWorldData;
import kome.common.data.KOMEVisualLocationService;
import lotr.common.LOTRLevelData;
import lotr.common.fac.LOTRFaction;
import net.minecraft.entity.player.EntityPlayerMP;

import java.util.ArrayList;
import java.util.List;

public class KOMEPacketProgressionRequest implements IMessage {
    @Override
    public void fromBytes(ByteBuf buf) {
    }

    @Override
    public void toBytes(ByteBuf buf) {
    }

    public static class Handler implements IMessageHandler<KOMEPacketProgressionRequest, IMessage> {
        @Override
        public IMessage onMessage(KOMEPacketProgressionRequest message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            KOMEWorldData data = KOMEWorldData.get(KOMEReflection.getWorld(player));
            // Reconcile an existing progression before projecting the book. Do not
            // create a record merely because a read-only GUI was opened; login and
            // the normal server reconciliation pass handle first-time records.
            if (data.progressions.containsKey(KOMEReflection.getEntityUUID(player)))
                KOMEProgressionAutoCompleter.runForPlayer(player, false);
            KOMEPlayerProgression progression = data.progressionForInspection(KOMEReflection.getEntityUUID(player));
            List completed = new ArrayList();
            for (KOMEProgressionAchievement achievement : KOMEProgressionAchievement.ALL) {
                if (progression.isCompleted(achievement)) {
                    completed.add(achievement.id);
                }
            }
            LOTRFaction pledge=LOTRLevelData.getData(player).getPledgeFaction();String pledgeName=pledge!=null&&pledge.isPlayableAlignmentFaction()?pledge.factionName():"";
            double alignment=pledge==null?0D:LOTRLevelData.getData(player).getAlignment(pledge);
            KOMEPacketHandler.network.sendTo(new KOMEPacketProgressionData(player.getCommandSenderName(), completed, progression.getAssignments(), KOMEProgressionSummary.text(progression,pledgeName), KOMEProgressionSummary.findLabel(progression), KOMEProgressionSummary.leaveRelationshipType(progression), KOMEProgressionSummary.leaveRelationshipLabel(progression), KOMEProgressionSummary.leaveRelationshipName(progression), KOMEProgressionRankSummary.project(progression,alignment)), player);
            KOMEVisualLocationService.syncIfChanged(player, progression, false);
            return null;
        }
    }
}
