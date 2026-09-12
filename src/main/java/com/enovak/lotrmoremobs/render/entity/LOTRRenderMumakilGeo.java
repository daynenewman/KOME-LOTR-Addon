package com.enovak.lotrmoremobs.render.entity;

import com.enovak.lotrmoremobs.entity.animal.LOTREntityMumakil;
import com.enovak.lotrmoremobs.model.mumakil.LOTRGeoModelMumakil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.geckominecraft.client.renderer.GlStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResource;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import org.apache.commons.lang3.tuple.Pair;
import software.bernie.geckolib3.core.event.predicate.AnimationEvent;
import software.bernie.geckolib3.core.util.Color;
import software.bernie.geckolib3.geo.render.built.GeoBone;
import software.bernie.geckolib3.geo.render.built.GeoModel;
import software.bernie.geckolib3.model.provider.data.EntityModelData;
import software.bernie.geckolib3.renderers.geo.GeoEntityRenderer;
import org.lwjgl.opengl.GL11;

/**
 * Experimental renderer that bypasses the Java ModelRenderer conversion and renders the exported .geo.json.
 *
 * Keep LOTRRenderMumakil available as the stable RenderLiving/ModelBase fallback while this branch tests
 * whether the original Geo model fixes the UV bleed at the root.
 */
