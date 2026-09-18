package com.fuzs.aquaacrobatics.client.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.fuzs.aquaacrobatics.entity.Pose;
import com.lotrcharactercreation.client.model.PlayerDwarfModelAdapter;
import com.lotrcharactercreation.client.model.PlayerElfModelAdapter;
import com.lotrcharactercreation.client.model.PlayerHobbitModelAdapter;
import com.lotrcharactercreation.client.model.PlayerManModelAdapter;
import com.lotrcharactercreation.client.model.PlayerOrcModelAdapter;

import lotr.client.model.LOTRModelGondorHelmet;
import net.minecraft.client.model.ModelBiped;

public class AquaLotrSpecialArmorPoseBridgeTest {

    private static final float TOLERANCE = 0.000001F;

    @Before
    public void resetBeforeTest() {
        AquaLotrSpecialArmorPoseBridge.resetForTests();
    }

    @After
    public void resetAfterTest() {
        AquaLotrSpecialArmorPoseBridge.resetForTests();
    }

    @Test
    public void accountSkinBodyStillDrivesRegisteredLotrSpecialArmor() {
        ModelBiped body = new ModelBiped();
        ModelBiped specialArmor = new ModelBiped();
        Object player = new Object();
        setLegacyCrouchPivots(specialArmor);

        AquaLotrSpecialArmorPoseBridge.beginPlayerRender(player);
        AquaLotrSpecialArmorPoseBridge.associateSpecialArmor(specialArmor, body);
        AquaLotrSpecialArmorPoseBridge.applyBodyPoseAndRecordAuthority(body, player, Pose.CROUCHING);
        AquaLotrSpecialArmorPoseBridge.applyAfterLotrAngles(specialArmor, player);

        assertCrouchingPivots(body);
        assertCrouchingPivots(specialArmor);
    }

    @Test
    public void lotrPresetManBodyReceivesCanonicalAquaCrouchPivots() {
        PlayerManModelAdapter manBody = new PlayerManModelAdapter();
        Object player = new Object();
        manBody.isSneak = true;

        manBody.setRotationAngles(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0625F, null);
        assertLegacyCrouchPivots(manBody);

        AquaLotrSpecialArmorPoseBridge.beginPlayerRender(player);
        AquaModelBipedLogic.applyCharacterCreationManPose(manBody, player, Pose.CROUCHING);

        assertTrue(AquaModelBipedLogic.isCharacterCreationManModel(manBody));
        assertCrouchingPivots(manBody);
    }

    @Test
    public void lotrPresetManAndVanillaArmorUseMatchingAquaCrouchPivots() {
        PlayerManModelAdapter manBody = new PlayerManModelAdapter();
        ModelBiped defaultArmor = new ModelBiped(1.0F);
        Object player = new Object();
        setLegacyCrouchPivots(manBody);
        setLegacyCrouchPivots(defaultArmor);

        AquaLotrSpecialArmorPoseBridge.beginPlayerRender(player);
        AquaModelBipedLogic.applyCharacterCreationManPose(manBody, player, Pose.CROUCHING);
        applyCanonicalPose(defaultArmor, Pose.CROUCHING);

        assertCrouchingPivots(manBody);
        assertCrouchingPivots(defaultArmor);
    }

    @Test
    public void lotrPresetManDrivesRealGondorHelmetAfterLotrAnglesRun() {
        PlayerManModelAdapter manBody = new PlayerManModelAdapter();
        LOTRModelGondorHelmet helmet = new LOTRModelGondorHelmet(1.0F);
        Object player = new Object();
        helmet.isSneak = true;

        helmet.setRotationAngles(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0625F, null);
        assertLegacyCrouchPivots(helmet);

        AquaLotrSpecialArmorPoseBridge.beginPlayerRender(player);
        AquaModelBipedLogic.applyCharacterCreationManPose(manBody, player, Pose.CROUCHING);
        AquaLotrSpecialArmorPoseBridge.associateSpecialArmor(helmet, manBody);
        AquaLotrSpecialArmorPoseBridge.applyAfterLotrAngles(helmet, player);

        assertCrouchingPivots(manBody);
        assertCrouchingPivots(helmet);
    }

