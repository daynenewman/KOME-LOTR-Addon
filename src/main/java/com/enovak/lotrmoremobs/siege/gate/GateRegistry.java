package com.enovak.lotrmoremobs.siege.gate;

import com.enovak.lotrmoremobs.siege.tile.TileEntitySiegeGate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public final class GateRegistry {

    private static final Map<World, WorldLinks> WORLD_LINKS =
            new WeakHashMap<World, WorldLinks>();

    private GateRegistry() {
    }

    public static synchronized void registerController(
            TileEntitySiegeGate controller
    ) {
        if (controller == null || controller.getWorldObj() == null) {
            return;
        }

        World world = controller.getWorldObj();
        WorldLinks links = getWorldLinks(world, true);
        BlockPosition controllerPosition = new BlockPosition(
                controller.xCoord,
                controller.yCoord,
                controller.zCoord
        );
        removeControllerLinks(links, controllerPosition);

        for (GatePartData part : controller.getGateParts()) {
            if (!part.hasValidAbsolutePosition(
                    controller.xCoord,
                    controller.yCoord,
                    controller.zCoord
            )) {
                continue;
            }

            linkPart(
                    links,
                    new BlockPosition(
                            part.getAbsoluteX(controller.xCoord),
                            part.getAbsoluteY(controller.yCoord),
                            part.getAbsoluteZ(controller.zCoord)
                    ),
                    controllerPosition
            );
        }
    }

    public static synchronized TileEntitySiegeGate getController(
            World world,
            int partX,
            int partY,
            int partZ
    ) {
        WorldLinks links = getWorldLinks(world, false);
        if (links == null) {
            return null;
        }

        BlockPosition partPosition = new BlockPosition(
                partX,
                partY,
                partZ
        );
        BlockPosition controllerPosition =
                links.controllersByPart.get(partPosition);
        if (controllerPosition == null) {
            return null;
        }

        if (!world.blockExists(
                controllerPosition.x,
                controllerPosition.y,
                controllerPosition.z
        )) {
            removePartLink(links, partPosition);
            return null;
        }

        TileEntity controller = world.getTileEntity(
                controllerPosition.x,
                controllerPosition.y,
                controllerPosition.z
        );
        if (controller instanceof TileEntitySiegeGate) {
            return (TileEntitySiegeGate)controller;
        }

        removePartLink(links, partPosition);
        return null;
    }

    public static synchronized SiegeGateOwnershipData.DurablePartOwner
            getDurablePartOwner(
                    World world,
                    int partX,
                    int partY,
                    int partZ
            ) {
        SiegeGateOwnershipData data =
                SiegeGateOwnershipData.get(world, false);
        return data == null || world == null
                ? null
                : data.findPartOwner(
                        world.provider.dimensionId,
                        partX,
                        partY,
                        partZ
                );
    }

    public static synchronized boolean hasDurableController(
            World world,
            int controllerX,
            int controllerY,
            int controllerZ
    ) {
        SiegeGateOwnershipData data =
                SiegeGateOwnershipData.get(world, false);
        return data != null
                && world != null
                && data.hasControllerRecord(
                        world.provider.dimensionId,
                        controllerX,
                        controllerY,
                        controllerZ
                );
    }

    public static synchronized boolean isPartOwnedBy(
            World world,
            int partX,
            int partY,
            int partZ,
            TileEntitySiegeGate controller
    ) {
        return controller != null
                && getController(world, partX, partY, partZ)
                == controller;
    }

    public static synchronized void unregisterGatePart(
            World world,
            int partX,
            int partY,
            int partZ
    ) {
        WorldLinks links = getWorldLinks(world, false);
        if (links == null) {
            return;
        }

        removePartLink(
                links,
                new BlockPosition(partX, partY, partZ)
        );
    }

    public static synchronized void unregisterController(
            World world,
            int controllerX,
            int controllerY,
            int controllerZ
    ) {
        WorldLinks links = getWorldLinks(world, false);
        if (links == null) {
            return;
        }

        removeControllerLinks(
                links,
                new BlockPosition(
                        controllerX,
                        controllerY,
                        controllerZ
                )
        );
    }

    public static synchronized void notifyPartChunkAvailabilityChanged(
            World world,
            int chunkX,
            int chunkZ
    ) {
        if (world == null || !world.isRemote) {
            return;
        }
        WorldLinks links = getWorldLinks(world, false);
        if (links == null) {
            return;
        }
        Set<BlockPosition> positions = links.controllersByPartChunk.get(
                chunkKey(chunkX, chunkZ)
        );
        if (positions == null || positions.isEmpty()) {
            return;
        }
        for (BlockPosition position
                : new HashSet<BlockPosition>(positions)) {
            if (!world.blockExists(position.x, position.y, position.z)) {
                continue;
            }
            TileEntity tileEntity = world.getTileEntity(
                    position.x,
                    position.y,
                    position.z
            );
            if (tileEntity instanceof TileEntitySiegeGate) {
                ((TileEntitySiegeGate)tileEntity)
                        .onPartChunkAvailabilityChanged();
            }
        }
    }

    public static synchronized void clearWorld(World world) {
        if (world != null) {
            WORLD_LINKS.remove(world);
        }
    }

    public static synchronized List<TileEntitySiegeGate>
            getLoadedControllers(World world) {
        List<TileEntitySiegeGate> controllers =
                new ArrayList<TileEntitySiegeGate>();
        WorldLinks links = getWorldLinks(world, false);
        if (links == null || world == null) {
            return controllers;
        }

        for (BlockPosition position
                : new HashSet<BlockPosition>(
                        links.partsByController.keySet()
                )) {
            if (!world.blockExists(
                    position.x,
                    position.y,
                    position.z
            )) {
                continue;
            }

            TileEntity tileEntity = world.getTileEntity(
                    position.x,
                    position.y,
                    position.z
            );
            if (tileEntity instanceof TileEntitySiegeGate) {
                controllers.add((TileEntitySiegeGate)tileEntity);
            }
        }

        return controllers;
    }

    public static synchronized List<TileEntitySiegeGate>
            getLoadedControllersWithin(
                    World world,
                    double x,
                    double y,
                    double z,
                    double radius
            ) {
        List<TileEntitySiegeGate> controllers =
                new ArrayList<TileEntitySiegeGate>();
        WorldLinks links = getWorldLinks(world, false);
        if (links == null || radius < 0.0D) {
            return controllers;
        }
        double radiusSq = radius * radius;
        for (BlockPosition position
                : new HashSet<BlockPosition>(
                        links.partsByController.keySet()
                )) {
            double dx = position.x + 0.5D - x;
            double dy = position.y + 0.5D - y;
            double dz = position.z + 0.5D - z;
            if (dx * dx + dy * dy + dz * dz > radiusSq
                    || !world.blockExists(
                            position.x,
                            position.y,
                            position.z
                    )) {
                continue;
            }
            TileEntity tileEntity = world.getTileEntity(
                    position.x,
                    position.y,
                    position.z
            );
            if (tileEntity instanceof TileEntitySiegeGate) {
                controllers.add((TileEntitySiegeGate)tileEntity);
            }
        }
        return controllers;
    }

    private static void linkPart(
            WorldLinks links,
            BlockPosition partPosition,
            BlockPosition controllerPosition
    ) {
        BlockPosition previousController =
                links.controllersByPart.put(
                        partPosition,
                        controllerPosition
                );
        if (previousController != null
                && !previousController.equals(controllerPosition)) {
            Set<BlockPosition> previousParts =
                    links.partsByController.get(previousController);
            if (previousParts != null) {
                previousParts.remove(partPosition);
                removeControllerFromPartChunkIfUnused(
                        links,
                        previousController,
                        partPosition.x >> 4,
                        partPosition.z >> 4,
                        previousParts
                );
                if (previousParts.isEmpty()) {
                    links.partsByController.remove(previousController);
                }
            }
        }

        Set<BlockPosition> controllerParts =
                links.partsByController.get(controllerPosition);
        if (controllerParts == null) {
            controllerParts = new HashSet<BlockPosition>();
            links.partsByController.put(
                    controllerPosition,
                    controllerParts
            );
        }
        controllerParts.add(partPosition);
        long partChunkKey = chunkKey(partPosition.x >> 4, partPosition.z >> 4);
        Set<BlockPosition> chunkControllers =
                links.controllersByPartChunk.get(partChunkKey);
        if (chunkControllers == null) {
            chunkControllers = new HashSet<BlockPosition>();
            links.controllersByPartChunk.put(
                    Long.valueOf(partChunkKey),
                    chunkControllers
            );
        }
        chunkControllers.add(controllerPosition);
    }

    private static void removePartLink(
            WorldLinks links,
            BlockPosition partPosition
    ) {
        BlockPosition controllerPosition =
                links.controllersByPart.remove(partPosition);
        if (controllerPosition == null) {
            return;
        }

        Set<BlockPosition> controllerParts =
                links.partsByController.get(controllerPosition);
        if (controllerParts != null) {
            controllerParts.remove(partPosition);
            removeControllerFromPartChunkIfUnused(
                    links,
                    controllerPosition,
                    partPosition.x >> 4,
                    partPosition.z >> 4,
                    controllerParts
            );
            if (controllerParts.isEmpty()) {
                links.partsByController.remove(controllerPosition);
            }
        }
    }

    private static void removeControllerLinks(
            WorldLinks links,
            BlockPosition controllerPosition
    ) {
        Set<BlockPosition> controllerParts =
                links.partsByController.remove(controllerPosition);
        if (controllerParts == null) {
            return;
        }

        for (BlockPosition partPosition : controllerParts) {
            if (controllerPosition.equals(
                    links.controllersByPart.get(partPosition)
            )) {
                links.controllersByPart.remove(partPosition);
            }
        }
        Set<Long> partChunks = new HashSet<Long>();
        for (BlockPosition partPosition : controllerParts) {
            partChunks.add(Long.valueOf(chunkKey(
                    partPosition.x >> 4,
                    partPosition.z >> 4
            )));
        }
        for (Long partChunk : partChunks) {
            Set<BlockPosition> chunkControllers =
                    links.controllersByPartChunk.get(partChunk);
            if (chunkControllers != null) {
                chunkControllers.remove(controllerPosition);
                if (chunkControllers.isEmpty()) {
                    links.controllersByPartChunk.remove(partChunk);
                }
            }
        }
    }

    private static void removeControllerFromPartChunkIfUnused(
            WorldLinks links,
            BlockPosition controllerPosition,
            int chunkX,
            int chunkZ,
            Set<BlockPosition> remainingParts
    ) {
        for (BlockPosition remaining : remainingParts) {
            if ((remaining.x >> 4) == chunkX
                    && (remaining.z >> 4) == chunkZ) {
                return;
            }
        }
        long key = chunkKey(chunkX, chunkZ);
        Set<BlockPosition> chunkControllers =
                links.controllersByPartChunk.get(key);
        if (chunkControllers != null) {
            chunkControllers.remove(controllerPosition);
            if (chunkControllers.isEmpty()) {
                links.controllersByPartChunk.remove(key);
            }
        }
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long)chunkX << 32) | (chunkZ & 0xffffffffL);
    }

    private static WorldLinks getWorldLinks(World world, boolean create) {
        if (world == null) {
            return null;
        }

        WorldLinks links = WORLD_LINKS.get(world);
        if (links == null && create) {
            links = new WorldLinks();
            WORLD_LINKS.put(world, links);
        }
        return links;
    }

    private static final class WorldLinks {
        private final Map<BlockPosition, BlockPosition>
                controllersByPart =
                new HashMap<BlockPosition, BlockPosition>();
        private final Map<BlockPosition, Set<BlockPosition>>
                partsByController =
                new HashMap<BlockPosition, Set<BlockPosition>>();
        private final Map<Long, Set<BlockPosition>>
                controllersByPartChunk =
                new HashMap<Long, Set<BlockPosition>>();
    }

    private static final class BlockPosition {
        private final int x;
        private final int y;
        private final int z;

        private BlockPosition(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof BlockPosition)) {
                return false;
            }

            BlockPosition position = (BlockPosition)other;
            return x == position.x
                    && y == position.y
                    && z == position.z;
        }

        @Override
        public int hashCode() {
            int result = x;
            result = 31 * result + y;
            result = 31 * result + z;
            return result;
        }
    }
}
