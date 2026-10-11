package kome.common.data;

import kome.common.KOMEReflection;
import kome.common.command.KOMECommandTroops;
import net.minecraft.entity.player.EntityPlayerMP;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static kome.common.data.KOMEConflictContracts.Context;
import static kome.common.data.KOMEConflictContracts.ExpectedConflict;
import static kome.common.data.KOMEConflictContracts.ValidatedDepartureRequest;
import static kome.common.data.KOMEJoinBattleDeploymentReceipt.State;

/** Minimal KOM-19 formal retreat: exact deployment, exact conflict, exact personal command. */
public final class KOMEFormalRetreatService {
    public static final KOMEFormalRetreatService INSTANCE = new KOMEFormalRetreatService();
    private static final String EGRESS_REASON = "Player formally retreated from the battle.";

    public enum Code {
        RETREATED,
        EGRESS_PENDING,
        NOT_DEPLOYED,
        NO_RETREATABLE_FORCES,
        MULTIPLE_CONFLICTS,
        ENTRY_RECOVERY_PENDING,
        CONFLICT_UNAVAILABLE,
        RETREAT_BLOCKED
    }

    public static final class Inspection {
        public final KOMEJoinBattleDeploymentReceipt receipt;
        public final String tileId, conflictId;
        public final List<String> companyIds;
        public final boolean canRetreat;
        public final String reason;

        private Inspection(KOMEJoinBattleDeploymentReceipt receipt,String tile,String conflict,
                List<String> companies,
                boolean canRetreat, String reason) {
            this.receipt = receipt;
            this.tileId=tile==null?"":tile;this.conflictId=conflict==null?"":conflict;
            this.companyIds = Collections.unmodifiableList(new ArrayList<String>(companies));
            this.canRetreat = canRetreat;
            this.reason = reason == null ? "" : reason;
        }

        public boolean isDeployed() {
            return receipt != null && receipt.getState() == State.DEPLOYED;
        }
    }

    public static final class Result {
        public final Code code;
        public final String tileId, conflictId, receiptId, message;
        public final List<String> companyIds;

        private Result(Code code, KOMEJoinBattleDeploymentReceipt receipt,
                KOMEConflictRecord conflict,List<String> companies, String message) {
            this.code = code;
            this.tileId = conflict!=null?conflict.getTileId():receipt == null ? "" : receipt.getTileId();
            this.conflictId = conflict!=null?conflict.getConflictId():receipt == null ? "" : receipt.getConflictId();
            this.receiptId = receipt == null ? "" : receipt.getReceiptId();
            this.companyIds = Collections.unmodifiableList(new ArrayList<String>(companies));
            this.message = message;
        }

        public boolean accepted() {
            return code == Code.RETREATED || code == Code.EGRESS_PENDING;
        }
    }

    private static final class CompanyPlan {
        final KOMEArmyCompany company;
        final KOMEArmyMovementOrder order;
        final KOMEMovementRetreatService.Plan route;
        StrategicPreparation departure;
        int allowanceBefore;
        int allowanceAfter;
        CompanyPlan(KOMEArmyCompany company, KOMEArmyMovementOrder order,
                KOMEMovementRetreatService.Plan route) {
            this.company = company; this.order = order; this.route = route;
        }
    }

    private static final class Target {
        final KOMEConflictRecord conflict;
        final List<String> companyIds;
        Target(KOMEConflictRecord conflict,List<String> companyIds){
            this.conflict=conflict;this.companyIds=companyIds;
        }
    }

    static final class StrategicPreparation {
        final String orderId, originTile, destinationTile;
        final Object delegate;
        StrategicPreparation(String orderId, String origin, String destination, Object delegate) {
            this.orderId = orderId; originTile = origin; destinationTile = destination;
            this.delegate = delegate;
        }
    }

    interface StrategicAccess {
        StrategicPreparation prepare(KOMEWorldData data, EntityPlayerMP player,
            KOMEArmyMovementOrder order, String destinationTile, long nowMillis);
        boolean execute(KOMEWorldData data, StrategicPreparation preparation, long nowMillis);
        void complete(KOMEWorldData data, EntityPlayerMP player, long nowMillis,
            java.util.Set<String> acceptedOrders);
        void release(StrategicPreparation preparation);
    }

    private static final StrategicAccess LIVE_STRATEGIC = new StrategicAccess() {
        @Override public StrategicPreparation prepare(KOMEWorldData data, EntityPlayerMP player,
                KOMEArmyMovementOrder order, String destinationTile, long nowMillis) {
            KOMECommandTroops.FormalRetreatDeparturePlan plan =
                KOMECommandTroops.prepareImmediateFormalRetreatStep(data,
                    retreatWorld(player), order, destinationTile, nowMillis);
            return new StrategicPreparation(order.id, plan.getOriginTile(),
                plan.getDestinationTile(), plan);
        }
        @Override public boolean execute(KOMEWorldData data,
                StrategicPreparation preparation, long nowMillis) {
            return KOMECommandTroops.executeImmediateFormalRetreatStep(data,
                (KOMECommandTroops.FormalRetreatDeparturePlan) preparation.delegate, nowMillis);
        }
        @Override public void complete(KOMEWorldData data, EntityPlayerMP player, long nowMillis,
                java.util.Set<String> acceptedOrders) {
            KOMECommandTroops.completeImmediateFormalRetreatSteps(data,
                retreatWorld(player), nowMillis, acceptedOrders);
        }
        @Override public void release(StrategicPreparation preparation) {
            if (preparation != null) KOMECommandTroops.releaseImmediateFormalRetreatPlan(
                (KOMECommandTroops.FormalRetreatDeparturePlan) preparation.delegate);
        }
    };