public class LOTRRenderMumakilGeo extends GeoEntityRenderer<LOTREntityMumakil>
        implements IResourceManagerReloadListener {
    private static final float BABY_PANIC_ANIMATION_MULTIPLIER = 1.0F;
    // SLOW_PANIC_AND_PLAYER_RIDDEN_LOCOMOTION_V1 / QUARTER_SPEED_RIDDEN_PANIC_AND_STRICT_TREE_BLOCKS_V2

    private static final String[] SADDLE_BONES = new String[] {
            "saddle",
            "front_strap",
            "saddle_mid_strap",
            "saddle_rear_strap"
    };

    private static final String[] WAR_EQUIPMENT_BONES = new String[] {
            "war_howdah",
            "howdah_rigging",
            "howdah_supports",
            "howdah_ladder",
            "perch_01",
            "perch_02",
            "perch_03",
            "perch_04",
            "front_right_ankle_spikes",
            "front_left_ankle_spikes",
            "left_tusk_spikes",
            "right_tusk_spikes",
            "left_steering",
            "right_steering",
            "left_hook"
    };

    private static final String[] ALL_EQUIPMENT_BONES = new String[] {
            "saddle",
            "front_strap",
            "saddle_mid_strap",
            "saddle_rear_strap",

            "war_howdah",
            "howdah_rigging",
            "howdah_supports",
            "howdah_ladder",
            "perch_01",
            "perch_02",
            "perch_03",
            "perch_04",
            "front_right_ankle_spikes",
            "front_left_ankle_spikes",
            "left_tusk_spikes",
            "right_tusk_spikes",
            "left_steering",
            "right_steering",
            "left_hook"
    };

    private static final boolean[] loggedEquipmentStates = new boolean[4];
    private static final boolean[] loggedWarBoneVisibilityStates = new boolean[2];

    private static boolean loggedSafeRenderStart;
    private static boolean loggedModelRequest;
    private static boolean loggedModelFallback;
    private static boolean loggedMissingGeoModel;
    private static boolean loggedTextureRequest;
    private static boolean loggedTextureFallback;
    private static boolean loggedBeforeGeoRender;

    private static final String RENDERER_BUILD_TAG = "BLOCKBENCH_MULTI_ANIMATION_JSON_V12_6_STRIKE_ISOLATION_2026_07_23"; // MUMAKIL_STRIKE_RENDER_ISOLATION_V2_1
    private static final boolean DEBUG_LOGS = false;
    private static final boolean USE_BLOCKBENCH_TRUMPET = true;
    private static final boolean USE_BLOCKBENCH_EAR_FLAP = true;
    private static final boolean USE_BLOCKBENCH_TAIL_FLIP = true;
    private static final boolean USE_BLOCKBENCH_STRIKES = true;

    private static final float BLOCKBENCH_TRUMPET_FALLBACK_LENGTH_SECONDS = 2.625F;
    private static final int BLOCKBENCH_EAR_FLAP_DURATION_TICKS = 45;
    private static final int BLOCKBENCH_TAIL_FLIP_DURATION_TICKS = 40;
    private static final int BLOCKBENCH_STRIKE_DURATION_TICKS = 36;

    /*
     * Random ambient animation cadence.
     * These are deterministic per entity, but visually random in-game.
     */
    private static final int AMBIENT_EAR_FLAP_INTERVAL_TICKS = 180;
    private static final int AMBIENT_EAR_FLAP_CHANCE_MODULO = 2;
    private static final int AMBIENT_TAIL_FLIP_INTERVAL_TICKS = 140;
    private static final int AMBIENT_TAIL_FLIP_CHANCE_MODULO = 2;

    /*
     * Your working in-game test needed the Blockbench X axis flipped.
     * Keep this at -1.0F unless the animation visually points the wrong way again.
     */
    private static final float BLOCKBENCH_TRUMPET_X_SIGN = -1.0F;

    private static final String BLOCKBENCH_TRUMPET_ANIMATION_NAME = "animation.mumakil.trumpet";
    private static final String BLOCKBENCH_EAR_FLAP_ANIMATION_NAME = "animation.mumakil.ear_flap";
    private static final String BLOCKBENCH_TAIL_FLIP_ANIMATION_NAME = "animation.mumakil.tail_flip";
    private static final String BLOCKBENCH_STRIKE_LEFT_ANIMATION_NAME = "animation.mumakil.strike_left";
    private static final String BLOCKBENCH_STRIKE_RIGHT_ANIMATION_NAME = "animation.mumakil.strike_right";
    private static final ResourceLocation BLOCKBENCH_TRUMPET_RESOURCE = new ResourceLocation(
            "lotrmoremobs",
            "animations/entity/mumakil/mumakil.animations.json"
    );

    private static boolean loggedRendererBuild;
    private static boolean loggedBlockbenchTrumpetApplied;
    private static boolean loggedBlockbenchEarFlapApplied;
    private static boolean loggedBlockbenchTailFlipApplied;
    private static boolean loggedBlockbenchStrikeApplied;

    private static boolean attemptedLoadBlockbenchAnimationFile;
    private static Map<String, BlockbenchAnimation> loadedBlockbenchAnimations;
    private static boolean attemptedLoadBlockbenchTrumpetAnimation;
    private static BlockbenchAnimation loadedBlockbenchTrumpetAnimation;

    /*
     * Geo models are immutable after GeckoLib builds them, but their bones are
     * reused and mutable. Cache identity-based lookups for the lifetime of the
     * resource set, then clear them on a resource reload.
     */
    private final Map<ResourceLocation, Boolean> resourceAvailability =
            new HashMap<ResourceLocation, Boolean>();
    private final Map<GeoModel, Map<String, GeoBone>> bonesByModel =
            new IdentityHashMap<GeoModel, Map<String, GeoBone>>();
    private final Map<GeoBone, List<GeoBone>> childrenByBone =
            new IdentityHashMap<GeoBone, List<GeoBone>>();

    private static final float[][] BB_TRUMPET_BODY_SWAY = new float[][] {
            {0.0000F, 0.0000F, 0.0000F, 0.0000F},
            {0.7917F, -4.4500F, 0.0000F, 0.0000F},
            {0.8333F, -4.3500F, 0.0000F, 0.0000F},
            {1.4167F, 0.0000F, 0.0000F, 0.0000F},
            {1.5417F, 0.5000F, 0.0000F, 0.0000F},
            {1.7083F, 0.0000F, 0.0000F, 0.0000F},
    };

    private static final float[][] BB_TRUMPET_UPPER_BACK = new float[][] {
            {0.1667F, 0.0000F, 0.0000F, 0.0000F},
            {0.7917F, -14.5000F, 0.0000F, 0.0000F},
            {0.8333F, -14.3500F, 0.0000F, 0.0000F},
            {1.4167F, 0.0000F, 0.0000F, 0.0000F},
    };

    private static final float[][] BB_TRUMPET_HEAD = new float[][] {
            {0.0833F, 0.0000F, 0.0000F, 0.0000F},
            {0.8333F, -42.5000F, 0.0000F, 0.0000F},
            {1.4167F, 0.0000F, 0.0000F, 0.0000F},
            {1.7083F, 6.0000F, 0.0000F, 0.0000F},
            {2.0000F, 1.0000F, 0.0000F, 0.0000F},
            {2.2500F, -2.0000F, 0.0000F, 0.0000F},
            {2.4583F, 0.0000F, 0.0000F, 0.0000F},
    };

    private static final float[][] BB_TRUMPET_LEFT_EAR = new float[][] {
            {0.0000F, 0.0000F, 0.0000F, 0.0000F},
            {0.1250F, 0.0000F, 0.0000F, 0.0000F},
            {0.2500F, 0.0000F, 0.6310F, 0.0000F},
            {0.3333F, 0.0000F, 1.6430F, 0.0000F},
            {0.4583F, 0.0000F, 2.7630F, 0.0000F},
            {0.5417F, 0.0000F, 3.0000F, 0.0000F},
            {0.6667F, 0.0000F, 3.0000F, 0.0000F},
            {0.7917F, 0.0000F, 3.0000F, 0.0000F},
            {1.0000F, 0.0000F, 3.0000F, 0.0000F},
            {1.2500F, 0.0000F, 3.0000F, 0.0000F},
            {1.4583F, 0.0000F, 3.0000F, 0.0000F},
            {1.6667F, 0.0000F, 2.9990F, 0.0000F},
            {1.8333F, 0.0000F, 2.2900F, 0.0000F},
            {2.0417F, 0.0000F, 0.9700F, 0.0000F},
            {2.2500F, 0.0000F, 0.0000F, 0.0000F},
    };

    private static final float[][] BB_TRUMPET_RIGHT_EAR = new float[][] {
            {0.0000F, 0.0000F, 0.0000F, 0.0000F},
            {0.1250F, 0.0000F, 0.0000F, 0.0000F},
            {0.2500F, 0.0000F, -0.6310F, 0.0000F},
            {0.3333F, 0.0000F, -1.6430F, 0.0000F},
            {0.4583F, 0.0000F, -2.7630F, 0.0000F},
            {0.5417F, 0.0000F, -3.0000F, 0.0000F},
            {0.6667F, 0.0000F, -3.0000F, 0.0000F},
            {0.7917F, 0.0000F, -3.0000F, 0.0000F},
            {1.0000F, 0.0000F, -3.0000F, 0.0000F},
            {1.2500F, 0.0000F, -3.0000F, 0.0000F},
            {1.4583F, 0.0000F, -3.0000F, 0.0000F},
            {1.6667F, 0.0000F, -2.9990F, 0.0000F},
            {1.8333F, 0.0000F, -2.2900F, 0.0000F},
            {2.0417F, 0.0000F, -0.9700F, 0.0000F},
            {2.2500F, 0.0000F, 0.0000F, 0.0000F},
    };

    private static final float[][] BB_TRUMPET_TRUNK = new float[][] {
            {0.0000F, 0.0000F, 0.0000F, 0.0000F},
            {0.8750F, -18.7510F, 0.1540F, 0.0000F},
            {1.3750F, -17.0700F, 0.0700F, 0.0000F},
            {1.7083F, 0.0000F, 0.0000F, 0.0000F},
    };

    private static final float[][] BB_TRUMPET_TRUNK_01 = new float[][] {
            {0.1250F, 0.0000F, 0.0000F, 0.0000F},
            {0.8750F, -36.0000F, 0.0000F, 0.0000F},
            {1.6250F, 0.0000F, 0.0000F, 0.0000F},
            {1.9167F, 8.0000F, 0.0000F, 0.0000F},
            {2.2083F, 0.0000F, 0.0000F, 0.0000F},
            {2.4167F, -1.0000F, 0.0000F, 0.0000F},
            {2.6250F, 0.0000F, 0.0000F, 0.0000F},
    };

    private static final float[][] BB_TRUMPET_TRUNK_02 = new float[][] {
            {0.1667F, 0.0000F, 0.0000F, 0.0000F},
            {0.9167F, -56.0000F, 0.0000F, 0.0000F},
            {1.7083F, 0.0000F, 0.0000F, 0.0000F},
    };

    private static final float[][] BB_TRUMPET_TRUNK_03 = new float[][] {
            {0.2083F, 0.0000F, 0.0000F, 0.0000F},
            {0.9583F, -74.5000F, 0.0000F, 0.0000F},
            {1.7500F, 0.0000F, 0.0000F, 0.0000F},
    };

    private static final float[][] BB_TRUMPET_TRUNK_04 = new float[][] {
            {0.3333F, 0.0000F, 0.0000F, 0.0000F},
            {1.0833F, -109.0000F, 0.0000F, 0.0000F},
            {1.8750F, 0.0000F, 0.0000F, 0.0000F},
    };

    private static final float[][] BB_TRUMPET_TRUNK_05 = new float[][] {
            {0.2917F, 0.0000F, 0.0000F, 0.0000F},
            {0.9167F, -98.8750F, 0.0000F, 0.0000F},
            {1.8333F, 0.0000F, 0.0000F, 0.0000F},
    };


    public LOTRRenderMumakilGeo() {
        super(new LOTRGeoModelMumakil());
        this.shadowSize = 5.0F;

        IResourceManager resourceManager =
                Minecraft.getMinecraft().getResourceManager();
        if (resourceManager instanceof IReloadableResourceManager) {
            ((IReloadableResourceManager)resourceManager)
                    .registerReloadListener(this);
        }

        if (DEBUG_LOGS && !loggedRendererBuild) {
            loggedRendererBuild = true;
            System.out.println("[LOTRMoreMobs] Mumakil Geo renderer build loaded: " + RENDERER_BUILD_TAG);
        }
    }

    @Override
    public void onResourceManagerReload(IResourceManager resourceManager) {
        this.resourceAvailability.clear();
        this.bonesByModel.clear();
        this.childrenByBone.clear();
        attemptedLoadBlockbenchAnimationFile = false;
        loadedBlockbenchAnimations = null;
        attemptedLoadBlockbenchTrumpetAnimation = false;
        loadedBlockbenchTrumpetAnimation = null;
    }

    /**
     * RenderManager can enter through the raw Entity signature. Keep this generic path and route only real
     * Mumakil instances into the safe static renderer instead of allowing dispatch to GeoEntityRenderer#doRender.
     */
    public void doRender(Entity entity, double x, double y, double z, float entityYaw, float partialTicks) {
        if (!(entity instanceof LOTREntityMumakil)) {
            return;
        }

        this.doRenderMumakil((LOTREntityMumakil)entity, x, y, z, entityYaw, partialTicks);
    }

    /**
     * LOTR's mob-spawner inventory preview can enter through the EntityLivingBase signature. If this exact
     * overload is missing, runtime dispatch can still reach GeckoLib's GeoEntityRenderer#doRender and its
     * obfuscated EntityLivingBase.func_98034_c(EntityPlayer) invisibility call. Route it through the same safe
     * static helper and never call super.doRender(...).
     */
    public void doRender(EntityLivingBase entity, double x, double y, double z, float entityYaw, float partialTicks) {
        if (!(entity instanceof LOTREntityMumakil)) {
            return;
        }

        this.doRenderMumakil((LOTREntityMumakil)entity, x, y, z, entityYaw, partialTicks);
    }

    private void doRenderMumakil(LOTREntityMumakil entity, double x, double y, double z, float entityYaw, float partialTicks) {
        if (entity == null) {
            return;
        }

        if (DEBUG_LOGS && !loggedSafeRenderStart) {
            loggedSafeRenderStart = true;
            System.out.println("[LOTRMoreMobs] Mumakil safe Geo render path started for entityId=" + entity.getEntityId());
        }

        if (entity.isInvisible()) {
            return;
        }

        this.shadowSize = 5.0F * entity.getMumakilRenderScale();

        boolean renderSaddle = LOTRGeoModelMumakil.shouldRenderSaddle(entity);
        boolean renderHowdahOrWarEquipment = LOTRGeoModelMumakil.shouldRenderHowdahOrWarEquipment(entity);
        this.logEquipmentState(entity, renderSaddle, renderHowdahOrWarEquipment);

        ResourceLocation modelLocation = this.modelProvider.getModelLocation(entity);
        if (!this.canLoadResource(modelLocation) && !LOTRGeoModelMumakil.PLAIN_MODEL.equals(modelLocation)) {
            if (DEBUG_LOGS && !loggedModelFallback) {
                loggedModelFallback = true;
                System.out.println("[LOTRMoreMobs] Mumakil Geo model missing, falling back to plain export: " + modelLocation);
            }
            modelLocation = LOTRGeoModelMumakil.PLAIN_MODEL;
        }

        if (DEBUG_LOGS && !loggedModelRequest) {
            loggedModelRequest = true;
            System.out.println("[LOTRMoreMobs] Mumakil Geo model requested: " + modelLocation
                    + " resourceFound=" + this.canLoadResource(modelLocation)
                    + " renderSaddle=" + renderSaddle
                    + " renderHowdahOrWarEquipment=" + renderHowdahOrWarEquipment);
        }

        GeoModel geoModel = this.modelProvider.getModel(modelLocation);
        if (geoModel == null) {
            if (DEBUG_LOGS && !loggedMissingGeoModel) {
                loggedMissingGeoModel = true;
                System.out.println("[LOTRMoreMobs] Mumakil Geo renderer could not load model: " + modelLocation);
            }
            return;
        }

        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(x, y, z);

            boolean isSitting = entity.ridingEntity != null && entity.ridingEntity.shouldRiderSit();
            EntityModelData entityModelData = new EntityModelData();
            entityModelData.isSitting = isSitting;
            entityModelData.isChild = entity.isChild();

            Pair<Float, Float> rotations = this.calculateRotations(entity, partialTicks, isSitting);
            float headPitch = entity.prevRotationPitch + (entity.rotationPitch - entity.prevRotationPitch) * partialTicks;
            float ageInTicks = entity.ticksExisted + partialTicks;
            this.applyRotations(entity, ageInTicks, rotations.getKey().floatValue(), partialTicks);

            float limbSwingAmount = 0.0F;
            float limbSwing = 0.0F;
            if (!isSitting && entity.isEntityAlive()) {
                limbSwingAmount = entity.prevLimbSwingAmount
                        + (entity.limbSwingAmount - entity.prevLimbSwingAmount) * partialTicks;
                boolean playerRidden =
                        entity.riddenByEntity instanceof EntityPlayer;
                limbSwing = playerRidden
                        ? entity.getPlayerRiddenLocomotionPhase(partialTicks)
                        : entity.limbSwing
                        - entity.limbSwingAmount * (1.0F - partialTicks);

                /*
                 * Unridden calves retain the established fast child phase.
                 * A player-ridden Mumak already uses the shared adult ridden
                 * half-rate phase above, so multiplying that phase again made
                 * a ridden calf animate three times faster than the adult.
                 */
                if (entity.isChild() && !playerRidden) {
                    limbSwing *= 3.0F;
                }

                /*
                 * The entity owns a continuously accumulated half-rate phase
                 * for player riding. Interpolating that phase here avoids the
                 * discontinuities caused by scaling the absolute native
                 * limbSwing value on individual frames. Swing amplitude and
                 * actual movement remain native.
                 */
                 if (entity.isBabyPanicAnimationActive()) {
                     limbSwing *= BABY_PANIC_ANIMATION_MULTIPLIER;
                 }

                if (limbSwingAmount > 1.0F) {
                    limbSwingAmount = 1.0F;
                }
            }

            entityModelData.headPitch = -headPitch;
            entityModelData.netHeadYaw = -rotations.getValue().floatValue();

            AnimationEvent<LOTREntityMumakil> animationEvent = new AnimationEvent<LOTREntityMumakil>(
                    entity,
                    limbSwing,
                    limbSwingAmount,
                    partialTicks,
                    limbSwingAmount >= 0.15F,
                    Collections.<Object>singletonList(entityModelData)
            );

            this.modelProvider.setLivingAnimations(entity, this.getUniqueID(entity), animationEvent);

            GlStateManager.pushMatrix();
            try {
                // Babies reuse the exact adult Geo model, texture, equipment bones, and animations.
                // Scale before the model offset so the complete rendered body stays proportionally aligned.
                float mumakilRenderScale = entity.getMumakilRenderScale();
                GL11.glScalef(mumakilRenderScale, mumakilRenderScale, mumakilRenderScale);

                // Move the visible Geo model relative to the entity hitbox.
                // This does NOT change the real hitbox.
                GlStateManager.translate(0.0F, 0.0F, 3.5F);

                ResourceLocation textureLocation = this.getEntityTexture(entity);
                if (!this.canLoadResource(textureLocation) && !LOTRGeoModelMumakil.WILD_TEXTURE.equals(textureLocation)) {
                    if (DEBUG_LOGS && !loggedTextureFallback) {
                        loggedTextureFallback = true;
                        System.out.println("[LOTRMoreMobs] Mumakil texture missing, falling back to wild texture: " + textureLocation);
                    }
                    textureLocation = LOTRGeoModelMumakil.WILD_TEXTURE;
                }

                if (DEBUG_LOGS && !loggedTextureRequest) {
                    loggedTextureRequest = true;
                    System.out.println("[LOTRMoreMobs] Mumakil Geo texture requested: " + textureLocation
                            + " resourceFound=" + this.canLoadResource(textureLocation)
                            + " renderSaddle=" + renderSaddle
                            + " renderHowdahOrWarEquipment=" + renderHowdahOrWarEquipment);
                }
                Minecraft.getMinecraft().renderEngine.bindTexture(textureLocation);

                Color renderColor = this.getRenderColor(entity, partialTicks);

                // Direct Java bone animation avoids GeckoLib controller/runtime issues in this 1.7.10 setup.
                this.applyMumakilBoneAnimations(geoModel, entity, limbSwing, limbSwingAmount, ageInTicks, partialTicks);

                // Apply bone visibility at the last safe point. GeckoLib can rebuild or touch bones during model
                // setup, so delay hiding/unhiding until immediately before the real Geo render call.
                this.applyEquipmentVisibility(geoModel, renderSaddle, renderHowdahOrWarEquipment);

                // Deliberately skip GeckoLib's EntityLivingBase.isInvisibleToPlayer(...) branch; in this
                // 1.7.10 dev runtime it dispatches to the missing obfuscated func_98034_c method.
                if (DEBUG_LOGS && !loggedBeforeGeoRender) {
                    loggedBeforeGeoRender = true;
                    System.out.println("[LOTRMoreMobs] Mumakil calling GeckoLib render(GeoModel, ...) with modelLoaded=true");
                }

                this.render(
                        geoModel,
                        entity,
                        partialTicks,
                        renderColor.getRed() / 255.0F,
                        renderColor.getGreen() / 255.0F,
                        renderColor.getBlue() / 255.0F,
                        renderColor.getAlpha() / 255.0F
                );

                Minecraft.getMinecraft().renderEngine.bindTexture(textureLocation);
                this.renderMumakilHurtOverlay(geoModel, entity, partialTicks);
            } finally {
                /*
                 * Leave the cached GeoModel neutral for any subsequent
                 * renderer path or Mumakil render in this frame.
                 */
                this.resetMumakilStrikeBoneState(geoModel);                GlStateManager.popMatrix();
            }
        } finally {
            GlStateManager.popMatrix();
        }
    }

    private boolean shouldRenderHurtOverlay(LOTREntityMumakil entity) {
        return entity != null && (entity.hurtTime > 0 || entity.deathTime > 0);
    }

    private void renderMumakilHurtOverlay(GeoModel geoModel, LOTREntityMumakil entity, float partialTicks) {
        if (!this.shouldRenderHurtOverlay(entity)) {
            return;
        }

        GlStateManager.pushMatrix();
        try {
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDepthFunc(GL11.GL_EQUAL);
            GL11.glDepthMask(false);

            this.render(
                    geoModel,
                    entity,
                    partialTicks,
                    1.0F,
                    0.12F,
                    0.12F,
                    0.55F
            );
        } finally {
            GL11.glDepthMask(true);
            GL11.glDepthFunc(GL11.GL_LEQUAL);
            GL11.glDisable(GL11.GL_BLEND);
            GlStateManager.popMatrix();
        }
    }

    private void logEquipmentState(LOTREntityMumakil entity, boolean renderSaddle, boolean renderHowdahOrWarEquipment) {
        int stateIndex = (renderSaddle ? 1 : 0) | (renderHowdahOrWarEquipment ? 2 : 0);
        if (DEBUG_LOGS && !loggedEquipmentStates[stateIndex]) {
            loggedEquipmentStates[stateIndex] = true;
            System.out.println("[LOTRMoreMobs] Mumakil Geo equipment state: isMountSaddled="
                    + entity.isMountSaddled()
                    + " detectedSaddleState=" + LOTRGeoModelMumakil.getSaddleDebugValue(entity)
                    + " detectedArmorState=" + LOTRGeoModelMumakil.getHowdahOrWarEquipmentDebugValue(entity)
                    + " shouldRenderSaddle=" + renderSaddle
                    + " shouldRenderHowdahOrWarEquipment=" + renderHowdahOrWarEquipment);
        }
    }

    /**
     * The exported test Geo currently contains animal body, saddle, and war kit in one model. Keep each layer
     * independent so normal saddle rendering does not pull in tusk spikes, ankle spikes, ropes, or the howdah.
     */
    private void applyEquipmentVisibility(GeoModel geoModel, boolean renderSaddle, boolean renderHowdahOrWarEquipment) {
        boolean showSaddle = renderSaddle;
        boolean showWarEquipment = renderSaddle && renderHowdahOrWarEquipment;

        // First reset every equipment bone to visible.
        // This clears sticky hidden flags from previous render states.
        this.applyBoneVisibility(geoModel, ALL_EQUIPMENT_BONES, true, true);

        // Then hide only what this state should not show.
        if (!showWarEquipment) {
            this.applyBoneVisibility(geoModel, WAR_EQUIPMENT_BONES, false, true);
        }

        if (!showSaddle) {
            this.applyBoneVisibility(geoModel, SADDLE_BONES, false, true);
        }
    }

    private void applyBoneVisibility(GeoModel geoModel, String[] boneNames, boolean visible, boolean logWarBones) {
        boolean hidden = !visible;
        boolean shouldLogThisPass = DEBUG_LOGS && logWarBones && !loggedWarBoneVisibilityStates[visible ? 1 : 0];

        for (int i = 0; i < boneNames.length; ++i) {
            GeoBone bone = this.getCachedBone(geoModel, boneNames[i]);

            if (shouldLogThisPass) {
                System.out.println("[LOTRMoreMobs] Mumakil Geo war bone visibility: bone="
                        + boneNames[i]
                        + " found=" + (bone != null)
                        + " requestedVisible=" + visible);
            }

            if (bone != null) {
                this.setBoneTreeVisibility(bone, hidden);
            }
        }

        if (shouldLogThisPass) {
            loggedWarBoneVisibilityStates[visible ? 1 : 0] = true;
        }
    }

    private void setBoneTreeVisibility(GeoBone geoBone, boolean hidden) {
        if (geoBone == null) {
            return;
        }

        geoBone.setHidden(hidden, false);
        geoBone.setCubesHidden(hidden);

        for (GeoBone childBone : this.getCachedChildBones(geoBone)) {
            this.setBoneTreeVisibility(childBone, hidden);
        }
    }

    private List<GeoBone> getCachedChildBones(GeoBone geoBone) {
        List<GeoBone> cached = this.childrenByBone.get(geoBone);
        if (cached != null) {
            return cached;
        }

        List<GeoBone> children = geoBone.childBones;
        if (children == null || children.isEmpty()) {
            cached = Collections.emptyList();
        } else {
            cached = Collections.unmodifiableList(
                    new ArrayList<GeoBone>(children)
            );
        }

        this.childrenByBone.put(geoBone, cached);
        return cached;
    }

    /**
     * MUMAKIL_STRIKE_RENDER_ISOLATION_V2_1
     *
     * GeckoLib caches and reuses the loaded GeoModel and its mutable
     * GeoBone objects. A strike sampled for one Mumakil can therefore leave
     * rotations behind for the next Mumakil rendered with that model.
     *
     * Clear every bone authored by either strike animation. This explicitly
     * includes the root "body" bone, which the normal idle/walk pose does not
     * otherwise overwrite.
     */
    private void resetMumakilStrikeBoneState(GeoModel geoModel) {
        this.resetBlockbenchAnimationBonesToDefault(
                geoModel,
                this.getBlockbenchAnimation(
                        BLOCKBENCH_STRIKE_LEFT_ANIMATION_NAME
                )
        );
        this.resetBlockbenchAnimationBonesToDefault(
                geoModel,
                this.getBlockbenchAnimation(
                        BLOCKBENCH_STRIKE_RIGHT_ANIMATION_NAME
                )
        );
    }

    private void resetBlockbenchAnimationBonesToDefault(
            GeoModel geoModel,
            BlockbenchAnimation animation
    ) {
        if (geoModel == null
                || animation == null
                || animation.boneRotationKeyframes == null) {
            return;
        }

        for (String boneName
                : animation.boneRotationKeyframes.keySet()) {
            this.setBoneRotation(
                    geoModel,
                    boneName,
                    degreesToRadians(
                            this.getDefaultBlockbenchBaseXDegrees(
                                    boneName
                            )
                    ),
                    0.0F,
                    0.0F
            );
        }
    }
    private void applyMumakilBoneAnimations(GeoModel geoModel, LOTREntityMumakil entity, float limbSwing, float limbSwingAmount, float ageInTicks, float partialTicks) {
        if (geoModel == null || entity == null) {
            return;
        }

        /*
         * Clear any strike pose left by the Mumakil rendered immediately
         * before this one.
         */
        this.resetMumakilStrikeBoneState(geoModel);
        float moveAmount = clamp(limbSwingAmount, 0.0F, 1.0F);
        float idleAmount = 1.0F - moveAmount * 0.45F;

        float idleSlow = ageInTicks * 0.055F;
        float idleMedium = ageInTicks * 0.085F;
        float walkPhase = limbSwing * 0.55F;

        float tailFlickProgress = getOccasionalProgress(entity, ageInTicks,
                AMBIENT_TAIL_FLIP_INTERVAL_TICKS,
                BLOCKBENCH_TAIL_FLIP_DURATION_TICKS,
                AMBIENT_TAIL_FLIP_CHANCE_MODULO,
                11);
        float earFlapProgress = getOccasionalProgress(entity, ageInTicks,
                AMBIENT_EAR_FLAP_INTERVAL_TICKS,
                BLOCKBENCH_EAR_FLAP_DURATION_TICKS,
                AMBIENT_EAR_FLAP_CHANCE_MODULO,
                71);


        /*
         * V8: trumpet is driven by the Blockbench-authored JSON loaded from resources.
         *
         * This deliberately does NOT use the old Java trumpet math. During the active trumpet window,
         * body_sway, upper_back, head, ears, and trunk bones are set from the Blockbench keyframes only.
         */
        float trumpetProgress =
                entity.getMumakilTrumpetAnimationProgress(
                        partialTicks
                );
        boolean blockbenchTrumpetActive = USE_BLOCKBENCH_TRUMPET
                && trumpetProgress >= 0.0F
                && this.getBlockbenchAnimation(BLOCKBENCH_TRUMPET_ANIMATION_NAME) != null;

        boolean blockbenchEarFlapActive = USE_BLOCKBENCH_EAR_FLAP
                && earFlapProgress >= 0.0F
                && this.getBlockbenchAnimation(BLOCKBENCH_EAR_FLAP_ANIMATION_NAME) != null;

        boolean blockbenchTailFlipActive = USE_BLOCKBENCH_TAIL_FLIP
                && tailFlickProgress >= 0.0F
                && this.getBlockbenchAnimation(BLOCKBENCH_TAIL_FLIP_ANIMATION_NAME) != null;

        float strikeProgress = this.getMumakilStrikeProgress(entity, partialTicks);
        boolean strikeLeft = this.shouldUseLeftStrike(entity, ageInTicks, strikeProgress);
        String strikeAnimationName = strikeLeft ? BLOCKBENCH_STRIKE_LEFT_ANIMATION_NAME : BLOCKBENCH_STRIKE_RIGHT_ANIMATION_NAME;
        if (this.getBlockbenchAnimation(strikeAnimationName) == null) {
            strikeAnimationName = strikeLeft ? BLOCKBENCH_STRIKE_RIGHT_ANIMATION_NAME : BLOCKBENCH_STRIKE_LEFT_ANIMATION_NAME;
        }
        boolean blockbenchStrikeActive = USE_BLOCKBENCH_STRIKES
                && strikeProgress > 0.0F
                && this.getBlockbenchAnimation(strikeAnimationName) != null;

        /*
         * Body/head/ear/trunk idle and walk motion.
         *
         * When the Blockbench trumpet is active, skip these keyed bones here and let the Blockbench
         * keyframes set them once, below. This prevents the old Java trumpet from visually bleeding through.
         */
        if (!blockbenchTrumpetActive) {
            float bodySwayZ = sin(idleSlow) * degreesToRadians(1.25F) * idleAmount;
            float bodySwayX = sin(walkPhase * 2.0F) * degreesToRadians(0.8F) * moveAmount;

            this.setBoneRotation(geoModel, "body_sway", bodySwayX, 0.0F, bodySwayZ);
            this.setBoneRotation(geoModel, "upper_back", 0.0F, 0.0F, 0.0F);

            float headX = sin(idleMedium + 0.8F) * degreesToRadians(1.0F) * idleAmount
                    + sin(walkPhase + 0.5F) * degreesToRadians(0.6F) * moveAmount;
            float headY = sin(idleSlow + 1.2F) * degreesToRadians(1.25F) * idleAmount;

            this.setBoneRotation(geoModel, "head", headX, headY, 0.0F);

            /*
             * Ear movement.
             *
             * Y is the face-fanning axis for the ears.
             */
            if (blockbenchEarFlapActive) {
                if (DEBUG_LOGS && !loggedBlockbenchEarFlapApplied) {
                    loggedBlockbenchEarFlapApplied = true;
                    System.out.println("[LOTRMoreMobs] Applying JSON Blockbench Mumakil ear flap keyframes. animation="
                            + BLOCKBENCH_EAR_FLAP_ANIMATION_NAME
                            + " build=" + RENDERER_BUILD_TAG);
                }
                this.applyJsonBlockbenchAnimation(geoModel, BLOCKBENCH_EAR_FLAP_ANIMATION_NAME, earFlapProgress);
            } else {
                float idleEarFan = sin(idleMedium + 1.6F) * degreesToRadians(1.5F) * idleAmount;

                this.setBoneRotation(geoModel, "left_ear", 0.0F, idleEarFan, 0.0F);
                this.setBoneRotation(geoModel, "right_ear", 0.0F, -idleEarFan, 0.0F);
            }

            /*
             * Normal trunk idle/walk motion.
             *
             * Keep these positive base values; they were the values that looked correct in-game.
             */
            float trunkSwingX = sin(idleSlow + 0.5F) * degreesToRadians(0.8F) * idleAmount
                    + sin(walkPhase) * degreesToRadians(0.8F) * moveAmount;
            float trunkSwingY = sin(idleMedium) * degreesToRadians(1.0F) * idleAmount
                    + sin(walkPhase * 0.75F) * degreesToRadians(0.4F) * moveAmount;

            this.setBoneRotation(geoModel, "trunk",
                    degreesToRadians(12.5F) + trunkSwingX,
                    trunkSwingY,
                    0.0F
            );

            this.setBoneRotation(geoModel, "trunk_01",
                    degreesToRadians(7.5F) + trunkSwingX * 0.50F,
                    -trunkSwingY * 0.20F,
                    0.0F
            );

            this.setBoneRotation(geoModel, "trunk_02",
                    trunkSwingX * 0.35F,
                    trunkSwingY * 0.16F,
                    0.0F
            );

            this.setBoneRotation(geoModel, "trunk_03",
                    trunkSwingX * 0.22F,
                    -trunkSwingY * 0.12F,
                    0.0F
            );

            this.setBoneRotation(geoModel, "trunk_04",
                    trunkSwingX * 0.12F,
                    trunkSwingY * 0.08F,
                    0.0F
            );

            this.setBoneRotation(geoModel, "trunk_05",
                    trunkSwingX * 0.06F,
                    -trunkSwingY * 0.06F,
                    0.0F
            );
        } else {
            if (DEBUG_LOGS && !loggedBlockbenchTrumpetApplied) {
                loggedBlockbenchTrumpetApplied = true;
                System.out.println("[LOTRMoreMobs] Applying JSON Blockbench Mumakil trumpet keyframes. progress="
                        + trumpetProgress
                        + " resource=" + BLOCKBENCH_TRUMPET_RESOURCE
                        + " animation=" + BLOCKBENCH_TRUMPET_ANIMATION_NAME
                        + " build=" + RENDERER_BUILD_TAG);
            }

            this.applyJsonBlockbenchTrumpetAnimation(geoModel, trumpetProgress);
        }

        /*
         * Tail idle swish plus rare fly-shooing flick.
         *
         * Z is the side-to-side tail axis.
         * The flick starts with a high-amplitude snap, then decays into smaller
         * follow-through swings like momentum traveling down the tail.
         */
        float tailIdle = sin(idleMedium + 2.4F) * degreesToRadians(2.5F) * idleAmount
                + sin(walkPhase + 1.5F) * degreesToRadians(1.25F) * moveAmount;

        if (blockbenchTailFlipActive) {
            if (DEBUG_LOGS && !loggedBlockbenchTailFlipApplied) {
                loggedBlockbenchTailFlipApplied = true;
                System.out.println("[LOTRMoreMobs] Applying JSON Blockbench Mumakil tail flip keyframes. animation="
                        + BLOCKBENCH_TAIL_FLIP_ANIMATION_NAME
                        + " build=" + RENDERER_BUILD_TAG);
            }
            this.applyJsonBlockbenchAnimation(geoModel, BLOCKBENCH_TAIL_FLIP_ANIMATION_NAME, tailFlickProgress);
        } else {
            this.setBoneRotation(geoModel, "tail_upper", 0.0F, 0.0F, tailIdle * 0.15F);
            this.setBoneRotation(geoModel, "tail_middle", 0.0F, 0.0F, tailIdle * 0.40F);
            this.setBoneRotation(geoModel, "tail_lower", 0.0F, 0.0F, tailIdle * 0.75F);
        }

        /*
         * Simple four-leg walking cycle.
         */
        float stepA = sin(walkPhase);
        float stepB = sin(walkPhase + 3.1415927F);

        float frontLegSwing = degreesToRadians(13.0F) * moveAmount;
        float backLegSwing = degreesToRadians(11.0F) * moveAmount;

        this.setBoneRotation(geoModel, "front_right_leg", stepA * frontLegSwing, 0.0F, 0.0F);
        this.setBoneRotation(geoModel, "back_left_leg", stepA * backLegSwing, 0.0F, 0.0F);

        this.setBoneRotation(geoModel, "front_left_leg", stepB * frontLegSwing, 0.0F, 0.0F);
        this.setBoneRotation(geoModel, "back_right_leg", stepB * backLegSwing, 0.0F, 0.0F);

        /*
         * Strike animations run last so they can override head/body/trunk/leg bones during attack swings.
         * The selected side alternates per swing. If one side is missing from JSON, the other side is used.
         */
        if (blockbenchStrikeActive) {
            if (DEBUG_LOGS && !loggedBlockbenchStrikeApplied) {
                loggedBlockbenchStrikeApplied = true;
                System.out.println("[LOTRMoreMobs] Applying JSON Blockbench Mumakil strike keyframes. animation="
                        + strikeAnimationName
                        + " progress=" + strikeProgress
                        + " build=" + RENDERER_BUILD_TAG);
            }
            this.applyJsonBlockbenchAnimation(geoModel, strikeAnimationName, strikeProgress);
        }
    }

    private void applyJsonBlockbenchTrumpetAnimation(GeoModel geoModel, float progress) {
        BlockbenchAnimation animation = this.getBlockbenchTrumpetAnimation();
        if (animation == null) {
            /*
             * Do not fall back to the old Java trumpet here. If the JSON is missing, failing loudly is better
             * than accidentally testing stale Java motion and thinking the Blockbench file is working.
             */
            return;
        }

        float lengthSeconds = animation.lengthSeconds > 0.0F
                ? animation.lengthSeconds
                : BLOCKBENCH_TRUMPET_FALLBACK_LENGTH_SECONDS;
        float timeSeconds = clamp(progress, 0.0F, 1.0F) * lengthSeconds;

        this.setBlockbenchJsonRotation(geoModel, animation, "body_sway", timeSeconds, 0.0F, 0.0F, 0.0F);
        this.setBlockbenchJsonRotation(geoModel, animation, "upper_back", timeSeconds, 0.0F, 0.0F, 0.0F);
        this.setBlockbenchJsonRotation(geoModel, animation, "head", timeSeconds, 0.0F, 0.0F, 0.0F);
        this.setBlockbenchJsonRotation(geoModel, animation, "left_ear", timeSeconds, 0.0F, 0.0F, 0.0F);
        this.setBlockbenchJsonRotation(geoModel, animation, "right_ear", timeSeconds, 0.0F, 0.0F, 0.0F);

        /*
         * The Blockbench keyframes are treated as animation offsets.
         * These two trunk base values are the in-game-positive values that already looked correct.
         */
        this.setBlockbenchJsonRotation(geoModel, animation, "trunk", timeSeconds, 12.5F, 0.0F, 0.0F);
        this.setBlockbenchJsonRotation(geoModel, animation, "trunk_01", timeSeconds, 7.5F, 0.0F, 0.0F);
        this.setBlockbenchJsonRotation(geoModel, animation, "trunk_02", timeSeconds, 0.0F, 0.0F, 0.0F);
        this.setBlockbenchJsonRotation(geoModel, animation, "trunk_03", timeSeconds, 0.0F, 0.0F, 0.0F);
        this.setBlockbenchJsonRotation(geoModel, animation, "trunk_04", timeSeconds, 0.0F, 0.0F, 0.0F);
        this.setBlockbenchJsonRotation(geoModel, animation, "trunk_05", timeSeconds, 0.0F, 0.0F, 0.0F);
    }

    private boolean applyJsonBlockbenchAnimation(GeoModel geoModel, String animationName, float progress) {
        BlockbenchAnimation animation = this.getBlockbenchAnimation(animationName);
        if (animation == null) {
            return false;
        }

        float lengthSeconds = animation.lengthSeconds > 0.0F
                ? animation.lengthSeconds
                : 1.0F;
        float timeSeconds = clamp(progress, 0.0F, 1.0F) * lengthSeconds;

        for (String boneName : animation.boneRotationKeyframes.keySet()) {
            this.setBlockbenchJsonRotation(
                    geoModel,
                    animation,
                    boneName,
                    timeSeconds,
                    this.getDefaultBlockbenchBaseXDegrees(boneName),
                    0.0F,
                    0.0F
            );
        }

        return true;
    }

    private float getDefaultBlockbenchBaseXDegrees(String boneName) {
        if ("trunk".equals(boneName)) {
            return 12.5F;
        }

        if ("trunk_01".equals(boneName)) {
            return 7.5F;
        }

        return 0.0F;
    }

    private float getMumakilStrikeProgress(LOTREntityMumakil entity, float partialTicks) {
        if (entity == null) {
            return 0.0F;
        }

        return clamp(
                entity.getMumakilStrikeAnimationProgress(partialTicks),
                0.0F,
                1.0F
        );
    }

    private boolean shouldUseLeftStrike(LOTREntityMumakil entity, float ageInTicks, float strikeProgress) {
        if (entity != null && strikeProgress > 0.0F) {
            return entity.isMumakilStrikeAnimationLeft();
        }

        int entityId = entity == null ? 0 : entity.getEntityId();
        int estimatedStartTick = (int)(ageInTicks - strikeProgress * (float)BLOCKBENCH_STRIKE_DURATION_TICKS);
        int strikeIndex = estimatedStartTick / BLOCKBENCH_STRIKE_DURATION_TICKS;
        return positiveModulo(entityId + strikeIndex, 2) == 0;
    }

    private void setBlockbenchJsonRotation(GeoModel geoModel, BlockbenchAnimation animation, String boneName, float timeSeconds,
                                           float baseXDegrees, float baseYDegrees, float baseZDegrees) {
        float[][] keyframes = animation.boneRotationKeyframes.get(boneName);

        float xDegrees = baseXDegrees;
        float yDegrees = baseYDegrees;
        float zDegrees = baseZDegrees;

        if (keyframes != null && keyframes.length > 0) {
            xDegrees += sampleBlockbenchKeyframes(keyframes, timeSeconds, 1) * BLOCKBENCH_TRUMPET_X_SIGN;
            yDegrees += sampleBlockbenchKeyframes(keyframes, timeSeconds, 2);
            zDegrees += sampleBlockbenchKeyframes(keyframes, timeSeconds, 3);
        }

        this.setBoneRotation(
                geoModel,
                boneName,
                degreesToRadians(xDegrees),
                degreesToRadians(yDegrees),
                degreesToRadians(zDegrees)
        );
    }

    private BlockbenchAnimation getBlockbenchTrumpetAnimation() {
        return this.getBlockbenchAnimation(BLOCKBENCH_TRUMPET_ANIMATION_NAME);
    }

    private BlockbenchAnimation getBlockbenchAnimation(String animationName) {
        Map<String, BlockbenchAnimation> animations = this.getBlockbenchAnimations();
        if (animations == null || animationName == null) {
            return null;
        }
        return animations.get(animationName);
    }

    private Map<String, BlockbenchAnimation> getBlockbenchAnimations() {
        if (attemptedLoadBlockbenchAnimationFile) {
            return loadedBlockbenchAnimations;
        }

        attemptedLoadBlockbenchAnimationFile = true;
        loadedBlockbenchAnimations = new LinkedHashMap<String, BlockbenchAnimation>();

        Reader reader = null;
        try {
            IResource resource = Minecraft.getMinecraft().getResourceManager().getResource(BLOCKBENCH_TRUMPET_RESOURCE);
            InputStream inputStream = resource.getInputStream();
            reader = new InputStreamReader(inputStream, "UTF-8");

            JsonElement rootElement = new JsonParser().parse(reader);
            if (rootElement == null || !rootElement.isJsonObject()) {
                System.out.println("[LOTRMoreMobs] Mumakil Blockbench JSON did not contain a root object: resource="
                        + BLOCKBENCH_TRUMPET_RESOURCE);
                return loadedBlockbenchAnimations;
            }

            JsonObject rootObject = rootElement.getAsJsonObject();
            JsonObject animationsObject = getJsonObject(rootObject, "animations");
            if (animationsObject == null) {
                System.out.println("[LOTRMoreMobs] Mumakil Blockbench JSON had no animations object: resource="
                        + BLOCKBENCH_TRUMPET_RESOURCE);
                return loadedBlockbenchAnimations;
            }

            for (Map.Entry<String, JsonElement> animationEntry : animationsObject.entrySet()) {
                if (animationEntry == null || animationEntry.getKey() == null) {
                    continue;
                }

                BlockbenchAnimation animation = parseBlockbenchAnimation(rootObject, animationEntry.getKey());
                if (animation != null) {
                    loadedBlockbenchAnimations.put(animationEntry.getKey(), animation);
                }
            }

            System.out.println("[LOTRMoreMobs] Loaded Mumakil Blockbench JSON animations: resource="
                    + BLOCKBENCH_TRUMPET_RESOURCE
                    + " animationCount=" + loadedBlockbenchAnimations.size()
                    + " names=" + loadedBlockbenchAnimations.keySet()
                    + " build=" + RENDERER_BUILD_TAG);
        } catch (Exception e) {
            System.out.println("[LOTRMoreMobs] Failed to load Mumakil Blockbench JSON animations: resource="
                    + BLOCKBENCH_TRUMPET_RESOURCE
                    + " error=" + e.getClass().getName()
                    + ": " + e.getMessage());
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception e) {
                }
            }
        }

        return loadedBlockbenchAnimations;
    }

    private static BlockbenchAnimation parseBlockbenchAnimation(JsonObject rootObject, String animationName) {
        if (rootObject == null || animationName == null) {
            return null;
        }

        JsonObject animationsObject = getJsonObject(rootObject, "animations");
        if (animationsObject == null || !animationsObject.has(animationName)) {
            return null;
        }

        JsonElement animationElement = animationsObject.get(animationName);
        if (animationElement == null || !animationElement.isJsonObject()) {
            return null;
        }

        JsonObject animationObject = animationElement.getAsJsonObject();
        BlockbenchAnimation animation = new BlockbenchAnimation();
        animation.lengthSeconds = getJsonFloat(animationObject, "animation_length", BLOCKBENCH_TRUMPET_FALLBACK_LENGTH_SECONDS);
        animation.boneRotationKeyframes = new LinkedHashMap<String, float[][]>();

        JsonObject bonesObject = getJsonObject(animationObject, "bones");
        if (bonesObject == null) {
            return animation;
        }

        for (Map.Entry<String, JsonElement> boneEntry : bonesObject.entrySet()) {
            if (boneEntry == null || boneEntry.getKey() == null || boneEntry.getValue() == null
                    || !boneEntry.getValue().isJsonObject()) {
                continue;
            }

            JsonObject boneObject = boneEntry.getValue().getAsJsonObject();
            JsonElement rotationElement = boneObject.get("rotation");
            float[][] rotationKeyframes = parseBlockbenchRotationKeyframes(rotationElement);
            if (rotationKeyframes != null && rotationKeyframes.length > 0) {
                animation.boneRotationKeyframes.put(boneEntry.getKey(), rotationKeyframes);
            }
        }

        return animation;
    }

    private static float[][] parseBlockbenchRotationKeyframes(JsonElement rotationElement) {
        if (rotationElement == null || rotationElement.isJsonNull()) {
            return null;
        }

        List<float[]> keyframes = new ArrayList<float[]>();

        if (rotationElement.isJsonArray()) {
            float[] vector = readBlockbenchVector(rotationElement);
            if (vector != null) {
                keyframes.add(new float[] {0.0F, vector[0], vector[1], vector[2]});
            }
        } else if (rotationElement.isJsonObject()) {
            JsonObject rotationObject = rotationElement.getAsJsonObject();

            /*
             * Some Blockbench exports may put a direct vector object here instead of a keyed timeline.
             */
            if (rotationObject.has("vector") || rotationObject.has("post") || rotationObject.has("pre")) {
                float[] vector = readBlockbenchVector(rotationObject);
                if (vector != null) {
                    keyframes.add(new float[] {0.0F, vector[0], vector[1], vector[2]});
                }
            } else {
                for (Map.Entry<String, JsonElement> keyframeEntry : rotationObject.entrySet()) {
                    if (keyframeEntry == null || keyframeEntry.getKey() == null) {
                        continue;
                    }

                    float timeSeconds;
                    try {
                        timeSeconds = Float.parseFloat(keyframeEntry.getKey());
                    } catch (Exception e) {
                        continue;
                    }

                    float[] vector = readBlockbenchVector(keyframeEntry.getValue());
                    if (vector != null) {
                        keyframes.add(new float[] {timeSeconds, vector[0], vector[1], vector[2]});
                    }
                }
            }
        }

        if (keyframes.isEmpty()) {
            return null;
        }

        Collections.sort(keyframes, new Comparator<float[]>() {
            public int compare(float[] left, float[] right) {
                if (left[0] < right[0]) {
                    return -1;
                }
                if (left[0] > right[0]) {
                    return 1;
                }
                return 0;
            }
        });

        float[][] result = new float[keyframes.size()][4];
        for (int i = 0; i < keyframes.size(); ++i) {
            result[i] = keyframes.get(i);
        }
        return result;
    }

    private static float[] readBlockbenchVector(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }

        if (element.isJsonArray()) {
            return readBlockbenchVectorArray(element.getAsJsonArray());
        }

        if (!element.isJsonObject()) {
            return null;
        }

        JsonObject object = element.getAsJsonObject();

        if (object.has("post")) {
            float[] vector = readBlockbenchVector(object.get("post"));
            if (vector != null) {
                return vector;
            }
        }

        if (object.has("vector")) {
            float[] vector = readBlockbenchVector(object.get("vector"));
            if (vector != null) {
                return vector;
            }
        }

        if (object.has("pre")) {
            return readBlockbenchVector(object.get("pre"));
        }

        return null;
    }

    private static float[] readBlockbenchVectorArray(JsonArray array) {
        if (array == null || array.size() <= 0) {
            return null;
        }

        float[] vector = new float[] {0.0F, 0.0F, 0.0F};
        int max = Math.min(array.size(), 3);
        for (int i = 0; i < max; ++i) {
            try {
                vector[i] = array.get(i).getAsFloat();
            } catch (Exception e) {
                vector[i] = 0.0F;
            }
        }
        return vector;
    }

    private static JsonObject getJsonObject(JsonObject object, String key) {
        if (object == null || key == null || !object.has(key)) {
            return null;
        }

        JsonElement element = object.get(key);
        if (element == null || !element.isJsonObject()) {
            return null;
        }

        return element.getAsJsonObject();
    }

    private static float getJsonFloat(JsonObject object, String key, float fallback) {
        if (object == null || key == null || !object.has(key)) {
            return fallback;
        }

        try {
            return object.get(key).getAsFloat();
        } catch (Exception e) {
            return fallback;
        }
    }

    private static float sampleBlockbenchKeyframes(float[][] keyframes, float timeSeconds, int componentIndex) {
        if (keyframes == null || keyframes.length <= 0) {
            return 0.0F;
        }

        if (componentIndex < 1 || componentIndex > 3) {
            return 0.0F;
        }

        if (timeSeconds <= keyframes[0][0]) {
            return keyframes[0][componentIndex];
        }

        int lastIndex = keyframes.length - 1;
        if (timeSeconds >= keyframes[lastIndex][0]) {
            return keyframes[lastIndex][componentIndex];
        }

        for (int i = 0; i < lastIndex; ++i) {
            float[] start = keyframes[i];
            float[] end = keyframes[i + 1];

            float startTime = start[0];
            float endTime = end[0];

            if (timeSeconds >= startTime && timeSeconds <= endTime) {
                float duration = endTime - startTime;
                if (duration <= 0.0001F) {
                    return end[componentIndex];
                }

                float localProgress = (timeSeconds - startTime) / duration;
                localProgress = smoothStep(localProgress);

                return start[componentIndex] + (end[componentIndex] - start[componentIndex]) * localProgress;
            }
        }

        return keyframes[lastIndex][componentIndex];
    }

    private static class BlockbenchAnimation {
        private float lengthSeconds;
        private Map<String, float[][]> boneRotationKeyframes;
    }

    private void setBoneRotation(GeoModel geoModel, String boneName, float rotationX, float rotationY, float rotationZ) {
        GeoBone geoBone = this.getCachedBone(geoModel, boneName);
        if (geoBone == null) {
            return;
        }

        geoBone.setRotationX(rotationX);
        geoBone.setRotationY(rotationY);
        geoBone.setRotationZ(rotationZ);
    }

    private GeoBone getCachedBone(GeoModel geoModel, String boneName) {
        if (geoModel == null || boneName == null) {
            return null;
        }

        Map<String, GeoBone> modelBones = this.bonesByModel.get(geoModel);
        if (modelBones == null) {
            modelBones = new HashMap<String, GeoBone>();
            this.bonesByModel.put(geoModel, modelBones);
        }

        if (modelBones.containsKey(boneName)) {
            return modelBones.get(boneName);
        }

        Optional<GeoBone> resolved = geoModel.getBone(boneName);
        GeoBone bone = resolved.isPresent() ? resolved.get() : null;
        modelBones.put(boneName, bone);
        return bone;
    }

    private static float degreesToRadians(float degrees) {
        return degrees * 0.017453292F;
    }

    private static float sin(float value) {
        return (float)Math.sin((double)value);
    }

    private static float clamp(float value, float min, float max) {
        if (value < min) {
            return min;
        }

        if (value > max) {
            return max;
        }

        return value;
    }

    private static float smoothStep(float value) {
        value = clamp(value, 0.0F, 1.0F);
        return value * value * (3.0F - 2.0F * value);
    }

    private static float getDelayedTrumpetValue(float progress, float delay, float riseDuration, float releaseStart) {
        if (progress < delay) {
            return 0.0F;
        }

        float local = clamp((progress - delay) / (1.0F - delay), 0.0F, 1.0F);
        float rise = smoothStep(local / riseDuration);
        float release = 1.0F - smoothStep((local - releaseStart) / (1.0F - releaseStart));
        return rise * release;
    }

    private static float getDelayedTrumpetFollowThrough(float progress, float delay) {
        if (progress < delay) {
            return 0.0F;
        }

        float local = clamp((progress - delay) / (1.0F - delay), 0.0F, 1.0F);

        /*
         * One overshoot lobe followed by a smaller recoil lobe. This gives
         * follow-through/settling without adding a constant sine wobble.
         */
        return getWindowedBump(local, 0.18F, 0.42F)
                - getWindowedBump(local, 0.52F, 0.88F) * 0.42F;
    }

    private static float getWindowedBump(float progress, float start, float end) {
        if (end <= start || progress <= start || progress >= end) {
            return 0.0F;
        }

        return smoothBump((progress - start) / (end - start));
    }

    private static float smoothBump(float value) {
        value = smoothStep(value);
        return value * (1.0F - value) * 4.0F;
    }

    private static float cos(float value) {
        return (float)Math.cos((double)value);
    }

    private static float getOccasionalProgress(LOTREntityMumakil entity, float ageInTicks, int intervalTicks, int durationTicks, int chanceModulo, int seed) {
        if (entity == null || intervalTicks <= 0 || durationTicks <= 0 || chanceModulo <= 0) {
            return -1.0F;
        }

        int tick = (int)ageInTicks;
        int cycle = tick / intervalTicks;
        int localTick = tick % intervalTicks;

        if (localTick >= durationTicks) {
            return -1.0F;
        }

        int hash = hashAnimationEvent(entity.getEntityId(), cycle, seed);
        if (positiveModulo(hash, chanceModulo) != 0) {
            return -1.0F;
        }

        return (float)localTick / (float)durationTicks;
    }

    private static float getOccasionalPulse(LOTREntityMumakil entity, float ageInTicks, int intervalTicks, int durationTicks, int chanceModulo, int seed) {
        if (entity == null || intervalTicks <= 0 || durationTicks <= 0 || chanceModulo <= 0) {
            return 0.0F;
        }

        int tick = (int)ageInTicks;
        int cycle = tick / intervalTicks;
        int localTick = tick % intervalTicks;

        if (localTick >= durationTicks) {
            return 0.0F;
        }

        int hash = hashAnimationEvent(entity.getEntityId(), cycle, seed);
        if (positiveModulo(hash, chanceModulo) != 0) {
            return 0.0F;
        }

        float progress = (float)localTick / (float)durationTicks;

        /*
         * Smooth in/out pulse.
         * 0 at the start, 1 in the middle, 0 at the end.
         */
        return sin(progress * 3.1415927F);
    }

    private static int hashAnimationEvent(int entityId, int cycle, int seed) {
        int value = entityId;
        value = value * 31 + cycle;
        value = value * 31 + seed;
        value ^= value << 13;
        value ^= value >> 17;
        value ^= value << 5;
        return value;
    }

    private static int positiveModulo(int value, int modulo) {
        int result = value % modulo;
        if (result < 0) {
            result += modulo;
        }
        return result;
    }

    /**
     * GeckoLib-Unofficial's default GeoEntityRenderer#getUniqueID path calls EntityLivingBase.getUniqueID()
     * through a name that crashes in this ForgeGradle 1.2 / 1.7.10 deobfuscated runtime. LOTR's creative
     * mob-spawner preview also renders temporary entities, so use the vanilla entity id as a safe per-instance
     * animation key instead of entering the broken func_110124_au call path.
     */
    public Integer getUniqueID(LOTREntityMumakil animatable) {
        return Integer.valueOf(animatable == null ? 0 : animatable.getEntityId());
    }

    public ResourceLocation getEntityTexture(Entity entity) {
        if (entity instanceof LOTREntityMumakil) {
            return this.modelProvider.getTextureLocation((LOTREntityMumakil)entity);
        }
        return LOTRGeoModelMumakil.WILD_TEXTURE;
    }

    public Color getRenderColor(LOTREntityMumakil animatable, float partialTicks) {
        return Color.ofRGBA(255, 255, 255, 255);
    }

    private boolean canLoadResource(ResourceLocation resourceLocation) {
        if (resourceLocation == null) {
            return false;
        }

        Boolean cached = this.resourceAvailability.get(resourceLocation);
        if (cached != null) {
            return cached.booleanValue();
        }

        InputStream stream = null;
        boolean available = false;
        try {
            IResource resource = Minecraft.getMinecraft()
                    .getResourceManager()
                    .getResource(resourceLocation);
            if (resource != null) {
                stream = resource.getInputStream();
                available = stream != null;

                /*
                 * SimpleResource owns a separate metadata stream in 1.7.10.
                 * Asking for any section parses and closes it when present.
                 */
                if (resource.hasMetadata()) {
                    try {
                        resource.getMetadata("texture");
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception e) {
            available = false;
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (Exception ignored) {
                }
            }
        }

        this.resourceAvailability.put(
                resourceLocation,
                Boolean.valueOf(available)
        );
        return available;
    }
}

