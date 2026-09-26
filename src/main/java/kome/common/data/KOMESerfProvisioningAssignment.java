package kome.common.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import lotr.common.item.LOTRItemMug;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.StatCollector;

/** Structured, persisted authority for one canonical Provisioning duty. */
public final class KOMESerfProvisioningAssignment {
    public interface CandidateResolver { Candidate resolve(String key,int legacyItemId,int damage); }
    /** Pure generation input; tests do not need Minecraft item bootstrap. */
    public static final class Candidate {
        public final String key, displayName; public final int itemId, damage, maxStack; public final boolean brewable;
        public Candidate(String key,int itemId,int damage,int maxStack,String displayName){this(key,itemId,damage,maxStack,displayName,false);}
        public Candidate(String key,int itemId,int damage,int maxStack,String displayName,boolean brewable){this.key=key;this.itemId=itemId;this.damage=damage;this.maxStack=Math.max(1,maxStack);this.displayName=displayName==null?"":displayName;this.brewable=brewable;}
        public static Candidate runtime(Item item,int damage){if(item==null)throw new IllegalArgumentException("Provisioning item is required.");ItemStack stack=new ItemStack(item,1,damage);return new Candidate(String.valueOf(Item.itemRegistry.getNameForObject(item)),Item.getIdFromItem(item),damage,item.getItemStackLimit(),stack.getDisplayName(),item instanceof LOTRItemMug&&((LOTRItemMug)item).isBrewable);}
    }
    public static final class Requirement {
        public final String itemKey, displayName, vessel; public final int itemId, damage, required, strength; public final boolean drink; public int delivered;
        Requirement(Candidate item,int required,String vessel){this(item.key,item.itemId,item.damage,required,0,item.displayName,vessel,vessel.length()!=0,item.brewable?0:-1);}
        Requirement(String key,int id,int damage,int required,int delivered,String display,String vessel,boolean drink,int strength){this.itemKey=key==null?"":key;this.itemId=id;this.damage=damage;this.required=required;this.delivered=Math.max(0,Math.min(required,delivered));this.displayName=display==null?"":display;this.vessel=vessel==null?"":vessel;this.drink=drink;this.strength=strength;}
        public boolean isDrink(){return drink;} public boolean complete(){return delivered>=required;}
        public String description(){
            if(!drink)return required+" "+displayName;
            if(vessel.length()==0)return required+" "+displayName+" (any vessel or strength)";
            LOTRItemMug.Vessel type=LOTRItemMug.Vessel.valueOf(vessel);
            String fallback=type.name().replace('_',' ').toLowerCase(java.util.Locale.ROOT);
            fallback=Character.toUpperCase(fallback.charAt(0))+fallback.substring(1);
            String container=type.getEmptyVesselItem()==null?fallback:new ItemStack(type.getEmptyVesselItem()).getDisplayName();
            if(container.length()==0||container.equalsIgnoreCase(type.name())||container.equalsIgnoreCase(type.name().replace('_',' ')))container=fallback;
            if(required!=1)container+="s";
            String potency=strength<0?"":strengthName(strength)+" ";
            return required+" "+container+" of "+potency+displayName;
        }
        public String progressText(){return description()+" ("+delivered+" delivered)";}
        private static String strengthName(int value){String[] nativeNames={"weak","light","moderate","strong","potent"};String key="item.lotr.drink."+nativeNames[value];String localized=StatCollector.translateToLocal(key);return localized.equals(key)?Character.toUpperCase(nativeNames[value].charAt(0))+nativeNames[value].substring(1):localized;}
        NBTTagCompound write(){NBTTagCompound tag=new NBTTagCompound();tag.setString("ItemKey",itemKey);tag.setInteger("Item",itemId);tag.setInteger("Damage",damage);tag.setInteger("Required",required);tag.setInteger("Delivered",delivered);tag.setString("Display",displayName);tag.setString("Vessel",vessel);if(drink&&vessel.length()!=0)tag.setInteger("Strength",strength);return tag;}
        static Requirement read(NBTTagCompound tag,CandidateResolver resolver,boolean drink,boolean exactDrink){String key=tag.getString("ItemKey");int damage=tag.getInteger("Damage"),required=tag.getInteger("Required");Candidate item=resolver.resolve(key,tag.getInteger("Item"),damage);if(item==null||required<=0)return null;String vessel=drink&&exactDrink?tag.getString("Vessel"):"";int strength=drink&&exactDrink?tag.getInteger("Strength"):-1;if(drink&&exactDrink){try{LOTRItemMug.Vessel.valueOf(vessel);}catch(Exception e){return null;}if(strength< -1||strength>4||item.brewable!=(strength>=0))return null;}return new Requirement(item.key,item.itemId,damage,required,tag.getInteger("Delivered"),tag.getString("Display"),vessel,drink,strength);}
    }
    public final List<Requirement> foods; public final Requirement drink;
    public KOMESerfProvisioningAssignment(List<Requirement> foods,Requirement drink){this.foods=foods;this.drink=drink;}
    public static KOMESerfProvisioningAssignment generate(String faction,Random random){return generate(KOMESerfProvisioningCatalog.foodsForFaction(faction),KOMESerfProvisioningCatalog.drinksForFaction(faction),KOMESerfProvisioningCatalog.vesselsForFaction(faction),random);}
    public static KOMESerfProvisioningAssignment generate(List<Candidate> foodPool,List<Candidate> drinkPool,List<String> vesselPool,Random random){
        if(random==null||foodPool==null||foodPool.size()<3||drinkPool==null||drinkPool.isEmpty()||vesselPool==null||vesselPool.isEmpty())throw new IllegalArgumentException("Complete Provisioning candidates are required.");
        List<Candidate> available=new ArrayList<Candidate>(foodPool);List<Requirement> foods=new ArrayList<Requirement>();
        while(foods.size()<3){Candidate item=available.remove(random.nextInt(available.size()));int quantity=item.maxStack==1?16+random.nextInt(9):(4+random.nextInt(5))*item.maxStack;foods.add(new Requirement(item,quantity,""));}
        Candidate drink=drinkPool.get(random.nextInt(drinkPool.size()));String vessel=vesselPool.get(random.nextInt(vesselPool.size()));
        Requirement requirement=new Requirement(drink,24+random.nextInt(9),vessel);
        if(drink.brewable)requirement=new Requirement(drink.key,drink.itemId,drink.damage,requirement.required,0,drink.displayName,vessel,true,random.nextInt(5));
        return new KOMESerfProvisioningAssignment(foods,requirement);
    }
    public boolean complete(){if(foods==null||foods.size()!=3)return false;for(Requirement food:foods)if(food==null||!food.complete())return false;return drink!=null&&drink.complete();}
    public String progressList(String separator){StringBuilder text=new StringBuilder();for(Requirement food:foods){if(text.length()!=0)text.append(separator);text.append(food.progressText());}if(text.length()!=0)text.append(separator);return text.append(drink.progressText()).toString();}
    public NBTTagCompound writeToNBT(){NBTTagCompound tag=new NBTTagCompound();if(drink.vessel.length()!=0)tag.setInteger("RequirementVersion",2);NBTTagList list=new NBTTagList();for(Requirement food:foods)list.appendTag(food.write());tag.setTag("Foods",list);tag.setTag("Drink",drink.write());return tag;}
    public static KOMESerfProvisioningAssignment readFromNBT(NBTTagCompound tag){return readFromNBT(tag,new CandidateResolver(){public Candidate resolve(String key,int legacyItemId,int damage){Item item=key==null||key.length()==0?Item.getItemById(legacyItemId):(Item)Item.itemRegistry.getObject(key);return item==null?null:Candidate.runtime(item,damage);}});}
    static KOMESerfProvisioningAssignment readFromNBT(NBTTagCompound tag,CandidateResolver resolver){if(tag==null||resolver==null)return null;NBTTagList list=tag.getTagList("Foods",10);if(list.tagCount()!=3||!tag.hasKey("Drink",10))return null;boolean exactDrink=tag.getInteger("RequirementVersion")>=2;List<Requirement> foods=new ArrayList<Requirement>();for(int i=0;i<3;i++){Requirement r=Requirement.read(list.getCompoundTagAt(i),resolver,false,false);if(r==null)return null;for(Requirement old:foods)if(old.itemKey.equals(r.itemKey)&&old.damage==r.damage)return null;foods.add(r);}Requirement drink=Requirement.read(tag.getCompoundTag("Drink"),resolver,true,exactDrink);if(drink==null)return null;for(Requirement food:foods)if(food.itemKey.equals(drink.itemKey)&&food.damage==drink.damage)return null;return new KOMESerfProvisioningAssignment(foods,drink);}
}
