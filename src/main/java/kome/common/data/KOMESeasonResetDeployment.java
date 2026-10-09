package kome.common.data;

import kome.common.KOMEReflection;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EntityList;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import lotr.common.entity.npc.LOTREntityNPC;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import net.minecraft.world.chunk.storage.RegionFileCache;
import net.minecraft.world.storage.ThreadedFileIOBase;
import net.minecraft.nbt.CompressedStreamTools;

/** Physical reset adapter: move existing objects or hydrate exclusively virtual snapshots. */
public final class KOMESeasonResetDeployment implements KOMESeasonResetService.Deployment {
    public static final String RECEIPT = "KOMESeasonReturn";
    private static java.lang.reflect.Field queuedUnloadsField;
    interface EntityCheckpoint { String save(World world, List<Entity> entities, Set<Chunk> touched); }
    private final EntityCheckpoint entityCheckpoint;
    private final java.util.function.Consumer<KOMEWorldData> intentCheckpoint;
    public KOMESeasonResetDeployment(World world) { this(world, KOMESeasonResetDeployment::saveEntities, data -> data.checkpointReset(world)); }
    KOMESeasonResetDeployment(World world, EntityCheckpoint checkpoint, java.util.function.Consumer<KOMEWorldData> intent) {
        entityCheckpoint = checkpoint; intentCheckpoint = intent;
    }

    @Override public void checkpoint(KOMEWorldData data) {
        intentCheckpoint.accept(data);
    }

    @Override public String apply(KOMEWorldData data, KOMESeasonResetState.Return entry, String token) {
        return apply(data, entry, token, null);
    }

