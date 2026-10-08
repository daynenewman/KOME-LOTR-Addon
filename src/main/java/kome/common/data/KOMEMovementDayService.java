package kome.common.data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BooleanSupplier;
import kome.common.config.KOMEConfigRegistry;
import kome.common.command.KOMECommandTroops;
import net.minecraft.world.World;

/** Movement-domain boundary operation, not a scheduler. Startup never replays missed days. */
public final class KOMEMovementDayService {
    private KOMEMovementDayService() { }

    public static KOMEDailyBoundary schedule() {
        return KOMEDailyBoundary.from(KOMEConfigRegistry.requireReadySnapshot().getDailyBatch());
    }

    public static void validateBoundary(String signature, long millis) {
        int separator = signature == null ? -1 : signature.lastIndexOf('@');
        if (separator <= 0) throw new IllegalArgumentException("Missing movement boundary identity");
        KOMEDailyBoundary persisted = KOMEDailyBoundary.persisted(signature.substring(0, separator), signature.substring(separator + 1));
        if (millis < 0L || persisted.latestBoundaryAtOrBefore(Instant.ofEpochMilli(millis)).toEpochMilli() != millis)
            throw new IllegalArgumentException("Invalid movement boundary instant");
    }

    /** Only genuine recruitment/admission calls this, after composition is known. */
    public static void initializeNewCompany(KOMEArmyCompany company, long now) {
        if (company.movementAllowanceInitialized) return;
        stamp(company, schedule(), schedule().latestBoundaryAtOrBefore(Instant.ofEpochMilli(now)).toEpochMilli());
        company.movementAllowance = company.getTilesPerDay();
    }

    public static void cap(KOMEArmyCompany company) {
        if (company.movementAllowance == 0) return;
        company.movementAllowance = Math.min(company.movementAllowance, company.getTilesPerDay());
    }

    public static void split(KOMEArmyCompany parent, KOMEArmyCompany child) {
        cap(parent);
        child.movementAllowance = 0;
        child.movementAllowanceInitialized = parent.movementAllowanceInitialized;
        child.movementBoundaryMillis = parent.movementBoundaryMillis;
        child.movementBoundarySchedule = parent.movementBoundarySchedule;
    }

    public static void merge(KOMEArmyCompany survivor, KOMEArmyCompany absorbed) {
        int remaining = Math.max(survivor.movementAllowance, absorbed.movementAllowance);
        survivor.movementAllowance = remaining == 0 ? 0 : Math.min(survivor.getTilesPerDay(), remaining);
        if (absorbed.movementBoundaryMillis > survivor.movementBoundaryMillis) {
            survivor.movementBoundaryMillis = absorbed.movementBoundaryMillis;
            survivor.movementBoundarySchedule = absorbed.movementBoundarySchedule;
        }
        survivor.movementAllowanceInitialized |= absorbed.movementAllowanceInitialized;
    }

    public static int remaining(KOMEWorldData data, KOMEArmyMovementOrder order) {
        KOMEArmyCompany company = data.armyCompanies.get(order.companyId);
        return company == null ? 0 : Math.min(company.movementAllowance, company.getTilesPerDay());
    }

    /** Commit callback is run only with credit; rejected departures never debit. */
    public static boolean depart(KOMEWorldData data, KOMEArmyMovementOrder order, boolean daily, BooleanSupplier accept) {
        data.ensureWritable();
        KOMEArmyCompany company = data.armyCompanies.get(order.companyId);
        KOMEFormalRetreatAuthority.quarantineIncompleteLegacy(data);
        if (KOMEFormalRetreatAuthority.isQuarantined(order)) return false;
        boolean formalRetreat = KOMEMovementRetreatService.isImmediateFormalRetreatStep(order);
        if (company == null || daily && !formalRetreat && remaining(data, order) <= 0) return false;
        if (!accept.getAsBoolean()) return false;
        if (daily && !formalRetreat) company.movementAllowance = remaining(data, order) - 1;
        order.dailyStepsRemaining = remaining(data, order); // compatibility/display mirror only
        return true;
    }

    public static List<KOMEArmyMovementOrder> orderedRoutes(KOMEWorldData data) {
        KOMEFormalRetreatAuthority.quarantineIncompleteLegacy(data);
        List<KOMEArmyMovementOrder> orders = new ArrayList<KOMEArmyMovementOrder>();
        for (KOMEArmyMovementOrder order : data.armyMovements.values())
            if (order != null && !KOMEFormalRetreatAuthority.isQuarantined(order)) orders.add(order);
        orders.sort(Comparator.comparing(order -> order.id));
        return orders;
    }

