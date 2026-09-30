package kome.common.data;

import lotr.common.LOTRLevelData;
import lotr.common.fac.LOTRFaction;
import net.minecraft.entity.player.EntityPlayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Builds the schema-7 records consumed by the single relationship screen.
 * Retired per-track presentation records are intentionally not emitted.
 */
public class KOMEAllianceRecordBuilder {
    private static final Set<UUID> operatorViewers = Collections.synchronizedSet(new HashSet<UUID>());

    public static void setOperatorView(EntityPlayer viewer, boolean requested) {
        if (viewer == null) {
            return;
        }
        UUID viewerId = kome.common.KOMEReflection.getEntityUUID(viewer);
        boolean operator = viewer.canCommandSenderUseCommand(2, "alliance");
        if (resolveOperatorView(operator, requested)) {
            operatorViewers.add(viewerId);
        } else {
            operatorViewers.remove(viewerId);
        }
    }

    public static void clearOperatorView(EntityPlayer viewer) {
        if (viewer != null) {
            operatorViewers.remove(kome.common.KOMEReflection.getEntityUUID(viewer));
        }
    }

    public static void resetSessionState() {
        operatorViewers.clear();
    }

    static boolean resolveOperatorView(boolean operator, boolean requested) {
        return operator && requested;
    }

    static boolean includeViewerRecord(boolean operatorView, boolean participant) {
        return operatorView || participant;
    }

    public static List build(KOMEWorldData data, EntityPlayer viewer) {
        return buildCanonical(data, viewer);
    }
    private static List buildCanonical(KOMEWorldData data, EntityPlayer viewer) {
        List lines = new ArrayList();
        List relationLines = new ArrayList();
        String viewerFactionKey = getViewerFactionKey(data, viewer);
        boolean operator = viewer == null || viewer.canCommandSenderUseCommand(2, "alliance");
        List<String> factions = KOMEAlliance.allFactionKeys();

        for (int i = 0; i < factions.size(); i++) {
            for (int j = i + 1; j < factions.size(); j++) {
                String first = factions.get(i);
                String second = factions.get(j);
                if (!operator && !first.equals(viewerFactionKey) && !second.equals(viewerFactionKey)) {
                    continue;
                }
                String pairKey = KOMEDiplomacyRecord.pairKey(first, second);
                KOMEDiplomacyRecord record = data.canonicalDiplomacyRecords.get(pairKey);
                boolean pending = record != null && record.pendingTarget != null;
                boolean canAccept = viewer != null && pending
                    && KOMERulerAuthorization.canActAsRuler(
                        data, record.receivingFaction,
                        kome.common.KOMEReflection.getEntityUUID(viewer));
                boolean canCancel = pending && (operator || viewer != null
                    && KOMERulerAuthorization.canActAsRuler(
                        data, record.requestingFaction,
                        kome.common.KOMEReflection.getEntityUUID(viewer)));
                KOMEDiplomacyRelation current =
                    KOMEDiplomacyService.getRelation(data, first, second);
                relationLines.add(
                    "DIPLOMACY_RELATION\t" + pairKey
                        + "\t" + first
                        + "\t" + second
                        + "\t" + current.key
                        + "\t" + flag(pending)
                        + "\t" + (pending ? record.pendingTarget.key : "")
                        + "\t" + (pending ? record.requestingFaction : "")
                        + "\t" + (pending ? record.receivingFaction : "")
                        + "\t" + flag(canAccept)
                        + "\t" + flag(canCancel)
                        + "\t" + (record == null ? "" : record.lastUpdatedBy)
                        + "\t" + (record == null ? 0L : record.updatedAt));
            }
        }

        lines.add("SUMMARY\t" + relationLines.size());
        lines.add("VIEWER\t" + viewerFactionKey + "\t" + getViewerFactionName(data, viewer)
            + "\t" + flag(isViewerKing(data, viewer, viewerFactionKey))
            + "\t" + flag(data.hasFactionKing(viewerFactionKey))
            + "\t" + flag(operator) + "\t" + flag(operator));
        lines.addAll(relationLines);

        if (viewer != null && viewerFactionKey.length() > 0) {
            for (String key : factions) {
                if (key.equals(viewerFactionKey)) {
                    continue;
                }
                KOMEDiplomacyRelation current =
                    KOMEDiplomacyService.getRelation(data, viewerFactionKey, key);
                KOMEDiplomacyRecord record = data.canonicalDiplomacyRecords.get(
                    KOMEDiplomacyRecord.pairKey(viewerFactionKey, key));
                boolean receiver = data.hasFactionKing(key);
                boolean available = receiver && (record == null || record.pendingTarget == null);
                lines.add("DIPLOMACY_REQUEST_OPTION\t" + key
                    + "\t" + displayFaction(key)
                    + "\t" + current.key
                    + "\t" + flag(receiver)
                    + "\t" + flag(available
                        && current.rank() < KOMEDiplomacyRelation.FRIENDS.rank())
                    + "\t" + flag(available
                        && current.rank() < KOMEDiplomacyRelation.ALLIES.rank())
                    + "\t" + (receiver ? (available ? "" : "A request is already pending.")
                        : "That faction has no recognized King to accept diplomacy."));
            }
        }
        return lines;
    }

    public static List build(KOMEWorldData data) {
        return build(data, null);
    }

    private static String flag(boolean value) {
        return value ? "1" : "0";
    }

    private static String displayFaction(String key) {
        return KOMEAlliance.displayFactionName(key);
    }

    private static String getViewerFactionKey(KOMEWorldData data, EntityPlayer viewer) {
        LOTRFaction faction = getViewerFaction(data, viewer);
        return faction == null ? "" : KOMEAlliance.normalizeFactionKey(faction.codeName());
    }

    private static String getViewerFactionName(KOMEWorldData data, EntityPlayer viewer) {
        LOTRFaction faction = getViewerFaction(data, viewer);
        return faction == null ? "No pledged faction" : faction.factionName();
    }

    private static LOTRFaction getViewerFaction(KOMEWorldData data, EntityPlayer viewer) {
        if (viewer == null) {
            return null;
        }
        LOTRFaction pledge = LOTRLevelData.getData(viewer).getPledgeFaction();
        if (pledge != null) {
            return pledge;
        }
        KOMEPlayerProgression progression =
            data.progressions.get(kome.common.KOMEReflection.getEntityUUID(viewer));
        return findFaction(progression == null ? "" : progression.getPledgedLordFaction());
    }

    private static boolean isViewerKing(KOMEWorldData data, EntityPlayer viewer, String factionKey) {
        return viewer != null && factionKey != null && factionKey.length() > 0
            && KOMERulerService.isRuler(data, factionKey, kome.common.KOMEReflection.getEntityUUID(viewer));
    }

    private static LOTRFaction findFaction(String value) {
        LOTRFaction direct = LOTRFaction.forName(value);
        if (direct != null) {
            return direct;
        }
        String normalized = KOMEAlliance.normalizeFactionKey(value);
        for (LOTRFaction faction : LOTRFaction.values()) {
            if (faction != null && faction.isPlayableAlignmentFaction()
                    && (KOMEAlliance.normalizeFactionKey(faction.codeName()).equals(normalized)
                    || KOMEAlliance.normalizeFactionKey(faction.factionName()).equals(normalized))) {
                return faction;
            }
        }
        return null;
    }
}
