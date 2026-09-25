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
        Site site=findSite(player.worldObj,liege,assignment.assignmentToken);
        if(site==null)return false;
        // Persist the chosen site before mutating the world; no restart path manufactures a second item.
        data.setBoolean(DATA_SITE_CREATED,true); data.setInteger(DATA_DIMENSION,player.worldObj.provider.dimensionId);
        data.setInteger(DATA_X,site.x); data.setInteger(DATA_Y,site.y); data.setInteger(DATA_Z,site.z);
        state.updateTrialAssignment(assignment.withStage(KOMESerfKnightTrialAssignment.Stage.ACTIVE,data));
        KOMEWorldData worldData=KOMEWorldData.get(player.worldObj); worldData.markDirty();
        EntityItem dropped=new EntityItem(player.worldObj,site.x+0.5D,site.y+0.2D,site.z+0.5D,assignedStack(assignment,player.getUniqueID()));
        dropped.delayBeforeCanPickup=0; dropped.lifespan=Integer.MAX_VALUE;
        if(!player.worldObj.spawnEntityInWorld(dropped)) { fail(state,worldData); return false; }
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
        if(!isRecovery(assignment)||assignment.stage!=KOMESerfKnightTrialAssignment.Stage.ACTIVE||assignment.data.getBoolean(DATA_RETRIEVED))return;
        int slot=findAssignedStack(player,assignment); if(slot>=0) onPickup(player,player.inventory.mainInventory[slot]);
    }

    /** Called only from the validated Liege Service interaction. */
    public static boolean deliver(EntityPlayerMP player,KOMEPlayerProgression progression,LOTREntityNPC liege) {
        if(player==null||progression==null||liege==null)return false;
        KOMESerfKnightProgression state=progression.getSerfKnightProgression(); KOMESerfKnightTrialAssignment assignment=state.getTrialAssignment();
        if(!isRecovery(assignment)||assignment.stage!=KOMESerfKnightTrialAssignment.Stage.ACTIVE||!assignment.data.getBoolean(DATA_RETRIEVED)||!state.getProspectiveLiege().hasSameIdentity(KOMEProgressionNpcRankService.referenceOf(liege)))return false;
        int slot=findAssignedStack(player,assignment); if(slot<0)return false;
        if(!consumeAssignedStack(player.inventory.mainInventory,assignment,player.getUniqueID()))return false;
        if(KOMESerfKnightService.markTrialObjectiveComplete(state).success) {
            KOMEWorldData.get(player.worldObj).markDirty(); player.inventoryContainer.detectAndSendChanges();
            KOMEProgressionAutoCompleter.syncPlayer(player,progression); KOMEProgressionNpcSpeech.say(player,liege,"You have returned what was lost. You have shown the care I require of a knight."); return true;
        }
        return false;
    }

    static ItemStack assignedStack(KOMESerfKnightTrialAssignment assignment,UUID owner) {
        ItemStack stack=new ItemStack(Items.gold_ingot); NBTTagCompound root=new NBTTagCompound(), tag=new NBTTagCompound();
        tag.setString(ITEM_TOKEN,assignment.assignmentToken); tag.setString(ITEM_OWNER,owner.toString()); root.setTag(ITEM_TAG,tag); stack.setTagCompound(root); return stack;
    }
    static boolean isAssignedTo(ItemStack stack,KOMESerfKnightTrialAssignment assignment,UUID owner) {
        if(stack==null||assignment==null||owner==null||stack.getItem()!=Items.gold_ingot||!stack.hasTagCompound()||!stack.getTagCompound().hasKey(ITEM_TAG,10))return false;
        NBTTagCompound tag=stack.getTagCompound().getCompoundTag(ITEM_TAG);
        return assignment.assignmentToken.equals(tag.getString(ITEM_TOKEN))&&owner.toString().equals(tag.getString(ITEM_OWNER));
    }
    static int findAssignedStack(EntityPlayerMP player,KOMESerfKnightTrialAssignment assignment) { for(int i=0;i<player.inventory.mainInventory.length;i++)if(isAssignedTo(player.inventory.mainInventory[i],assignment,player.getUniqueID()))return i; return -1; }
    static boolean consumeAssignedStack(ItemStack[] inventory,KOMESerfKnightTrialAssignment assignment,UUID owner) {
        if(inventory==null)return false; for(int i=0;i<inventory.length;i++)if(isAssignedTo(inventory[i],assignment,owner)){inventory[i].stackSize--;if(inventory[i].stackSize<=0)inventory[i]=null;return true;} return false;
    }
    static boolean isRecovery(KOMESerfKnightTrialAssignment assignment) { return assignment!=null&&"recovery".equals(assignment.trialId); }
    /** Removes only the still-uncollected, exact assigned object when its canonical trial is abandoned. */
    public static void cleanup(EntityPlayerMP player,KOMESerfKnightTrialAssignment assignment){if(player==null||!isRecovery(assignment)||!assignment.data.hasKey(DATA_OBJECT))return;String id=assignment.data.getString(DATA_OBJECT);for(Object value:player.worldObj.loadedEntityList)if(value instanceof EntityItem&&id.equals(((EntityItem)value).getUniqueID().toString())&&isAssignedTo(((EntityItem)value).getEntityItem(),assignment,player.getUniqueID()))((EntityItem)value).setDead();}
    private static void fail(KOMESerfKnightProgression state,KOMEWorldData world) { KOMESerfKnightTrialAssignment assignment=state.getTrialAssignment(); if(assignment!=null){state.updateTrialAssignment(assignment.withStage(KOMESerfKnightTrialAssignment.Stage.FAILED,null));world.markDirty();} }
    private static Site findSite(World world,LOTREntityNPC liege,String token) {
        if(world==null||world.provider.dimensionId!=liege.worldObj.provider.dimensionId||world.provider.dimensionId==-1)return null;
        int hash=token==null?0:token.hashCode();
        for(int i=0;i<24;i++) { double angle=((hash+i*97)&0x7fffffff)%6283/1000D; int distance=MIN_DISTANCE+((hash>>>Math.min(24,i*2))&63); distance=Math.min(MAX_DISTANCE,distance);
            int x=(int)Math.floor(liege.posX+Math.cos(angle)*distance), z=(int)Math.floor(liege.posZ+Math.sin(angle)*distance), y=world.getTopSolidOrLiquidBlock(x,z);
            if(y>1&&y<world.getActualHeight()-2&&world.getBlock(x,y-1,z).getMaterial().isSolid()&&world.isAirBlock(x,y,z)&&world.isAirBlock(x,y+1,z)&&world.getBlock(x,y-1,z)!=net.minecraft.init.Blocks.lava&&world.getBlock(x,y-1,z)!=net.minecraft.init.Blocks.flowing_lava)return new Site(x,y,z);
        } return null;
    }
    private static final class Site { final int x,y,z; Site(int x,int y,int z){this.x=x;this.y=y;this.z=z;} }
}