    /** Pure model seam used only by service tests; production always uses LIVE_STRATEGIC. */
    static final StrategicAccess TEST_STRATEGIC = new StrategicAccess() {
        @Override public StrategicPreparation prepare(KOMEWorldData data, EntityPlayerMP player,
                KOMEArmyMovementOrder order, String destinationTile, long nowMillis) {
            return new StrategicPreparation(order.id,
                KOMEConquestTile.normalizeId(order.currentTile),
                KOMEConquestTile.normalizeId(destinationTile), null);
        }
        @Override public boolean execute(KOMEWorldData data,
                StrategicPreparation preparation, long nowMillis) {
            KOMEArmyMovementOrder order = null;
            for (KOMEArmyMovementOrder candidate : data.armyMovements.values())
                if (candidate != null && preparation.orderId.equals(candidate.id)
                        && preparation.originTile.equals(
                            KOMEConquestTile.normalizeId(candidate.currentTile))
                        && preparation.destinationTile.equals(
                            KOMEConquestTile.normalizeId(candidate.nextTile))
                        && KOMEMovementRetreatService.isAcceptedFormalRetreat(candidate)) {
                    order = candidate; break;
                }
            if (order == null) return false;
            order.status = KOMEArmyMovementOrder.MOVING;
            order.currentRouteIndex = order.nextRouteIndex;
            order.completedSteps = Math.max(order.completedSteps, order.currentRouteIndex);
            order.currentTile = preparation.destinationTile;
            order.currentStepOriginTile = preparation.destinationTile;
            if (order.traveledRouteTiles.isEmpty() || !preparation.destinationTile.equals(
                    order.traveledRouteTiles.get(order.traveledRouteTiles.size() - 1)))
                order.traveledRouteTiles.add(preparation.destinationTile);
            KOMEArmyCompany company = data.armyCompanies.get(order.companyId);
            if (company != null) {
                company.currentTile = preparation.destinationTile;
                company.status = order.currentRouteIndex >= order.finalRouteIndex
                    ? KOMEArmyCompany.STATIONED : KOMEArmyCompany.MOVING;
                company.movementOrderId = order.currentRouteIndex >= order.finalRouteIndex
                    ? "" : order.id;
            }
            for (UUID unitId : order.units) {
                KOMEHiredUnitRecord record = data.hiredUnits.get(unitId);
                if (record != null) record.currentTile = preparation.destinationTile;
            }
            if (order.currentRouteIndex >= order.finalRouteIndex) order.markArrived();
            else {
                order.status = KOMEArmyMovementOrder.WAITING_NEXT_STEP;
                order.nextRouteIndex = order.currentRouteIndex + 1;
                order.nextTile = order.routeTiles.get(order.nextRouteIndex);
            }
            return true;
        }
        @Override public void complete(KOMEWorldData data, EntityPlayerMP player, long nowMillis,
                java.util.Set<String> acceptedOrders) { }
        @Override public void release(StrategicPreparation preparation) { }
    };

    private KOMEFormalRetreatService() { }

    /** Inspects retreat authority for one exact conflict; physical deployment is irrelevant. */
    public Inspection inspectConflict(KOMEWorldData data,UUID playerId,String conflictId){
        KOMEJoinBattleDeploymentReceipt receipt=KOMEJoinBattleEgressService.INSTANCE.findOpen(data,playerId);
        Target target=findTarget(data,playerId,conflictId);
        if(target==null)return new Inspection(receipt,"",conflictId,
            Collections.<String>emptyList(),false,
            "You do not personally command committed Campaign companies in this battle.");
        try{
            prepare(data,target.conflict,target.companyIds);
            return new Inspection(receipt,target.conflict.getTileId(),target.conflict.getConflictId(),
                target.companyIds,true,
                "Formal Retreat withdraws all Campaign companies you personally command in this battle.");
        }catch(IllegalArgumentException blocked){
            return new Inspection(receipt,target.conflict.getTileId(),target.conflict.getConflictId(),
                target.companyIds,false,blocked.getMessage());
        }
    }

    /** Backward-compatible inspection of the open physical deployment, if any. */
    public Inspection inspect(KOMEWorldData data,UUID playerId){
        KOMEJoinBattleDeploymentReceipt receipt=KOMEJoinBattleEgressService.INSTANCE.findOpen(data,playerId);
        if(receipt==null)return new Inspection(null,"","",Collections.<String>emptyList(),false,
            "You are not currently in or joining a battle.");
        Inspection exact=inspectConflict(data,playerId,receipt.getConflictId());
        if(receipt.getState()==State.PENDING_EGRESS)return new Inspection(receipt,
            receipt.getTileId(),receipt.getConflictId(),exact.companyIds,true,
            "Returning from "+identity(receipt)+".");
        return exact;
    }

    public Result retreat(KOMEWorldData data,EntityPlayerMP player,long nowMillis){
        return retreat(data,player,"",nowMillis,null,LIVE_STRATEGIC,null);
    }

