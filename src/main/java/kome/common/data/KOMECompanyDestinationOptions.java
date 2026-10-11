package kome.common.data;

import java.util.*;
import kome.common.command.KOMECompanyMovementService;

/** Detached server projection. Accepted orders and expected consequences never imply physical arrival. */
public final class KOMECompanyDestinationOptions {
    public static final int MAX_ROWS = 8, MAX_ROUTE_TILES = 64, MAX_ARRIVAL_CHECKS = 64;
    public static final int MAX_CATALOGUE = 4096, MAX_MEMBERS = 256;
    public enum Result { OK, INVALID_REQUEST, CATALOGUE_LIMIT, COMPANY_LIMIT, CATALOGUE_CHANGED,
        ARRIVAL_BUDGET_EXCEEDED, PROJECTION_LIMIT, RATE_LIMITED }
    public enum EvaluationState { COMPLETE, DEFERRED_ROUTE_LIMIT }
    public enum Entitlement { FOOT_OR_MIXED, MOUNTED }
    public static final class Row {
        public final String companyId, name, currentTile, destinationTile;
        public final EvaluationState evaluationState;
        public final Entitlement entitlement;
        public final int tilesPerDay, remainingAllowance, distance;
        /** Canonical permanent faction investment, in centi-population; -1 means incomplete/unknown. */
        public final long populationCenti;
        public final int unitCount;
        /** Duration at server evaluation time, not a client-clock countdown; -1 means unavailable. */
        public final long etaMillis;
        public final List<String> route;
        /** Null for deferred rows: unknown eligibility must never look like a movement denial. */
        public final KOMECompanyMovementService.Status status;
        public final KOMECompanyMovementService.ScheduleMode scheduleMode;
        public final boolean canStartNow, canArriveNow, legalRoute;
        /** Daily timing only; timed overrides deliberately carry no guessed ETA. */
        public final boolean timingAvailable;
        public final int futureBoundaries;
        public final long nextBoundaryMillis, estimatedCompletionMillis;
        public final String denialCode, denialDetail, heldConflictId;
        public final long heldConflictRevision;
        public final KOMECompanyMovementService.Consequence consequence;
        public final String destinationConflictId;
        public final long destinationConflictRevision;
        /** Opaque stale-state evidence only. A later execution path must revalidate live authority. */
        public final String legacyFingerprint, confirmationFingerprint;
        public Row(KOMECompanyMovementService.Evaluation e) {
            this(e.companyId,e.companyName,e.currentTile,e.destinationTile,
                e.groundPopulation == 0 && e.mountedPopulation > 0 ? Entitlement.MOUNTED : Entitlement.FOOT_OR_MIXED,
                e.tilesPerDay,e.remainingAllowance,e.distanceTiles,e.routeTiles,e.status,e.scheduleMode,
                e.canStartNow,e.canArriveNow,e.legalRoute,
                e.dailyTiming != null && e.scheduleMode == KOMECompanyMovementService.ScheduleMode.DAILY,
                !hasDailyTiming(e) ? 0 : e.dailyTiming.requiredBoundaries,
                !hasDailyTiming(e) ? 0 : e.dailyTiming.nextBoundaryMillis,
                !hasDailyTiming(e) ? 0 : e.dailyTiming.estimatedCompletionMillis,
                e.denial == null ? "" : e.denial.code,e.denial == null ? "" : e.denial.reason,
                e.heldConflictId,e.heldConflictRevision,e.consequence,e.destinationConflictId,e.destinationConflictRevision,
                e.confirmation == null ? "" : e.confirmation.legacyFingerprint,
                e.confirmation == null ? "" : e.confirmation.serviceFingerprint);
        }
        private static boolean hasDailyTiming(KOMECompanyMovementService.Evaluation e) {
            return e.dailyTiming != null && e.scheduleMode == KOMECompanyMovementService.ScheduleMode.DAILY;
        }
        public Row(KOMECompanyMovementService.DeferredEvaluation e) {
            this(EvaluationState.DEFERRED_ROUTE_LIMIT, e.companyId,e.companyName,e.currentTile,e.destinationTile,
                e.groundPopulation == 0 && e.mountedPopulation > 0 ? Entitlement.MOUNTED : Entitlement.FOOT_OR_MIXED,
                e.tilesPerDay,e.remainingAllowance,-1,Collections.<String>emptyList(),null,e.scheduleMode,
                false,false,false,false,0,0,0,"","","",-1,null,"",-1,"","");
            if (!e.exceedsSingleRouteLimit) throw new IllegalArgumentException("Request budget requires continuation, not a deferred row");
        }
        public Row(String id,String name,String origin,String destination,Entitlement entitlement,
                int tilesPerDay,int allowance,int distance,List<String> route,
                KOMECompanyMovementService.Status status,KOMECompanyMovementService.ScheduleMode mode,
                boolean start,boolean arrive,boolean legal,boolean timing,int boundaries,long next,long completion,
                String denial,String detail,String held,long heldRevision,KOMECompanyMovementService.Consequence consequence,
                String conflict,long revision,String legacy,String confirmation) {
            this(EvaluationState.COMPLETE,id,name,origin,destination,entitlement,tilesPerDay,allowance,distance,route,
                status,mode,start,arrive,legal,timing,boundaries,next,completion,denial,detail,held,heldRevision,
                consequence,conflict,revision,legacy,confirmation);
        }
        public Row(EvaluationState state,String id,String name,String origin,String destination,Entitlement entitlement,
                int tilesPerDay,int allowance,int distance,List<String> route,
                KOMECompanyMovementService.Status status,KOMECompanyMovementService.ScheduleMode mode,
                boolean start,boolean arrive,boolean legal,boolean timing,int boundaries,long next,long completion,
                String denial,String detail,String held,long heldRevision,KOMECompanyMovementService.Consequence consequence,
                String conflict,long revision,String legacy,String confirmation) {
            this(state,id,name,origin,destination,entitlement,tilesPerDay,allowance,distance,route,status,mode,
                start,arrive,legal,timing,boundaries,next,completion,denial,detail,held,heldRevision,consequence,
                conflict,revision,legacy,confirmation,-1,-1,-1);
        }
        public Row(Row base,long populationCenti,int unitCount,long etaMillis) {
            this(base.evaluationState,base.companyId,base.name,base.currentTile,base.destinationTile,base.entitlement,
                base.tilesPerDay,base.remainingAllowance,base.distance,base.route,base.status,base.scheduleMode,
                base.canStartNow,base.canArriveNow,base.legalRoute,base.timingAvailable,base.futureBoundaries,
                base.nextBoundaryMillis,base.estimatedCompletionMillis,base.denialCode,base.denialDetail,
                base.heldConflictId,base.heldConflictRevision,base.consequence,base.destinationConflictId,
                base.destinationConflictRevision,base.legacyFingerprint,base.confirmationFingerprint,populationCenti,unitCount,etaMillis);
        }
        private Row(EvaluationState state,String id,String name,String origin,String destination,Entitlement entitlement,
                int tilesPerDay,int allowance,int distance,List<String> route,
                KOMECompanyMovementService.Status status,KOMECompanyMovementService.ScheduleMode mode,
                boolean start,boolean arrive,boolean legal,boolean timing,int boundaries,long next,long completion,
                String denial,String detail,String held,long heldRevision,KOMECompanyMovementService.Consequence consequence,
                String conflict,long revision,String legacy,String confirmation,long populationCenti,int unitCount,long etaMillis) {
            this.populationCenti=populationCenti;this.unitCount=unitCount;this.etaMillis=etaMillis;
            evaluationState=Objects.requireNonNull(state);
            companyId=Objects.requireNonNull(id);this.name=Objects.requireNonNull(name);
            currentTile=Objects.requireNonNull(origin);destinationTile=Objects.requireNonNull(destination);
            this.entitlement=Objects.requireNonNull(entitlement);this.tilesPerDay=tilesPerDay;
            remainingAllowance=allowance;this.distance=distance;this.route=immutable(route);
            this.status=status;scheduleMode=Objects.requireNonNull(mode);
            canStartNow=start;canArriveNow=arrive;legalRoute=legal;timingAvailable=timing;
            futureBoundaries=boundaries;nextBoundaryMillis=next;estimatedCompletionMillis=completion;
            denialCode=Objects.requireNonNull(denial);denialDetail=Objects.requireNonNull(detail);
            heldConflictId=Objects.requireNonNull(held);heldConflictRevision=heldRevision;
            this.consequence=consequence;destinationConflictId=Objects.requireNonNull(conflict);
            destinationConflictRevision=revision;legacyFingerprint=Objects.requireNonNull(legacy);
            confirmationFingerprint=Objects.requireNonNull(confirmation);
        }
    }
    public final UUID session;
    public final long sequence;
    public final String destination, catalogueToken, nextCompanyId;
    public final Result result;
    public final List<Row> rows;
    public final boolean hasMore;
    /** Zero normally; -1 means the rate-limit connection capacity has no predictable retry time. */
    public final long retryAfterMillis;
    public KOMECompanyDestinationOptions(UUID session,long sequence,String destination,Result result,
            List<Row> rows,String catalogueToken,String nextCompanyId,boolean more) {
        this(session,sequence,destination,result,rows,catalogueToken,nextCompanyId,more,0);
    }
    public KOMECompanyDestinationOptions(UUID session,long sequence,String destination,Result result,
            List<Row> rows,String catalogueToken,String nextCompanyId,boolean more,long retryAfterMillis) {
        this.retryAfterMillis=retryAfterMillis;
        this.session=Objects.requireNonNull(session);this.sequence=sequence;this.destination=Objects.requireNonNull(destination);
        this.result=Objects.requireNonNull(result);this.rows=immutable(rows);
        this.catalogueToken=Objects.requireNonNull(catalogueToken);this.nextCompanyId=Objects.requireNonNull(nextCompanyId);hasMore=more;
    }
    private static <T> List<T> immutable(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<T>(values));
    }
}
