package kome.common.data;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import lotr.common.entity.npc.LOTREntityNPC;
/** Current service owns Quest; the third eligible report earns standing. */
public final class KOMELiegeProgressionInteraction {
    public enum Action { COMMISSION, STATUS, REPORT, TRIAL, TRIAL_REPORT, UNAVAILABLE }
    private KOMELiegeProgressionInteraction() {}
    public static boolean ownsQuest(EntityPlayer player,LOTREntityNPC npc){return KOMECurrentLiege.valid(player,npc)&&KOMEWorldData.get(player.worldObj).getProgression(player.getUniqueID()).getCanonicalRank()==KOMEProgressionRank.KNIGHT;}
    public static Action action(KOMEPlayerProgression p,double alignment,String faction){
        if(p==null||p.getCanonicalRank()!=KOMEProgressionRank.KNIGHT)return Action.UNAVAILABLE;
        KOMELordshipTrial t=p.getLordship().assignment();if(t!=null&&t.ordinaryService)return t.ready()?Action.REPORT:Action.STATUS;
        KOMEKnightCommission a=p.getKnightService().assignment();if(a!=null)return a.stage==KOMEKnightCommission.Stage.READY_TO_REPORT?Action.REPORT:
            a.stage==KOMEKnightCommission.Stage.OFFERED||a.stage==KOMEKnightCommission.Stage.FAILED?Action.COMMISSION:Action.STATUS;
        return p.getKnightService().qualifyingTypes(faction).size()>=3?Action.REPORT:Action.COMMISSION;
    }
    public static boolean quest(EntityPlayerMP player,LOTREntityNPC lord){
        if(!ownsQuest(player,lord)||player.getDistanceSqToEntity(lord)>64)return false;
        KOMEWorldData data=KOMEWorldData.get(player.worldObj);
        synchronized(data){
            KOMEPlayerProgression testing=data.getProgression(player.getUniqueID());
            if(testing.getKnightService().testing()&&testing.getKnightService().assignment()==null&&testing.getLordship().assignment()==null){KOMEProgressionNpcSpeech.say(player,lord,"The field exercise is concluded. Arrange another through the staff commission command, or end the exercise before resuming earned service.");return true;}
            if(KOMEServicePromotionService.report(player,lord))return true;
            KOMEPlayerProgression p=data.getProgression(player.getUniqueID());
            if(KOMEKnightCommissionService.prepare(player,lord)==null)KOMEProgressionNpcSpeech.say(player,lord,"I cannot assign a safe commission here. Your service is recorded; seek another suitable place.");
            else KOMEKnightCommissionService.acceptOrReport(player,lord);
            KOMEProgressionAutoCompleter.syncPlayer(player,p);return true;
        }
    }
}
