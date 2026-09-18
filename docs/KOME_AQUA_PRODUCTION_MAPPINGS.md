# Bundled Aqua: production mapping audit

Scope: the six Aqua transformers registered by `kome.core.KOMECorePlugin`, their
generated methods/interfaces, and the bundled Aqua helpers. Baseline is
`cd9af14a9533bebb0bda9e8d32f914cac81dad9f`. No gameplay feature is removed.

## Evidence and transformation phase

Evidence is local and inspectable, not inferred from similar modern APIs:

- **M**: MCP stable_12 `joined.srg`, `methods.csv`, `fields.csv` under
  `%USERPROFILE%/.gradle/caches/minecraft/de/oceanlabs/mcp/mcp_stable/12/`.
  `joined.srg` SHA-256:
  `43D4376110B2638213D3096F80F8395C076EB05B58D6C0DBD22C0D999B37A357`.
- **T**: this project's Forge 10.13.4.1614 patched target source and bytecode,
  `build/rfg/minecraft-src/java/`, `build/rfg/recompiled_minecraft-1.7.10.jar`,
  and the SRG vanilla bytecode `build/rfg/srg_merged_minecraft.jar`.
- **F**: Forge 1614 `CoreModManager`, `FMLDeobfTweaker`, and
  `DeobfuscationTransformer` in the generated target sources. The launch log and
  `KOMECorePlugin` SortingIndex 1100 place Aqua after FML deobfuscation.
- **A**: compiled bundled Aqua helper/interface definitions and ASM fixtures in
  `src/test/java/com/fuzs/aquaacrobatics/core/asm/`.

At this phase **readable class names do not imply MCP member names**. Production
classes have readable owners/descriptors and SRG members. Gradle reobfuscates
ordinary Java references, not the string literals used to generate instructions.
This explains the startup water-method failure and the subsequent latent member
linkage/override defects.

`AquaAsmMappings.member` uses Forge's existing `runtimeDeobfuscationEnabled`
injection, through `AquaAcrobaticsCore.isDevEnv()`. It chooses MCP in development,
SRG in production, and the explicit raw alias for a raw owner. Missing environment
initialization fails visibly. `type` handles raw/readable descriptor owners.
There is no second remapper, classloading-based member discovery, new reflection,
or new transformer registration. Lookup aliases are kept separate from emitted
names: accepting an MCP alias while matching never authorizes emitting MCP in
production. Existing raw branches remain available but are not Forge's normal
post-deobfuscation production phase.

## Class/descriptor namespace inventory

In the tables, `LType;` denotes the full internal name in this inventory, not a
literal shortened descriptor in the implementation. Java primitives have their
normal JVM descriptors. Target dispatch always uses readable dotted
`transformedName`. Raw aliases are verified from M; readable forms from M/T.

| Type | Readable owner | Raw owner |
|---|---|---|
| Entity | net/minecraft/entity/Entity | sa |
| Living | net/minecraft/entity/EntityLivingBase | sv |
| Player | net/minecraft/entity/player/EntityPlayer | yz |
| ServerPlayer | net/minecraft/entity/player/EntityPlayerMP | mw |
| Boat / Item / Throwable | net/minecraft/entity/item/EntityBoat; net/minecraft/entity/item/EntityItem; net/minecraft/entity/projectile/EntityThrowable | xi / xk / zk |
| Block / Grass / Mycelium / Liquid | net/minecraft/block/Block; BlockGrass; BlockMycelium; BlockLiquid (same package) | aji / alh / amd / alw |
| World / WorldClient | net/minecraft/world/World; net/minecraft/client/multiplayer/WorldClient | ahb / bjf |
| AABB / Vec3 / Hit | net/minecraft/util/AxisAlignedBB; Vec3; MovingObjectPosition (same package) | azt / azw / azu |
| Material / DamageSource | net/minecraft/block/material/Material; net/minecraft/util/DamageSource | awt / ro |
| Biped / ModelBase | net/minecraft/client/model/ModelBiped; ModelBase (same package) | bhm / bhr |
| AbstractPlayer / SP / ClientPlayer | net/minecraft/client/entity/AbstractClientPlayer; EntityPlayerSP; EntityClientPlayerMP (same package) | blg / blk / bjk |
| RenderPlayer / LivingRenderer | net/minecraft/client/renderer/entity/RenderPlayer; RendererLivingEntity (same package) | bop / boh |
| EntityRenderer / Biome | net/minecraft/client/renderer/EntityRenderer; net/minecraft/world/biome/BiomeGenBase | blt / ahu |

