package kome.common.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.enovak.lotrmoremobs.entity.animal.LOTREntityMumakil;
import kome.common.KOMEReflection;
import lotr.common.entity.animal.LOTREntityHorse;
import lotr.common.entity.npc.LOTREntityNPC;
import lotr.common.entity.npc.LOTREntityWarg;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.passive.EntityHorse;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S13PacketDestroyEntities;
import net.minecraft.network.play.server.S1BPacketEntityAttach;
import net.minecraft.network.play.server.S26PacketMapChunkBulk;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.ChunkWatchEvent;

/** Live-world adapter for Join Battle entry. It never calls LOTR/KOME fast-travel authority. */
final class KOMEJoinBattlePhysicalAccess implements KOMEJoinBattleEntryService.PhysicalAccess {
    static final KOMEJoinBattlePhysicalAccess INSTANCE = new KOMEJoinBattlePhysicalAccess();
    private static final int SEARCH_RADIUS = 24;
    private static final int EGRESS_SEARCH_RADIUS = 8;
    private static final int EGRESS_VERTICAL_RANGE = 4;
    /** Four chunks around the exact persisted source/destination is bounded recovery authority. */
    private static final double MOUNT_RECOVERY_RADIUS_SQ = 64D * 64D;
    private static final String[] TRANSIENT_MOUNT_NBT = {"id", "Pos", "Motion", "Rotation",
        "Dimension", "OnGround", "FallDistance", "Fire", "Air", "PortalCooldown",
        "HurtTime", "DeathTime", "AttackTime"};

    private KOMEJoinBattlePhysicalAccess() { }
    @Override public long now(){return System.currentTimeMillis();}

    @Override public KOMEJoinBattleEntryService.Preparation prepare(KOMEWorldData data,
            EntityPlayerMP player,KOMEArmyCompany company,String tileId,String conflictId,
            long conflictRevision){
        if(data==null||player==null||company==null)
            return KOMEJoinBattleEntryService.Preparation.denied(
                KOMEJoinBattleService.Reason.COMPANY_INCOHERENT);
        MountInspection mount=inspectMount(player);
        if(mount.reason!=KOMEJoinBattleService.Reason.ALLOWED)
            return KOMEJoinBattleEntryService.Preparation.denied(mount.reason);
        KOMEJoinBattleCompanyAnchorService.Resolution resolved=
            KOMEJoinBattleCompanyAnchorService.INSTANCE.resolve(data,player,company.id,tileId,
                conflictId,conflictRevision);
        if(resolved.reason!=KOMEJoinBattleService.Reason.ALLOWED||resolved.anchor==null)
            return KOMEJoinBattleEntryService.Preparation.denied(resolved.reason);
        try{
        LOTREntityNPC anchor=resolved.anchor;
        World destinationWorld=anchor.worldObj;
        if(mount.mounted&&destinationWorld.provider.dimensionId!=player.dimension)
            return KOMEJoinBattleEntryService.Preparation.denied(
                KOMEJoinBattleService.Reason.MOUNTED_CROSS_DIMENSION_UNSUPPORTED);

        double width=player.width,height=player.height;
        if(mount.mounted){
            width=Math.max(width,mount.entity.width);
            height=Math.max(mount.entity.height,
                mount.entity.getMountedYOffset()+player.getYOffset()+player.height);
        }
        KOMEStrategicDeploymentResolver.Validation safe=
            KOMEStrategicDeploymentResolver.resolveAround(destinationWorld,tileId,
                anchor.posX,anchor.posY,anchor.posZ,SEARCH_RADIUS,width,height);
        if(!safe.valid)return KOMEJoinBattleEntryService.Preparation.denied(
            KOMEJoinBattleService.Reason.SAFE_POSITION_UNAVAILABLE);
        KOMEJoinBattleDeploymentReceipt.Pose destination=new KOMEJoinBattleDeploymentReceipt.Pose(
            safe.anchor.dimensionId,safe.anchor.x,safe.anchor.y,safe.anchor.z,
            player.rotationYaw,player.rotationPitch);
        KOMEJoinBattleDeploymentReceipt.Pose source=pose(player);
        if(!mount.mounted){
            if(!validatePlayerPlacement(destinationWorld,destination,player))
                return KOMEJoinBattleEntryService.Preparation.denied(
                    KOMEJoinBattleService.Reason.SAFE_POSITION_UNAVAILABLE);
            return KOMEJoinBattleEntryService.Preparation.unmounted(source,destination);
        }
        if(!validateMountedPlacement(destinationWorld,destination,player,mount.entity))
            return KOMEJoinBattleEntryService.Preparation.denied(
                KOMEJoinBattleService.Reason.SAFE_POSITION_UNAVAILABLE);
        NBTTagCompound snapshot=new NBTTagCompound();
        try{
            if(!mount.entity.writeMountToNBT(snapshot))
                return KOMEJoinBattleEntryService.Preparation.denied(
                    KOMEJoinBattleService.Reason.MOUNT_TRANSFER_FAILED);
        }
        catch(Throwable failure){return KOMEJoinBattleEntryService.Preparation.denied(
            KOMEJoinBattleService.Reason.MOUNT_TRANSFER_FAILED);}
        if(snapshot.hasNoTags())return KOMEJoinBattleEntryService.Preparation.denied(
            KOMEJoinBattleService.Reason.MOUNT_TRANSFER_FAILED);
        return KOMEJoinBattleEntryService.Preparation.mounted(source,destination,
            KOMEReflection.getEntityUUID(mount.entity),mount.entityType,mount.profile,
            pose(mount.entity),snapshot);
        }finally{resolved.close();}
    }

    @Override public boolean isAtDestination(EntityPlayerMP player,
            KOMEJoinBattleDeploymentReceipt receipt){
        KOMEJoinBattleDeploymentReceipt.Pose destination=receipt.getDeploymentDestination();
        if(player==null||destination==null||player.dimension!=destination.dimensionId)return false;
        if(!receipt.isEnteredMounted())return distanceSquared(player,destination)<=0.25D
            &&KOMEReflection.getRidingEntity(player)==null;
        Entity mount=KOMEReflection.getRidingEntity(player);
        boolean coherent=mount!=null&&exactMount(mount,receipt)
            &&KOMEReflection.getRiddenByEntity(mount)==player
            &&mountedStateCoherent(player,mount,destination,receipt.getTileId());
        if(!coherent)return false;
        try{
            List<Entity> exact=findRecoveryMounts(player,receipt);
            return exact.size()==1&&exact.get(0)==mount;
        }catch(RuntimeException unavailable){return false;}
    }

    @Override public KOMEJoinBattleService.Reason preflight(KOMEWorldData data,
            EntityPlayerMP player,KOMEJoinBattleDeploymentReceipt receipt){
        if(data==null||player==null||receipt==null||receipt.getDeploymentDestination()==null)
            return KOMEJoinBattleService.Reason.INVALID_REQUEST;
        KOMEConflictRecord record=data.getConflictService().get(receipt.getTileId());
        if(record==null||!record.isActive()
                ||!receipt.getConflictId().equals(record.getConflictId()))
            return KOMEJoinBattleService.Reason.STALE_CONFLICT;
        KOMEJoinBattleCompanyAnchorService.Resolution resolved=
            KOMEJoinBattleCompanyAnchorService.INSTANCE.resolve(data,player,
                receipt.getSelectedCompanyId(),receipt.getTileId(),receipt.getConflictId(),
                record.getRevision());
        if(resolved.reason!=KOMEJoinBattleService.Reason.ALLOWED||resolved.anchor==null)
            return resolved.reason;
        try{
            KOMEJoinBattleDeploymentReceipt.Pose destination=receipt.getDeploymentDestination();
            LOTREntityNPC anchor=resolved.anchor;
            if(anchor.worldObj==null||anchor.worldObj.provider==null
                    ||anchor.worldObj.provider.dimensionId!=destination.dimensionId)
                return KOMEJoinBattleService.Reason.COMPANY_LOCATION_UNAVAILABLE;
            double dx=anchor.posX-destination.x,dz=anchor.posZ-destination.z;
            if(dx*dx+dz*dz>SEARCH_RADIUS*SEARCH_RADIUS)
                return KOMEJoinBattleService.Reason.SAFE_POSITION_UNAVAILABLE;
            World target=world(destination.dimensionId);
            if(target==null||!KOMEStrategicDeploymentResolver.ensureChunkAvailable(target,
                    MathHelper.floor_double(destination.x),MathHelper.floor_double(destination.z)))
                return KOMEJoinBattleService.Reason.COMPANY_LOCATION_UNAVAILABLE;
            if(!receipt.isEnteredMounted())return validatePlayerPlacement(target,destination,player)
                ?KOMEJoinBattleService.Reason.ALLOWED
                :KOMEJoinBattleService.Reason.SAFE_POSITION_UNAVAILABLE;
            if(player.dimension!=destination.dimensionId)
                return KOMEJoinBattleService.Reason.MOUNTED_CROSS_DIMENSION_UNSUPPORTED;
            Entity source=hasExpectedRidingMount(player,receipt)
                ?KOMEReflection.getRidingEntity(player):findLoaded(receipt.getMountUuid());
            // A missing source is an existing recovery obligation. deployMounted will search the
            // exact recorded chunks and restore it; no destination mutation occurs in preflight.
            if(source==null)return receipt.getTemporaryMountNbt()==null
                ?KOMEJoinBattleService.Reason.MOUNT_TRANSFER_FAILED
                :KOMEJoinBattleService.Reason.ALLOWED;
            if(!exactMount(source,receipt))
                return KOMEJoinBattleService.Reason.MOUNT_LOCATION_AMBIGUOUS;
            Entity rider=KOMEReflection.getRiddenByEntity(source);
            if(rider!=null&&rider!=player)
                return KOMEJoinBattleService.Reason.INVALID_MOUNT_RELATIONSHIP;
            if(distanceSquared(source,receipt.getMountSourceAnchor())>0.25D
                    &&distanceSquared(source,destination)>0.25D)
                return KOMEJoinBattleService.Reason.MOUNT_LOCATION_AMBIGUOUS;
            return validateMountedPlacement(target,destination,player,source)
                ?KOMEJoinBattleService.Reason.ALLOWED
                :KOMEJoinBattleService.Reason.SAFE_POSITION_UNAVAILABLE;
        }finally{resolved.close();}
    }

    @Override public KOMEJoinBattleEntryService.PhysicalResult deploy(EntityPlayerMP player,
            KOMEJoinBattleDeploymentReceipt receipt){
        try{
            if(receipt.isEnteredMounted())return deployMounted(player,receipt);
            if(KOMEReflection.getRidingEntity(player)!=null)
                return KOMEJoinBattleEntryService.PhysicalResult.pending(
                    KOMEJoinBattleService.Reason.INVALID_MOUNT_RELATIONSHIP);
            movePlayer(player,receipt.getDeploymentDestination());
            return verifyPlayer(player,receipt)
                ?KOMEJoinBattleEntryService.PhysicalResult.deployed()
                :KOMEJoinBattleEntryService.PhysicalResult.pending(
                    KOMEJoinBattleService.Reason.SAFE_POSITION_UNAVAILABLE);
        }catch(Throwable failure){
            return receipt.isEnteredMounted()?rollbackMountedEntry(player,receipt):
                KOMEJoinBattleEntryService.PhysicalResult.pending(
                    KOMEJoinBattleService.Reason.SAFE_POSITION_UNAVAILABLE);
        }
    }

