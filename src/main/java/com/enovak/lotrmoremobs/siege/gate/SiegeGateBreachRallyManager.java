package com.enovak.lotrmoremobs.siege.gate;

import com.enovak.lotrmoremobs.config.MumakilConfig;
import com.enovak.lotrmoremobs.siege.ram.EntityBattleRam;
import com.enovak.lotrmoremobs.siege.tile.TileEntitySiegeGate;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import lotr.common.entity.npc.LOTREntityNPC;
import lotr.common.fac.LOTRFaction;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;

/**
 * Short-lived breach reaction for nearby LOTR combat NPCs.
 *
 * This does not add or remove AI tasks and never assigns an attack target. A
 * newly breached gate only gives nearby allied defenders and hostile attackers
 * a temporary navigator destination. As soon as normal LOTR AI acquires a
 * combat target, this helper stops touching that NPC.
 */
public final class SiegeGateBreachRallyManager {

    public static final double RALLY_RADIUS = 32.0D;
    public static final int RALLY_DURATION_TICKS = 120;
    private static final int RALLY_REPATH_INTERVAL_TICKS = 10;
    private static final double RALLY_SPEED = 1.25D;
    private static final double ARRIVAL_DISTANCE_SQ = 3.0D * 3.0D;
    private static final double ATTACKER_CROSS_DISTANCE = 2.0D;
    private static final double DEFENDER_HOLD_DISTANCE = 1.25D;
    private static final int MAX_RALLY_NPCS_PER_BREACH = 64;

    private static final Map<World, Map<UUID, RallyState>> RALLIES_BY_WORLD =
            new WeakHashMap<World, Map<UUID, RallyState>>();

    private SiegeGateBreachRallyManager() {
    }

    public static void beginRally(TileEntitySiegeGate gate) {
        if (!MumakilConfig.enableSiegeGateBreachNpcRally) {
            return;
        }

        if (gate == null
                || gate.getWorldObj() == null
                || gate.getWorldObj().isRemote
                || gate.getGateFaction() == null
                || gate.getGateParts().isEmpty()) {
            return;
        }

        World world = gate.getWorldObj();
        LOTRFaction gateFaction = gate.getGateFaction();
        BreachGeometry geometry = BreachGeometry.from(gate);
        if (geometry == null) {
            return;
        }

        AxisAlignedBB searchBox = AxisAlignedBB.getBoundingBox(
                geometry.centerX - RALLY_RADIUS,
                geometry.feetY - 8.0D,
                geometry.centerZ - RALLY_RADIUS,
                geometry.centerX + RALLY_RADIUS,
                geometry.feetY + 12.0D,
                geometry.centerZ + RALLY_RADIUS
        );

        List nearby = world.getEntitiesWithinAABB(
                LOTREntityNPC.class,
                searchBox
        );
        if (nearby == null || nearby.isEmpty()) {
            return;
        }

        Map<UUID, RallyState> worldRallies = getWorldRallies(world, true);
        long now = world.getTotalWorldTime();
        int enrolled = 0;

        for (Object object : nearby) {
            if (enrolled >= MAX_RALLY_NPCS_PER_BREACH) {
                break;
            }
            if (!(object instanceof LOTREntityNPC)) {
                continue;
            }

            LOTREntityNPC npc = (LOTREntityNPC)object;
            RallyRole role = classify(npc, gateFaction);
            if (role == null || !canRally(npc)) {
                continue;
            }

            RallyDestination destination = geometry.destinationFor(npc, role);
            if (destination == null) {
                continue;
            }

            RallyState existing = worldRallies.get(npc.getUniqueID());
            if (existing != null
                    && existing.expiresAtTick > now
                    && existing.distanceSqFrom(npc)
                    <= destination.distanceSqFrom(npc)) {
                continue;
            }

            RallyState state = new RallyState(
                    npc,
                    destination.x,
                    destination.y,
                    destination.z,
                    now + RALLY_DURATION_TICKS,
                    now
            );

            if (!issueMove(npc, state)) {
                /*
                 * A cross-through target can be temporarily unreachable even
                 * though the breach itself is approachable. Fall back to the
                 * actual opening rather than enrolling an NPC in a dead path.
                 */
                state.targetX = geometry.centerX;
                state.targetY = geometry.feetY;
                state.targetZ = geometry.centerZ;
                if (!issueMove(npc, state)) {
                    continue;
                }
            }

            state.nextRepathTick = now + RALLY_REPATH_INTERVAL_TICKS;
            worldRallies.put(npc.getUniqueID(), state);
            ++enrolled;
        }

        if (worldRallies.isEmpty()) {
            RALLIES_BY_WORLD.remove(world);
        }
    }

