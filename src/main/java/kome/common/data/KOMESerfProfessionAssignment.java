package kome.common.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/** Structured, persisted authority for one canonical Profession duty. */
public final class KOMESerfProfessionAssignment {
    public static final int VERSION=1;
    public static final String EXACT_METADATA="exact";
    public enum QuantityTier {
        BULK_COMMON(32,96), STANDARD(12,32), UNCOMMON(6,16), REGIONAL_METAL(4,12), RARE(1,4), NONSTACKABLE(1,2);
        public final int minimum,maximum;
        QuantityTier(int minimum,int maximum){this.minimum=minimum;this.maximum=maximum;}
    }
    public interface CandidateResolver {Candidate resolve(String key,int legacyItemId,int damage);}
    public static final class Candidate {
        public final String key,displayName,metadataPolicy;public final int itemId,damage,maxStack;public final QuantityTier tier;
        public Candidate(String key,int itemId,int damage,int maxStack,String displayName,QuantityTier tier){this(key,itemId,damage,maxStack,displayName,tier,EXACT_METADATA);}
        public Candidate(String key,int itemId,int damage,int maxStack,String displayName,QuantityTier tier,String metadataPolicy){this.key=key==null?"":key;this.itemId=itemId;this.damage=damage;this.maxStack=Math.max(1,maxStack);this.displayName=displayName==null?"":displayName;this.tier=tier;this.metadataPolicy=metadataPolicy==null?"":metadataPolicy;}
        public static Candidate runtime(Item item,int damage,QuantityTier tier){if(item==null||tier==null)throw new IllegalArgumentException("Profession candidate item and tier are required.");ItemStack stack=new ItemStack(item,1,damage);return new Candidate(String.valueOf(Item.itemRegistry.getNameForObject(item)),Item.getIdFromItem(item),damage,item.getItemStackLimit(),stack.getDisplayName(),tier);}
    }
    public static final class Requirement {
        public final String itemKey,displayName,metadataPolicy;public final int itemId,damage,required;public int delivered;
        Requirement(Candidate item,int required){this(item.key,item.itemId,item.damage,item.metadataPolicy,required,0,item.displayName);}
        Requirement(String key,int id,int damage,String policy,int required,int delivered,String display){this.itemKey=key==null?"":key;this.itemId=id;this.damage=damage;this.metadataPolicy=policy==null?"":policy;this.required=required;this.delivered=delivered;this.displayName=display==null?"":display;}
        public boolean complete(){return delivered>=required;}
        NBTTagCompound write(){NBTTagCompound tag=new NBTTagCompound();tag.setString("ItemKey",itemKey);tag.setInteger("Item",itemId);tag.setInteger("Damage",damage);tag.setString("MetadataPolicy",metadataPolicy);tag.setInteger("Required",required);tag.setInteger("Delivered",delivered);tag.setString("Display",displayName);return tag;}
        static Requirement read(NBTTagCompound tag,CandidateResolver resolver){if(tag==null||resolver==null)return null;String key=tag.getString("ItemKey"),policy=tag.getString("MetadataPolicy");int damage=tag.getInteger("Damage"),required=tag.getInteger("Required"),delivered=tag.getInteger("Delivered");if(!EXACT_METADATA.equals(policy)||required<=0||delivered<0||delivered>required)return null;Candidate candidate=resolver.resolve(key,tag.getInteger("Item"),damage);if(candidate==null||candidate.key.length()==0)return null;String display=tag.getString("Display");if(display.trim().length()==0)display=candidate.displayName;return display.trim().length()==0?null:new Requirement(candidate.key,candidate.itemId,damage,policy,required,delivered,display);}
    }

    public final String tradeKey,tradeDisplayName,materialProfileKey;
    public final List<Requirement> requirements;
    public KOMESerfProfessionAssignment(String tradeKey,String tradeDisplayName,String profile,List<Requirement> requirements){this.tradeKey=tradeKey==null?"":tradeKey;this.tradeDisplayName=tradeDisplayName==null?"":tradeDisplayName;this.materialProfileKey=profile==null?"":profile;this.requirements=requirements;}

