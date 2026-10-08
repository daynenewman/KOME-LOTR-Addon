package kome.common.data;

import java.util.UUID;
import lotr.common.entity.npc.LOTREntityNPC;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

/**
 * Server-owned recovery encounter using a normal vanilla item and EntityItem. The item tag is
 * deliberately private implementation data: it never changes the item's name or tooltip.
 */
public final class KOMESerfKnightRecoveryService {
    static final String DATA_SITE_CREATED="RecoverySiteCreated", DATA_DIMENSION="RecoveryDimension", DATA_X="RecoveryX", DATA_Y="RecoveryY", DATA_Z="RecoveryZ", DATA_OBJECT="RecoveryObject", DATA_RETRIEVED="RecoveryRetrieved";
    static final String ITEM_TAG="KOMERecovery", ITEM_TOKEN="Token", ITEM_OWNER="Owner";
    static final int MIN_DISTANCE=112, MAX_DISTANCE=176;
    private KOMESerfKnightRecoveryService() { }

    /** Activates exactly once. A persisted-but-unspawned site is never recreated ambiguously. */
    public static boolean activate(EntityPlayerMP player,KOMEPlayerProgression progression,LOTREntityNPC liege) {
        if(player==null||progression==null||liege==null||player.worldObj.isRemote)return false;
        KOMESerfKnightProgression state=progression.getSerfKnightProgression();
        KOMESerfKnightTrialAssignment assignment=state.getTrialAssignment();
        if(!isRecovery(assignment)||assignment.stage!=KOMESerfKnightTrialAssignment.Stage.ASSIGNED)return false;
        NBTTagCompound data=(NBTTagCompound)assignment.data.copy();
        if(data.getBoolean(DATA_SITE_CREATED)) return false;
        if(!data.hasKey("RecoveryItem",10)){
            ItemStack selected=KOMEProgressionLostItems.choose(assignment.factionKey,new java.util.Random(assignment.assignmentToken.hashCode()));
            if(selected==null)return false;data.setTag("RecoveryItem",selected.writeToNBT(new NBTTagCompound()));
            assignment=assignment.withStage(assignment.stage,data);
        }
        Site site=findSite(player.worldObj,liege,assignment.assignmentToken);
        if(site==null)return false;
        // Persist the chosen site before mutating the world; no restart path manufactures a second item.
        data.setBoolean(DATA_SITE_CREATED,true); data.setInteger(DATA_DIMENSION,player.worldObj.provider.dimensionId);
        data.setInteger(DATA_X,site.x); data.setInteger(DATA_Y,site.y); data.setInteger(DATA_Z,site.z);
        state.updateTrialAssignment(assignment.withStage(KOMESerfKnightTrialAssignment.Stage.ACTIVE,data));
        KOMEWorldData worldData=KOMEWorldData.get(player.worldObj); worldData.markDirty();
        EntityItem dropped=new EntityItem(player.worldObj,site.x+0.5D,site.y+0.2D,site.z+0.5D,assignedStack(assignment,player.getUniqueID()));
        dropped.delayBeforeCanPickup=0; dropped.lifespan=Integer.MAX_VALUE;
        if(!KOMEProgressionItemDrops.spawn(player.worldObj,dropped)) { KOMEProgressionEncounterCleanup.failTrial(worldData,player.worldObj,state); return false; }
        data.setString(DATA_OBJECT,dropped.getUniqueID().toString());
        state.updateTrialAssignment(state.getTrialAssignment().withStage(KOMESerfKnightTrialAssignment.Stage.ACTIVE,data)); worldData.markDirty();
        return true;
    }

    public static void onPickup(EntityPlayerMP player,ItemStack stack) {
        if(player==null||stack==null||player.worldObj.isRemote)return;
        KOMEWorldData world=KOMEWorldData.get(player.worldObj); KOMEPlayerProgression progression=world.getProgression(player.getUniqueID());
        KOMESerfKnightProgression state=progression.getSerfKnightProgression(); KOMESerfKnightTrialAssignment assignment=state.getTrialAssignment();
        if(isAssignedTo(stack,assignment,player.getUniqueID())&&!assignment.data.getBoolean(DATA_RETRIEVED)) {
            NBTTagCompound data=(NBTTagCompound)assignment.data.copy(); data.setBoolean(DATA_RETRIEVED,true);
            state.updateTrialAssignment(assignment.withStage(KOMESerfKnightTrialAssignment.Stage.ACTIVE,data)); world.markDirty();
        }
    }

