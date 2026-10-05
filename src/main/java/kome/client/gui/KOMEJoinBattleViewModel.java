package kome.client.gui;

import kome.common.network.KOMEPacketJoinBattleSelectionResult;
import kome.common.network.KOMEPacketJoinBattleViewResponse;

/** Pure client presentation state; never decides eligibility. */
public final class KOMEJoinBattleViewModel {
    private KOMEPacketJoinBattleViewResponse view;
    private String selectedCompanyId="", status="";
    public KOMEJoinBattleViewModel(KOMEPacketJoinBattleViewResponse view){accept(view);}
    public void accept(KOMEPacketJoinBattleViewResponse next){
        view=next; selectedCompanyId=""; status=next.reasonText;
    }
    public void select(String companyId){
        selectedCompanyId="";
        if(!view.isAllowed())return;
        for(KOMEPacketJoinBattleViewResponse.CompanyRow row:view.companyRows())
            if(row.selectable&&row.companyId.equals(companyId)){selectedCompanyId=companyId;break;}
    }
    public void accept(KOMEPacketJoinBattleSelectionResult result){
        view=result.current; status=result.message;
        if(result.status!=KOMEPacketJoinBattleSelectionResult.Status.READY_FOR_DEPLOYMENT)selectedCompanyId="";
    }
    public KOMEPacketJoinBattleViewResponse view(){return view;}
    public String selectedCompanyId(){return selectedCompanyId;}
    public String status(){return status;}
    public boolean canSubmit(){return view.isAllowed()&&!selectedCompanyId.isEmpty();}
}
