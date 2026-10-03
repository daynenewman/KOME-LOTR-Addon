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
        List<KOMEVisualMarker> markers = new ArrayList<KOMEVisualMarker>();
        if (progression == null) return markers;
        KOMESerfKnightProgression state = progression.getSerfKnightProgression();
        KOMEVisualMarker relationship = relationshipMarker(progression, state, worldData);
        if (relationship != null) markers.add(relationship);
        if(state.isTrialCompleted()&&!state.hasPartingGift()&&state.getSerfdomMaster().isSet())
            markers.add(relationship(KOMEVisualMarker.Role.MASTER_GIFT,state.getSerfdomMaster()));
        if ("courier".equals(state.getActiveAssignmentKind())) {
            KOMESerfCourierAssignment courier = KOMESerfCourierAssignment.readFromNBT(
                state.getDuty(KOMESerfKnightDutyType.COURIER).getAssignmentData());
            if (courier != null && courier.stage == KOMESerfCourierAssignment.Stage.OUTBOUND && !courier.confirmedRecipientDeath) {
                KOMEProgressionNpcRef recipient = courier.recipient;
                markers.add(recipient.isSet()
                    ? new KOMEVisualMarker(KOMEVisualMarker.Role.COURIER, recipient.entityUuid,
                        recipient.displayName, "Deliver the dispatch", recipient.dimension,
                        recipient.x, recipient.y, recipient.z)
                    : new KOMEVisualMarker(KOMEVisualMarker.Role.COURIER, "",
                        "Courier Destination", courier.destinationName, courier.dimension,
                        courier.destinationX, 0.0D, courier.destinationZ));
            }
        }
        KOMEVisualMarker recovery = KOMESerfKnightRecoveryService.searchMarker(state.getTrialAssignment());
        if (recovery != null) markers.add(recovery);
        KOMEKnightCommission commission=progression.getKnightService().assignment();
        if(commission!=null&&commission.live()&&commission.stage!=KOMEKnightCommission.Stage.READY_TO_REPORT)
            markers.add(new KOMEVisualMarker(KOMEVisualMarker.Role.COMMISSION,"",KOMEKnightCommissionPresentation.title(commission.type),commission.place,commission.dimension,commission.type==KOMEKnightCommission.Type.DANGEROUS_ESCORT?commission.destinationX:commission.x,0,commission.type==KOMEKnightCommission.Type.DANGEROUS_ESCORT?commission.destinationZ:commission.z));
        if(commission!=null&&commission.type==KOMEKnightCommission.Type.DANGEROUS_ESCORT&&commission.stage==KOMEKnightCommission.Stage.ACTIVE&&!commission.threatResolved)
            markers.add(new KOMEVisualMarker(KOMEVisualMarker.Role.COMMISSION,"",KOMEKnightCommissionPresentation.title(KOMEKnightCommission.Type.BORDER_INCURSION),commission.place,commission.dimension,commission.x,0,commission.z));
        KOMELordshipTrial trial=progression.getLordship().assignment();
        if(trial!=null&&trial.objective.stage==KOMEKnightCommission.Stage.ACTIVE){
            KOMEKnightCommission a=trial.objective;
            markers.add(new KOMEVisualMarker(KOMEVisualMarker.Role.COMMISSION,"",KOMELordshipTrialPresentation.title(trial),a.place,a.dimension,a.type==KOMEKnightCommission.Type.DANGEROUS_ESCORT?a.destinationX:a.x,0,a.type==KOMEKnightCommission.Type.DANGEROUS_ESCORT?a.destinationZ:a.z));
            if(a.type==KOMEKnightCommission.Type.DANGEROUS_ESCORT&&!a.threatResolved)markers.add(new KOMEVisualMarker(KOMEVisualMarker.Role.COMMISSION,"","Reported threat",a.place,a.dimension,a.x,0,a.z));
        }
        return markers;
    }

    private static KOMEVisualMarker relationshipMarker(KOMEPlayerProgression progression,
            KOMESerfKnightProgression state, KOMEWorldData worldData) {
        KOMEProgressionRank rank = progression.getCanonicalRank();
        KOMEProgressionNpcRef ref;
        KOMEVisualMarker.Role role;
        if (rank == KOMEProgressionRank.LORD || rank == KOMEProgressionRank.PRINCE) {
            ref = state.hasLiege() ? state.getLiege() : state.getSerfdomMaster();
            role = KOMEVisualMarker.Role.LORD_LIEGE;
        } else if (rank == KOMEProgressionRank.KNIGHT) {
            ref = state.hasLiege() ? state.getLiege() : state.getSerfdomMaster();
            role = KOMEVisualMarker.Role.KNIGHT_LIEGE;
        } else if (rank == KOMEProgressionRank.SERF && state.hasLiege()) {
            ref = state.getLiege();
            role = KOMEVisualMarker.Role.KNIGHT_LIEGE;
        } else {
            ref = state.getSerfdomMaster();
            role = KOMEVisualMarker.Role.SERFDOM_MASTER;
        }
        if (ref.isSet() && isActiveRuler(worldData, ref)) role = KOMEVisualMarker.Role.RULER;
        return ref.isSet() ? relationship(role, ref) : null;
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
            || !ref.displayName.equals(npc.getNPCName());
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
        List<KOMEVisualMarker> markers = markersFor(progression, worldData);
        String signature = signature(markers);
        UUID playerId = KOMEReflection.getEntityUUID(player);
        if (!force && signature.equals(LAST_SENT.get(playerId))) return;
        LAST_SENT.put(playerId, signature);
        KOMEPacketHandler.network.sendTo(new KOMEPacketVisualMarkers(markers), player);
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
