package kome.common.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import kome.common.data.KOMEProgressionPermissionRegistry.Ability;
import lotr.common.LOTRLevelData;
import lotr.common.LOTRPlayerData;
import lotr.common.world.map.LOTRWaypoint;
import net.minecraft.entity.player.EntityPlayerMP;

/** Read-only rank eligibility and domain-authored availability. Never saves unlock flags. */
public final class KOMEProgressionAbilityPolicy {
    public enum Availability { LOCKED, AVAILABLE, UNAVAILABLE, CONTEXT_REQUIRED }

    public static final class Outcome {
        public final String abilityId, reason, targetKey;
        public final boolean rankUnlocked;
        public final Availability availability;
        public Outcome(String id, boolean unlocked, Availability availability, String reason, String target) {
            if (Ability.forId(id)==null || availability==null || unlocked==(availability==Availability.LOCKED))
                throw new IllegalArgumentException("Invalid rank ability outcome");
            abilityId=id; rankUnlocked=unlocked; this.availability=availability;
            this.reason=reason==null?"":reason; targetKey=target==null?"":target;
        }
        public String tooltip() {
            Ability ability=Ability.forId(abilityId);
            return ability.title+"\n"+(rankUnlocked?"Standing earned. ":"")+reason+"\n"+ability.description;
        }
        @Override public String toString() { return abilityId+"|"+rankUnlocked+"|"+availability+"|"+reason+"|"+targetKey; }
    }
    private KOMEProgressionAbilityPolicy() { }