Other readable targets: `net.minecraft.client.entity.EntityOtherPlayerMP`,
`net.minecraft.client.renderer.ItemRenderer`,
`net.minecraft.client.renderer.entity.RenderBoat`. Their routing does not emit a
guessed raw class constant: it uses the verified input owner/descriptor. The same
is true of `IBlockAccess`, the player's sleep-result enum, and client movement
packet constructor descriptors. Character Creation targets are mod-owned stable
`com.lotrcharactercreation.client.render.RacePlayerRenderer` and
`com.lotrcharactercreation.client.model.Player{Dwarf,Elf,Hobbit,Man,Orc}ModelAdapter`.

## Minecraft member inventory

Classification: MCP column = development; SRG column = production; raw column =
obfuscated. **L** is matching, **E** emitted instruction, **O** generated override.
Owners show the declaring class where inherited; target/receiver is shown in the
first column. Every row was checked against M and T unless explicitly marked F.
`Common`, `Client`, `Player`, `Server`, `Late`, and `Biome` abbreviate the six
`Aqua*Transformer` classes in `core/asm`.

| Transformer / target | Semantic member / owner | Descriptor | Development name | Production SRG | Raw | Use / correction |
|---|---|---|---|---|---|---|
| Common / Entity | perform water movement / Entity | ()Z | handleWaterMovement | func_70072_I | N | L: corrected wrong getter selector; verify AABB expansion shape |
| Common / Entity | existing in-water flag / Entity | ()Z | isInWater | func_70090_H | M | NOT patched; negative regression fixture |
| Common / Entity | movement / Entity | (DDD)V | moveEntity | func_70091_d | d | L: preserved |
| Common / Entity | expand water AABB / AABB | (DDD)LAABB; | expand | func_72314_b | b | L: validate owner, opcode, descriptor and zero/Y/zero arguments |
| Common / Entity | walking block / World | (III)LBlock; | getBlock | func_147439_a | a | L: validate World owner and virtual call; preserve ordinal/local contract |
| Common / Living; Player / Player | entity tick / Entity, Living | ()V | onEntityUpdate | func_70030_z | C | L/E/O: consistent environment selection; Player invokes Living super |
| Common / Living; Player / Player | travel / Living | (FF)V | moveEntityWithHeading | func_70612_e | e | L: preserved |
| Common / Living | material check / Entity | (LMaterial;)Z | isInsideOfMaterial | func_70055_a | a | L: unchanged breathing bridge |
| Common / Living | set air / Entity | (I)V | setAir | func_70050_g | h | L: unchanged three call-site argument bridges |
| Common / Living | horizontal collision / Entity | Z | isCollidedHorizontally | field_70123_F | E | L GETFIELD: ordinal 1 unchanged |
| Common / Living; Player / Player | jump flag / Living | Z | isJumping | field_70703_bu | bc | E GETFIELD: fix BOTH generated accessors; inherited Player receiver valid |
| Common / Boat, Item, Throwable; Server; Client / OtherPlayer | update tick / Entity and overrides | ()V | onUpdate | func_70071_h_ | h | L: preserved |
| Common / Boat | rotation / Entity | (FF)V | setRotation | func_70101_b | b | L: bridge after second of exactly two calls |
| Common / Boat | random generator / Entity | Ljava/util/Random; | rand | field_70146_Z | Z | E GETFIELD on Boat: corrected production field |
| Common / Boat | splash sound / Entity | ()Ljava/lang/String; | getSplashSound | func_145777_O | O | E INVOKEVIRTUAL on Boat: corrected production call |
| Common / Item | vertical motion / Entity | D | motionY | field_70181_x | w | L GETFIELD: ordinal 0 unchanged |
| Common / Throwable | ray trace / World | (LVec3;LVec3;)LHit; | rayTraceBlocks | func_72933_a | a | L: helper retains receiver and exact input descriptor |
| Common / Throwable | position X / Entity | D | posX | field_70165_t | s | L PUTFIELD on Throwable: single write required |
| Common / Throwable | block collisions / Entity | ()V | func_145775_I (actual T) | func_145775_I | I | E INVOKEVIRTUAL on Throwable: retain actual dev/SRG name; correct raw branch |
| Common / Grass, Mycelium | block tick / Block | (LWorld;IIILjava/util/Random;)V | updateTick | func_149674_a | a | L: preserved |
| Common / Grass, Mycelium | four-argument placement / World | (IIILBlock;)Z | setBlock | func_147449_b | b | L: corrected func_147465_d (wrong six-argument overload) |
| Common / Grass, Mycelium | six-argument placement / World | (IIILBlock;II)Z | setBlock | func_147465_d | d | Not the ordinal-1 target; unchanged elsewhere |
| Server / ServerPlayer | dimensions / Entity | F / F | width / height | field_70130_N / field_70131_O | M / N | E GETFIELD: correct names AND Entity declaring owner (sa raw) |
| Server / ServerPlayer; Player / Player | resizing / Entity | (FF)V | setSize | func_70105_a | a | Server E INVOKEVIRTUAL fixed; Player L sleep redirect preserved |
| Server / ServerPlayer | death / Living | (LDamageSource;)V | onDeath | func_70645_a | a | L: exact descriptor replaces broad object argument matching |
| Server / ServerPlayer; Player / Player | eye height / Entity | ()F | getEyeHeight | func_70047_e | g | L: preserved; not confused with Aqua's overloaded getEyeHeight |
| Player / Player | prepare spawn / Entity | ()V | preparePlayerToSpawn | func_70065_x | A | L: existing dedicated-server absence allowed |
| Player / Player | sleep / Player | (III)LPlayer$EnumStatus; | sleepInBedAt | func_71018_a | a | L: descriptor derived from target, stable semantics |
| Player / Player; Late / SP | living tick / Living | ()V | onLivingUpdate | func_70636_d | e | L: Late now requires INVOKESPECIAL to actual superclass |
| Player / Player | read/write entity flag / Entity | (I)Z / (IZ)V | getFlag / setFlag | func_70083_f / func_70052_a | g / a | E INVOKEVIRTUAL: centralized existing dev/SRG selection plus correct raw |
| Player / Player | watcher callback / Entity | (I)V | func_145781_i (actual T) | func_145781_i | i | O/E INVOKESPECIAL: retain actual name, correct raw branch |
| Client / RenderPlayer | first-person arm / RenderPlayer | (LPlayer;)V | renderFirstPersonArm | func_82441_a | a | L: corrected func_76986_a; exact owner/descriptor enforced |
| Client / RenderPlayer | arm angles / Biped | (FFFFFFLEntity;)V | setRotationAngles | func_78087_a | a | L INVOKEVIRTUAL: exact Biped owner; reject ambiguous/wrong-owner calls |
| Client / RenderPlayer | player render / RenderPlayer | (LAbstractPlayer;DDDFF)V | doRender | func_76986_a | a | L: valid here, not the first-person arm method |
| Client / RenderPlayer | super render / LivingRenderer | (LLiving;DDDFF)V | doRender | func_76986_a | a | L: exact INVOKESPECIAL owner and descriptor |
| Client / RenderPlayer | body rotation / RenderPlayer | (LAbstractPlayer;FFF)V | rotateCorpse | func_77043_a | a | L: preserved |
| Client / Biped | model render / Biped | (LEntity;FFFFFF)V | render | func_78088_a | a | L: angle invocation owner/descriptor checked |
| Client / Biped, Character Creation models | angles / Biped, model adapter | (FFFFFFLEntity;)V | setRotationAngles | func_78087_a | a | L: preserved; Character Creation post bridge mod-owned |
| Client / Biped | swing field / ModelBase | F | onGround (actual T) | field_78095_p | p | L GETFIELD: intentional ordinal 0, retained |
| Client / Biped | living animations / ModelBase | (LLiving;FFF)V | setLivingAnimations | func_78086_a | a | O: correct production override; reject existing signature collision |
| Client / SP | block pushout / Entity/SP | (DDD)Z | func_145771_j (actual T), pushOutOfBlocks alias | func_145771_j | j | L: accept stable_12 label too; no wrong emission |
| Client / SP | action tick / Living/SP | ()V | updateEntityActionState | func_70626_be | bq | L: preserved |
| Client / ClientPlayer | send movement / ClientPlayer | ()V | sendMotionUpdates | func_71166_b | a | L by both packet constructors (DDDDZ)V and (DDDDFFZ)V; no emitted MC name |
| Client / ItemRenderer | warped-water overlay / ItemRenderer | (F)V | renderWarpedTextureOverlay (actual T) | func_78448_c | c | L: actual target name preserved |
| Client / EntityRenderer | camera / EntityRenderer | (F)V | orientCamera | func_78467_g | h | L: preserved |
| Client / EntityRenderer | camera Y / Entity | F | yOffset | field_70129_M | L | L GETFIELD: yOffset - 1.62 shape preserved |
| Client / EntityRenderer | previous X / Entity | D | prevPosX | field_70169_q | p | L GETFIELD: following anchor preserved |
| Client / EntityRenderer | camera ray / World or WorldClient | (LVec3;LVec3;)LHit; | rayTraceBlocks | func_72933_a | a | L INVOKEVIRTUAL: exact receiver is retained in helper descriptor |
| Client / RenderBoat | boat render / RenderBoat | (LBoat;DDDFF)V | doRender | func_76986_a | a | L: second GL scale distinguishes typed render from generic bridge |
| Client / RacePlayerRenderer | overridden render / mod class | (LAbstractPlayer;DDDFF)V | doRender | func_76986_a | a alias | L: two actual-super delegates; no registration change |