    public Result retreat(KOMEWorldData data,EntityPlayerMP player,String conflictId,long nowMillis){
        return retreat(data,player,conflictId,nowMillis,null,LIVE_STRATEGIC,null);
    }

    Result retreat(KOMEWorldData data,EntityPlayerMP player,long nowMillis,
            KOMEJoinBattleEgressService.PhysicalAccess testPhysical){
        return retreat(data,player,"",nowMillis,testPhysical,TEST_STRATEGIC,null);
    }

    Result retreat(KOMEWorldData data,EntityPlayerMP player,String conflictId,long nowMillis,
            KOMEJoinBattleEgressService.PhysicalAccess testPhysical){
        return retreat(data,player,conflictId,nowMillis,testPhysical,TEST_STRATEGIC,null);
    }

    Result retreat(KOMEWorldData data,EntityPlayerMP player,String conflictId,long nowMillis,
            KOMEJoinBattleEgressService.PhysicalAccess testPhysical,
            KOMEJoinBattleEntryService.PhysicalAccess entryPhysical){
        return retreat(data,player,conflictId,nowMillis,testPhysical,TEST_STRATEGIC,entryPhysical);
    }

    Result retreat(KOMEWorldData data,EntityPlayerMP player,String conflictId,long nowMillis,
            KOMEJoinBattleEgressService.PhysicalAccess testPhysical,StrategicAccess strategic,
            KOMEJoinBattleEntryService.PhysicalAccess entryPhysical){
        if(data==null||player==null)return result(Code.NO_RETREATABLE_FORCES,null,null,
            Collections.<String>emptyList(),"You have no personally commanded committed forces available to retreat.");
        UUID playerId=KOMEReflection.getEntityUUID(player);
        KOMEJoinBattleDeploymentReceipt receipt=KOMEJoinBattleEgressService.INSTANCE.findOpen(data,playerId);
        String requested=canonicalRequestedConflictId(conflictId);

        String preferred=requested.isEmpty()&&receipt!=null?receipt.getConflictId():requested;
        List<KOMEArmyMovementOrder> pending=new ArrayList<KOMEArmyMovementOrder>();
        for(KOMEArmyMovementOrder order:data.armyMovements.values()){
            KOMEFormalRetreatBatch batch=order.formalRetreatBatch;
            if(batch!=null&&!batch.finalized()&&batch.commander.equals(playerId)
                    &&(preferred.isEmpty()||preferred.equals(batch.conflictId)))pending.add(order);
        }
        if(pending.size()>1)return result(Code.MULTIPLE_CONFLICTS,receipt,null,
            Collections.<String>emptyList(),"Several accepted retreats are recovering. Specify /kome battle retreat <conflictId>.");
        if(pending.size()==1)return resumeBatch(data,pending.get(0),player,nowMillis,testPhysical,strategic,null);
        if(!preferred.isEmpty()&&legacyIncomplete(data,preferred))
            return result(Code.RETREAT_BLOCKED,receipt,receipt==null?null:
                data.getConflictService().get(receipt.getTileId()),Collections.<String>emptyList(),
                "An incomplete legacy retreat has no durable accepted-group authority. Recovery is preserved for operator review.");

        if(receipt!=null&&receipt.getState()==State.PENDING_EGRESS
                &&(requested.isEmpty()||requested.equals(receipt.getConflictId()))){
            return finishEgress(data,player,receipt,Collections.<String>emptyList(),null,nowMillis,testPhysical);
        }

        Target target=null;
        if(!requested.isEmpty())target=findTarget(data,playerId,requested);
        else if(receipt!=null)target=findTarget(data,playerId,receipt.getConflictId());
        if(target==null&&requested.isEmpty()&&receipt==null){
            List<Target> available=targets(data,playerId);
            if(available.size()==1)target=available.get(0);
            else if(available.size()>1)return result(Code.MULTIPLE_CONFLICTS,receipt,null,
                Collections.<String>emptyList(),choices(data,available));
        }
        if(target==null)return result(Code.NO_RETREATABLE_FORCES,receipt,null,
            Collections.<String>emptyList(),requested.isEmpty()
                ?"You have no personally commanded committed forces available to retreat."
                :"You do not personally command committed Campaign companies in "+requested+".");

        KOMEConflictRecord conflict=target.conflict;
        List<String> companyIds=target.companyIds;
        if(legacyIncomplete(data,conflict.getConflictId()))
            return result(Code.RETREAT_BLOCKED,receipt,conflict,companyIds,
                "An incomplete legacy retreat has no durable accepted-group authority. Operator review is required.");
        for(String companyId:companyIds){
            KOMEArmyCompany candidate=data.armyCompanies.get(companyId);
            if(KOMEFormalRetreatAuthority.reservation(data,companyId,candidate.movementOrderId)!=null)
                return result(Code.RETREAT_BLOCKED,receipt,conflict,companyIds,
                    "Company "+companyId+" is already part of an unfinished Formal Retreat.");
        }
        final List<CompanyPlan> plans;
        try{plans=prepare(data,conflict,companyIds);}
        catch(IllegalArgumentException blocked){return result(Code.RETREAT_BLOCKED,receipt,conflict,
            companyIds,"Formal retreat from "+identity(conflict)+" was blocked: "+blocked.getMessage());}

        boolean sameReceipt=receipt!=null&&receipt.getConflictId().equals(conflict.getConflictId());
        if(sameReceipt&&receipt.getState()==State.PENDING_ENTRY){
            KOMEJoinBattleEntryService.Result cancelled=entryPhysical==null
                ?KOMEJoinBattleEntryService.INSTANCE.cancelAccepted(data,player,
                    receipt.getReceiptId(),nowMillis,
                    "The unfinished Join Battle was cancelled for Formal Retreat.")
                :KOMEJoinBattleEntryService.INSTANCE.cancelAccepted(data,player,
                    receipt.getReceiptId(),nowMillis,
                    "The unfinished Join Battle was cancelled for Formal Retreat.",entryPhysical);
            KOMEJoinBattleDeploymentReceipt after=data.getJoinBattleDeploymentReceipts()
                .get(receipt.getReceiptId());
            if(after==null||after.getState()!=State.CLOSED)return result(
                Code.ENTRY_RECOVERY_PENDING,receipt,conflict,companyIds,
                "Joining "+identity(receipt)+" must finish safe mount/player recovery before retreat: "
                    +cancelled.message);
            receipt=after;
        }

        // Route, controller, physical snapshot and first-destination checks all complete before
        // the first commitment is released. Tickets remain held until every step is published.
        try {
            for (CompanyPlan plan : plans) {
                plan.departure = strategic.prepare(data, player, plan.order,
                    plan.route.routeTiles.get(1), nowMillis);
                plan.allowanceBefore = Math.max(0, Math.min(plan.company.movementAllowance,
                    plan.company.getTilesPerDay()));
                plan.allowanceAfter = Math.max(0, plan.allowanceBefore - 1);
            }
        } catch (IllegalArgumentException blocked) {
            releaseStrategic(plans, strategic);
            return result(Code.RETREAT_BLOCKED, receipt, conflict,companyIds,
                "Formal retreat from " + identity(conflict) + " was blocked: "
                    + blocked.getMessage());
        }

        try {
        List<KOMEFormalRetreatBatch.Member> members=new ArrayList<KOMEFormalRetreatBatch.Member>();
        for(CompanyPlan plan:plans)members.add(new KOMEFormalRetreatBatch.Member(
            plan.company.id,plan.order.id,plan.company.owner,plan.route.routeTiles,
            plan.allowanceBefore,plan.allowanceAfter,KOMEFormalRetreatBatch.Progress.PREPARED));
        KOMEFormalRetreatBatch batch=new KOMEFormalRetreatBatch(UUID.randomUUID(),playerId,
            conflict.getConflictId(),conflict.getTileId(),sameReceipt?receipt.getReceiptId():"",
            nowMillis,members,KOMEFormalRetreatBatch.Phase.STRATEGIC_PENDING,false);
        KOMEArmyMovementOrder holder=plans.get(0).order;
        data.ensureWritable();
        // The COMPLETE manifest exists before the first release. No later membership inference.
        for(CompanyPlan plan:plans)
            if(KOMEFormalRetreatAuthority.reservation(data,plan.company.id,plan.order.id)!=null)
                throw new IllegalArgumentException("Company "+plan.company.id+" is already part of an unfinished Formal Retreat.");
        holder.formalRetreatBatch=batch;data.markDirty();
        return resumeBatch(data,holder,player,nowMillis,testPhysical,strategic,plans);
        } catch(IllegalArgumentException invalid) {
            return result(Code.RETREAT_BLOCKED,receipt,conflict,companyIds,invalid.getMessage());
        }
        finally{releaseStrategic(plans,strategic);}
    }