    @Test
    public void lotrPresetManStandingPoseRemainsAligned() {
        PlayerManModelAdapter manBody = new PlayerManModelAdapter();
        ModelBiped defaultArmor = new ModelBiped(1.0F);
        ModelBiped specialArmor = new ModelBiped(1.0F);
        Object player = new Object();
        setLegacyCrouchPivots(manBody);
        setLegacyCrouchPivots(defaultArmor);
        setLegacyCrouchPivots(specialArmor);

        AquaLotrSpecialArmorPoseBridge.beginPlayerRender(player);
        AquaModelBipedLogic.applyCharacterCreationManPose(manBody, player, Pose.STANDING);
        applyCanonicalPose(defaultArmor, Pose.STANDING);
        AquaLotrSpecialArmorPoseBridge.associateSpecialArmor(specialArmor, manBody);
        AquaLotrSpecialArmorPoseBridge.applyAfterLotrAngles(specialArmor, player);

        assertStandingPivots(manBody);
        assertStandingPivots(defaultArmor);
        assertStandingPivots(specialArmor);
    }

    @Test
    public void otherCharacterCreationAdaptersRemainLegacyAndCannotBecomeAuthority() {
        ModelBiped[] otherRaceModels = {
            new PlayerDwarfModelAdapter(),
            new PlayerHobbitModelAdapter(),
            new PlayerElfModelAdapter(),
            new PlayerOrcModelAdapter()
        };
        Object player = new Object();

        for (ModelBiped otherRaceModel : otherRaceModels) {
            ModelBiped specialArmor = new ModelBiped();
            setLegacyCrouchPivots(otherRaceModel);
            setLegacyCrouchPivots(specialArmor);
            AquaLotrSpecialArmorPoseBridge.beginPlayerRender(player);
            AquaModelBipedLogic.applyCharacterCreationManPose(otherRaceModel, player, Pose.CROUCHING);
            AquaLotrSpecialArmorPoseBridge.associateSpecialArmor(specialArmor, otherRaceModel);
            AquaLotrSpecialArmorPoseBridge.applyAfterLotrAngles(specialArmor, player);

            assertFalse(AquaModelBipedLogic.isCharacterCreationManModel(otherRaceModel));
            assertLegacyCrouchPivots(otherRaceModel);
            assertLegacyCrouchPivots(specialArmor);
        }
    }

    @Test
    public void unregisteredLotrLikeModelIsUntouched() {
        ModelBiped body = new ModelBiped();
        ModelBiped unregisteredModel = new ModelBiped();
        Object player = new Object();
        setLegacyCrouchPivots(unregisteredModel);

        AquaLotrSpecialArmorPoseBridge.beginPlayerRender(player);
        AquaLotrSpecialArmorPoseBridge.applyBodyPoseAndRecordAuthority(body, player, Pose.CROUCHING);
        AquaLotrSpecialArmorPoseBridge.applyAfterLotrAngles(unregisteredModel, player);

        assertLegacyCrouchPivots(unregisteredModel);
    }

    @Test
    public void cachedSpecialArmorCannotReuseAnotherPlayersPoseAuthority() {
        ModelBiped sharedBody = new ModelBiped();
        ModelBiped cachedSpecialArmor = new ModelBiped();
        Object firstRemotePlayer = new Object();
        Object secondRemotePlayer = new Object();
        AquaLotrSpecialArmorPoseBridge.associateSpecialArmor(cachedSpecialArmor, sharedBody);

        AquaLotrSpecialArmorPoseBridge.beginPlayerRender(firstRemotePlayer);
        AquaLotrSpecialArmorPoseBridge.applyBodyPoseAndRecordAuthority(
            sharedBody, firstRemotePlayer, Pose.CROUCHING);
        AquaLotrSpecialArmorPoseBridge.applyAfterLotrAngles(cachedSpecialArmor, firstRemotePlayer);
        assertCrouchingPivots(cachedSpecialArmor);

        setLegacyCrouchPivots(cachedSpecialArmor);
        AquaLotrSpecialArmorPoseBridge.beginPlayerRender(secondRemotePlayer);
        AquaLotrSpecialArmorPoseBridge.applyAfterLotrAngles(cachedSpecialArmor, secondRemotePlayer);
        assertLegacyCrouchPivots(cachedSpecialArmor);

        AquaLotrSpecialArmorPoseBridge.applyBodyPoseAndRecordAuthority(
            sharedBody, secondRemotePlayer, Pose.STANDING);
        AquaLotrSpecialArmorPoseBridge.applyAfterLotrAngles(cachedSpecialArmor, secondRemotePlayer);
        assertStandingPivots(cachedSpecialArmor);
    }

