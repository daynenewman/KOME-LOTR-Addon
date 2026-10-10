package kome.client.gui;

import kome.common.network.KOMEPacketJoinBattleSelectionResult;
import kome.common.network.KOMEPacketJoinBattleViewResponse;
import kome.common.data.KOMEJoinBattleActionTokenService;

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
        if(!selectedCompanyId.isEmpty()&&!hasUsableActionToken())
            status="Battle view expired. Click Join Battle to refresh.";
    }
    public void accept(KOMEPacketJoinBattleSelectionResult result){
        view=result.current; status=result.message;
        if(result.status==KOMEPacketJoinBattleSelectionResult.Status.REJECTED)selectedCompanyId="";
    }
    public KOMEPacketJoinBattleViewResponse view(){return view;}
    public String selectedCompanyId(){return selectedCompanyId;}
    public String status(){return status;}
    public void setStatus(String value){status=value==null?"":value;}
    public boolean canAttemptSubmit(){return view.isAllowed()&&!selectedCompanyId.isEmpty();}
    public boolean hasUsableActionToken(){return KOMEJoinBattleActionTokenService.isUsableToken(view.actionToken);}
    public boolean canSubmit(){return canAttemptSubmit()&&hasUsableActionToken();}
    public void markTokenRefresh(){status="Battle view expired. Refreshing...";}
}