Critical M line references: water 19759-19760; arm 15526; jump 7880;
rand 7757; splash 19761; dimensions 7744-7745; resize 19776;
ModelBase animation 14450; four-/six-argument setBlock 9781/9816.
Names alone are insufficient: the `a`, `b`, `N`, and `g` raw aliases have many
unrelated overloads. Exact descriptors/owners and instruction shape disambiguate.

Two CSV labels are not the names in this project's actual patched development
classes: `field_78095_p` is labelled `swingProgress` but T uses `onGround`, and
`func_78448_c` is labelled `renderWaterOverlayTexture` but T uses
`renderWarpedTextureOverlay`. Similarly T retains `func_145775_I` and
`func_145771_j`. Replacing verified T names blindly from CSV would break runClient.

## Stable names, helpers and interface emission

These identifiers are deliberately **not** remapped to guessed SRG names.

| Transformer / target | Owner / semantic member | Descriptor | Classification / verification / use |
|---|---|---|---|
| Biome / Biome | getWaterColorMultiplier | ()I | Forge-added stable (F/T), L/E; original IRETURN filter and existing aqua$waterColorMultiplier facade; static AquaBiomeLogic.c(LBiome;I)I |
| Server / ServerPlayer | getDefaultEyeHeight | ()F | Forge-added stable (F/T), L |
| Common / Liquid | Block.getLightOpacity | (LIBlockAccess;III)I | Forge-added stable (F/T), O and INVOKESPECIAL; common-side descriptor derived from getBlocksMovement shape, not client-only brightness |
| Player / Player | FMLCommonHandler.onPlayerPostTick | (LPlayer;)V | FML stable (F/T), L INVOKEVIRTUAL; opcode/owner/argument verified |
| Client / ItemRenderer | GL11.glColor4f | (FFFF)V | LWJGL stable, L INVOKESTATIC ordinal 0 |
| Client / RenderBoat | GL11.glScalef | (FFF)V | LWJGL stable, L ordinal 1 |
| Client / SP | Math.round | (F)I | JDK stable, L; replaced with static mod helper |
| All constructors | actual superclass.<init> | existing target descriptor | JVM stable, L; unchanged constructors remain intact |

