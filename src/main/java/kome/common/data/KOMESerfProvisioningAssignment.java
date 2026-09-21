package kome.common.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import lotr.common.item.LOTRItemMug;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/** Structured, persisted authority for one canonical Provisioning duty. */
public final class KOMESerfProvisioningAssignment {
    public interface CandidateResolver { Candidate resolve(String key,int legacyItemId,int damage); }
    /** Pure generation input; tests do not need Minecraft item bootstrap. */
    public static final class Candidate {
        public final String key, displayName; public final int itemId, damage, maxStack;
        public Candidate(String key,int itemId,int damage,int maxStack,String displayName){this.key=key;this.itemId=itemId;this.damage=damage;this.maxStack=Math.max(1,maxStack);this.displayName=displayName==null?"":displayName;}
        public static Candidate runtime(Item item,int damage){if(item==null)throw new IllegalArgumentException("Provisioning item is required.");ItemStack stack=new ItemStack(item,1,damage);return new Candidate(String.valueOf(Item.itemRegistry.getNameForObject(item)),Item.getIdFromItem(item),damage,item.getItemStackLimit(),stack.getDisplayName());}
    }
    public static final class Requirement {
        public final String itemKey, displayName, vessel; public final int itemId, damage, required; public int delivered;
        Requirement(Candidate item,int required,String vessel){this(item.key,item.itemId,item.damage,required,0,item.displayName,vessel);}
        Requirement(String key,int id,int damage,int required,int delivered,String display,String vessel){this.itemKey=key==null?"":key;this.itemId=id;this.damage=damage;this.required=required;this.delivered=Math.max(0,Math.min(required,delivered));this.displayName=display==null?"":display;this.vessel=vessel==null?"":vessel;}
        public boolean isDrink(){return vessel.length()!=0;} public boolean complete(){return delivered>=required;}
        NBTTagCompound write(){NBTTagCompound tag=new NBTTagCompound();tag.setString("ItemKey",itemKey);tag.setInteger("Item",itemId);tag.setInteger("Damage",damage);tag.setInteger("Required",required);tag.setInteger("Delivered",delivered);tag.setString("Display",displayName);tag.setString("Vessel",vessel);return tag;}
        static Requirement read(NBTTagCompound tag,CandidateResolver resolver){String key=tag.getString("ItemKey");int damage=tag.getInteger("Damage"),required=tag.getInteger("Required");Candidate item=resolver.resolve(key,tag.getInteger("Item"),damage);if(item==null||required<=0)return null;String vessel=tag.getString("Vessel");if(vessel.length()!=0){try{LOTRItemMug.Vessel.valueOf(vessel);}catch(Exception e){return null;}}return new Requirement(item.key,item.itemId,damage,required,tag.getInteger("Delivered"),tag.getString("Display"),vessel);}
    }
    public final List<Requirement> foods; public final Requirement drink;
    public KOMESerfProvisioningAssignment(List<Requirement> foods,Requirement drink){this.foods=foods;this.drink=drink;}
    public static KOMESerfProvisioningAssignment generate(String faction,Random random){return generate(KOMESerfProvisioningCatalog.foodsForFaction(faction),KOMESerfProvisioningCatalog.drinksForFaction(faction),KOMESerfProvisioningCatalog.vesselsForFaction(faction),random);}
    public static KOMESerfProvisioningAssignment generate(List<Candidate> foodPool,List<Candidate> drinkPool,List<String> vesselPool,Random random){
        if(random==null||foodPool==null||foodPool.size()<3||drinkPool==null||drinkPool.isEmpty()||vesselPool==null||vesselPool.isEmpty())throw new IllegalArgumentException("Complete Provisioning candidates are required.");
        List<Candidate> available=new ArrayList<Candidate>(foodPool);List<Requirement> foods=new ArrayList<Requirement>();
        while(foods.size()<3){Candidate item=available.remove(random.nextInt(available.size()));int quantity=item.maxStack==1?16+random.nextInt(9):(4+random.nextInt(5))*item.maxStack;foods.add(new Requirement(item,quantity,""));}
        Candidate drink=drinkPool.get(random.nextInt(drinkPool.size()));String vessel=vesselPool.get(random.nextInt(vesselPool.size()));
        return new KOMESerfProvisioningAssignment(foods,new Requirement(drink,24+random.nextInt(9),vessel));
    }
    public boolean complete(){if(foods==null||foods.size()!=3)return false;for(Requirement food:foods)if(food==null||!food.complete())return false;return drink!=null&&drink.complete();}
    public NBTTagCompound writeToNBT(){NBTTagCompound tag=new NBTTagCompound();NBTTagList list=new NBTTagList();for(Requirement food:foods)list.appendTag(food.write());tag.setTag("Foods",list);tag.setTag("Drink",drink.write());return tag;}
    public static KOMESerfProvisioningAssignment readFromNBT(NBTTagCompound tag){return readFromNBT(tag,new CandidateResolver(){public Candidate resolve(String key,int legacyItemId,int damage){Item item=key==null||key.length()==0?Item.getItemById(legacyItemId):(Item)Item.itemRegistry.getObject(key);return item==null?null:Candidate.runtime(item,damage);}});}
    static KOMESerfProvisioningAssignment readFromNBT(NBTTagCompound tag,CandidateResolver resolver){if(tag==null||resolver==null)return null;NBTTagList list=tag.getTagList("Foods",10);if(list.tagCount()!=3||!tag.hasKey("Drink",10))return null;List<Requirement> foods=new ArrayList<Requirement>();for(int i=0;i<3;i++){Requirement r=Requirement.read(list.getCompoundTagAt(i),resolver);if(r==null||r.isDrink())return null;for(Requirement old:foods)if(old.itemKey.equals(r.itemKey)&&old.damage==r.damage)return null;foods.add(r);}Requirement drink=Requirement.read(tag.getCompoundTag("Drink"),resolver);if(drink==null||!drink.isDrink())return null;for(Requirement food:foods)if(food.itemKey.equals(drink.itemKey)&&food.damage==drink.damage)return null;return new KOMESerfProvisioningAssignment(foods,drink);}
}