    /** Also catches inventory transfers that do not fire the ordinary pickup event. */
    public static void tickPlayer(EntityPlayerMP player) {
        if(player==null||player.worldObj.isRemote)return;
        KOMEWorldData world=KOMEWorldData.get(player.worldObj); KOMEPlayerProgression progression=world.getProgression(player.getUniqueID());
        KOMESerfKnightTrialAssignment assignment=progression.getSerfKnightProgression().getTrialAssignment();
        if(!isRecovery(assignment)||assignment.stage!=KOMESerfKnightTrialAssignment.Stage.ACTIVE)return;
        int slot=findAssignedStack(player,assignment);
        if(slot>=0){ if(!assignment.data.getBoolean(DATA_RETRIEVED)) onPickup(player,player.inventory.mainInventory[slot]); return; }
        if(!assignment.data.getBoolean(DATA_RETRIEVED)) ensureRecoveryObject(player,progression,assignment);
    }

    /** Called only from the validated Liege Service interaction. */
    public static boolean deliver(EntityPlayerMP player,KOMEPlayerProgression progression,LOTREntityNPC liege) {
        if(player==null||progression==null||liege==null)return false;
        KOMESerfKnightProgression state=progression.getSerfKnightProgression(); KOMESerfKnightTrialAssignment assignment=state.getTrialAssignment();
        if(!isRecovery(assignment)||assignment.stage!=KOMESerfKnightTrialAssignment.Stage.ACTIVE||!assignment.data.getBoolean(DATA_RETRIEVED)||!state.getLiege().hasSameIdentity(KOMEProgressionNpcRankService.referenceOf(liege)))return false;
        int slot=findAssignedStack(player,assignment);
        if(slot<0){
            KOMEProgressionEncounterCleanup.failTrial(KOMEWorldData.get(player.worldObj),player.worldObj,state);
            KOMEProgressionNpcSpeech.say(player,liege,"The keepsake has been lost again. This trial is ended. Seek new service before you try again.");
            KOMEProgressionAutoCompleter.syncPlayer(player,progression);return true;
        }
        if(!isAssignedTo(player.getCurrentEquippedItem(),assignment,player.getUniqueID())){
            KOMEProgressionNpcSpeech.say(player,liege,"Place the recovered keepsake in your hand, and give it to me.");return true;
        }
        if(!consumeAssignedStack(player.inventory.mainInventory,assignment,player.getUniqueID()))return false;
        player.worldObj.playSoundAtEntity(player,"random.pop",0.4F,1F);
        if(KOMESerfKnightService.markTrialObjectiveComplete(state).success) {
            KOMEProgressionServiceRewards.trial(player,state);
            KOMEWorldData.get(player.worldObj).markDirty(); player.inventoryContainer.detectAndSendChanges();
            KOMEProgressionAutoCompleter.syncPlayer(player,progression); KOMEProgressionNpcSpeech.say(player,liege,"You have returned what was lost. You have shown the care I require of a knight."); return true;
        }
        return false;
    }