All `com/fuzs/aquaacrobatics/` owners are **mod-owned stable**. Static bridges
prepend the receiver type to the original arguments; return descriptors are
unchanged. Fixtures resolve each emitted mod call against actual compiled helper
metadata and check static/instance shape. Specifically:

- Common: `AquaEntityPrimitiveLogic` bubble callbacks, water-Y and climbing-block
  helpers; `AquaLivingEntityLogic` breathing, air and ladder helpers;
  `AquaThrowableLogic` projectile initialization/ray trace;
  `UnderwaterGrassLikeHandler` two head callbacks and placement guard;
  `AquaItemWaterPhysicsLogic.getMotionYForUpdate`; `AquaLiquidLightingLogic`;
  `AquaBoatRockingLogic` data registration, update, angle and bubble callbacks.
- Player: `AquaPlayerAsmHooks`, `AquaPlayerResizeLogic`, `AquaPlayerLifecycleLogic`,
  `AquaPlayerSizeMetadataLogic`, `AquaPlayerPresentationLogic`,
  `AquaPlayerDataWatcherLogic`, `AquaPlayerCompatibilityLogic`,
  `AquaPlayerWaterLogic`, `AquaPlayerLegacyBobLogic`, `AquaSwimmingTravelLogic`.
- Server: the existing ServerPlayer overloads in `AquaPlayerLifecycleLogic`.
- Client/Late: `AquaClientPlayerMovementPolicy`, `AquaClientPlayerSwimmingLogic`,
  `AquaRemotePlayerPresentationLogic`, `AquaWaterOverlayRenderLogic`,
  `AquaBoatRenderLogic`, `AquaModelBipedLogic`, `AquaRenderPlayerLogic`,
  `AquaPlayerLightingLogic`, `AquaCameraRenderLogic`, `AquaCameraCollisionLogic`.

