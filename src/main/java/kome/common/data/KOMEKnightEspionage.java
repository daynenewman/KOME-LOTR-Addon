package kome.common.data;

import java.util.*;
import lotr.common.*;
import lotr.common.entity.npc.*;
import lotr.common.fac.LOTRFaction;
import lotr.common.quest.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.world.World;
import static kome.common.data.KOMEKnightCommission.*;

/** Authenticated adapter to v36.15's actual theft, witness, sound and particle implementation.
 * No native miniquest is registered or saved; the existing commission owns all evidence. */
public final class KOMEKnightEspionage {
    static final String DISPATCH="KOMESealedDispatch";
    private static final ThreadLocal<Attempt> CURRENT=new ThreadLocal<Attempt>();
    interface Sight {boolean watching(LOTREntityNPC npc,net.minecraft.entity.EntityLivingBase player);}
    static Sight sight=new Sight(){
        private java.lang.reflect.Method method;private LOTRMiniQuestPickpocket nativeSight;
        public boolean watching(LOTREntityNPC npc,net.minecraft.entity.EntityLivingBase player){
            try{if(method==null){method=LOTRMiniQuestPickpocket.class.getDeclaredMethod("isEntityWatching",net.minecraft.entity.EntityLiving.class,net.minecraft.entity.EntityLivingBase.class);method.setAccessible(true);nativeSight=new LOTRMiniQuestPickpocket(null);}return (Boolean)method.invoke(nativeSight,npc,player);}
            catch(ReflectiveOperationException unavailable){throw new IllegalStateException("Native v36.15 pickpocket sight is unavailable",unavailable);}
        }
    };
    private KOMEKnightEspionage() {}
    // Actual v36.15 military traders audited in Pass 4; no King or fabricated NPC class.
    private static final String[] OFFICERS={"BreeCaptain","RangerNorthCaptain","BlueDwarfCommander","HighElfLord","RivendellLord",
        "GundabadOrcMercenaryCaptain","AngmarOrcMercenaryCaptain","AngmarHillmanChieftain","WoodElfCaptain","DolGuldurOrcChieftain",
        "DaleCaptain","DwarfCommander","GaladhrimLord","DunlendingWarlord","UrukHaiMercenaryCaptain","RohirrimMarshal","GondorianCaptain",
        "MordorOrcMercenaryCaptain","DorwinionCaptain","EasterlingWarlord","NearHaradrimWarlord","MoredainChieftain","TauredainChieftain"};
    static String officer(World world,LOTRFaction faction){
        for(String suffix:OFFICERS){String name="lotr.common.entity.npc.LOTREntity"+suffix;LOTREntityNPC npc=KOMEKnightCommissionService.construct(world,name);
            if(npc!=null&&npc.getFaction()==faction&&!npc.isChild()&&npc.bossInfo==null&&KOMEProgressionLords.isStandingTrialLiegeCandidate(npc))return name;
        }return null;
    }
    private static final class Attempt {
        final EntityPlayerMP player;final LOTREntityNPC npc;final KOMEKnightCommission a;
        Attempt(EntityPlayerMP player,LOTREntityNPC npc,KOMEKnightCommission a){this.player=player;this.npc=npc;this.a=a;}
    }
    /** Native hostile target seeking has no facing requirement. During this one saved infiltration,
     * apply the native pickpocket's own 130-degree sight rule to the assignee until an alarm.
     * Hurt retaliation, native theft detection, every other player and normal combat remain intact. */
    static boolean interceptTarget(LOTREntityNPC npc,EntityPlayerMP player){
        KOMEProgressionEncounterMarker.Marker marker=KOMEProgressionEncounterMarker.read(npc);
        if(marker==null||!KOMEKnightCommissionService.MARKER.equals(marker.kind)||!player.getUniqueID().equals(marker.owner))return false;
        KOMEWorldData data=KOMEWorldData.get(npc.worldObj);KOMEPlayerProgression p=data.progressions.get(marker.owner);KOMEKnightCommission a=p==null?null:p.getKnightService().assignment();
        if(a==null||a.type!=Type.WAR_PLANS||a.stage!=Stage.ACTIVE||a.detected||!a.token.equals(marker.token)||!a.liege.hasSameIdentity(p.getSerfKnightProgression().getLiege())||!KOMEProgressionFactionResolver.matches(a.faction,LOTRLevelData.getData(player).getPledgeFaction()))return false;
        boolean owned=false;for(Actor actor:a.actors)if(actor.role==Role.ENEMY&&!actor.dead&&actor.id.equals(npc.getUniqueID().toString())&&actor.className.equals(npc.getClass().getName()))owned=true;if(!owned)return false;
        if(CURRENT.get()!=null||!player.isSneaking()||a.combatDamage>0||npc.getAITarget()==player||sight.watching(npc,player)){
            a.detected=true;data.markDirty();alert(player,a);return false;
        }
        npc.setAttackTarget(null);npc.getNavigator().clearPathEntity();return true;
    }
    static boolean validTarget(EntityPlayerMP player,LOTREntityNPC npc,KOMEKnightCommission a){
        if(a==null||a.type!=Type.WAR_PLANS||a.stage!=Stage.ACTIVE||a.dispatchStolen||!a.encounterCreated||npc==null||npc.worldObj!=player.worldObj
                ||!npc.isEntityAlive()||!a.targetId.equals(npc.getUniqueID().toString())||!KOMEProgressionFactionResolver.matches(a.enemyFaction,npc.getFaction()))return false;
        KOMEPlayerProgression p=KOMEWorldData.get(player.worldObj).getProgression(player.getUniqueID());
        KOMEProgressionEncounterMarker.Marker marker=KOMEProgressionEncounterMarker.read(npc);
        if(p.getCanonicalRank()!=KOMEProgressionRank.KNIGHT||!a.liege.hasSameIdentity(p.getSerfKnightProgression().getLiege())
                ||!KOMEProgressionFactionResolver.matches(a.faction,LOTRLevelData.getData(player).getPledgeFaction())||marker==null
                ||!KOMEKnightCommissionService.MARKER.equals(marker.kind)||!player.getUniqueID().equals(marker.owner)||!a.token.equals(marker.token))return false;
        for(Actor actor:a.actors)if(!actor.dead&&actor.role==Role.ENEMY&&actor.id.equals(a.targetId)&&actor.className.equals(npc.getClass().getName()))return true;
        return false;
    }
    public static boolean interact(EntityPlayerMP player,LOTREntityNPC npc){
        KOMEKnightCommission a=KOMEWorldData.get(player.worldObj).getProgression(player.getUniqueID()).getKnightService().assignment();
        if(!validTarget(player,npc,a))return false;
        if(!player.isSneaking()||player.getCurrentEquippedItem()!=null){KOMEProgressionNpcSpeech.say(player,npc,"Approach unseen, crouch, and keep your hand empty to lift the dispatch.");return true;}
        if(player.getDistanceSqToEntity(npc)>9||!player.canEntityBeSeen(npc))return true;
        long now=player.worldObj.getTotalWorldTime();if(now<a.nextPickpocketAt)return true;a.nextPickpocketAt=now+40;
        LOTRMiniQuestPickpocket nativeTheft=new LOTRMiniQuestPickpocket(LOTRLevelData.getData(player)){
            @Override protected void updateQuest(){} // The commission persists; no parallel native quest/reward ledger.
        };
        nativeTheft.entityUUID=UUID.fromString(a.liege.entityUuid);nativeTheft.entityNameFull=a.liege.displayName;nativeTheft.pickpocketFaction=npc.getFaction();
        CURRENT.set(new Attempt(player,npc,a));
        try{nativeTheft.onInteractOther(player,npc);}finally{CURRENT.remove();}
        if(npc.getAttackTarget()==player){a.detected=true;alert(player,a);}
        if(a.dispatchStolen){a.phase=Phase.ESCAPE;if(a.complication==Complication.COUNTERATTACK){a.complicationTriggered=true;a.detected=true;alert(player,a);}}
        KOMEWorldData.get(player.worldObj).markDirty();player.inventory.markDirty();player.inventoryContainer.detectAndSendChanges();return true;
    }
    /** Used only by the native theft's two interface instructions. Existing native targets retain their methods. */
    public static boolean pickpocketable(LOTREntityNPC npc){Attempt c=CURRENT.get();return c!=null&&c.npc==npc||npc instanceof IPickpocketable;}
    public static IPickpocketable adapter(LOTREntityNPC npc){
        final Attempt c=CURRENT.get();if(c==null||c.npc!=npc)return (IPickpocketable)npc;
        return new IPickpocketable(){public boolean canPickpocket(){return validTarget(c.player,c.npc,c.a);}
            public ItemStack createPickpocketItem(){return steal(c.player,c.npc,c.a);}};
    }
    public static void achievement(LOTRPlayerData data,LOTRAchievement achievement){Attempt c=CURRENT.get();if(c==null||!c.a.adminForced)data.addAchievement(achievement);}
    static ItemStack steal(EntityPlayerMP player,LOTREntityNPC npc,KOMEKnightCommission a){
        if(!validTarget(player,npc,a))throw new IllegalStateException("Unbound dispatch target");
        a.dispatchStolen=true;a.phase=Phase.ESCAPE;KOMEWorldData.get(player.worldObj).markDirty();return dispatch(a,player.getUniqueID());
    }
    static ItemStack dispatch(KOMEKnightCommission a,UUID owner){
        ItemStack book=new ItemStack(Items.written_book);NBTTagCompound root=new NBTTagCompound(),tag=new NBTTagCompound();
        root.setString("title","Sealed military dispatch");root.setString("author",a.enemyFaction);
        NBTTagList pages=new NBTTagList();pages.appendTag(new NBTTagString("Sealed orders taken from "+a.place+". Deliver these unopened to "+a.liege.displayName+"."));root.setTag("pages",pages);
        tag.setString("Owner",owner.toString());tag.setString("Token",a.token);tag.setString("Target",a.targetId);tag.setString("Faction",a.faction);tag.setInteger("Revision",a.revision);root.setTag(DISPATCH,tag);book.setTagCompound(root);return book;
    }
    static boolean matches(ItemStack item,KOMEKnightCommission a,UUID owner){
        if(item==null||item.getItem()!=Items.written_book||item.stackSize!=1||a==null||a.type!=Type.WAR_PLANS||!a.dispatchStolen||a.targetId.isEmpty()||!item.hasTagCompound())return false;
        NBTTagCompound tag=item.getTagCompound().getCompoundTag(DISPATCH);
        return owner.toString().equals(tag.getString("Owner"))&&a.token.equals(tag.getString("Token"))&&a.targetId.equals(tag.getString("Target"))&&a.faction.equals(tag.getString("Faction"))&&a.revision==tag.getInteger("Revision");
    }
    static boolean consume(EntityPlayerMP player,KOMEKnightCommission a){if(!matches(player.getCurrentEquippedItem(),a,player.getUniqueID()))return false;player.inventory.setInventorySlotContents(player.inventory.currentItem,null);return true;}
    static void alert(EntityPlayerMP player,KOMEKnightCommission a){for(Actor actor:a.actors)if(actor.role==Role.ENEMY&&!actor.dead){Entity entity=KOMEKnightCommissionService.findLoaded(player.worldObj,actor.id);if(entity instanceof LOTREntityNPC)((LOTREntityNPC)entity).setAttackTarget(player,true);}}
    static boolean escaped(EntityPlayerMP player,KOMEKnightCommission a){
        if(!a.dispatchStolen||player.dimension!=a.dimension||!player.isEntityAlive()||a.atSite(player.dimension,player.posX,player.posZ,128))return false;
        for(Actor actor:a.actors)if(actor.role==Role.ENEMY&&!actor.dead){Entity e=KOMEKnightCommissionService.findLoaded(player.worldObj,actor.id);if(e!=null&&e.isEntityAlive()&&player.getDistanceSqToEntity(e)<48*48)return false;}
        for(ItemStack item:player.inventory.mainInventory)if(matches(item,a,player.getUniqueID()))return true;return false;
    }
    public static boolean bound(ItemStack item){return item!=null&&item.hasTagCompound()&&item.getTagCompound().hasKey(DISPATCH,10);}
    static boolean reconcile(KOMEWorldData data,net.minecraft.entity.item.EntityItem entity){
        ItemStack item=entity.getEntityItem();if(!bound(item))return false;
        try{UUID owner=UUID.fromString(item.getTagCompound().getCompoundTag(DISPATCH).getString("Owner"));KOMEPlayerProgression p=data.progressions.get(owner);KOMEKnightCommission a=p==null?null:p.getKnightService().assignment();
            if(a!=null&&a.live()&&matches(item,a,owner)){entity.lifespan=Integer.MAX_VALUE;entity.age=0;return false;}
        }catch(IllegalArgumentException invalid){}entity.setDead();return true;
    }
    static boolean canPickup(ItemStack item,UUID owner){return !bound(item)||owner!=null&&owner.toString().equals(item.getTagCompound().getCompoundTag(DISPATCH).getString("Owner"));}
    static void cleanup(World world,UUID owner,KOMEKnightCommission a){
        for(Object o:world.loadedEntityList)if(o instanceof net.minecraft.entity.item.EntityItem){net.minecraft.entity.item.EntityItem e=(net.minecraft.entity.item.EntityItem)o;if(belongs(e.getEntityItem(),a,owner))e.setDead();}
        net.minecraft.entity.player.EntityPlayer p=world.func_152378_a(owner);if(p!=null&&p.inventory!=null)for(int i=0;i<p.inventory.mainInventory.length;i++)if(belongs(p.inventory.mainInventory[i],a,owner))p.inventory.mainInventory[i]=null;
    }
    static boolean belongs(ItemStack item,KOMEKnightCommission a,UUID owner){return bound(item)&&a.token.equals(item.getTagCompound().getCompoundTag(DISPATCH).getString("Token"))&&owner.toString().equals(item.getTagCompound().getCompoundTag(DISPATCH).getString("Owner"));}
}
