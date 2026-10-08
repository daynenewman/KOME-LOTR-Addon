package kome.common.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kome.common.KOMEReflection;
import kome.common.network.KOMEPacketHandler;
import kome.common.network.KOMEPacketVisualMarkers;
import lotr.common.entity.npc.LOTREntityNPC;
import net.minecraft.entity.player.EntityPlayerMP;

/** Server authority for player-specific relationship and temporary objective presentation. */
public final class KOMEVisualLocationService {
    private static final double LOCATION_UPDATE_DISTANCE_SQ = 16.0D;
    private static final Map<UUID, String> LAST_SENT = new HashMap<UUID, String>();

    private KOMEVisualLocationService() { }

    public static List<KOMEVisualMarker> markersFor(KOMEPlayerProgression progression) {
        return markersFor(progression, null);
    }

    static List<KOMEVisualMarker> markersFor(KOMEPlayerProgression progression, KOMEWorldData worldData) {
        return markersFor(progression,worldData,150D);
    }

    static List<KOMEVisualMarker> markersFor(KOMEPlayerProgression progression,KOMEWorldData worldData,double alignment) {
        List<KOMEVisualMarker> markers = new ArrayList<KOMEVisualMarker>();
        if (progression == null) return markers;
        KOMESerfKnightProgression state = progression.getSerfKnightProgression();
        KOMEVisualMarker relationship = relationshipMarker(progression, state, worldData,alignment);
        if (relationship != null) markers.add(relationship);
        if(state.isTrialCompleted()&&!state.hasPartingGift()&&KOMEPartingGiftService.giftMaster(state).isSet())
            markers.add(relationship(KOMEVisualMarker.Role.MASTER_GIFT,KOMEPartingGiftService.giftMaster(state)));
        KOMESerfKnightTrialAssignment standing=state.getTrialAssignment();
        if(!state.isTrialCompleted()&&standing!=null&&standing.stage!=KOMESerfKnightTrialAssignment.Stage.FAILED){
            if("escort".equals(standing.trialId)&&KOMESerfKnightEscortService.hasDestination(standing)){
                markers.add(new KOMEVisualMarker(KOMEVisualMarker.Role.ESCORT,"",KOMESerfKnightEscortService.destinationName(standing),"",standing.liege.dimension,standing.data.getDouble(KOMESerfKnightEscortService.DEST_X),0,standing.data.getDouble(KOMESerfKnightEscortService.DEST_Z)));
                KOMEProgressionNpcRef charge=KOMEProgressionNpcRef.readFromNBT(standing.data.getCompoundTag("EscortTarget"));
                if(charge.isSet()&&standing.stage==KOMESerfKnightTrialAssignment.Stage.ACTIVE)markers.add(new KOMEVisualMarker(KOMEVisualMarker.Role.ESCORT,charge.entityUuid,charge.displayName,"",charge.dimension,charge.x,charge.y,charge.z));
            }
            if("defense".equals(standing.trialId)){
                KOMEProgressionNpcRef beneficiary=KOMEProgressionNpcRef.readFromNBT(standing.data.getCompoundTag(KOMESerfKnightDefenseService.OBJECTIVE));
                if(beneficiary.isSet())markers.add(new KOMEVisualMarker(KOMEVisualMarker.Role.DEFENSE,beneficiary.entityUuid,beneficiary.displayName,"",beneficiary.dimension,beneficiary.x,beneficiary.y,beneficiary.z));
                for(String id:KOMESerfKnightDefenseService.enemyIds(standing))if(!KOMESerfKnightDefenseService.deadIds(standing).contains(id))
                    markers.add(new KOMEVisualMarker(KOMEVisualMarker.Role.ENCOUNTER_ENEMY,id,"Attacker","",standing.liege.dimension,0,0,0));
            }
        }
        if ("courier".equals(state.getActiveAssignmentKind())) {
            KOMESerfCourierAssignment courier = KOMESerfCourierAssignment.readFromNBT(
                state.getDuty(KOMESerfKnightDutyType.COURIER).getAssignmentData());
            if (courier != null && courier.stage == KOMESerfCourierAssignment.Stage.OUTBOUND && !courier.confirmedRecipientDeath) {
                KOMEProgressionNpcRef recipient = courier.recipient;
                markers.add(recipient.isSet()
                    ? new KOMEVisualMarker(KOMEVisualMarker.Role.COURIER, recipient.entityUuid,
                        recipient.displayName, "", recipient.dimension,
                        recipient.x, recipient.y, recipient.z)
                    : new KOMEVisualMarker(KOMEVisualMarker.Role.COURIER, "",
                        "Deliver the message", "", courier.dimension,
                        courier.destinationX, 0.0D, courier.destinationZ));
            }
        }
        KOMEVisualMarker recovery = KOMESerfKnightRecoveryService.searchMarker(state.getTrialAssignment());
        if (recovery != null) markers.add(recovery);
        KOMEKnightCommission commission=progression.getKnightService().assignment();
        if(commission!=null&&commission.live()&&commission.stage!=KOMEKnightCommission.Stage.READY_TO_REPORT)
            markers.add(new KOMEVisualMarker(KOMEVisualMarker.Role.COMMISSION,"",KOMEKnightCommissionPresentation.title(commission.type),"",commission.dimension,commission.type==KOMEKnightCommission.Type.DANGEROUS_ESCORT?commission.destinationX:commission.x,0,commission.type==KOMEKnightCommission.Type.DANGEROUS_ESCORT?commission.destinationZ:commission.z));
        if(commission!=null&&commission.type==KOMEKnightCommission.Type.DANGEROUS_ESCORT&&commission.stage==KOMEKnightCommission.Stage.ACTIVE&&!commission.threatResolved)
            markers.add(new KOMEVisualMarker(KOMEVisualMarker.Role.COMMISSION,"","Repel the raiders","",commission.dimension,commission.x,0,commission.z));
        KOMELordshipTrial trial=progression.getLordship().assignment();
        if(trial!=null&&trial.objective.stage==KOMEKnightCommission.Stage.ACTIVE){
            KOMEKnightCommission a=trial.objective;
            markers.add(new KOMEVisualMarker(KOMEVisualMarker.Role.COMMISSION,"",KOMELordshipTrialPresentation.title(trial),"",a.dimension,a.type==KOMEKnightCommission.Type.DANGEROUS_ESCORT?a.destinationX:a.x,0,a.type==KOMEKnightCommission.Type.DANGEROUS_ESCORT?a.destinationZ:a.z));
            if(a.type==KOMEKnightCommission.Type.DANGEROUS_ESCORT&&!a.threatResolved)markers.add(new KOMEVisualMarker(KOMEVisualMarker.Role.COMMISSION,"","Repel the incursion","",a.dimension,a.x,0,a.z));
        }
        KOMEKnightCommission field=trial==null?commission:trial.objective;
        if(field!=null&&field.stage==KOMEKnightCommission.Stage.ACTIVE)for(KOMEKnightCommission.Actor actor:field.actors)
            if(actor.role==KOMEKnightCommission.Role.ENEMY&&!actor.dead)
                markers.add(new KOMEVisualMarker(KOMEVisualMarker.Role.ENCOUNTER_ENEMY,actor.id,"Attacker","",field.dimension,actor.x,actor.y,actor.z));
        if(field!=null&&field.stage==KOMEKnightCommission.Stage.ACTIVE)for(KOMEKnightCommission.Actor actor:field.actors)
            if(!actor.dead&&(actor.role==KOMEKnightCommission.Role.CHARGE||actor.role==KOMEKnightCommission.Role.BENEFICIARY))
                markers.add(new KOMEVisualMarker(field.type==KOMEKnightCommission.Type.DANGEROUS_ESCORT&&actor.role==KOMEKnightCommission.Role.CHARGE?KOMEVisualMarker.Role.ESCORT:KOMEVisualMarker.Role.COMMISSION,actor.id,actor.displayName.isEmpty()?KOMEKnightCommissionPresentation.title(field.type):actor.displayName,"",field.dimension,actor.x,actor.y,actor.z));
        for(KOMEVisualMarker ruler:KOMEProgressionRulerService.markers(worldData)){
            markers.removeIf(m->m.entityUuid.equals(ruler.entityUuid));
            markers.add(ruler);
        }
        return markers;
    }

