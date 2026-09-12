package com.enovak.lotrmoremobs.siege.client.render;

import java.lang.reflect.Field;
import java.util.IdentityHashMap;
import java.util.Map;

import com.enovak.lotrmoremobs.siege.ram.EntityBattleRam;

import lotr.common.entity.npc.LOTREntityNPC;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraftforge.client.event.RenderLivingEvent;

/**
 * Client-only pose lock for the arm each ram carrier has nearest the ram.
 *
 * <p>The NPC still uses its normal walking animation for both legs and the
 * outside arm.  Only the inside arm is replaced for the duration of that one
 * render call with a delegating ModelRenderer that ignores the walk swing and
 * renders a small fixed "holding the ram" pose.</p>
 */
public final class RamCarrierArmPose {

    private static final float HOLD_PITCH = -0.20F;
    private static final float HOLD_INWARD_ROLL = 0.18F;

    private static final Field MAIN_MODEL_FIELD = findRendererField(
            "mainModel",
            "field_77045_g"
    );

    private static final Field RENDER_PASS_MODEL_FIELD = findRendererField(
            "renderPassModel",
            "field_77046_h"
    );

    private static final Map<LOTREntityNPC, PoseState> ACTIVE =
            new IdentityHashMap<LOTREntityNPC, PoseState>();

    private RamCarrierArmPose() {
    }

    public static void begin(
            RenderLivingEvent.Pre event,
            LOTREntityNPC crew,
            int slot
    ) {
        if (event == null
                || event.renderer == null
                || crew == null
                || slot < 0
                || slot >= EntityBattleRam.CREW_SLOT_COUNT) {
            return;
        }

        end(crew);

        /*
         * Runtime testing establishes the visual inside arm directly from the
         * carrier's formation side. Positive local X uses the right arm;
         * negative local X uses the left arm. This intentionally looks
         * opposite to the earlier model-space assumption because LOTR's NPC
         * renderer applies its own facing/model transforms before ModelBiped.
         */
        boolean lockRightArm =
                EntityBattleRam.getCrewLocalX(slot) > 0.0D;

        ModelBase mainModel = getRendererModel(
                event.renderer,
                MAIN_MODEL_FIELD
        );

        ModelBase passModel = getRendererModel(
                event.renderer,
                RENDER_PASS_MODEL_FIELD
        );

        ArmSwap mainSwap = ArmSwap.install(
                mainModel,
                lockRightArm
        );

        ArmSwap passSwap = passModel == mainModel
                ? null
                : ArmSwap.install(
                        passModel,
                        lockRightArm
                );

        if (mainSwap != null || passSwap != null) {
            ACTIVE.put(
                    crew,
                    new PoseState(mainSwap, passSwap)
            );
        }
    }

    public static void end(LOTREntityNPC crew) {
        if (crew == null) {
            return;
        }

        PoseState state = ACTIVE.remove(crew);
        if (state != null) {
            state.restore();
        }
    }

    public static void restoreAll() {
        if (ACTIVE.isEmpty()) {
            return;
        }

        PoseState[] states = ACTIVE.values().toArray(
                new PoseState[ACTIVE.size()]
        );
        ACTIVE.clear();

        for (PoseState state : states) {
            if (state != null) {
                state.restore();
            }
        }
    }

    private static ModelBase getRendererModel(
            RendererLivingEntity renderer,
            Field field
    ) {
        if (renderer == null || field == null) {
            return null;
        }

        try {
            Object value = field.get(renderer);
            return value instanceof ModelBase
                    ? (ModelBase)value
                    : null;
        } catch (IllegalAccessException ignored) {
            return null;
        }
    }

    private static Field findRendererField(String... names) {
        for (String name : names) {
            try {
                Field field = RendererLivingEntity.class
                        .getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                // Try the next MCP/SRG name.
            }
        }
        return null;
    }

    private static final class PoseState {
        private final ArmSwap mainSwap;
        private final ArmSwap passSwap;

        private PoseState(
                ArmSwap mainSwap,
                ArmSwap passSwap
        ) {
            this.mainSwap = mainSwap;
            this.passSwap = passSwap;
        }

        private void restore() {
            if (mainSwap != null) {
                mainSwap.restore();
            }
            if (passSwap != null) {
                passSwap.restore();
            }
        }
    }

    private static final class ArmSwap {
        private final ModelBiped model;
        private final boolean rightArm;
        private final ModelRenderer original;
        private final FixedCarrierArmRenderer replacement;

        private ArmSwap(
                ModelBiped model,
                boolean rightArm,
                ModelRenderer original,
                FixedCarrierArmRenderer replacement
        ) {
            this.model = model;
            this.rightArm = rightArm;
            this.original = original;
            this.replacement = replacement;
        }