    /**
     * Recreates an uncollected recovery object only when the player has naturally returned near
     * the persisted site and that chunk is already loaded. This never force-loads terrain.
     */
    private static void ensureRecoveryObject(EntityPlayerMP player,KOMEPlayerProgression progression,KOMESerfKnightTrialAssignment assignment) {
        if(player.worldObj.getTotalWorldTime()%40L!=0L||!assignment.data.getBoolean(DATA_SITE_CREATED))return;
        if(assignment.data.getInteger(DATA_DIMENSION)!=player.worldObj.provider.dimensionId)return;
        int x=assignment.data.getInteger(DATA_X), y=assignment.data.getInteger(DATA_Y), z=assignment.data.getInteger(DATA_Z);
        double dx=player.posX-(x+0.5D), dy=player.posY-(y+0.5D), dz=player.posZ-(z+0.5D);
        if(dx*dx+dy*dy+dz*dz>4096D||!player.worldObj.getChunkProvider().chunkExists(x>>4,z>>4))return;
        for(Object value:player.worldObj.loadedEntityList)
            if(value instanceof EntityItem&&!((EntityItem)value).isDead&&isAssignedTo(((EntityItem)value).getEntityItem(),assignment,player.getUniqueID())){
                if(assignment.data.getInteger("RecoveryMissingTicks")>0){NBTTagCompound data=(NBTTagCompound)assignment.data.copy();data.removeTag("RecoveryMissingTicks");progression.getSerfKnightProgression().updateTrialAssignment(assignment.withStage(assignment.stage,data));KOMEWorldData.get(player.worldObj).markDirty();}
                return;
            }
        if(assignment.data.hasKey(DATA_OBJECT)){
            NBTTagCompound evidence=(NBTTagCompound)assignment.data.copy();int missing=evidence.getInteger("RecoveryMissingTicks")+40;
            evidence.setInteger("RecoveryMissingTicks",missing);progression.getSerfKnightProgression().updateTrialAssignment(assignment.withStage(assignment.stage,evidence));
            KOMEWorldData.get(player.worldObj).markDirty();
            if(missing>=1200){KOMEProgressionEncounterCleanup.failTrial(KOMEWorldData.get(player.worldObj),player.worldObj,progression.getSerfKnightProgression());KOMEProgressionAutoCompleter.syncPlayer(player,progression);}
            return;
        }
        int spawnY=y;
        if(spawnY<=1||spawnY>=player.worldObj.getActualHeight()-2||!player.worldObj.isAirBlock(x,spawnY,z))
            spawnY=player.worldObj.getTopSolidOrLiquidBlock(x,z);
        if(spawnY<=1||spawnY>=player.worldObj.getActualHeight()-2||!player.worldObj.isAirBlock(x,spawnY,z))return;
        EntityItem replacement=new EntityItem(player.worldObj,x+0.5D,spawnY+0.2D,z+0.5D,assignedStack(assignment,player.getUniqueID()));
        replacement.delayBeforeCanPickup=0; replacement.lifespan=Integer.MAX_VALUE;
        if(!KOMEProgressionItemDrops.spawn(player.worldObj,replacement))return;
        NBTTagCompound data=(NBTTagCompound)assignment.data.copy();
        data.setInteger(DATA_Y,spawnY); data.setString(DATA_OBJECT,replacement.getUniqueID().toString());
        progression.getSerfKnightProgression().updateTrialAssignment(assignment.withStage(KOMESerfKnightTrialAssignment.Stage.ACTIVE,data));
        KOMEWorldData.get(player.worldObj).markDirty();
    }


    /**
     * Returns a deliberately approximate map target for an active, unretrieved recovery object.
     * The marker is offset from the real persisted site so the map guides a search rather than
     * revealing the exact dropped-item position.
     */
    static KOMEVisualMarker searchMarker(KOMESerfKnightTrialAssignment assignment) {
        if(!isRecovery(assignment)||assignment.stage!=KOMESerfKnightTrialAssignment.Stage.ACTIVE
                ||!assignment.data.getBoolean(DATA_SITE_CREATED)||assignment.data.getBoolean(DATA_RETRIEVED))return null;
        int x=assignment.data.getInteger(DATA_X), z=assignment.data.getInteger(DATA_Z);
        int hash=assignment.assignmentToken==null?0:assignment.assignmentToken.hashCode();
        int radius=24+((hash>>>4)&15);
        double angle=((hash&0x7fffffff)%6283)/1000D;
        double markerX=x+Math.cos(angle)*radius, markerZ=z+Math.sin(angle)*radius;
        return new KOMEVisualMarker(KOMEVisualMarker.Role.RECOVERY_SEARCH, "",
            "Recover the lost item", "", assignment.data.getInteger(DATA_DIMENSION),
            markerX, 0.0D, markerZ);
    }

