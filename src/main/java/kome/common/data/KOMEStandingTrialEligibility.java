package kome.common.data;

/** Shared alignment gate for seeking a new Liege; accepted relationships are retained. */
public final class KOMEStandingTrialEligibility {
    private KOMEStandingTrialEligibility() { }

    /**
     * v36.15 canTradeWith minima for combat unit traders, verified in the shipped jar.
     * Gondor includes Blackroot/Lebennin/Lossarnach (150), Dorwinion includes the
     * human captain (150), and Mordor includes the Orc mercenary captain (150).
     * Their more expensive captains retain their own canTradeWith checks.
     */
    public static int requiredAlignment(String factionKey) {
        String key=KOMEAlliance.normalizeFactionKey(factionKey);
        if("dunedain".equals(key)||"highelves".equals(key)||"lothlorien".equals(key))return 300;
        if("woodelf".equals(key))return 250;
        if("bluemountains".equals(key)||"durinsfolk".equals(key)
                ||"taurethrim".equals(key)||"halftroll".equals(key))return 200;
        return KOMESerfKnightService.REQUIRED_ALIGNMENT;
    }

    public static boolean meetsAlignment(KOMESerfKnightProgression state,double alignment,String factionKey) {
        return state!=null&&(state.hasLiege()||alignment>=requiredAlignment(factionKey));
    }
}