    private static KOMEVisualMarker relationshipMarker(KOMEPlayerProgression progression,
            KOMESerfKnightProgression state, KOMEWorldData worldData,double alignment) {
        KOMEProgressionRank rank = progression.getCanonicalRank();
        KOMEProgressionNpcRef ref;
        KOMEVisualMarker.Role role;
        if (rank == KOMEProgressionRank.LORD || rank == KOMEProgressionRank.PRINCE) {
            ref = state.getLiege();
            role = KOMEVisualMarker.Role.LORD_LIEGE;
        } else if (rank == KOMEProgressionRank.KNIGHT) {
            ref = state.getLiege();
            role = KOMEVisualMarker.Role.KNIGHT_LIEGE;
        } else if (rank == KOMEProgressionRank.SERF && state.hasLiege()) {
            ref = state.getLiege();
            role = KOMEVisualMarker.Role.KNIGHT_LIEGE;
        } else {
            ref = state.hasMasterRelationship()?state.getSerfdomMaster():KOMEProgressionNpcRef.EMPTY;
            role = KOMEVisualMarker.Role.SERFDOM_MASTER;
        }
        if (ref.isSet() && isActiveRuler(worldData, ref)) role = KOMEVisualMarker.Role.RULER;
        if(!ref.isSet())return null;
        if(worldData!=null&&role!=KOMEVisualMarker.Role.SERFDOM_MASTER&&role!=KOMEVisualMarker.Role.MASTER_GIFT){
            KOMEProgressionNpcRankRecord authority=worldData.progressionNpcRanks.get(UUID.fromString(ref.entityUuid));
            KOMEProgressionNpcRank npcRank=authority==null?KOMEProgressionNpcRank.LORD:authority.rank;
            if(!KOMEProgressionLiegePolicy.accepts(worldData,rank,npcRank,ref.factionKey,ref.factionKey))return null;
        }
        boolean actionable=false;
        if(rank==KOMEProgressionRank.SERF){
            if(ref.hasSameIdentity(state.getLiege()))actionable=!state.isTrialCompleted();
            else actionable=state.isTrialCompleted()&&alignment>=KOMESerfKnightService.REQUIRED_ALIGNMENT||state.hasActiveAssignment()&&state.getTrialId().isEmpty()
                ||KOMESerfKnightService.nextDuty(state)!=null&&KOMESerfKnightService.mayIssueAssignment(state,KOMESerfKnightService.calendarDayNow());
        }else if(rank==KOMEProgressionRank.KNIGHT)actionable=state.hasLiege()&&
            (progression.getKnightService().assignment()!=null||progression.getLordship().assignment()!=null
                ||progression.getKnightService().qualifyingTypes(ref.factionKey).size()<KOMEKnightCommission.Type.values().length
                ||KOMELordshipTrialService.prerequisites(progression,alignment,ref.factionKey));
        return new KOMEVisualMarker(role,ref.entityUuid,role==KOMEVisualMarker.Role.SERFDOM_MASTER?"Master "+ref.displayName:rankedLabel(ref,worldData),"",ref.dimension,ref.x,ref.y,ref.z,actionable);
    }
    static String rankedLabel(KOMEProgressionNpcRef ref,KOMEWorldData data){
        KOMEProgressionNpcRankRecord record=data==null?null:data.progressionNpcRanks.get(UUID.fromString(ref.entityUuid));
        return record==null?ref.displayName:KOMEProgressionNativeAuthority.name(record.factionKey,record.rank,record.displayName);
    }