Generated interface methods retain their source declarations: bubble callbacks
`(Z)V`; living/player `aqua$isJumping()Z`; boat rocking `(F)F`, random
`()Ljava/util/Random;`, splash `()Ljava/lang/String;`; player state/pose/size,
swimming/crawling flags, overloaded Aqua eye-height and lifecycle facades; client
movement storage getter, swimming predicates `()Z` / `(FF)Z`; model
`setSwimAnimation(F)V` and `getSwimAnimation()F`. `INVOKEINTERFACE` targets remain
mod-owned (`IPlayerResizeable`, `IAquaClientPlayerMovementStorageAccess`).
These are not vanilla overrides except those explicitly marked O above.

Generated fields are mod-owned: `aqua$playerState` (AquaPlayerState),
`aqua$isNewProjectile` (Z), `aqua$movementStorage` (MovementInputStorage),
`aqua$networkOriginalPosY` (D), `swimAnimation` (F), and the new synthetic
`aqua$patched$<transformer>` (Z). Mod `AquaPlayerState.size`, `EntitySize.width`
and `EntitySize.height` must remain readable; banning every literal `width` would
incorrectly rename these mod fields. No injected Minecraft GETSTATIC/PUTSTATIC
member required correction. Preserved vanilla static references and ordinary
Java helper calls are reobfuscated normally by the build.

The helper-source scan found no other ASM generators or use of
`FMLDeobfuscatingRemapper`. Existing `AquaClientPlayerMovementPolicy` reflection
uses both `sprintToggleTimer/field_71156_d` and `flyToggleTimer/field_71101_bC`,
verified against M/T and left unchanged. Existing `OptifineHelper` reflection
targets OptiFine-owned names, not MCP/SRG fields. No reflection is added to
transformed gameplay methods.

## Failure and registration contracts

All six transformers publish bytes only after their patch and verification
complete. The shared wrapper adds transformer/target context and rethrows the
original RuntimeException as its cause; it never returns partial bytes or
silently swallows a required transformation. Exact method lookup reports expected
aliases, descriptor and match count. Instruction failures report the missing or
ambiguous semantic anchor/shape. The synthetic marker rejects a second application
to already transformed bytes independently for each transformer.

Existing intentional optionality is unchanged: ItemPhysic owns EntityItem when
present; preparePlayerToSpawn is absent on a dedicated server; the old
require=0 grass ordinal-1 placement bridge remains optional when a mod has removed
that call. No formerly required patch is disabled. The corrected SRG overload
makes that grass bridge execute in unmodified production targets.