    public static KOMESerfProfessionAssignment generate(KOMESerfProfessionClassifier.Profession profession,String faction,Random random){if(profession==null)profession=KOMESerfProfessionClassifier.forKey("general_labor");return generate(profession.key,profession.displayName,profession.materialProfile.key,KOMESerfProfessionCatalog.candidatesFor(profession.materialProfile,faction),random);}
    static KOMESerfProfessionAssignment generate(String tradeKey,String tradeName,String profile,List<Candidate> pool,Random random){
        if(KOMESerfProfessionClassifier.forKey(tradeKey)==null||KOMESerfMaterialProfile.forKey(profile)==null||tradeName==null||tradeName.trim().length()==0||pool==null||pool.size()<3||random==null)throw new IllegalArgumentException("Complete Profession candidates are required.");
        List<Candidate> available=distinct(pool),selected=new ArrayList<Candidate>();
        takeTier(available,selected,QuantityTier.BULK_COMMON,random);
        takeTier(available,selected,QuantityTier.STANDARD,random);
        while(selected.size()<3){List<Candidate> normal=new ArrayList<Candidate>();for(Candidate c:available)if(c.tier!=QuantityTier.RARE||!hasRare(selected))normal.add(c);List<Candidate> source=normal.isEmpty()?available:normal;if(source.isEmpty())throw new IllegalArgumentException("Three distinct Profession candidates are required.");Candidate chosen=source.get(random.nextInt(source.size()));selected.add(chosen);available.remove(chosen);}
        List<Requirement> requirements=new ArrayList<Requirement>();for(Candidate candidate:selected)requirements.add(new Requirement(candidate,quantity(candidate,random)));
        return new KOMESerfProfessionAssignment(tradeKey,tradeName,profile,requirements);
    }
    private static List<Candidate> distinct(List<Candidate> pool){List<Candidate> result=new ArrayList<Candidate>();for(Candidate candidate:pool){if(candidate==null||candidate.tier==null||candidate.key.length()==0||!EXACT_METADATA.equals(candidate.metadataPolicy))continue;boolean found=false;for(Candidate old:result)if(old.key.equals(candidate.key)&&old.damage==candidate.damage){found=true;break;}if(!found)result.add(candidate);}return result;}
    private static void takeTier(List<Candidate> available,List<Candidate> selected,QuantityTier tier,Random random){List<Candidate> matches=new ArrayList<Candidate>();for(Candidate candidate:available)if(candidate.tier==tier)matches.add(candidate);if(matches.isEmpty())return;Candidate chosen=matches.get(random.nextInt(matches.size()));selected.add(chosen);available.remove(chosen);}
    private static boolean hasRare(List<Candidate> values){for(Candidate value:values)if(value.tier==QuantityTier.RARE)return true;return false;}
    static int quantity(Candidate candidate,Random random){QuantityTier tier=candidate.maxStack==1?QuantityTier.NONSTACKABLE:candidate.tier;return tier.minimum+random.nextInt(tier.maximum-tier.minimum+1);}
    public boolean complete(){if(requirements==null||requirements.size()!=3)return false;for(Requirement requirement:requirements)if(requirement==null||!requirement.complete())return false;return true;}
    public NBTTagCompound writeToNBT(){NBTTagCompound tag=new NBTTagCompound();tag.setInteger("Version",VERSION);tag.setString("TradeKey",tradeKey);tag.setString("TradeDisplayName",tradeDisplayName);tag.setString("MaterialProfileKey",materialProfileKey);NBTTagList list=new NBTTagList();for(Requirement requirement:requirements)list.appendTag(requirement.write());tag.setTag("Requirements",list);return tag;}
    public static KOMESerfProfessionAssignment readFromNBT(NBTTagCompound tag){return readFromNBT(tag,new CandidateResolver(){public Candidate resolve(String key,int legacyItemId,int damage){Item item=key==null||key.length()==0?Item.getItemById(legacyItemId):(Item)Item.itemRegistry.getObject(key);return item==null?null:Candidate.runtime(item,damage,QuantityTier.STANDARD);}});}
    static KOMESerfProfessionAssignment readFromNBT(NBTTagCompound tag,CandidateResolver resolver){if(tag==null||resolver==null||tag.getInteger("Version")!=VERSION)return null;String trade=tag.getString("TradeKey"),name=tag.getString("TradeDisplayName"),profile=tag.getString("MaterialProfileKey");if(KOMESerfProfessionClassifier.forKey(trade)==null||KOMESerfMaterialProfile.forKey(profile)==null||name.trim().length()==0)return null;NBTTagList list=tag.getTagList("Requirements",10);if(list.tagCount()!=3)return null;List<Requirement> requirements=new ArrayList<Requirement>();for(int i=0;i<3;i++){Requirement requirement=Requirement.read(list.getCompoundTagAt(i),resolver);if(requirement==null)return null;for(Requirement old:requirements)if(old.itemKey.equals(requirement.itemKey)&&old.damage==requirement.damage)return null;requirements.add(requirement);}return new KOMESerfProfessionAssignment(trade,name,profile,requirements);}
}
