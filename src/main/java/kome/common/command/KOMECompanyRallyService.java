package kome.common.command;

import java.util.*;
import java.util.function.*;
import kome.common.KOMEReflection;
import kome.common.data.*;
import net.minecraft.entity.player.EntityPlayerMP;
import static kome.common.data.KOMECompanyDestinationOptions.*;
import static kome.common.data.KOMECompanyRallySnapshot.*;

/** Server-thread-only bounded Rally sessions. Exactly one company evaluation/execution globally per tick. */
public final class KOMECompanyRallyService {
    private final KOMECompanyMovementService movement;
    private final KOMECompanyDestinationQuery projections;
    private final List<Session> sessions=new ArrayList<Session>();
    private int turn;
    public KOMECompanyRallyService(KOMECompanyMovementService movement){this.movement=movement;projections=new KOMECompanyDestinationQuery(movement);}
    private static final class Candidate {
        final String id,name,origin,faction;final int speed,allowance;
        Candidate(KOMEArmyCompany c){id=c.id;name=c.name;origin=c.currentTile;faction=c.faction;speed=c.getTilesPerDay();allowance=Math.max(0,Math.min(c.movementAllowance,speed));}
    }
    private static final class Session {
        Object connection;EntityPlayerMP actor;UUID actorId,serverSession,client,token;
        KOMEWorldData data;String destination;long expires;
        List<Candidate> candidates;List<Entry> rows=new ArrayList<Entry>();
        BooleanSupplier current;Consumer<KOMECompanyRallySnapshot> publish;
        Phase phase=Phase.LOADING;
        int revision,evaluated,included,processed,rallied,pending;
        String message="";
    }
    private Session find(Object connection){for(Session s:sessions)if(s.connection==connection)return s;return null;}
    public void forget(Object connection){sessions.removeIf(s->s.connection==connection);}
    public void clear(){sessions.clear();turn=0;}
    public int size(){return sessions.size();}
    public int retainedRows(){int count=0;for(Session s:sessions)count+=s.rows.size();return count;}
    private boolean live(Session s){return s.current.getAsBoolean()&&s.actor.playerNetServerHandler==s.connection
        &&Objects.equals(s.actorId,KOMEReflection.getEntityUUID(s.actor))
        &&s.serverSession.equals(KOMECompanyMovementSessions.current(s.actor));}
    public KOMECompanyRallySnapshot open(Object connection,KOMEWorldData data,EntityPlayerMP actor,UUID client,String destination,
            BooleanSupplier current,Consumer<KOMECompanyRallySnapshot> publish,long now){
        Session old=find(connection);
        if(old!=null&&old.phase==Phase.EXECUTING)return error(client,destination,"A confirmed Rally is still executing.");
        forget(connection);purge(now);
        if(data==null||actor==null||actor.playerNetServerHandler!=connection||!current.getAsBoolean()
                ||!KOMEConquestTile.isCanonicalTileId(destination))return error(client,destination,"A current server connection and tile are required.");
        if(sessions.size()>=MAX_SESSIONS)return error(client,destination,"Rally capacity is busy. Try again shortly.");
        if(data.armyCompanies.size()>MAX_CATALOGUE)return error(client,destination,"Company catalogue exceeds the Rally safety limit.");
        List<Candidate> candidates=new ArrayList<Candidate>();
        for(KOMEArmyCompany c:data.armyCompanies.values()){
            if(c==null||!movement.canPlayerConsiderCompany(data,actor,c))continue;
            if(c.units.size()>MAX_MEMBERS)return error(client,destination,"A company exceeds the bounded member limit.");
            boolean campaign=false;for(UUID id:c.units)if(KOMEHiredUnitClassification.isCampaignUnit(data,id)){campaign=true;break;}
            if(!campaign)continue;
            if(candidates.size()==MAX_CANDIDATES)return error(client,destination,"Rally supports at most "+MAX_CANDIDATES+" Campaign companies. No partial Rally was prepared.");
            candidates.add(new Candidate(c));
        }
        candidates.sort(Comparator.comparing(c->c.id));
        Session s=new Session();s.connection=connection;s.data=data;s.actor=actor;s.actorId=KOMEReflection.getEntityUUID(actor);
        s.serverSession=KOMECompanyMovementSessions.current(actor);s.client=client;s.token=UUID.randomUUID();s.destination=destination;
        s.expires=now+LIFETIME_MILLIS;s.candidates=candidates;s.current=current;s.publish=publish;
        if(candidates.isEmpty())s.phase=Phase.READY;
        sessions.add(s);return snapshot(s,null);
    }
    public KOMECompanyRallySnapshot confirm(Object connection,EntityPlayerMP actor,UUID client,UUID token,String destination,long now){
        Session s=find(connection);
        if(s==null||s.actor!=actor||!s.client.equals(client)||!s.token.equals(token)||!s.destination.equals(destination)
                ||now>=s.expires||!live(s))return error(client,destination,"Rally preview expired or belongs to another connection. Refresh.");
        if(s.phase!=Phase.READY||s.included==0)return null; // Consumed, incomplete or empty: never execute twice.
        s.phase=Phase.EXECUTING;s.expires=now+LIFETIME_MILLIS;s.revision++;
        return snapshot(s,null);
    }
    public void cancel(Object connection,UUID client,UUID token){
        Session s=find(connection);if(s!=null&&s.client.equals(client)&&(token.equals(new UUID(0,0))||s.token.equals(token))
            &&s.phase!=Phase.EXECUTING)forget(connection);
    }
    private void purge(long now){
        Iterator<Session> it=sessions.iterator();
        while(it.hasNext()){
            Session s=it.next();boolean live;
            try{live=live(s);}catch(RuntimeException stale){live=false;}
            if(!live||now>=s.expires&&s.phase!=Phase.EXECUTING){
                it.remove();if(live){s.phase=Phase.ERROR;s.message="Rally session expired. Refresh before confirming.";s.revision++;publish(s,null);}
            }
        }
    }
    public void tick(long now){
        purge(now);if(sessions.isEmpty())return;
        for(int i=0;i<sessions.size();i++){
            turn=Math.floorMod(turn,sessions.size());Session s=sessions.get(turn++);if(s.phase!=Phase.LOADING&&s.phase!=Phase.EXECUTING)continue;
            if(s.phase==Phase.LOADING)previewStep(s);else {s.expires=now+LIFETIME_MILLIS;executeStep(s);}
            return;
        }
    }
    private Row fallback(Session s,Candidate c,String code){
        return new Row(c.id,c.name,c.origin,s.destination,Entitlement.FOOT_OR_MIXED,c.speed,c.allowance,0,Collections.<String>emptyList(),
            KOMECompanyMovementService.Status.BLOCKED,KOMECompanyMovementService.ScheduleMode.DAILY,false,false,false,false,0,0,0,code,
            KOMECompanyMovementResult.playerDenialDetail(code),"",-1,KOMECompanyMovementService.Consequence.NONE,"",-1,"","");
    }
    private Row evaluate(Session s,Candidate c){
        KOMEArmyCompany company=s.data.armyCompanies.get(c.id);
        if(company==null)return fallback(s,c,"UNKNOWN_COMPANY");
        if(!movement.canPlayerConsiderCompany(s.data,s.actor,company))return fallback(s,c,"UNAUTHORIZED");
        if(company.units.size()>MAX_MEMBERS)return fallback(s,c,"INELIGIBLE_MEMBER");
        try{return projections.evaluateRow(s.data,s.actor,c.id,s.destination,new KOMECompanyMovementService.ArrivalBudget(MAX_ARRIVAL_CHECKS,MAX_ROUTE_TILES));}
        catch(KOMECompanyMovementService.EvaluationLimitExceeded deferred){return new Row(deferred.deferred);}
    }
    public static boolean immediate(Row row){
        return row.evaluationState==EvaluationState.COMPLETE&&row.status==KOMECompanyMovementService.Status.CAN_START_AND_ARRIVE_NOW
            &&row.canStartNow&&row.canArriveNow&&row.legalRoute&&!row.confirmationFingerprint.isEmpty()&&!row.legacyFingerprint.isEmpty();
    }
    public static String skipReason(Row row){
        if(row.currentTile.equals(row.destinationTile))return "Already here";
        if(row.evaluationState!=EvaluationState.COMPLETE)return "Route evaluation deferred";
        if(row.status==KOMECompanyMovementService.Status.CAN_START_BUT_CONTINUES_LATER)return "Requires later movement days";
        return row.denialDetail.isEmpty()?"Cannot arrive immediately":row.denialDetail;
    }
    private void previewStep(Session s){
        Candidate c=s.candidates.get(s.evaluated);Entry entry;
        try{
            Row row=evaluate(s,c);boolean included=immediate(row);
            KOMEArmyCompany company=s.data.armyCompanies.get(c.id);
            String faction=company!=null&&movement.canPlayerConsiderCompany(s.data,s.actor,company)?company.faction:c.faction;
            entry=new Entry(s.evaluated,row,faction,included,Outcome.PREVIEW,included?"Will rally":concise(skipReason(row)));
        }catch(RuntimeException failure){
            s.phase=Phase.ERROR;s.message="Rally preview could not be completed. Nothing was moved. Refresh.";s.revision++;publish(s,null);return;
        }
        s.rows.add(entry);s.evaluated++;if(entry.included)s.included++;
        if(s.evaluated==s.candidates.size())s.phase=Phase.READY;
        s.revision++;publish(s,entry);
    }
    private void executeStep(Session s){
        Entry before=s.rows.get(s.processed);Entry after=before;
        if(before.included){
            Candidate c=s.candidates.get(s.processed);Row row=before.company;Outcome outcome=Outcome.SKIPPED;String reason;boolean submitted=false;
            try{
                KOMEArmyCompany company=s.data.armyCompanies.get(c.id);
                if(company!=null&&!before.company.currentTile.equals(company.currentTile))reason="Source changed after preview";
                else {
                    row=evaluate(s,c);
                    if(!immediate(row))reason=skipReason(row);
                    else {
                        // Fresh per-company confirmation deliberately observes prior Rally arrivals.
                        submitted=true;KOMECompanyMovementResult result=KOMECompanyMapMovementSubmission.submit(movement,s.data,s.actor,
                            s.client,s.processed+1,s.evaluated,c.id,s.destination,row.legacyFingerprint,row.confirmationFingerprint);
                        if(result.outcome==KOMECompanyMovementResult.Outcome.ARRIVED){
                            outcome=Outcome.RALLIED;reason="Rallied"+(row.consequence==KOMECompanyMovementService.Consequence.NONE?"":": "+row.consequence.name().replace("EXPECTED_","").replace('_',' '));s.rallied++;
                        }else if(result.accepted()){outcome=Outcome.PENDING;reason="Order accepted; arrival not confirmed";s.pending++;}
                        else reason=result.detail;
                    }
                }
            }catch(RuntimeException failure){
                if(submitted){outcome=Outcome.PENDING;s.pending++;}
                reason="Movement outcome could not be confirmed. Inspect this company before retrying.";
            }
            after=new Entry(before.index,before.company,before.faction,true,outcome,concise(reason));
        }else after=new Entry(before.index,before.company,before.faction,false,Outcome.SKIPPED,before.reason);
        s.rows.set(s.processed,after);s.processed++;
        if(s.processed==s.rows.size())s.phase=Phase.DONE;
        s.revision++;publish(s,after);
    }
    private static String concise(String reason){return reason.length()>256?reason.substring(0,253)+"...":reason;}
    private void publish(Session s,Entry entry){
        try{s.publish.accept(snapshot(s,entry));}
        catch(RuntimeException failure){
            // Never let an oversized projection or broken transport abort the server tick.
            if(s.phase!=Phase.EXECUTING&&s.phase!=Phase.DONE){
                s.phase=Phase.ERROR;s.message="Rally preview could not be published completely. Nothing was moved. Refresh.";s.revision++;
            }
            try{s.publish.accept(snapshot(s,null));}catch(RuntimeException unavailable){ }
        }
    }
    private KOMECompanyRallySnapshot snapshot(Session s,Entry entry){
        String message=s.message;
        if(message.isEmpty())message=s.phase==Phase.LOADING?"Loading complete Rally preview...":s.phase==Phase.READY?
            s.included+" will rally. "+(s.candidates.size()-s.included)+" skipped.":s.phase==Phase.EXECUTING?"Rallying companies...":
            s.rallied+" companies rallied. "+(s.candidates.size()-s.rallied-s.pending)+" skipped."+(s.pending==0?"":" "+s.pending+" orders awaiting arrival.");
        return new KOMECompanyRallySnapshot(s.client,s.token,s.destination,s.phase,s.revision,s.candidates.size(),s.evaluated,s.included,s.processed,s.rallied,message,entry);
    }
    private KOMECompanyRallySnapshot error(UUID client,String destination,String message){
        return new KOMECompanyRallySnapshot(client,new UUID(0,0),destination,Phase.ERROR,0,0,0,0,0,0,message,null);
    }
}