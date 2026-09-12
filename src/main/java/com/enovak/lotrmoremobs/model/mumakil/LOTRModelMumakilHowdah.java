package com.enovak.lotrmoremobs.model;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;

public class LOTRModelMumakilHowdah extends ModelBase {
    private final ModelRenderer master;
    private final ModelRenderer sway;
    private final ModelRenderer bodyAnchor;
    private final ModelRenderer platform;

    public LOTRModelMumakilHowdah() {
        this.textureWidth = 512;
        this.textureHeight = 512;

        this.master = new ModelRenderer(this);
        this.master.setRotationPoint(0.0F, -26.0F, 50.0F);

        this.sway = new ModelRenderer(this);
        this.sway.setRotationPoint(0.0F, -13.0F, -67.0F);
        this.master.addChild(this.sway);

        this.bodyAnchor = new ModelRenderer(this);
        this.bodyAnchor.setRotationPoint(-2.0F, 1.0F, 17.0F);
        this.sway.addChild(this.bodyAnchor);

        this.platform = new ModelRenderer(this, 52, 449);
        this.platform.setRotationPoint(0.0F, -23.0F, 0.0F);
        this.platform.rotateAngleX = -0.1745F;
        this.platform.addBox(-18.0F, 1.0F, -15.0F, 40, 2, 55, 0.0F);
        this.bodyAnchor.addChild(this.platform);
    }

    @Override
    public void render(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                       float netHeadYaw, float headPitch, float scale) {
        this.master.render(scale);
    }
}
