package com.lotrcharactercreation.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.lotrcharactercreation.appearance.AppearancePreset;
import com.lotrcharactercreation.appearance.AppearanceSourceType;
import com.lotrcharactercreation.body.RaceBodyDefinition;
import com.lotrcharactercreation.client.appearance.ClientAppearanceTextureResolver;
import com.lotrcharactercreation.client.model.PlayerDwarfModelAdapter;
import com.lotrcharactercreation.client.model.PlayerElfModelAdapter;
import com.lotrcharactercreation.client.model.PlayerHobbitModelAdapter;
import com.lotrcharactercreation.client.model.PlayerManModelAdapter;
import com.lotrcharactercreation.client.model.PlayerOrcModelAdapter;
import com.lotrcharactercreation.race.PlayerRace;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class AppearancePreviewRenderer {

    private static final float MODEL_UNIT = 0.0625F;
    private static final float VANILLA_PLAYER_RENDER_SCALE = 0.9375F;

    private final ModelBiped vanillaPlayerModel = new ModelBiped(0.0F);
    private final PlayerManModelAdapter manModel = new PlayerManModelAdapter();
    private final PlayerDwarfModelAdapter dwarfModel = new PlayerDwarfModelAdapter();
    private final PlayerElfModelAdapter elfModel = new PlayerElfModelAdapter();
    private final PlayerHobbitModelAdapter hobbitModel = new PlayerHobbitModelAdapter();
    private final PlayerOrcModelAdapter orcModel = new PlayerOrcModelAdapter();

    public void draw(EntityPlayer player, AppearancePreset preset, int centerX, int bottomY, int scale,
        float mouseOffsetX, float mouseOffsetY, float partialTicks) {
        if (player == null || preset == null) {
            return;
        }

        PlayerRace race = preset.getRace();
        ResourceLocation texture = ClientAppearanceTextureResolver.resolve(player, preset);
        boolean vanillaManFallback = texture == null && race == PlayerRace.MAN;
        if (texture == null) {
            texture = ClientAppearanceTextureResolver.resolveFallback(player, race, preset.getSex());
        }

        ModelBiped model = getModel(preset, vanillaManFallback);
        if (model == null || texture == null) {
            return;
        }
        configureModel(preset, vanillaManFallback);
        float bodyYaw = (float) Math.atan(mouseOffsetX / 40.0F) * 35.0F;
        float headYaw = (float) Math.atan(mouseOffsetX / 40.0F) * 20.0F;
        float headPitch = -((float) Math.atan(mouseOffsetY / 40.0F)) * 20.0F;
        float raceScale = RaceBodyDefinition.forRace(race)
            .getRenderScale();

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        try {
            GL11.glEnable(GL11.GL_COLOR_MATERIAL);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL12.GL_RESCALE_NORMAL);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glTranslatef(centerX, bottomY, 100.0F);
            GL11.glScalef(-scale, scale, scale);
            GL11.glRotatef(135.0F, 0.0F, 1.0F, 0.0F);
            RenderHelper.enableStandardItemLighting();
            GL11.glRotatef(-135.0F, 0.0F, 1.0F, 0.0F);
            GL11.glRotatef(180.0F + bodyYaw, 0.0F, 1.0F, 0.0F);
            GL11.glScalef(
                VANILLA_PLAYER_RENDER_SCALE * raceScale,
                VANILLA_PLAYER_RENDER_SCALE * raceScale,
                VANILLA_PLAYER_RENDER_SCALE * raceScale);
            GL11.glTranslatef(0.0F, -24.0F * MODEL_UNIT - 0.0078125F, 0.0F);

            Minecraft.getMinecraft()
                .getTextureManager()
                .bindTexture(texture);
            model.isChild = false;
            model.onGround = 0.0F;
            model.render(player, 0.0F, 0.0F, player.ticksExisted + partialTicks, headYaw, headPitch, MODEL_UNIT);
        } finally {
            resetModel(preset);
            GL11.glPopMatrix();
            GL11.glPopAttrib();
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    private ModelBiped getModel(AppearancePreset preset, boolean vanillaManFallback) {
        switch (preset.getRace()) {
            case MAN:
                return preset.getSourceType() == AppearanceSourceType.MINECRAFT_ACCOUNT || vanillaManFallback
                    ? vanillaPlayerModel
                    : manModel;
            case DWARF:
                return dwarfModel;
            case ELF:
                return elfModel;
            case HOBBIT:
                return hobbitModel;
            case ORC:
            case URUK_HAI:
                return orcModel;
            default:
                return null;
        }
    }

    private void configureModel(AppearancePreset preset, boolean vanillaManFallback) {
        switch (preset.getRace()) {
            case MAN:
                manModel.resetPlayerPresentation();
                if (preset.getSourceType() != AppearanceSourceType.MINECRAFT_ACCOUNT && !vanillaManFallback) {
                    manModel.configurePlayerPresentation(preset.getSex());
                }
                break;
            case DWARF:
                dwarfModel.resetPlayerPresentation();
                dwarfModel.configurePlayerPresentation(preset.getSex(), false);
                break;
            case ELF:
                elfModel.resetPlayerPresentation();
                elfModel.configurePlayerPresentation(preset.getSex(), false);
                break;
            case HOBBIT:
                hobbitModel.resetPlayerPresentation();
                hobbitModel.configurePlayerPresentation(preset.getSex(), false);
                break;
            case ORC:
            case URUK_HAI:
                orcModel.resetPlayerPresentation();
                break;
            default:
                break;
        }
    }

    private void resetModel(AppearancePreset preset) {
        switch (preset.getRace()) {
            case MAN:
                manModel.resetPlayerPresentation();
                break;
            case DWARF:
                dwarfModel.resetPlayerPresentation();
                break;
            case ELF:
                elfModel.resetPlayerPresentation();
                break;
            case HOBBIT:
                hobbitModel.resetPlayerPresentation();
                break;
            case ORC:
            case URUK_HAI:
                orcModel.resetPlayerPresentation();
                break;
            default:
                break;
        }
    }

}