    private String apply(KOMEWorldData data, KOMESeasonResetState.Return entry, String token, UUID onlyUnit) {
        World world = DimensionManager.getWorld(entry.capital == null
            ? lotr.common.LOTRDimension.MIDDLE_EARTH.dimensionID : entry.capital.getDeploymentDimensionId());
        if (!entry.returnRequired && entry.virtualUnits.isEmpty()) return "";
        if (world == null) return "Load the destination dimension before retrying";
        KOMEStrategicDeploymentResolver.Anchor capital = null;
        if (entry.returnRequired) {
            KOMEFactionCapitalRecord record=KOMEFactionCapitalService.getCapital(data,entry.nativeFaction);
            if(record==null)return "No authoritative capital exists for faction: " + entry.nativeFaction;
            // Returned survivors may occupy the capital reference on a retry. Actual placement
            // below still uses resolveAround's full terrain, liquid and live-entity clearance.
            KOMEStrategicDeploymentResolver.Validation validation=
                KOMEStrategicDeploymentResolver.validateResetSearchOrigin(world,record.getCapitalTileId(),
                    record.getDeploymentDimensionId(),record.getDeploymentX(),record.getDeploymentY(),record.getDeploymentZ());
            if(!validation.valid)return "Capital deployment is unavailable for " + record.getFactionId() + ": " + validation.reason;
            capital=validation.anchor;
        }
        int index = 0;
        List<Entity> verified = new ArrayList<Entity>();
        Set<Chunk> touched = new LinkedHashSet<Chunk>();
        for (Long key : entry.originChunks) {
            int x = (int)(key.longValue() >> 32), z = key.intValue();
            if (!KOMEStrategicDeploymentResolver.ensureChunkAvailable(world, x << 4, z << 4)) return "Original company chunk is unavailable; return remains pending";
            touched.add(world.getChunkFromChunkCoords(x, z));
        }
        for (UUID id : entry.units) {
            if (onlyUnit != null && !onlyUnit.equals(id)) continue;
            if (!entry.returnRequired && !entry.virtualUnits.contains(id)) continue;
            KOMEHiredUnitRecord record = data.hiredUnits.get(id);
            Entity entity = find(id);
            if (entity == null && !entry.virtualUnits.contains(id) && record != null && record.stationedEntityData != null) {
                // A stationary snapshot is only a hint for loading the existing entity, never a spawn source.
                NBTTagCompound snapshot = record.stationedEntityData;
                net.minecraft.nbt.NBTTagList pos = snapshot.getTagList("Pos", 6);
                if (pos.tagCount() == 3 && snapshot.getInteger("Dimension") == world.provider.dimensionId) {
                    KOMETileResolution origin = KOMETileWorldResolver.INSTANCE.resolveWorldPosition(world.provider.dimensionId,
                        pos.func_150309_d(0), pos.func_150309_d(2));
                    if (origin.status == KOMETileResolution.Status.RESOLVED && entry.origin.equals(origin.tileId)) {
                        KOMEStrategicDeploymentResolver.ensureChunkAvailable(world,
                            (int)Math.floor(pos.func_150309_d(0)), (int)Math.floor(pos.func_150309_d(2)));
                        entity = find(id);
                    }
                }
            }
            boolean restoring = entity == null;
            if (restoring) {
                if (entry.complete || !entry.virtualUnits.contains(id)) return "Load company unit " + id + " at " + entry.origin + "; stationary snapshots are not proof of absence";
                if (record == null || record.movingEntityData == null) return "Authoritative movement snapshot unavailable for " + id;
                try { entity = restoreVirtualUnit(record, world); }
                catch (IllegalArgumentException invalidHealth) {
                    return "Authoritative survivor health unavailable for " + id + ": " + invalidHealth.getMessage();
                }
                if (entity == null || !id.equals(KOMEReflection.getEntityUUID(entity)))
                    return "Movement snapshot identity does not match " + id;
            }
            if (!(entity instanceof LOTREntityNPC) || !aliveTree(entity)) return "Unit or mount is not a surviving live entity: " + id;
            if (entity.worldObj != world) return "Unit " + id + " is in another dimension; cross-dimension return remains pending";
            if (!restoring && token.equals(entity.getEntityData().getString(RECEIPT)) && inTile(entity, entry.destination)) {
                verified.add(entity);
                touched.add(world.getChunkFromBlockCoords((int)Math.floor(entity.posX), (int)Math.floor(entity.posZ)));
                index++; continue;
            }
            if (!restoring && !token.equals(entity.getEntityData().getString(RECEIPT)) && !inTile(entity, entry.origin))
                return "Loaded unit location disagrees with the reset origin; repair company location before retrying";
            if (!restoring && entry.virtualUnits.contains(id) && !token.equals(entity.getEntityData().getString(RECEIPT)))
                return "Stale virtual entity " + id + " must unload before its authoritative snapshot can be restored";
            double x = capital == null ? entity.posX : capital.x;
            double y = capital == null ? entity.posY : capital.y;
            double z = capital == null ? entity.posZ : capital.z;
            double width = entity.width, height = entity.height;
            for (Entity mount = entity.ridingEntity; mount != null; mount = mount.ridingEntity) {
                width = Math.max(width, mount.width); height += mount.height;
                if (restoring && find(KOMEReflection.getEntityUUID(mount)) != null) return "A saved mount UUID is already loaded; resolve duplicate evidence";
            }
            KOMEStrategicDeploymentResolver.Validation position = KOMEStrategicDeploymentResolver.resolveAround(world,
                entry.destination, x + (index % 8) * 3, y, z + (index / 8) * 3, 24, width, height);
            if (!position.valid) return "Safe return deployment unavailable for " + id + ": " + position.reason;
            boolean newOrigins = false;
            if (!restoring) for (Entity part = entity; part != null; part = part.ridingEntity) {
                int xChunk = (int)Math.floor(part.posX) >> 4, zChunk = (int)Math.floor(part.posZ) >> 4;
                newOrigins |= entry.originChunks.add(chunkKey(xChunk, zChunk));
                touched.add(world.getChunkFromChunkCoords(xChunk, zChunk));
            }
            if (newOrigins) checkpoint(data);
            moveTree(entity, position.anchor.x, position.anchor.y, position.anchor.z, token, !restoring);
            if (restoring && !spawnTree(world, entity)) return "World rejected return of " + id + "; snapshot retained for retry";
            if (find(id) != entity || !aliveTree(entity) || !inTile(entity, entry.destination))
                return "Physical return verification failed for " + id;
            verified.add(entity);
            touched.add(world.getChunkFromBlockCoords((int)Math.floor(entity.posX), (int)Math.floor(entity.posZ)));
            index++;
        }
        return entityCheckpoint.save(world, verified, touched);
    }

    static long chunkKey(int x, int z) { return ((long)x << 32) | (z & 0xffffffffL); }