    static boolean isRecoveryTagged(ItemStack stack) {
        return stack!=null
            &&stack.hasTagCompound()
            &&stack.getTagCompound().hasKey(ITEM_TAG,10);
    }

    static boolean activeInventoryItem(
            ItemStack stack,
            KOMEPlayerProgression progression,
            UUID owner) {
        if(!isRecoveryTagged(stack)||progression==null||owner==null)return false;

        NBTTagCompound tag=
            stack.getTagCompound().getCompoundTag(ITEM_TAG);

        KOMESerfKnightProgression state=
            progression.getSerfKnightProgression();

        KOMESerfKnightTrialAssignment assignment=
            state.getTrialAssignment();

        return isRecovery(assignment)
            &&!state.isTrialCompleted()
            &&assignment.stage==KOMESerfKnightTrialAssignment.Stage.ACTIVE
            &&owner.toString().equals(tag.getString(ITEM_OWNER))
            &&assignment.assignmentToken.equals(tag.getString(ITEM_TOKEN));
    }

    static boolean canPickup(ItemStack stack,UUID picker) {
        if(stack==null||picker==null||!stack.hasTagCompound()||!stack.getTagCompound().hasKey(ITEM_TAG,10))return true;
        NBTTagCompound tag=stack.getTagCompound().getCompoundTag(ITEM_TAG);
        try{return picker.equals(UUID.fromString(tag.getString(ITEM_OWNER)));}
        catch(Exception ignored){return false;}
    }
    static ItemStack assignedStack(KOMESerfKnightTrialAssignment assignment,UUID owner) {
        ItemStack stack=assignedBase(assignment); NBTTagCompound root=new NBTTagCompound(), tag=new NBTTagCompound();
        tag.setString(ITEM_TOKEN,assignment.assignmentToken); tag.setString(ITEM_OWNER,owner.toString()); root.setTag(ITEM_TAG,tag); stack.setTagCompound(root); return stack;
    }
    static boolean isAssignedTo(ItemStack stack,KOMESerfKnightTrialAssignment assignment,UUID owner) {
        if(stack==null||assignment==null||owner==null||!stack.hasTagCompound()||!stack.getTagCompound().hasKey(ITEM_TAG,10))return false;
        ItemStack expected=assignedBase(assignment);if(stack.getItem()!=expected.getItem()||stack.getItemDamage()!=expected.getItemDamage())return false;
        NBTTagCompound tag=stack.getTagCompound().getCompoundTag(ITEM_TAG);
        return assignment.assignmentToken.equals(tag.getString(ITEM_TOKEN))&&owner.toString().equals(tag.getString(ITEM_OWNER));
    }
    private static ItemStack assignedBase(KOMESerfKnightTrialAssignment assignment){
        ItemStack saved=assignment.data.hasKey("RecoveryItem",10)?ItemStack.loadItemStackFromNBT(assignment.data.getCompoundTag("RecoveryItem")):null;
        return saved==null?new ItemStack(Items.gold_ingot):saved.copy();
    }
    static int findAssignedStack(EntityPlayerMP player,KOMESerfKnightTrialAssignment assignment) { for(int i=0;i<player.inventory.mainInventory.length;i++)if(isAssignedTo(player.inventory.mainInventory[i],assignment,player.getUniqueID()))return i; return -1; }
    static boolean consumeAssignedStack(ItemStack[] inventory,KOMESerfKnightTrialAssignment assignment,UUID owner) {
        if(inventory==null)return false; for(int i=0;i<inventory.length;i++)if(isAssignedTo(inventory[i],assignment,owner)){inventory[i].stackSize--;if(inventory[i].stackSize<=0)inventory[i]=null;return true;} return false;
    }
    static boolean isRecovery(KOMESerfKnightTrialAssignment assignment) { return assignment!=null&&"recovery".equals(assignment.trialId); }    /** Removes the exact assignment object from the loaded world and owning player's inventory. */
    public static void cleanup(EntityPlayerMP player,KOMESerfKnightTrialAssignment assignment){
        if(player==null||!isRecovery(assignment))return;
        cleanup(player.worldObj,player.getUniqueID(),assignment);
        if(cleanupInventory(player.inventory.mainInventory,assignment,player.getUniqueID())>0)
            player.inventoryContainer.detectAndSendChanges();
    }
    static void cleanup(World world,UUID owner,KOMESerfKnightTrialAssignment assignment){
        if(world==null||owner==null||!isRecovery(assignment)||!assignment.data.hasKey(DATA_OBJECT))return;
        String id=assignment.data.getString(DATA_OBJECT);
        for(Object value:world.loadedEntityList)
            if(value instanceof EntityItem
                    &&id.equals(((EntityItem)value).getUniqueID().toString())
                    &&isAssignedTo(((EntityItem)value).getEntityItem(),assignment,owner))
                ((EntityItem)value).setDead();
    }
    static int cleanupInventory(ItemStack[] inventory,KOMESerfKnightTrialAssignment assignment,UUID owner){
        if(inventory==null||owner==null||!isRecovery(assignment))return 0;
        int removed=0;
        for(int i=0;i<inventory.length;i++){
            if(isAssignedTo(inventory[i],assignment,owner)){
                inventory[i]=null;
                removed++;
            }
        }
        return removed;
    }
    public static boolean reconcileLoadedItem(KOMEWorldData world,EntityItem item){
        if(world==null||item==null)return false;

        ItemStack stack=item.getEntityItem();
        if(stack==null
                ||!stack.hasTagCompound()
                ||!stack.getTagCompound().hasKey(ITEM_TAG,10)){
            return false;
        }

        NBTTagCompound tag=stack.getTagCompound().getCompoundTag(ITEM_TAG);
        String ownerText=tag.getString(ITEM_OWNER);
        String token=tag.getString(ITEM_TOKEN);

        UUID owner;
        try{
            owner=UUID.fromString(ownerText);
        }catch(Exception ignored){
            item.setDead();
            return true;
        }

        KOMEPlayerProgression progression=world.progressions.get(owner);
        boolean active=false;

        if(progression!=null){
            KOMESerfKnightTrialAssignment assignment=
                progression.getSerfKnightProgression().getTrialAssignment();

            if(isRecovery(assignment)
                    &&assignment.stage==KOMESerfKnightTrialAssignment.Stage.ACTIVE
                    &&token.equals(assignment.assignmentToken)
                    &&isAssignedTo(stack,assignment,owner)){
                String expected=assignment.data.getString(DATA_OBJECT);
                active=assignment.data.getBoolean(DATA_RETRIEVED)||expected.length()==0||expected.equals(item.getUniqueID().toString());
            }
        }

        if(active){item.lifespan=Integer.MAX_VALUE;item.age=0;return false;}

        item.setDead();
        return true;
    }

