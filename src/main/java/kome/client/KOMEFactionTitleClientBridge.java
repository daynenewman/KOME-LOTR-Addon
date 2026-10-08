package kome.client;

import kome.common.data.KOMEAlliance;
import kome.common.data.KOMEClientData;
import kome.common.data.KOMEDiplomacyRecord;
import kome.common.data.KOMEDiplomacyRelation;
import kome.common.data.KOMEProgressionRankSummary;
import lotr.common.LOTRLevelData;
import lotr.common.fac.LOTRFaction;
import lotr.common.fac.LOTRFactionRelations;
import net.minecraft.client.Minecraft;

/**
 * Client presentation policy for the native LOTR factions screen.
 *
 * Own pledged faction:
 *   KOME progression title only.
 *
 * Foreign faction:
 *   KOME's effective diplomacy relation.
 *
 * Unpledged:
 *   Neutral to every faction.
 */
public final class KOMEFactionTitleClientBridge {
    private static volatile KOMEProgressionRankSummary rankSummary =
        KOMEProgressionRankSummary.EMPTY;

    private KOMEFactionTitleClientBridge() {
    }

    public static void updateRankSummary(
            KOMEProgressionRankSummary summary) {
        rankSummary =
            summary == null
                ? KOMEProgressionRankSummary.EMPTY
                : summary;
    }

    public static void reset() {
        rankSummary=KOMEProgressionRankSummary.EMPTY;
    }

    /**
     * Called from the transformed LOTRGuiFactions rank line.
     *
     * Other-player inspection deliberately preserves vanilla behavior:
     * these labels describe the local player's own faction identity and
     * diplomacy, not another player's.
     */
    public static String resolveFactionStatus(
            LOTRFaction viewedFaction,
            boolean otherPlayer,
            String vanillaTitle) {
        if(otherPlayer||viewedFaction==null) {
            return safe(vanillaTitle);
        }

        if(!KOMEClientConfig.useRankTitlesGlobal()) {
            return safe(vanillaTitle);
        }

        Minecraft minecraft=Minecraft.getMinecraft();

        if(minecraft==null||minecraft.thePlayer==null) {
            return safe(vanillaTitle);
        }

        LOTRFaction pledge=
            LOTRLevelData.getData(
                minecraft.thePlayer)
                .getPledgeFaction();

        String viewedKey=
            KOMEAlliance.normalizeFactionKey(
                viewedFaction.codeName());

        if(pledge==null) {
            return project(
                "",
                viewedKey,
                "",
                KOMEDiplomacyRelation.NEUTRAL);
        }

        String pledgeKey=
            KOMEAlliance.normalizeFactionKey(
                pledge.codeName());

        if(pledgeKey.equals(viewedKey)) {
            KOMEProgressionRankSummary current=rankSummary;

            String ownTitle=
                current!=null
                    &&pledgeKey.equals(
                        KOMEAlliance.normalizeFactionKey(
                            current.factionKey))
                    ?safe(current.currentRank)
                    :"";

            return "Rank: " + project(
                pledgeKey,
                viewedKey,
                ownTitle,
                KOMEDiplomacyRelation.ALLIES);
        }

        KOMEDiplomacyRelation relation=
            cachedRelation(
                pledgeKey,
                viewedKey);

        if(relation==null) {
            relation=
                KOMEDiplomacyRelation.fromLotrRelation(
                    LOTRFactionRelations.getRelations(
                        pledge,
                        viewedFaction));
        }

        return project(
            pledgeKey,
            viewedKey,
            "",
            relation);
    }

    /** Called from transformed LOTRTickHandlerClient.renderAlignmentBar. */
    public static String resolveAlignmentBarTitle(
            String vanillaTitle,
            LOTRFaction viewedFaction,
            boolean otherPlayer) {
        String result = resolveFactionStatus(
            viewedFaction,
            otherPlayer,
            vanillaTitle);
        return result.startsWith("Rank: ") ? result.substring(6) : result;
    }

    static String project(
            String pledgeKey,
            String viewedKey,
            String ownRankTitle,
            KOMEDiplomacyRelation relation) {
        String pledge=
            KOMEAlliance.normalizeFactionKey(
                pledgeKey);

        String viewed=
            KOMEAlliance.normalizeFactionKey(
                viewedKey);

        if(pledge.length()==0) {
            return "Neutral";
        }

        if(pledge.equals(viewed)) {
            String title=safe(ownRankTitle);

            return title.length()==0
                ?"Wanderer"
                :title;
        }

        return relationLabel(relation);
    }

    static String relationLabel(
            KOMEDiplomacyRelation relation) {
        if(relation==null) {
            return "Neutral";
        }

        switch(relation) {
            case MORTAL_ENEMIES:
                return "Mortal Enemy";
            case ENEMIES:
                return "Enemy";
            case FRIENDS:
                return "Friendly";
            case ALLIES:
                return "Allies";
            case NEUTRAL:
            default:
                return "Neutral";
        }
    }

    private static KOMEDiplomacyRelation cachedRelation(
            String first,
            String second) {
        try {
            KOMEDiplomacyRecord record=
                KOMEClientData.INSTANCE
                    .canonicalDiplomacyRecords
                    .get(
                        KOMEDiplomacyRecord.pairKey(
                            first,
                            second));

            return record==null
                ?null
                :record.relation;
        } catch(RuntimeException ignored) {
            return null;
        }
    }

    private static String safe(String value) {
        return value==null?"":value;
    }
}