    @Test
    public void cachedArmorCannotCrossAccountManPresetManOrAnotherRace() {
        ModelBiped accountSkinBody = new ModelBiped();
        PlayerManModelAdapter presetManBody = new PlayerManModelAdapter();
        PlayerElfModelAdapter elfBody = new PlayerElfModelAdapter();
        ModelBiped cachedSpecialArmor = new ModelBiped();
        Object accountPlayer = new Object();
        Object presetPlayer = new Object();
        Object elfPlayer = new Object();

        AquaLotrSpecialArmorPoseBridge.beginPlayerRender(accountPlayer);
        AquaLotrSpecialArmorPoseBridge.applyBodyPoseAndRecordAuthority(
            accountSkinBody, accountPlayer, Pose.CROUCHING);
        AquaLotrSpecialArmorPoseBridge.associateSpecialArmor(cachedSpecialArmor, accountSkinBody);
        AquaLotrSpecialArmorPoseBridge.applyAfterLotrAngles(cachedSpecialArmor, accountPlayer);
        assertCrouchingPivots(cachedSpecialArmor);

        setLegacyCrouchPivots(cachedSpecialArmor);
        AquaLotrSpecialArmorPoseBridge.beginPlayerRender(presetPlayer);
        AquaModelBipedLogic.applyCharacterCreationManPose(presetManBody, presetPlayer, Pose.STANDING);
        AquaLotrSpecialArmorPoseBridge.associateSpecialArmor(cachedSpecialArmor, presetManBody);
        AquaLotrSpecialArmorPoseBridge.applyAfterLotrAngles(cachedSpecialArmor, presetPlayer);
        assertStandingPivots(cachedSpecialArmor);

        setLegacyCrouchPivots(cachedSpecialArmor);
        AquaLotrSpecialArmorPoseBridge.beginPlayerRender(elfPlayer);
        AquaModelBipedLogic.applyCharacterCreationManPose(elfBody, elfPlayer, Pose.CROUCHING);
        AquaLotrSpecialArmorPoseBridge.associateSpecialArmor(cachedSpecialArmor, elfBody);
        AquaLotrSpecialArmorPoseBridge.applyAfterLotrAngles(cachedSpecialArmor, elfPlayer);
        assertLegacyCrouchPivots(cachedSpecialArmor);
    }

    @Test
    public void newRenderOfSamePlayerRequiresFreshBodyPoseAuthority() {
        ModelBiped sharedBody = new ModelBiped();
        ModelBiped cachedSpecialArmor = new ModelBiped();
        Object player = new Object();
        AquaLotrSpecialArmorPoseBridge.associateSpecialArmor(cachedSpecialArmor, sharedBody);

        AquaLotrSpecialArmorPoseBridge.beginPlayerRender(player);
        AquaLotrSpecialArmorPoseBridge.applyBodyPoseAndRecordAuthority(sharedBody, player, Pose.CROUCHING);
        AquaLotrSpecialArmorPoseBridge.applyAfterLotrAngles(cachedSpecialArmor, player);
        assertCrouchingPivots(cachedSpecialArmor);

        setLegacyCrouchPivots(cachedSpecialArmor);
        AquaLotrSpecialArmorPoseBridge.beginPlayerRender(player);
        AquaLotrSpecialArmorPoseBridge.applyAfterLotrAngles(cachedSpecialArmor, player);

        assertLegacyCrouchPivots(cachedSpecialArmor);
    }