    private static void releaseStrategic(List<CompanyPlan> plans, StrategicAccess strategic) {
        for (CompanyPlan plan : plans) if (plan.departure != null)
            strategic.release(plan.departure);
    }

    private static net.minecraft.world.World retreatWorld(EntityPlayerMP player){
        return player==null?net.minecraftforge.common.DimensionManager.getWorld(0):KOMEReflection.getWorld(player);
    }

    private final java.util.Map<KOMEWorldData,Long> nextBatchPoll=
        new java.util.WeakHashMap<KOMEWorldData,Long>();

    /** Transient cadence; all mutation/completion authority lives in the persisted batch. */
    public void tick(KOMEWorldData data,long now){
        Long next=nextBatchPoll.get(data);if(next!=null&&now<next)return;
        nextBatchPoll.put(data,now+5000L);
        for(KOMEArmyMovementOrder holder:new ArrayList<KOMEArmyMovementOrder>(data.armyMovements.values())){
            KOMEFormalRetreatBatch batch=holder.formalRetreatBatch;
            if(batch==null||batch.finalized())continue;
            EntityPlayerMP player=null;
            net.minecraft.server.MinecraftServer server=net.minecraft.server.MinecraftServer.getServer();
            if(server!=null&&server.getConfigurationManager()!=null)
                for(Object value:server.getConfigurationManager().playerEntityList)
                    if(value instanceof EntityPlayerMP&&batch.commander.equals(
                            KOMEReflection.getEntityUUID((EntityPlayerMP)value)))player=(EntityPlayerMP)value;
            resumeBatch(data,holder,player,now,null,LIVE_STRATEGIC,null);
        }
    }

