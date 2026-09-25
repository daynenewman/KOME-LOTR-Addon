package kome.common.data;

import java.util.UUID;
import kome.common.KOMEReflection;
import lotr.common.LOTRLevelData;
import lotr.common.entity.npc.LOTREntityNPC;
import lotr.common.fac.LOTRFaction;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Server-only destination resolution, dispatch identity, and delivery validation. */
public final class KOMECourierService {
    private static final Logger LOGGER=LogManager.getLogger("KOMECourier");
    private static final String TAG="KOMECourier";
    static final double ARRIVAL_RADIUS=192D,SETTLEMENT_RADIUS=256D;
    private KOMECourierService(){}

    public static ItemStack message(KOMESerfCourierAssignment a,EntityPlayerMP p,KOMEProgressionNpcRef master){return message(a,KOMEReflection.getEntityUUID(p),master);}
    static ItemStack message(KOMESerfCourierAssignment a,UUID player,KOMEProgressionNpcRef master){
        ItemStack book=new ItemStack(Items.written_book);NBTTagCompound root=new NBTTagCompound(),hidden=new NBTTagCompound();
        hidden.setString("Assignment",a.token);hidden.setString("Owner",player.toString());hidden.setString("Master",master.entityUuid);hidden.setString("Destination",a.destinationKey);root.setTag(TAG,hidden);
        root.setString("title",dispatchTitle(a));root.setString("author",master.displayName);NBTTagList pages=new NBTTagList();
        pages.appendTag(new NBTTagString(dispatchText(a,master)));root.setTag("pages",pages);book.setTagCompound(root);return book;
    }

    static String dispatchTitle(KOMESerfCourierAssignment a){LOTRFaction f=KOMEProgressionFactionResolver.resolve(a.destinationFactionKey);return f==null?"Sealed Dispatch":f.factionName()+" Dispatch";}
    static String dispatchText(KOMESerfCourierAssignment a,KOMEProgressionNpcRef master){
        String recipient=a.recipient.isSet()?a.recipient.displayName:"The appointed recipient";
        String body=story(a.storyVariant,a.destinationFactionKey,a.destinationName);
        LOTRFaction faction=KOMEProgressionFactionResolver.resolve(a.destinationFactionKey);String factionName=faction==null?a.destinationFactionKey:faction.factionName();
        return recipient+",\n\n"+body+"\n\nFor "+factionName+".\n— "+master.displayName+"\n\nCarry this sealed letter to "+recipient+" at "+a.destinationName+".";
    }
    private static String story(int variant,String faction,String destination){
        String f=faction==null?"":faction.toLowerCase();boolean rohan=f.contains("rohan");boolean evil=f.contains("mordor")||f.contains("orc")||f.contains("uruk");boolean dwarf=f.contains("dwarf");boolean elf=f.contains("elf")||f.contains("lothlorien");
        switch(Math.floorMod(variant,5)){
        case 0:return rohan?"Riders report movement along the road. Keep watch from "+destination+" and send word if they draw nearer.":evil?"Scouts have sighted enemies near the road. Double the watch and report every movement.":"Patrols report strangers on the road. Keep a close watch and send word of anything amiss.";
        case 1:return dwarf?"Our stores of iron and lamp-oil run low. Count what remains and make ready to receive fresh supplies.":rohan?"See that the horse-lines and grain stores are provisioned before the next patrol rides.":"Take account of the provisions at "+destination+" and report what must be sent before winter.";
        case 2:return evil?"Muster those fit to fight and see that their weapons are ready. Delay will not be forgiven.":elf?"Call the wardens together and see that bowstrings and stores are made ready.":"Muster the available men and inspect their arms. Send me a true account of your strength.";
        case 3:return dwarf?"The road-borne merchants are overdue. Hold their goods safely and send an accounting of every crate.":"Receive the next supply train in good order and send back a tally of the goods entrusted to you.";
        default:return "Place this report among the records of "+destination+" and return a sealed acknowledgement by the next messenger.";
        }
    }

