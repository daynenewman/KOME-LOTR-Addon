package com.enovak.lotrmoremobs.siege.client.model;

import com.enovak.lotrmoremobs.siege.ram.EntityBattleRam;
import net.minecraft.util.ResourceLocation;
import software.bernie.geckolib3.core.event.predicate.AnimationEvent;
import software.bernie.geckolib3.model.AnimatedGeoModel;

/**
 * Resource provider for the user-authored Battle Ram Geo model.
 *
 * <p>The normal GeckoLib animation processor is intentionally bypassed. Its
 * unofficial 1.7.10 build references obfuscated runtime members which are not
 * available in this project's deobfuscated environment. RenderBattleRam uses
 * the same safe, direct Geo rendering approach as the Mumakil renderer.</p>
 */
public class GeoModelBattleRam extends AnimatedGeoModel<EntityBattleRam> {

    public static final ResourceLocation MODEL = new ResourceLocation(
            "lotrmoremobs",
            "geo/entity/siege/battle_ram.geo.json"
    );
    public static final ResourceLocation TEXTURE = new ResourceLocation(
            "lotrmoremobs",
            "textures/entity/siege/battle_ram.png"
    );
    public static final ResourceLocation ATTACK_ANIMATION =
            new ResourceLocation(
                    "lotrmoremobs",
                    "animations/entity/siege/battle_ram.attacking.json"
            );

    @Override
    public ResourceLocation getAnimationFileLocation(EntityBattleRam entity) {
        return ATTACK_ANIMATION;
    }

    @Override
    public ResourceLocation getModelLocation(EntityBattleRam entity) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureLocation(EntityBattleRam entity) {
        return TEXTURE;
    }

    @Override
    public void setLivingAnimations(
            EntityBattleRam entity,
            Integer uniqueId,
            AnimationEvent event
    ) {
        // RenderBattleRam applies cached authored keyframes directly.
    }
}
