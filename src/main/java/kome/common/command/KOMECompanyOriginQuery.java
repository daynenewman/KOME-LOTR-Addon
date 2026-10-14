package kome.common.command;

import java.util.*;
import kome.common.data.*;
import net.minecraft.entity.player.EntityPlayerMP;
import static kome.common.data.KOMECompanyDestinationOptions.*;

/** Read-only source catalogue and direct one-company evaluation. Never routes via destination-wide paging. */
public final class KOMECompanyOriginQuery {
    public static final KOMECompanyOriginQuery INSTANCE=new KOMECompanyOriginQuery(KOMECompanyMovementService.INSTANCE);
    private final KOMECompanyMovementService movement;
    private final KOMECompanyDestinationQuery projections;
    public KOMECompanyOriginQuery(KOMECompanyMovementService movement){this.movement=movement;projections=new KOMECompanyDestinationQuery(movement);}
    private boolean candidate(KOMEWorldData data,EntityPlayerMP actor,KOMEArmyCompany c,String source) {
        if(c==null||!source.equals(c.currentTile)||!movement.canPlayerConsiderCompany(data,actor,c)
                ||c.units.isEmpty()||c.units.size()>MAX_MEMBERS)return false;
        Set<UUID> seen=new HashSet<UUID>();
        for(UUID member:c.units) {
            KOMEHiredUnitRecord record=data.hiredUnits.get(member);
            if(!seen.add(member)||record==null||!member.equals(record.entity)||!c.id.equals(record.companyId)
                    ||!KOMEHiredUnitClassification.isCampaignUnit(record))return false;
        }
        return true;
    }
    public KOMECompanySourceOptions source(KOMEWorldData data,EntityPlayerMP actor,UUID session,long seq,String source,String after,String token) {
        List<KOMECompanySourceOptions.Row> rows=new ArrayList<KOMECompanySourceOptions.Row>();
        if(data==null||!KOMEConquestTile.isCanonicalTileId(source)||data.armyCompanies.size()>MAX_CATALOGUE)
            return new KOMECompanySourceOptions(session,seq,source,Result.CATALOGUE_LIMIT,rows,"","",false,0);
        List<String> ids=new ArrayList<String>();
        for(KOMEArmyCompany c:data.armyCompanies.values())if(candidate(data,actor,c,source))ids.add(c.id);
        Collections.sort(ids);
        String current=UUID.nameUUIDFromBytes(ids.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        if(!after.isEmpty()&&(!current.equals(token)||!ids.contains(after)))
            return new KOMECompanySourceOptions(session,seq,source,Result.CATALOGUE_CHANGED,rows,"","",false,0);
        boolean more=false;String last="";
        for(String id:ids) {
            if(!after.isEmpty()&&id.compareTo(after)<=0)continue;
            if(rows.size()==MAX_ROWS){more=true;break;}
            KOMEArmyCompany c=data.armyCompanies.get(id);
            java.math.BigInteger population=KOMEPopulationService.getCompanyInvestmentCenti(data,c);
            rows.add(new KOMECompanySourceOptions.Row(id,c.name,c.units.size(),population==null?-1:population.longValueExact(),
                c.groundPopulation==0&&c.mountedPopulation>0,Math.max(0,Math.min(c.movementAllowance,c.getTilesPerDay())),
                c.getTilesPerDay(),c.isMoving()?"Moving":"Stationed",KOMEConflictMovementService.isActivelyCommitted(data,id)));
            last=id;
        }
        return new KOMECompanySourceOptions(session,seq,source,Result.OK,rows,current,last,more,0);
    }
    /** Destination-aware source-only page: shared projection and one shared arrival budget per request. */
    public KOMECompanyDestinationOptions destination(KOMEWorldData data,EntityPlayerMP actor,UUID session,long seq,
            String source,String destination,String after,String token){
        KOMECompanySourceOptions candidates=source(data,actor,session,seq,source,after,token);
        if(candidates.result!=Result.OK)return KOMECompanyDestinationQuery.error(session,seq,destination,candidates.result);
        List<Row> rows=new ArrayList<Row>();String last=after;
        KOMECompanyMovementService.ArrivalBudget budget=new KOMECompanyMovementService.ArrivalBudget(MAX_ARRIVAL_CHECKS,MAX_ROUTE_TILES);
        for(KOMECompanySourceOptions.Row candidate:candidates.rows){
            try{rows.add(projections.evaluateRow(data,actor,candidate.companyId,destination,budget));last=candidate.companyId;}
            catch(KOMECompanyMovementService.EvaluationLimitExceeded limited){
                if(!limited.deferred.exceedsSingleRouteLimit)
                    return new KOMECompanyDestinationOptions(session,seq,destination,Result.OK,rows,candidates.token,last,true);
                rows.add(new Row(limited.deferred));last=candidate.companyId;
            }
        }
        return new KOMECompanyDestinationOptions(session,seq,destination,Result.OK,rows,candidates.token,last,candidates.more);
    }
    public KOMECompanyDestinationOptions target(KOMEWorldData data,EntityPlayerMP actor,UUID session,long seq,
            String source,String company,String destination) {
        if(data==null||!candidate(data,actor,data.armyCompanies.get(company),source))
            return KOMECompanyDestinationQuery.error(session,seq,destination,Result.SOURCE_CHANGED);
        try {
            Row row=projections.evaluateRow(data,actor,company,destination,
                new KOMECompanyMovementService.ArrivalBudget(MAX_ARRIVAL_CHECKS,MAX_ROUTE_TILES));
            return new KOMECompanyDestinationOptions(session,seq,destination,Result.OK,Collections.singletonList(row),"",company,false);
        } catch(KOMECompanyMovementService.EvaluationLimitExceeded limited) {
            Row row=new Row(limited.deferred);
            return new KOMECompanyDestinationOptions(session,seq,destination,Result.OK,Collections.singletonList(row),"",company,false);
        }
    }
}