    /** Anchor on startup or schedule change without granting credit, including legacy companies. */
    public static void anchor(KOMEWorldData data, long now) {
        data.ensureWritable();
        KOMEDailyBoundary schedule = schedule();
        long boundary = schedule.latestBoundaryAtOrBefore(Instant.ofEpochMilli(now)).toEpochMilli();
        boolean changed = data.movementBoundaryMillis < boundary || !schedule.signature().equals(data.movementBoundarySchedule);
        data.movementBoundaryMillis = Math.max(boundary, data.movementBoundaryMillis);
        data.movementBoundarySchedule = schedule.signature();
        for (KOMEArmyCompany company : data.armyCompanies.values()) {
            if (!company.movementAllowanceInitialized || company.movementBoundaryMillis < boundary
                    || !schedule.signature().equals(company.movementBoundarySchedule)) {
                stamp(company, schedule, boundary);
                cap(company);
                changed = true;
            }
        }
        changed |= mirror(data, schedule.nextBoundary(Instant.ofEpochMilli(boundary)).toEpochMilli());
        if (changed) data.markDirty();
    }

    /** One canonical boundary, idempotent. Caller supplies only the current observed boundary. */
    public static boolean applyBoundary(KOMEWorldData data, long boundary) {
        data.ensureWritable();
        KOMEDailyBoundary schedule = schedule();
        validateBoundary(schedule.signature(), boundary);
        if (!schedule.signature().equals(data.movementBoundarySchedule)) {
            anchor(data, boundary);
            return false;
        }
        if (boundary <= data.movementBoundaryMillis) return false;
        data.movementBoundaryMillis = boundary;
        for (KOMEArmyCompany company : data.armyCompanies.values()) {
            if (company.movementBoundaryMillis >= boundary) continue;
            stamp(company, schedule, boundary);
            company.movementAllowance = company.getTilesPerDay();
        }
        mirror(data, schedule.nextBoundary(Instant.ofEpochMilli(boundary)).toEpochMilli());
        data.markDirty();
        return true;
    }

    /** Future MOVEMENT stage can call this; existing tick owns when it is called today. */
    public static void applyBoundaryAndAdvance(KOMEWorldData data, World world, long boundary, long now) {
        if (boundary != schedule().latestBoundaryAtOrBefore(Instant.ofEpochMilli(now)).toEpochMilli())
            throw new IllegalArgumentException("Only the current observed movement boundary may advance routes; no offline replay");
        if (applyBoundary(data, boundary)) KOMECommandTroops.processMovementTick(data, world, now);
    }

    public static void observe(KOMEWorldData data, long now) {
        KOMEDailyBoundary schedule = schedule();
        if (!schedule.signature().equals(data.movementBoundarySchedule)) { anchor(data, now); return; }
        applyBoundary(data, schedule.latestBoundaryAtOrBefore(Instant.ofEpochMilli(now)).toEpochMilli());
    }

    private static void stamp(KOMEArmyCompany company, KOMEDailyBoundary schedule, long boundary) {
        company.movementAllowanceInitialized = true;
        company.movementBoundaryMillis = boundary;
        company.movementBoundarySchedule = schedule.signature();
    }

    private static boolean mirror(KOMEWorldData data, long next) {
        boolean changed = false;
        for (KOMEArmyMovementOrder order : orderedRoutes(data)) {
            int remaining = remaining(data, order);
            changed |= order.dailyStepsRemaining != remaining || order.nextDailyStepMillis != next;
            order.dailyStepsRemaining = remaining;
            order.nextDailyStepMillis = next;
            if (KOMEArmyMovementOrder.WAITING_NEXT_STEP.equals(order.status)
                    && data.movementSecondsPerTileOverride <= 0 && data.movementTotalSecondsOverride <= 0) {
                long departure = remaining > 0 ? 0L : next;
                changed |= order.nextStepDepartureMillis != departure || order.nextStepAvailableMillis != departure;
                order.nextStepDepartureMillis = departure;
                order.nextStepAvailableMillis = order.nextStepDepartureMillis;
            }
        }
        return changed;
    }
}
