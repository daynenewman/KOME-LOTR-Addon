# Player-arm rendering investigation and correction

Date: 2026-09-17. Branch: `dayne/fix-aqua-production-mappings`.
Base: `cd9af14a9533bebb0bda9e8d32f914cac81dad9f`.
This correction is uncommitted. Its combined production JAR was deployed to the
local `Lord of the Rings DEV` CurseForge instance for client validation.

## Findings and confidence

**Confirmed incompatibility:** the installed FoamFix JAR bundles Ears skin support.
With `mc18SkinSupport=true`, Ears replaces `RenderPlayer.modelBipedMain`'s arm
objects with shared modern-account-skin arms, including on custom LOTR racial
models. Its left arm uses modern UV coordinates `(32,48)`, whereas these models
own their LOTR geometry and texture coordinates. The replacement is not an Aqua
method-mapping error. A regression reproduces the incorrect replacement with
Aqua transformation disabled, as well as enabled.

**Likely explanation of the screenshots:** inappropriate arm texture coordinates
make much of the left arm look absent or like a thin horizontal sheet. The tested
idle model has two normal-sized, visible arms and normal angles; it is not missing
a model box or rotated by an incorrect living-animation override. The screenshot's
exact pixel appearance has not been reproduced with an OpenGL client in this pass.

**Additional reproduced Aqua defects:** cached swimming state survived null or
non-Aqua entities, and the Character Creation post-angle hook could apply swimming
during a first-person render despite its neutral-pose context. These have separate
regressions and are corrected without changing swimming interpolation.

The supplied second image shows an inventory-like player preview. The independent
Character Creation appearance selector has a different renderer. Its model setup
passes the executable tests, but Ears' RenderPlayer callback does not directly
explain a defect in that isolated renderer. A screenshot/retest of that exact screen
is still needed before claiming its observed symptom is resolved.

## Runtime evidence

Read-only inspection of:

- `C:/Users/dayne/curseforge/minecraft/Instances/Lord of the Rings DEV/logs/latest.log`
- `C:/Users/dayne/curseforge/minecraft/Instances/Lord of the Rings DEV/config/foamfix.cfg`
- `C:/Users/dayne/curseforge/minecraft/Instances/Lord of the Rings DEV/mods/FoamFix-1.7.10-universal-1.0.4.jar`

FoamFix SHA-256:
`3C82A60149F024D9A02CD582FFD49AEE36DBA2394AF3065E054D8E6B5364D3C6`.

`javap -p -c` of the installed
`pl.asie.foamfix.repack.com.unascribed.ears.Ears` confirms:

- `amendPlayerRenderer` constructs modern arms and saves static fat/slim arm objects.
- `beforeRender(RenderPlayer, EntityPlayer)` assigns both active model arm fields;
  it has no exclusion for LOTR racial models.
- `onRenderPlayerPre` invokes `beforeRender`.
- The bundled RenderPlayer transformer also inserts first-person setup.

No relevant ModelBiped/RenderPlayer linkage exception was found in the inspected
session log. Unrelated mod discovery/texture/network diagnostics were present;
absence of an exception is not evidence of correct rendering. Two queries of
`getASMTransformerClass()` are not duplicate transformer registration.

## Shared and separate render paths

1. Third person: RenderManager -> RacePlayerRenderer.doRender -> RenderPlayer /
   RendererLivingEntity -> racial ModelBiped subclass.
2. Inventory: GuiInventory.drawEntityOnScreen -> RenderManager -> same racial
   renderer/model. RacePlayerRenderer installs its racial model before calling the
   superclass; Ears' Pre callback can therefore replace that model's arms.
3. Appearance selector: GuiAppearanceSelection -> AppearancePreviewRenderer.draw
   -> separately owned model.render. This uses the real player for context, not a
   fabricated entity; null player exits early. It does not use RenderPlayer.Pre.
   ManSkinReviewPreviewRenderer similarly owns its own model.

The lowest common model ancestry is ModelBiped/ModelBase, through LOTRModelBiped
and the individual LOTR racial models. No single RenderPlayer callback covers
all three paths. The direct preview does not call setLivingAnimations itself.

## Mapping and bytecode evidence

Source of mappings: local authoritative MCP stable_12 `joined.srg` and
`methods.csv`, under `.gradle/caches/minecraft/de/oceanlabs/mcp/mcp_stable/12`,
cross-checked against the target Minecraft classes.

