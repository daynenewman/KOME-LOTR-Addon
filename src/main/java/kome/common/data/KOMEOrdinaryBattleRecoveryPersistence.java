package kome.common.data;

import java.util.*;
import net.minecraft.nbt.*;
import static kome.common.data.KOMEOrdinaryBattleRecoveryRegistry.*;

/** Strict codec for the single ConflictData v3 recovery registry. */
final class KOMEOrdinaryBattleRecoveryPersistence {
    private KOMEOrdinaryBattleRecoveryPersistence() { }
    static NBTTagCompound write(KOMEOrdinaryBattleRecoveryRegistry registry) {
        // Validate the complete graph before publishing any NBT into the world root.
        KOMEOrdinaryBattleRecoveryRegistry.restore(registry.targets(), registry.episodes());
        NBTTagCompound root = new NBTTagCompound(); NBTTagList targets = new NBTTagList();
        for (Target t : registry.targets().values()) {
            NBTTagCompound row = new NBTTagCompound(); s(row,"TargetId",t.id); s(row,"Component",t.component);
            s(row,"PhysicalId",t.physicalId); row.setBoolean("Terminal",t.terminal);
            row.setTag("Aliases", uuids(t.aliases)); targets.appendTag(row);
        }
        root.setTag("Targets",targets); NBTTagList episodes = new NBTTagList();
        for (Episode e : registry.episodes().values()) {
            NBTTagCompound row = new NBTTagCompound(); s(row,"ConflictId",e.conflictId); s(row,"TileId",e.tileId);
            s(row,"EpisodeId",e.episodeId); row.setLong("StartedAtMillis",e.startedAt); optional(row,"EndedAtMillis",e.endedAt);
            s(row,"Coverage",e.coverage); row.setBoolean("ActualHostileCombatObserved",e.actualHostileCombatObserved);
            NBTTagList people = new NBTTagList();
            for (Participant p : e.participants.values()) {
                NBTTagCompound n = new NBTTagCompound(); s(n,"ParticipantId",p.id); s(n,"CompanyId",p.companyId);
                s(n,"FactionId",p.factionId); n.setLong("AdmittedAtMillis",p.admittedAt); s(n,"Disposition",p.disposition);
                optional(n,"ExitedAtMillis",p.exitedAt); s(n,"ExitReason",p.exitReason); n.setTag("MountIds",uuids(p.mountIds)); people.appendTag(n);
            }
            row.setTag("Participants",people);
            if (e.intent != null) {
                Intent i = e.intent; NBTTagCompound n = new NBTTagCompound();
                s(n,"ConflictId",i.conflictId); s(n,"EpisodeId",i.episodeId); n.setLong("ExpectedRevision",i.expectedRevision);
                s(n,"ExpectedState",i.expectedState); s(n,"Kind",i.kind); s(n,"ExpectedOwner",i.expectedOwner); s(n,"Winner",i.winner);
                n.setFloat("RecoveryAmount",i.recoveryAmount); s(n,"Stage",i.stage); optional(n,"ClosedAtMillis",i.closedAt);
                row.setTag("ResolutionIntent",n);
            }
            NBTTagList grants = new NBTTagList();
            for (Grant g : e.grants.values()) {
                NBTTagCompound n = new NBTTagCompound(); s(n,"ParticipantId",g.participantId); s(n,"TargetId",g.targetId);
                s(n,"Component",g.component); n.setFloat("Amount",g.amount); s(n,"State",g.state); s(n,"TerminalReason",g.terminalReason);
                if (g.transition != null) {
                    Transition t = g.transition; NBTTagCompound h = new NBTTagCompound(); s(h,"Token",t.token); s(h,"PhysicalId",t.physicalId);
                    h.setLong("SourceRevision",t.sourceRevision); h.setFloat("SourceHealth",t.sourceHealth);
                    h.setFloat("TargetHealth",t.targetHealth); h.setFloat("MaxHealth",t.maxHealth); n.setTag("Transition",h);
                }
                grants.appendTag(n);
            }
            row.setTag("Grants",grants); episodes.appendTag(row);
        }
        root.setTag("Episodes",episodes); return root;
    }
    static KOMEOrdinaryBattleRecoveryRegistry read(NBTTagCompound root) {
        Map<UUID,Target> targets = new TreeMap<UUID,Target>(); Map<String,Episode> episodes = new TreeMap<String,Episode>();
        NBTTagList rows = list(root,"Targets");
        for (int j=0;j<rows.tagCount();j++) {
            NBTTagCompound n = rows.getCompoundTagAt(j); Target t = new Target(uuid(n,"TargetId"),
                Component.valueOf(str(n,"Component")),uuid(n,"PhysicalId"),uuidSet(n,"Aliases"),bool(n,"Terminal"));
            require(targets.put(t.id,t)==null,"Duplicate recovery target identity");
        }
        rows = list(root,"Episodes");
        for (int j=0;j<rows.tagCount();j++) {
            NBTTagCompound n = rows.getCompoundTagAt(j); Map<UUID,Participant> people = new TreeMap<UUID,Participant>();
            Map<String,Grant> grants = new TreeMap<String,Grant>(); NBTTagList participants = list(n,"Participants");
            for (int k=0;k<participants.tagCount();k++) {
                NBTTagCompound p = participants.getCompoundTagAt(k);
                Participant person = new Participant(uuid(p,"ParticipantId"),str(p,"CompanyId"),str(p,"FactionId"),
                    number(p,"AdmittedAtMillis"),Disposition.valueOf(str(p,"Disposition")),optional(p,"ExitedAtMillis"),
                    str(p,"ExitReason"),uuidSet(p,"MountIds"));
                require(people.put(person.id,person)==null,"Duplicate episode participant");
            }
            Intent intent = null;
            if(n.hasKey("ResolutionIntent")) {
                NBTTagCompound i = compound(n,"ResolutionIntent"); intent = new Intent(str(i,"ConflictId"),str(i,"EpisodeId"),
                    number(i,"ExpectedRevision"),KOMEConflictRecord.State.valueOf(str(i,"ExpectedState")),ResolutionKind.valueOf(str(i,"Kind")),
                    str(i,"ExpectedOwner"),str(i,"Winner"),decimal(i,"RecoveryAmount"),Stage.valueOf(str(i,"Stage")),optional(i,"ClosedAtMillis"));
            }
            NBTTagList grantRows = list(n,"Grants");
            for(int k=0;k<grantRows.tagCount();k++) {
                NBTTagCompound g = grantRows.getCompoundTagAt(k); Transition transition = null;
                if(g.hasKey("Transition")) {
                    NBTTagCompound h = compound(g,"Transition"); transition = new Transition(uuid(h,"Token"),uuid(h,"PhysicalId"),
                        number(h,"SourceRevision"),decimal(h,"SourceHealth"),decimal(h,"TargetHealth"),decimal(h,"MaxHealth"));
                }
                Grant grant = new Grant(uuid(g,"ParticipantId"),uuid(g,"TargetId"),Component.valueOf(str(g,"Component")),
                    decimal(g,"Amount"),GrantState.valueOf(str(g,"State")),transition,str(g,"TerminalReason"));
                require(grants.put(grant.key(),grant)==null,"Duplicate component recovery grant");
            }
            Episode e = new Episode(str(n,"ConflictId"),str(n,"TileId"),str(n,"EpisodeId"),number(n,"StartedAtMillis"),optional(n,"EndedAtMillis"),
                Coverage.valueOf(str(n,"Coverage")),bool(n,"ActualHostileCombatObserved"),people,intent,grants);
            require(episodes.put(e.episodeId,e)==null,"Duplicate recovery episode identity");
        }
        return KOMEOrdinaryBattleRecoveryRegistry.restore(targets,episodes);
    }
    private static NBTTagList uuids(Set<UUID> ids) {
        NBTTagList list = new NBTTagList(); for(UUID id : ids) { NBTTagCompound n=new NBTTagCompound(); s(n,"Id",id); list.appendTag(n); } return list;
    }
    private static Set<UUID> uuidSet(NBTTagCompound n,String key) {
        Set<UUID> result=new TreeSet<UUID>(); NBTTagList rows=list(n,key);
        for(int i=0;i<rows.tagCount();i++) require(result.add(uuid(rows.getCompoundTagAt(i),"Id")),"Duplicate UUID in "+key); return result;
    }
    private static void s(NBTTagCompound n,String key,Object value) { n.setString(key,value.toString()); }
    private static void optional(NBTTagCompound n,String key,Long value) { if(value!=null)n.setLong(key,value); }
    private static void type(NBTTagCompound n,String key,int type) { require(n.hasKey(key,type),"Missing or invalid recovery "+key); }
    private static String str(NBTTagCompound n,String key) { type(n,key,8); return n.getString(key); }
    private static UUID uuid(NBTTagCompound n,String key) { String raw=str(n,key); UUID id=UUID.fromString(raw); require(raw.equals(id.toString()),"Noncanonical UUID"); return id; }
    private static long number(NBTTagCompound n,String key) { type(n,key,4); return n.getLong(key); }
    private static Long optional(NBTTagCompound n,String key) { return n.hasKey(key)?Long.valueOf(number(n,key)):null; }
    private static float decimal(NBTTagCompound n,String key) { type(n,key,5); return n.getFloat(key); }
    private static boolean bool(NBTTagCompound n,String key) { type(n,key,1); byte value=n.getByte(key); require(value==0||value==1,"Invalid boolean"); return value==1; }
    private static NBTTagCompound compound(NBTTagCompound n,String key) { type(n,key,10); return n.getCompoundTag(key); }
    private static NBTTagList list(NBTTagCompound n,String key) {
        type(n,key,9); NBTTagList value=(NBTTagList)n.getTag(key);
        require(value.tagCount()==0||value.func_150303_d()==10,"Recovery list is not compounds: "+key); return value;
    }
}
