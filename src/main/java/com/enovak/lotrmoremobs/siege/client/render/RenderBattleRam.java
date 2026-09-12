package com.enovak.lotrmoremobs.siege.client.render;

import com.enovak.lotrmoremobs.siege.client.model.GeoModelBattleRam;
import com.enovak.lotrmoremobs.siege.ram.EntityBattleRam;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import cpw.mods.fml.common.FMLLog;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import net.geckominecraft.client.renderer.GlStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import lotr.common.entity.npc.LOTREntityNPC;
import lotr.common.fac.LOTRFaction;
import com.enovak.lotrmoremobs.siege.ram.BattleRamCrewTypes;
import org.apache.commons.lang3.tuple.Pair;
import software.bernie.geckolib3.core.event.predicate.AnimationEvent;
import software.bernie.geckolib3.geo.render.built.GeoBone;
import software.bernie.geckolib3.geo.render.built.GeoModel;
import software.bernie.geckolib3.model.provider.data.EntityModelData;
import software.bernie.geckolib3.renderers.geo.GeoEntityRenderer;

/**
 * Safe Forge 1.7.10 Geo renderer for the user-authored Battle Ram.
 *
 * <p>This deliberately bypasses GeckoLib's normal controller/runtime path,
 * matching the proven Mumakil renderer. The cached Geo model contains mutable
 * bones, so every render begins from and finally restores the captured neutral
 * Master pose.</p>
 */