    public static boolean matching(ItemStack s,KOMESerfCourierAssignment a,EntityPlayerMP p,KOMEProgressionNpcRef m){return matching(s,a,KOMEReflection.getEntityUUID(p),m);}
    static boolean matching(ItemStack s,KOMESerfCourierAssignment a,UUID owner,KOMEProgressionNpcRef m){return identityMatch(s,a,owner,m)&&a.destinationKey.equals(s.getTagCompound().getCompoundTag(TAG).getString("Destination"));}
    private static boolean identityMatch(ItemStack s,KOMESerfCourierAssignment a,UUID owner,KOMEProgressionNpcRef m){if(s==null||a==null||owner==null||m==null||s.getItem()!=Items.written_book||!s.hasTagCompound()||!s.getTagCompound().hasKey(TAG,10))return false;NBTTagCompound t=s.getTagCompound().getCompoundTag(TAG);return a.valid()&&a.token.equals(t.getString("Assignment"))&&owner.toString().equals(t.getString("Owner"))&&m.entityUuid.equals(t.getString("Master"));}
    public static boolean hasMessage(EntityPlayerMP p,KOMESerfCourierAssignment a,KOMEProgressionNpcRef m){for(ItemStack s:p.inventory.mainInventory)if(matching(s,a,p,m))return true;return false;}
    public static boolean removeMessage(EntityPlayerMP p,KOMESerfCourierAssignment a,KOMEProgressionNpcRef m){for(int i=0;i<p.inventory.mainInventory.length;i++)if(matching(p.inventory.mainInventory[i],a,p,m)){p.inventory.mainInventory[i]=null;return true;}return false;}
    /** Called only for a nearby NPC interaction; the binding and letter remain server authority. */
    public static boolean deliverToRecipient(EntityPlayerMP player,KOMEWorldData world,LOTREntityNPC npc){
        if(player==null||world==null||npc==null)return false;
        KOMEPlayerProgression progression=world.getProgression(player.getUniqueID());KOMESerfKnightProgression state=progression.getSerfKnightProgression();
        if(progression.getCanonicalRank()!=KOMEProgressionRank.SERF||!"courier".equals(state.getActiveAssignmentKind()))return false;
        KOMESerfCourierAssignment assignment=KOMESerfCourierAssignment.readFromNBT(state.getDuty(KOMESerfKnightDutyType.COURIER).getAssignmentData());
        if(assignment==null||!validRecipient(player,npc,assignment,state.getSerfdomMaster())||!removeMessage(player,assignment,state.getSerfdomMaster()))return false;
        assignment.stage=KOMESerfCourierAssignment.Stage.DELIVERED;
        state.setDutyAssignmentData(KOMESerfKnightDutyType.COURIER,assignment.writeToNBT());world.markDirty();
        KOMEProgressionNpcRoles.syncPlayer(world,player.getUniqueID());npc.getEntityData().removeTag(KOMECourierRecipientSpawner.TOKEN);
        player.inventoryContainer.detectAndSendChanges();KOMEProgressionAutoCompleter.syncPlayer(player,progression);
        return true;
    }
    /** The Master explicitly accepts a delivered dispatch on return. */
    public static boolean reportToMaster(EntityPlayerMP player,KOMEWorldData world,KOMEPlayerProgression progression){
        if(player==null||world==null||progression==null||progression.getCanonicalRank()!=KOMEProgressionRank.SERF)return false;
        KOMESerfKnightProgression state=progression.getSerfKnightProgression();
        if(!"courier".equals(state.getActiveAssignmentKind()))return false;
        KOMESerfCourierAssignment assignment=KOMESerfCourierAssignment.readFromNBT(state.getDuty(KOMESerfKnightDutyType.COURIER).getAssignmentData());
        if(assignment==null||assignment.stage!=KOMESerfCourierAssignment.Stage.DELIVERED||!assignment.recipient.isSet())return false;
        if(!KOMESerfKnightService.completeDuty(progression,KOMESerfKnightDutyType.COURIER).success)return false;
        KOMEProgressionNpcRoles.syncPlayer(world,player.getUniqueID());
        world.markDirty();player.inventoryContainer.detectAndSendChanges();KOMEProgressionAutoCompleter.syncPlayer(player,progression);return true;
    }