    static String egressBlockedReason(KOMEWorldData data,KOMEJoinBattleDeploymentReceipt receipt){
        for(KOMEArmyMovementOrder order:data.armyMovements.values()){
            KOMEFormalRetreatBatch b=order.formalRetreatBatch;
            if(b!=null&&b.receiptId.equals(receipt.getReceiptId())
                    &&b.phase==KOMEFormalRetreatBatch.Phase.STRATEGIC_PENDING)
                return "The accepted strategic retreat group has not finished its first departure.";
        }
        if(legacyIncomplete(data,receipt.getConflictId()))
            return "An incomplete legacy retreat lacks durable accepted-group authority; operator review is required.";
        return null;
    }

    private static boolean legacyIncomplete(KOMEWorldData data,String conflict){
        KOMEFormalRetreatAuthority.quarantineIncompleteLegacy(data);
        for(KOMEArmyMovementOrder order:data.armyMovements.values())
            if(KOMEFormalRetreatAuthority.isQuarantined(order)
                    &&conflict.equals(order.conflictRelease.conflictId))return true;
        return false;
    }

    private static KOMEFormalRetreatBatch saveBatch(KOMEWorldData data,
            KOMEArmyMovementOrder holder,KOMEFormalRetreatBatch batch){
        if(holder.formalRetreatBatch==null||!holder.formalRetreatBatch.id.equals(batch.id)
                ||holder.formalRetreatBatch.finalized())
            throw new IllegalArgumentException("Retreat batch identity/finalization changed");
        data.ensureWritable();holder.formalRetreatBatch=batch;data.markDirty();return batch;
    }

