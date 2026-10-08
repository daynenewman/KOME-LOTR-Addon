package kome.common.data;

import java.util.UUID;
import kome.common.KOMEReflection;
import lotr.common.LOTRLevelData;
import lotr.common.entity.npc.LOTREntityNPC;
import lotr.common.fac.LOTRFaction;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.world.World;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Server-only destination resolution, dispatch identity, and delivery validation. */
public final class KOMECourierService {
    private static final Logger LOGGER=LogManager.getLogger("KOMECourier");
    private static final String TAG="KOMECourier";
    static final double ARRIVAL_RADIUS=192D,SETTLEMENT_RADIUS=256D;
    private KOMECourierService(){}

    public static boolean hasBookSpace(EntityPlayerMP player){return player!=null&&player.inventory!=null&&emptyBookSlot(player.inventory.mainInventory)>=0;}
    static int emptyBookSlot(ItemStack[] inventory){
        if(inventory==null)return -1;
        for(int i=0;i<Math.min(9,inventory.length);i++)if(inventory[i]==null)return i;
        for(int i=9;i<inventory.length;i++)if(inventory[i]==null)return i;
        return -1;
    }
    /** Compatibility entry point, with physical server-side issuance only. */
    public static boolean issueMessage(EntityPlayerMP player,KOMESerfCourierAssignment assignment,KOMEProgressionNpcRef master){
        if(player==null||master==null||player.worldObj==null)return false;
        for(Object value:player.worldObj.loadedEntityList)if(value instanceof LOTREntityNPC&&master.hasSameIdentity(KOMEProgressionNpcRankService.referenceOf((LOTREntityNPC)value)))
            return KOMECourierIssuance.initial(player,KOMEWorldData.get(player.worldObj),(LOTREntityNPC)value);
        return false;
    }

    public static ItemStack message(KOMESerfCourierAssignment a,EntityPlayerMP p,KOMEProgressionNpcRef master){return message(a,KOMEReflection.getEntityUUID(p),master);}
    public static EntityItem dropMessageFromMaster(
            EntityPlayerMP player,
            LOTREntityNPC masterNpc,
            KOMESerfCourierAssignment assignment,
            KOMEProgressionNpcRef master) {
        if(player==null||masterNpc==null||assignment==null||master==null||masterNpc.worldObj==null||masterNpc.worldObj.isRemote)return null;

        return KOMEProgressionItemDrops.drop(masterNpc.worldObj,masterNpc.posX,masterNpc.posY+0.5D,masterNpc.posZ,message(assignment,player,master));
    }

    public static boolean canPickup(
            ItemStack stack,
            UUID picker) {
        if(!isCourierTagged(stack))return true;
        if(picker==null)return false;

        NBTTagCompound courier=
            stack.getTagCompound()
                .getCompoundTag(TAG);

        return picker.toString()
            .equals(courier.getString("Owner"));
    }

    public static boolean hasDispatch(
            EntityPlayerMP player,
            KOMESerfCourierAssignment assignment,
            KOMEProgressionNpcRef master) {
        if(player==null||assignment==null||master==null)return false;

        if(hasMessage(player,assignment,master))return true;

        UUID owner=KOMEReflection.getEntityUUID(player);

        for(Object value:player.worldObj.loadedEntityList) {
            if(!(value instanceof EntityItem))continue;

            EntityItem entity=(EntityItem)value;

            if(!entity.isDead
                    &&matching(
                        entity.getEntityItem(),
                        assignment,
                        owner,
                        master)) {
                return true;
            }
        }

        return false;
    }
    static ItemStack message(KOMESerfCourierAssignment a,UUID player,KOMEProgressionNpcRef master){
        ItemStack book=new ItemStack(Items.written_book);NBTTagCompound root=new NBTTagCompound(),hidden=new NBTTagCompound();
        hidden.setString("Assignment",a.token);hidden.setString("Owner",player.toString());hidden.setString("Master",master.entityUuid);hidden.setString("Destination",a.destinationKey);hidden.setInteger("Revision",a.documentRevision);root.setTag(TAG,hidden);
        String text=dispatchText(a,master);hidden.setString("Correspondence",text);
        root.setString("title",dispatchTitle(a));root.setString("author",master.displayName);NBTTagList pages=new NBTTagList();
        for(String page:KOMECourierCorrespondence.pages(text))pages.appendTag(new NBTTagString(page));
        root.setTag("pages",pages);book.setTagCompound(root);return book;
    }
    static String dispatchTitle(KOMESerfCourierAssignment a){
        String recipient=a.recipientName.length()>0?a.recipientName:a.recipient.isSet()?a.recipient.displayName:"My correspondent";
        String title="To "+recipient;return title.substring(0,Math.min(32,title.length()));
    }
    static String dispatchText(KOMESerfCourierAssignment a,KOMEProgressionNpcRef master){return a.letterText.length()>0?a.letterText:KOMECourierCorrespondence.compose(a,master);}

