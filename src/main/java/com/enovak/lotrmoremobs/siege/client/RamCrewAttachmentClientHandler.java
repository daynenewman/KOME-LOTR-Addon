package com.enovak.lotrmoremobs.siege.client;

import com.enovak.lotrmoremobs.siege.network.RamCrewAttachmentPacket;
import com.enovak.lotrmoremobs.siege.client.render.RamCarrierArmPose;
import com.enovak.lotrmoremobs.siege.ram.EntityBattleRam;
import com.enovak.lotrmoremobs.siege.ram.SiegeRamDiagnostics;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import lotr.common.entity.npc.LOTREntityNPC;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.world.WorldEvent;

/**
 * Reconstructs the ten carrier slots from the client ram transform. The map
 * contains identities only; entity instances are weak and are validated
 * against the current client world on every use.
 */
public class RamCrewAttachmentClientHandler {

    private static final int PENDING_ENTITY_TIMEOUT_TICKS = 100;

    private static final int IMPACT_INERTIA_TICKS = 10;
    private static final float IMPACT_INERTIA_DISTANCE = 0.26F;

    private static final Map<UUID, Attachment> ATTACHMENTS =
            new HashMap<UUID, Attachment>();
    /* Entries exist only between a living-render Pre/Post pair (or, if a
     * render aborts, until the following client tick). */
    private static final Map<LOTREntityNPC, ItemStack> HIDDEN_RENDER_ITEMS =
            new IdentityHashMap<LOTREntityNPC, ItemStack>();

    public static void apply(RamCrewAttachmentPacket packet) {
        if (packet == null) {
            return;
        }
        UUID crewUuid = packet.getCrewUuid();
        SiegeRamDiagnostics.clientOnce(
                "packet:" + crewUuid + ":" + packet.getRamUuid() + ":"
                        + packet.getSlot() + ":" + packet.isAttached(),
                "CLIENT_PACKET", packetFields(packet) + " attached="
                        + packet.isAttached()
        );
        if (!packet.isAttached()) {
            removeAttachment(crewUuid);
            return;
        }
        if (packet.getSlot() < 0
                || packet.getSlot() >= EntityBattleRam.CREW_SLOT_COUNT) {
            return;
        }

        Iterator<Map.Entry<UUID, Attachment>> iterator =
                ATTACHMENTS.entrySet().iterator();
        while (iterator.hasNext()) {
            Attachment existing = iterator.next().getValue();
            if (existing.ramUuid.equals(packet.getRamUuid())
                    && existing.slot == packet.getSlot()) {
                releaseClientEntity(existing);
                iterator.remove();
            }
        }
        ATTACHMENTS.put(crewUuid, new Attachment(packet));
        SiegeRamDiagnostics.clientOnce(
                "attach-add:" + crewUuid + ":" + packet.getRamUuid() + ":"
                        + packet.getSlot(),
                "CLIENT_ATTACH_ADD", packetFields(packet)
        );
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            RamCarrierArmPose.restoreAll();
            restoreAllTemporarilyHiddenItems();
            return;
        }
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        World world = minecraft.theWorld;
        if (world == null) {
            clear();
            return;
        }

