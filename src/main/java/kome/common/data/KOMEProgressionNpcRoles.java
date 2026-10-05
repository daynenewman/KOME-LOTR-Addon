package kome.common.data;

import java.util.*;
import kome.common.KOMEReflection;
import lotr.common.entity.npc.LOTREntityNPC;

/**
 * Canonical progression state owns the leases; this is its indexed projection.
 * If active KOME gameplay requires a particular NPC UUID, give it a role lease.
 * No vanilla or LOTR persistence bit is acquired or released here.
 */
public final class KOMEProgressionNpcRoles {
    private KOMEProgressionNpcRoles() {}
    public static void rebuild(KOMEWorldData world) {
        if(world==null)return;
        world.progressionNpcRoleLeases.clear();
        for(Map.Entry<UUID,KOMEPlayerProgression> row:world.progressions.entrySet()) syncPlayer(world,row.getKey());
    }
    public static void syncPlayer(KOMEWorldData world,UUID player) {
        if(world==null||player==null)return;
        for(Set<KOMEProgressionNpcRoleLease> roles:world.progressionNpcRoleLeases.values())
            for(Iterator<KOMEProgressionNpcRoleLease> it=roles.iterator();it.hasNext();)if(player.equals(it.next().player))it.remove();
        KOMEPlayerProgression progression=world.progressions.get(player);
        if(progression==null){prune(world);return;}
        KOMESerfKnightProgression state=progression.getSerfKnightProgression();
        add(world,player,state.getSerfdomMaster(),KOMEProgressionNpcRoleLease.Role.SERFDOM_MASTER,state.getSerfdomMaster().entityUuid);
        add(world,player,state.getLiege(),KOMEProgressionNpcRoleLease.Role.LIEGE,state.getLiege().entityUuid);
        if("courier".equals(state.getActiveAssignmentKind())) {
            KOMESerfCourierAssignment courier=KOMESerfCourierAssignment.readFromNBT(state.getDuty(KOMESerfKnightDutyType.COURIER).getAssignmentData());
            if(courier!=null&&courier.stage==KOMESerfCourierAssignment.Stage.OUTBOUND&&!courier.confirmedRecipientDeath)
                add(world,player,courier.recipient,KOMEProgressionNpcRoleLease.Role.COURIER_RECIPIENT,courier.token);
        }
        KOMESerfKnightTrialAssignment trial=state.getTrialAssignment();
        if(trial!=null&&trial.stage==KOMESerfKnightTrialAssignment.Stage.ACTIVE&&!state.isTrialCompleted()) {
            if("escort".equals(trial.trialId))add(world,player,ref(trial,"EscortTarget"),KOMEProgressionNpcRoleLease.Role.ESCORT_CHARGE,trial.assignmentToken);
            if("defense".equals(trial.trialId))add(world,player,ref(trial,"DefenseObjective"),KOMEProgressionNpcRoleLease.Role.DEFENSE_PROTECTED,trial.assignmentToken);
        }
        KOMEKnightCommission commission=progression.getKnightService().assignment();
        if(progression.getCanonicalRank()==KOMEProgressionRank.KNIGHT&&commission!=null&&commission.live())for(KOMEKnightCommission.Actor actor:commission.actors)if(!actor.dead)
            add(world,player,new KOMEProgressionNpcRef(actor.id,commission.place,actor.role==KOMEKnightCommission.Role.ENEMY?commission.enemyFaction:commission.faction,commission.dimension,actor.x,actor.y,actor.z),actor.role==KOMEKnightCommission.Role.ENEMY?KOMEProgressionNpcRoleLease.Role.COMMISSION_ENEMY:KOMEProgressionNpcRoleLease.Role.COMMISSION_BENEFICIARY,commission.token);
        KOMELordshipTrial lordship=progression.getLordship().assignment();
        if(progression.getCanonicalRank()==KOMEProgressionRank.KNIGHT&&lordship!=null&&!lordship.forceReleased&&(lordship.objective.stage==KOMEKnightCommission.Stage.ACTIVE||lordship.ready()))for(KOMEKnightCommission.Actor actor:lordship.objective.actors)if(!actor.dead&&(!lordship.ready()||actor.role==KOMEKnightCommission.Role.GUARD)){
            KOMEKnightCommission a=lordship.objective;
            add(world,player,new KOMEProgressionNpcRef(actor.id,a.place,actor.role==KOMEKnightCommission.Role.ENEMY?a.enemyFaction:a.faction,a.dimension,actor.x,actor.y,actor.z),KOMEProgressionNpcRoleLease.Role.LORDSHIP_GUARD,a.token);
        }
        prune(world);
    }
    private static void prune(KOMEWorldData world){for(Iterator<Map.Entry<UUID,Set<KOMEProgressionNpcRoleLease>>> it=world.progressionNpcRoleLeases.entrySet().iterator();it.hasNext();)if(it.next().getValue().isEmpty())it.remove();}
    private static KOMEProgressionNpcRef ref(KOMESerfKnightTrialAssignment trial,String key){return trial.data.hasKey(key,10)?KOMEProgressionNpcRef.readFromNBT(trial.data.getCompoundTag(key)):KOMEProgressionNpcRef.EMPTY;}
    private static void add(KOMEWorldData world,UUID player,KOMEProgressionNpcRef ref,KOMEProgressionNpcRoleLease.Role role,String token){
        if(ref==null||!ref.isSet())return;
        try { UUID npc=UUID.fromString(ref.entityUuid);Set<KOMEProgressionNpcRoleLease> roles=world.progressionNpcRoleLeases.get(npc);if(roles==null){roles=new HashSet<KOMEProgressionNpcRoleLease>();world.progressionNpcRoleLeases.put(npc,roles);}roles.add(new KOMEProgressionNpcRoleLease(npc,player,role,token)); }
        catch(IllegalArgumentException ignored) { }
    }
    public static boolean protects(KOMEWorldData world,UUID npc){Set<KOMEProgressionNpcRoleLease> roles=world==null||npc==null?null:world.progressionNpcRoleLeases.get(npc);return roles!=null&&!roles.isEmpty();}
    public static boolean availableForNewRole(KOMEWorldData world,UUID npc){return world!=null&&npc!=null&&!protects(world,npc);}
    public static boolean preventDespawn(LOTREntityNPC npc){return npc!=null&&!npc.worldObj.isRemote&&protects(KOMEWorldData.get(npc.worldObj),KOMEReflection.getEntityUUID(npc));}
}