    public static boolean matching(ItemStack s,KOMESerfCourierAssignment a,EntityPlayerMP p,KOMEProgressionNpcRef m){return matching(s,a,KOMEReflection.getEntityUUID(p),m);}
    static boolean matching(ItemStack s,KOMESerfCourierAssignment a,UUID owner,KOMEProgressionNpcRef m){return identityMatch(s,a,owner,m)&&a.documentRevision==s.getTagCompound().getCompoundTag(TAG).getInteger("Revision")&&a.destinationKey.equals(s.getTagCompound().getCompoundTag(TAG).getString("Destination"));}

    static boolean isCourierTagged(ItemStack stack){
        return stack!=null
            &&stack.hasTagCompound()
            &&stack.getTagCompound().hasKey(TAG,10);
    }

    static boolean activeInventoryDispatch(
            ItemStack stack,
            KOMEPlayerProgression progression,
            UUID owner){
        if(!isCourierTagged(stack)||progression==null||owner==null)return false;

        KOMESerfKnightProgression state=
            progression.getSerfKnightProgression();

        if(!"courier".equals(state.getActiveAssignmentKind()))return false;

        KOMESerfCourierAssignment assignment=
            KOMESerfCourierAssignment.readFromNBT(
                state.getDuty(KOMESerfKnightDutyType.COURIER).getAssignmentData());

        return assignment!=null
            &&assignment.stage==KOMESerfCourierAssignment.Stage.OUTBOUND
            &&matching(stack,assignment,owner,state.getSerfdomMaster());
    }
    private static boolean identityMatch(ItemStack s,KOMESerfCourierAssignment a,UUID owner,KOMEProgressionNpcRef m){if(s==null||a==null||owner==null||m==null||s.getItem()!=Items.written_book||!s.hasTagCompound()||!s.getTagCompound().hasKey(TAG,10))return false;NBTTagCompound t=s.getTagCompound().getCompoundTag(TAG);return a.valid()&&a.token.equals(t.getString("Assignment"))&&owner.toString().equals(t.getString("Owner"))&&m.entityUuid.equals(t.getString("Master"));}
    public static boolean hasMessage(EntityPlayerMP p,KOMESerfCourierAssignment a,KOMEProgressionNpcRef m){for(ItemStack s:p.inventory.mainInventory)if(matching(s,a,p,m))return true;return false;}
    public static boolean removeMessage(EntityPlayerMP p,KOMESerfCourierAssignment a,KOMEProgressionNpcRef m){for(int i=0;i<p.inventory.mainInventory.length;i++)if(matching(p.inventory.mainInventory[i],a,p,m)){p.inventory.mainInventory[i]=null;return true;}return false;}
    private static boolean removeHeldMessage(EntityPlayerMP player,KOMESerfCourierAssignment a,KOMEProgressionNpcRef master){
        ItemStack held=player.getCurrentEquippedItem();if(!matching(held,a,player,master)||held.stackSize<=0)return false;
        if(--held.stackSize==0)player.inventory.mainInventory[player.inventory.currentItem]=null;
        player.inventory.markDirty();return true;
    }
    /** Called only for a nearby NPC interaction; the binding and letter remain server authority. */
    public static boolean deliverToRecipient(EntityPlayerMP player,KOMEWorldData world,LOTREntityNPC npc){
        if(player==null||world==null||npc==null)return false;
        synchronized(world){
        KOMEPlayerProgression progression=world.getProgression(player.getUniqueID());KOMESerfKnightProgression state=progression.getSerfKnightProgression();
        if(progression.getCanonicalRank()!=KOMEProgressionRank.SERF||!"courier".equals(state.getActiveAssignmentKind()))return false;
        KOMESerfCourierAssignment assignment=KOMESerfCourierAssignment.readFromNBT(state.getDuty(KOMESerfKnightDutyType.COURIER).getAssignmentData());
        if(assignment==null||assignment.confirmedRecipientDeath||!validRecipient(player,npc,assignment,state.getSerfdomMaster())||!removeHeldMessage(player,assignment,state.getSerfdomMaster()))return false;
        assignment.stage=KOMESerfCourierAssignment.Stage.DELIVERED;
        state.setDutyAssignmentData(KOMESerfKnightDutyType.COURIER,assignment.writeToNBT());world.markDirty();

        player.worldObj.playSoundAtEntity(
            npc,
            "mob.horse.leather",
            0.5F,
            1.0F);

        KOMEProgressionNpcRoles.syncPlayer(world,player.getUniqueID());npc.getEntityData().removeTag(KOMECourierRecipientSpawner.TOKEN);
        player.inventoryContainer.detectAndSendChanges();KOMEProgressionAutoCompleter.syncPlayer(player,progression);
        return true;
        }
    }
    /** The Master explicitly accepts a delivered dispatch on return. */
    public static boolean reportToMaster(EntityPlayerMP player,KOMEWorldData world,KOMEPlayerProgression progression){
        if(player==null||world==null||progression==null||progression.getCanonicalRank()!=KOMEProgressionRank.SERF)return false;
        synchronized(world){
        KOMESerfKnightProgression state=progression.getSerfKnightProgression();
        if(!"courier".equals(state.getActiveAssignmentKind()))return false;
        KOMESerfCourierAssignment assignment=KOMESerfCourierAssignment.readFromNBT(state.getDuty(KOMESerfKnightDutyType.COURIER).getAssignmentData());
        if(assignment==null)return false;
        if(assignment.confirmedRecipientDeath){
            if(!hasMessage(player,assignment,state.getSerfdomMaster()))return false;
            removeMessage(player,assignment,state.getSerfdomMaster());
        }else if(assignment.stage!=KOMESerfCourierAssignment.Stage.DELIVERED||!assignment.recipient.isSet())return false;
        if(!KOMESerfKnightService.completeDuty(progression,KOMESerfKnightDutyType.COURIER).success)return false;
        if(!assignment.confirmedRecipientDeath)KOMEProgressionServiceRewards.duty(player,state,KOMESerfKnightDutyType.COURIER);
        cleanup(player,assignment,state.getSerfdomMaster());
        KOMEProgressionNpcRoles.syncPlayer(world,player.getUniqueID());
        world.markDirty();player.inventoryContainer.detectAndSendChanges();KOMEProgressionAutoCompleter.syncPlayer(player,progression);return true;
        }
    }

