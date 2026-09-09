package com.lotrcharactercreation.client.render;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.client.event.RenderPlayerEvent;
import com.enovak.lotrmoremobs.entity.animal.LOTREntityMumakil;
import net.minecraft.entity.EntityLivingBase;

import org.lwjgl.opengl.GL11;

import com.lotrcharactercreation.appearance.AppearancePreset;
import com.lotrcharactercreation.appearance.AppearancePresetRegistry;
import com.lotrcharactercreation.appearance.PlayerSex;
import com.lotrcharactercreation.body.RaceBodyDefinition;
import com.lotrcharactercreation.client.appearance.ClientAppearanceTextureResolver;
import com.lotrcharactercreation.client.appearance.ClientPlayerAppearanceCache;
import com.lotrcharactercreation.client.appearance.ClientPlayerAppearanceCache.SynchronizedPlayerAppearance;
import com.lotrcharactercreation.client.body.ClientPlayerEyeCameraService;
import com.lotrcharactercreation.client.model.PlayerDwarfModelAdapter;
import com.lotrcharactercreation.client.model.PlayerElfModelAdapter;
import com.lotrcharactercreation.client.model.PlayerHobbitModelAdapter;
import com.lotrcharactercreation.client.model.PlayerManModelAdapter;
import com.lotrcharactercreation.client.model.PlayerOrcModelAdapter;
import com.lotrcharactercreation.race.PlayerRace;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import lotr.client.model.LOTRModelDwarf;
import lotr.client.model.LOTRModelElf;
import lotr.client.model.LOTRModelHobbit;
import lotr.client.model.LOTRModelOrc;

@SideOnly(Side.CLIENT)
public class RacePlayerRenderer extends RenderPlayer {

    private static final float VANILLA_HELD_ITEM_Y = 0.1875F;
    private static final float VANILLA_BOW_HELD_ITEM_Y = 0.125F;
    private static final float VANILLA_ROTATE_AROUND_Y = 0.125F;
    private static final float HOBBIT_HELD_ITEM_Y = 0.075F;

    private final PlayerManModelAdapter manModel = new PlayerManModelAdapter();
    private final PlayerDwarfModelAdapter dwarfModel = new PlayerDwarfModelAdapter();
    private final ModelBiped dwarfArmorChestplate = new LOTRModelDwarf(1.0F);
    private final ModelBiped dwarfArmor = new LOTRModelDwarf(0.5F);
    private final PlayerElfModelAdapter elfModel = new PlayerElfModelAdapter();
    private final ModelBiped elfArmorChestplate = new LOTRModelElf(1.0F);
    private final ModelBiped elfArmor = new LOTRModelElf(0.5F);
    private final PlayerHobbitModelAdapter hobbitModel = new PlayerHobbitModelAdapter();
    private final ModelBiped hobbitArmorChestplate = new LOTRModelHobbit(1.0F);
    private final ModelBiped hobbitArmor = new LOTRModelHobbit(0.5F);
    private final PlayerOrcModelAdapter orcModel = new PlayerOrcModelAdapter();
    private final ModelBiped orcArmorChestplate = new LOTRModelOrc(1.0F);
    private final ModelBiped orcArmor = new LOTRModelOrc(0.5F);
    private final HobbitHeldItemArmTransform hobbitHeldItemArmTransform = new HobbitHeldItemArmTransform(hobbitModel);

    @SubscribeEvent(priority = EventPriority.NORMAL, receiveCanceled = true)
    public void reclaimPlayerRenderer(RenderPlayerEvent.Pre event) {
        if (event.renderer == this) {
            return;
        }

        EntityPlayer player = event.entityPlayer;
        if (!requiresCustomRenderer(player)) {
            return;
        }

        ensureRaceRendererMapping(player);
    }

    @SubscribeEvent
    public void reclaimLocalPlayerRenderer(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        if (player != null && requiresCustomRenderer(player)) {
            ensureRaceRendererMapping(player);
        }
    }

@Override
protected void renderModel(
    EntityLivingBase entity,
    float limbSwing,
    float limbSwingAmount,
    float ageInTicks,
    float netHeadYaw,
    float headPitch,
    float scaleFactor
) {
    if (entity.ridingEntity instanceof LOTREntityMumakil
        && ((LOTREntityMumakil) entity.ridingEntity)
            .hasMumakilSyncedHowdahEquipped()) {
        this.modelBipedMain.isRiding = false;
    }

    super.renderModel(
        entity,
        limbSwing,
        limbSwingAmount,
        ageInTicks,
        netHeadYaw,
        headPitch,
        scaleFactor
    );
}