    /** Rewrites the one assignment-owned physical book in place, removing accidental duplicates. */
    static void refreshDispatch(EntityPlayerMP p,KOMESerfCourierAssignment a,KOMEProgressionNpcRef master){
        UUID owner=KOMEReflection.getEntityUUID(p);int first=-1;
        for(int i=0;i<p.inventory.mainInventory.length;i++)if(identityMatch(p.inventory.mainInventory[i],a,owner,master)){if(first<0)first=i;else p.inventory.mainInventory[i]=null;}
        ItemStack current=message(a,owner,master);if(first>=0)p.inventory.mainInventory[first]=current;else p.inventory.addItemStackToInventory(current);p.inventoryContainer.detectAndSendChanges();
    }

    public static boolean validRecipient(EntityPlayerMP p,LOTREntityNPC n,KOMESerfCourierAssignment a,KOMEProgressionNpcRef master){return eligible(n,a,master)&&a.stage==KOMESerfCourierAssignment.Stage.OUTBOUND&&a.recipient.isSet()&&a.recipient.hasSameIdentity(KOMEProgressionNpcRankService.referenceOf(n))&&KOMEProgressionFactionResolver.matches(a.masterFactionKey,LOTRLevelData.getData(p).getPledgeFaction())&&p.getDistanceSqToEntity(n)<=64D;}
    private static boolean eligible(LOTREntityNPC n,KOMESerfCourierAssignment a,KOMEProgressionNpcRef master){return n!=null&&n.isEntityAlive()&&!n.isChild()&&KOMEProgressionNpcRankService.isValidFactionNpc(n)&&n.hiredNPCInfo!=null&&!n.hiredNPCInfo.isActive&&n.bossInfo==null&&!n.isTraderEscort&&!master.hasSameIdentity(KOMEProgressionNpcRankService.referenceOf(n))&&KOMEProgressionFactionResolver.matches(a.destinationFactionKey,n.getFaction())&&a.atDestination(n.worldObj.provider.dimensionId,n.posX,n.posZ,SETTLEMENT_RADIUS);}

