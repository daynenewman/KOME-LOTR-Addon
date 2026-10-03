package kome.common.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import lotr.common.LOTRMod;
import lotr.common.fac.LOTRFaction;
import lotr.common.inventory.LOTRInventoryPouch;
import lotr.common.item.LOTRItemPouch;
import lotr.common.item.LOTRItemCoin;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/** Native medium pouch, generated once into saved state; claims reserve one inventory slot. */
public final class KOMEPartingGiftService {
    private KOMEPartingGiftService() { }
    public static ItemStack pending(KOMESerfKnightProgression state,Random random) {
        if(state==null||!state.isTrialCompleted()||state.hasPartingGift()||!state.getSerfdomMaster().isSet())return null;
        ItemStack saved=state.getPendingPartingGift();
        if(saved==null){saved=generate(state.getSerfdomMaster().factionKey,random);state.setPendingPartingGift(saved);}
        return saved==null?null:saved.copy();
    }
    static ItemStack generate(String faction,Random random) {
        if(LOTRMod.pouch==null||LOTRMod.silverCoin==null)return null;
        ItemStack pouch=new ItemStack(LOTRMod.pouch,1,1);
        LOTRFaction nativeFaction=KOMEProgressionFactionResolver.resolve(faction);
        if(nativeFaction!=null)LOTRItemPouch.setPouchColor(pouch,nativeFaction.getFactionColor());
        List<ItemStack> contents=new ArrayList<ItemStack>();
        List<Item> food=new ArrayList<Item>();for(Item item:KOMELocalProvisionFoods.forFaction(faction))if(item!=null)food.add(item);
        if(food.isEmpty())return null;
        ItemStack ration=new ItemStack(food.get(random.nextInt(food.size())));
        int quantity=ration.getMaxStackSize()>1?32+random.nextInt(33):4+random.nextInt(3);
        while(quantity>0){ItemStack serving=ration.copy();serving.stackSize=Math.min(quantity,serving.getMaxStackSize());contents.add(serving);quantity-=serving.stackSize;}
        Item[] equipment=equipment(faction);
        contents.add(new ItemStack(equipment[0]));if(random.nextBoolean())contents.add(new ItemStack(equipment[1]));
        contents.add(new ItemStack(equipment[2]));if(random.nextBoolean())contents.add(new ItemStack(equipment[3]));
        int value=32+random.nextInt(33);
        for(int denomination=LOTRItemCoin.values.length-1;denomination>=0;denomination--){int count=value/LOTRItemCoin.values[denomination];value%=LOTRItemCoin.values[denomination];if(count>0)contents.add(new ItemStack(LOTRMod.silverCoin,count,denomination));}
        pack(pouch,contents);return pouch;
    }
    static void pack(ItemStack pouch,List<ItemStack> contents) {
        LOTRInventoryPouch inventory=new LOTRInventoryPouch(pouch);int slot=0;
        for(ItemStack stack:contents){
            if(stack==null||stack.getItem()==null||stack.stackSize<=0||stack.stackSize>Math.min(stack.getMaxStackSize(),inventory.getInventoryStackLimit())||slot>=inventory.getSizeInventory())throw new IllegalArgumentException("Invalid parting gift stack at slot "+slot+" (item="+(stack==null?"null":stack.getItem())+")");
            inventory.setInventorySlotContents(slot++,stack.copy());
        }
        inventory.markDirty();
    }
    /** Two weapons and two armor alternatives, all verified fields in the shipped jar. */
    static Item[] equipment(String faction) {
        Item[] result;
        switch(KOMEAlliance.normalizeFactionKey(faction)) {
            case "rohan":result=new Item[]{LOTRMod.swordRohan,LOTRMod.spearRohan,LOTRMod.helmetRohan,LOTRMod.bodyRohan};break;
            case "gondor":result=new Item[]{LOTRMod.swordGondor,LOTRMod.spearGondor,LOTRMod.helmetGondor,LOTRMod.bodyGondor};break;
            case "durinsfolk":result=new Item[]{LOTRMod.swordDwarven,LOTRMod.hammerDwarven,LOTRMod.helmetDwarven,LOTRMod.bodyDwarven};break;
            case "bluemountains":result=new Item[]{LOTRMod.swordBlueDwarven,LOTRMod.hammerBlueDwarven,LOTRMod.helmetBlueDwarven,LOTRMod.bodyBlueDwarven};break;
            case "highelves":result=new Item[]{LOTRMod.swordHighElven,LOTRMod.spearHighElven,LOTRMod.helmetHighElven,LOTRMod.bodyHighElven};break;
            case "woodelf":result=new Item[]{LOTRMod.swordWoodElven,LOTRMod.spearWoodElven,LOTRMod.helmetWoodElven,LOTRMod.bodyWoodElven};break;
            case "lothlorien":result=new Item[]{LOTRMod.swordElven,LOTRMod.spearElven,LOTRMod.helmetElven,LOTRMod.bodyElven};break;
            case "dorwinion":result=new Item[]{LOTRMod.swordDorwinionElf,LOTRMod.daggerDorwinionElf,LOTRMod.helmetDorwinion,LOTRMod.bodyDorwinion};break;
            case "angmar":result=new Item[]{LOTRMod.swordAngmar,LOTRMod.spearAngmar,LOTRMod.helmetAngmar,LOTRMod.bodyAngmar};break;
            case "dolguldur":result=new Item[]{LOTRMod.swordDolGuldur,LOTRMod.spearDolGuldur,LOTRMod.helmetDolGuldur,LOTRMod.bodyDolGuldur};break;
            case "isengard":result=new Item[]{LOTRMod.scimitarUruk,LOTRMod.spearUruk,LOTRMod.helmetUruk,LOTRMod.bodyUruk};break;
            case "mordor":case "gundabad":result=new Item[]{LOTRMod.scimitarOrc,LOTRMod.spearOrc,LOTRMod.helmetOrc,LOTRMod.bodyOrc};break;
            case "halftroll":result=new Item[]{LOTRMod.scimitarHalfTroll,LOTRMod.hammerHalfTroll,LOTRMod.helmetHalfTroll,LOTRMod.bodyHalfTroll};break;
            case "morwaith":result=new Item[]{LOTRMod.spearMoredain,LOTRMod.daggerMoredain,LOTRMod.helmetMoredain,LOTRMod.bodyMoredain};break;
            case "taurethrim":result=new Item[]{LOTRMod.swordTauredain,LOTRMod.spearTauredain,LOTRMod.helmetTauredain,LOTRMod.bodyTauredain};break;
            case "rhudel":result=new Item[]{LOTRMod.swordRhun,LOTRMod.spearRhun,LOTRMod.helmetRhun,LOTRMod.bodyRhun};break;
            case "harad":result=new Item[]{LOTRMod.swordGulfHarad,LOTRMod.spearHarad,LOTRMod.helmetHaradRobes,LOTRMod.bodyHaradRobes};break;
            case "dale":result=new Item[]{LOTRMod.swordDale,LOTRMod.spearDale,LOTRMod.helmetDale,LOTRMod.bodyDale};break;
            case "dunedain":result=new Item[]{LOTRMod.rangerBow,LOTRMod.daggerGondor,LOTRMod.helmetRanger,LOTRMod.bodyRanger};break;
            default:result=new Item[]{Items.iron_sword,Items.bow,Items.leather_helmet,Items.leather_chestplate};
        }
        Item[] fallback={Items.iron_sword,Items.bow,Items.leather_helmet,Items.leather_chestplate};
        for(int i=0;i<result.length;i++)if(result[i]==null)result[i]=fallback[i];return result;
    }
}
