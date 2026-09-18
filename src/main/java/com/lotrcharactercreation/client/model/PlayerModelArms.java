package com.lotrcharactercreation.client.model;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Keeps LOTR geometry/UV ownership when skin mods swap RenderPlayer's arm fields. */
@SideOnly(Side.CLIENT)
final class PlayerModelArms {
    private final ModelRenderer right;
    private final ModelRenderer left;

    PlayerModelArms(ModelBiped model) {
        right = model.bipedRightArm;
        left = model.bipedLeftArm;
    }

    void restore(ModelBiped model) {
        // FoamFix/Ears installs shared modern-skin arms during RenderPlayer.Pre
        // and first-person setup. LOTR textures do not use those UV coordinates.
        // Restore before animation, not afterwards: Aqua and held-item math must
        // update the arms that this model will actually draw.
        model.bipedRightArm = right;
        model.bipedLeftArm = left;
    }
}