    public static KOMEProgressionRank rank(KOMEWorldData data, UUID actor) {
        KOMEPlayerProgression progression=data==null?null:data.progressions.get(actor);
        return progression==null?KOMEProgressionRank.WANDERER:progression.getCanonicalRank();
    }
    public static boolean unlocked(KOMEWorldData data, UUID actor, Ability ability) {
        return actor!=null && ability!=null && rank(data,actor).order>=ability.minimumRank.order;
    }
    public static String rankDenial(KOMEWorldData data, UUID actor, Ability ability) {
        return unlocked(data,actor,ability)?"":"Requires "+ability.minimumRank.displayName+" standing.";
    }
    /** Planning/content filter only: the Warfare registry does not contain specialist rewards yet. */
    public static boolean warfareRankEligible(KOMEWorldData data, UUID actor) {
        return actor!=null && rank(data,actor).order>=KOMEProgressionRank.KNIGHT.order;
    }
    public static String recruitmentAuthorityDenial(KOMEWorldData data, UUID actor, String faction, boolean admin) {
        if (data==null || actor==null) return "Campaign recruitment authority is unavailable.";
        if (admin) return "";
        String denial=rankDenial(data,actor,Ability.CAMPAIGN_RECRUITMENT);
        if (!denial.isEmpty()) return denial+" Ordinary native followers remain available under LOTR rules.";
        String key=KOMEAlliance.normalizeFactionKey(faction);
        if (key.isEmpty() || !key.equals(KOMEAlliance.normalizeFactionKey(data.getPlayerFactionKey(actor))))
            return "Requires a current pledge to the population-owning faction.";
        if (KOMERulerAuthorization.canActAsRuler(data,key,actor)) return "";
        return "Requires Crown recruitment authority. Royal recruitment commissions are not available yet.";
    }
    public static List<Outcome> project(KOMEWorldData data, EntityPlayerMP player) {
        if (data==null || player==null) return Collections.emptyList();
        UUID actor=player.getUniqueID();
        KOMEProgressionRank rank=rank(data,actor);
        lotr.common.fac.LOTRFaction pledge=LOTRLevelData.getData(player).getPledgeFaction();
        String faction=pledge==null || !pledge.isPlayableAlignmentFaction()?"":pledge.codeName();
        List<Outcome> rows=new ArrayList<Outcome>();
        for (Ability ability:Ability.values()) {
            // Only inherited/current and the immediate next standing are shown.
            if (ability.minimumRank.order>rank.order+1) continue;
            if (!unlocked(data,actor,ability)) {
                rows.add(outcome(ability,false,Availability.LOCKED,rankDenial(data,actor,ability),""));
                continue;
            }
            String reason=""; String target="";
            Availability state=Availability.CONTEXT_REQUIRED;
            if (ability==Ability.START_RETURN) {
                LOTRWaypoint waypoint=KOMEWaypointAccessService.originalStartingWaypoint(player);
                if (waypoint==null) { state=Availability.UNAVAILABLE; reason="No verified original starting waypoint is saved."; }
                else {
                    target=waypoint.getCodeName();
                    KOMEWaypointAccessService.Decision decision=KOMEWaypointAccessService.evaluatePlayer(player,waypoint,
                        KOMEWaypointAccessService.hasNativeProgression(player,waypoint));
                    LOTRPlayerData nativeData=LOTRLevelData.getData(player);
                    if (!lotr.common.LOTRConfig.enableFastTravel) { state=Availability.UNAVAILABLE; reason="Fast travel is disabled."; }
                    else if (!decision.finalAllowed) { state=Availability.UNAVAILABLE; reason=decision.reason; }
                    else if (nativeData.getTimeSinceFT()<nativeData.getWaypointFTTime(waypoint,player)) {
                        state=Availability.UNAVAILABLE; reason="Return travel is still on cooldown.";
                    } else if (!nativeData.canFastTravel()) {
                        state=Availability.UNAVAILABLE; reason="You cannot fast travel while under attack.";
                    } else if (player.isPlayerSleeping()) {
                        state=Availability.UNAVAILABLE; reason="Leave your bed before travelling.";
                    } else { state=Availability.AVAILABLE; reason="Return to "+waypoint.getDisplayName()+"."; }
                }
            } else if (faction.isEmpty() && ability!=Ability.COMPANY_COMMAND && ability!=Ability.STRATEGIC_ORDERS) { state=Availability.UNAVAILABLE; reason="Requires a current faction pledge."; }
            else if (ability==Ability.CAMPAIGN_RECRUITMENT) {
                reason=recruitmentAuthorityDenial(data,actor,faction,false);
                if (!reason.isEmpty()) state=Availability.UNAVAILABLE;
                else if (KOMEPopulationService.getAvailablePopulationCenti(data,faction)<=0) {
                    state=Availability.UNAVAILABLE; reason="Insufficient faction population.";
                } else reason="Crown authority present. Choose a legal recruitment tile and unit; population cost and native hire rules are rechecked.";
            } else if (ability==Ability.BATTLE_SERVICE) {
                state=Availability.UNAVAILABLE; reason=KOMEJoinBattleText.forReason(KOMEJoinBattleService.Reason.NO_ACTIVE_CONFLICT);
                for (KOMEConflictRecord conflict:data.getConflictService().records().values()) {
                    if (!conflict.isActive()) continue;
                    KOMEJoinBattleService.Projection decision=KOMEJoinBattleService.INSTANCE.evaluate(data,player,conflict.getTileId());
                    reason=KOMEJoinBattleText.forReason(decision.reason);
                    if (decision.isAllowed()) { state=Availability.CONTEXT_REQUIRED; reason="Eligible company at "+conflict.getTileId()+". Use Join Battle; deployment is rechecked."; break; }
                }
            } else if (ability==Ability.COMPANY_COMMAND || ability==Ability.STRATEGIC_ORDERS) {
                boolean commanded=false;
                for (KOMEArmyCompany company:data.armyCompanies.values())
                    if (KOMECompanyCommandAuthority.canPersonallyCommand(data,company,actor)) { commanded=true; break; }
                if (!commanded) { state=Availability.UNAVAILABLE; reason="Requires a legally owned or delegated company."; }
                else reason=ability==Ability.COMPANY_COMMAND?"You control a company. Its current state and action rules still apply."
                    :"Select a company and destination; route and conflict rules still apply.";
            } else if (ability==Ability.MILITARY_EQUIPMENT) reason="Native faction/alignment and item rules apply. Exceptional equipment is separate.";
            else reason="Choose a legal faction Build or tile. Existing construction, review and time rules apply.";
            rows.add(outcome(ability,true,state,reason,target));
        }
        return Collections.unmodifiableList(rows);
    }
    private static Outcome outcome(Ability ability, boolean unlocked, Availability state, String reason, String target) {
        return new Outcome(ability.id,unlocked,state,reason,target);
    }
}