public class RenderBattleRam extends GeoEntityRenderer<EntityBattleRam>
        implements IResourceManagerReloadListener {

    private static final String MASTER_BONE = "Master";
    private static final String ATTACK_ANIMATION_NAME = "Attacking";
    private static final float ATTACK_SOURCE_LENGTH_SECONDS = 12.0F;
    private static final float ATTACK_CONTACT_SOURCE_SECONDS = 9.5F;
    private static final float DEGREES_TO_RADIANS = 0.017453292F;

    /*
     * Gecko's MatrixStack converts GeoBone translations from authored pixels
     * to world units with /16 and handles its own X convention. The authored
     * model faces negative Z, so the Master channel's negative Z contact pose
     * must remain negative; applying another Z sign conversion reverses the
     * authored pullback and strike.
     */
    private static final float AUTHORED_POSITION_X_SIGN = 1.0F;
    private static final float AUTHORED_POSITION_Y_SIGN = 1.0F;
    private static final float AUTHORED_POSITION_Z_SIGN = 1.0F;

    /* Root presentation transform, kept centralized for runtime tuning. */
    private static final float MODEL_SCALE = 1.0F;
    private static final float MODEL_GROUND_OFFSET = 0.0F;

    private static final float MOVEMENT_BOB_MAX = 0.035F;
    private static final float MOVEMENT_BOB_RISE_RESPONSE = 0.42F;
    private static final float MOVEMENT_BOB_FALL_RESPONSE = 0.25F;

    private final Map<GeoModel, Map<String, GeoBone>> bonesByModel =
            new IdentityHashMap<GeoModel, Map<String, GeoBone>>();
    private final Map<GeoBone, BonePose> neutralPoses =
            new IdentityHashMap<GeoBone, BonePose>();
    private final Map<EntityBattleRam, RamBobState> ramBobStates =
            new WeakHashMap<EntityBattleRam, RamBobState>();

    private boolean attemptedAttackLoad;
    private AuthoredAttackAnimation attackAnimation;

    private World hirePreviewWorld;
    private LOTRFaction hirePreviewFaction;
    private LOTREntityNPC hirePreviewCrew;
    private Render hirePreviewCrewRenderer;

    public RenderBattleRam() {
        super(new GeoModelBattleRam());
        this.shadowSize = 1.2F;

        IResourceManager resourceManager =
                Minecraft.getMinecraft().getResourceManager();
        if (resourceManager instanceof IReloadableResourceManager) {
            ((IReloadableResourceManager)resourceManager)
                    .registerReloadListener(this);
        }
    }

    @Override
    public void onResourceManagerReload(IResourceManager resourceManager) {
        this.bonesByModel.clear();
        this.neutralPoses.clear();
        this.ramBobStates.clear();
        this.attemptedAttackLoad = false;
        this.attackAnimation = null;
        this.hirePreviewWorld = null;
        this.hirePreviewFaction = null;
        this.hirePreviewCrew = null;
        this.hirePreviewCrewRenderer = null;
    }

    /** Never enter GeoEntityRenderer's unsafe 1.7.10 raw render path. */
    public void doRender(
            Entity entity,
            double x,
            double y,
            double z,
            float entityYaw,
            float partialTicks
    ) {
        if (entity instanceof EntityBattleRam) {
            doRenderBattleRam(
                    (EntityBattleRam)entity,
                    x,
                    y,
                    z,
                    entityYaw,
                    partialTicks
            );
        }
    }

    /** Also covers inventory/spawner entry points without calling super. */
    public void doRender(
            EntityLivingBase entity,
            double x,
            double y,
            double z,
            float entityYaw,
            float partialTicks
    ) {
        if (entity instanceof EntityBattleRam) {
            doRenderBattleRam(
                    (EntityBattleRam)entity,
                    x,
                    y,
                    z,
                    entityYaw,
                    partialTicks
            );
        }
    }

    private void doRenderBattleRam(
            EntityBattleRam ram,
            double x,
            double y,
            double z,
            float entityYaw,
            float partialTicks
    ) {
        if (ram == null || ram.isInvisible()) {
            return;
        }

        GeoModel geoModel = this.modelProvider.getModel(
                this.modelProvider.getModelLocation(ram)
        );
        if (geoModel == null) {
            return;
        }

        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(x, y, z);

            boolean sitting = ram.ridingEntity != null
                    && ram.ridingEntity.shouldRiderSit();
            Pair<Float, Float> rotations = this.calculateRotations(
                    ram,
                    partialTicks,
                    sitting
            );
            float ageInTicks = ram.ticksExisted + partialTicks;
            this.applyRotations(
                    ram,
                    ageInTicks,
                    rotations.getKey().floatValue(),
                    partialTicks
            );

            EntityModelData modelData = new EntityModelData();
            modelData.isSitting = sitting;
            modelData.isChild = false;
            modelData.headPitch = 0.0F;
            modelData.netHeadYaw = 0.0F;
            AnimationEvent<EntityBattleRam> event =
                    new AnimationEvent<EntityBattleRam>(
                            ram,
                            0.0F,
                            0.0F,
                            partialTicks,
                            false,
                            Collections.<Object>singletonList(modelData)
                    );
            this.modelProvider.setLivingAnimations(
                    ram,
                    this.getUniqueID(ram),
                    event
            );

            GlStateManager.pushMatrix();
            try {
                float movementBob = getMovementBob(
                        ram,
                        partialTicks
                );
                GlStateManager.translate(
                        0.0F,
                        MODEL_GROUND_OFFSET + movementBob,
                        0.0F
                );
                org.lwjgl.opengl.GL11.glScalef(
                        MODEL_SCALE,
                        MODEL_SCALE,
                        MODEL_SCALE
                );
                Minecraft.getMinecraft().renderEngine.bindTexture(
                        GeoModelBattleRam.TEXTURE
                );

                GeoBone master = getCachedBone(geoModel, MASTER_BONE);
                BonePose neutral = master == null
                        ? null
                        : getNeutralPose(master);
                if (neutral != null) {
                    neutral.restore(master);
                }

                try {
                    /*
                     * Ram strikes are now physical server movement. Keep the
                     * authored model in its neutral pose instead of applying
                     * the old root-bone lunge on top of that real movement.
                     */
                    this.render(
                            geoModel,
                            ram,
                            partialTicks,
                            1.0F,
                            1.0F,
                            1.0F,
                            1.0F
                    );
                } finally {
                    if (master != null && neutral != null) {
                        neutral.restore(master);
                    }
                }
            } finally {
                GlStateManager.popMatrix();
            }
        } finally {
            GlStateManager.popMatrix();
        }

        if (ram.isHirePreview()) {
            renderHirePreviewCrew(
                    ram,
                    x,
                    y,
                    z,
                    partialTicks
            );
        }
    }

    /**
     * Small client-only suspension bob driven by the ram's real horizontal
     * travel. Amplitude eases in/out so ordinary follow movement feels alive
     * without changing the entity position, collision, gate contact point, or
     * carrier grounding.
     */
    private float getMovementBob(
            EntityBattleRam ram,
            float partialTicks
    ) {
        if (ram == null || ram.worldObj == null || ram.isHirePreview()) {
            return 0.0F;
        }

        RamBobState state = ramBobStates.get(ram);
        if (state == null) {
            state = new RamBobState();
            state.lastWorldTick = ram.worldObj.getTotalWorldTime();
            ramBobStates.put(ram, state);
        }

        long worldTick = ram.worldObj.getTotalWorldTime();
        if (worldTick != state.lastWorldTick) {
            state.previousAmplitude = state.amplitude;
            state.previousPhase = state.phase;

            double dx = ram.posX - ram.lastTickPosX;
            double dz = ram.posZ - ram.lastTickPosZ;
            double travel = Math.sqrt(dx * dx + dz * dz);

            if (ram.getRamState()
                    == com.enovak.lotrmoremobs.siege.ram.BattleRamState.PAUSED
                    || travel < 0.0015D) {
                travel = 0.0D;
            }

            float movement = (float)Math.min(
                    1.0D,
                    travel / (EntityBattleRam.BASE_RAM_MOVE_SPEED * 0.85D)
            );

            float targetAmplitude = MOVEMENT_BOB_MAX * movement;
            float response = targetAmplitude > state.amplitude
                    ? MOVEMENT_BOB_RISE_RESPONSE
                    : MOVEMENT_BOB_FALL_RESPONSE;

            state.amplitude +=
                    (targetAmplitude - state.amplitude) * response;

            if (travel > 0.0D) {
                float phaseAdvance = (float)Math.min(
                        1.05D,
                        0.28D + travel * 3.55D
                );
                state.phase += phaseAdvance;
            }

            state.lastWorldTick = worldTick;
        }

        float alpha = Math.max(0.0F, Math.min(1.0F, partialTicks));
        float amplitude = state.previousAmplitude
                + (state.amplitude - state.previousAmplitude) * alpha;
        float phase = state.previousPhase
                + (state.phase - state.previousPhase) * alpha;

        return (float)Math.sin(phase) * amplitude;
    }

    private void renderHirePreviewCrew(
            EntityBattleRam ram,
            double x,
            double y,
            double z,
            float partialTicks
    ) {
        if (ram.worldObj == null
                || !ram.worldObj.isRemote
                || !ram.isHirePreview()) {
            return;
        }

        LOTRFaction faction = ram.getRamFaction();
        Class<? extends LOTREntityNPC> crewClass =
                BattleRamCrewTypes.getCrewClass(faction);
        if (crewClass == null) {
            return;
        }

        if (hirePreviewCrew == null
                || hirePreviewWorld != ram.worldObj
                || hirePreviewFaction != faction
                || hirePreviewCrew.getClass() != crewClass) {
            hirePreviewCrew = createHirePreviewCrew(
                    ram.worldObj,
                    crewClass
            );
            hirePreviewWorld = ram.worldObj;
            hirePreviewFaction = faction;
            hirePreviewCrewRenderer = findRenderer(hirePreviewCrew);
        }

        if (hirePreviewCrew == null
                || hirePreviewCrewRenderer == null) {
            return;
        }

        LOTREntityNPC crew = hirePreviewCrew;

        /*
         * LOTR's hiring GUI deliberately gives the preview entity a smaller
         * body yaw (renderYawOffset) than its mouse-driven look yaw
         * (rotationYaw). The ram model follows that body yaw, so the crew
         * formation must use the same value or the two pieces visibly orbit
         * the cursor at different rates.
         */
        float formationYaw = ram.renderYawOffset;
        double angle = Math.toRadians(formationYaw);
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);

        for (int slot = 0;
                slot < EntityBattleRam.CREW_SLOT_COUNT;
                ++slot) {
            double localX = EntityBattleRam.getCrewLocalX(slot);
            double localZ = EntityBattleRam.getCrewLocalZ(slot);
            double offsetX = localX * cos - localZ * sin;
            double offsetZ = localX * sin + localZ * cos;

            stabilizeHirePreviewCrew(
                    crew,
                    ram,
                    offsetX,
                    offsetZ,
                    formationYaw
            );

            GlStateManager.pushMatrix();
            try {
                hirePreviewCrewRenderer.doRender(
                        crew,
                        x + offsetX,
                        y,
                        z + offsetZ,
                        crew.rotationYaw,
                        partialTicks
                );
            } finally {
                GlStateManager.popMatrix();
            }
        }
    }

    private LOTREntityNPC createHirePreviewCrew(
            World world,
            Class<? extends LOTREntityNPC> crewClass
    ) {
        try {
            Constructor<? extends LOTREntityNPC> constructor =
                    crewClass.getConstructor(World.class);
            LOTREntityNPC crew = constructor.newInstance(world);
            crew.initCreatureForHire(null);
            crew.refreshCurrentAttackMode();
            crew.setCurrentItemOrArmor(0, null);
            crew.noClip = true;
            return crew;
        } catch (Exception exception) {
            FMLLog.warning(
                    "[LOTRMoreMobs] Could not create Battle Ram hire "
                            + "preview carrier: %s",
                    exception.getClass().getSimpleName()
            );
            return null;
        }
    }

    private Render findRenderer(LOTREntityNPC crew) {
        if (crew == null) {
            return null;
        }
        Class entityClass = crew.getClass();
        while (entityClass != null) {
            Object renderer = RenderManager.instance.entityRenderMap.get(
                    entityClass
            );
            if (renderer instanceof Render) {
                return (Render)renderer;
            }
            entityClass = entityClass.getSuperclass();
        }
        return null;
    }

    private void stabilizeHirePreviewCrew(
            LOTREntityNPC crew,
            EntityBattleRam ram,
            double offsetX,
            double offsetZ,
            float formationYaw
    ) {
        crew.setPosition(
                ram.posX + offsetX,
                ram.posY,
                ram.posZ + offsetZ
        );
        crew.prevPosX = crew.posX;
        crew.prevPosY = crew.posY;
        crew.prevPosZ = crew.posZ;
        crew.lastTickPosX = crew.posX;
        crew.lastTickPosY = crew.posY;
        crew.lastTickPosZ = crew.posZ;
        crew.rotationYaw = formationYaw;
        crew.prevRotationYaw = formationYaw;
        crew.renderYawOffset = formationYaw;
        crew.prevRenderYawOffset = formationYaw;
        crew.rotationYawHead = formationYaw;
        crew.prevRotationYawHead = formationYaw;
        crew.rotationPitch = 0.0F;
        crew.prevRotationPitch = 0.0F;
        crew.limbSwing = 0.0F;
        crew.limbSwingAmount = 0.0F;
        crew.prevLimbSwingAmount = 0.0F;
        crew.swingProgress = 0.0F;
        crew.prevSwingProgress = 0.0F;
        crew.motionX = 0.0D;
        crew.motionY = 0.0D;
        crew.motionZ = 0.0D;
        crew.setCurrentItemOrArmor(0, null);
        crew.ticksExisted = 0;
    }

    private void applyAuthoredAttack(
            GeoBone master,
            BonePose neutral,
            AuthoredAttackAnimation animation,
            float phaseTicks
    ) {
        float sourceTime = mapAttackPhaseToSourceTime(
                phaseTicks,
                animation.lengthSeconds
        );
        float[] position = sample(animation.position, sourceTime);
        float[] rotation = sample(animation.rotation, sourceTime);
        float[] scale = sample(animation.scale, sourceTime);

        master.setPositionX(
                neutral.positionX
                        + position[0] * AUTHORED_POSITION_X_SIGN
        );
        master.setPositionY(
                neutral.positionY
                        + position[1] * AUTHORED_POSITION_Y_SIGN
        );
        master.setPositionZ(
                neutral.positionZ
                        + position[2] * AUTHORED_POSITION_Z_SIGN
        );

        /* Match Gecko 1.0.4's standard rotation keyframe conversion. */
        master.setRotationX(
                neutral.rotationX
                        - rotation[0] * DEGREES_TO_RADIANS
        );
        master.setRotationY(
                neutral.rotationY
                        - rotation[1] * DEGREES_TO_RADIANS
        );
        master.setRotationZ(
                neutral.rotationZ
                        + rotation[2] * DEGREES_TO_RADIANS
        );

        master.setScaleX(neutral.scaleX * scale[0]);
        master.setScaleY(neutral.scaleY * scale[1]);
        master.setScaleZ(neutral.scaleZ * scale[2]);
    }

    private AuthoredAttackAnimation getAttackAnimation() {
        if (attemptedAttackLoad) {
            return attackAnimation;
        }
        attemptedAttackLoad = true;

        InputStream stream = null;
        Reader reader = null;
        try {
            IResource resource = Minecraft.getMinecraft()
                    .getResourceManager()
                    .getResource(GeoModelBattleRam.ATTACK_ANIMATION);
            stream = resource.getInputStream();
            reader = new InputStreamReader(stream, "UTF-8");
            JsonObject root = new JsonParser().parse(reader).getAsJsonObject();
            if (!root.has("format_version")
                    || !root.get("format_version").isJsonPrimitive()
                    || root.get("format_version")
                    .getAsString().trim().length() == 0) {
                throw new IllegalArgumentException(
                        "Battle Ram animation requires format_version"
                );
            }
            JsonObject animations = requireObject(root, "animations");
            JsonObject attacking = requireObject(
                    animations,
                    ATTACK_ANIMATION_NAME
            );
            float length = requireFiniteFloat(
                    attacking,
                    "animation_length"
            );
            if (length <= 0.0F) {
                throw new IllegalArgumentException(
                        "Battle Ram Attacking animation_length must be > 0"
                );
            }
            if (ATTACK_CONTACT_SOURCE_SECONDS > length) {
                throw new IllegalArgumentException(
                        "Battle Ram contact time exceeds animation length"
                );
            }
            JsonObject bones = requireObject(attacking, "bones");
            JsonObject master = requireObject(bones, MASTER_BONE);
            attackAnimation = new AuthoredAttackAnimation(
                    length,
                    readGeckoKeyframes(
                            requireObject(master, "position"),
                            "position",
                            length
                    ),
                    readGeckoKeyframes(
                            requireObject(master, "rotation"),
                            "rotation",
                            length
                    ),
                    readGeckoKeyframes(
                            requireObject(master, "scale"),
                            "scale",
                            length
                    )
            );
        } catch (Exception exception) {
            FMLLog.warning(
                    "[LOTRMoreMobs] Could not load Battle Ram authored "
                            + "Attacking animation: %s",
                    exception.toString()
            );
            attackAnimation = null;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception ignored) {
                }
            } else if (stream != null) {
                try {
                    stream.close();
                } catch (Exception ignored) {
                }
            }
        }
        return attackAnimation;
    }

    private static JsonObject requireObject(
            JsonObject parent,
            String memberName
    ) {
        if (parent == null
                || !parent.has(memberName)
                || !parent.get(memberName).isJsonObject()) {
            throw new IllegalArgumentException(
                    "Battle Ram animation requires object '"
                            + memberName
                            + "'"
            );
        }
        return parent.getAsJsonObject(memberName);
    }

    private static float requireFiniteFloat(
            JsonObject parent,
            String memberName
    ) {
        if (parent == null || !parent.has(memberName)) {
            throw new IllegalArgumentException(
                    "Battle Ram animation requires number '"
                            + memberName
                            + "'"
            );
        }
        float value = parent.get(memberName).getAsFloat();
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            throw new IllegalArgumentException(
                    "Battle Ram animation number '"
                            + memberName
                            + "' must be finite"
            );
        }
        return value;
    }

    private static float[][] readGeckoKeyframes(
            JsonObject channel,
            final String channelName,
            float animationLength
    ) {
        if (channel.entrySet().isEmpty()) {
            throw new IllegalArgumentException(
                    "Battle Ram Master " + channelName + " is empty"
            );
        }
        List<float[]> parsed = new ArrayList<float[]>();
        for (Map.Entry<String, JsonElement> entry : channel.entrySet()) {
            float time;
            try {
                time = Float.parseFloat(entry.getKey());
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException(
                        "Battle Ram " + channelName
                                + " keyframe has invalid time '"
                                + entry.getKey() + "'",
                        exception
                );
            }
            if (Float.isNaN(time)
                    || Float.isInfinite(time)
                    || time < 0.0F
                    || time > animationLength) {
                throw new IllegalArgumentException(
                        "Battle Ram " + channelName
                                + " keyframe time is outside animation: "
                                + time
                );
            }
            JsonElement keyframeElement = entry.getValue();
            if (!keyframeElement.isJsonObject()) {
                throw new IllegalArgumentException(
                        "Battle Ram " + channelName
                                + " keyframe " + time
                                + " must be an object"
                );
            }
            JsonObject keyframe = keyframeElement.getAsJsonObject();
            if (!keyframe.has("vector")
                    || !keyframe.get("vector").isJsonArray()) {
                throw new IllegalArgumentException(
                        "Battle Ram " + channelName
                                + " keyframe " + time
                                + " requires vector"
                );
            }
            JsonArray vector = keyframe.getAsJsonArray("vector");
            if (vector.size() != 3) {
                throw new IllegalArgumentException(
                        "Battle Ram " + channelName
                                + " keyframe " + time
                                + " vector must have x, y, z"
                );
            }
            float[] frame = new float[4];
            frame[0] = time;
            for (int component = 0; component < 3; ++component) {
                float value = vector.get(component).getAsFloat();
                if (Float.isNaN(value) || Float.isInfinite(value)) {
                    throw new IllegalArgumentException(
                            "Battle Ram " + channelName
                                    + " keyframe " + time
                                    + " contains a non-finite vector"
                    );
                }
                frame[component + 1] = value;
            }
            parsed.add(frame);
        }
        Collections.sort(parsed, new Comparator<float[]>() {
            @Override
            public int compare(float[] first, float[] second) {
                return Float.compare(first[0], second[0]);
            }
        });
        float[][] frames = new float[parsed.size()][4];
        for (int i = 0; i < parsed.size(); ++i) {
            frames[i] = parsed.get(i);
        }
        return frames;
    }

    private static float mapAttackPhaseToSourceTime(
            float phaseTicks,
            float animationLength
    ) {
        float clampedPhase = Math.max(
                0.0F,
                Math.min(EntityBattleRam.ATTACK_INTERVAL_TICKS, phaseTicks)
        );
        if (clampedPhase <= EntityBattleRam.ATTACK_IMPACT_TICK) {
            return clampedPhase
                    / EntityBattleRam.ATTACK_IMPACT_TICK
                    * ATTACK_CONTACT_SOURCE_SECONDS;
        }
        float recoveryTicks = EntityBattleRam.ATTACK_INTERVAL_TICKS
                - EntityBattleRam.ATTACK_IMPACT_TICK;
        return ATTACK_CONTACT_SOURCE_SECONDS
                + (clampedPhase - EntityBattleRam.ATTACK_IMPACT_TICK)
                / recoveryTicks
                * (animationLength - ATTACK_CONTACT_SOURCE_SECONDS);
    }

    private static float[] sample(float[][] frames, float timeSeconds) {
        if (frames == null || frames.length == 0) {
            return new float[] {0.0F, 0.0F, 0.0F};
        }
        if (timeSeconds <= frames[0][0]) {
            return vector(frames[0]);
        }
        int last = frames.length - 1;
        if (timeSeconds >= frames[last][0]) {
            return vector(frames[last]);
        }
        for (int i = 0; i < last; ++i) {
            float[] start = frames[i];
            float[] end = frames[i + 1];
            if (timeSeconds >= start[0] && timeSeconds <= end[0]) {
                float duration = end[0] - start[0];
                if (duration <= 0.00001F) {
                    return vector(end);
                }
                float progress = (timeSeconds - start[0]) / duration;
                return new float[] {
                        interpolate(start[1], end[1], progress),
                        interpolate(start[2], end[2], progress),
                        interpolate(start[3], end[3], progress)
                };
            }
        }
        return vector(frames[last]);
    }

    private static float[] vector(float[] frame) {
        return new float[] {frame[1], frame[2], frame[3]};
    }

    private static float interpolate(float start, float end, float progress) {
        return start + (end - start) * progress;
    }

    private GeoBone getCachedBone(GeoModel model, String name) {
        Map<String, GeoBone> bones = this.bonesByModel.get(model);
        if (bones == null) {
            bones = new HashMap<String, GeoBone>();
            this.bonesByModel.put(model, bones);
        }
        if (bones.containsKey(name)) {
            return bones.get(name);
        }
        Optional<GeoBone> resolved = model.getBone(name);
        GeoBone bone = resolved.isPresent() ? resolved.get() : null;
        bones.put(name, bone);
        return bone;
    }

    private BonePose getNeutralPose(GeoBone bone) {
        BonePose pose = this.neutralPoses.get(bone);
        if (pose == null) {
            pose = new BonePose(bone);
            this.neutralPoses.put(bone, pose);
        }
        return pose;
    }

    public Integer getUniqueID(EntityBattleRam ram) {
        return Integer.valueOf(ram == null ? 0 : ram.getEntityId());
    }

    public ResourceLocation getTextureLocation(EntityBattleRam ram) {
        return GeoModelBattleRam.TEXTURE;
    }

    @Override
    public ResourceLocation getEntityTexture(Entity entity) {
        return GeoModelBattleRam.TEXTURE;
    }

    private static final class AuthoredAttackAnimation {
        private final float lengthSeconds;
        private final float[][] position;
        private final float[][] rotation;
        private final float[][] scale;

        private AuthoredAttackAnimation(
                float lengthSeconds,
                float[][] position,
                float[][] rotation,
                float[][] scale
        ) {
            this.lengthSeconds = lengthSeconds > 0.0F
                    ? lengthSeconds
                    : ATTACK_SOURCE_LENGTH_SECONDS;
            this.position = position;
            this.rotation = rotation;
            this.scale = scale;
        }
    }

    private static final class RamBobState {
        private long lastWorldTick;
        private float previousAmplitude;
        private float amplitude;
        private float previousPhase;
        private float phase;
    }

    private static final class BonePose {
        private final float positionX;
        private final float positionY;
        private final float positionZ;
        private final float rotationX;
        private final float rotationY;
        private final float rotationZ;
        private final float scaleX;
        private final float scaleY;
        private final float scaleZ;

        private BonePose(GeoBone bone) {
            this.positionX = bone.getPositionX();
            this.positionY = bone.getPositionY();
            this.positionZ = bone.getPositionZ();
            this.rotationX = bone.getRotationX();
            this.rotationY = bone.getRotationY();
            this.rotationZ = bone.getRotationZ();
            this.scaleX = bone.getScaleX();
            this.scaleY = bone.getScaleY();
            this.scaleZ = bone.getScaleZ();
        }

        private void restore(GeoBone bone) {
            bone.setPositionX(positionX);
            bone.setPositionY(positionY);
            bone.setPositionZ(positionZ);
            bone.setRotationX(rotationX);
            bone.setRotationY(rotationY);
            bone.setRotationZ(rotationZ);
            bone.setScaleX(scaleX);
            bone.setScaleY(scaleY);
            bone.setScaleZ(scaleZ);
        }
    }
}

