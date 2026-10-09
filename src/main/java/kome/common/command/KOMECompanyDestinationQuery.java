package kome.common.command;

import java.util.*;
import kome.common.KOMEReflection;
import kome.common.data.*;
import net.minecraft.entity.player.EntityPlayerMP;
import static kome.common.data.KOMECompanyDestinationOptions.*;

/** Server-thread-only bounded catalogue. No entity/world scan, persistent cache, or movement mutation. */
public final class KOMECompanyDestinationQuery {
    public static final KOMECompanyDestinationQuery INSTANCE = new KOMECompanyDestinationQuery(KOMECompanyMovementService.INSTANCE);
    private final KOMECompanyMovementService movement;
    public KOMECompanyDestinationQuery(KOMECompanyMovementService movement) { this.movement=Objects.requireNonNull(movement); }
    public KOMECompanyDestinationOptions query(KOMEWorldData data,EntityPlayerMP actor,UUID session,long sequence,
            String destination,int limit,String after,String catalogue) {
        if (session==null) throw new IllegalArgumentException("Session required");
        if (data==null || actor==null || actor.worldObj==null || actor.worldObj.isRemote
                || actor.worldObj.provider==null || KOMEReflection.getEntityUUID(actor)==null
                || sequence<=0 || limit<1 || limit>MAX_ROWS || after==null || catalogue==null
                || !KOMEConquestTile.isCanonicalTileId(destination))
            return error(session,sequence,destination,Result.INVALID_REQUEST);
        if(data.armyCompanies.size()>MAX_CATALOGUE) return error(session,sequence,destination,Result.CATALOGUE_LIMIT);
        List<String> candidates=new ArrayList<String>();
        for(KOMEArmyCompany company:data.armyCompanies.values()) {
            if(company==null) continue;
            if(!movement.canPlayerConsiderCompany(data,actor,company)) continue;
            if(company.units.size()>MAX_MEMBERS) return error(session,sequence,destination,Result.COMPANY_LIMIT);
            // Fail closed: empty/all-missing membership cannot establish Campaign classification.
            // A known Campaign member plus a missing/ORDINARY member is retained for canonical denial.
            boolean campaign=false;
            for(UUID member:company.units) if(KOMEHiredUnitClassification.isCampaignUnit(data,member)) {campaign=true;break;}
            if(campaign) candidates.add(company.id);
        }
        Collections.sort(candidates);
        // Exact identity window fingerprint, without trusting a client cursor as permission.
        String token=java.util.UUID.nameUUIDFromBytes(candidates.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        if(!after.isEmpty() && (!token.equals(catalogue) || !candidates.contains(after)))
            return error(session,sequence,destination,Result.CATALOGUE_CHANGED);
        List<Row> rows=new ArrayList<Row>();String last=after;
        KOMECompanyMovementService.ArrivalBudget budget=new KOMECompanyMovementService.ArrivalBudget(MAX_ARRIVAL_CHECKS,MAX_ROUTE_TILES);
        for(String company:candidates) {
            if(!after.isEmpty() && company.compareTo(after)<=0) continue;
            if(rows.size()==limit) return page(session,sequence,destination,Result.OK,rows,token,last,true);
            try {
                KOMECompanyMovementService.Evaluation evaluation=movement.evaluate(data,actor,company,destination,budget);
                rows.add(new Row(evaluation));last=company;
            } catch(KOMECompanyMovementService.EvaluationLimitExceeded exhausted) {
                if (!exhausted.deferred.exceedsSingleRouteLimit)
                    return page(session,sequence,destination,Result.ARRIVAL_BUDGET_EXCEEDED,rows,token,last,true);
                // One company cannot fit even a fresh request. Expose unknown eligibility and advance past it.
                rows.add(new Row(exhausted.deferred));last=company;
            }
        }
        return page(session,sequence,destination,Result.OK,rows,token,last,false);
    }
    private static KOMECompanyDestinationOptions page(UUID session,long sequence,String destination,Result result,
            List<Row> rows,String token,String last,boolean more) {
        return new KOMECompanyDestinationOptions(session,sequence,destination,result,rows,token,last,more);
    }
    public static KOMECompanyDestinationOptions error(UUID session,long sequence,String destination,Result result) {
        return page(session,sequence,destination,result,Collections.<Row>emptyList(),"","",false);
    }
}