        private static ArmSwap install(
                ModelBase modelBase,
                boolean rightArm
        ) {
            if (!(modelBase instanceof ModelBiped)) {
                return null;
            }

            ModelBiped model = (ModelBiped)modelBase;
            ModelRenderer original = rightArm
                    ? model.bipedRightArm
                    : model.bipedLeftArm;

            if (original == null
                    || original instanceof FixedCarrierArmRenderer) {
                return null;
            }

            FixedCarrierArmRenderer replacement =
                    new FixedCarrierArmRenderer(
                            model,
                            original,
                            rightArm
                    );

            if (rightArm) {
                model.bipedRightArm = replacement;
            } else {
                model.bipedLeftArm = replacement;
            }

            return new ArmSwap(
                    model,
                    rightArm,
                    original,
                    replacement
            );
        }

        private void restore() {
            if (rightArm) {
                if (model.bipedRightArm == replacement) {
                    model.bipedRightArm = original;
                }
            } else if (model.bipedLeftArm == replacement) {
                model.bipedLeftArm = original;
            }
        }
    }

    private static final class FixedCarrierArmRenderer
            extends ModelRenderer {

        private final ModelRenderer delegate;
        private final boolean rightArm;

        private FixedCarrierArmRenderer(
                ModelBase owner,
                ModelRenderer delegate,
                boolean rightArm
        ) {
            super(owner);
            this.delegate = delegate;
            this.rightArm = rightArm;

            /* ModelRenderer registers itself with ModelBase on construction.
             * This proxy is swapped into a biped field only for one render and
             * must not accumulate in the model's bookkeeping list each frame. */
            owner.boxList.remove(this);

            copyPresentationFromDelegate();
        }

        @Override
        public void render(float scale) {
            withFixedPose(scale, 0);
        }

        @Override
        public void renderWithRotation(float scale) {
            withFixedPose(scale, 1);
        }

        @Override
        public void postRender(float scale) {
            withFixedPose(scale, 2);
        }

        private void withFixedPose(
                float scale,
                int renderMode
        ) {
            float oldRotationPointX = delegate.rotationPointX;
            float oldRotationPointY = delegate.rotationPointY;
            float oldRotationPointZ = delegate.rotationPointZ;
            float oldRotateAngleX = delegate.rotateAngleX;
            float oldRotateAngleY = delegate.rotateAngleY;
            float oldRotateAngleZ = delegate.rotateAngleZ;
            float oldOffsetX = delegate.offsetX;
            float oldOffsetY = delegate.offsetY;
            float oldOffsetZ = delegate.offsetZ;
            boolean oldShowModel = delegate.showModel;
            boolean oldHidden = delegate.isHidden;

            try {
                delegate.rotationPointX = this.rotationPointX;
                delegate.rotationPointY = this.rotationPointY;
                delegate.rotationPointZ = this.rotationPointZ;
                delegate.offsetX = this.offsetX;
                delegate.offsetY = this.offsetY;
                delegate.offsetZ = this.offsetZ;
                delegate.showModel = this.showModel;
                delegate.isHidden = this.isHidden;

                delegate.rotateAngleX = HOLD_PITCH;
                delegate.rotateAngleY = 0.0F;
                delegate.rotateAngleZ = rightArm
                        ? -HOLD_INWARD_ROLL
                        : HOLD_INWARD_ROLL;

                if (renderMode == 1) {
                    delegate.renderWithRotation(scale);
                } else if (renderMode == 2) {
                    delegate.postRender(scale);
                } else {
                    delegate.render(scale);
                }
            } finally {
                delegate.rotationPointX = oldRotationPointX;
                delegate.rotationPointY = oldRotationPointY;
                delegate.rotationPointZ = oldRotationPointZ;
                delegate.rotateAngleX = oldRotateAngleX;
                delegate.rotateAngleY = oldRotateAngleY;
                delegate.rotateAngleZ = oldRotateAngleZ;
                delegate.offsetX = oldOffsetX;
                delegate.offsetY = oldOffsetY;
                delegate.offsetZ = oldOffsetZ;
                delegate.showModel = oldShowModel;
                delegate.isHidden = oldHidden;
            }
        }

        private void copyPresentationFromDelegate() {
            this.rotationPointX = delegate.rotationPointX;
            this.rotationPointY = delegate.rotationPointY;
            this.rotationPointZ = delegate.rotationPointZ;
            this.rotateAngleX = delegate.rotateAngleX;
            this.rotateAngleY = delegate.rotateAngleY;
            this.rotateAngleZ = delegate.rotateAngleZ;
            this.offsetX = delegate.offsetX;
            this.offsetY = delegate.offsetY;
            this.offsetZ = delegate.offsetZ;
            this.showModel = delegate.showModel;
            this.isHidden = delegate.isHidden;
        }
    }
}