    Result resumeBatch(KOMEWorldData data,KOMEArmyMovementOrder holder,EntityPlayerMP player,
            long now,KOMEJoinBattleEgressService.PhysicalAccess physical,StrategicAccess strategic,
            List<CompanyPlan> initial){
        KOMEFormalRetreatBatch batch=holder.formalRetreatBatch;
        List<String> ids=new ArrayList<String>();for(KOMEFormalRetreatBatch.Member m:batch.members)ids.add(m.companyId);
        KOMEJoinBattleDeploymentReceipt receipt=batch.receiptId.isEmpty()?null:
            data.getJoinBattleDeploymentReceipts().get(batch.receiptId);
        KOMEConflictRecord conflict=data.getConflictService().get(batch.tileId);
        if(batch.finalized())return result(Code.RETREATED,receipt,conflict,ids,"This accepted retreat is already complete.");
        try{
            KOMEFormalRetreatBatch.validateWorld(data);
            if(legacyIncomplete(data,batch.conflictId))
                throw new IllegalArgumentException("Incomplete legacy retreat group authority requires operator review.");
            for(int i=0;i<batch.members.size();i++){
                KOMEFormalRetreatBatch.Member member=batch.members.get(i);
                if(member.progress==KOMEFormalRetreatBatch.Progress.COMPLETE)continue;
                KOMEArmyMovementOrder order=data.armyMovements.get(member.orderId);
                KOMEArmyCompany company=data.armyCompanies.get(member.companyId);
                if(order==null||company==null||!member.companyId.equals(order.companyId)
                        ||!member.owner.equals(order.owner)||!member.owner.equals(company.owner))
                    throw new IllegalArgumentException("Exact accepted company/order authority is unavailable: "+member.companyId);
                if(member.progress==KOMEFormalRetreatBatch.Progress.PREPARED){
                    if(order.conflictRelease==null){
                        KOMEConflictRecord current=data.getConflictService().get(batch.tileId);
                        if(current==null||!current.isActive()||!batch.conflictId.equals(current.getConflictId()))
                            throw new IllegalArgumentException("The accepted conflict changed before departure publication.");
                        KOMEConflictMovementHandoff.Result release=KOMEConflictMovementHandoff.releaseCommitment(data,
                            new ValidatedDepartureRequest(batch.tileId,member.companyId,company.faction,
                                ExpectedConflict.at(batch.conflictId,current.getRevision())),member.orderId,
                            KOMEConflictMovementHandoff.Outcome.FORMAL_RETREAT,
                            new Context(Math.max(now,current.getLastTransition().timestampMillis),batch.commander.toString(),EGRESS_REASON));
                        if(!release.accepted())throw new IllegalArgumentException(release.reason);
                    }
                    if(order.conflictRelease.outcome!=KOMEConflictMovementHandoff.Outcome.FORMAL_RETREAT
                            ||!batch.conflictId.equals(order.conflictRelease.conflictId)
                            ||batch.acceptedAtMillis>order.conflictRelease.appliedAtMillis)
                        throw new IllegalArgumentException("Movement release belongs to a different accepted action.");
                    company.movementOrderId=order.id;
                    KOMEMovementRetreatService.publish(data,order,
                        KOMEMovementRetreatService.acceptedPlan(order,member.route),now);
                    // Debit and its durable progress marker are published in the same world-data turn.
                    company.movementAllowance=Math.max(0,company.movementAllowance-
                        (member.allowanceBefore-member.allowanceAfter));
                    order.dailyStepsRemaining=company.movementAllowance;
                    batch=saveBatch(data,holder,batch.member(i,KOMEFormalRetreatBatch.Progress.RELEASED));
                    member=batch.members.get(i);
                }
                if(!member.route.equals(order.routeTiles)||order.conflictRelease==null
                        ||!batch.conflictId.equals(order.conflictRelease.conflictId)
                        ||order.conflictRelease.outcome!=KOMEConflictMovementHandoff.Outcome.FORMAL_RETREAT)
                    throw new IllegalArgumentException("Accepted retreat route identity changed: "+member.companyId);
                if(order.completedSteps>=1&&order.currentRouteIndex>=1){
                    batch=saveBatch(data,holder,batch.member(i,KOMEFormalRetreatBatch.Progress.COMPLETE));continue;
                }
                if(!member.orderId.equals(company.movementOrderId))
                    throw new IllegalArgumentException("Accepted company has a different movement link: "+member.companyId);
                if(KOMEArmyMovementOrder.WAITING_NEXT_STEP.equals(order.status)){
                    StrategicPreparation prepared=initial==null?null:initial.get(i).departure;
                    boolean owned=prepared==null;
                    try{
                        if(prepared==null)prepared=strategic.prepare(data,player,order,member.route.get(1),now);
                        if(!strategic.execute(data,prepared,now))
                            throw new IllegalArgumentException("Immediate departure remains pending for "+member.companyId);
                    }finally{if(owned&&prepared!=null)strategic.release(prepared);}
                    batch=saveBatch(data,holder,batch.member(i,KOMEFormalRetreatBatch.Progress.DEPARTED));
                }
            }
            if(batch.phase==KOMEFormalRetreatBatch.Phase.STRATEGIC_PENDING){
                java.util.Set<String> unfinished=new java.util.HashSet<String>();
                for(KOMEFormalRetreatBatch.Member member:batch.members)
                    if(member.progress!=KOMEFormalRetreatBatch.Progress.COMPLETE)unfinished.add(member.orderId);
                strategic.complete(data,player,now,unfinished);
            }
            for(int i=0;i<batch.members.size();i++){
                KOMEFormalRetreatBatch.Member member=batch.members.get(i);
                if(member.progress==KOMEFormalRetreatBatch.Progress.COMPLETE)continue;
                KOMEArmyMovementOrder order=data.armyMovements.get(member.orderId);
                if(order.completedSteps<1||order.currentRouteIndex<1)
                    throw new IllegalArgumentException("Arrival reconstruction remains pending for "+member.companyId);
                batch=saveBatch(data,holder,batch.member(i,KOMEFormalRetreatBatch.Progress.COMPLETE));
            }
            if(batch.phase==KOMEFormalRetreatBatch.Phase.STRATEGIC_PENDING)
                batch=saveBatch(data,holder,batch.advance(KOMEFormalRetreatBatch.Phase.STRATEGIC_COMPLETE,false));
            if(batch.phase==KOMEFormalRetreatBatch.Phase.STRATEGIC_COMPLETE){
                KOMEConflictRecord current=data.getConflictService().get(batch.tileId);
                boolean abandoned=false;
                if(current!=null&&batch.conflictId.equals(current.getConflictId())){
                    if(current.isActive()){
                        KOMEConflictLifecycleService.OrdinaryAbandonmentResult end=
                            KOMEConflictLifecycleService.INSTANCE.reconcileOrdinaryConflictAfterCommitmentChange(data,
                                batch.tileId,ExpectedConflict.at(batch.conflictId,current.getRevision()),
                                new Context(Math.max(now,current.getLastTransition().timestampMillis),
                                    batch.commander.toString(),EGRESS_REASON));
                        if(end.code==KOMEConflictLifecycleService.OrdinaryAbandonmentCode.END_FAILED
                                ||end.code==KOMEConflictLifecycleService.OrdinaryAbandonmentCode.UNRESOLVED_AUTHORITY)
                            throw new IllegalArgumentException(end.reason);
                        abandoned=end.ended();
                    }else abandoned=KOMEConflictLifecycleService.FORMAL_RETREAT_ABANDONMENT_REASON.equals(current.getLastTransition().reason);
                }
                // A replaced conflict is never mutated. Its old physical receipt is still authoritative.
                batch=saveBatch(data,holder,batch.advance(KOMEFormalRetreatBatch.Phase.ABANDONMENT_RECONCILED,abandoned));
            }
            receipt=batch.receiptId.isEmpty()?null:data.getJoinBattleDeploymentReceipts().get(batch.receiptId);
            if(!batch.receiptId.isEmpty()&&(receipt==null||!batch.commander.equals(receipt.getPlayerId())
                    ||!batch.conflictId.equals(receipt.getConflictId())))
                throw new IllegalArgumentException("Exact accepted physical receipt is unavailable.");
            if(batch.phase==KOMEFormalRetreatBatch.Phase.ABANDONMENT_RECONCILED){
                if(receipt!=null&&receipt.isOpen()){
                    if(receipt.getState()==State.PENDING_ENTRY)
                        throw new IllegalArgumentException("Accepted entry cancellation remains unresolved.");
                    receipt=KOMEJoinBattleEgressService.INSTANCE.requestEgress(data,receipt,now,EGRESS_REASON);
                }
                batch=saveBatch(data,holder,batch.advance(KOMEFormalRetreatBatch.Phase.EGRESS_HANDED_OFF,false));
            }
            if(receipt!=null&&receipt.isOpen()){
                if(player==null) return result(Code.EGRESS_PENDING,receipt,conflict,ids,"Strategic retreat completed; safe return will resume on login.");
                KOMEJoinBattleEgressService.CompletionResult egress=physical==null
                    ?KOMEJoinBattleEgressService.INSTANCE.requestAndCompleteDetailed(data,player,receipt.getReceiptId(),now,EGRESS_REASON)
                    :KOMEJoinBattleEgressService.INSTANCE.requestAndCompleteDetailed(data,player,receipt.getReceiptId(),now,EGRESS_REASON,physical);
                if(egress.completion!=KOMEJoinBattleEgressService.Completion.CLOSED)
                    return result(Code.EGRESS_PENDING,receipt,conflict,ids,"Strategic retreat completed; physical return remains pending: "+egress.reason);
            }
            batch=saveBatch(data,holder,batch.advance(KOMEFormalRetreatBatch.Phase.FINALIZED,false));
            List<String> outcomes=new ArrayList<String>();
            for(KOMEFormalRetreatBatch.Member m:batch.members)outcomes.add(m.companyId+" retreated "+batch.tileId+
                " -> "+m.route.get(1)+"; movement "+m.allowanceBefore+" -> "+m.allowanceAfter);
            return result(Code.RETREATED,receipt,conflict,ids,"Formal retreat accepted from "+batch.tileId+" ("+batch.conflictId+"): "+
                join(outcomes)+(batch.abandoned?"; attacking force withdrew and the battle ended.":"."));
        }catch(IllegalArgumentException blocked){
            return result(Code.RETREAT_BLOCKED,receipt,conflict,ids,"Accepted retreat recovery remains pending: "+blocked.getMessage());
        }
    }


