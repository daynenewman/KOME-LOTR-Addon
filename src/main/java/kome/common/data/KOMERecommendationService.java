package kome.common.data;

import lotr.common.LOTRLevelData;
import lotr.common.entity.npc.LOTREntityNPC;
import lotr.common.fac.LOTRFaction;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.util.ChatComponentText;

/** The written letter is an in-world keepsake. Only saved, earned provenance admits service. */
public final class KOMERecommendationService {
    private KOMERecommendationService() {}
    public static boolean matches(ItemStack book,KOMEServiceRecognition.Record r){
        if(book==null||r==null||book.getItem()!=Items.written_book||!book.hasTagCompound())return false;
        NBTTagCompound n=book.getTagCompound();
        return r.id.equals(n.getString("KOMERecommendation"))&&r.recipient.toString().equals(n.getString("Recipient"))
            &&r.issuer.entityUuid.equals(n.getString("Issuer"))&&r.conferred.key.equals(n.getString("Standing"))
            &&KOMEProgressionFactionResolver.matches(n.getString("Faction"),KOMEProgressionFactionResolver.resolve(r.faction));
    }
    public static boolean hasIntroduction(EntityPlayer player){
        if(player==null||player.worldObj==null||player.worldObj.isRemote||player.inventory==null)return false;
        LOTRFaction f=LOTRLevelData.getData(player).getPledgeFaction();if(f==null)return false;
        KOMEPlayerProgression p=KOMEWorldData.get(player.worldObj).getProgression(player.getUniqueID());
        KOMEServiceRecognition.Record r=p.getServiceRecognition().recommendation(player.getUniqueID(),p.getCanonicalRank(),f.codeName());
        if(r==null)return false;for(ItemStack book:player.inventory.mainInventory)if(matches(book,r))return true;return false;
    }
    static void keepsake(EntityPlayerMP player){
        KOMEPlayerProgression p=KOMEWorldData.get(player.worldObj).getProgression(player.getUniqueID());
        for(KOMEServiceRecognition.Record r:p.getServiceRecognition().records())if(r.redeemed)
            for(ItemStack book:player.inventory.mainInventory)if(matches(book,r))book.getTagCompound().setBoolean("KOMERecommendationRedeemed",true);
        player.inventory.markDirty();player.inventoryContainer.detectAndSendChanges();
    }
    public static String recognition(LOTRFaction f,KOMEProgressionRank from){
        boolean harsh=f!=null&&f.isBadRelation(LOTRFaction.GONDOR);
        if(from==KOMEProgressionRank.SERF)return harsh?"You have proved useful. Our people recognize you as a Knight. Take my word to a Lord.":
            "You have served faithfully. In our name, I grant you Knight standing. Take my letter to a Lord.";
        return harsh?"You have earned Lord standing. Carry my word to the Crown, and do not shame it.":
            "Your courage has earned Lord standing. My letter commends you to the Crown at our capital.";
    }
    public static String recognition(LOTRFaction f,KOMEProgressionRank from,KOMEProgressionNpcRank issuerRank){
        String standing=KOMEFactionProgressionTitles.title(f==null?"":f.codeName(),from==KOMEProgressionRank.SERF?KOMEProgressionRank.KNIGHT:KOMEProgressionRank.LORD);
        if(from==KOMEProgressionRank.SERF)return recognition(f,from).replace("Knight standing",standing+" standing").replace("as a Knight","as "+standing);
        boolean harsh=f!=null&&f.isBadRelation(LOTRFaction.GONDOR);
        return (harsh?"Your strength has proved your worth. ":"Your deeds have earned our people's trust. ")
            +"In our people's name, I grant "+standing+" standing. "
            +(issuerRank==KOMEProgressionNpcRank.LORD?"We now stand as equals. ":"You have joined our realm's nobility. ")
            +"My letter is for the Crown at our capital.";
    }
    public static boolean eligible(KOMEPlayerProgression p,java.util.UUID owner,String faction){
        return p!=null&&!p.getSerfKnightProgression().hasLiege()
            &&p.getServiceRecognition().recommendation(owner,p.getCanonicalRank(),faction)!=null;
    }
    /** Objective existing service permits replacement after a superior is lost; no arbitrary reputation score. */
    static boolean recoveryAdmission(KOMEPlayerProgression p,String faction){
        if(p==null||p.getSerfKnightProgression().hasLiege()||!p.getSerfKnightProgression().isLiegeReplacementRequired())return false;
        if(p.getSerfKnightProgression().getDeceasedLiege().isSet()&&KOMEProgressionFactionResolver.matches(p.getSerfKnightProgression().getDeceasedLiege().factionKey,KOMEProgressionFactionResolver.resolve(faction))
            &&(p.getCanonicalRank()==KOMEProgressionRank.KNIGHT||p.getCanonicalRank()==KOMEProgressionRank.LORD))return true;
        for(KOMEServiceRecognition.Record r:p.getServiceRecognition().records())
            if(!r.invalidated&&r.redeemed&&r.conferred==p.getCanonicalRank()&&KOMEProgressionFactionResolver.matches(r.faction,KOMEProgressionFactionResolver.resolve(faction)))return true;
        return p.getCanonicalRank()==KOMEProgressionRank.KNIGHT&&!p.getKnightService().qualifyingTypes(faction).isEmpty();
    }
    static synchronized boolean redeem(KOMEPlayerProgression p,java.util.UUID owner,String faction,KOMEProgressionNpcRef receiving){
        if(receiving==null||!receiving.isSet()||!KOMEProgressionFactionResolver.matches(receiving.factionKey,KOMEProgressionFactionResolver.resolve(faction))||!eligible(p,owner,faction))return false;
        KOMEServiceRecognition.Record r=p.getServiceRecognition().recommendation(owner,p.getCanonicalRank(),faction);
        r.redeemed=true;p.getSerfKnightProgression().setLiege(receiving);return true;
    }
    public static ItemStack letter(KOMEServiceRecognition.Record r,String playerName){
        ItemStack book=new ItemStack(Items.written_book);NBTTagCompound n=new NBTTagCompound();
        String culture=KOMEAlliance.normalizeFactionKey(r.faction);
        LOTRFaction f=KOMEProgressionFactionResolver.resolve(r.faction);
        boolean harsh=f!=null&&f.isBadRelation(LOTRFaction.GONDOR);String title=harsh?(r.from==KOMEProgressionRank.SERF?"A Master's Word":"A Commander's Word"):("durinsfolk".equals(culture)||"bluemountains".equals(culture))?"Declaration of Service":"Letter of Recommendation";
        n.setString("title",title);n.setString("author",r.issuer.displayName);
        String receiver=r.conferred==KOMEProgressionRank.KNIGHT?"a "+KOMEProgressionNativeAuthority.name(r.faction,KOMEProgressionNpcRank.LORD,"")+" or "+KOMEProgressionNativeAuthority.name(r.faction,KOMEProgressionNpcRank.PRINCE,"")+" at a regional seat":
            "the "+KOMEProgressionNativeAuthority.name(r.faction,KOMEProgressionNpcRank.KING,"")+" at our faction capital";
        String role=r.from==KOMEProgressionRank.SERF?"their former Master":"their former Liege";
        NBTTagList pages=new NBTTagList();pages.appendTag(new NBTTagString("To "+receiver+" of our people,\n\nI commend "+playerName+" to your service.\n\n"+
            (harsh?"Strength and loyalty have earned this standing.":"rohan".equals(culture)?"Their courage and loyalty are proven.":("durinsfolk".equals(culture)||"bluemountains".equals(culture))?"Their deeds and labour have earned our trust.":"gondor".equals(culture)?"Their honour and service are known to us.":"Their service has earned my trust.")+"\n\n"+r.issuer.displayName+", "+role+"\n"+(f==null?r.faction:f.factionName())));
        String service=r.accomplishments;
        for(int start=0;start<service.length();start+=190)pages.appendTag(new NBTTagString((start==0?"Service rendered:\n\n":"")+service.substring(start,Math.min(service.length(),start+190))));
        pages.appendTag(new NBTTagString("I recognized their "+KOMEFactionProgressionTitles.title(r.faction,r.conferred)+" standing in our faction's name. Receive them when they seek your service.\n\nThis letter remains a keepsake after its introduction is accepted."));
        NBTTagList wrapped=new NBTTagList();for(int i=0;i<pages.tagCount();i++)for(String page:KOMECourierCorrespondence.pages(pages.getStringTagAt(i)))wrapped.appendTag(new NBTTagString(page));
        n.setTag("pages",wrapped);n.setString("KOMERecommendation",r.id);n.setString("Recipient",r.recipient.toString());
        n.setString("Issuer",r.issuer.entityUuid);n.setString("Faction",r.faction);n.setString("Standing",r.conferred.key);book.setTagCompound(n);return book;
    }
    /** Explicit recovery also works after the issuer has died or unloaded; never spawns a replacement NPC. */
    public static boolean deliver(EntityPlayerMP player){
        KOMEWorldData data=KOMEWorldData.get(player.worldObj);KOMEPlayerProgression p=data.getProgression(player.getUniqueID());
        LOTRFaction f=LOTRLevelData.getData(player).getPledgeFaction();if(f==null)return false;
        KOMEServiceRecognition.Record r=p.getServiceRecognition().recommendation(player.getUniqueID(),p.getCanonicalRank(),f.codeName());if(r==null)return false;
        for(ItemStack stack:player.inventory.mainInventory)if(matches(stack,r))return true;
        int slot=-1;for(int i=0;i<player.inventory.mainInventory.length;i++)if(player.inventory.mainInventory[i]==null){slot=i;break;}
        if(slot<0){
            player.addChatMessage(new ChatComponentText("Your letter is recorded. Make room, then request it again or use /progression recommendation."));return false;
        }
        player.inventory.setInventorySlotContents(slot,letter(r,player.getCommandSenderName()));
        player.inventory.markDirty();player.inventoryContainer.detectAndSendChanges();data.markDirty();return true;
    }
    public static boolean recognizeFormer(EntityPlayerMP player,LOTREntityNPC npc){
        if(player==null||npc==null||player.worldObj.isRemote||npc.worldObj!=player.worldObj||!npc.isEntityAlive()||player.getDistanceSqToEntity(npc)>64)return false;
        KOMEPlayerProgression p=KOMEWorldData.get(player.worldObj).getProgression(player.getUniqueID());
        for(KOMEServiceRecognition.Record r:p.getServiceRecognition().records())if(r.issuer.entityUuid.equals(npc.getUniqueID().toString())||r.priorSuperior.entityUuid.equals(npc.getUniqueID().toString())){
            KOMEProgressionNpcSpeech.say(player,npc,npc.getFaction().isBadRelation(LOTRFaction.GONDOR)?"I remember your service. Do not disgrace your standing.":
                r.conferred==KOMEProgressionRank.LORD?"We meet as fellow Lords. I remember your service.":"You have come far. I remember your service.");
            deliver(player);return true;
        }return false;
    }
}
