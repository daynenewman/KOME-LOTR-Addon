package kome.common.data;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import kome.common.KOMEReflection;
import kome.common.command.KOMECommandTroops;
import lotr.common.entity.npc.LOTREntityNPC;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;

/** Physical referee only. No strategic, membership, route, or population writes. */
public final class KOMECampaignTileConfinementService implements KOMEServerTileAwareness.BoundaryGuard {
    public static final KOMECampaignTileConfinementService INSTANCE =
        new KOMECampaignTileConfinementService(KOMETileWorldResolver.INSTANCE);
    static final long AUDIT_INTERVAL = 100L;

    public enum Code {
        NOT_CAMPAIGN, UNKNOWN, IN_TILE, STRATEGIC_CONTRADICTION,
        RETURNED_TO_PLACEMENT, NO_SAFE_RETURN, RETURN_NOT_VERIFIED,
        RETRY_BLOCKED
    }

    public static final class Result {
        public final Code code;
        public final UUID unitId;
        public final String companyId, authoritativeTile, observedTile, reason;
        private Result(Code code, KOMEHiredUnitRecord record, String target, String observed, String reason) {
            this.code = code;
            unitId = record == null ? null : record.entity;
            companyId = record == null ? "" : clean(record.companyId);
            authoritativeTile = target; observedTile = observed; this.reason = reason;
        }
        public boolean corrected() {
            return code == Code.RETURNED_TO_PLACEMENT;
        }
    }

    static final class Position {
        final int dimension;
        final double x, y, z;
        Position(int dimension, double x, double y, double z) {
            this.dimension = dimension; this.x = x; this.y = y; this.z = z;
        }
    }

    /** Testable boundary to the real loaded entity; safe() must include terrain/body checks. */
    interface PhysicalUnit {
        Position position();
        boolean safe(Position position);
        Position placement(String tile);
        void returnTo(Position position);
        void halt();
    }

    private static final class State {
        String tile = "", company = "";
        long lastAudit = Long.MIN_VALUE;
        int coalesced;
        boolean blocked;
        boolean strategicInvalid;
        KOMETileWorldResolver.ReadView view;
    }

    private final KOMETileWorldResolver resolver;
    private final Map<UUID, State> states = new HashMap<UUID, State>();
    private long inspections, correctionAttempts;

    KOMECampaignTileConfinementService(KOMETileWorldResolver resolver) { this.resolver = resolver; }
    public long inspectionCount() { return inspections; }
    public long correctionAttemptCount() { return correctionAttempts; }
    @Override public void reset() { states.clear(); inspections = 0L; correctionAttempts = 0L; }
    @Override public void removed(Entity entity) { states.remove(KOMEReflection.getEntityUUID(entity)); }

    @Override public boolean onSample(Entity entity, KOMETileResolution physical,
            KOMETileWorldResolver.ReadView view, long tick) {
        // A geometry publication after the sampling pass began makes this evidence UNKNOWN.
        if (view != resolver.readView()) return false;
        KOMEWorldData data = KOMEWorldData.get(entity.worldObj);
        KOMEHiredUnitRecord record = data.hiredUnits.get(KOMEReflection.getEntityUUID(entity));
        if (!KOMEHiredUnitClassification.isCampaignUnit(record)) {
            states.remove(KOMEReflection.getEntityUUID(entity));
            return false;
        }
        Result result = evaluate(data, record, new LoadedUnit(data, (LOTREntityNPC) entity, view),
            KOMEServerTileAwareness.Availability.AVAILABLE, physical, tick);
        return result.corrected() || result.code == Code.RETURN_NOT_VERIFIED;
    }