    private static boolean isActiveRuler(KOMEWorldData worldData, KOMEProgressionNpcRef ref) {
        if (worldData == null || ref == null || !ref.isSet()) return false;
        try {
            return KOMEProgressionNpcRankService.isActivePoliticalNpcKing(
                worldData, UUID.fromString(ref.entityUuid));
        } catch (IllegalArgumentException invalidUuid) {
            return false;
        }
    }

    private static KOMEVisualMarker relationship(KOMEVisualMarker.Role role, KOMEProgressionNpcRef ref) {
        return new KOMEVisualMarker(role, ref.entityUuid, ref.displayName, role.label,
            ref.dimension, ref.x, ref.y, ref.z);
    }

    /** Refreshes immutable canonical NPC references only when a loaded NPC moved materially. */
    public static boolean refreshLoadedLocations(EntityPlayerMP player, KOMEWorldData data,
            KOMEPlayerProgression progression) {
        if (player == null || data == null || progression == null) return false;
        KOMESerfKnightProgression state = progression.getSerfKnightProgression();
        KOMESerfCourierAssignment courier = "courier".equals(state.getActiveAssignmentKind())
            ? KOMESerfCourierAssignment.readFromNBT(state.getDuty(KOMESerfKnightDutyType.COURIER).getAssignmentData())
            : null;
        boolean changed = false;
        for (Object value : player.worldObj.loadedEntityList) {
            if (!(value instanceof LOTREntityNPC)) continue;
            LOTREntityNPC npc = (LOTREntityNPC) value;
            String id = KOMEReflection.getEntityUUID(npc).toString();
            KOMESerfKnightTrialAssignment trial=state.getTrialAssignment();
            KOMEProgressionNpcRef charge=KOMESerfKnightEscortService.target(trial);
            if(trial!=null&&trial.stage==KOMESerfKnightTrialAssignment.Stage.ACTIVE&&id.equals(charge.entityUuid)
                    &&KOMESerfKnightEscortService.isActiveEscort(data,npc)&&materiallyChanged(charge,npc)){
                net.minecraft.nbt.NBTTagCompound updated=(net.minecraft.nbt.NBTTagCompound)trial.data.copy();
                updated.setTag("EscortTarget",KOMEProgressionNpcRankService.referenceOf(npc).writeToNBT());
                state.updateTrialAssignment(trial.withStage(trial.stage,updated));changed=true;
            }
            if (id.equals(state.getSerfdomMaster().entityUuid)
                    && materiallyChanged(state.getSerfdomMaster(), npc)) {
                state.updateSerfdomMasterLocation(KOMEProgressionNpcRankService.referenceOf(npc));
                changed = true;
            }
            if (id.equals(state.getLiege().entityUuid)
                    && materiallyChanged(state.getLiege(), npc)) {
                state.updateLiegeLocation(KOMEProgressionNpcRankService.referenceOf(npc));
                changed = true;
            }
            if(!state.hasPartingGift()&&id.equals(state.getFormerMaster().entityUuid)
                    &&npc.isEntityAlive()&&KOMEProgressionFactionResolver.matches(state.getFormerMaster().factionKey,npc.getFaction())
                    &&KOMEPartingGiftService.giftMaster(state).isSet()&&materiallyChanged(state.getFormerMaster(),npc)){
                state.updateFormerMasterLocation(KOMEProgressionNpcRankService.referenceOf(npc));changed=true;
            }
            if (courier != null && id.equals(courier.recipient.entityUuid)
                    && materiallyChanged(courier.recipient, npc)) {
                courier.recipient = KOMEProgressionNpcRankService.referenceOf(npc);
                state.setDutyAssignmentData(KOMESerfKnightDutyType.COURIER, courier.writeToNBT());
                changed = true;
            }
        }
        if (changed) data.markDirty();
        return changed;
    }

