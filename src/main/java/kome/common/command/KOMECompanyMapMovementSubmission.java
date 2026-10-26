package kome.common.command;

import java.util.UUID;
import kome.common.data.*;
import kome.common.network.KOMECompanyMapProjection;
import net.minecraft.entity.player.EntityPlayerMP;

/** Destination-workflow adapter. All decisions and execution remain in the shared movement boundary. */
public final class KOMECompanyMapMovementSubmission {
    private KOMECompanyMapMovementSubmission() { }
    public static KOMECompanyMovementResult submit(KOMECompanyMovementService service,KOMEWorldData data,EntityPlayerMP actor,
            UUID session,long sequence,long preview,String company,String destination,String legacy,String guard) {
        KOMEArmyCompany found=data==null?null:data.armyCompanies.get(company);
        boolean readable=KOMECompanyMapProjection.directlyVisible(data,actor,found);
        if(found!=null&&!service.canPlayerConsiderCompany(data,actor,found))
            return KOMECompanyMovementResult.denied(session,sequence,preview,company,destination,
                KOMECompanyMovementResult.Outcome.REJECTED,readable?"UNAUTHORIZED":"UNKNOWN_COMPANY",
                KOMECompanyMovementResult.playerDenialDetail(readable?"UNAUTHORIZED":"UNKNOWN_COMPANY"));
        KOMECompanyMovementService.Submission submitted=service.submitConfirmed(data,actor,company,destination,
            KOMECompanyMovementService.Confirmation.fromTransport(company,destination,legacy,guard));
        if(!readable && !submitted.accepted)
            return KOMECompanyMovementResult.denied(session,sequence,preview,company,destination,
                KOMECompanyMovementResult.Outcome.REJECTED,"UNKNOWN_COMPANY",
                KOMECompanyMovementResult.playerDenialDetail("UNKNOWN_COMPANY"));
        return KOMECompanyMovementResult.from(session,sequence,preview,company,destination,submitted);
    }
}