| Semantic member | Declaring owner | MCP | SRG | Descriptor |
| --- | --- | --- | --- | --- |
| Living-animation callback | ModelBase | setLivingAnimations | func_78086_a | `(Lnet/minecraft/entity/EntityLivingBase;FFF)V` |
| Model angles | ModelBase; overridden by ModelBiped | setRotationAngles | func_78087_a | `(FFFFFFLnet/minecraft/entity/Entity;)V` |
| Right arm | ModelBiped | bipedRightArm | field_78112_f | `Lnet/minecraft/client/model/ModelRenderer;` |
| Left arm | ModelBiped | bipedLeftArm | field_78113_g | `Lnet/minecraft/client/model/ModelRenderer;` |

`func_78086_a` was and remains correct. Aqua generates that override on ModelBiped,
forwarding this, entity, limb swing, amount, and partial tick in the original order
to AquaModelBipedLogic.living. The inherited ModelBase implementation is empty;
there is no lost superclass animation. It is not setRotationAngles. The existing
mapping corrections and transformer registration are unchanged in this pass.

## Implementation and ownership

- `PlayerModelArms` retains each custom model's original left/right arm objects.
- PlayerManModelAdapter, PlayerElfModelAdapter, PlayerDwarfModelAdapter,
  PlayerHobbitModelAdapter, and PlayerOrcModelAdapter restore those objects in their
  existing reset path **before** superclass angles and Aqua's final hook run.
- Geometry, UVs and model ownership are preserved, while held-item, walking,
  swimming and crawling calculations operate on the objects actually drawn.
- Vanilla account-skin models are not changed; FoamFix, Aqua and Character Creation
  are not removed or disabled.
- AquaModelBipedLogic clears stale blend for unsupported/null entities and honors
  first-person context in both base and racial post-angle paths. Valid third-person
  swimming/crawling math is unchanged.

The arm ownership issue predates the uncommitted mapping corrections. The stale
pose paths also existed in the committed source. This establishes code provenance,
not the date on which this particular modpack first displayed the symptom.

## Regression tests

`src/test/java/com/fuzs/aquaacrobatics/core/asm/AquaModelPoseTest.java`:

- `idleActualModelsBeforeAndAfterAquaHaveTwoNeutralArms`
- `missingEntityClearsPriorSwimmingOnGeneratedLivingOverride`
- `racialModelsRetainTheirOwnArmGeometryAfterSkinModReplacement`
- `swimCrawlAndReturnToLandKeepBothOwnedArmsInBothNamespaces`
- `firstPersonRacialAnglesDoNotReapplyThirdPersonSwimming`
- `directPreviewAnglesWithoutLivingCallbackDiscardPreviousEntityBlend`
- `previewPresentationResetRestoresGeometryAndNeutralPose`
- `actualModelRenderEntryDrawsBothArmsForWorldAndPreviewSetup`

Tests execute actual model and Aqua transformer/helper code. The replacement test
performs the two arm assignments observed in Ears bytecode; it does not run the
complete Ears mod. An inert ASM-authored entity fixture supplies pose state without
a world or NPC spawn. Tests check both arms, identity, finite positions/rotations,
land reset, held-item angles and model draw dispatch.

Development and SRG model-method layouts are transformed and structurally checked;
SRG methods are then mapped back solely for execution against the test runtime's
MCP superclass. OpenGL output is replaced with a draw recorder. No Minecraft/Forge
class files are added to the repository. These are not full client, texture-pixel,
multiplayer, or real Forge launch tests. Swimming/crawling tests exercise their
shared SWIMMING presentation pose, not water physics.

## Validation and manual gate

Focused command:

```powershell
.\gradlew.bat test --tests 'com.fuzs.aquaacrobatics.core.asm.*' --tests 'com.lotrcharactercreation.*' --tests kome.integration.CharacterCreationIsolationTest --no-daemon --console=plain
```

Full command:

```powershell
.\gradlew.bat test --no-daemon --console=plain
```

Full suite: 677 tests, 675 passed, 2 skipped, zero failures/errors.

The deployed source and destination JARs both had SHA-256:
`37865CD249CFF010032E486C53C4D3C232F06641F7223BB7625697E463A1933E`.
The user reported that startup, fresh-world loading, save/reload, a full client
restart, swimming and crawling passed, and that both arms became visible after
the correction.

The exact screen-by-screen arm checklist was not separately recorded, so this is
not independent confirmation of every third-person, inventory, appearance-
selection and first-person preview surface. Matching dedicated-server and
multiplayer validation remain outstanding.

The automated limitations above remain: the tests do not replace texture-pixel,
real Forge launch, dedicated-server or multiplayer validation. The runtime result
is user-reported evidence from the deployed local client, not a new automated test.