        Iterator<Map.Entry<UUID, Attachment>> iterator =
                ATTACHMENTS.entrySet().iterator();
        while (iterator.hasNext()) {
            Attachment attachment = iterator.next().getValue();
            if (attachment.dimensionId != world.provider.dimensionId) {
                SiegeRamDiagnostics.clientOnce(
                        "attach-remove-dimension:" + attachment.crewUuid
                                + ":" + attachment.ramUuid + ":"
                                + attachment.slot,
                        "CLIENT_ATTACH_REMOVED", attachmentFields(attachment)
                                + " reason=DIMENSION_MISMATCH"
                );
                releaseClientEntity(attachment);
                iterator.remove();
                continue;
            }

            EntityBattleRam ram = attachment.getRam(world);
            LOTREntityNPC crew = attachment.getCrew(world);
            if (ram == null || crew == null) {
                if (attachment.wasResolved
                        || ++attachment.unresolvedTicks
                        > PENDING_ENTITY_TIMEOUT_TICKS) {
                    String eventName = attachment.wasResolved
                            ? "CLIENT_ATTACH_REMOVED"
                            : "CLIENT_ATTACH_TIMEOUT";
                    String reason = attachment.wasResolved
                            ? "RESOLVED_ENTITY_MISSING"
                            : "PENDING_TIMEOUT";
                    SiegeRamDiagnostics.clientOnce(
                            "attach-remove:" + eventName + ":"
                                    + attachment.crewUuid + ":"
                                    + attachment.ramUuid + ":"
                                    + attachment.slot,
                            eventName, attachmentFields(attachment)
                                    + " reason=" + reason + " pendingAge="
                                    + attachment.unresolvedTicks
                                    + " ramResolved=" + (ram != null)
                                    + " crewResolved=" + (crew != null)
                    );
                    releaseClientEntity(attachment);
                    iterator.remove();
                }
                continue;
            }
            attachment.unresolvedTicks = 0;
            if (!attachment.wasResolved) {
                SiegeRamDiagnostics.clientOnce(
                        "attach-resolved:" + attachment.crewUuid + ":"
                                + attachment.ramUuid + ":" + attachment.slot,
                        "CLIENT_ATTACH_RESOLVED", attachmentFields(attachment)
                );
            }
            attachment.wasResolved = true;
            if (ram.isDead
                    || crew.isDead
                    || ram.worldObj != world
                    || crew.worldObj != world) {
                SiegeRamDiagnostics.clientOnce(
                        "attach-remove-dead:" + attachment.crewUuid + ":"
                                + attachment.ramUuid + ":" + attachment.slot,
                        "CLIENT_ATTACH_REMOVED", attachmentFields(attachment)
                                + " reason=ENTITY_DEAD_OR_WORLD_MISMATCH"
                );
                releaseClientEntity(attachment);
                iterator.remove();
                continue;
            }
            EntityBattleRam.applyCrewFormation(
                    ram,
                    crew,
                    attachment.slot,
                    true
            );
            smoothClientFormationPresentation(
                    attachment,
                    ram,
                    crew
            );
            updateImpactInertiaPresentation(
                    attachment,
                    ram,
                    crew
            );
            updateCrewLocomotionAnimation(
                    attachment,
                    ram,
                    crew
            );
        }
    }

    /**
     * Keeps the carrier presentation slightly softer than the exact
     * authoritative formation transform. The server still owns the real
     * positions; this only filters the client copy after the exact slot has
     * been reconstructed.
     *
     * <p>Horizontal corrections are eased so packet/pathing micro-corrections
     * do not read as carrier jitter. Large corrections still snap immediately
     * so teleports, fast travel, and chunk re-entry cannot leave ghost crew
     * behind. Y remains exact so feet continue to follow terrain rather than
     * visually hovering on slopes.</p>
     */
    private static void smoothClientFormationPresentation(
            Attachment attachment,
            EntityBattleRam ram,
            LOTREntityNPC crew
    ) {
        if (attachment == null || ram == null || crew == null) {
            return;
        }

        double exactX = crew.posX;
        double exactY = crew.posY;
        double exactZ = crew.posZ;

        if (!attachment.presentationInitialized) {
            attachment.presentationInitialized = true;
            attachment.presentationX = exactX;
            attachment.presentationY = exactY;
            attachment.presentationZ = exactZ;
            attachment.previousPresentationX = exactX;
            attachment.previousPresentationY = exactY;
            attachment.previousPresentationZ = exactZ;
            attachment.presentationYaw = ram.rotationYaw;
            attachment.previousPresentationYaw = ram.rotationYaw;
        } else {
            attachment.previousPresentationX = attachment.presentationX;
            attachment.previousPresentationY = attachment.presentationY;
            attachment.previousPresentationZ = attachment.presentationZ;

            double dx = exactX - attachment.presentationX;
            double dz = exactZ - attachment.presentationZ;
            double errorSq = dx * dx + dz * dz;

            if (errorSq > 2.25D) {
                attachment.presentationX = exactX;
                attachment.presentationZ = exactZ;
                attachment.previousPresentationX = exactX;
                attachment.previousPresentationZ = exactZ;
            } else {
                /*
                 * 0.62 keeps the formation visually attached while still
                 * removing single-tick navigation/network corrections.
                 */
                attachment.presentationX += dx * 0.62D;
                attachment.presentationZ += dz * 0.62D;
            }

            /*
             * Do not low-pass terrain height. A horizontally smoothed carrier
             * should still plant its feet on the ground chosen by the shared
             * formation solver on this tick.
             */
            attachment.presentationY = exactY;
            attachment.previousPresentationY = exactY;

            attachment.previousPresentationYaw =
                    attachment.presentationYaw;

            float yawDelta = wrapDegrees(
                    ram.rotationYaw - attachment.presentationYaw
            );
            float yawStep = yawDelta * 0.38F;
            if (yawStep > 18.0F) {
                yawStep = 18.0F;
            } else if (yawStep < -18.0F) {
                yawStep = -18.0F;
            }
            attachment.presentationYaw =
                    wrapDegrees(attachment.presentationYaw + yawStep);
        }

        crew.setPosition(
                attachment.presentationX,
                attachment.presentationY,
                attachment.presentationZ
        );
        crew.prevPosX = attachment.previousPresentationX;
        crew.prevPosY = attachment.previousPresentationY;
        crew.prevPosZ = attachment.previousPresentationZ;
        crew.lastTickPosX = attachment.previousPresentationX;
        crew.lastTickPosY = attachment.previousPresentationY;
        crew.lastTickPosZ = attachment.previousPresentationZ;

        if (!Float.isNaN(attachment.presentationYaw)) {
            crew.prevRotationYaw =
                    attachment.previousPresentationYaw;
            crew.rotationYaw =
                    attachment.presentationYaw;
            crew.prevRotationYawHead =
                    attachment.previousPresentationYaw;
            crew.rotationYawHead =
                    attachment.presentationYaw;
            crew.prevRenderYawOffset =
                    attachment.previousPresentationYaw;
            crew.renderYawOffset =
                    attachment.presentationYaw;
        }
    }

    /**
     * Drives the NPC's normal biped walk cycle from the ram's actual physical
     * travel instead of from the carrier entity's zeroed motion fields.
     *
     * <p>This applies while following, approaching a gate, backing up, and
     * charging. Speed is filtered before it reaches the limb animation, and
     * retreat travel reverses the stride direction. Each slot receives a
     * small persistent phase offset so ten carriers do not march as a single
     * perfectly mirrored animation. The inner arm is still independently
     * locked by {@link RamCarrierArmPose}.</p>
     */
    private static void updateCrewLocomotionAnimation(
            Attachment attachment,
            EntityBattleRam ram,
            LOTREntityNPC crew
    ) {
        if (attachment == null || ram == null || crew == null) {
            return;
        }

        double dx = ram.posX - ram.lastTickPosX;
        double dz = ram.posZ - ram.lastTickPosZ;
        double horizontalTravel = Math.sqrt(dx * dx + dz * dz);

        if (ram.getRamState()
                == com.enovak.lotrmoremobs.siege.ram.BattleRamState.PAUSED) {
            horizontalTravel = 0.0D;
        }

        /*
         * Ignore tiny packet/noise motion, then filter the travel value.
         * The asymmetric response starts walking promptly but lets it settle
         * more gradually when the ram stops.
         */
        if (horizontalTravel < 0.0025D) {
            horizontalTravel = 0.0D;
        }

        double response = horizontalTravel
                > attachment.filteredTravel
                ? 0.42D
                : 0.24D;

        attachment.filteredTravel +=
                (horizontalTravel - attachment.filteredTravel) * response;

        if (attachment.filteredTravel < 0.0010D) {
            attachment.filteredTravel = 0.0D;
        }

        float targetAmount = (float)Math.min(
                1.0D,
                attachment.filteredTravel * 4.25D
        );

        /*
         * Keep a low but readable leg motion once the heavy ram is genuinely
         * moving. This prevents very slow following from looking like sliding.
         */
        if (attachment.filteredTravel > 0.008D
                && targetAmount < 0.22F) {
            targetAmount = 0.22F;
        }

        crew.prevLimbSwingAmount = crew.limbSwingAmount;
        crew.limbSwingAmount +=
                (targetAmount - crew.limbSwingAmount) * 0.30F;

        if (!attachment.strideInitialized) {
            attachment.strideInitialized = true;
            attachment.stridePhase =
                    attachment.slot * 0.31F;
        }

        /*
         * A small per-slot cadence variation removes the toy-soldier effect
         * without allowing the crew to visibly fall out of formation. Keep
         * the stride phase monotonic even while the ram backs up. Vanilla
         * biped walking is distance-driven rather than signed-direction-
         * driven; reversing this phase at every backup/charge transition was
         * the source of the visible foot snapping in the attack sequence.
         */
        float cadenceScale =
                0.96F + (attachment.slot % 5) * 0.02F;

        attachment.stridePhase +=
                crew.limbSwingAmount
                        * cadenceScale;

        crew.limbSwing = attachment.stridePhase;
    }

    /**
     * Gives the crew a short client-only follow-through when the physical ram
     * hits the gate. The ram has already stopped authoritatively; the carrier
     * render continues a fraction of a block toward the gate and then settles
     * back into its exact formation slot, creating visible body inertia
     * without changing hitboxes, pathing, or siege timing.
     */
    private static void updateImpactInertiaPresentation(
            Attachment attachment,
            EntityBattleRam ram,
            LOTREntityNPC crew
    ) {
        if (attachment == null || ram == null || crew == null) {
            return;
        }

        int impactSerial = ram.getRamImpactSerial();

        if (!attachment.impactSerialInitialized) {
            attachment.impactSerialInitialized = true;
            attachment.lastImpactSerial = impactSerial;
        } else if (impactSerial != attachment.lastImpactSerial) {
            attachment.lastImpactSerial = impactSerial;
            attachment.impactInertiaTicks = IMPACT_INERTIA_TICKS;
        }

        attachment.previousImpactOffset =
                attachment.currentImpactOffset;

        if (attachment.impactInertiaTicks > 0) {
            int elapsed = IMPACT_INERTIA_TICKS
                    - attachment.impactInertiaTicks + 1;
            float progress = elapsed
                    / (float)IMPACT_INERTIA_TICKS;

            float envelope;
            if (progress < 0.30F) {
                envelope = smoothStep(progress / 0.30F);
            } else {
                envelope = 1.0F - smoothStep(
                        (progress - 0.30F) / 0.70F
                );
            }

            float slotVariation =
                    0.94F + (attachment.slot % 3) * 0.03F;

            attachment.currentImpactOffset =
                    IMPACT_INERTIA_DISTANCE
                            * envelope
                            * slotVariation;

            --attachment.impactInertiaTicks;
        } else {
            attachment.currentImpactOffset = 0.0F;
        }

        if (attachment.currentImpactOffset == 0.0F
                && attachment.previousImpactOffset == 0.0F) {
            return;
        }

        double yaw = Math.toRadians(ram.rotationYaw);
        double forwardX = -Math.sin(yaw);
        double forwardZ = Math.cos(yaw);

        double previousYaw = Math.toRadians(ram.prevRotationYaw);
        double previousForwardX = -Math.sin(previousYaw);
        double previousForwardZ = Math.cos(previousYaw);

        crew.setPosition(
                crew.posX
                        + forwardX * attachment.currentImpactOffset,
                crew.posY,
                crew.posZ
                        + forwardZ * attachment.currentImpactOffset
        );

        crew.prevPosX += previousForwardX
                * attachment.previousImpactOffset;
        crew.prevPosZ += previousForwardZ
                * attachment.previousImpactOffset;
        crew.lastTickPosX = crew.prevPosX;
        crew.lastTickPosZ = crew.prevPosZ;
    }

    private static float smoothStep(float value) {
        float clamped = Math.max(0.0F, Math.min(1.0F, value));
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }

    private static float wrapDegrees(float angle) {
        angle %= 360.0F;
        if (angle >= 180.0F) {
            angle -= 360.0F;
        }
        if (angle < -180.0F) {
            angle += 360.0F;
        }
        return angle;
    }

    /**
     * Hides slot 0 only for a carrier whose existing attachment packet still
     * resolves to the exact ram/crew UUID pair in this client world. The item
     * is restored immediately after rendering, so this is presentation-only
     * and cannot alter server equipment or death drops.
     */
    @SubscribeEvent
    public void onRenderLivingPre(RenderLivingEvent.Pre event) {
        if (!(event.entity instanceof LOTREntityNPC)) {
            return;
        }
        LOTREntityNPC crew = (LOTREntityNPC)event.entity;
        boolean validAttachment = isValidClientAttachment(crew);
        if (!validAttachment || HIDDEN_RENDER_ITEMS.containsKey(crew)) {
            logVisibleWeaponWithoutAttachment(crew, validAttachment);
            return;
        }

        Attachment attachment = ATTACHMENTS.get(crew.getUniqueID());
        if (attachment != null) {
            RamCarrierArmPose.begin(
                    event,
                    crew,
                    attachment.slot
            );
        }

        ItemStack heldItem = crew.getHeldItem();
        if (heldItem != null) {
            SiegeRamDiagnostics.clientOnce(
                    "weapon-hidden:" + crew.getUniqueID(),
                    "CLIENT_WEAPON_HIDDEN",
                    "crew=" + crew.getUniqueID() + " entity="
                            + crew.getEntityId() + " item="
                            + describeItem(heldItem)
            );
            HIDDEN_RENDER_ITEMS.put(crew, heldItem);
            crew.setCurrentItemOrArmor(0, (ItemStack)null);
        }
    }

    @SubscribeEvent
    public void onRenderLivingPost(RenderLivingEvent.Post event) {
        if (event.entity instanceof LOTREntityNPC) {
            LOTREntityNPC crew = (LOTREntityNPC)event.entity;
            RamCarrierArmPose.end(crew);
            restoreTemporarilyHiddenItem(crew);
        }
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (event != null
                && event.world != null
                && event.world.isRemote) {
            logClientClear("WORLD_UNLOAD");
            clear();
            SiegeRamDiagnostics.clearClientSession();
        }
    }

    @SubscribeEvent
    public void onClientDisconnect(
            FMLNetworkEvent.ClientDisconnectionFromServerEvent event
    ) {
        logClientClear("DISCONNECT");
        clear();
        SiegeRamDiagnostics.clearClientSession();
    }

    public static void clear() {
        RamCarrierArmPose.restoreAll();
        restoreAllTemporarilyHiddenItems();
        for (Attachment attachment : ATTACHMENTS.values()) {
            releaseClientEntity(attachment);
        }
        ATTACHMENTS.clear();
    }

    private static void removeAttachment(UUID crewUuid) {
        Attachment removed = ATTACHMENTS.remove(crewUuid);
        if (removed != null) {
            SiegeRamDiagnostics.clientOnce(
                    "attach-remove-packet:" + removed.crewUuid + ":"
                            + removed.ramUuid + ":" + removed.slot,
                    "CLIENT_ATTACH_REMOVED", attachmentFields(removed)
                            + " reason=DETACH_PACKET"
            );
            releaseClientEntity(removed);
        }
    }

    private static void releaseClientEntity(Attachment attachment) {
        LOTREntityNPC crew = attachment.crewReference == null
                ? null
                : attachment.crewReference.get();
        if (crew != null && !crew.isDead) {
            RamCarrierArmPose.end(crew);
            restoreTemporarilyHiddenItem(crew);
            crew.noClip = false;
            crew.entityCollisionReduction = 0.0F;
        }
    }

    private static boolean isExactClientRam(
            EntityBattleRam ram,
            World world,
            int ramEntityId,
            int dimensionId
    ) {
        return ram != null
                && !ram.isDead
                && ram.worldObj == world
                && world != null
                && world.isRemote
                && world.provider.dimensionId == dimensionId
                && ram.getEntityId() == ramEntityId;
    }

    private static boolean isValidClientAttachment(LOTREntityNPC crew) {
        if (crew == null || crew.isDead || crew.worldObj == null) {
            return false;
        }
        Attachment attachment = ATTACHMENTS.get(crew.getUniqueID());
        if (attachment == null
                || attachment.dimensionId
                != crew.worldObj.provider.dimensionId
                || !attachment.crewUuid.equals(crew.getUniqueID())
                || attachment.slot < 0
                || attachment.slot >= EntityBattleRam.CREW_SLOT_COUNT) {
            return false;
        }
        LOTREntityNPC resolvedCrew = attachment.getCrew(crew.worldObj);
        EntityBattleRam ram = attachment.getRam(crew.worldObj);
        return resolvedCrew == crew
                && ram != null
                && !ram.isDead
                && ram.worldObj == crew.worldObj;
    }

    private static void logVisibleWeaponWithoutAttachment(
            LOTREntityNPC crew, boolean validAttachment
    ) {
        if (crew == null || crew.getHeldItem() == null || validAttachment) {
            return;
        }
        Attachment attachment = ATTACHMENTS.get(crew.getUniqueID());
        boolean carrierLike = attachment != null
                || crew.getEntityData().hasKey("SiegeRamUUID")
                || crew.getEntityData().getBoolean("SiegeRamCarrier");
        if (!carrierLike) {
            return;
        }
        World world = crew.worldObj;
        EntityBattleRam ram = attachment == null || world == null
                ? null : attachment.getRam(world);
        LOTREntityNPC resolvedCrew = attachment == null || world == null
                ? null : attachment.getCrew(world);
        SiegeRamDiagnostics.clientOnce(
                "visible-weapon-no-attachment:" + crew.getUniqueID(),
                "CLIENT_VISIBLE_WEAPON_NO_ATTACHMENT",
                "crew=" + crew.getUniqueID() + " entity="
                        + crew.getEntityId() + " item="
                        + describeItem(crew.getHeldItem()) + " pending="
                        + (attachment != null) + " ramResolved=" + (ram != null)
                        + " crewResolved=" + (resolvedCrew == crew)
                        + " wasResolved="
                        + (attachment != null && attachment.wasResolved)
                        + " pendingAge="
                        + (attachment == null ? -1 : attachment.unresolvedTicks)
        );
    }

    private static void logClientClear(String reason) {
        if (ATTACHMENTS.isEmpty() && HIDDEN_RENDER_ITEMS.isEmpty()) {
            return;
        }
        SiegeRamDiagnostics.client(
                "CLIENT_ATTACH_CLEAR_WORLD",
                "reason=" + reason + " attachments=" + ATTACHMENTS.size()
                        + " hiddenItems=" + HIDDEN_RENDER_ITEMS.size()
        );
    }

    private static String packetFields(RamCrewAttachmentPacket packet) {
        return "ram=" + packet.getRamUuid() + " ramEntity="
                + packet.getRamEntityId() + " crew=" + packet.getCrewUuid()
                + " crewEntity=" + packet.getCrewEntityId() + " slot="
                + packet.getSlot();
    }

    private static String attachmentFields(Attachment attachment) {
        return "ram=" + attachment.ramUuid + " ramEntity="
                + attachment.ramEntityId + " crew=" + attachment.crewUuid
                + " crewEntity=" + attachment.crewEntityId + " slot="
                + attachment.slot;
    }

    private static String describeItem(ItemStack item) {
        if (item == null || item.getItem() == null) {
            return "null";
        }
        return item.getItem().getUnlocalizedName() + ":" + item.getItemDamage();
    }

    private static void restoreTemporarilyHiddenItem(LOTREntityNPC crew) {
        ItemStack heldItem = HIDDEN_RENDER_ITEMS.remove(crew);
        if (heldItem != null
                && crew != null
                && !crew.isDead
                && crew.getHeldItem() == null) {
            crew.setCurrentItemOrArmor(0, heldItem);
        }
    }

    private static void restoreAllTemporarilyHiddenItems() {
        if (HIDDEN_RENDER_ITEMS.isEmpty()) {
            return;
        }
        Map<LOTREntityNPC, ItemStack> pending =
                new IdentityHashMap<LOTREntityNPC, ItemStack>(
                        HIDDEN_RENDER_ITEMS
                );
        HIDDEN_RENDER_ITEMS.clear();
        for (Map.Entry<LOTREntityNPC, ItemStack> entry
                : pending.entrySet()) {
            LOTREntityNPC crew = entry.getKey();
            if (crew != null
                    && !crew.isDead
                    && crew.getHeldItem() == null) {
                crew.setCurrentItemOrArmor(0, entry.getValue());
            }
        }
    }

    private static final class Attachment {
        private final int dimensionId;
        private final int ramEntityId;
        private final UUID ramUuid;
        private final int crewEntityId;
        private final UUID crewUuid;
        private final int slot;
        private WeakReference<EntityBattleRam> ramReference;
        private WeakReference<LOTREntityNPC> crewReference;
        private int unresolvedTicks;
        private boolean wasResolved;

        /*
         * Client-only presentation state. None of these values are sent to
         * the server or written into NPC/ram persistence.
         */
        private boolean presentationInitialized;
        private double presentationX;
        private double presentationY;
        private double presentationZ;
        private double previousPresentationX;
        private double previousPresentationY;
        private double previousPresentationZ;
        private float presentationYaw = Float.NaN;
        private float previousPresentationYaw = Float.NaN;
        private double filteredTravel;
        private boolean strideInitialized;
        private float stridePhase;
        private boolean impactSerialInitialized;
        private int lastImpactSerial;
        private int impactInertiaTicks;
        private float currentImpactOffset;
        private float previousImpactOffset;

        private Attachment(RamCrewAttachmentPacket packet) {
            dimensionId = packet.getDimensionId();
            ramEntityId = packet.getRamEntityId();
            ramUuid = packet.getRamUuid();
            crewEntityId = packet.getCrewEntityId();
            crewUuid = packet.getCrewUuid();
            slot = packet.getSlot();
        }

        private EntityBattleRam getRam(World world) {
            EntityBattleRam cached = ramReference == null
                    ? null
                    : ramReference.get();
            if (isExactClientRam(
                    cached,
                    world,
                    ramEntityId,
                    dimensionId
            )) {
                return cached;
            }
            if (world == null || world.provider.dimensionId != dimensionId) {
                return null;
            }
            Entity entity = world.getEntityByID(ramEntityId);
            if (entity instanceof EntityBattleRam) {
                cached = (EntityBattleRam)entity;
                if (isExactClientRam(
                        cached,
                        world,
                        ramEntityId,
                        dimensionId
                )) {
                    ramReference = new WeakReference<EntityBattleRam>(cached);
                    return cached;
                }
            }
            return null;
        }

        private LOTREntityNPC getCrew(World world) {
            LOTREntityNPC cached = crewReference == null
                    ? null
                    : crewReference.get();
            if (cached != null) {
                return world.getEntityByID(crewEntityId) == cached
                        && crewUuid.equals(cached.getUniqueID())
                        ? cached
                        : null;
            }
            Entity entity = world.getEntityByID(crewEntityId);
            if (entity instanceof LOTREntityNPC
                    && crewUuid.equals(entity.getUniqueID())) {
                cached = (LOTREntityNPC)entity;
                crewReference = new WeakReference<LOTREntityNPC>(cached);
                return cached;
            }
            return null;
        }
    }
}