    Result evaluate(KOMEWorldData data, KOMEHiredUnitRecord record, PhysicalUnit unit,
            KOMEServerTileAwareness.Availability availability, KOMETileResolution physical, long tick) {
        if (!KOMEHiredUnitClassification.isCampaignUnit(record))
            return result(Code.NOT_CAMPAIGN, record, "", "", "Explicit CAMPAIGN record required.");
        inspections++;
        String target = KOMEConquestTile.normalizeId(record.currentTile);
        String observed = physical == null ? "" : physical.tileId;
        State state = states.get(record.entity);
        if (state == null) { state = new State(); states.put(record.entity, state); }
        KOMETileWorldResolver.ReadView view = resolver.readView();
        if (!target.equals(state.tile) || !clean(record.companyId).equals(state.company) || state.view != view) {
            state.tile = target; state.company = clean(record.companyId); state.view = view;
            state.blocked = false;
        }
        KOMEArmyCompany company = data.armyCompanies.get(clean(record.companyId));
        if (target.isEmpty() || data.getConquestTileIfPresent(target) == null
                || (!clean(record.companyId).isEmpty() && (company == null
                    || !target.equals(KOMEConquestTile.normalizeId(company.currentTile))))) {
            state.strategicInvalid = true;
            return diagnostic(data, state, tick, result(Code.STRATEGIC_CONTRADICTION, record, target, observed,
                "Missing/contradictory record or detachment strategic tile; reconciliation required."));
        }
        if (state.strategicInvalid) {
            state.strategicInvalid = false; state.blocked = false;
        }
        if (availability != KOMEServerTileAwareness.Availability.AVAILABLE || physical == null
                || physical.status != KOMETileResolution.Status.RESOLVED)
            return result(Code.UNKNOWN, record, target, "", "No trustworthy resolved physical tile.");
        if (target.equals(observed)) {
            state.blocked = false;
            return result(Code.IN_TILE, record, target, observed, "Within authoritative conquest tile.");
        }
        if (state.blocked)
            return result(Code.RETRY_BLOCKED, record, target, observed,
                "Previous safe-return failure; awaiting in-tile recovery, reload, geometry or strategic repair.");
        correctionAttempts++;
        boolean relocationAttempted = false;
        try {
            // A border-adjacent position invites native Follow AI to cross again. Always station
            // at the authoritative tile's canonical deployment origin, with bounded safe placement.
            Position destination = unit.placement(target);
            if (resolver.readView() != view)
                return result(Code.UNKNOWN, record, target, "", "Geometry changed during safe-return search.");
            if (destination == null || !inside(view, destination, target) || !unit.safe(destination)) {
                state.blocked = true;
                return diagnostic(data, state, tick, result(Code.NO_SAFE_RETURN, record, target, observed,
                    "Bounded safe placement exhausted; entity retained, no strategic state changed."));
            }
            relocationAttempted = true;
            unit.returnTo(destination);
            Position returned = unit.position();
            if (!inside(view, returned, target) || !unit.safe(returned)) {
                state.blocked = true;
                return diagnostic(data, state, tick, result(Code.RETURN_NOT_VERIFIED, record, target, observed,
                    "Return did not verify inside authoritative tile; further teleports blocked."));
            }
            unit.halt();
            return diagnostic(data, state, tick, result(Code.RETURNED_TO_PLACEMENT,
                record, target, observed, "Illegal physical displacement corrected at tile station; native hired unit halted."));
        } catch (RuntimeException failure) {
            state.blocked = true;
            return diagnostic(data, state, tick, result(relocationAttempted ? Code.RETURN_NOT_VERIFIED : Code.NO_SAFE_RETURN,
                record, target, observed, "Safe-return adapter failed; entity retained and retries blocked: "
                    + failure.getClass().getSimpleName()));
        }
    }

    private static boolean inside(KOMETileWorldResolver.ReadView view, Position p, String tile) {
        if (p == null || Double.isNaN(p.y) || Double.isInfinite(p.y)) return false;
        KOMETileResolution resolved = view.resolveWorldPosition(p.dimension, p.x, p.z);
        return resolved.status == KOMETileResolution.Status.RESOLVED && tile.equals(resolved.tileId);
    }

    private static Result result(Code code, KOMEHiredUnitRecord record, String tile, String observed, String reason) {
        return new Result(code, record, tile, observed, reason);
    }

    private static Result diagnostic(KOMEWorldData data, State state, long tick, Result result) {
        // Always expose new failures, even immediately after a correction; coalesce boundary pressure.
        boolean failure = result.code == Code.NO_SAFE_RETURN || result.code == Code.RETURN_NOT_VERIFIED;
        if (failure || state.lastAudit == Long.MIN_VALUE || tick - state.lastAudit >= AUDIT_INTERVAL) {
            KOMEAuditService.record(data, System.currentTimeMillis(), "CAMPAIGN_CONFINEMENT", result.code.name(),
                "SERVER", String.valueOf(result.unitId), result.reason,
                "detachment=" + result.companyId + " authoritative=" + result.authoritativeTile
                    + " observed=" + result.observedTile + " coalesced=" + state.coalesced);
            state.lastAudit = tick; state.coalesced = 0;
        } else if (state.coalesced < Integer.MAX_VALUE) state.coalesced++;
        return result;
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }

    private static final class LoadedUnit implements PhysicalUnit {
        final KOMEWorldData data;
        final LOTREntityNPC npc;
        final KOMETileWorldResolver.ReadView view;
        LoadedUnit(KOMEWorldData data, LOTREntityNPC npc, KOMETileWorldResolver.ReadView view) {
            this.data = data; this.npc = npc; this.view = view;
        }
        @Override public Position position() {
            return new Position(npc.worldObj.provider.dimensionId, npc.posX, npc.posY, npc.posZ);
        }
        @Override public boolean safe(Position p) {
            return p.dimension == npc.worldObj.provider.dimensionId
                && KOMECommandTroops.isSafeConfinementPosition(npc, p.x, p.y, p.z);
        }
        @Override public Position placement(String tile) {
            double[] p = KOMECommandTroops.findSafeConfinementPosition(data, npc, tile, view);
            return p == null ? null : new Position(npc.worldObj.provider.dimensionId, p[0], p[1], p[2]);
        }
        @Override public void returnTo(Position p) {
            Entity entity = npc;
            for (int depth = 0; entity != null && depth < 8; depth++) {
                if (entity instanceof EntityLivingBase)
                    ((EntityLivingBase) entity).setPositionAndUpdate(p.x, p.y, p.z);
                else entity.setLocationAndAngles(p.x, p.y, p.z, entity.rotationYaw, entity.rotationPitch);
                entity.motionX = entity.motionY = entity.motionZ = 0D;
                entity.fallDistance = 0F;
                if (entity instanceof EntityLiving) ((EntityLiving) entity).getNavigator().clearPathEntity();
                entity = KOMEReflection.getRidingEntity(entity);
            }
        }
        @Override public void halt() {
            // v36.15 Halt is a state (canMove=false), not a Task enum value. Clear Guard only
            // after an illegal return so the safe native halt path yields isHalted()==true.
            if (npc.hiredNPCInfo.isGuardMode()) npc.hiredNPCInfo.setGuardMode(false);
            npc.hiredNPCInfo.halt();
        }
    }
}
