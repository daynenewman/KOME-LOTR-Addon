package kome.common.data;

import java.util.List;
import lotr.common.LOTRMod;
import lotr.common.item.LOTRItemCoin;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/** Small physical compensation using existing issuance, with saved pre-spawn reservations. */
public final class KOMEProgressionServiceRewards {
    public static final int FOOD_UNITS=2,COIN_VALUE=2;
    private KOMEProgressionServiceRewards() {}
    public static void duty(EntityPlayerMP player,KOMESerfKnightProgression state,KOMESerfKnightDutyType type){
        KOMESerfKnightProgression.Duty duty=state.getDuty(type);
        if(player.worldObj.isRemote||!duty.isCompleted()||duty.rewardReserved)return;
        duty.rewardReserved=true;KOMEWorldData.get(player.worldObj).markDirty();
        issue(player,state.getSerfdomMaster().factionKey,true);
    }
    public static void trial(EntityPlayerMP player,KOMESerfKnightProgression state){
        if(player.worldObj.isRemote||!state.isTrialCompleted()||state.trialRewardReserved)return;
        state.trialRewardReserved=true;KOMEWorldData.get(player.worldObj).markDirty();issue(player,state.getLiege().factionKey,false);
    }
    public static void commission(EntityPlayerMP player,KOMEKnightCommission a){
        if(player.worldObj.isRemote||a.stage!=KOMEKnightCommission.Stage.REPORTED||a.rewardReserved)return;
        a.rewardReserved=true;KOMEWorldData.get(player.worldObj).markDirty();issue(player,a.faction,a.type==KOMEKnightCommission.Type.STOLEN_GOODS);
    }
    private static void issue(EntityPlayerMP player,String faction,boolean coins){
        if(coins&&LOTRMod.silverCoin!=null&&LOTRItemCoin.values!=null){
            for(int i=0;i<LOTRItemCoin.values.length;i++)if(LOTRItemCoin.values[i]==1){
                KOMEProgressionItemDrops.drop(player.worldObj,player.posX,player.posY+0.5,player.posZ,new ItemStack(LOTRMod.silverCoin,COIN_VALUE,i));return;
            }
        }
        Item[] food=KOMELocalProvisionFoods.forFaction(faction);if(food.length==0)return;
        Item item=food[0];if(item==null)return;ItemStack stack=new ItemStack(item);
        stack.stackSize=Math.min(FOOD_UNITS,stack.getMaxStackSize());KOMEProgressionItemDrops.drop(player.worldObj,player.posX,player.posY+0.5,player.posZ,stack);
    }
}
