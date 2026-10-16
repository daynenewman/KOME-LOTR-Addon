package kome.common.command;

import java.util.UUID;
import kome.common.data.*;
import net.minecraft.entity.player.EntityPlayerMP;

/** Destination-workflow adapter. All decisions and execution remain in the shared movement boundary. */
public final class KOMECompanyMapMovementSubmission {
    private KOMECompanyMapMovementSubmission() { }
    public static KOMECompanyMovementResult submit(KOMECompanyMovementService service,KOMEWorldData data,EntityPlayerMP actor,
            UUID session,long sequence,long preview,String company,String destination,String legacy,String guard) {
        KOMEArmyCompany found=data==null?null:data.armyCompanies.get(company);
        if(found!=null&&!service.canPlayerConsiderCompany(data,actor,found))
            return KOMECompanyMovementResult.denied(session,sequence,preview,company,destination,
                KOMECompanyMovementResult.Outcome.REJECTED,"UNAUTHORIZED",KOMECompanyMovementResult.playerDenialDetail("UNAUTHORIZED"));
        return KOMECompanyMovementResult.from(session,sequence,preview,company,destination,
            service.submitConfirmed(data,actor,company,destination,
                KOMECompanyMovementService.Confirmation.fromTransport(company,destination,legacy,guard)));
    }
}