    private Result finishEgress(KOMEWorldData data, EntityPlayerMP player,
            KOMEJoinBattleDeploymentReceipt receipt, List<String> companyIds,
            String completedOutcomes, long nowMillis,
            KOMEJoinBattleEgressService.PhysicalAccess testPhysical) {
        KOMEJoinBattleEgressService.CompletionResult completion =
            testPhysical == null
                ? KOMEJoinBattleEgressService.INSTANCE.requestAndCompleteDetailed(data, player,
                    receipt.getReceiptId(), nowMillis, EGRESS_REASON)
                : KOMEJoinBattleEgressService.INSTANCE.requestAndCompleteDetailed(data, player,
                    receipt.getReceiptId(), nowMillis, EGRESS_REASON, testPhysical);
        if (completion.completion == KOMEJoinBattleEgressService.Completion.CLOSED)
            return result(Code.RETREATED, receipt, null,companyIds,
                "Formal retreat accepted from " + identity(receipt) + ": "
                    + (completedOutcomes == null
                        ? retreatOutcomes(data, companyIds) : completedOutcomes) + ".");
        return result(Code.EGRESS_PENDING, receipt, null,companyIds,
            "Strategic retreat was accepted from " + identity(receipt)
                + ", but physical egress remains pending: " + completion.reason);
    }

    private static String stepOutcomes(List<CompanyPlan> plans) {
        if (plans.isEmpty()) return "no personally commanded companies";
        List<String> outcomes = new ArrayList<String>();
        for (CompanyPlan plan : plans)
            outcomes.add(plan.company.id + " retreated " + plan.departure.originTile
                + " -> " + plan.departure.destinationTile + "; movement "
                + plan.allowanceBefore + " -> " + plan.allowanceAfter);
        return join(outcomes);
    }

    private static String retreatOutcomes(KOMEWorldData data, List<String> companyIds) {
        if (companyIds.isEmpty()) return "no personally commanded companies";
        List<String> outcomes = new ArrayList<String>();
        for (String companyId : companyIds) {
            KOMEArmyCompany company = data.armyCompanies.get(companyId);
            KOMEArmyMovementOrder order = company == null ? null
                : data.armyMovements.get(company.movementOrderId);
            if (order == null) for (KOMEArmyMovementOrder candidate : data.armyMovements.values())
                if (candidate != null && companyId.equals(candidate.companyId)
                        && KOMEMovementRetreatService.isAcceptedFormalRetreat(candidate)) {
                    order = candidate; break;
                }
            String from = order == null ? "?" : order.originTile;
            String to = company == null ? "?" : company.currentTile;
            int remaining = company == null ? 0 : Math.max(0, Math.min(
                company.movementAllowance, company.getTilesPerDay()));
            outcomes.add(companyId + " retreated " + from + " -> " + to
                + "; movement now " + remaining);
        }
        return join(outcomes);
    }

    private static List<String> commandedCommitments(KOMEWorldData data,
            KOMEConflictRecord conflict, UUID playerId) {
        List<String> ids = new ArrayList<String>();
        for (String companyId : conflict.getCommitments().keySet()) {
            KOMEArmyCompany company = data.armyCompanies.get(companyId);
            if (company != null && KOMECompanyCommandAuthority.canPersonallyCommand(
                    data, company, playerId)) ids.add(companyId);
        }
        Collections.sort(ids);
        return ids;
    }

    private static Target findTarget(KOMEWorldData data,UUID playerId,String conflictId){
        String wanted=canonicalRequestedConflictId(conflictId);
        if(wanted.isEmpty())return null;
        for(KOMEConflictRecord conflict:data.getConflictService().records().values()){
            if(conflict==null||!conflict.isActive()||!wanted.equals(conflict.getConflictId()))continue;
            List<String> ids=commandedCommitments(data,conflict,playerId);
            return ids.isEmpty()?null:new Target(conflict,ids);
        }
        return null;
    }