    @Override public KOMEJoinBattleEntryService.PhysicalResult rollback(EntityPlayerMP player,
            KOMEJoinBattleDeploymentReceipt receipt){
        if(receipt!=null&&receipt.isEnteredMounted())return rollbackMountedEntry(player,receipt);
        if(player==null||receipt==null||receipt.getReturnAnchor()==null)
            return KOMEJoinBattleEntryService.PhysicalResult.pending(
                KOMEJoinBattleService.Reason.SAFE_POSITION_UNAVAILABLE);
        try{
            KOMEJoinBattleDeploymentReceipt.Pose source=receipt.getReturnAnchor();
            World sourceWorld=world(source.dimensionId);
            if(sourceWorld==null)return KOMEJoinBattleEntryService.PhysicalResult.pending(
                KOMEJoinBattleService.Reason.SAFE_POSITION_UNAVAILABLE);
            ReturnResolution resolved=resolveReturnPlacement(sourceWorld,source,player,null);
            if(!resolved.valid)return KOMEJoinBattleEntryService.PhysicalResult.pending(
                KOMEJoinBattleService.Reason.SAFE_POSITION_UNAVAILABLE);
            if(KOMEReflection.getRidingEntity(player)!=null)player.mountEntity(null);
            movePlayer(player,resolved.pose);
            return verifyReturn(player,resolved.pose)
                ?KOMEJoinBattleEntryService.PhysicalResult.rolledBack(
                    KOMEJoinBattleDeploymentReceipt.MountDisposition.SEPARATED)
                :KOMEJoinBattleEntryService.PhysicalResult.pending(
                    KOMEJoinBattleService.Reason.SAFE_POSITION_UNAVAILABLE);
        }catch(Throwable failure){return KOMEJoinBattleEntryService.PhysicalResult.pending(
            KOMEJoinBattleService.Reason.SAFE_POSITION_UNAVAILABLE);}
    }

    static KOMEJoinBattleEgressService.Preparation prepareEgress(EntityPlayerMP player,
            KOMEJoinBattleDeploymentReceipt receipt){
        if(player==null||receipt==null||receipt.getReturnAnchor()==null)
            return KOMEJoinBattleEgressService.Preparation.unavailable();
        KOMEJoinBattleDeploymentReceipt.Pose target=receipt.getReturnAnchor();
        World targetWorld=world(target.dimensionId);
        if(targetWorld==null)return KOMEJoinBattleEgressService.Preparation.unavailable(
            "Saved return dimension " + target.dimensionId + " is unavailable.");

        if(receipt.isEnteredMounted() && receipt.getMountTransferPhase()==
                KOMEJoinBattleDeploymentReceipt.MountTransferPhase.EGRESS_TRANSFER_PENDING){
            String unresolved=recoverArmedEgress(player,receipt);
            if(unresolved!=null)return KOMEJoinBattleEgressService.Preparation.unavailable(unresolved);
            return KOMEJoinBattleEgressService.Preparation.restoredSource();
        }

        if(receipt.isEnteredMounted()){
            Entity riding=KOMEReflection.getRidingEntity(player);
            boolean exact=riding instanceof EntityLivingBase&&!riding.isDead&&riding.isEntityAlive()
                &&receipt.getMountUuid().equals(KOMEReflection.getEntityUUID(riding))
                &&KOMEReflection.getRiddenByEntity(riding)==player
                &&profileOf(riding)==receipt.getMountProfile()
                &&receipt.getMountEntityType().equals(EntityList.getEntityString(riding));
            if(exact){
                // Captured directly from the original mount, independently of the rider's
                // elevated returnAnchor. Both exact and nearby candidates are mount-base poses.
                KOMEJoinBattleDeploymentReceipt.Pose mountTarget=receipt.getMountSourceAnchor();
                if(mountTarget==null||mountTarget.dimensionId!=target.dimensionId)
                    return KOMEJoinBattleEgressService.Preparation.unavailable(
                        "Recorded mounted return authority is inconsistent.");
                ReturnResolution resolved=resolveReturnPlacement(targetWorld,mountTarget,
                    player,riding);
                if(!resolved.valid)return KOMEJoinBattleEgressService.Preparation.unavailable(
                    resolved.reason);
                NBTTagCompound snapshot=new NBTTagCompound();
                try{
                    if(!riding.writeMountToNBT(snapshot))
                        return KOMEJoinBattleEgressService.Preparation.unavailable(
                            "Mounted egress snapshot could not be captured safely.");
                }
                catch(Throwable failure){return KOMEJoinBattleEgressService.Preparation.unavailable(
                    "Mounted egress snapshot could not be captured safely.");}
                KOMEJoinBattleDeploymentReceipt.Pose playerTarget=shift(target,
                    resolved.pose.x-mountTarget.x,resolved.pose.y-mountTarget.y,
                    resolved.pose.z-mountTarget.z);
                return snapshot.hasNoTags()?KOMEJoinBattleEgressService.Preparation.unavailable(
                    "Mounted egress snapshot was empty.")
                    :KOMEJoinBattleEgressService.Preparation.mounted(snapshot,
                        playerTarget,resolved.pose);
            }
            ReturnResolution resolved=resolveReturnPlacement(targetWorld,target,player,null);
            if(!resolved.valid)return KOMEJoinBattleEgressService.Preparation.unavailable(
                resolved.reason);
            Entity recorded=findLoaded(receipt.getMountUuid());
            KOMEJoinBattleDeploymentReceipt.MountDisposition disposition=recorded==null
                ?KOMEJoinBattleDeploymentReceipt.MountDisposition.UNAVAILABLE
                :recorded.isDead||!recorded.isEntityAlive()
                    ?KOMEJoinBattleDeploymentReceipt.MountDisposition.DEAD
                    :KOMEJoinBattleDeploymentReceipt.MountDisposition.SEPARATED;
            return KOMEJoinBattleEgressService.Preparation.playerOnly(disposition,resolved.pose);
        }
        ReturnResolution resolved=resolveReturnPlacement(targetWorld,target,player,null);
        return resolved.valid
            ?KOMEJoinBattleEgressService.Preparation.playerOnly(null,resolved.pose)
            :KOMEJoinBattleEgressService.Preparation.unavailable(resolved.reason);
    }

    static boolean isRidingRecordedMount(EntityPlayerMP player,KOMEJoinBattleDeploymentReceipt receipt){
        Entity mount=player==null?null:KOMEReflection.getRidingEntity(player);
        return receipt!=null&&receipt.isEnteredMounted()&&exactMount(mount,receipt)
            &&KOMEReflection.getRiddenByEntity(mount)==player;
    }

    /** An armed egress owns the exact mount even while the rider is temporarily detached. */
    static String recoverArmedEgress(EntityPlayerMP player,KOMEJoinBattleDeploymentReceipt receipt){
        NBTTagCompound snapshot=receipt.getTemporaryMountNbt();
        if(snapshot==null)return "Mounted return recovery snapshot is unavailable.";
        try{
            net.minecraft.nbt.NBTTagList pos=snapshot.getTagList("Pos",6);
            if(pos.tagCount()!=3)return "Mounted return recovery source position is invalid.";
            KOMEJoinBattleDeploymentReceipt.Pose sourcePose=new KOMEJoinBattleDeploymentReceipt.Pose(
                receipt.getReturnAnchor().dimensionId,pos.func_150309_d(0),pos.func_150309_d(1),
                pos.func_150309_d(2),player.rotationYaw,player.rotationPitch);
            World sourceWorld=world(sourcePose.dimensionId);
            if(sourceWorld==null)return "Mounted return recovery dimension is unavailable.";
            if(!KOMEStrategicDeploymentResolver.ensureChunkAvailable(sourceWorld,
                    MathHelper.floor_double(sourcePose.x),MathHelper.floor_double(sourcePose.z)))
                return "Mounted return recovery source chunk is unavailable.";
            KOMEJoinBattleDeploymentReceipt.Pose target=receipt.getMountSourceAnchor();
            // A previous bounded nearby return may have crossed the anchor's chunk boundary.
            for(int cx=MathHelper.floor_double(target.x-EGRESS_SEARCH_RADIUS)>>4;
                    cx<=(MathHelper.floor_double(target.x+EGRESS_SEARCH_RADIUS)>>4);cx++)
                for(int cz=MathHelper.floor_double(target.z-EGRESS_SEARCH_RADIUS)>>4;
                        cz<=(MathHelper.floor_double(target.z+EGRESS_SEARCH_RADIUS)>>4);cz++)
                    if(!KOMEStrategicDeploymentResolver.ensureChunkAvailable(sourceWorld,cx<<4,cz<<4))
                        return "Mounted return recovery target chunk is unavailable.";
            List<Entity> copies=includeNestedMount(player,receipt,findLoadedMounts(receipt.getMountUuid()));
            if(copies.size()>1)return "Mounted return recovery found ambiguous exact-UUID copies.";
            Entity mount=copies.isEmpty()?null:copies.get(0);
            if(mount!=null&&(!exactMount(mount,receipt)
                    ||!withinRecoveryArea(mount,sourcePose)&&!withinRecoveryArea(mount,target)))
                return "Mounted return recovery identity or location is ambiguous.";
            Entity riding=KOMEReflection.getRidingEntity(player);
            if(riding!=null&&riding!=mount)return "Mounted return recovery cannot detach an unrelated mount.";
            if(mount!=null&&KOMEReflection.getRiddenByEntity(mount)!=null
                    &&KOMEReflection.getRiddenByEntity(mount)!=player)
                return "Mounted return recovery cannot seize another rider's mount.";
            if(mount==null){
                // The durable armed phase, not a lookup miss alone, authorizes reconstruction.
                mount=recreate(snapshot,sourceWorld,sourcePose,receipt.getMountUuid(),
                    receipt.getMountEntityType(),receipt.getMountProfile());
                if(mount==null)return "Exact mounted return recovery could not restore the consumed mount.";
            }
            if(!validateReturnPlacement(sourceWorld,pose(mount),player,mount))
                return "Mounted return recovery pose is not currently safe.";
            if(!synchronizeMountedPlayerForEgress(player,mount))
                return "Mounted return recovery could not restore the rider relationship.";
            List<Entity> verified=includeNestedMount(player,receipt,findLoadedMounts(receipt.getMountUuid()));
            return verified.size()==1&&verified.get(0)==mount?null:
                "Mounted return recovery UUID uniqueness is unresolved.";
        }catch(RuntimeException failure){return "Mounted return recovery could not verify exact source authority.";}
    }

    static KOMEJoinBattleEgressService.PhysicalResult egress(EntityPlayerMP player,
            KOMEJoinBattleDeploymentReceipt receipt,
            KOMEJoinBattleEgressService.Preparation preparation){
        if(player==null||receipt==null||preparation==null||!preparation.ready)
            return KOMEJoinBattleEgressService.PhysicalResult.failed();
        try{
            if(preparation.transferRecordedMount)return egressMounted(player,receipt,preparation);
            if(KOMEReflection.getRidingEntity(player)!=null)player.mountEntity(null);
            KOMEJoinBattleDeploymentReceipt.Pose target=preparation.playerTarget==null
                ?receipt.getReturnAnchor():preparation.playerTarget;
            movePlayer(player,target);
            return verifyReturn(player,target)
                ?KOMEJoinBattleEgressService.PhysicalResult.completed(
                    preparation.playerOnlyDisposition)
                :KOMEJoinBattleEgressService.PhysicalResult.failed(
                    "Return teleport could not be verified at the resolved safe position.");
        }catch(Throwable failure){return KOMEJoinBattleEgressService.PhysicalResult.failed(
            "Return teleport failed: "+failure.getClass().getSimpleName()+".");}
    }