The combined KOME registry/order and manifest remain unchanged. F's
`CoreModManager` calls `getASMTransformerClass()` for a null check and again for
iteration; this is **not duplicate registration**. The regression test queries it
twice and checks each returned list contains each expected transformer once in
the existing order. Installing a second standalone Aqua-containing mod remains a
separate incompatible duplicate integration, not fixed by weakening guards.

## Validation and limitations

`AquaProductionMappingsTest` runs actual transformers on legal, repository-owned
ASM instruction fixtures in both MCP and SRG layouts. It checks getter vs
implementation, arm vs generic renderer, names/owners/descriptors, negative and
ambiguous matches, emitted links, class/stack ASM verification, and duplicate
application. It does not contain copied Minecraft or Forge classes. Its optional
`aqua.expectedJar` assertion allows running the same suite with the production
distributable first on the classpath and proving the tested transformer came from
that JAR rather than `build/classes`.

The raw server/boat/model fixtures additionally execute those transformers and
check raw owners, field names and override descriptors with ASM verification.
Unlike the normal MCP/SRG fixtures, raw fixture descriptors have not yet passed
through FML's remapper, so their mod helper links are not resolved against the
already readable compiled helper classes at that intermediate stage.

Supplemental offline inspection reads locally generated Forge-patched classes
and remaps their members in memory using M; no game classes are added to source
control. That sweep checks real instruction layouts across all six transformers.
That mapping-only sweep is not a LaunchWrapper/modpack launch, entity execution,
or multiplayer test. ASM verification and a clean build cannot establish
compatibility with every other mod's transformation. The later combined mapping
and racial-arm artifact passed user-reported local CurseForge startup, fresh-world
loading, save/reload, full client restart, swimming and crawling. Boat-specific,
complete gameplay, dedicated-server and multiplayer validation remain outstanding.

### Recorded validation (2026-09-16 local time)

All commands below ran from the repository root:

```powershell
.\gradlew.bat test --tests com.fuzs.aquaacrobatics.core.asm.AquaProductionMappingsTest --no-daemon --console=plain
.\gradlew.bat test --no-daemon --console=plain
.\gradlew.bat clean test build --no-daemon --console=plain
git diff --check
git diff --cached --check
```

- Focused: 17 tests, zero failures/errors/skips.
- Full and clean build: 669 tests discovered, 667 passed, two existing
  environment-dependent Character Creation symbolic-link tests skipped;
  zero failures/errors. Clean test/build completed successfully.
- Production-JAR-first rerun: same 17 tests passed on the Gradle Java 8 launcher
  (Zulu 8.0.492), with `aqua.expectedJar` verifying actual transformer code source.
  The external diagnostic init script only reordered the test classpath; project
  build configuration was not changed.
- Offline real-layout sweep: 20 target/transformer pairs in development, 20 in
  mapped SRG, and 20 SRG pairs using the production JAR all passed ASM analysis
  and resolution of newly injected Minecraft/Aqua links through the actual class
  hierarchy. The external probe uses JDK 21 only to inspect byte arrays; it does
  not load or launch the Minecraft runtime. Its missing Forge GUI log-appender
  warnings are a standalone diagnostic-classpath limitation, not a transformation
  failure. These sweeps do not exercise the other installed mods' transformers.
- Earlier mapping-only artifact: `build/libs/KOME-LOTR-Addon-1.0.8.jar`, SHA-256
  `D0BC30282E9C804ECA3141E12C0BE3F29629C626B5D087925D5B2834379548F3`.
  All six Aqua transformer entries and the helper occur once; class-file version
  52 (Java 8). No duplicate JAR entries. Manifest retains
  `FMLCorePlugin: kome.core.KOMECorePlugin`, `FMLCorePluginContainsFMLMod: true`,
  and `FMLAT: lotrcharactercreation_at.cfg`; the access-transformer resource is
  present. The registry test also passed using the packaged registry class.
- Final combined mapping and racial-arm correction artifact: SHA-256
  `37865CD249CFF010032E486C53C4D3C232F06641F7223BB7625697E463A1933E`.
- Both Git whitespace checks passed. Git's LF-to-CRLF notices are not whitespace
  errors. At the time of the earlier mapping-only validation, no source was staged,
  committed or deployed.
