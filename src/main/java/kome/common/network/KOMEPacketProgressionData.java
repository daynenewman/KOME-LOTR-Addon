package kome.common.network;

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
    public List<kome.common.data.KOMEProgressionAbilityPolicy.Outcome> abilities = java.util.Collections.emptyList();
    public KOMEPacketProgressionData abilities(List<kome.common.data.KOMEProgressionAbilityPolicy.Outcome> rows) {
        abilities=java.util.Collections.unmodifiableList(new ArrayList<>(rows));
        return this;
    }
    public java.util.UUID playerId = new java.util.UUID(0L, 0L);
    public String canonicalRankKey = "wanderer";
    public long revision;
    public int dimension;
    public boolean progressionEnabled = true;

    public KOMEPacketProgressionData authority(java.util.UUID id, String rankKey,
            long publication, int worldDimension, boolean enabled) {
        playerId = id;
        canonicalRankKey = rankKey;
        revision = publication;
        dimension = worldDimension;
        progressionEnabled = enabled;
        return this;
    }

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
        KOMEPopulationWire.readHeader(buf);
        playerId = new java.util.UUID(buf.readLong(), buf.readLong());
        canonicalRankKey = KOMEPopulationWire.readText(buf);
        if (kome.common.data.KOMEProgressionRank.forKey(canonicalRankKey) == null)
            throw new IllegalArgumentException("Unknown canonical progression rank");
        revision = buf.readLong();
        dimension = buf.readInt();
        progressionEnabled = buf.readBoolean();
        playerName = KOMEPopulationWire.readText(buf);
        int count = readCount(buf);
        completed = new ArrayList();
        for (int i = 0; i < count; i++) {
            completed.add(KOMEPopulationWire.readText(buf));
        }
        int assignmentCount = readCount(buf);
        assignments = new HashMap();
        for (int i = 0; i < assignmentCount; i++) {
            assignments.put(KOMEPopulationWire.readText(buf), KOMEPopulationWire.readText(buf));
        }
        canonicalSummary=KOMEPopulationWire.readText(buf);findLabel=KOMEPopulationWire.readText(buf);leaveRelationshipType=KOMEPopulationWire.readText(buf);leaveRelationshipLabel=KOMEPopulationWire.readText(buf);leaveRelationshipName=KOMEPopulationWire.readText(buf);
        String factionKey=KOMEPopulationWire.readText(buf);
        String current=KOMEPopulationWire.readText(buf),next=KOMEPopulationWire.readText(buf),promotion=KOMEPopulationWire.readText(buf);
        int requirementCount=readCount(buf);List requirements=new ArrayList();
        for(int i=0;i<requirementCount;i++)requirements.add(readRequirement(buf));
        rankSummary=new KOMEProgressionRankSummary(factionKey,current,next,promotion,requirements,KOMEPopulationWire.readText(buf),KOMEPopulationWire.readText(buf),KOMEPopulationWire.readText(buf));
        int abilityCount=buf.readInt();
        if (abilityCount<0 || abilityCount>kome.common.data.KOMEProgressionPermissionRegistry.Ability.values().length)
            throw new IllegalArgumentException("Invalid ability count");
        List<kome.common.data.KOMEProgressionAbilityPolicy.Outcome> rows=new ArrayList<>();
        java.util.Set<String> ids=new java.util.HashSet<>();
        for (int i=0;i<abilityCount;i++) {
            String id=KOMEPopulationWire.readText(buf);
            if (!ids.add(id)) throw new IllegalArgumentException("Duplicate ability");
            boolean unlocked=buf.readBoolean();
            kome.common.data.KOMEProgressionAbilityPolicy.Availability state=
                kome.common.data.KOMEProgressionAbilityPolicy.Availability.valueOf(KOMEPopulationWire.readText(buf));
            rows.add(new kome.common.data.KOMEProgressionAbilityPolicy.Outcome(id,unlocked,state,
                KOMEPopulationWire.readText(buf),KOMEPopulationWire.readText(buf)));
        }
        abilities(rows);
        KOMEPopulationWire.requireFullyRead(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        KOMEPopulationWire.writeHeader(buf);
        buf.writeLong(playerId.getMostSignificantBits());
        buf.writeLong(playerId.getLeastSignificantBits());
        KOMEPopulationWire.writeText(buf, canonicalRankKey);
        buf.writeLong(revision);
        buf.writeInt(dimension);
        buf.writeBoolean(progressionEnabled);
        KOMEPopulationWire.writeText(buf, playerName);
        buf.writeInt(completed.size());
        for (Object id : completed) {
            KOMEPopulationWire.writeText(buf, String.valueOf(id));
        }
        buf.writeInt(assignments.size());
        for (Object entryObject : assignments.entrySet()) {
            Map.Entry entry = (Map.Entry) entryObject;
            KOMEPopulationWire.writeText(buf, String.valueOf(entry.getKey()));
            KOMEPopulationWire.writeText(buf, String.valueOf(entry.getValue()));
        }
        KOMEPopulationWire.writeText(buf,canonicalSummary);KOMEPopulationWire.writeText(buf,findLabel);KOMEPopulationWire.writeText(buf,leaveRelationshipType);KOMEPopulationWire.writeText(buf,leaveRelationshipLabel);KOMEPopulationWire.writeText(buf,leaveRelationshipName);
        KOMEProgressionRankSummary ranks=rankSummary==null?KOMEProgressionRankSummary.EMPTY:rankSummary;
        KOMEPopulationWire.writeText(buf,ranks.factionKey);KOMEPopulationWire.writeText(buf,ranks.currentRank);KOMEPopulationWire.writeText(buf,ranks.nextRank);KOMEPopulationWire.writeText(buf,ranks.promotionTitle);buf.writeInt(ranks.requirements.size());
        for(KOMEProgressionRankSummary.Requirement requirement:ranks.requirements)writeRequirement(buf,requirement);
        KOMEPopulationWire.writeText(buf,ranks.activityHeading);KOMEPopulationWire.writeText(buf,ranks.activityTitle);KOMEPopulationWire.writeText(buf,ranks.activityObjective);
        buf.writeInt(abilities.size());
        for (kome.common.data.KOMEProgressionAbilityPolicy.Outcome row:abilities) {
            KOMEPopulationWire.writeText(buf,row.abilityId);buf.writeBoolean(row.rankUnlocked);
            KOMEPopulationWire.writeText(buf,row.availability.name());
            KOMEPopulationWire.writeText(buf,row.reason);KOMEPopulationWire.writeText(buf,row.targetKey);
        }
    }

    private static int readCount(ByteBuf buf) {
        int count = buf.readInt();
        if (count < 0 || count > 256) throw new IllegalArgumentException("Invalid progression row count");
        return count;
    }

    /** Detach queued publication from the network message and its mutable collections. */
    public KOMEPacketProgressionData copy() {
        return new KOMEPacketProgressionData(playerName, new ArrayList(completed),
            new HashMap(assignments), canonicalSummary, findLabel, leaveRelationshipType,
            leaveRelationshipLabel, leaveRelationshipName, rankSummary)
            .authority(playerId, canonicalRankKey, revision, dimension, progressionEnabled).abilities(abilities);
    }
    private static KOMEProgressionRankSummary.Requirement readRequirement(ByteBuf buf) {
        String label=KOMEPopulationWire.readText(buf);int current=buf.readInt(),required=buf.readInt();boolean complete=buf.readBoolean();
        int childCount=readCount(buf);List<KOMEProgressionRankSummary.Requirement> children=new ArrayList<KOMEProgressionRankSummary.Requirement>();
        for(int i=0;i<childCount;i++)children.add(new KOMEProgressionRankSummary.Requirement(
            KOMEPopulationWire.readText(buf),buf.readInt(),buf.readInt(),buf.readBoolean()));
        return new KOMEProgressionRankSummary.Requirement(label,current,required,complete,children);
    }

    private static void writeRequirement(ByteBuf buf,KOMEProgressionRankSummary.Requirement requirement) {
        KOMEPopulationWire.writeText(buf,requirement.label);buf.writeInt(requirement.current);buf.writeInt(requirement.required);buf.writeBoolean(requirement.complete);
        buf.writeInt(requirement.children.size());
        for(KOMEProgressionRankSummary.Requirement child:requirement.children){KOMEPopulationWire.writeText(buf,child.label);buf.writeInt(child.current);buf.writeInt(child.required);buf.writeBoolean(child.complete);}
    }

    public static class Handler implements IMessageHandler<KOMEPacketProgressionData, IMessage> {
        @Override
        public IMessage onMessage(KOMEPacketProgressionData message, MessageContext ctx) {
            KOMEAddon.proxy.acceptProgressionData(message, ctx.getClientHandler());
            return null;
        }
    }
}