    private static KOMEJoinBattleEgressService.PhysicalResult egressMounted(
            EntityPlayerMP player,KOMEJoinBattleDeploymentReceipt receipt,
            KOMEJoinBattleEgressService.Preparation preparation){
        Entity source=KOMEReflection.getRidingEntity(player);
        if(!(source instanceof EntityLivingBase)||source.isDead||!source.isEntityAlive()
                ||!receipt.getMountUuid().equals(KOMEReflection.getEntityUUID(source))
                ||KOMEReflection.getRiddenByEntity(source)!=player)
            return KOMEJoinBattleEgressService.PhysicalResult.failed();
        NBTTagCompound snapshot=receipt.getTemporaryMountNbt();
        if(snapshot==null||receipt.getMountTransferPhase()!=
                KOMEJoinBattleDeploymentReceipt.MountTransferPhase.EGRESS_TRANSFER_PENDING)
            return KOMEJoinBattleEgressService.PhysicalResult.failed();
        KOMEJoinBattleDeploymentReceipt.Pose playerTarget=preparation.playerTarget==null
            ?receipt.getReturnAnchor():preparation.playerTarget;
        KOMEJoinBattleDeploymentReceipt.Pose mountTarget=preparation.mountTarget==null
            ?receipt.getMountSourceAnchor():preparation.mountTarget;
        if(playerTarget==null||mountTarget==null
                ||playerTarget.dimensionId!=mountTarget.dimensionId)
            return KOMEJoinBattleEgressService.PhysicalResult.failed(
                "Mounted return authority is incomplete.");
        World targetWorld=world(mountTarget.dimensionId),sourceWorld=source.worldObj;
        KOMEJoinBattleDeploymentReceipt.Pose sourcePose=pose(source);
        if(targetWorld==null||!validateReturnPlacement(targetWorld,mountTarget,player,source))
            return KOMEJoinBattleEgressService.PhysicalResult.failed(
                "Resolved mounted return position is no longer safe.");
        if(player.dimension==playerTarget.dimensionId&&distanceSquared(source,mountTarget)<=0.25D){
            if(synchronizeMountedPlayerForEgress(player,source)
                    &&verifyMountedEgress(player,source,receipt,mountTarget)==null)
                return KOMEJoinBattleEgressService.PhysicalResult.completed(
                    KOMEJoinBattleDeploymentReceipt.MountDisposition.RETURNED_WITH_PLAYER);
            return KOMEJoinBattleEgressService.PhysicalResult.failed(
                "Mounted return rider synchronization could not be verified.");
        }

        if(!detachMountedPlayer(player,source))return KOMEJoinBattleEgressService.PhysicalResult.failed(
            "Mounted egress could not detach the battle-side rider relationship safely.");
        if(!retireMountForTransfer(player,source)){
            synchronizeMountedPlayer(player,source);
            return KOMEJoinBattleEgressService.PhysicalResult.failed(
                "Mounted egress could not retire the battle-side mount tracking safely.");
        }
        // Establish the return subscription and deliver terrain BEFORE spawnEntityInWorld can
        // publish the replacement through EntityTracker. Tracker membership alone cannot prove
        // that its spawn followed the client's chunk load/unload stream.
        try{movePlayer(player,playerTarget);}
        catch(RuntimeException unavailable){
            boolean restored=restoreEgressSource(player,receipt,snapshot,sourceWorld,sourcePose);
            return KOMEJoinBattleEgressService.PhysicalResult.failedAfterRollback(
                "Mounted return terrain publication failed; "+egressRollbackDescription(restored),restored);
        }
        Entity replacement=recreate(snapshot,targetWorld,mountTarget,receipt.getMountUuid(),
            receipt.getMountEntityType(),receipt.getMountProfile());
        if(replacement==null){
            boolean restored=restoreEgressSource(player,receipt,snapshot,sourceWorld,sourcePose);
            return KOMEJoinBattleEgressService.PhysicalResult.failedAfterRollback(
                "Mounted egress recreation failed; "+egressRollbackDescription(restored),restored);
        }
        try{
            RiderSyncResult sync=synchronizeMountedPlayerDetailed(player,replacement,
                MountPublicationMode.ORDERED_EGRESS);
            String failure=sync.success()?verifyMountedEgress(player,replacement,receipt,mountTarget)
                :"Mounted return synchronization stopped at "+sync.stage.name()+".";
            if(failure!=null){
                KOMEJoinBattleEntryRecoveryService.LOGGER.warn("Mounted egress {}: {}",
                    receipt.getReceiptId(),failure);
                detachMountedPlayer(player,replacement);
                boolean retired=retireMountForTransfer(player,replacement);
                boolean restored=retired&&restoreEgressSource(player,receipt,snapshot,sourceWorld,sourcePose);
                return KOMEJoinBattleEgressService.PhysicalResult.failedAfterRollback(
                    failure+" "+egressRollbackDescription(restored),restored);
            }
            return KOMEJoinBattleEgressService.PhysicalResult.completed(
                KOMEJoinBattleDeploymentReceipt.MountDisposition.RETURNED_WITH_PLAYER);
        }catch(Throwable failure){
            try{detachMountedPlayer(player,replacement);
                retireMountForTransfer(player,replacement);}catch(Throwable ignored){}
            boolean restored=restoreEgressSource(player,receipt,snapshot,sourceWorld,sourcePose);
            return KOMEJoinBattleEgressService.PhysicalResult.failedAfterRollback(
                "Mounted egress failed; "+egressRollbackDescription(restored),restored);
        }
    }

    private static String egressRollbackDescription(boolean verified){
        return verified?"The battle-side player and mount were restored safely.":
            "Exact mount recovery remains unresolved; the return receipt is preserved.";
    }

    private static String verifyMountedEgress(EntityPlayerMP player,Entity mount,
            KOMEJoinBattleDeploymentReceipt receipt,KOMEJoinBattleDeploymentReceipt.Pose target){
        if(!exactMount(mount,receipt))return "Returned mount identity/type/profile did not match.";
        List<Entity> copies=findLoadedMounts(receipt.getMountUuid());
        if(copies.size()!=1||copies.get(0)!=mount)return "Returned mount UUID uniqueness could not be verified.";
        if(player.dimension!=target.dimensionId||mount.dimension!=target.dimensionId)
            return "Returned player/mount dimension did not match.";
        if(distanceSquared(mount,target)>0.25D)return "Returned mount position did not match.";
        if(KOMEReflection.getRidingEntity(player)!=mount||KOMEReflection.getRiddenByEntity(mount)!=player)
            return "Returned rider relationship did not match.";
        if(!riderPositionCoherent(player,mount))return "Returned rider position did not match the mount.";
        if(!validateReturnPlacement(mount.worldObj,target,player,mount))
            return "Returned mount/rider clearance or support is unsafe.";
        return null;
    }

    static final class ReturnResolution{
        final boolean valid;final KOMEJoinBattleDeploymentReceipt.Pose pose;final String reason;
        private ReturnResolution(boolean valid,KOMEJoinBattleDeploymentReceipt.Pose pose,String reason){
            this.valid=valid;this.pose=pose;this.reason=reason==null?"":reason;
        }
        static ReturnResolution ok(KOMEJoinBattleDeploymentReceipt.Pose pose){
            return new ReturnResolution(true,pose,"");
        }
        static ReturnResolution failed(String reason){return new ReturnResolution(false,null,reason);}
    }