    @Override
    public void doRender(AbstractClientPlayer player, double x, double y, double z, float yaw, float partialTicks) {
        SynchronizedPlayerAppearance appearance = getAppearance(player);
        PlayerRace renderedRace = getCustomRenderedRace(player, appearance);
        if (renderedRace == null) {
            super.doRender(player, x, y, z, yaw, partialTicks);
            return;
        }

        ModelBiped racePlayerModel = getPlayerModel(renderedRace);
        ModelBase previousMainModel = mainModel;
        ModelBiped previousPlayerModel = modelBipedMain;
        ModelBiped previousArmorChestplateModel = modelArmorChestplate;
        ModelBiped previousArmorModel = modelArmor;
        boolean manResourceAppearance = renderedRace == PlayerRace.MAN;
        double compensatedY = manResourceAppearance ? y
            : y + ClientPlayerEyeCameraService.getInstance()
                .getLocalPlayerRenderYOffsetCompensation(player);

        try {
            configurePlayerPresentation(renderedRace, appearance.getSex(), player.inventory.armorItemInSlot(2) != null);
            mainModel = racePlayerModel;
            modelBipedMain = racePlayerModel;
            if (!manResourceAppearance) {
                modelArmorChestplate = getArmorChestplateModel(renderedRace);
                modelArmor = getArmorModel(renderedRace);
            }
            if (renderedRace == PlayerRace.ELF) {
                resetElfArmorLegPose();
            }
            super.doRender(player, x, compensatedY, z, yaw, partialTicks);
        } finally {
            if (renderedRace == PlayerRace.ELF) {
                resetElfArmorLegPose();
            }
            mainModel = previousMainModel;
            modelBipedMain = previousPlayerModel;
            modelArmorChestplate = previousArmorChestplateModel;
            modelArmor = previousArmorModel;
            resetPlayerPresentation(renderedRace);
        }
    }

    @Override
    protected ResourceLocation getEntityTexture(AbstractClientPlayer player) {
        SynchronizedPlayerAppearance appearance = getAppearance(player);
        if (getCustomRenderedRace(player, appearance) == null) {
            return super.getEntityTexture(player);
        }

        ResourceLocation texture = resolveAppearanceTexture(player, appearance);
        return texture == null ? super.getEntityTexture(player) : texture;
    }

    @Override
    protected void preRenderCallback(AbstractClientPlayer player, float partialTicks) {
        super.preRenderCallback(player, partialTicks);
        PlayerRace renderedRace = getCustomRenderedRace(player, getAppearance(player));
        if (renderedRace != null && renderedRace != PlayerRace.MAN) {
            float renderScale = RaceBodyDefinition.forRace(renderedRace)
                .getRenderScale();
            GL11.glScalef(renderScale, renderScale, renderScale);
        }
    }

    @Override
    protected void renderEquippedItems(AbstractClientPlayer player, float partialTicks) {
        ItemStack heldItem = player.inventory.getCurrentItem();
        if (!isHobbit(player, getAppearance(player)) || heldItem == null) {
            super.renderEquippedItems(player, partialTicks);
            return;
        }

        ModelBiped equippedModel = modelBipedMain;
        ModelRenderer originalRightArm = equippedModel.bipedRightArm;
        ItemStack renderedHeldItem = player.fishEntity == null ? heldItem : new ItemStack(Items.stick);
        hobbitHeldItemArmTransform.configure(originalRightArm, getHobbitHeldItemYCorrection(renderedHeldItem));
        equippedModel.bipedRightArm = hobbitHeldItemArmTransform;
        try {
            super.renderEquippedItems(player, partialTicks);
        } finally {
            equippedModel.bipedRightArm = originalRightArm;
            hobbitHeldItemArmTransform.clear();
        }
    }

    @Override
    public void renderFirstPersonArm(EntityPlayer player) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (player != minecraft.thePlayer) {
            super.renderFirstPersonArm(player);
            return;
        }

        SynchronizedPlayerAppearance appearance = getAppearance(player);
        if (isManLotrAppearance(player, appearance)) {
            renderManFirstPersonArm(minecraft, player, appearance);
            return;
        }
        if (isHobbit(player, appearance)) {
            renderHobbitFirstPersonArm(minecraft, player, appearance);
            return;
        }
        if (isElf(player, appearance)) {
            renderElfFirstPersonArm(minecraft, player, appearance);
            return;
        }
        if (isOrc(player, appearance) || isUrukHai(player, appearance)) {
            renderOrcFirstPersonArm(minecraft, player, appearance);
            return;
        }
        if (!isDwarf(player, appearance)) {
            super.renderFirstPersonArm(player);
            return;
        }