    /** Rewrites the one assignment-owned physical book in place, removing accidental duplicates. */
    static void refreshDispatch(EntityPlayerMP p,KOMESerfCourierAssignment a,KOMEProgressionNpcRef master){
        UUID owner=KOMEReflection.getEntityUUID(p);int first=-1;
        for(int i=0;i<p.inventory.mainInventory.length;i++)if(matching(p.inventory.mainInventory[i],a,owner,master)){if(first<0)first=i;else p.inventory.mainInventory[i]=null;}
        ItemStack current=message(a,owner,master);if(first>=0)p.inventory.mainInventory[first]=current;else for(Object value:p.worldObj.loadedEntityList)if(value instanceof EntityItem){EntityItem entity=(EntityItem)value;if(!entity.isDead&&matching(entity.getEntityItem(),a,owner,master))entity.setEntityItemStack(current.copy());}p.inventory.markDirty();p.inventoryContainer.detectAndSendChanges();
    }

    public static boolean validRecipient(EntityPlayerMP p,LOTREntityNPC n,KOMESerfCourierAssignment a,KOMEProgressionNpcRef master){return eligible(n,a,master)&&a.stage==KOMESerfCourierAssignment.Stage.OUTBOUND&&a.recipient.isSet()&&a.recipient.hasSameIdentity(KOMEProgressionNpcRankService.referenceOf(n))&&KOMEProgressionFactionResolver.matches(a.masterFactionKey,LOTRLevelData.getData(p).getPledgeFaction())&&p.getDistanceSqToEntity(n)<=64D;}
    private static boolean eligible(LOTREntityNPC n,KOMESerfCourierAssignment a,KOMEProgressionNpcRef master){return n!=null&&n.isEntityAlive()&&!n.isChild()&&KOMEProgressionNpcRankService.isValidFactionNpc(n)&&n.hiredNPCInfo!=null&&!n.hiredNPCInfo.isActive&&n.bossInfo==null&&!n.isTraderEscort&&!master.hasSameIdentity(KOMEProgressionNpcRankService.referenceOf(n))&&KOMEProgressionFactionResolver.matches(a.destinationFactionKey,n.getFaction())&&a.atDestination(n.worldObj.provider.dimensionId,n.posX,n.posZ,SETTLEMENT_RADIUS);}

    static void cleanup(World world,KOMESerfCourierAssignment assignment){
        if(world==null||assignment==null)return;
        LOTREntityNPC recipient=KOMECourierRecipientSpawner.findOwned(world,assignment.token);
        if(recipient!=null)KOMECourierRecipientSpawner.retire(recipient,assignment.token);
    }