    @Test
    public void firstPersonManArmDoesNotReceiveThirdPersonPosePivotsOrAuthority() {
        PlayerManModelAdapter manBody = new PlayerManModelAdapter();
        ModelBiped specialArmor = new ModelBiped();
        Object player = new Object();
        setLegacyCrouchPivots(manBody);
        setLegacyCrouchPivots(specialArmor);
        AquaLotrSpecialArmorPoseBridge.beginPlayerRender(player);

        FirstPersonArmRenderContext.push();
        try {
            AquaModelBipedLogic.applyCharacterCreationManPose(manBody, player, Pose.CROUCHING);
        } finally {
            FirstPersonArmRenderContext.pop();
        }
        AquaLotrSpecialArmorPoseBridge.associateSpecialArmor(specialArmor, manBody);
        AquaLotrSpecialArmorPoseBridge.applyAfterLotrAngles(specialArmor, player);

        assertLegacyCrouchPivots(manBody);
        assertLegacyCrouchPivots(specialArmor);
    }

    @Test
    public void swimmingPoseLeavesExistingManPivotsAndLimbAnglesForSwimmingLogic() {
        PlayerManModelAdapter manBody = new PlayerManModelAdapter();
        Object player = new Object();
        setLegacyCrouchPivots(manBody);
        manBody.bipedRightArm.rotateAngleX = 0.75F;
        manBody.bipedLeftArm.rotateAngleZ = -0.5F;

        AquaLotrSpecialArmorPoseBridge.beginPlayerRender(player);
        AquaModelBipedLogic.applyCharacterCreationManPose(manBody, player, Pose.SWIMMING);

        assertLegacyCrouchPivots(manBody);
        assertEquals(0.75F, manBody.bipedRightArm.rotateAngleX, TOLERANCE);
        assertEquals(-0.5F, manBody.bipedLeftArm.rotateAngleZ, TOLERANCE);
    }

    private static void applyCanonicalPose(ModelBiped model, Pose pose) {
        AquaPlayerRenderLogic.applyPosePivots(
            pose,
            model.bipedHead,
            model.bipedHeadwear,
            model.bipedBody,
            model.bipedRightArm,
            model.bipedLeftArm,
            model.bipedRightLeg,
            model.bipedLeftLeg);
    }

    private static void setLegacyCrouchPivots(ModelBiped model) {
        model.bipedHead.rotationPointY = 1.0F;
        model.bipedHeadwear.rotationPointY = 1.0F;
        model.bipedBody.rotationPointY = 0.0F;
        model.bipedRightArm.rotationPointY = 2.0F;
        model.bipedLeftArm.rotationPointY = 2.0F;
        model.bipedRightLeg.rotationPointY = 9.0F;
        model.bipedLeftLeg.rotationPointY = 9.0F;
    }

    private static void assertCrouchingPivots(ModelBiped model) {
        assertPivots(model, 4.2F, 3.2F, 5.2F, 12.0F);
    }

    private static void assertStandingPivots(ModelBiped model) {
        assertPivots(model, 0.0F, 0.0F, 2.0F, 12.0F);
    }

    private static void assertLegacyCrouchPivots(ModelBiped model) {
        assertPivots(model, 1.0F, 0.0F, 2.0F, 9.0F);
    }

    private static void assertPivots(ModelBiped model, float headY, float bodyY, float armY, float legY) {
        assertEquals(headY, model.bipedHead.rotationPointY, TOLERANCE);
        assertEquals(headY, model.bipedHeadwear.rotationPointY, TOLERANCE);
        assertEquals(bodyY, model.bipedBody.rotationPointY, TOLERANCE);
        assertEquals(armY, model.bipedRightArm.rotationPointY, TOLERANCE);
        assertEquals(armY, model.bipedLeftArm.rotationPointY, TOLERANCE);
        assertEquals(legY, model.bipedRightLeg.rotationPointY, TOLERANCE);
        assertEquals(legY, model.bipedLeftLeg.rotationPointY, TOLERANCE);
    }
}