    /** Use the existing chunk loader, then read native Anvil NBT back without creating entities. */
    static String saveEntities(World world, List<Entity> entities, Set<Chunk> touched) {
        if (!(world.getChunkProvider() instanceof ChunkProviderServer)) return "Server chunk persistence is unavailable";
        ChunkProviderServer provider = (ChunkProviderServer) world.getChunkProvider();
        if (!provider.canSave() || !(provider.currentChunkLoader instanceof AnvilChunkLoader))
            return "Writable Anvil chunk persistence is required to confirm company returns";
        AnvilChunkLoader loader = (AnvilChunkLoader) provider.currentChunkLoader;
        try {
            for (Chunk chunk : touched) loader.saveChunk(world, chunk);
            loader.saveExtraData();
            ThreadedFileIOBase.threadedIOInstance.waitForFinish();
            for (Chunk chunk : touched) {
                NBTTagCompound disk;
                try (java.io.DataInputStream input = RegionFileCache.getChunkInputStream(loader.chunkSaveLocation, chunk.xPosition, chunk.zPosition)) {
                    if (input == null) return "Returned entity chunk has not reached disk";
                    disk = CompressedStreamTools.read(input).getCompoundTag("Level");
                }
                for (Entity entity : entities) for (Entity part = entity; part != null; part = part.ridingEntity) {
                    boolean destination = chunk.xPosition == ((int)Math.floor(part.posX) >> 4)
                        && chunk.zPosition == ((int)Math.floor(part.posZ) >> 4);
                    NBTTagCompound saved = savedEntity(disk.getTagList("Entities", 10), KOMEReflection.getEntityUUID(part));
                    if (!destination && saved != null) return "Origin chunk still contains a returned unit; retry persistence";
                    if (destination && !matchesReceipt(saved, part)) return "Return receipt or surviving HP is not yet durable; retry persistence";
                }
            }
            return "";
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return "Interrupted while saving return entities; retry required";
        } catch (Exception unavailable) {
            return "Cannot confirm saved return entities: " + unavailable.getMessage();
        }
    }

    static boolean matchesReceipt(NBTTagCompound saved, Entity entity) {
        if (saved == null || !entity.getEntityData().getString(RECEIPT).equals(saved.getCompoundTag("ForgeData").getString(RECEIPT))) return false;
        net.minecraft.nbt.NBTTagList position = saved.getTagList("Pos", 6);
        if (position.tagCount() != 3 || position.func_150309_d(0) != entity.posX
                || position.func_150309_d(1) != entity.posY || position.func_150309_d(2) != entity.posZ) return false;
        if (entity instanceof EntityLivingBase) {
            float hp = saved.hasKey("HealF") ? saved.getFloat("HealF") : saved.getFloat("Health");
            if (Float.compare(hp, ((EntityLivingBase)entity).getHealth()) != 0) return false;
        }
        return true;
    }

    static NBTTagCompound savedEntity(net.minecraft.nbt.NBTTagList entities, UUID id) {
        for (int i = 0; i < entities.tagCount(); i++) {
            NBTTagCompound entity = entities.getCompoundTagAt(i);
            while (entity != null) {
                if (entity.getLong("UUIDMost") == id.getMostSignificantBits()
                        && entity.getLong("UUIDLeast") == id.getLeastSignificantBits()) return entity;
                entity = entity.hasKey("Riding", 10) ? entity.getCompoundTag("Riding") : null;
            }
        }
        return null;
    }

    public static Entity find(UUID id) {
        Entity found = null;
        for (World world : DimensionManager.getWorlds()) {
            List<?> queued = queuedUnloads(world);
            for (Object value : world.loadedEntityList) {
                if (!(value instanceof Entity) || !id.equals(KOMEReflection.getEntityUUID((Entity) value))) continue;
                // Chunk reload can join its replacement before World drains the old unload list.
                // Rejecting that replacement leaves a mounted rider ticking but untracked.
                if (queued.contains(value)) continue;
                if (found != null && found != value) throw new IllegalStateException("Duplicate loaded company unit " + id);
                found = (Entity) value;
            }
        }
        return found;
    }

    private static List<?> queuedUnloads(World world) {
        try {
            if (queuedUnloadsField == null) {
                try { queuedUnloadsField = World.class.getDeclaredField("unloadedEntityList"); }
                catch (NoSuchFieldException remapped) { queuedUnloadsField = World.class.getDeclaredField("field_72997_g"); }
                queuedUnloadsField.setAccessible(true);
            }
            List<?> queued = (List<?>) queuedUnloadsField.get(world);
            return queued == null ? java.util.Collections.emptyList() : queued;
        } catch (ReflectiveOperationException unavailable) {
            throw new IllegalStateException("Cannot inspect queued entity unloads", unavailable);
        }
    }

    public static boolean inTile(Entity entity, String tile) {
        KOMETileResolution resolved = KOMETileWorldResolver.INSTANCE.resolveWorldPosition(entity.dimension, entity.posX, entity.posZ);
        return resolved.status == KOMETileResolution.Status.RESOLVED && tile.equals(resolved.tileId);
    }

    static boolean aliveTree(Entity entity) {
        if (entity == null || entity instanceof net.minecraft.entity.player.EntityPlayer || entity.isDead || entity instanceof EntityLivingBase
                && (!Float.isFinite(((EntityLivingBase) entity).getHealth()) || ((EntityLivingBase) entity).getHealth() <= 0)) return false;
        return entity.ridingEntity == null || aliveTree(entity.ridingEntity);
    }

    static Entity restoreVirtualUnit(KOMEHiredUnitRecord record, World world) {
        return restoreTree(KOMECampaignHealth.movementSnapshot(record, record.movingEntityData), world);
    }

