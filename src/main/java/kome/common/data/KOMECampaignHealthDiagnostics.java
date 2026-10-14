package kome.common.data;

import java.util.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.*;
import net.minecraft.nbt.*;
import static kome.common.data.KOMEOrdinaryBattleRecoveryRegistry.Component;

/** Detached inspection: never observes, installs HP, publishes a projection or allocates a transition. */
public final class KOMECampaignHealthDiagnostics {
    private KOMECampaignHealthDiagnostics() { }
    public static String describe(KOMEWorldData data,KOMEHiredUnitRecord record,Entity physical) {
        if(!KOMEHiredUnitClassification.isCampaignUnit(record))return "Health: unavailable (no Campaign health authority)";
        try {
            require(data!=null&&data.hiredUnits.get(record.entity)==record,"unit authority is not canonical");
            if(physical==null&&record.healthObservedEntity!=null){
                Entity observed=record.healthObservedEntity.get();
                if(observed!=null&&!observed.isDead&&observed.worldObj!=null&&!observed.worldObj.isRemote
                    &&record.entity.equals(observed.getUniqueID())
                    &&observed.worldObj.getEntityByID(observed.getEntityId())==observed)physical=observed;
            }
            KOMECampaignHealth.validate(record.survivingHealth);
            KOMECampaignHealthProjection saved=KOMECampaignHealth.versioned(record.survivingHealth)
                ?KOMECampaignHealthProjection.read(record.survivingHealth):null;
            KOMECampaignHealthProjection live=physical==null?null:KOMECampaignHealthEntity.projection(physical);
            if(physical!=null){
                require(physical instanceof EntityLivingBase&&physical.getUniqueID().equals(record.entity),"physical identity is unresolved");
                require(live==null||Float.compare(live.hp,((EntityLivingBase)physical).getHealth())==0,"live HP lacks a matching health revision");
                require(live!=null||saved!=null&&saved.fence==null
                    &&Float.compare(saved.hp,((EntityLivingBase)physical).getHealth())==0,"live health acknowledgment is unresolved");
            }
            require(saved!=null||live!=null,"versioned health is not available");
            if(saved!=null)validate(data,record,saved);
            if(live!=null)validate(data,record,live);
            KOMECampaignHealthProjection chosen=saved==null?live:live==null?saved:KOMECampaignHealth.reconcile(record,saved,live);
            String maximum;
            try {
                float max=physical instanceof EntityLivingBase?((EntityLivingBase)physical).getMaxHealth():savedMax(record);
                require(Float.isFinite(max)&&max>0&&chosen.hp<=max,"normal Max HP is unresolved");
                maximum=Float.toString(max);
            } catch(RuntimeException unresolvedMax){maximum="unresolved";}
            return "Health: "+Float.toString(chosen.hp)+" / "+maximum+" | revision "+chosen.revision
                +" | terminal "+(chosen.terminal?"yes":"no")+" | recovery fence "
                +(chosen.fence==null?"none":"present #"+chosen.fence.sequence)
                +(physical==null?" | persisted":"");
        } catch(RuntimeException unresolved) {
            return "Health: unresolved ("+clean(unresolved.getMessage())+")";
        }
    }
    private static void validate(KOMEWorldData data,KOMEHiredUnitRecord record,KOMECampaignHealthProjection p){
        require(p.alias.equals(record.entity)&&p.component==Component.RIDER,"health alias/component contradiction");
        KOMEOrdinaryBattleRecoveryRegistry.Target target=data.getOrdinaryBattleRecovery().targetForPhysical(record.entity);
        require(target==null||target.id.equals(p.target)&&target.physicalId.equals(p.alias)
            &&(!target.terminal||p.terminal),"recovery target contradicts health");
        KOMERecoveryStartup startup=data.recoveryStartup;
        if(startup==null){require(p.fence==null,"recovery fence awaits journal validation");return;}
        require(startup.permitsWork("",p.target),"recovery authority is blocked/quarantined");
        KOMERecoveryJournalView view=startup.journal();KOMERecoveryStartupPlan.validateFence(view,p);
        KOMERecoveryJournalView.Entry prepare=view.latestPreparations.get(p.target);
        if(prepare!=null)KOMERecoveryStartupPlan.reconcile(view,prepare,p);
        require(!view.terminalTargets.contains(p.target)||p.terminal,"terminal journal contradicts health");
    }
    private static float savedMax(KOMEHiredUnitRecord record){
        NBTTagCompound snapshot=record.movingEntityData!=null?record.movingEntityData:record.stationedEntityData;
        require(snapshot!=null&&snapshot.hasKey("Attributes",9),"saved Max HP is not available");
        NBTTagList attributes=snapshot.getTagList("Attributes",10);NBTTagCompound maximum=null;
        for(int i=0;i<attributes.tagCount();i++){
            NBTTagCompound attribute=attributes.getCompoundTagAt(i);
            if(SharedMonsterAttributes.maxHealth.getAttributeUnlocalizedName().equals(attribute.getString("Name"))){
                require(maximum==null,"duplicate Max HP attribute");maximum=attribute;
            }
        }
        require(maximum!=null&&maximum.hasKey("Base",6)&&Double.isFinite(maximum.getDouble("Base")),"invalid saved Max HP");
        IAttributeInstance attribute=new ServersideAttributeMap().registerAttribute(SharedMonsterAttributes.maxHealth);
        attribute.setBaseValue(maximum.getDouble("Base"));
        if(maximum.hasKey("Modifiers")){
            require(maximum.hasKey("Modifiers",9),"invalid saved Max HP modifiers");
            NBTTagList modifiers=(NBTTagList)maximum.getTag("Modifiers");
            require(modifiers.tagCount()==0||modifiers.func_150303_d()==10,"invalid saved Max HP modifiers");
            for(int i=0;i<modifiers.tagCount();i++){
                NBTTagCompound modifier=modifiers.getCompoundTagAt(i);
                require(modifier.hasKey("Amount",6)&&Double.isFinite(modifier.getDouble("Amount"))
                    &&modifier.hasKey("UUIDMost",4)&&modifier.hasKey("UUIDLeast",4)
                    &&modifier.hasKey("Operation",3),"invalid saved Max HP modifier");
                AttributeModifier decoded=SharedMonsterAttributes.readAttributeModifierFromNBT(modifier);
                require(decoded!=null,"invalid saved Max HP modifier");attribute.applyModifier(decoded);
            }
        }
        return (float)attribute.getAttributeValue();
    }
    private static String clean(String message){return message==null?"authority unavailable":message.replace('\n',' ').replace('\r',' ');}
    private static void require(boolean valid,String reason){if(!valid)throw new IllegalArgumentException(reason);}
}
