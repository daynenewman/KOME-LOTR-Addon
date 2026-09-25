package kome.common.network;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import kome.common.KOMEAddon;
import kome.common.data.KOMEProgressionRankSummary;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KOMEPacketProgressionData implements IMessage {
    public String playerName;
    public List completed = new ArrayList();
    public Map assignments = new HashMap();
    public String canonicalSummary="", findLabel="", leaveRelationshipType="", leaveRelationshipLabel="", leaveRelationshipName="";
    public KOMEProgressionRankSummary rankSummary = KOMEProgressionRankSummary.EMPTY;

    public KOMEPacketProgressionData() {
    }

    public KOMEPacketProgressionData(String playerName, List completed) {
        this(playerName, completed, new HashMap());
    }

    public KOMEPacketProgressionData(String playerName, List completed, Map assignments) {
        this.playerName = playerName;
        this.completed = completed;
        this.assignments = assignments;
    }
    public KOMEPacketProgressionData(String playerName,List completed,Map assignments,String summary,String find,String leaveType,String leaveLabel,String leaveName){this(playerName,completed,assignments);canonicalSummary=summary;findLabel=find;leaveRelationshipType=leaveType;leaveRelationshipLabel=leaveLabel;leaveRelationshipName=leaveName;}
    public KOMEPacketProgressionData(String playerName,List completed,Map assignments,String summary,String find,String leaveType,String leaveLabel,String leaveName,KOMEProgressionRankSummary ranks){this(playerName,completed,assignments,summary,find,leaveType,leaveLabel,leaveName);rankSummary=ranks==null?KOMEProgressionRankSummary.EMPTY:ranks;}

    @Override
    public void fromBytes(ByteBuf buf) {
        playerName = ByteBufUtils.readUTF8String(buf);
        int count = buf.readInt();
        completed = new ArrayList();
        for (int i = 0; i < count; i++) {
            completed.add(ByteBufUtils.readUTF8String(buf));
        }
        int assignmentCount = buf.readInt();
        assignments = new HashMap();
        for (int i = 0; i < assignmentCount; i++) {
            assignments.put(ByteBufUtils.readUTF8String(buf), ByteBufUtils.readUTF8String(buf));
        }
        canonicalSummary=ByteBufUtils.readUTF8String(buf);findLabel=ByteBufUtils.readUTF8String(buf);leaveRelationshipType=ByteBufUtils.readUTF8String(buf);leaveRelationshipLabel=ByteBufUtils.readUTF8String(buf);leaveRelationshipName=ByteBufUtils.readUTF8String(buf);
        String current=ByteBufUtils.readUTF8String(buf),next=ByteBufUtils.readUTF8String(buf),promotion=ByteBufUtils.readUTF8String(buf);
        int requirementCount=buf.readInt();List requirements=new ArrayList();
        for(int i=0;i<requirementCount;i++)requirements.add(new KOMEProgressionRankSummary.Requirement(ByteBufUtils.readUTF8String(buf),buf.readInt(),buf.readInt(),buf.readBoolean()));
        rankSummary=new KOMEProgressionRankSummary(current,next,promotion,requirements,ByteBufUtils.readUTF8String(buf),ByteBufUtils.readUTF8String(buf),ByteBufUtils.readUTF8String(buf));
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, playerName);
        buf.writeInt(completed.size());
        for (Object id : completed) {
            ByteBufUtils.writeUTF8String(buf, String.valueOf(id));
        }
        buf.writeInt(assignments.size());
        for (Object entryObject : assignments.entrySet()) {
            Map.Entry entry = (Map.Entry) entryObject;
            ByteBufUtils.writeUTF8String(buf, String.valueOf(entry.getKey()));
            ByteBufUtils.writeUTF8String(buf, String.valueOf(entry.getValue()));
        }
        ByteBufUtils.writeUTF8String(buf,canonicalSummary);ByteBufUtils.writeUTF8String(buf,findLabel);ByteBufUtils.writeUTF8String(buf,leaveRelationshipType);ByteBufUtils.writeUTF8String(buf,leaveRelationshipLabel);ByteBufUtils.writeUTF8String(buf,leaveRelationshipName);
        KOMEProgressionRankSummary ranks=rankSummary==null?KOMEProgressionRankSummary.EMPTY:rankSummary;
        ByteBufUtils.writeUTF8String(buf,ranks.currentRank);ByteBufUtils.writeUTF8String(buf,ranks.nextRank);ByteBufUtils.writeUTF8String(buf,ranks.promotionTitle);buf.writeInt(ranks.requirements.size());
        for(KOMEProgressionRankSummary.Requirement requirement:ranks.requirements){ByteBufUtils.writeUTF8String(buf,requirement.label);buf.writeInt(requirement.current);buf.writeInt(requirement.required);buf.writeBoolean(requirement.complete);}
        ByteBufUtils.writeUTF8String(buf,ranks.activityHeading);ByteBufUtils.writeUTF8String(buf,ranks.activityTitle);ByteBufUtils.writeUTF8String(buf,ranks.activityObjective);
    }

    public static class Handler implements IMessageHandler<KOMEPacketProgressionData, IMessage> {
        @Override
        public IMessage onMessage(KOMEPacketProgressionData message, MessageContext ctx) {
            KOMEAddon.proxy.updateProgressionData(message.playerName, message.completed, message.assignments, message.canonicalSummary, message.findLabel, message.leaveRelationshipType, message.leaveRelationshipLabel, message.leaveRelationshipName, message.rankSummary);
            return null;
        }
    }
}
