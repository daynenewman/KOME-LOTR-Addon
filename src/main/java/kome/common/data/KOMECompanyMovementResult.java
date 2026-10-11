package kome.common.data;

import java.util.Objects;
import java.util.UUID;
import kome.common.command.KOMECompanyMovementService.Submission;

/** Detached server outcome. An accepted order is not a promise of physical arrival. */
public final class KOMECompanyMovementResult {
    public enum Outcome { ARRIVED, ORDER_ACCEPTED, STALE_PREVIEW, REJECTED, SERVER_ERROR }
    public final UUID session;
    public final long sequence, previewSequence;
    public final String companyId, destination, orderId, orderStatus, currentTile, code, detail;
    public final int completedSteps, distance;
    public final Outcome outcome;
    public KOMECompanyMovementResult(UUID session,long sequence,long previewSequence,String companyId,String destination,
            Outcome outcome,String orderId,String orderStatus,String currentTile,int completedSteps,int distance,String code,String detail) {
        this.session=Objects.requireNonNull(session);this.sequence=sequence;this.previewSequence=previewSequence;
        this.companyId=companyId;this.destination=destination;this.outcome=Objects.requireNonNull(outcome);
        this.orderId=orderId;this.orderStatus=orderStatus;this.currentTile=currentTile;
        this.completedSteps=completedSteps;this.distance=distance;this.code=code;this.detail=detail;
    }
    public boolean accepted() {return outcome==Outcome.ARRIVED||outcome==Outcome.ORDER_ACCEPTED;}
    public static KOMECompanyMovementResult from(UUID session,long sequence,long preview,String company,String destination,Submission result) {
        if(!result.accepted)return denied(session,sequence,preview,company,destination,
            "STALE_PREVIEW".equals(result.denial.code)?Outcome.STALE_PREVIEW:Outcome.REJECTED,result.denial.code,playerDenialDetail(result.denial.code));
        boolean completed=destination.equals(result.currentTile)&&result.distanceTiles>0&&result.completedSteps==result.distanceTiles;
        boolean arrived=completed&&(KOMEArmyMovementOrder.ARRIVED.equals(result.orderStatus)
            ||KOMEArmyMovementOrder.CONFLICT_HELD.equals(result.orderStatus)||KOMEArmyMovementOrder.ACCESS_HALTED.equals(result.orderStatus));
        return new KOMECompanyMovementResult(session,sequence,preview,company,destination,
            arrived?Outcome.ARRIVED:Outcome.ORDER_ACCEPTED,
            result.orderId,result.orderStatus,result.currentTile,result.completedSteps,result.distanceTiles,"","");
    }
    public static KOMECompanyMovementResult denied(UUID session,long sequence,long preview,String company,String destination,
            Outcome outcome,String code,String detail) {
        return new KOMECompanyMovementResult(session,sequence,preview,company,destination,outcome,"","","",0,0,code,detail.length()>512?detail.substring(0,509)+"...":detail);
    }
    /** Player projections use typed messages; canonical command/admin diagnostics retain exact evidence. */
    public static String playerDenialDetail(String code) {
        switch (code) {
            case "UNKNOWN_COMPANY": return "Company is no longer available.";
            case "UNAUTHORIZED": return "You no longer control this company.";
            case "INELIGIBLE_MEMBER": return "Company membership changed or a member cannot move. Refresh the preview.";
            case "INCOHERENT_COMPANY": return "Company membership or location changed. Refresh the preview.";
            case "STALE_PREVIEW": return "Movement preview is stale; refresh and try again.";
            case "NO_ALLOWANCE": return "Company has no movement allowance remaining until the next movement day.";
            case "CONFLICT_HELD": return "Company is committed to an active battle.";
            case "FORMAL_RETREAT_BLOCKED": return "Company is reserved by Formal Retreat or requires operator recovery.";
            case "ALREADY_MOVING": return "Company already has a movement order.";
            case "EMPTY_COMPANY": return "Company has no units available to move.";
            case "FACTION_MISMATCH": return "Company faction does not match your movement authority.";
            case "INVALID_DESTINATION": return "Choose a different valid destination tile.";
            case "ARRIVAL_UNAVAILABLE": return "An arrival point on this route is unavailable. Refresh or choose another destination.";
            case "PASSAGE_DENIED": return "Military passage or hostile destination authority is no longer available.";
            case "MOUNTAIN_EDGE_BLOCKED": return "A mountain route step is blocked; a legal pass is required.";
            case "RIVER_EDGE_BLOCKED": return "A river route step is blocked; a legal crossing is required.";
            case "ROUTE_EDGE_BLOCKED": return "A route step is blocked.";
            case "ROUTE_EDGE_MISSING": return "No legal route is available.";
            case "GEOGRAPHY_UNAVAILABLE":
            case "UNKNOWN_GEOGRAPHY": return "Route geography is unavailable.";
            case "INVALID_ACTOR": return "A current server connection is required. Reconnect and refresh the preview.";
            case "SESSION_LIMIT": return "Movement confirmation capacity is unavailable. Try again later.";
            default: return "Movement could not start. Refresh the preview or ask an operator to check company readiness.";
        }
    }
}