    private static Site findSite(World world,LOTREntityNPC liege,String token) {
        if(world==null||world.provider.dimensionId!=liege.worldObj.provider.dimensionId||world.provider.dimensionId==-1)return null;
        int hash=token==null?0:token.hashCode();
        for(int i=0;i<24;i++) { double angle=((hash+i*97)&0x7fffffff)%6283/1000D; int distance=MIN_DISTANCE+((hash>>>Math.min(24,i*2))&63); distance=Math.min(MAX_DISTANCE,distance);
            int x=(int)Math.floor(liege.posX+Math.cos(angle)*distance), z=(int)Math.floor(liege.posZ+Math.sin(angle)*distance);
            if(!KOMEKnightCommissionService.loadedAround(world,x,z))continue;
            int y=world.getTopSolidOrLiquidBlock(x,z);
            if(y>1&&y<world.getActualHeight()-2&&world.getBlock(x,y-1,z).getMaterial().isSolid()&&world.isAirBlock(x,y,z)&&world.isAirBlock(x,y+1,z)&&world.getBlock(x,y-1,z)!=net.minecraft.init.Blocks.lava&&world.getBlock(x,y-1,z)!=net.minecraft.init.Blocks.flowing_lava)return new Site(x,y,z);
        } return null;
    }
    private static final class Site { final int x,y,z; Site(int x,int y,int z){this.x=x;this.y=y;this.z=z;} }
}