    private static boolean materiallyChanged(KOMEProgressionNpcRef ref, LOTREntityNPC npc) {
        if (ref.dimension != npc.worldObj.provider.dimensionId) return true;
        double dx = ref.x - npc.posX, dy = ref.y - npc.posY, dz = ref.z - npc.posZ;
        return dx * dx + dy * dy + dz * dz >= LOCATION_UPDATE_DISTANCE_SQ
            || !ref.displayName.equals(KOMEProgressionNpcRankService.referenceOf(npc).displayName);
    }

    public static void refreshAndSync(EntityPlayerMP player, KOMEWorldData data,
            KOMEPlayerProgression progression, boolean force) {
        refreshLoadedLocations(player, data, progression);
        syncIfChanged(player, data, progression, force);
    }

    public static void syncIfChanged(EntityPlayerMP player, KOMEPlayerProgression progression, boolean force) {
        syncIfChanged(player, player == null ? null : KOMEWorldData.get(player.worldObj), progression, force);
    }

    static void syncIfChanged(EntityPlayerMP player, KOMEWorldData worldData,
            KOMEPlayerProgression progression, boolean force) {
        if (player == null || progression == null || KOMEPacketHandler.network == null) return;
        lotr.common.fac.LOTRFaction pledge=lotr.common.LOTRLevelData.getData(player).getPledgeFaction();
        double alignment=pledge==null?0D:lotr.common.LOTRLevelData.getData(player).getAlignment(pledge);
        List<KOMEVisualMarker> markers = markersFor(progression, worldData,alignment);
        markers.removeIf(marker->marker.role==KOMEVisualMarker.Role.RULER&&!ownRuler(marker,worldData,pledge));
        // Role leases also include non-actionable witnesses and Trial guards. Hide their
        // native indicators without inventing another visible relationship/action icon.
        for(Map.Entry<UUID,java.util.Set<KOMEProgressionNpcRoleLease>> entry:worldData.progressionNpcRoleLeases.entrySet()){
            boolean relevant=false;for(KOMEProgressionNpcRoleLease lease:entry.getValue())if(lease.player.equals(player.getUniqueID())){relevant=true;break;}
            if(relevant&&markers.stream().noneMatch(m->m.entityUuid.equals(entry.getKey().toString())))
                markers.add(new KOMEVisualMarker(KOMEVisualMarker.Role.PARTICIPANT,entry.getKey().toString(),"","",player.dimension,0,0,0,false));
        }
        // A retained farewell reward is only actionable while serving the matching pledge.
        final lotr.common.fac.LOTRFaction currentPledge=pledge;
        markers.removeIf(marker->marker.role==KOMEVisualMarker.Role.MASTER_GIFT
            &&!KOMEProgressionFactionResolver.matches(KOMEPartingGiftService.giftMaster(progression.getSerfKnightProgression()).factionKey,currentPledge));
        String signature = signature(markers);
        UUID playerId = KOMEReflection.getEntityUUID(player);
        if (!force && signature.equals(LAST_SENT.get(playerId))) return;
        LAST_SENT.put(playerId, signature);
        KOMEPacketHandler.network.sendTo(new KOMEPacketVisualMarkers(markers), player);
    }
    static boolean ownRuler(KOMEVisualMarker marker,KOMEWorldData data,lotr.common.fac.LOTRFaction pledge){
        if(pledge==null||data==null||marker.entityUuid.isEmpty())return false;
        KOMEProgressionNpcRankRecord record=data.progressionNpcRanks.get(UUID.fromString(marker.entityUuid));
        return record!=null&&KOMEProgressionFactionResolver.matches(record.factionKey,pledge);
    }

    static String signature(List<KOMEVisualMarker> markers) {
        StringBuilder value = new StringBuilder();
        if (markers != null) for (KOMEVisualMarker marker : markers)
            value.append(marker.signature()).append('\n');
        return value.toString();
    }

    public static void clearPlayer(UUID playerId) { if (playerId != null) LAST_SENT.remove(playerId); }
    public static void resetSession() { LAST_SENT.clear(); }
}