    /** Exact durable anchor first, then a deterministic bounded safe search around that anchor. */
    static ReturnResolution resolveReturnPlacement(World world,
            KOMEJoinBattleDeploymentReceipt.Pose anchor,EntityPlayerMP player,Entity mount){
        if(world==null||anchor==null||player==null||world.provider==null
                ||world.provider.dimensionId!=anchor.dimensionId)
            return ReturnResolution.failed("Saved return dimension is unavailable.");
        int x=MathHelper.floor_double(anchor.x),z=MathHelper.floor_double(anchor.z);
        if(!KOMEStrategicDeploymentResolver.ensureChunkAvailable(world,x,z))
            return ReturnResolution.failed("Saved return chunk could not be loaded.");
        if(validateReturnPlacement(world,anchor,player,mount))return ReturnResolution.ok(anchor);
        for(int radius=1;radius<=EGRESS_SEARCH_RADIUS;radius++){
            for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++){
                if(Math.max(Math.abs(dx),Math.abs(dz))!=radius)continue;
                for(int vertical=0;vertical<=EGRESS_VERTICAL_RANGE;vertical++){
                    int[] offsets=vertical==0?new int[]{0}:new int[]{vertical,-vertical};
                    for(int dy:offsets){
                        KOMEJoinBattleDeploymentReceipt.Pose candidate=shift(anchor,dx,dy,dz);
                        if(validateReturnPlacement(world,candidate,player,mount))
                            return ReturnResolution.ok(candidate);
                    }
                }
            }
        }
        return ReturnResolution.failed(mount!=null
            ?"Your mounted return location is obstructed or unsupported and no nearby safe position could be found."
            :"Saved return position is obstructed or unsupported, and no safe position was found within "
                +EGRESS_SEARCH_RADIUS+" blocks.");
    }

    private static KOMEJoinBattleDeploymentReceipt.Pose shift(
            KOMEJoinBattleDeploymentReceipt.Pose pose,double dx,double dy,double dz){
        return new KOMEJoinBattleDeploymentReceipt.Pose(pose.dimensionId,pose.x+dx,
            pose.y+dy,pose.z+dz,pose.yaw,pose.pitch);
    }

    private static boolean restoreEgressSource(EntityPlayerMP player,
            KOMEJoinBattleDeploymentReceipt receipt,NBTTagCompound snapshot,
            World sourceWorld,KOMEJoinBattleDeploymentReceipt.Pose sourcePose){
        Entity restored=recreate(snapshot,sourceWorld,sourcePose,receipt.getMountUuid(),
            receipt.getMountEntityType(),receipt.getMountProfile());
        if(restored==null)return false;
        try{return synchronizeMountedPlayer(player,restored)
            &&mountedStateCoherent(player,restored,sourcePose,null);}
        catch(Throwable failure){return false;}
    }

    static boolean validateReturnPlacement(World world,
            KOMEJoinBattleDeploymentReceipt.Pose pose,EntityPlayerMP player,Entity mount){
        if(world==null||pose==null||player==null||world.provider==null
                ||world.provider.dimensionId!=pose.dimensionId)return false;
        int blockX=MathHelper.floor_double(pose.x),blockY=MathHelper.floor_double(pose.y),
            blockZ=MathHelper.floor_double(pose.z);
        double width=mount==null?player.width:Math.max(player.width,mount.width);
        double height=mount==null?player.height:Math.max(mount.height,
            mount.getMountedYOffset()+player.getYOffset()+player.height);
        if(blockY<1||pose.y+height>world.getActualHeight()
                ||!KOMEStrategicDeploymentResolver.ensureChunkAvailable(world,blockX,blockZ))
            return false;
        if(mount==null&&!validatePlayerPlacement(world,pose,player))return false;
        if(mount!=null&&!validateMountedPlacement(world,pose,player,mount))return false;
        double half=width/2D;
        AxisAlignedBB support=AxisAlignedBB.getBoundingBox(pose.x-half,pose.y-0.2D,
            pose.z-half,pose.x+half,pose.y+0.01D,pose.z+half);
        // Support must be terrain, never an entity. In 1.7.10 the entity-inclusive query
        // dereferences its query entity for every nearby entity: passing null appears to
        // work in distant preflight, then throws once the returned mount/rider is present.
        // Body checks above still reject blocks, liquids and unrelated entity overlap.
        try{return !world.func_147461_a(support).isEmpty()
            &&!world.isAnyLiquid(support);}
        catch(Throwable unavailable){return false;}
    }

    private static boolean verifyReturn(EntityPlayerMP player,
            KOMEJoinBattleDeploymentReceipt.Pose target){
        return player!=null&&target!=null&&player.dimension==target.dimensionId
            &&distanceSquared(player,target)<=0.25D;
    }

    private KOMEJoinBattleEntryService.PhysicalResult deployMounted(EntityPlayerMP player,
            KOMEJoinBattleDeploymentReceipt receipt){
        if(KOMEJoinBattleEntryRecoveryService.INSTANCE.awaitingLoginPublication(player))
            return KOMEJoinBattleEntryService.PhysicalResult.pending(
                KOMEJoinBattleService.Reason.ENTRY_ALREADY_IN_PROGRESS);
        KOMEJoinBattleDeploymentReceipt.Pose destination=receipt.getDeploymentDestination();
        if(player.dimension!=destination.dimensionId)
            return rollbackMountedEntry(player,receipt,
                KOMEJoinBattleService.Reason.PLAYER_TELEPORT_FAILED);
        List<Entity> candidates=findRecoveryMounts(player,receipt);
        RollbackMountResolution resolved=resolveContinuingMounts(player,receipt,candidates);
        if(!resolved.success()){
            logMountRecoveryFailure(receipt,candidates,resolved.reason);
            return KOMEJoinBattleEntryService.PhysicalResult.pending(resolved.reason);
        }
        Entity source=resolved.source;
        // A nested rider reference is authority even before world-list/tracker publication.
        // Adopt it, but leave publication to vanilla; never replace it on a lookup miss.
        if(source!=null&&!source.worldObj.loadedEntityList.contains(source))
            return KOMEJoinBattleEntryService.PhysicalResult.pending(
                KOMEJoinBattleService.Reason.ENTRY_ALREADY_IN_PROGRESS);
        for(Entity duplicate:resolved.remove)retireExactMountForTransfer(player,duplicate,receipt);
        if(!resolved.remove.isEmpty()){
            KOMEJoinBattleEntryRecoveryService.LOGGER.info("LOGIN_SOURCE_DUPLICATES_RECONCILED {}; removed={}",
                receipt.getReceiptId(),resolved.remove.size());
            List<Entity> survivors=findRecoveryMounts(player,receipt);
            if(survivors.size()!=1||survivors.get(0)!=source)
                return KOMEJoinBattleEntryService.PhysicalResult.pending(
                    KOMEJoinBattleService.Reason.MULTIPLE_MOUNT_COPIES_FOUND);
        }
        // DESTINATION_PUBLICATION_PENDING is durable proof that destructive transfer may already
        // have consumed the source. Recovery loads and checks only the recorded source/destination
        // chunks before it considers reconstructing the one exact UUID from the saved snapshot.
        if(source==null)return rollbackMountedEntry(player,receipt,
            KOMEJoinBattleService.Reason.SOURCE_MOUNT_NOT_FOUND_AFTER_REMOVAL);
        if(!exactMount(source,receipt))return KOMEJoinBattleEntryService.PhysicalResult.pending(
            KOMEJoinBattleService.Reason.MOUNT_LOCATION_AMBIGUOUS);
        if(KOMEReflection.getRiddenByEntity(source)==null){
            if(distanceSquared(source,destination)<=0.25D){
                return synchronizeMountedPlayer(player,source)
                    &&mountedStateCoherent(player,source,destination,receipt.getTileId())
                    ?KOMEJoinBattleEntryService.PhysicalResult.deployed()
                    :rollbackMountedEntry(player,receipt,
                        KOMEJoinBattleService.Reason.REMOUNT_FAILED);
            }
            if(distanceSquared(source,receipt.getMountSourceAnchor())<=0.25D){
                if(!synchronizeMountedPlayer(player,source))return rollbackMountedEntry(player,receipt,
                    KOMEJoinBattleService.Reason.REMOUNT_FAILED);
            }
        }
        if(KOMEReflection.getRidingEntity(player)!=source
                ||KOMEReflection.getRiddenByEntity(source)!=player
                ||!receipt.getMountUuid().equals(KOMEReflection.getEntityUUID(source)))
            return rollbackMountedEntry(player,receipt,
                KOMEJoinBattleService.Reason.INVALID_MOUNT_RELATIONSHIP);
        MountInspection check=inspectMount(player);
        if(check.reason!=KOMEJoinBattleService.Reason.ALLOWED||check.profile!=receipt.getMountProfile()
                ||!check.entityType.equals(receipt.getMountEntityType()))
            return rollbackMountedEntry(player,receipt,
                KOMEJoinBattleService.Reason.DESTINATION_PROFILE_MISMATCH);
        World target=world(destination.dimensionId);
        if(target==null||!validateMountedPlacement(target,destination,player,source))
            return KOMEJoinBattleEntryService.PhysicalResult.rolledBack(
                KOMEJoinBattleDeploymentReceipt.MountDisposition.RETURNED_WITH_PLAYER);

        if(!detachMountedPlayer(player,source))return rollbackMountedEntry(player,receipt,
            KOMEJoinBattleService.Reason.INVALID_MOUNT_RELATIONSHIP);
        if(!retireMountForTransfer(player,source)){
            synchronizeMountedPlayer(player,source);
            return rollbackMountedEntry(player,receipt,
                KOMEJoinBattleService.Reason.MOUNT_TRANSFER_FAILED);
        }
        Recreation recreated=recreateDetailed(receipt.getTemporaryMountNbt(),target,destination,
            receipt.getMountUuid(),receipt.getMountEntityType(),receipt.getMountProfile());
        if(!recreated.success()){
            return rollbackMountedEntry(player,receipt,recreated.reason);
        }
        Entity replacement=recreated.entity;
        try{
            if(!synchronizeMountedPlayer(player,replacement)
                    ||!mountedStateCoherent(player,replacement,destination,receipt.getTileId())){
                detachMountedPlayer(player,replacement);
                retireExactMountForTransfer(player,replacement,receipt);
                return rollbackMountedEntry(player,receipt,
                    KOMEJoinBattleService.Reason.REMOUNT_FAILED);
            }
            if(!exactMount(replacement,receipt)
                    ||KOMEReflection.getRidingEntity(player)!=replacement
                    ||KOMEReflection.getRiddenByEntity(replacement)!=player){
                detachMountedPlayer(player,replacement);
                retireExactMountForTransfer(player,replacement,receipt);
                return rollbackMountedEntry(player,receipt,
                    KOMEJoinBattleService.Reason.REMOUNT_FAILED);
            }
            return KOMEJoinBattleEntryService.PhysicalResult.deployed();
        }catch(Throwable failure){
            try{detachMountedPlayer(player,replacement);
                retireExactMountForTransfer(player,replacement,receipt);}catch(Throwable ignored){}
            return rollbackMountedEntry(player,receipt,
                KOMEJoinBattleService.Reason.PLAYER_TELEPORT_FAILED);
        }
    }

    private static KOMEJoinBattleEntryService.PhysicalResult rollbackMountedEntry(
            EntityPlayerMP player,KOMEJoinBattleDeploymentReceipt receipt){
        return rollbackMountedEntry(player,receipt,KOMEJoinBattleService.Reason.MOUNT_TRANSFER_FAILED);
    }

    private static KOMEJoinBattleEntryService.PhysicalResult rollbackMountedEntry(
            EntityPlayerMP player,KOMEJoinBattleDeploymentReceipt receipt,
            KOMEJoinBattleService.Reason initiatingFailure){
        if(player==null||receipt==null||!receipt.isEnteredMounted())
            return KOMEJoinBattleEntryService.PhysicalResult.pending(
                KOMEJoinBattleService.Reason.MOUNT_TRANSFER_FAILED);
        if(KOMEJoinBattleEntryRecoveryService.INSTANCE.awaitingLoginPublication(player))
            return KOMEJoinBattleEntryService.PhysicalResult.pending(
                KOMEJoinBattleService.Reason.ENTRY_ALREADY_IN_PROGRESS);
        KOMEJoinBattleDeploymentReceipt.Pose sourcePose=receipt.getMountSourceAnchor();
        KOMEJoinBattleDeploymentReceipt.Pose playerPose=receipt.getReturnAnchor();
        World sourceWorld=world(sourcePose.dimensionId);
        if(sourceWorld==null||playerPose.dimensionId!=sourcePose.dimensionId)
            return KOMEJoinBattleEntryService.PhysicalResult.pending(
                KOMEJoinBattleService.Reason.MOUNT_TRANSFER_FAILED);
        try{
            if(!KOMEStrategicDeploymentResolver.ensureChunkAvailable(sourceWorld,
                    MathHelper.floor_double(sourcePose.x),MathHelper.floor_double(sourcePose.z)))
                return KOMEJoinBattleEntryService.PhysicalResult.pending(
                    KOMEJoinBattleService.Reason.MOUNT_TRANSFER_FAILED);
            KOMEJoinBattleDeploymentReceipt.Pose destination=receipt.getDeploymentDestination();
            World destinationWorld=destination==null?null:world(destination.dimensionId);
            if(destinationWorld==null||!KOMEStrategicDeploymentResolver.ensureChunkAvailable(
                    destinationWorld,MathHelper.floor_double(destination.x),
                    MathHelper.floor_double(destination.z)))
                return KOMEJoinBattleEntryService.PhysicalResult.pending(
                    KOMEJoinBattleService.Reason.MOUNT_TRANSFER_FAILED);
            Entity riding=KOMEReflection.getRidingEntity(player);
            Entity preferredSource=riding!=null&&exactMount(riding,receipt)?riding:null;
            List<Entity> exact=findRecoveryMounts(player,receipt);
            RollbackMountResolution resolution=resolveRollbackMounts(player,receipt,exact,
                preferredSource);
            if(!resolution.success()){
                logMountRecoveryFailure(receipt,exact,resolution.reason);
                return KOMEJoinBattleEntryService.PhysicalResult.pending(resolution.reason);
            }
            Entity source=resolution.source;
            if(source!=null&&!source.worldObj.loadedEntityList.contains(source))
                return KOMEJoinBattleEntryService.PhysicalResult.pending(
                    KOMEJoinBattleService.Reason.ENTRY_ALREADY_IN_PROGRESS);
            if(resolution.destinationSurvivor){
                // Persist CURRENT state before retiring the sole live copy. Recovery after any
                // interruption must use this state, never the older entry snapshot.
                receipt=rebaseRollbackSnapshot(KOMEWorldData.get(player.worldObj),receipt,source);
            }
            if(preferredSource!=null&&!detachMountedPlayer(player,preferredSource))
                return KOMEJoinBattleEntryService.PhysicalResult.pending(
                    KOMEJoinBattleService.Reason.INVALID_MOUNT_RELATIONSHIP);
            for(Entity duplicate:resolution.remove)removeExactMount(duplicate,receipt);
            if(resolution.destinationSurvivor){
                if(!retireMountForTransfer(player,source))return KOMEJoinBattleEntryService.PhysicalResult.pending(
                    KOMEJoinBattleService.Reason.MOUNT_TRANSFER_FAILED);
                source=null;
            }
            exact=findLoadedMounts(receipt.getMountUuid());
            if(source==null){
                if(!exact.isEmpty())return KOMEJoinBattleEntryService.PhysicalResult.pending(
                    KOMEJoinBattleService.Reason.MULTIPLE_MOUNT_COPIES_FOUND);
                if(receipt.getMountTransferPhase()!=KOMEJoinBattleDeploymentReceipt.MountTransferPhase
                        .DESTINATION_PUBLICATION_PENDING){
                    // No consumption is outstanding. A later-dead/missing mount is NOT resurrected.
                    ReturnResolution safe=resolveReturnPlacement(sourceWorld,playerPose,player,null);
                    if(!safe.valid)return KOMEJoinBattleEntryService.PhysicalResult.pending(
                        KOMEJoinBattleService.Reason.SAFE_POSITION_UNAVAILABLE);
                    if(KOMEReflection.getRidingEntity(player)!=null)player.mountEntity(null);
                    movePlayer(player,safe.pose);
                    return verifyReturn(player,safe.pose)
                        ?KOMEJoinBattleEntryService.PhysicalResult.rolledBack(
                            KOMEJoinBattleDeploymentReceipt.MountDisposition.UNAVAILABLE)
                        :KOMEJoinBattleEntryService.PhysicalResult.pending(KOMEJoinBattleService.Reason.PLAYER_TELEPORT_FAILED);
                }
                // A snapshot alone does not prove source consumption.
                if(receipt.getMountTransferPhase()!=KOMEJoinBattleDeploymentReceipt.MountTransferPhase
                        .DESTINATION_PUBLICATION_PENDING)
                    return KOMEJoinBattleEntryService.PhysicalResult.pending(
                        KOMEJoinBattleService.Reason.MOUNT_LOCATION_AMBIGUOUS);
                KOMEJoinBattleEntryRecoveryService.LOGGER.info("LOGIN_MOUNT_TRULY_MISSING / SOURCE_RECONSTRUCTION_AFTER_STABILIZATION {}",receipt.getReceiptId());
                Recreation restored=recreateDetailed(receipt.getTemporaryMountNbt(),sourceWorld,sourcePose,
                    receipt.getMountUuid(),receipt.getMountEntityType(),receipt.getMountProfile());
                if(!restored.success())return KOMEJoinBattleEntryService.PhysicalResult.pending(
                    restored.reason==KOMEJoinBattleService.Reason.DESTINATION_UUID_MISMATCH
                        ?KOMEJoinBattleService.Reason.SOURCE_ROLLBACK_UUID_MISMATCH
                        :KOMEJoinBattleService.Reason.SOURCE_ROLLBACK_SPAWN_FAILED);
                source=restored.entity;
            }else{
                if(exact.size()!=1||exact.get(0)!=source)
                    return KOMEJoinBattleEntryService.PhysicalResult.pending(
                        KOMEJoinBattleService.Reason.MULTIPLE_MOUNT_COPIES_FOUND);
                source.setLocationAndAngles(sourcePose.x,sourcePose.y,sourcePose.z,
                    sourcePose.yaw,sourcePose.pitch);
                source.motionX=source.motionY=source.motionZ=0D;source.fallDistance=0F;
                sourceWorld.updateEntityWithOptionalForce(source,false);
            }
            if(source==null||!exactMount(source,receipt))return KOMEJoinBattleEntryService.PhysicalResult.pending(
                KOMEJoinBattleService.Reason.SOURCE_ROLLBACK_UUID_MISMATCH);
            if(!synchronizeMountedPlayer(player,source))return KOMEJoinBattleEntryService.PhysicalResult.pending(
                KOMEJoinBattleService.Reason.REMOUNT_FAILED);
            if(!verifyMountedRollback(player,source,receipt))
                return KOMEJoinBattleEntryService.PhysicalResult.pending(
                    KOMEJoinBattleService.Reason.REMOUNT_FAILED);
            return KOMEJoinBattleEntryService.PhysicalResult.rolledBack(initiatingFailure,
                KOMEJoinBattleDeploymentReceipt.MountDisposition.RETURNED_WITH_PLAYER);
        }catch(Throwable failure){
            return KOMEJoinBattleEntryService.PhysicalResult.pending(
                KOMEJoinBattleService.Reason.MOUNT_TRANSFER_FAILED);
        }
    }

    /** Rebase recovery authority before consuming a verified sole destination survivor. */
    static KOMEJoinBattleDeploymentReceipt rebaseRollbackSnapshot(KOMEWorldData data,
            KOMEJoinBattleDeploymentReceipt receipt,Entity survivor){
        if(data.getJoinBattleDeploymentReceipts().get(receipt.getReceiptId())!=receipt
                ||receipt.getState()!=KOMEJoinBattleDeploymentReceipt.State.PENDING_ENTRY
                ||receipt.getMountTransferPhase()!=KOMEJoinBattleDeploymentReceipt.MountTransferPhase.DESTINATION_PUBLICATION_PENDING
                ||!exactMount(survivor,receipt)||survivor.isDead||!survivor.isEntityAlive())
            throw new IllegalArgumentException("Rollback survivor authority changed");
        NBTTagCompound current=new NBTTagCompound();
        if(!survivor.writeMountToNBT(current)||current.hasNoTags())
            throw new IllegalArgumentException("Current rollback survivor state cannot be saved");
        KOMEJoinBattleDeploymentReceipt updated=KOMEJoinBattleDeploymentReceipt.copyOf(receipt)
            .temporaryMountNbt(current).updatedAtMillis(Math.max(System.currentTimeMillis(),receipt.getUpdatedAtMillis())).build();
        data.ensureWritable();data.getJoinBattleDeploymentReceipts().replace(updated);data.markDirty();
        return updated;
    }

    private static boolean verifyMountedRollback(EntityPlayerMP player,Entity mount,
            KOMEJoinBattleDeploymentReceipt receipt){
        if(!exactMount(mount,receipt)||mount.isDead||!mount.isEntityAlive()
                ||KOMEReflection.getRidingEntity(player)!=mount
                ||KOMEReflection.getRiddenByEntity(mount)!=player
                ||!mountedStateCoherent(player,mount,receipt.getMountSourceAnchor(),null))return false;
        List<Entity> exact=findLoadedMounts(receipt.getMountUuid());
        return exact.size()==1&&exact.get(0)==mount;
    }

    private static boolean exactMount(Entity entity,KOMEJoinBattleDeploymentReceipt receipt){
        return entity instanceof EntityLivingBase&&!entity.isDead&&entity.isEntityAlive()
            &&receipt.getMountUuid().equals(KOMEReflection.getEntityUUID(entity))
            &&receipt.getMountEntityType().equals(EntityList.getEntityString(entity))
            &&receipt.getMountProfile()==profileOf(entity);
    }

    static boolean hasExpectedRidingMount(EntityPlayerMP player,
            KOMEJoinBattleDeploymentReceipt receipt){
        Entity riding=player==null?null:KOMEReflection.getRidingEntity(player);
        return receipt!=null&&receipt.isEnteredMounted()&&exactMount(riding,receipt)
            &&KOMEReflection.getRiddenByEntity(riding)==player
            &&(receipt.getMountTransferPhase()==KOMEJoinBattleDeploymentReceipt.MountTransferPhase.NOT_STARTED
                ||semanticallyEquivalentMountState(receipt.getTemporaryMountNbt(),riding));
    }

    static boolean hasPublishedRecoveryMount(KOMEJoinBattleDeploymentReceipt receipt){
        for(Entity candidate:findLoadedMounts(receipt.getMountUuid()))
            if(exactMount(candidate,receipt)
                    &&(withinRecoveryArea(candidate,receipt.getMountSourceAnchor())
                        ||withinRecoveryArea(candidate,receipt.getDeploymentDestination())))return true;
        return false;
    }

    /** Include nested Riding first, independent of chunk, world-list and tracker publication. */
    static List<Entity> includeNestedMount(EntityPlayerMP player,
            KOMEJoinBattleDeploymentReceipt receipt,List<Entity> discovered){
        List<Entity> all=new ArrayList<Entity>();
        Entity riding=player==null?null:KOMEReflection.getRidingEntity(player);
        if(riding!=null)all.add(riding); // Invalid exact-UUID candidates must fail validation, not vanish.
        if(discovered!=null)all.addAll(discovered);
        return distinctLiveMounts(all,receipt.getMountUuid());
    }

    private static List<Entity> findRecoveryMounts(EntityPlayerMP player,
            KOMEJoinBattleDeploymentReceipt receipt){
        List<Entity> found=new ArrayList<Entity>();
        Entity nested=KOMEReflection.getRidingEntity(player);
        if(nested!=null)found.add(nested);
        for(KOMEJoinBattleDeploymentReceipt.Pose pose:new KOMEJoinBattleDeploymentReceipt.Pose[]{
                receipt.getMountSourceAnchor(),receipt.getDeploymentDestination()}){
            if(pose==null)throw new IllegalStateException("Missing mount recovery pose");
            World target=world(pose.dimensionId);
            if(target==null||!KOMEStrategicDeploymentResolver.ensureChunkAvailable(target,
                    MathHelper.floor_double(pose.x),MathHelper.floor_double(pose.z)))
                throw new IllegalStateException("Mount recovery chunk unavailable");
            Chunk chunk=target.getChunkFromBlockCoords(MathHelper.floor_double(pose.x),
                MathHelper.floor_double(pose.z));
            for(List list:chunk.entityLists)for(Object value:list)
                if(value instanceof Entity)found.add((Entity)value);
        }
        found.addAll(findLoadedMounts(receipt.getMountUuid()));
        return includeNestedMount(player,receipt,found);
    }

    /** Continuing entry may resolve equivalent SOURCE copies; mixed-side ambiguity stays closed. */
    static RollbackMountResolution resolveContinuingMounts(EntityPlayerMP player,
            KOMEJoinBattleDeploymentReceipt receipt,List<Entity> discovered){
        List<Entity> exact=includeNestedMount(player,receipt,discovered);
        Entity preferred=hasExpectedRidingMount(player,receipt)
            ?KOMEReflection.getRidingEntity(player):null;
        for(Entity candidate:exact){
            if(!exactMount(candidate,receipt))return RollbackMountResolution.failed(
                KOMEJoinBattleService.Reason.DESTINATION_PROFILE_MISMATCH);
            if(candidate==KOMEReflection.getRidingEntity(player)&&preferred==null)
                return RollbackMountResolution.failed(KOMEJoinBattleService.Reason.MOUNT_DUPLICATE_STATE_MISMATCH);
            if(exact.size()>1&&!withinRecoveryArea(candidate,receipt.getMountSourceAnchor()))
                return RollbackMountResolution.failed(KOMEJoinBattleService.Reason.MOUNT_LOCATION_AMBIGUOUS);
        }
        if(exact.size()==1&&withinRecoveryArea(exact.get(0),receipt.getDeploymentDestination())
                &&!withinRecoveryArea(exact.get(0),receipt.getMountSourceAnchor())){
            Entity destination=exact.get(0);
            if(receipt.getMountTransferPhase()!=KOMEJoinBattleDeploymentReceipt.MountTransferPhase
                    .DESTINATION_PUBLICATION_PENDING
                    ||KOMEReflection.getRiddenByEntity(destination)!=null
                        &&KOMEReflection.getRiddenByEntity(destination)!=player)
                return RollbackMountResolution.failed(KOMEJoinBattleService.Reason.MOUNT_LOCATION_AMBIGUOUS);
            return new RollbackMountResolution(destination,Collections.<Entity>emptyList(),
                KOMEJoinBattleService.Reason.ALLOWED);
        }
        RollbackMountResolution resolution=resolveRollbackMounts(player,receipt,exact,preferred);
        if(!resolution.success())KOMEJoinBattleEntryRecoveryService.LOGGER.warn(
            "LOGIN_SOURCE_DUPLICATES_AMBIGUOUS {}: {}",receipt.getReceiptId(),resolution.reason);
        return resolution;
    }

    /**
     * Source-canonical cancellation/rollback planning. Continuing entry can reuse this only after
     * independently proving that ALL competing copies are source-side transaction equivalents.
     */
    static RollbackMountResolution resolveRollbackMounts(EntityPlayerMP player,
            KOMEJoinBattleDeploymentReceipt receipt,List<Entity> discovered,
            Entity preferredSource){
        if(receipt==null||!receipt.isEnteredMounted())
            return RollbackMountResolution.failed(
                KOMEJoinBattleService.Reason.MOUNT_TRANSFER_FAILED);
        List<Entity> exact=distinctLiveMounts(discovered,receipt.getMountUuid());
        if(exact.isEmpty())return RollbackMountResolution.recreate();
        List<Entity> source=new ArrayList<Entity>(),destination=new ArrayList<Entity>();
        for(Entity candidate:exact){
            if(!exactMount(candidate,receipt))return RollbackMountResolution.failed(
                KOMEJoinBattleService.Reason.DESTINATION_PROFILE_MISMATCH);
            Entity rider=KOMEReflection.getRiddenByEntity(candidate);
            if(rider!=null&&rider!=player)return RollbackMountResolution.failed(
                KOMEJoinBattleService.Reason.MOUNT_LOCATION_AMBIGUOUS);
            boolean atSource=withinRecoveryArea(candidate,receipt.getMountSourceAnchor());
            boolean atDestination=withinRecoveryArea(candidate,receipt.getDeploymentDestination());
            if(!atSource&&!atDestination)return RollbackMountResolution.failed(
                KOMEJoinBattleService.Reason.MOUNT_COPY_OUTSIDE_RECOVERY_AREA);
            if(atSource&&atDestination){
                boolean exactSource=distanceSquared(candidate,receipt.getMountSourceAnchor())<=0.25D;
                boolean exactDestination=distanceSquared(candidate,
                    receipt.getDeploymentDestination())<=0.25D;
                if(candidate==preferredSource||exactSource&&!exactDestination)atDestination=false;
                else if(exactDestination&&!exactSource)atSource=false;
                else return RollbackMountResolution.failed(
                    KOMEJoinBattleService.Reason.MOUNT_LOCATION_AMBIGUOUS);
            }
            (atSource?source:destination).add(candidate);
        }
        if(exact.size()>1)for(Entity candidate:exact)
            if(!semanticallyEquivalentMountState(receipt.getTemporaryMountNbt(),candidate))
                return RollbackMountResolution.failed(
                    KOMEJoinBattleService.Reason.MOUNT_DUPLICATE_STATE_MISMATCH);

        Entity canonical=null;
        if(preferredSource!=null&&source.contains(preferredSource))canonical=preferredSource;
        if(canonical==null){
            List<Entity> exactSource=new ArrayList<Entity>();
            for(Entity candidate:source)if(distanceSquared(candidate,
                    receipt.getMountSourceAnchor())<=0.25D)exactSource.add(candidate);
            if(exactSource.size()==1)canonical=exactSource.get(0);
            else if(source.size()==1)canonical=source.get(0);
            else if(!source.isEmpty())return RollbackMountResolution.failed(
                KOMEJoinBattleService.Reason.MULTIPLE_MOUNT_COPIES_FOUND);
        }
        if(canonical==null&&destination.size()>1)return RollbackMountResolution.failed(
            KOMEJoinBattleService.Reason.MULTIPLE_MOUNT_COPIES_FOUND);
        if(canonical==null&&destination.size()==1){
            if(receipt.getMountTransferPhase()!=KOMEJoinBattleDeploymentReceipt.MountTransferPhase
                    .DESTINATION_PUBLICATION_PENDING)
                return RollbackMountResolution.failed(KOMEJoinBattleService.Reason.MOUNT_LOCATION_AMBIGUOUS);
            // Keep this survivor until CURRENT state is durable. The caller then uses the
            // existing ordered retirement/recreation/attachment path, not historical NBT.
            canonical=destination.get(0);
        }
        List<Entity> remove=new ArrayList<Entity>();
        for(Entity candidate:exact)if(candidate!=canonical)remove.add(candidate);
        if(!remove.isEmpty() && receipt.getMountTransferPhase()!=
                KOMEJoinBattleDeploymentReceipt.MountTransferPhase.DESTINATION_PUBLICATION_PENDING)
            return RollbackMountResolution.failed(KOMEJoinBattleService.Reason.MOUNT_LOCATION_AMBIGUOUS);
        return new RollbackMountResolution(canonical,remove,
            KOMEJoinBattleService.Reason.ALLOWED,destination.contains(canonical));
    }

    private static List<Entity> distinctLiveMounts(List<Entity> discovered,UUID expectedUuid){
        List<Entity> result=new ArrayList<Entity>();
        Set<Entity> seen=Collections.newSetFromMap(new IdentityHashMap<Entity,Boolean>());
        if(discovered==null||expectedUuid==null)return result;
        for(Entity entity:discovered)if(entity!=null&&seen.add(entity)&&!entity.isDead
                &&entity.isEntityAlive()
                &&expectedUuid.equals(KOMEReflection.getEntityUUID(entity)))result.add(entity);
        return result;
    }

    private static boolean withinRecoveryArea(Entity entity,
            KOMEJoinBattleDeploymentReceipt.Pose pose){
        return entity!=null&&pose!=null&&entity.worldObj!=null&&entity.worldObj.provider!=null
            &&entity.worldObj.provider.dimensionId==pose.dimensionId
            &&distanceSquared(entity,pose)<=MOUNT_RECOVERY_RADIUS_SQ;
    }

    static boolean semanticallyEquivalentMountState(NBTTagCompound expected,Entity candidate){
        if(expected==null||candidate==null)return false;
        NBTTagCompound actual=new NBTTagCompound();
        try{if(!candidate.writeMountToNBT(actual))return false;}
        catch(Throwable failure){return false;}
        return semanticallyEquivalentMountNbt(expected,actual);
    }

    static boolean semanticallyEquivalentMountNbt(NBTTagCompound first,NBTTagCompound second){
        if(first==null||second==null)return false;
        NBTTagCompound left=(NBTTagCompound)first.copy(),right=(NBTTagCompound)second.copy();
        for(String key:TRANSIENT_MOUNT_NBT){left.removeTag(key);right.removeTag(key);}
        return left.equals(right);
    }

    static final class RollbackMountResolution{
        final Entity source;final List<Entity> remove;final KOMEJoinBattleService.Reason reason;
        final boolean destinationSurvivor;
        private RollbackMountResolution(Entity source,List<Entity> remove,
                KOMEJoinBattleService.Reason reason){
            this(source,remove,reason,false);
        }
        private RollbackMountResolution(Entity source,List<Entity> remove,
                KOMEJoinBattleService.Reason reason,boolean destinationSurvivor){
            this.source=source;this.remove=Collections.unmodifiableList(
                new ArrayList<Entity>(remove));this.reason=reason;
            this.destinationSurvivor=destinationSurvivor;
        }
        static RollbackMountResolution recreate(){return new RollbackMountResolution(null,
            Collections.<Entity>emptyList(),KOMEJoinBattleService.Reason.ALLOWED);}
        static RollbackMountResolution failed(KOMEJoinBattleService.Reason reason){
            return new RollbackMountResolution(null,Collections.<Entity>emptyList(),reason);}
        boolean success(){return reason==KOMEJoinBattleService.Reason.ALLOWED;}
    }

    private static void logMountRecoveryFailure(KOMEJoinBattleDeploymentReceipt receipt,
            List<Entity> candidates,KOMEJoinBattleService.Reason reason){
        StringBuilder details=new StringBuilder();
        for(Entity candidate:distinctLiveMounts(candidates,receipt.getMountUuid())){
            if(details.length()>0)details.append("; ");
            NBTTagCompound state=new NBTTagCompound();
            boolean snapshot=false;try{snapshot=candidate.writeMountToNBT(state);}catch(Throwable ignored){}
            details.append("entityId=").append(candidate.getEntityId())
                .append(",class=").append(candidate.getClass().getName())
                .append(",type=").append(EntityList.getEntityString(candidate))
                .append(",dimension=").append(candidate.dimension)
                .append(",position=").append(candidate.posX).append('/').append(candidate.posY)
                .append('/').append(candidate.posZ).append(",dead=").append(candidate.isDead)
                .append(",alive=").append(candidate.isEntityAlive())
                .append(",health=").append(candidate instanceof EntityLivingBase
                    ?((EntityLivingBase)candidate).getHealth():-1F)
                .append(",rider=").append(KOMEReflection.getRiddenByEntity(candidate))
                .append(",riding=").append(KOMEReflection.getRidingEntity(candidate))
                .append(",sourceSide=").append(withinRecoveryArea(candidate,
                    receipt.getMountSourceAnchor()))
                .append(",destinationSide=").append(withinRecoveryArea(candidate,
                    receipt.getDeploymentDestination()))
                .append(",snapshotEquivalent=").append(snapshot&&semanticallyEquivalentMountNbt(
                    receipt.getTemporaryMountNbt(),state));
        }
        cpw.mods.fml.common.FMLLog.warning("KOME Join Battle mount recovery %s for %s/%s: %s",
            reason.name(),receipt.getReceiptId(),receipt.getMountUuid(),details.toString());
    }

    private static void removeExactMount(Entity entity,KOMEJoinBattleDeploymentReceipt receipt){
        if(exactMount(entity,receipt))remove(entity);
    }

    private static void retireExactMountForTransfer(EntityPlayerMP player,Entity entity,
            KOMEJoinBattleDeploymentReceipt receipt){
        if(exactMount(entity,receipt))retireMountForTransfer(player,entity);
    }

    static Entity recreate(NBTTagCompound nbt,World world,
            KOMEJoinBattleDeploymentReceipt.Pose pose,UUID expectedUuid,String expectedType,
            KOMEJoinBattleDeploymentReceipt.MountProfile profile){
        return recreateDetailed(nbt,world,pose,expectedUuid,expectedType,profile).entity;
    }

    /** LOTR's native fast travel constructs by registered type, then reads mount NBT. */
    static Recreation recreateDetailed(NBTTagCompound nbt,World world,
            KOMEJoinBattleDeploymentReceipt.Pose pose,UUID expectedUuid,String expectedType,
            KOMEJoinBattleDeploymentReceipt.MountProfile profile){
        if(nbt==null||world==null||pose==null)return Recreation.failed(
            KOMEJoinBattleService.Reason.DESTINATION_MOUNT_SPAWN_FAILED);
        Entity entity=null;
        try{
            entity=EntityList.createEntityByName(expectedType,world);
            if(!(entity instanceof EntityLivingBase))return Recreation.failed(
                KOMEJoinBattleService.Reason.DESTINATION_MOUNT_SPAWN_FAILED);
            entity.readFromNBT((NBTTagCompound)nbt.copy());
            if(!expectedUuid.equals(KOMEReflection.getEntityUUID(entity)))return Recreation.failed(
                KOMEJoinBattleService.Reason.DESTINATION_UUID_MISMATCH);
            if(!expectedType.equals(EntityList.getEntityString(entity))||profileOf(entity)!=profile)
                return Recreation.failed(KOMEJoinBattleService.Reason.DESTINATION_PROFILE_MISMATCH);
            entity.setLocationAndAngles(pose.x,pose.y,pose.z,pose.yaw,pose.pitch);
            entity.motionX=entity.motionY=entity.motionZ=0D;entity.fallDistance=0F;
            // This scope owns spawn only. Tracker publication has its own bounded forceSpawn
            // scope, so early geometry/recovery exits cannot retain this temporary flag.
            entity.forceSpawn=true;
            if(!world.spawnEntityInWorld(entity)){remove(entity);return Recreation.failed(
                KOMEJoinBattleService.Reason.DESTINATION_MOUNT_SPAWN_FAILED);}
            // LOTR fast travel explicitly performs this publication update after spawning the
            // recreated mount. It is particularly important for LOTR mount watcher/state setup.
            if(!world.isRemote)world.updateEntityWithOptionalForce(entity,false);
            List<Entity> published=new ArrayList<Entity>();
            for(Object value:world.loadedEntityList)if(value instanceof Entity)
                published.add((Entity)value);
            int copies=distinctLiveMounts(published,expectedUuid).size();
            if(copies!=1){remove(entity);return Recreation.failed(copies>1
                ?KOMEJoinBattleService.Reason.MULTIPLE_MOUNT_COPIES_FOUND
                :KOMEJoinBattleService.Reason.DESTINATION_MOUNT_SPAWN_FAILED);}
            return Recreation.success(entity);
        }catch(Throwable failure){if(entity!=null)remove(entity);return Recreation.failed(
            KOMEJoinBattleService.Reason.DESTINATION_MOUNT_SPAWN_FAILED);}
        finally{if(entity!=null)entity.forceSpawn=false;}
    }

    static final class Recreation{
        final Entity entity;final KOMEJoinBattleService.Reason reason;
        private Recreation(Entity entity,KOMEJoinBattleService.Reason reason){
            this.entity=entity;this.reason=reason;
        }
        static Recreation success(Entity entity){return new Recreation(entity,KOMEJoinBattleService.Reason.ALLOWED);}
        static Recreation failed(KOMEJoinBattleService.Reason reason){return new Recreation(null,reason);}
        boolean success(){return entity!=null&&reason==KOMEJoinBattleService.Reason.ALLOWED;}
    }

    private static void remove(Entity entity){
        if(entity==null)return;
        World world=entity.worldObj;
        if(world instanceof WorldServer)try{
            ((WorldServer)world).getEntityTracker().removeEntityFromAllTrackingPlayers(entity);
        }catch(Throwable ignored){}
        KOMEReflection.setDead(entity);
        if(world!=null)try{world.removeEntity(entity);}catch(Throwable ignored){}
    }

    /**
     * Ends the old transient entity lifecycle before a same-UUID replacement is published. Vanilla
     * normally batches non-player S13 destroys until EntityPlayerMP.onUpdate; this transfer cannot
     * let that delayed destroy trail the replacement's spawn/attach sequence for the rider.
     */
    static boolean retireMountForTransfer(EntityPlayerMP rider,Entity mount){
        if(mount==null||mount.isDead)return false;
        try{
            if(rider!=null&&rider.playerNetServerHandler!=null)
                rider.playerNetServerHandler.sendPacket(
                    new S13PacketDestroyEntities(new int[]{mount.getEntityId()}));
            if(mount.worldObj instanceof WorldServer){
                WorldServer world=(WorldServer)mount.worldObj;
                world.getEntityTracker().removeEntityFromAllTrackingPlayers(mount);
                if(!world.getEntityTracker().getTrackingPlayers(mount).isEmpty())return false;
            }
            remove(mount);
            return mount.isDead;
        }catch(Throwable failure){return false;}
    }

    static void movePlayer(EntityPlayerMP player,KOMEJoinBattleDeploymentReceipt.Pose pose){
        if(player.dimension!=pose.dimensionId)player.travelToDimension(pose.dimensionId);
        player.setPositionAndRotation(pose.x,pose.y,pose.z,pose.yaw,pose.pitch);
        if(player.worldObj instanceof WorldServer){
            WorldServer target=(WorldServer)player.worldObj;
            if(!KOMEStrategicDeploymentResolver.ensureChunkAvailable(target,
                    MathHelper.floor_double(pose.x),MathHelper.floor_double(pose.z)))
                throw new IllegalStateException("Destination chunk unavailable");
            // setPlayerLocation only sends S08; it does NOT move PlayerManager subscriptions.
            // Do not depend on a later movement/teleport acknowledgement to deliver terrain.
            target.getPlayerManager().updatePlayerPertinentChunks(player);
            Chunk chunk=target.getChunkFromChunkCoords(MathHelper.floor_double(pose.x)>>4,
                MathHelper.floor_double(pose.z)>>4);
            if(!publishChunkToPlayerBeforeMount(player,target,chunk))
                throw new IllegalStateException("Destination chunk publication unavailable");
        }
        if(player.playerNetServerHandler!=null)
            player.playerNetServerHandler.setPlayerLocation(pose.x,pose.y,pose.z,pose.yaw,pose.pitch);
        else{player.setPositionAndUpdate(pose.x,pose.y,pose.z);player.rotationYaw=pose.yaw;player.rotationPitch=pose.pitch;}
        player.motionX=player.motionY=player.motionZ=0D;player.fallDistance=0F;
    }

    /**
     * Publishes the mount to this player before sending the vanilla rider attachment. The player
     * is placed at the mount-derived rider height before EntityPlayerMP.mountEntity sends its own
     * attach/position packets, then the canonical attach packet is repeated after rider positioning.
     */
    static boolean synchronizeMountedPlayer(EntityPlayerMP player,Entity mount){
        RiderSyncResult result=synchronizeMountedPlayerDetailed(player,mount,
            MountPublicationMode.ENTRY_OR_RECOVERY);
        logRiderSyncFailure(result);
        return result.success();
    }

    private static boolean synchronizeMountedPlayerForEgress(EntityPlayerMP player,Entity mount){
        RiderSyncResult result=synchronizeMountedPlayerDetailed(player,mount,
            MountPublicationMode.ORDERED_EGRESS);
        logRiderSyncFailure(result);
        return result.success();
    }

    private static void logRiderSyncFailure(RiderSyncResult result){
        if(!result.success())cpw.mods.fml.common.FMLLog.warning(
            "KOME mounted rider synchronization failed at %s "
                +"(publicationMode=%s, playerCleanBeforeAttach=%s, mountCleanBeforeAttach=%s, "
                +"afterMount=%s, afterRiderUpdate=%s, afterPositionSync=%s)",
            result.stage.name(),result.publicationMode.name(),
            Boolean.valueOf(result.playerCleanBeforeAttach),
            Boolean.valueOf(result.mountCleanBeforeAttach),Boolean.valueOf(result.afterMount),
            Boolean.valueOf(result.afterRiderUpdate),Boolean.valueOf(result.afterPositionSync));
    }

    /**
     * One canonical 1.7.10 rider-attachment sequence shared by entry, rollback, and egress.
     * EntityPlayerMP.mountEntity supplies the normal player attach/location packets; the final
     * tracker broadcast repeats the authoritative relationship after the newly spawned mount is
     * known to the destination player.
     */
    static RiderSyncResult synchronizeMountedPlayerDetailed(EntityPlayerMP player,Entity mount){
        return synchronizeMountedPlayerDetailed(player,mount,
            MountPublicationMode.ENTRY_OR_RECOVERY);
    }

    static RiderSyncResult synchronizeMountedPlayerDetailed(EntityPlayerMP player,Entity mount,
            MountPublicationMode publicationMode){
        if(publicationMode==null)publicationMode=MountPublicationMode.ENTRY_OR_RECOVERY;
        if(player==null||mount==null||mount.isDead||!mount.isEntityAlive()
                ||mount.worldObj==null||player.worldObj!=mount.worldObj
                ||player.dimension!=mount.dimension)
            return RiderSyncResult.failed(publicationMode,RiderSyncStage.INVALID_INPUT,false,false,
                false,false,false);
        boolean playerClean=false,mountClean=false,afterMount=false,
            afterRiderUpdate=false,afterPositionSync=false;
        try{
            Entity playerRide=KOMEReflection.getRidingEntity(player);
            Entity mountRider=KOMEReflection.getRiddenByEntity(mount);
            boolean alreadyAttached=playerRide==mount&&mountRider==player;
            playerClean=alreadyAttached||playerRide==null;
            mountClean=alreadyAttached||mountRider==null;
            if(!playerClean)return RiderSyncResult.failed(publicationMode,
                RiderSyncStage.PLAYER_DETACH_FAILED,
                playerClean,mountClean,false,false,false);
            if(!mountClean)return RiderSyncResult.failed(publicationMode,
                RiderSyncStage.MOUNT_ALREADY_RIDDEN,
                playerClean,mountClean,false,false,false);

            double riderY=mount.posY+mount.getMountedYOffset()+player.getYOffset();
            player.setPositionAndRotation(mount.posX,riderY,mount.posZ,
                player.rotationYaw,player.rotationPitch);
            player.motionX=player.motionY=player.motionZ=0D;player.fallDistance=0F;
            RiderSyncStage publication=publishMountToPlayer(player,mount,publicationMode);
            if(publication!=RiderSyncStage.SUCCESS)return RiderSyncResult.failed(
                publicationMode,
                publication,playerClean,mountClean,false,false,false);

            if(!alreadyAttached)player.mountEntity(mount);
            afterMount=KOMEReflection.getRidingEntity(player)==mount
                &&KOMEReflection.getRiddenByEntity(mount)==player;
            if(!afterMount)return RiderSyncResult.failed(publicationMode,RiderSyncStage.ATTACH_FAILED,
                playerClean,mountClean,false,false,false);

            mount.updateRiderPosition();
            afterRiderUpdate=KOMEReflection.getRidingEntity(player)==mount
                &&KOMEReflection.getRiddenByEntity(mount)==player&&riderPositionCoherent(player,mount);
            if(!afterRiderUpdate)return RiderSyncResult.failed(publicationMode,
                RiderSyncStage.RIDER_UPDATE_FAILED,
                playerClean,mountClean,afterMount,false,false);

            if(player.playerNetServerHandler!=null){
                S1BPacketEntityAttach attachment=new S1BPacketEntityAttach(0,player,mount);
                if(mount.worldObj instanceof WorldServer)
                    ((WorldServer)mount.worldObj).getEntityTracker().func_151247_a(player,attachment);
                player.playerNetServerHandler.sendPacket(attachment);
                player.playerNetServerHandler.setPlayerLocation(player.posX,player.posY,player.posZ,
                    player.rotationYaw,player.rotationPitch);
            }
            afterPositionSync=KOMEReflection.getRidingEntity(player)==mount
                &&KOMEReflection.getRiddenByEntity(mount)==player&&riderPositionCoherent(player,mount);
            mount.forceSpawn=false;
            return afterPositionSync?RiderSyncResult.success(publicationMode,
                playerClean,mountClean,afterMount,afterRiderUpdate):RiderSyncResult.failed(
                    publicationMode,
                    RiderSyncStage.FINAL_POSITION_SYNC_FAILED,playerClean,mountClean,
                    afterMount,afterRiderUpdate,false);
        }catch(Throwable failure){
            return RiderSyncResult.failed(publicationMode,RiderSyncStage.EXCEPTION,
                playerClean,mountClean,
                afterMount,afterRiderUpdate,afterPositionSync);
        }
    }

    /** Canonical dismount with reciprocal verification before either entity is removed. */
    static boolean detachMountedPlayer(EntityPlayerMP player,Entity mount){
        if(player==null||mount==null)return false;
        Entity playerRide=KOMEReflection.getRidingEntity(player);
        Entity mountRider=KOMEReflection.getRiddenByEntity(mount);
        if(playerRide==null&&mountRider==null)return true;
        if(playerRide!=mount||mountRider!=player)return false;
        try{player.mountEntity(null);}
        catch(Throwable failure){return false;}
        return KOMEReflection.getRidingEntity(player)==null
            &&KOMEReflection.getRiddenByEntity(mount)==null;
    }

    enum RiderSyncStage{
        SUCCESS,INVALID_INPUT,PLAYER_DETACH_FAILED,MOUNT_ALREADY_RIDDEN,
        RETURN_CHUNK_DELIVERY_FAILED,MOUNT_TRACKING_FAILED,ATTACH_FAILED,RIDER_UPDATE_FAILED,
        FINAL_POSITION_SYNC_FAILED,EXCEPTION
    }

    enum MountPublicationMode{ENTRY_OR_RECOVERY,ORDERED_EGRESS}

    static final class RiderSyncResult{
        final RiderSyncStage stage;final MountPublicationMode publicationMode;
        final boolean playerCleanBeforeAttach,mountCleanBeforeAttach;
        final boolean afterMount,afterRiderUpdate,afterPositionSync;
        private RiderSyncResult(MountPublicationMode publicationMode,RiderSyncStage stage,
                boolean playerCleanBeforeAttach,
                boolean mountCleanBeforeAttach,boolean afterMount,boolean afterRiderUpdate,
                boolean afterPositionSync){
            this.publicationMode=publicationMode;this.stage=stage;
            this.playerCleanBeforeAttach=playerCleanBeforeAttach;
            this.mountCleanBeforeAttach=mountCleanBeforeAttach;this.afterMount=afterMount;
            this.afterRiderUpdate=afterRiderUpdate;this.afterPositionSync=afterPositionSync;
        }
        static RiderSyncResult success(MountPublicationMode publicationMode,
                boolean playerClean,boolean mountClean,
                boolean afterMount,boolean afterRiderUpdate){
            return new RiderSyncResult(publicationMode,RiderSyncStage.SUCCESS,
                playerClean,mountClean,
                afterMount,afterRiderUpdate,true);
        }
        static RiderSyncResult failed(MountPublicationMode publicationMode,RiderSyncStage stage,
                boolean playerClean,
                boolean mountClean,boolean afterMount,boolean afterRiderUpdate,
                boolean afterPositionSync){
            return new RiderSyncResult(publicationMode,stage,playerClean,mountClean,afterMount,
                afterRiderUpdate,afterPositionSync);
        }
        boolean success(){return stage==RiderSyncStage.SUCCESS;}
    }

    private static RiderSyncStage publishMountToPlayer(EntityPlayerMP player,Entity mount,
            MountPublicationMode publicationMode){
        if(!(mount.worldObj instanceof WorldServer))return RiderSyncStage.SUCCESS;
        WorldServer world=(WorldServer)mount.worldObj;
        MinecraftServer server=MinecraftServer.getServer();
        if(server==null||server.getConfigurationManager()==null)return RiderSyncStage.MOUNT_TRACKING_FAILED;
        server.getConfigurationManager().updatePlayerPertinentChunks(player);
        Chunk chunk=world.getChunkFromChunkCoords(mount.chunkCoordX,mount.chunkCoordZ);
        return publishPreparedMountToPlayer(player,mount,world,chunk,publicationMode);
    }

    static RiderSyncStage publishPreparedMountToPlayer(EntityPlayerMP player,Entity mount,WorldServer world,
            Chunk chunk,MountPublicationMode publicationMode){
        try{
            if(!publishChunkToPlayerBeforeMount(player,world,chunk))
                return RiderSyncStage.RETURN_CHUNK_DELIVERY_FAILED;
            // Egress delivered the exact return chunk. Permit this verified mount through the
            // tracker while vanilla watcher registration catches up. Entry behavior is unchanged.
            mount.forceSpawn=true;
            world.getEntityTracker().func_85172_a(player,chunk);
            return world.getEntityTracker().getTrackingPlayers(mount).contains(player)
                ?RiderSyncStage.SUCCESS:RiderSyncStage.MOUNT_TRACKING_FAILED;
        }finally{mount.forceSpawn=false;}
    }

    /** Synchronous delivery of the exact, collision-validated return chunk, before entity spawn. */
    static boolean publishChunkToPlayerBeforeMount(EntityPlayerMP player,WorldServer world,
            Chunk chunk){
        if(player==null||world==null||chunk==null||player.playerNetServerHandler==null
                ||player.worldObj!=world||chunk.worldObj!=world||!chunk.isTerrainPopulated)return false;
        ChunkCoordIntPair target=chunk.getChunkCoordIntPair();
        boolean pending=player.loadedChunks.contains(target);
        if(pending||!world.getPlayerManager().isPlayerWatchingChunk(player,target.chunkXPos,
                target.chunkZPos)){
            // func_150802_k additionally requires tick/light-population flags. Those gate
            // vanilla's background send queue, not serialization of this already generated
            // source chunk. Waiting for them here strands a same-tick distant return.
            player.playerNetServerHandler.sendPacket(new S26PacketMapChunkBulk(
                Collections.singletonList(chunk)));
            List tiles=world.func_147486_a(target.chunkXPos*16,0,target.chunkZPos*16,
                target.chunkXPos*16+15,256,target.chunkZPos*16+15);
            for(Object value:tiles)if(value instanceof TileEntity){
                Packet description=((TileEntity)value).getDescriptionPacket();
                if(description!=null)player.playerNetServerHandler.sendPacket(description);
            }
            player.loadedChunks.remove(target);
            MinecraftForge.EVENT_BUS.post(new ChunkWatchEvent.Watch(target,player));
        }
        return true;
    }

    private static boolean riderPositionCoherent(EntityPlayerMP player,Entity mount){
        double expectedY=mount.posY+mount.getMountedYOffset()+player.getYOffset();
        double dx=player.posX-mount.posX,dy=player.posY-expectedY,dz=player.posZ-mount.posZ;
        return dx*dx+dy*dy+dz*dz<=0.25D;
    }

    static boolean mountedStateCoherent(EntityPlayerMP player,Entity mount,
            KOMEJoinBattleDeploymentReceipt.Pose mountPose,String expectedTile){
        if(player==null||mount==null||mountPose==null||mount.worldObj==null
                ||player.dimension!=mountPose.dimensionId||mount.dimension!=mountPose.dimensionId
                ||distanceSquared(mount,mountPose)>0.25D
                ||KOMEReflection.getRidingEntity(player)!=mount
                ||KOMEReflection.getRiddenByEntity(mount)!=player
                ||!riderPositionCoherent(player,mount)
                ||!validateMountedPlacement(mount.worldObj,pose(mount),player,mount))return false;
        if(expectedTile==null)return true;
        KOMETileResolution tile=KOMEBuildService.tileAtWorldCoordinates(
            mount.dimension,mount.posX,mount.posZ);
        return tile.status==KOMETileResolution.Status.RESOLVED&&expectedTile.equals(tile.tileId);
    }

    private static boolean verifyPlayer(EntityPlayerMP player,
            KOMEJoinBattleDeploymentReceipt receipt){
        KOMEJoinBattleDeploymentReceipt.Pose destination=receipt.getDeploymentDestination();
        if(player.dimension!=destination.dimensionId||distanceSquared(player,destination)>0.25D)return false;
        KOMETileResolution tile=KOMEBuildService.tileAtWorldCoordinates(
            destination.dimensionId,player.posX,player.posZ);
        return tile.status==KOMETileResolution.Status.RESOLVED
            &&receipt.getTileId().equals(tile.tileId);
    }

    private static double distanceSquared(Entity entity,KOMEJoinBattleDeploymentReceipt.Pose pose){
        double dx=entity.posX-pose.x,dy=entity.posY-pose.y,dz=entity.posZ-pose.z;
        return dx*dx+dy*dy+dz*dz;
    }

    private static MountInspection inspectMount(EntityPlayerMP player){
        Entity mount=KOMEReflection.getRidingEntity(player);
        if(mount==null)return MountInspection.unmounted();
        if(!(mount instanceof EntityLivingBase)||KOMEReflection.getRiddenByEntity(mount)!=player
                ||KOMEReflection.getRidingEntity(mount)!=null||KOMEReflection.getRiddenByEntity(player)!=null
                ||mount.worldObj!=player.worldObj)
            return MountInspection.denied(KOMEJoinBattleService.Reason.INVALID_MOUNT_RELATIONSHIP);
        KOMEJoinBattleDeploymentReceipt.MountProfile profile=profileOf(mount);
        if(profile==null)return MountInspection.denied(KOMEJoinBattleService.Reason.UNSUPPORTED_MOUNT);
        String type=EntityList.getEntityString(mount);
        if(type==null||type.trim().isEmpty())return MountInspection.denied(
            KOMEJoinBattleService.Reason.UNSUPPORTED_MOUNT);
        return MountInspection.mounted(mount,type,profile);
    }

    static KOMEJoinBattleDeploymentReceipt.MountProfile profileOf(Entity entity){
        if(entity==null||entity instanceof LOTREntityMumakil)return null;
        if(entity instanceof LOTREntityHorse)
            return KOMEJoinBattleDeploymentReceipt.MountProfile.LOTR_HORSE_FAMILY;
        if(entity instanceof LOTREntityWarg)
            return KOMEJoinBattleDeploymentReceipt.MountProfile.LOTR_WARG;
        if(entity instanceof EntityHorse)
            return KOMEJoinBattleDeploymentReceipt.MountProfile.VANILLA_HORSE;
        return null;
    }

    static boolean validMember(KOMEArmyCompany company,KOMEHiredUnitRecord record,
            UUID expected,String tileId){
        return company!=null&&record!=null&&expected!=null&&expected.equals(record.entity)
            &&company.id.equals(record.companyId)&&company.units.contains(expected)
            &&KOMEHiredUnitClassification.isCampaignUnit(record)&&!record.farmhand
            &&record.type==KOMEPopulationType.OFFENSIVE&&!record.populationReturned
            &&tileId.equals(KOMEConquestTile.normalizeId(company.currentTile))
            &&tileId.equals(KOMEConquestTile.normalizeId(record.currentTile));
    }

    private static Entity findLoaded(UUID uuid){
        MinecraftServer server=MinecraftServer.getServer();
        if(server==null||server.worldServers==null||uuid==null)return null;
        for(WorldServer world:server.worldServers)if(world!=null)
            for(Object value:world.loadedEntityList)if(value instanceof Entity
                    &&!((Entity)value).isDead
                    &&((Entity)value).isEntityAlive()
                    &&uuid.equals(KOMEReflection.getEntityUUID((Entity)value)))return (Entity)value;
        return null;
    }

    private static List<Entity> findLoadedMounts(UUID uuid){
        List<Entity> discovered=new ArrayList<Entity>();
        MinecraftServer server=MinecraftServer.getServer();
        if(server==null||server.worldServers==null||uuid==null)return discovered;
        for(WorldServer world:server.worldServers)if(world!=null)
            for(Object value:world.loadedEntityList)if(value instanceof Entity
                    &&uuid.equals(KOMEReflection.getEntityUUID((Entity)value)))
                discovered.add((Entity)value);
        return distinctLiveMounts(discovered,uuid);
    }

    private static WorldServer world(int dimension){
        MinecraftServer server=MinecraftServer.getServer();
        return server==null?null:server.worldServerForDimension(dimension);
    }

    private static KOMEJoinBattleDeploymentReceipt.Pose pose(Entity entity){
        return new KOMEJoinBattleDeploymentReceipt.Pose(entity.worldObj.provider.dimensionId,
            entity.posX,entity.posY,entity.posZ,entity.rotationYaw,entity.rotationPitch);
    }

    static boolean validateMountedPlacement(World world,KOMEJoinBattleDeploymentReceipt.Pose pose,
            EntityPlayerMP player,Entity mount){
        if(world==null||pose==null||player==null||mount==null
                ||world.provider.dimensionId!=pose.dimensionId)return false;
        double mountHalf=mount.width/2D;
        double riderY=pose.y+mount.getMountedYOffset()+player.getYOffset();
        if(pose.y<1D||riderY<pose.y||riderY+player.height>world.getActualHeight()
                ||!hasSolidFootprintSupport(world,pose,mount.width))return false;
        AxisAlignedBB mountBox=AxisAlignedBB.getBoundingBox(pose.x-mountHalf,pose.y,
            pose.z-mountHalf,pose.x+mountHalf,pose.y+mount.height,pose.z+mountHalf);
        double riderHalf=player.width/2D;
        AxisAlignedBB riderBox=AxisAlignedBB.getBoundingBox(pose.x-riderHalf,riderY,
            pose.z-riderHalf,pose.x+riderHalf,riderY+player.height,pose.z+riderHalf);
        try{
            return mountedPairCollisionFree(world,mountBox,player,mount)
                &&mountedPairCollisionFree(world,riderBox,player,mount)
                &&!world.isAnyLiquid(mountBox)&&!world.isAnyLiquid(riderBox);
        }catch(Throwable unavailable){return false;}
    }

    /** Block collision plus unrelated-entity overlap, explicitly excluding this rider/mount pair. */
    private static boolean mountedPairCollisionFree(World world,AxisAlignedBB box,
            EntityPlayerMP player,Entity mount){
        if(!world.func_147461_a(box).isEmpty())return false;
        List nearby=world.getEntitiesWithinAABBExcludingEntity(null,box.expand(0.25D,0.25D,0.25D));
        for(Object value:nearby)if(value instanceof Entity){
            Entity other=(Entity)value;
            if(other==player||other==mount||other.isDead)continue;
            AxisAlignedBB otherBox=other.boundingBox;
            if(otherBox!=null&&otherBox.intersectsWith(box))return false;
        }
        return true;
    }

    private static boolean hasSolidFootprintSupport(World world,
            KOMEJoinBattleDeploymentReceipt.Pose pose,double width){
        double half=width/2D;
        int minX=MathHelper.floor_double(pose.x-half),maxX=MathHelper.floor_double(
            pose.x+half-1.0E-7D),minZ=MathHelper.floor_double(pose.z-half),
            maxZ=MathHelper.floor_double(pose.z+half-1.0E-7D);
        int y=MathHelper.floor_double(pose.y)-1;
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++){
            if(!world.blockExists(x,y,z)
                    &&!KOMEStrategicDeploymentResolver.ensureChunkAvailable(world,x,z))return false;
            net.minecraft.block.Block ground=world.getBlock(x,y,z);
            if(ground==null||!ground.getMaterial().isSolid()||ground.getMaterial().isLiquid()
                    ||ground.getCollisionBoundingBoxFromPool(world,x,y,z)==null)return false;
        }
        return true;
    }

    static boolean validatePlayerPlacement(World world,KOMEJoinBattleDeploymentReceipt.Pose pose,
            EntityPlayerMP player){
        if(world==null||pose==null||player==null||world.provider.dimensionId!=pose.dimensionId)return false;
        double half=player.width/2D;
        AxisAlignedBB body=AxisAlignedBB.getBoundingBox(pose.x-half,pose.y,pose.z-half,
            pose.x+half,pose.y+player.height,pose.z+half);
        try{return mountedPairCollisionFree(world,body,player,null)&&!world.isAnyLiquid(body);}
        catch(Throwable unavailable){return false;}
    }

    private static final class MountInspection{
        final boolean mounted;final Entity entity;final String entityType;
        final KOMEJoinBattleDeploymentReceipt.MountProfile profile;
        final KOMEJoinBattleService.Reason reason;
        private MountInspection(boolean mounted,Entity entity,String type,
                KOMEJoinBattleDeploymentReceipt.MountProfile profile,
                KOMEJoinBattleService.Reason reason){
            this.mounted=mounted;this.entity=entity;this.entityType=type;
            this.profile=profile;this.reason=reason;
        }
        static MountInspection unmounted(){return new MountInspection(false,null,"",null,
            KOMEJoinBattleService.Reason.ALLOWED);}
        static MountInspection mounted(Entity entity,String type,
                KOMEJoinBattleDeploymentReceipt.MountProfile profile){
            return new MountInspection(true,entity,type,profile,KOMEJoinBattleService.Reason.ALLOWED);}
        static MountInspection denied(KOMEJoinBattleService.Reason reason){
            return new MountInspection(false,null,"",null,reason);}
    }
}
