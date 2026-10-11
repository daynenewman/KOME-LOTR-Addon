package kome.common.data;

import lotr.common.entity.npc.LOTREntityNPC;
import net.minecraft.entity.player.EntityPlayerMP;

/** Explicit Master visit repairs an obsolete requirement once; login never rerolls goods. */
public final class KOMEProgressionGoodsRecovery {
    private KOMEProgressionGoodsRecovery() {}
    public static boolean repair(EntityPlayerMP player,LOTREntityNPC master){
        KOMEWorldData data=KOMEWorldData.get(player.worldObj);KOMEPlayerProgression p=data.getProgression(player.getUniqueID());KOMESerfKnightProgression s=p.getSerfKnightProgression();
        if(!KOMESerfdomMasterService.validateCurrentMasterInteraction(player,data,master,true).success)return false;
        priceLegacy(data,s);
        if("profession".equals(s.getActiveAssignmentKind())){
            KOMESerfProfessionAssignment old=KOMESerfProfessionAssignment.readFromNBT(s.getDuty(KOMESerfKnightDutyType.PROFESSION).getAssignmentData());boolean valid=old!=null;
            if(old!=null)for(KOMESerfProfessionAssignment.Requirement r:old.requirements)if(!r.complete()&&!KOMEProgressionGoodsEligibility.profession(r.requestedStack()))valid=false;
            if(valid)return false;
            KOMESerfProfessionAssignment replacement=KOMESerfProfessionAssignment.generate(KOMESerfProfessionClassifier.classify(master),master.getFaction().codeName(),player.worldObj.rand);
            if(old!=null)for(KOMESerfProfessionAssignment.Requirement r:replacement.requirements)for(KOMESerfProfessionAssignment.Requirement before:old.requirements)if(r.itemKey.equals(before.itemKey)&&r.damage==before.damage){r.delivered=Math.min(r.required,before.delivered);r.valueMilli=before.valueMilli;}
            s.setDutyAssignmentData(KOMESerfKnightDutyType.PROFESSION,replacement.writeToNBT());
        }else if("provisioning".equals(s.getActiveAssignmentKind())){
            KOMESerfProvisioningAssignment old=KOMESerfProvisioningAssignment.readFromNBT(s.getDuty(KOMESerfKnightDutyType.PROVISIONING).getAssignmentData());boolean valid=old!=null;
            if(old!=null){for(KOMESerfProvisioningAssignment.Requirement r:old.foods)if(!r.complete()&&!KOMEProgressionGoodsEligibility.provision(player,r.requestedStack()))valid=false;if(!old.drink.complete()&&!KOMEProgressionGoodsEligibility.provision(player,old.drink.requestedStack()))valid=false;}
            if(valid)return false;
            KOMESerfProvisioningAssignment replacement=KOMESerfProvisioningAssignment.generate(player,master.getFaction().codeName(),player.worldObj.rand);
            if(old!=null)for(KOMESerfProvisioningAssignment.Requirement r:replacement.foods)for(KOMESerfProvisioningAssignment.Requirement before:old.foods)if(r.itemKey.equals(before.itemKey)&&r.damage==before.damage){r.delivered=Math.min(r.required,before.delivered);r.valueMilli=before.valueMilli;}
            s.setDutyAssignmentData(KOMESerfKnightDutyType.PROVISIONING,replacement.writeToNBT());
        }else return false;
        data.markDirty();KOMEProgressionAutoCompleter.syncPlayer(player,p);KOMEProgressionNpcSpeech.say(player,master,"Those supplies are no longer suitable. I have replaced the request in your book. Matching deposits and today's service limit remain recorded.");return true;
    }
    /** Add quotes to active legacy requests without changing their goods or deposited counts. */
    static void priceLegacy(KOMEWorldData data,KOMESerfKnightProgression state){
        KOMESerfKnightDutyType type=KOMESerfKnightDutyType.forKey(state.getActiveAssignmentKind());if(type==null)return;
        net.minecraft.nbt.NBTTagCompound saved=state.getDuty(type).getAssignmentData();if(saved==null)return;boolean changed=false;
        if(type==KOMESerfKnightDutyType.PROVISIONING){KOMESerfProvisioningAssignment a=KOMESerfProvisioningAssignment.readFromNBT(saved);if(a==null)return;net.minecraft.nbt.NBTTagList foods=saved.getTagList("Foods",10);for(int i=0;i<a.foods.size();i++)if(!foods.getCompoundTagAt(i).hasKey("ValueMilli")){a.foods.get(i).valueMilli=KOMEProgressionGoodsValue.unitMilli(a.foods.get(i).requestedStack());changed=true;}if(!saved.getCompoundTag("Drink").hasKey("ValueMilli")){a.drink.valueMilli=KOMEProgressionGoodsValue.unitMilli(a.drink.requestedStack());changed=true;}if(changed)state.setDutyAssignmentData(type,a.writeToNBT());}
        else if(type==KOMESerfKnightDutyType.PROFESSION){KOMESerfProfessionAssignment a=KOMESerfProfessionAssignment.readFromNBT(saved);if(a==null)return;net.minecraft.nbt.NBTTagList goods=saved.getTagList("Requirements",10);for(int i=0;i<a.requirements.size();i++)if(!goods.getCompoundTagAt(i).hasKey("ValueMilli")){a.requirements.get(i).valueMilli=KOMEProgressionGoodsValue.unitMilli(a.requirements.get(i).requestedStack());changed=true;}if(changed)state.setDutyAssignmentData(type,a.writeToNBT());}
        if(changed)data.markDirty();
    }
}
