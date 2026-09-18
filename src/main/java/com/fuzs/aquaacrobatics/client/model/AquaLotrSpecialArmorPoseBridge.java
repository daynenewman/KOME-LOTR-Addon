package com.fuzs.aquaacrobatics.client.model;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.client.model.ModelBiped;

import com.fuzs.aquaacrobatics.entity.Pose;

/**
 * Carries the final Aqua body pose across LOTR's cached special-armor model
 * boundary. All entries are weak and every correction is also checked against
 * the entity currently being rendered, because a RenderPlayer and its cached
 * armor models are shared by local and remote players.
 */
public final class AquaLotrSpecialArmorPoseBridge {

    private static final Object LOCK = new Object();
    private static final Map<ModelBiped, WeakReference<ModelBiped>> SPECIAL_ARMOR_BODIES =
        new WeakHashMap<ModelBiped, WeakReference<ModelBiped>>();
    private static final Map<ModelBiped, PoseAuthority> BODY_POSES =
        new WeakHashMap<ModelBiped, PoseAuthority>();
    private static final ThreadLocal<RenderContext> RENDER_CONTEXT = new ThreadLocal<RenderContext>() {
        @Override
        protected RenderContext initialValue() {
            return new RenderContext();
        }
    };

    private AquaLotrSpecialArmorPoseBridge() {}

    /** Starts a new RenderPlayer scope, invalidating every prior pose snapshot. */
    public static void beginPlayerRender(Object entity) {
        RenderContext context = RENDER_CONTEXT.get();
        ++context.generation;
        context.entity = new WeakReference<Object>(entity);
    }

    /** Object signature is stable in both deobfuscated and raw 1.7.10 bytecode. */
    public static void associateSpecialArmor(Object specialArmor, Object body) {
        if (!(specialArmor instanceof ModelBiped)) return;

        ModelBiped specialModel = (ModelBiped) specialArmor;
        synchronized (LOCK) {
            if (body instanceof ModelBiped && body != specialArmor) {
                SPECIAL_ARMOR_BODIES.put(specialModel, new WeakReference<ModelBiped>((ModelBiped) body));
            } else {
                SPECIAL_ARMOR_BODIES.remove(specialModel);
            }
        }
    }

    static void clearPoseAuthority(ModelBiped body) {
        synchronized (LOCK) {
            BODY_POSES.remove(body);
        }
    }

    static void recordPoseAuthority(ModelBiped body, Object entity, Pose pose) {
        if (entity == null || pose == null) {
            clearPoseAuthority(body);
            return;
        }
        RenderContext context = RENDER_CONTEXT.get();
        if (context.entity.get() != entity) {
            clearPoseAuthority(body);
            return;
        }
        synchronized (LOCK) {
            BODY_POSES.put(body, new PoseAuthority(entity, pose, context.generation));
        }
    }

    static void applyBodyPoseAndRecordAuthority(ModelBiped body, Object entity, Pose pose) {
        AquaPlayerRenderLogic.applyPosePivots(
            pose,
            body.bipedHead,
            body.bipedHeadwear,
            body.bipedBody,
            body.bipedRightArm,
            body.bipedLeftArm,
            body.bipedRightLeg,
            body.bipedLeftLeg);
        recordPoseAuthority(body, entity, pose);
    }

    /**
     * Runs after the special model's own LOTR angle calculation. Only a model
     * selected by LOTRArmorModels, whose associated body established a modern
     * Aqua pose for this exact entity, can reach the canonical pivot helper.
     */
    public static void applyAfterLotrAngles(Object model, Object entity) {
        if (!(model instanceof ModelBiped) || entity == null) return;

        ModelBiped specialModel = (ModelBiped) model;
        Pose pose;
        RenderContext context = RENDER_CONTEXT.get();
        if (context.entity.get() != entity) return;
        synchronized (LOCK) {
            WeakReference<ModelBiped> bodyReference = SPECIAL_ARMOR_BODIES.get(specialModel);
            if (bodyReference == null) return;

            ModelBiped body = bodyReference.get();
            if (body == null) {
                SPECIAL_ARMOR_BODIES.remove(specialModel);
                return;
            }

            PoseAuthority authority = BODY_POSES.get(body);
            if (authority == null || authority.entity.get() != entity
                || authority.generation != context.generation) return;
            pose = authority.pose;
        }

        AquaPlayerRenderLogic.applyPosePivots(
            pose,
            specialModel.bipedHead,
            specialModel.bipedHeadwear,
            specialModel.bipedBody,
            specialModel.bipedRightArm,
            specialModel.bipedLeftArm,
            specialModel.bipedRightLeg,
            specialModel.bipedLeftLeg);
    }

    static void resetForTests() {
        synchronized (LOCK) {
            SPECIAL_ARMOR_BODIES.clear();
            BODY_POSES.clear();
        }
        RENDER_CONTEXT.remove();
    }

    private static final class PoseAuthority {
        private final WeakReference<Object> entity;
        private final Pose pose;
        private final long generation;

        private PoseAuthority(Object entity, Pose pose, long generation) {
            this.entity = new WeakReference<Object>(entity);
            this.pose = pose;
            this.generation = generation;
        }
    }

    private static final class RenderContext {
        private long generation;
        private WeakReference<Object> entity = new WeakReference<Object>(null);
    }
}