    public static void process(World world) {
        if (world == null || world.isRemote) {
            return;
        }

        if (!MumakilConfig.enableSiegeGateBreachNpcRally) {
            clearWorld(world);
            return;
        }

        Map<UUID, RallyState> worldRallies = getWorldRallies(world, false);
        if (worldRallies == null || worldRallies.isEmpty()) {
            return;
        }

        long now = world.getTotalWorldTime();
        Iterator<Map.Entry<UUID, RallyState>> iterator =
                worldRallies.entrySet().iterator();

        while (iterator.hasNext()) {
            RallyState state = iterator.next().getValue();
            LOTREntityNPC npc = state == null ? null : state.npc.get();

            if (npc == null
                    || npc.worldObj != world
                    || npc.isDead
                    || !npc.isEntityAlive()
                    || now >= state.expiresAtTick
                    || npc.getAttackTarget() != null
                    || EntityBattleRam.hasRamCrewTag(npc)) {
                iterator.remove();
                continue;
            }

            if (state.distanceSqFrom(npc) <= ARRIVAL_DISTANCE_SQ) {
                iterator.remove();
                continue;
            }

            if (now >= state.nextRepathTick) {
                issueMove(npc, state);
                state.nextRepathTick = now + RALLY_REPATH_INTERVAL_TICKS;
            }
        }

        if (worldRallies.isEmpty()) {
            RALLIES_BY_WORLD.remove(world);
        }
    }

    public static void clearWorld(World world) {
        if (world != null) {
            RALLIES_BY_WORLD.remove(world);
        }
    }

    private static RallyRole classify(
            LOTREntityNPC npc,
            LOTRFaction gateFaction
    ) {
        if (npc == null || gateFaction == null) {
            return null;
        }

        LOTRFaction npcFaction = npc.getFaction();
        if (npcFaction == null) {
            return null;
        }

        if (npcFaction == gateFaction || npcFaction.isAlly(gateFaction)) {
            return RallyRole.DEFENDER;
        }

        if (npcFaction.isBadRelation(gateFaction)
                || gateFaction.isBadRelation(npcFaction)) {
            return RallyRole.ATTACKER;
        }

        return null;
    }

    private static boolean canRally(LOTREntityNPC npc) {
        if (npc == null
                || npc.isDead
                || !npc.isEntityAlive()
                || npc.getAttackTarget() != null
                || EntityBattleRam.hasRamCrewTag(npc)
                || npc.ridingEntity != null) {
            return false;
        }

        /*
         * Keep civilians/traders out without depending on every concrete LOTR
         * NPC class. Hired units are intentionally eligible: a nearby hired
         * warrior is still part of the battle, but active combat always wins.
         */
        if (npc.hiredNPCInfo != null && npc.hiredNPCInfo.isActive) {
            return true;
        }

        ItemStack held = npc.getHeldItem();
        if (held != null) {
            return true;
        }

        IAttributeInstance attackDamage =
                npc.getEntityAttribute(SharedMonsterAttributes.attackDamage);
        return attackDamage != null && attackDamage.getAttributeValue() > 2.0D;
    }

    private static boolean issueMove(LOTREntityNPC npc, RallyState state) {
        return npc != null
                && state != null
                && npc.getNavigator().tryMoveToXYZ(
                        state.targetX,
                        state.targetY,
                        state.targetZ,
                        RALLY_SPEED
                );
    }