    static int cleanupWorldDispatches(
            World world,
            KOMESerfCourierAssignment assignment,
            UUID owner,
            KOMEProgressionNpcRef master) {
        if(world==null||assignment==null||owner==null||master==null)return 0;

        int removed=0;

        for(Object value:world.loadedEntityList) {
            if(!(value instanceof EntityItem))continue;

            EntityItem entity=(EntityItem)value;

            if(!entity.isDead
                    &&identityMatch(
                        entity.getEntityItem(),
                        assignment,
                        owner,
                        master)) {
                entity.setDead();
                removed++;
            }
        }

        return removed;
    }

    static void cleanup(
            World world,
            KOMESerfCourierAssignment assignment,
            UUID owner,
            KOMEProgressionNpcRef master) {
        cleanup(world,assignment);
        cleanupWorldDispatches(
            world,
            assignment,
            owner,
            master);
    }
    static int cleanupInventory(EntityPlayerMP player,KOMESerfCourierAssignment assignment,KOMEProgressionNpcRef master){
        if(player==null||assignment==null||master==null)return 0;
        int removed=0;UUID owner=KOMEReflection.getEntityUUID(player);
        for(int i=0;i<player.inventory.mainInventory.length;i++){
            if(identityMatch(player.inventory.mainInventory[i],assignment,owner,master)){
                player.inventory.mainInventory[i]=null;
                removed++;
            }
        }
        if(removed>0)player.inventoryContainer.detectAndSendChanges();
        return removed;
    }

    public static void cleanup(EntityPlayerMP player,KOMESerfCourierAssignment assignment,KOMEProgressionNpcRef master){
        if(player==null||assignment==null)return;
        cleanup(
            player.worldObj,
            assignment,
            KOMEReflection.getEntityUUID(player),
            master);
        cleanupInventory(player,assignment,master);
    }
    public static void tickPlayer(EntityPlayerMP p){
        // KOMEEvents already calls this on world-time multiples of 20. Player age has an
        // independent phase after login, so combining the two clocks can suppress every call.
        if(p==null||p.worldObj.isRemote||p.worldObj.getTotalWorldTime()%100L!=0L)return;
        KOMEWorldData world=KOMEWorldData.get(p.worldObj);
        KOMEPlayerProgression progression=world.getProgression(p.getUniqueID());
        KOMESerfKnightProgression state=progression.getSerfKnightProgression();
        if(!"courier".equals(state.getActiveAssignmentKind()))return;
        KOMESerfCourierAssignment a=KOMESerfCourierAssignment.readFromNBT(state.getDuty(KOMESerfKnightDutyType.COURIER).getAssignmentData());
        if(a==null||a.stage!=KOMESerfCourierAssignment.Stage.OUTBOUND||a.confirmedRecipientDeath)return;
        if(a.recipient.isSet()){
            LOTREntityNPC bound=loadedRecipient(p,a.recipient.entityUuid);
            if(bound==null)return; // Unload is not death.
            // A loaded but changed NPC is not proof of death either.
            return;
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
    public static boolean handleRecipientDeath(KOMEWorldData world,String npcId,net.minecraft.world.World serverWorld){
        if(world==null||npcId==null)return false;boolean changed=false;
        synchronized(world){for(java.util.Map.Entry<UUID,KOMEPlayerProgression> entry:world.progressions.entrySet()){
            KOMEPlayerProgression progression=entry.getValue();KOMESerfKnightProgression state=progression.getSerfKnightProgression();
            if(!"courier".equals(state.getActiveAssignmentKind()))continue;
            KOMESerfCourierAssignment a=KOMESerfCourierAssignment.readFromNBT(state.getDuty(KOMESerfKnightDutyType.COURIER).getAssignmentData());
            if(a!=null&&a.stage==KOMESerfCourierAssignment.Stage.OUTBOUND&&!a.confirmedRecipientDeath&&npcId.equals(a.recipient.entityUuid)){
                a.confirmedRecipientDeath=true;a.recipientDeaths=Math.min(100,a.recipientDeaths+1);
                state.setDutyAssignmentData(KOMESerfKnightDutyType.COURIER,a.writeToNBT());
                KOMEProgressionNpcRoles.syncPlayer(world,entry.getKey());
                if(serverWorld!=null){net.minecraft.entity.player.EntityPlayer player=serverWorld.func_152378_a(entry.getKey());if(player instanceof EntityPlayerMP)KOMEProgressionAutoCompleter.syncPlayer((EntityPlayerMP)player,progression);}
                changed=true;
            }
        }if(changed)world.markDirty();}return changed;
    }
    static long replacementDelay(int deaths){return Math.min(6000L,1200L*Math.max(1,deaths));}
}
