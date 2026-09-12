package kome.common.data;

import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;

/** Persisted campaign clock. All transitions are explicit and caller-clocked. */
public final class KOMEWarSeasonState {
    public enum Phase { MAINTENANCE, PRE_WAR, WAR, FINALE, RESET }

    public long seasonId = 1L;
    public Phase phase = Phase.MAINTENANCE;
    public long minimumWarEndMillis = -1L;
    public UUID finaleTriggerActor;
    public String finaleTriggerActorName = "";
    public long finaleTriggerTimeMillis = -1L;
    public long finaleEndTimeMillis = -1L;
    /** PENDING while RESET is in progress, COMPLETE after its explicit completion. */
    public String resetStatus = "NOT_STARTED";

    public boolean isPopulationPayoutEnabled() { return phase == Phase.WAR || phase == Phase.FINALE; }

    public TransitionResult beginPreWar(long now) { return transition(Phase.MAINTENANCE, Phase.PRE_WAR, now, "Pre-War may begin only from Maintenance."); }

    public TransitionResult recordLegalConflict(long now, long minimumWarLengthMillis) {
        if (phase != Phase.MAINTENANCE && phase != Phase.PRE_WAR) return TransitionResult.denied("A legal conflict cannot begin War while the season is " + phase + ".");
        phase = Phase.WAR;
        minimumWarEndMillis = minimumWarLengthMillis < 0L ? -1L : safeAdd(now, minimumWarLengthMillis);
        resetStatus = "NOT_STARTED";
        return TransitionResult.allowed();
    }

    public TransitionResult triggerFinale(UUID actor, String actorName, boolean eligible, long now) {
        if (phase != Phase.WAR) return TransitionResult.denied("Finale may be triggered only during War; current phase is " + phase + ".");
        if (!eligible) return TransitionResult.denied("Only an eligible ruler participating in an active war may trigger Finale.");
        if (minimumWarEndMillis < 0L) return TransitionResult.denied("Finale is unavailable until season.minimumWarSeasonLengthDays is configured.");
        if (now < minimumWarEndMillis) return TransitionResult.denied("The minimum War duration has not elapsed; " + (minimumWarEndMillis - now) + " ms remain.");
        if (actor == null) return TransitionResult.denied("Finale requires a player actor.");
        phase = Phase.FINALE;
        finaleTriggerActor = actor;
        finaleTriggerActorName = actorName == null ? "" : actorName;
        finaleTriggerTimeMillis = now;
        finaleEndTimeMillis = -1L; // No Finale duration has been configured.
        return TransitionResult.allowed();
    }

    public TransitionResult beginReset(long now) {
        TransitionResult result = transition(Phase.FINALE, Phase.RESET, now, "Reset may begin only from Finale.");
        if (result.allowed) { resetStatus = "PENDING"; finaleEndTimeMillis = now; }
        return result;
    }

    public TransitionResult completeReset(long now) {
        if (phase != Phase.RESET) return TransitionResult.denied("Reset can be completed only while the season is RESET.");
        phase = Phase.MAINTENANCE;
        seasonId = Math.max(1L, seasonId) + 1L;
        minimumWarEndMillis = -1L;
        finaleTriggerActor = null;
        finaleTriggerActorName = "";
        finaleTriggerTimeMillis = -1L;
        finaleEndTimeMillis = now;
        resetStatus = "COMPLETE";
        return TransitionResult.allowed();
    }

    /** Operator repair is deliberately auditable at the command boundary. */
    public TransitionResult repair(Phase target, long now) {
        if (target == null) return TransitionResult.denied("A repair target phase is required.");
        phase = target;
        if (target == Phase.RESET) resetStatus = "PENDING";
        else if (target == Phase.MAINTENANCE) resetStatus = "COMPLETE";
        return TransitionResult.allowed();
    }

    private TransitionResult transition(Phase required, Phase target, long now, String reason) {
        if (phase != required) return TransitionResult.denied(reason + " Current phase is " + phase + ".");
        phase = target;
        return TransitionResult.allowed();
    }

    private static long safeAdd(long a, long b) { return b > Long.MAX_VALUE - a ? Long.MAX_VALUE : Math.max(0L, a + b); }

    public void readFromNBT(NBTTagCompound tag) {
        seasonId = Math.max(1L, tag.hasKey("SeasonId") ? tag.getLong("SeasonId") : 1L);
        try { phase = Phase.valueOf(tag.hasKey("Phase") ? tag.getString("Phase") : Phase.MAINTENANCE.name()); } catch (IllegalArgumentException e) { phase = Phase.MAINTENANCE; }
        minimumWarEndMillis = tag.hasKey("MinimumWarEndMillis") ? tag.getLong("MinimumWarEndMillis") : -1L;
        finaleTriggerActor = null;
        if (tag.hasKey("FinaleTriggerActor")) try { finaleTriggerActor = UUID.fromString(tag.getString("FinaleTriggerActor")); } catch (IllegalArgumentException ignored) { }
        finaleTriggerActorName = tag.getString("FinaleTriggerActorName");
        finaleTriggerTimeMillis = tag.hasKey("FinaleTriggerTimeMillis") ? tag.getLong("FinaleTriggerTimeMillis") : -1L;
        finaleEndTimeMillis = tag.hasKey("FinaleEndTimeMillis") ? tag.getLong("FinaleEndTimeMillis") : -1L;
        resetStatus = tag.hasKey("ResetStatus") ? tag.getString("ResetStatus") : "NOT_STARTED";
    }

    public void writeToNBT(NBTTagCompound tag) {
        tag.setLong("SeasonId", Math.max(1L, seasonId)); tag.setString("Phase", phase.name());
        tag.setLong("MinimumWarEndMillis", minimumWarEndMillis);
        if (finaleTriggerActor != null) tag.setString("FinaleTriggerActor", finaleTriggerActor.toString());
        tag.setString("FinaleTriggerActorName", finaleTriggerActorName == null ? "" : finaleTriggerActorName);
        tag.setLong("FinaleTriggerTimeMillis", finaleTriggerTimeMillis); tag.setLong("FinaleEndTimeMillis", finaleEndTimeMillis);
        tag.setString("ResetStatus", resetStatus == null ? "NOT_STARTED" : resetStatus);
    }

    public static final class TransitionResult {
        public final boolean allowed; public final String reason;
        private TransitionResult(boolean allowed, String reason) { this.allowed = allowed; this.reason = reason; }
        static TransitionResult allowed() { return new TransitionResult(true, ""); }
        static TransitionResult denied(String reason) { return new TransitionResult(false, reason); }
    }
}