    static Entity restoreTree(NBTTagCompound snapshot, World world) {
        // No UUID stripping, healing, health floor, sanitization or changes to the stored tag.
        if (snapshot.hasKey("Health") && (!(snapshot.getFloat("Health") > 0) || !Float.isFinite(snapshot.getFloat("Health")))) return null;
        if (snapshot.hasKey("HealF") && (!(snapshot.getFloat("HealF") > 0) || !Float.isFinite(snapshot.getFloat("HealF")))) return null;
        Entity entity = EntityList.createEntityFromNBT(snapshot, world);
        if (entity == null) return null;
        if (snapshot.hasKey("Riding", 10)) {
            Entity mount = restoreTree(snapshot.getCompoundTag("Riding"), world);
            if (mount == null) return null;
            entity.mountEntity(mount);
        }
        return entity;
    }

    private static void moveTree(Entity entity, double x, double y, double z, String token, boolean loaded) {
        if (entity.ridingEntity != null) moveTree(entity.ridingEntity, x, y, z, token, loaded);
        if (loaded && entity.addedToChunk) entity.worldObj.getChunkFromChunkCoords(entity.chunkCoordX, entity.chunkCoordZ).setChunkModified();
        entity.setLocationAndAngles(x, y, z, entity.rotationYaw, entity.rotationPitch);
        entity.getEntityData().setString(RECEIPT, token);
        if (loaded) entity.worldObj.updateEntityWithOptionalForce(entity, false);
        entity.worldObj.getChunkFromBlockCoords((int) Math.floor(x), (int) Math.floor(z)).setChunkModified();
    }

    private static boolean spawnTree(World world, Entity entity) {
        Entity mount = entity.ridingEntity;
        if (mount != null && !spawnTree(world, mount)) return false;
        // forceSpawn is not a new unit purchase and does not alter HP or identity.
        entity.forceSpawn = true;
        if (world.spawnEntityInWorld(entity)) return true;
        if (mount != null) removeRestoredTree(world, mount);
        return false;
    }

    private static void removeRestoredTree(World world, Entity entity) {
        if (entity.ridingEntity != null) removeRestoredTree(world, entity.ridingEntity);
        world.removeEntity(entity);
    }

    /** Reject obsolete disk copies of virtual movement entities; allow only receipt-bearing hydration. */
    public static boolean rejectStaleVirtual(KOMEWorldData data, Entity entity) {
        UUID id = KOMEReflection.getEntityUUID(entity);
        KOMEHiredUnitRecord record = data.hiredUnits.get(id);
        if (record == null) {
            // Mounts are part of a purchased unit snapshot, not independent hired-unit records.
            for (KOMEHiredUnitRecord candidate : data.hiredUnits.values()) {
                if (!candidate.seasonReturnVirtual || candidate.movingEntityData == null) continue;
                NBTTagCompound mount = candidate.movingEntityData;
                while (mount.hasKey("Riding", 10)) {
                    mount = mount.getCompoundTag("Riding");
                    if (mount.getLong("UUIDMost") == id.getMostSignificantBits()
                            && mount.getLong("UUIDLeast") == id.getLeastSignificantBits()) record = candidate;
                }
            }
        }
        if (record == null || record.seasonReturnToken.isEmpty()) return false;
        Entity existing = find(id);
        if (existing != null && existing != entity) return true;
        return record.seasonReturnVirtual
            && !record.seasonReturnToken.equals(entity.getEntityData().getString(RECEIPT));
    }

    /** Runs after chunk insertion, before normal hired-unit observation can publish a partial return. */
    public static boolean holdForReset(KOMEWorldData data, LOTREntityNPC npc) {
        KOMESeasonResetState.Return entry = KOMESeasonResetService.entryFor(data, KOMEReflection.getEntityUUID(npc));
        if (entry == null) return KOMESeasonResetService.active(data);
        boolean needsReceipt = (entry.returnRequired || entry.virtualUnits.contains(KOMEReflection.getEntityUUID(npc)))
            && !KOMESeasonResetService.token(data, entry).equals(npc.getEntityData().getString(RECEIPT));
        if (entry.complete && needsReceipt && !KOMESeasonResetService.active(data)) {
            // Replay a saved return if entity chunks lagged the saved journal. Never republish its audit.
            new KOMESeasonResetDeployment(npc.worldObj).apply(data, entry, KOMESeasonResetService.token(data, entry), KOMEReflection.getEntityUUID(npc));
            needsReceipt = !KOMESeasonResetService.token(data, entry).equals(npc.getEntityData().getString(RECEIPT));
        }
        if (KOMESeasonResetService.active(data) || needsReceipt) {
            npc.getNavigator().clearPathEntity();
            npc.setAttackTarget(null);
            return true;
        }
        return false;
    }
}