    public static void tickPlayer(EntityPlayerMP p){
        // KOMEEvents already calls this on world-time multiples of 20. Player age has an
        // independent phase after login, so combining the two clocks can suppress every call.
        if(p==null||p.worldObj.isRemote||p.worldObj.getTotalWorldTime()%100L!=0L)return;
        KOMEWorldData world=KOMEWorldData.get(p.worldObj);
        KOMEPlayerProgression progression=world.getProgression(p.getUniqueID());
        KOMESerfKnightProgression state=progression.getSerfKnightProgression();
        if(!"courier".equals(state.getActiveAssignmentKind()))return;
        KOMESerfCourierAssignment a=KOMESerfCourierAssignment.readFromNBT(state.getDuty(KOMESerfKnightDutyType.COURIER).getAssignmentData());
        if(a==null||a.stage!=KOMESerfCourierAssignment.Stage.OUTBOUND)return;
        if(a.recipient.isSet()){
            LOTREntityNPC bound=loadedRecipient(p,a.recipient.entityUuid);
            if(bound==null)return; // Unload is not death.
            if(eligible(bound,a,state.getSerfdomMaster()))return;
            // A loaded, genuinely invalid bound entity cannot keep the duty stuck.
            a.recipient=KOMEProgressionNpcRef.EMPTY;
            persist(world,p,progression,state,a);refreshDispatch(p,a,state.getSerfdomMaster());
        }
        if(!a.atDestination(p.dimension,p.posX,p.posZ,ARRIVAL_RADIUS)||p.worldObj.getTotalWorldTime()<a.nextRecipientWorldTime)return;
        if(LOGGER.isDebugEnabled())LOGGER.debug("Courier arrival token={} dimension={} destination=({}, {}) player=({}, {})",a.token,a.dimension,a.destinationX,a.destinationZ,p.posX,p.posZ);
        LOTREntityNPC recipient=KOMECourierRecipientSpawner.findOwned(p.worldObj,a.token);
        if(recipient==null)recipient=KOMECourierRecipientSpawner.spawn(p,a);
        if(recipient==null)return; // Unsafe or unloaded terrain: wait here, never reroute.
        a.recipient=KOMEProgressionNpcRankService.referenceOf(recipient);
        persist(world,p,progression,state,a);refreshDispatch(p,a,state.getSerfdomMaster());
        if(LOGGER.isDebugEnabled())LOGGER.debug("Courier bound token={} recipient={} class={} stage={} lease={}",a.token,a.recipient.entityUuid,recipient.getClass().getName(),a.stage,KOMEProgressionNpcRoles.protects(world,recipient.getUniqueID()));
    }    private static LOTREntityNPC loadedRecipient(EntityPlayerMP player,String id){for(Object value:player.worldObj.loadedEntityList)if(value instanceof LOTREntityNPC&&id.equals(((LOTREntityNPC)value).getUniqueID().toString()))return (LOTREntityNPC)value;return null;}
    private static void persist(KOMEWorldData world,EntityPlayerMP p,KOMEPlayerProgression progression,KOMESerfKnightProgression state,KOMESerfCourierAssignment a){state.setDutyAssignmentData(KOMESerfKnightDutyType.COURIER,a.writeToNBT());KOMEProgressionNpcRoles.syncPlayer(world,p.getUniqueID());world.markDirty();KOMEProgressionAutoCompleter.syncPlayer(p,progression);}

    public static boolean handleRecipientDeath(KOMEWorldData world,String npcId){return handleRecipientDeath(world,npcId,null);}
    public static boolean handleRecipientDeath(KOMEWorldData world,String npcId,net.minecraft.world.World serverWorld){if(world==null||npcId==null)return false;boolean changed=false;for(java.util.Map.Entry<UUID,KOMEPlayerProgression> entry:world.progressions.entrySet()){KOMEPlayerProgression progression=entry.getValue();KOMESerfKnightProgression state=progression.getSerfKnightProgression();if(!"courier".equals(state.getActiveAssignmentKind()))continue;KOMESerfCourierAssignment a=KOMESerfCourierAssignment.readFromNBT(state.getDuty(KOMESerfKnightDutyType.COURIER).getAssignmentData());if(a!=null&&a.stage==KOMESerfCourierAssignment.Stage.OUTBOUND&&npcId.equals(a.recipient.entityUuid)){a.recipient=KOMEProgressionNpcRef.EMPTY;a.recipientDeaths=Math.min(100,a.recipientDeaths+1);long now=serverWorld==null?0L:serverWorld.getTotalWorldTime();a.nextRecipientWorldTime=now+replacementDelay(a.recipientDeaths);state.setDutyAssignmentData(KOMESerfKnightDutyType.COURIER,a.writeToNBT());KOMEProgressionNpcRoles.syncPlayer(world,entry.getKey());if(serverWorld!=null){net.minecraft.entity.player.EntityPlayer player=serverWorld.func_152378_a(entry.getKey());if(player instanceof EntityPlayerMP)refreshDispatch((EntityPlayerMP)player,a,state.getSerfdomMaster());}changed=true;}}if(changed)world.markDirty();return changed;}
    static long replacementDelay(int deaths){return Math.min(6000L,1200L*Math.max(1,deaths));}
}