        ResourceLocation texture = resolveAppearanceTexture(player, appearance);
        if (texture == null) {
            minecraft.getTextureManager()
                .bindTexture(minecraft.thePlayer.getLocationSkin());
            super.renderFirstPersonArm(player);
            return;
        }

        ModelBiped previousPlayerModel = modelBipedMain;
        try {
            dwarfModel.resetPlayerPresentation();
            dwarfModel.configurePlayerPresentation(appearance.getSex(), player.inventory.armorItemInSlot(2) != null);
            modelBipedMain = dwarfModel;
            minecraft.getTextureManager()
                .bindTexture(texture);
            super.renderFirstPersonArm(player);
        } finally {
            modelBipedMain = previousPlayerModel;
            dwarfModel.resetPlayerPresentation();
            minecraft.getTextureManager()
                .bindTexture(minecraft.thePlayer.getLocationSkin());
        }
    }

    private void renderManFirstPersonArm(Minecraft minecraft, EntityPlayer player,
        SynchronizedPlayerAppearance appearance) {
        ResourceLocation texture = resolveAppearanceTexture(player, appearance);
        if (texture == null) {
            minecraft.getTextureManager()
                .bindTexture(minecraft.thePlayer.getLocationSkin());
            super.renderFirstPersonArm(player);
            return;
        }

        ModelBiped previousPlayerModel = modelBipedMain;
        try {
            manModel.resetPlayerPresentation();
            manModel.configurePlayerPresentation(appearance.getSex(), true);
            modelBipedMain = manModel;
            minecraft.getTextureManager()
                .bindTexture(texture);
            super.renderFirstPersonArm(player);
        } finally {
            modelBipedMain = previousPlayerModel;
            manModel.resetPlayerPresentation();
            minecraft.getTextureManager()
                .bindTexture(minecraft.thePlayer.getLocationSkin());
        }
    }

    private void renderOrcFirstPersonArm(Minecraft minecraft, EntityPlayer player,
        SynchronizedPlayerAppearance appearance) {
        ResourceLocation texture = resolveAppearanceTexture(player, appearance);
        if (texture == null) {
            minecraft.getTextureManager()
                .bindTexture(minecraft.thePlayer.getLocationSkin());
            super.renderFirstPersonArm(player);
            return;
        }

        ModelBiped previousPlayerModel = modelBipedMain;
        try {
            orcModel.resetPlayerPresentation();
            modelBipedMain = orcModel;
            minecraft.getTextureManager()
                .bindTexture(texture);
            super.renderFirstPersonArm(player);
        } finally {
            modelBipedMain = previousPlayerModel;
            orcModel.resetPlayerPresentation();
            minecraft.getTextureManager()
                .bindTexture(minecraft.thePlayer.getLocationSkin());
        }
    }

    private void renderElfFirstPersonArm(Minecraft minecraft, EntityPlayer player,
        SynchronizedPlayerAppearance appearance) {
        ResourceLocation texture = resolveAppearanceTexture(player, appearance);
        if (texture == null) {
            minecraft.getTextureManager()
                .bindTexture(minecraft.thePlayer.getLocationSkin());
            super.renderFirstPersonArm(player);
            return;
        }

        ModelBiped previousPlayerModel = modelBipedMain;
        try {
            elfModel.resetPlayerPresentation();
            elfModel.configurePlayerPresentation(appearance.getSex(), player.inventory.armorItemInSlot(2) != null);
            modelBipedMain = elfModel;
            minecraft.getTextureManager()
                .bindTexture(texture);
            super.renderFirstPersonArm(player);
        } finally {
            modelBipedMain = previousPlayerModel;
            elfModel.resetPlayerPresentation();
            minecraft.getTextureManager()
                .bindTexture(minecraft.thePlayer.getLocationSkin());
        }
    }

    private void renderHobbitFirstPersonArm(Minecraft minecraft, EntityPlayer player,
        SynchronizedPlayerAppearance appearance) {
        ResourceLocation texture = resolveAppearanceTexture(player, appearance);
        if (texture == null) {
            minecraft.getTextureManager()
                .bindTexture(minecraft.thePlayer.getLocationSkin());
            super.renderFirstPersonArm(player);
            return;
        }

        try {
            hobbitModel.resetPlayerPresentation();
            hobbitModel.configurePlayerPresentation(appearance.getSex(), player.inventory.armorItemInSlot(2) != null);
            minecraft.getTextureManager()
                .bindTexture(texture);
            hobbitModel.renderFirstPersonRightArm(player);
        } finally {
            hobbitModel.resetPlayerPresentation();
            minecraft.getTextureManager()
                .bindTexture(minecraft.thePlayer.getLocationSkin());
        }
    }

    private SynchronizedPlayerAppearance getAppearance(EntityPlayer player) {
        return ClientPlayerAppearanceCache.getInstance()
            .get(player);
    }

    private boolean requiresCustomRenderer(EntityPlayer player) {
        SynchronizedPlayerAppearance appearance = getAppearance(player);
        return appearance != null && appearance.isCharacterCreationComplete()
            && getCustomRenderedRace(player, appearance) != null;
    }

    private void ensureRaceRendererMapping(EntityPlayer player) {
        if (RenderManager.instance.entityRenderMap.get(player.getClass()) != this) {
            RenderManager.instance.entityRenderMap.put(player.getClass(), this);
        }
        if (RenderManager.instance.entityRenderMap.get(EntityPlayer.class) != this) {
            RenderManager.instance.entityRenderMap.put(EntityPlayer.class, this);
        }
    }

    private boolean isDwarf(EntityPlayer player, SynchronizedPlayerAppearance appearance) {
        return getCustomRenderedRace(player, appearance) == PlayerRace.DWARF;
    }

    private boolean isManLotrAppearance(EntityPlayer player, SynchronizedPlayerAppearance appearance) {
        return getCustomRenderedRace(player, appearance) == PlayerRace.MAN;
    }

    private boolean isElf(EntityPlayer player, SynchronizedPlayerAppearance appearance) {
        return getCustomRenderedRace(player, appearance) == PlayerRace.ELF;
    }

    private boolean isHobbit(EntityPlayer player, SynchronizedPlayerAppearance appearance) {
        return getCustomRenderedRace(player, appearance) == PlayerRace.HOBBIT;
    }

    private boolean isOrc(EntityPlayer player, SynchronizedPlayerAppearance appearance) {
        return getCustomRenderedRace(player, appearance) == PlayerRace.ORC;
    }

    private boolean isUrukHai(EntityPlayer player, SynchronizedPlayerAppearance appearance) {
        return getCustomRenderedRace(player, appearance) == PlayerRace.URUK_HAI;
    }

    private PlayerRace getCustomRenderedRace(EntityPlayer player, SynchronizedPlayerAppearance appearance) {
        if (appearance == null) {
            return null;
        }

        PlayerRace race = appearance.getRace();
        if (race == null) {
            return null;
        }
        switch (race) {
            case MAN:
                return isValidManLotrAppearance(player, appearance) ? PlayerRace.MAN : null;
            case ELF:
            case DWARF:
            case HOBBIT:
            case ORC:
            case URUK_HAI:
                return RaceBodyDefinition.forRace(race)
                    .isPrototypeSizeEnabled() ? race : null;
            default:
                return null;
        }
    }

    private static boolean isValidManLotrAppearance(EntityPlayer player, SynchronizedPlayerAppearance appearance) {
        String presetId = appearance.getAppearancePresetId();
        if (!AppearancePresetRegistry.isPresetValid(PlayerRace.MAN, appearance.getSex(), presetId)) {
            return false;
        }

        AppearancePreset preset = AppearancePresetRegistry.findById(presetId);
        return ClientAppearanceTextureResolver.isLotrCharacterTexture(preset) && ClientAppearanceTextureResolver
            .resolveWithFallback(player, PlayerRace.MAN, appearance.getSex(), presetId) != null;
    }

    private ModelBiped getPlayerModel(PlayerRace race) {
        switch (race) {
            case MAN:
                return manModel;
            case ELF:
                return elfModel;
            case DWARF:
                return dwarfModel;
            case HOBBIT:
                return hobbitModel;
            case ORC:
            case URUK_HAI:
                return orcModel;
            default:
                throw new IllegalArgumentException("Unsupported custom-rendered race: " + race);
        }
    }

    private ModelBiped getArmorChestplateModel(PlayerRace race) {
        switch (race) {
            case ELF:
                return elfArmorChestplate;
            case DWARF:
                return dwarfArmorChestplate;
            case HOBBIT:
                return hobbitArmorChestplate;
            case ORC:
            case URUK_HAI:
                return orcArmorChestplate;
            default:
                throw new IllegalArgumentException("Unsupported custom-rendered race: " + race);
        }
    }

    private ModelBiped getArmorModel(PlayerRace race) {
        switch (race) {
            case ELF:
                return elfArmor;
            case DWARF:
                return dwarfArmor;
            case HOBBIT:
                return hobbitArmor;
            case ORC:
            case URUK_HAI:
                return orcArmor;
            default:
                throw new IllegalArgumentException("Unsupported custom-rendered race: " + race);
        }
    }

    private void resetElfArmorLegPose() {
        resetArmorLegPose(elfArmorChestplate);
        resetArmorLegPose(elfArmor);
    }

    private static void resetArmorLegPose(ModelBiped armorModel) {
        armorModel.bipedRightLeg.rotateAngleY = 0.0F;
        armorModel.bipedRightLeg.rotateAngleZ = 0.0F;
        armorModel.bipedLeftLeg.rotateAngleY = 0.0F;
        armorModel.bipedLeftLeg.rotateAngleZ = 0.0F;
    }

    private void configurePlayerPresentation(PlayerRace race, PlayerSex sex, boolean wearingChestArmor) {
        switch (race) {
            case MAN:
                manModel.configurePlayerPresentation(sex, wearingChestArmor);
                break;
            case ELF:
                elfModel.configurePlayerPresentation(sex, wearingChestArmor);
                break;
            case DWARF:
                dwarfModel.configurePlayerPresentation(sex, wearingChestArmor);
                break;
            case HOBBIT:
                hobbitModel.configurePlayerPresentation(sex, wearingChestArmor);
                break;
            case ORC:
            case URUK_HAI:
                orcModel.resetPlayerPresentation();
                break;
            default:
                throw new IllegalArgumentException("Unsupported custom-rendered race: " + race);
        }
    }

    private void resetPlayerPresentation(PlayerRace race) {
        switch (race) {
            case MAN:
                manModel.resetPlayerPresentation();
                break;
            case ELF:
                elfModel.resetPlayerPresentation();
                break;
            case DWARF:
                dwarfModel.resetPlayerPresentation();
                break;
            case HOBBIT:
                hobbitModel.resetPlayerPresentation();
                break;
            case ORC:
            case URUK_HAI:
                orcModel.resetPlayerPresentation();
                break;
            default:
                throw new IllegalArgumentException("Unsupported custom-rendered race: " + race);
        }
    }

    private static ResourceLocation resolveAppearanceTexture(EntityPlayer player,
        SynchronizedPlayerAppearance appearance) {
        return ClientAppearanceTextureResolver
            .resolveWithFallback(player, appearance.getRace(), appearance.getSex(), appearance.getAppearancePresetId());
    }

    private static float getHobbitHeldItemYCorrection(ItemStack heldItem) {
        Item item = heldItem.getItem();
        if (item == null) {
            return 0.0F;
        }

        IItemRenderer customRenderer = MinecraftForgeClient
            .getItemRenderer(heldItem, IItemRenderer.ItemRenderType.EQUIPPED);
        boolean usesBlock3DTransform = customRenderer != null && customRenderer.shouldUseRenderHelper(
            IItemRenderer.ItemRenderType.EQUIPPED,
            heldItem,
            IItemRenderer.ItemRendererHelper.BLOCK_3D);
        if (usesBlock3DTransform || item instanceof ItemBlock && RenderBlocks.renderItemIn3d(
            Block.getBlockFromItem(item)
                .getRenderType())) {
            return HOBBIT_HELD_ITEM_Y - VANILLA_HELD_ITEM_Y;
        }
        if (item == Items.bow) {
            return HOBBIT_HELD_ITEM_Y - VANILLA_BOW_HELD_ITEM_Y;
        }
        if (item.isFull3D() && item.shouldRotateAroundWhenRendering()) {
            return VANILLA_HELD_ITEM_Y - VANILLA_ROTATE_AROUND_Y;
        }
        return HOBBIT_HELD_ITEM_Y - VANILLA_HELD_ITEM_Y;
    }

    private static final class HobbitHeldItemArmTransform extends ModelRenderer {

        private ModelRenderer delegate;
        private float yCorrection;

        private HobbitHeldItemArmTransform(ModelBase owner) {
            super(owner);
            owner.boxList.remove(this);
        }

        private void configure(ModelRenderer delegate, float yCorrection) {
            this.delegate = delegate;
            this.yCorrection = yCorrection;
        }

        private void clear() {
            delegate = null;
            yCorrection = 0.0F;
        }

        @Override
        public void render(float scale) {
            if (delegate != null) {
                delegate.render(scale);
            }
        }

        @Override
        public void renderWithRotation(float scale) {
            if (delegate != null) {
                delegate.renderWithRotation(scale);
            }
        }

        @Override
        public void postRender(float scale) {
            if (delegate != null) {
                delegate.postRender(scale);
                GL11.glTranslatef(0.0F, yCorrection, 0.0F);
            }
        }
    }
}