    /** Human command arguments are case-insensitive; persisted conflict identities stay canonical. */
    private static String canonicalRequestedConflictId(String conflictId) {
        String requested = conflictId == null ? "" : conflictId.trim();
        if (requested.isEmpty()) return "";
        String upper = requested.toUpperCase(Locale.ROOT);
        try {
            return KOMEConflictIdAllocator.requireIdentity(upper);
        } catch (IllegalArgumentException malformed) {
            // Retain the supplied value so the existing no-authority response identifies it.
            return requested;
        }
    }

    private static List<Target> targets(KOMEWorldData data,UUID playerId){
        List<Target> result=new ArrayList<Target>();
        for(KOMEConflictRecord conflict:data.getConflictService().records().values()){
            if(conflict==null||!conflict.isActive())continue;
            List<String> ids=commandedCommitments(data,conflict,playerId);
            if(!ids.isEmpty())result.add(new Target(conflict,ids));
        }
        Collections.sort(result,new Comparator<Target>(){
            @Override public int compare(Target left,Target right){
                return left.conflict.getConflictId().compareTo(right.conflict.getConflictId());
            }
        });
        return result;
    }

    private static String choices(KOMEWorldData data,List<Target> targets){
        List<String> values=new ArrayList<String>();
        for(Target target:targets){
            List<String> companies=new ArrayList<String>();
            for(String id:target.companyIds){
                KOMEArmyCompany company=data.armyCompanies.get(id);
                companies.add(company==null||company.name==null||company.name.trim().isEmpty()
                    ?id:id+" ("+company.name.trim()+")");
            }
            values.add(target.conflict.getTileId()+" ("+target.conflict.getConflictId()+"): "
                +join(companies));
        }
        return "You command forces in multiple battles: "+join(values)
            +". Use /kome battle retreat <conflictId>.";
    }

    private static List<CompanyPlan> prepare(KOMEWorldData data,
            KOMEConflictRecord conflict, List<String> companyIds) {
        List<CompanyPlan> plans = new ArrayList<CompanyPlan>();
        for (String companyId : companyIds) {
            KOMEArmyCompany company = data.armyCompanies.get(companyId);
            KOMEConflictRecord.Commitment commitment = conflict.getCommitments().get(companyId);
            KOMECompanyCoherenceService.Assessment coherence = company == null ? null
                : KOMECompanyCoherenceService.INSTANCE.assess(data, company);
            if (company == null || commitment == null || coherence.status
                    == KOMECompanyCoherenceService.Status.INCOHERENT)
                throw new IllegalArgumentException("Company " + companyId + " is not coherent"
                    + (coherence == null ? "." : ": " + issueCodes(coherence) + "."));
            if (!conflict.getTileId().equals(KOMEConquestTile.normalizeId(company.currentTile)))
                throw new IllegalArgumentException("Company " + companyId
                    + " is no longer at the conflict tile.");
            KOMEArmyMovementOrder order = data.armyMovements.get(commitment.movementOrderId);
            if (order == null || !company.id.equals(order.companyId)
                    || !order.id.equals(company.movementOrderId)
                    || !order.id.equals(commitment.movementOrderId)
                    || !java.util.Objects.equals(order.owner, company.owner)
                    || !KOMEAlliance.normalizeFactionKey(order.ownerFaction).equals(
                        KOMEAlliance.normalizeFactionKey(company.faction))
                    || !conflict.getTileId().equals(KOMEConquestTile.normalizeId(order.currentTile))
                    || !KOMEArmyMovementOrder.CONFLICT_HELD.equals(order.status)
                    || !conflict.getConflictId().equals(order.conflictHoldId))
                throw new IllegalArgumentException("Company " + companyId
                    + " has no exact conflict-held retreat route.");
            try {
                plans.add(new CompanyPlan(company, order,
                    KOMEMovementRetreatService.prepare(data, order, true)));
            } catch (IllegalArgumentException blocked) {
                throw new IllegalArgumentException("company " + companyId + ": "
                    + blocked.getMessage());
            }
        }
        Collections.sort(plans, new Comparator<CompanyPlan>() {
            @Override public int compare(CompanyPlan left, CompanyPlan right) {
                return left.company.id.compareTo(right.company.id);
            }
        });
        return plans;
    }

    private static String identity(KOMEJoinBattleDeploymentReceipt receipt) {
        return receipt.getTileId() + " (" + receipt.getConflictId() + ")";
    }

    private static String identity(KOMEConflictRecord conflict){
        return conflict.getTileId()+" ("+conflict.getConflictId()+")";
    }

    private static String join(List<String> ids) {
        StringBuilder text = new StringBuilder();
        for (String id : ids) {
            if (text.length() > 0) text.append(", ");
            text.append(id);
        }
        return text.toString();
    }

    private static String issueCodes(KOMECompanyCoherenceService.Assessment assessment) {
        StringBuilder text = new StringBuilder();
        for (KOMECompanyCoherenceService.Issue issue : assessment.issues) {
            if (text.length() > 0) text.append(", ");
            text.append(issue.code.name());
        }
        return text.toString();
    }

    private static Result result(Code code,KOMEJoinBattleDeploymentReceipt receipt,
            KOMEConflictRecord conflict,List<String> companies,String message){
        return new Result(code,receipt,conflict,companies,message);
    }
}