    private static Map<UUID, RallyState> getWorldRallies(
            World world,
            boolean create
    ) {
        Map<UUID, RallyState> result = RALLIES_BY_WORLD.get(world);
        if (result == null && create) {
            result = new HashMap<UUID, RallyState>();
            RALLIES_BY_WORLD.put(world, result);
        }
        return result;
    }

    private enum RallyRole {
        DEFENDER,
        ATTACKER
    }

    private static final class RallyState {
        private final WeakReference<LOTREntityNPC> npc;
        private double targetX;
        private double targetY;
        private double targetZ;
        private final long expiresAtTick;
        private long nextRepathTick;

        private RallyState(
                LOTREntityNPC npc,
                double targetX,
                double targetY,
                double targetZ,
                long expiresAtTick,
                long nextRepathTick
        ) {
            this.npc = new WeakReference<LOTREntityNPC>(npc);
            this.targetX = targetX;
            this.targetY = targetY;
            this.targetZ = targetZ;
            this.expiresAtTick = expiresAtTick;
            this.nextRepathTick = nextRepathTick;
        }

        private double distanceSqFrom(LOTREntityNPC npc) {
            double dx = npc.posX - targetX;
            double dy = npc.posY - targetY;
            double dz = npc.posZ - targetZ;
            return dx * dx + dy * dy + dz * dz;
        }
    }

    private static final class RallyDestination {
        private final double x;
        private final double y;
        private final double z;

        private RallyDestination(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        private double distanceSqFrom(LOTREntityNPC npc) {
            double dx = npc.posX - x;
            double dy = npc.posY - y;
            double dz = npc.posZ - z;
            return dx * dx + dy * dy + dz * dz;
        }
    }

    private static final class BreachGeometry {
        private final GateOrientation orientation;
        private final double centerX;
        private final double feetY;
        private final double centerZ;

        private BreachGeometry(
                GateOrientation orientation,
                double centerX,
                double feetY,
                double centerZ
        ) {
            this.orientation = orientation;
            this.centerX = centerX;
            this.feetY = feetY;
            this.centerZ = centerZ;
        }

        private static BreachGeometry from(TileEntitySiegeGate gate) {
            List<GatePartData> parts = gate.getGateParts();
            if (parts == null || parts.isEmpty()) {
                return null;
            }

            int minX = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int minY = Integer.MAX_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxZ = Integer.MIN_VALUE;

            for (GatePartData part : parts) {
                minX = Math.min(minX, part.getRelativeX());
                maxX = Math.max(maxX, part.getRelativeX());
                minY = Math.min(minY, part.getRelativeY());
                minZ = Math.min(minZ, part.getRelativeZ());
                maxZ = Math.max(maxZ, part.getRelativeZ());
            }

            GateOrientation orientation = gate.getGateOrientation();
            if (orientation == null) {
                orientation = maxX - minX >= maxZ - minZ
                        ? GateOrientation.WIDTH_X
                        : GateOrientation.WIDTH_Z;
            }

            return new BreachGeometry(
                    orientation,
                    gate.xCoord + (minX + maxX + 1) * 0.5D,
                    gate.yCoord + minY,
                    gate.zCoord + (minZ + maxZ + 1) * 0.5D
            );
        }

        private RallyDestination destinationFor(
                LOTREntityNPC npc,
                RallyRole role
        ) {
            double x = centerX;
            double z = centerZ;

            if (orientation == GateOrientation.WIDTH_X) {
                double side = npc.posZ < centerZ ? -1.0D : 1.0D;
                z += role == RallyRole.ATTACKER
                        ? -side * ATTACKER_CROSS_DISTANCE
                        : side * DEFENDER_HOLD_DISTANCE;
            } else {
                double side = npc.posX < centerX ? -1.0D : 1.0D;
                x += role == RallyRole.ATTACKER
                        ? -side * ATTACKER_CROSS_DISTANCE
                        : side * DEFENDER_HOLD_DISTANCE;
            }

            return new RallyDestination(x, feetY, z);
        }
    }
}
