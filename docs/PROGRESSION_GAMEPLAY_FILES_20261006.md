# Files changed by the progression gameplay pass

Compared with the file-hash snapshot taken at session start. Pre-existing uncommitted changes remain preserved.

| File | Change | Purpose |
| --- | --- | --- |
| docs/PROGRESSION_GAMEPLAY_AUDIT_20261006.md | Created | Initial native/KOME audit, baseline findings and explicit no-generation safety decision. |
| docs/PROGRESSION_GAMEPLAY_FILES_20261006.md | Created | Complete created/modified file inventory for this pass with per-file purpose. |
| docs/PROGRESSION_GAMEPLAY_REPORT_20261006.md | Created | All 61 request statuses, integration, behavior, rewards, verification and live-testing limitations. |
| src/main/java/com/lotrcharactercreation/client/gui/ClientCreationContinuation.java | Created | New client pause/options continuation using the latest server-authorized mandatory screen; disconnect cleanup. |
| src/main/java/com/lotrcharactercreation/client/gui/GuiAppearanceSelection.java | Modified | Mandatory ESC pause and Gender context label; earlier preview work preserved. |
| src/main/java/com/lotrcharactercreation/client/gui/GuiCharacterConfirmation.java | Modified | Final mandatory-stage ESC pause and Gender context label. |
| src/main/java/com/lotrcharactercreation/client/gui/GuiRaceSelection.java | Modified | Open the normal pause menu on ESC. |
| src/main/java/com/lotrcharactercreation/client/gui/GuiSexSelection.java | Modified | Mandatory ESC pause behavior and visible Gender label; serialized SEX retained. |
| src/main/java/com/lotrcharactercreation/client/gui/GuiStartingFactionSelection.java | Modified | Mandatory ESC pauses while explicit Back remains available. |
| src/main/java/com/lotrcharactercreation/proxy/ClientProxy.java | Modified | Route mandatory stage screens through continuation; remove redundant selection success chat while retaining errors. |
| src/main/java/kome/client/KOMEFactionTitleClientBridge.java | Modified | Own-faction Rank prefix; alignment-bar title avoids inheriting the page prefix. |
| src/main/java/kome/client/KOMEProgressionMapFocus.java | Modified | Resolve moving escort marker targets through updated UUID presentation while preserving native map focus/highlight. |
| src/main/java/kome/client/KOMEVisualRenderBridge.java | Modified | Native action icon fallback for eligible prospective Lieges and single-line concise hovers. |
| src/main/java/kome/common/data/KOMECourierService.java | Modified | Small reserved reward after genuine completed delivery/report; existing replacement rules retained. |
| src/main/java/kome/common/data/KOMEEvents.java | Modified | Remove automatic gift tick issuance call; narrow active item expiry protection, escort recovery/behavior and protected actor/shelter observation hooks. |
| src/main/java/kome/common/data/KOMEKnightCommission.java | Modified | Additive saved destination proof, actor display names and reward reservation. |
| src/main/java/kome/common/data/KOMEKnightCommissionLocations.java | Modified | Existing physical shelter discovery and hostile native territorial adjacency; no invented roadside refuge. |
| src/main/java/kome/common/data/KOMEKnightCommissionPresentation.java | Modified | Saved-fact sequential objectives, stage-local counts and actual enemy/item/verified-location dialogue. |
| src/main/java/kome/common/data/KOMEKnightCommissionService.java | Modified | Curated property, direct held handover, finite recovery, item protection, distant safe actors, shelter revalidation and host-free real escort arrival. |
| src/main/java/kome/common/data/KOMELiegeProgressionInteraction.java | Modified | Actionable safe-assignment-unavailable dialogue through the existing Quest route. |
| src/main/java/kome/common/data/KOMELordshipTrialPresentation.java | Modified | Shared saved-fact stage projection with book-only survivor/recovery context and localized fallback. |
| src/main/java/kome/common/data/KOMELordshipTrialService.java | Modified | Revalidate physical trial destination using the same existing commission lifecycle. |
| src/main/java/kome/common/data/KOMEPartingGiftService.java | Modified | Tick reconciliation no longer issues gifts; explicit locked drop remains the one issuance boundary. |
| src/main/java/kome/common/data/KOMEProgressionAutoCompleter.java | Modified | Remove technical auto-completion chat and publish courier copy timing in the progression book. |
| src/main/java/kome/common/data/KOMEProgressionBanditPressure.java | Created | New local native probability multiplier for valid carriers, with shared-player/local-bandit/pledge/disabled-progression guards. |
| src/main/java/kome/common/data/KOMEProgressionDestinations.java | Created | New bounded actual roof/wall observation and persisted shelter selection; conservative rejection and no block writes. |
| src/main/java/kome/common/data/KOMEProgressionEncounterSites.java | Created | New loaded-only approximately 50-block open-ground actor placement with terrain/container/building avoidance. |
| src/main/java/kome/common/data/KOMEProgressionEscortFollowing.java | Modified | Existing teleport lease extended with owner-only native follow/wait/recalculate and charge-only pursuit restraint. |
| src/main/java/kome/common/data/KOMEProgressionLanguage.java | Created | New native-first KOME localization adapter with packaged English fallback for headless presentation. |
| src/main/java/kome/common/data/KOMEProgressionLostItems.java | Created | New curated selections from existing faction equipment/native silver and actual content display names. |
| src/main/java/kome/common/data/KOMEProgressionNativeAuthority.java | Modified | Personal name plus actual faction-specific title/faction; native family name sync instead of forced custom plates. |
| src/main/java/kome/common/data/KOMEProgressionNpcInteractionService.java | Modified | Explicit gift claim, advancement guidance, real item/reward paths and explained failed activation. |
| src/main/java/kome/common/data/KOMEProgressionNpcRankService.java | Modified | Apply authority naming through native family synchronization. |
| src/main/java/kome/common/data/KOMEProgressionNpcSpeech.java | Modified | Faction-aware Master prose and actual saved courier recipient context via native LOTR speech. |
| src/main/java/kome/common/data/KOMEProgressionProtectedActors.java | Created | New reversible saved native home-area lease and scoped pursuit restraint; no invulnerability. |
| src/main/java/kome/common/data/KOMEProgressionRulerService.java | Modified | Native ruler names and correct pre-spawn title-only versus initialized identity marker labels. |
| src/main/java/kome/common/data/KOMEProgressionServiceRewards.java | Created | New two-unit coin/food compensation with persisted pre-spawn reservations; no alignment or rank credit. |
| src/main/java/kome/common/data/KOMEProgressionSummary.java | Modified | Progression book replacement-message availability/timing section. |
| src/main/java/kome/common/data/KOMEProgressionTrackerService.java | Modified | Bounded minute-level courier book refresh through existing diff publication. |
| src/main/java/kome/common/data/KOMEProgressionTrackerSnapshot.java | Modified | Binary courier/escort and sequential stage-local commission/trial objectives; no copy countdown. |
| src/main/java/kome/common/data/KOMESerfdomMasterService.java | Modified | Rank conferral prepares optional gift state without physically issuing it. |
| src/main/java/kome/common/data/KOMESerfKnightDefenseService.java | Modified | Distant safe initial attackers, small success reward and bounded missing protected-actor failure. |
| src/main/java/kome/common/data/KOMESerfKnightEscortService.java | Modified | Physical verified destination, saved proof, loaded missing-actor recovery and actual arrival; exclude authority actors as charges. |
| src/main/java/kome/common/data/KOMESerfKnightProgression.java | Modified | Persist reward reservations and retain destination proof on retry; compatible completed-service defaults. |
| src/main/java/kome/common/data/KOMESerfKnightRecoveryService.java | Modified | Persist faction item choice, require physical held return, death-drop lifespan protection and recoverable loss; loaded-only site search. |
| src/main/java/kome/common/data/KOMEVisualLocationService.java | Modified | Saved identity labels, exact standing escort position refresh, concise objective markers and own-pledge ruler filtering. |
| src/main/java/kome/common/data/KOMEWorldData.java | Modified | Additive bounded observed-shelter NBT integrated with transactional load/copy/reset/save. |
| src/main/java/kome/common/item/KOMEItemStolenProperty.java | Modified | Existing sealed recovery property displays the selected native content name/icon without manufacturing usable equipment. |
| src/main/java/kome/common/network/KOMEPacketRelationshipAction.java | Modified | Route existing standing-trial service activation through consistent validated failure handling. |
| src/main/java/kome/common/network/KOMEPacketSerfdomMasterAction.java | Modified | One-time modest rewards for completed provisions/profession through the existing menu path. |
| src/main/java/kome/core/KOMECorePlugin.java | Modified | Register the narrow native bandit event transformer. |
| src/main/java/kome/core/KOMEProgressionBanditTransformer.java | Created | New audited hook into native LOTREventSpawner.spawnBandits local probability, with idempotent transformation. |
| src/main/resources/assets/kome/lang/en_US.lang | Modified | Short objectives, context-aware narrative templates and natural failure/report text. |
| src/test/java/com/lotrcharactercreation/CharacterCreationContinuationTest.java | Created | New actual confirmed-selection NBT reload/stage and server finalization authorization test. |
| src/test/java/com/lotrcharactercreation/client/gui/CharacterCreationPauseTest.java | Created | New actual five-screen ESC dispatch, pause/options deferral, resume and disconnect tests with inert rendering. |
| src/test/java/kome/common/data/KOMECanonicalPlayerRankTest.java | Modified | Update expected provisions prose while retaining native speech and canonical authority contracts. |
| src/test/java/kome/common/data/KOMECourierBoundedRecoveryTest.java | Modified | Successful-delivery fixture supports the newly issued physical secondary reward. |
| src/test/java/kome/common/data/KOMECourierGeographyTest.java | Modified | Allow modeled native hostile neighboring biome in geographic fixture; original courier assertions retained. |
| src/test/java/kome/common/data/KOMEKnightCommissionGameplayTest.java | Modified | Physical shelter proof and inert navigator; assert real arrival without another host and preserved actor lifecycle. |
| src/test/java/kome/common/data/KOMEKnightCommissionInteractionTest.java | Modified | Real shelter/border fixture and stage-local report counts; existing acceptance/replay/report tests retained. |
| src/test/java/kome/common/data/KOMELordshipTrialFixture.java | Modified | Real shelter proof for existing trial lifecycle scenarios. |
| src/test/java/kome/common/data/KOMELordshipTrialGenerationTest.java | Modified | Existing shelter and legitimate modeled frontier for all existing native trial scenarios. |
| src/test/java/kome/common/data/KOMELordshipTrialIntegrationTest.java | Modified | Assert report stage count reset while preserving quotas, survivor return, authority and reload coverage. |
| src/test/java/kome/common/data/KOMEProgressionBanditPressureTest.java | Created | New rate, carrier, shared-player, pledge, revision and progression-disabled behavior tests. |
| src/test/java/kome/common/data/KOMEProgressionFollowupTest.java | Modified | Gift requires interaction, copy availability moves into book, existing cooldown/replay/pouch tests retained. |
| src/test/java/kome/common/data/KOMEProgressionGameplayFixture.java | Created | New real test roof/wall blocks, observed proof and modeled hostile neighboring territory. |
| src/test/java/kome/common/data/KOMEProgressionGameplayPassTest.java | Created | New runtime stage, physical shelter, registry, actor distance, marker, item, reward, ruler, home lease and recoverable failed-activation tests. |
| src/test/java/kome/common/data/KOMEProgressionHardeningFactionTest.java | Modified | All playable-faction native constructor coverage with previously verified shelter and neighboring territory evidence. |
| src/test/java/kome/common/data/KOMEProgressionHardeningGeographyTest.java | Modified | Persist verified destinations; empty hostile geography declines incursions and unsafe surfaces are rejected. |
| src/test/java/kome/common/data/KOMEProgressionHierarchyCorrectionsTest.java | Modified | Assert personal-name/title/faction format and no fabricated Westfold territory. |
| src/test/java/kome/common/data/KOMEProgressionRulerLifecycleTest.java | Modified | Native family-name fixture and assertions; singleton, capital safety and respawn tests retained. |
| src/test/java/kome/common/data/KOMEProgressionUxInteractionTest.java | Modified | Explicit optional gift and verified escort destination/arrival fixtures; existing UX/authority regressions retained. |
| src/test/java/kome/common/data/KOMESerfKnightDefenseServiceTest.java | Modified | Retain no-duplicate activation/native combat contracts with bounded loaded missing-objective evidence. |
| src/test/java/kome/common/data/KOMESerfKnightRecoveryServiceTest.java | Modified | Replace lost-item auto-success expectation with real recoverable failure/no reward behavior. |
| src/test/java/kome/common/data/KOMESerfKnightStabilizationTest.java | Modified | Explicit post-promotion gift claim and real shelter proof; native hiring/cleanup/reconnect coverage retained. |
| src/test/java/kome/common/data/KOMEVisualLocationServiceTest.java | Modified | Exact Master labels and concise hovers; existing moving/packet/identity/cleanup coverage retained. |
| src/test/java/kome/common/KOMEAccessFixture.java | Modified | Native block/item bootstrap plus physical shelter terrain, tile lookup and player state for runtime tests. |
| src/test/java/kome/core/KOMEProgressionBanditTransformerTest.java | Created | New shipped-class hook/idempotency assertions and JVM execution of the transformed native method. |
| src/test/java/kome/core/KOMEWaypointTransformerTest.java | Modified | Updated transformer registration inventory; existing native waypoint bytecode tests unchanged. |
| src/test/java/kome/integration/CharacterCreationIsolationTest.java | Modified | Register bandit hook and explicitly scope new NPC leases while retaining prohibition on KOME-owned player creation storage. |
