package com.enovak.lotrmoremobs.siege.client.model;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;

/**
 * Isolated fallback geometry used until the authored ram asset is added to
 * the repository. The entity/render contract does not depend on this model.
 */
public class ModelBattleRam extends ModelBase {

    private final ModelRenderer beam;
    private final ModelRenderer frame;
    private final ModelRenderer roof;
    private final ModelRenderer leftRunner;
    private final ModelRenderer rightRunner;

    public ModelBattleRam() {
        textureWidth = 64;
        textureHeight = 32;

        beam = new ModelRenderer(this, 0, 0);
        beam.addBox(-3.0F, -3.0F, -48.0F, 6, 6, 96);
        beam.setRotationPoint(0.0F, 14.0F, 0.0F);

        frame = new ModelRenderer(this, 0, 0);
        frame.addBox(-13.0F, -4.0F, -39.0F, 26, 8, 78);
        frame.setRotationPoint(0.0F, 18.0F, 0.0F);

        roof = new ModelRenderer(this, 0, 0);
        roof.addBox(-16.0F, -3.0F, -42.0F, 32, 6, 84);
        roof.setRotationPoint(0.0F, -2.0F, 0.0F);

        leftRunner = new ModelRenderer(this, 0, 0);
        leftRunner.addBox(-3.0F, -3.0F, -40.0F, 6, 6, 80);
        leftRunner.setRotationPoint(-15.0F, 20.0F, 0.0F);

        rightRunner = new ModelRenderer(this, 0, 0);
        rightRunner.addBox(-3.0F, -3.0F, -40.0F, 6, 6, 80);
        rightRunner.setRotationPoint(15.0F, 20.0F, 0.0F);
    }

    @Override
    public void render(
            Entity entity,
            float limbSwing,
            float limbSwingAmount,
            float ageInTicks,
            float netHeadYaw,
            float headPitch,
            float scale
    ) {
        beam.render(scale);
        frame.render(scale);
        roof.render(scale);
        leftRunner.render(scale);
        rightRunner.render(scale);
    }
}
