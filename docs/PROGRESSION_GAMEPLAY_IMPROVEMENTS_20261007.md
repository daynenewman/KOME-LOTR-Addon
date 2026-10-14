# Progression gameplay improvements — started 2026-10-07, completed 2026-10-08

Implemented on `elijahrnovak/kom-72-implement-revised-branch-based-player-progression-system`, based on `69dbad5a8d2d1cef461e8327b961ce9194d8241f`. The starting working tree was clean. The synchronized dev integration, other contributors' systems and existing stashes were retained. No commit, push or merge occurred.

This report describes code behavior and automated evidence. **Minecraft was not launched.** It does not claim visual correctness, reliable live pathfinding, or production readiness.

## Implemented changes

- Native world player lettering receives a synchronized canonical rank/faction suffix while keeping the original native name, GameProfile, skins, nicknames, visibility checks, scoreboard below-name display, chat, commands and tab identity. Only exact legacy KOME title-team memberships are retired. Rohan's existing “Rider of Rohan” title is not given a duplicate faction suffix. Native English faction resources provide a dedicated-server fallback.
- Earned Serf, Knight, Lord and existing Lord → Prince transitions announce publicly through native server chat. Calls occur only at successful transition boundaries. Rank/state guards reject duplicate reports; synchronization, loading, faction changes and staff rank repair do not announce earned promotions. Forced service provenance suppresses earned announcements.
- The current/prospective Liege confers Knight standing, with a short culture-sensitive speech. The former Master cannot confer it through clicks or the old menu packet. Trial completion and legitimate personal conferral remain separate; the former Master's gift stays optional. Completed trials can be heard by a valid replacement Liege after ordinary loss.
- New Provision duties request 3–4 distinct goods including a drink, with safe reduction for a small pool. Actual food types use local/common pools; native ordinary-mug water is the conservative drink route. Cooked-food requests check the player's cooking permission against actual native smelting outputs.
- New Profession duties request 3–4 distinct nonfood goods using existing occupation/interface/class/trade-pool classification. Conservative gathering and ordinary recipe inputs replace unverified regional military alloys, equipment and reward-only materials. Existing quantity conventions remain; rarity/effort balancing still needs improvement.
- Saved per-unit native standard trade quotes and cumulative real deposits replace flat Provision/Profession compensation. Partial handovers carry fractional value; cumulative reservations prevent replay payment. There is no added service bonus. Active legacy requests gain saved prices without rerolling goods or deposits. Completed legacy assignments gain no retroactive reward. An explicit Master visit can replace invalidated requirements while preserving selected matching deposits and cadence.
- Courier HUD objectives are text/icon only. Recovery HUD icons use saved item metadata or sealed-wrapper contents, with a neutral fallback for missing data. Existing bread/profession icons are preserved; relief uses provision presentation.
- Show Me uses native map coordinates, maximum native zoom power 4, stable camera fields and the existing ten-second yellow emphasis. The focused map restores the prior zoom preference on close. Active markers remain server-owned; moving charge targeting uses current published actor coordinates.
- Active Liege service presentation uses the current assignment's title, stage, destination, goods/actors and report/recovery instructions.
- Correct held bound recovery/property handovers precede ordinary NPC GUI/offer handling, including inventory transfers before pickup/tracker flags refresh. Completed-trial conferral does not require a living former Master. Successful Master goods and relief handovers also consume and acknowledge before ordinary screens. Empty/nonmatching Master deliveries leave native GUI access available. The central Forge handler owns cancellation; native/server-thread packet validation remains.
- Item handovers use `mob.horse.leather` at the receiving NPC, the native LOTR pouch-restock sound. Deliberate progression drops use `random.pop` at the dropped entity. Playback occurs on the server after successful exchange/issuance.
- Failed Standing trials clean up and release the prospective Liege. A bounded saved record holds player, NPC, faction, trial/token, failure time and expiry. The same NPC remains barred for 20 real elapsed minutes, independently of daily trial cadence. Eligibility and the assignment mutation boundary both enforce it. The book explains rejection and retained duties.
- Relevant noble Quest buttons can remain visible and disabled with server-authored primary blockers. Existing packet/GUI state transports relevance/reason and refreshes as eligibility changes.
- Travelers use native speech for actual separation warnings with a 20-second cooldown. Gratitude comes from the arrived traveler and triggers once. Existing no-teleport follow/wait/recalculate controls remain.
- Small incidental escort ambushes use native regional hostile forces and safe loaded natural sites, at most two mobs per successful attempt. Rolls are suppressed by nearby hostiles, guarded by a minute interval, initial accumulated travel, native terrain/spawn eligibility and a nearby threshold of four. Friendly eligible territory uses 6% versus 18% at the roll gate. Mobs have no objective token, required-enemy outline, kill quota or required persistence lease; native despawning remains.
- The old outline path exited on the default zero Forge stencil bits. Its replacement is a scoped, stencil-free inverted main-model hull within the native rendering transform. It avoids recursive whole-entity rendering and framebuffer mutation, restores GL attributes/matrix/active texture, and only targets the owner's bound required NPC enemies at 32–96 blocks. It adds one model pass per relevant rendered NPC.
- Eight saved recent destinations per player/context and weighting from existing recent commission history prefer legitimate alternatives. Saved assignments do not change on reconnect/map use. Unsupported geography is not manufactured to achieve variety.
- `/kome progression finishDuty`, `status`, `why`, and `resetDuty` extend the existing staff command router. Mutations are logged, server-authoritative and permission checked before state access. Courier/trial/commission personal reporting remains pending. Forced work does not mint rewards, count earned commission service or grant rank automatically. Reset cleans current test actors/items/leases while retaining unrelated earned history, rank and cadence.

## Partial implementation and limitations

- **Red outlines require live confirmation.** Bytecode/selection tests pass; no screenshot or GPU test exists. The hull is the native main model, does not provide through-terrain glowing, and is not a separate armor-shell outline. Other renderers/shaders can affect its visibility.
- Native nameplate readability, native speech timing, successful GUI suppression, map centering/zoom preference restoration and multiplayer refresh require live confirmation.
- Show Me currently excludes relationship-only return/report targets. Supported geographic focus is implemented; full report-stage navigation is not. Audit issue P-008 records this.
- Disabled Quest explanations cover principal state/rank/faction/cooldown blockers, but passive native-offer occupancy/preparation can still leave an unavailable button without a useful reason. Audit P-006 records the authoritative-result mismatch.
- Culture/role dialogue is curated for selected high-frequency interactions; shared older speech remains. Unknown/soldier occupations can still use the established general-labor fallback. No separate dialogue/personality engine was built.
- Goods acquisition is deliberately conservative, not an exhaustive native recipe/trade reachability engine. Most specialized drinks reduce to water. Existing material quantity tiers still share the 32–64 stackable convention.
- Invalid request replacement is achievable recovery, not lossless refund of every old deposit. Physical payment spawn failure consumes its reservation. Minecraft's separate player/entity/world saves do not provide crash-atomic exchange transactions.
- Escort paths/refuge coverage and realized ambush frequency were not measured in Minecraft. Existing dangerous Knight escorts retain their mandatory bound encounter; newly added incidental ambushes impose no kills.
- No new Lord/Prince gameplay, Fangorn Liege host, refuge construction, shader-specific outline system or protocol support for old builds was implemented. These were preserved scope boundaries or audit findings, not silently claimed complete.

## Compensation examples

Native standard pool costs were inspected in the shipped v36.15 bytecode. The policy takes one quarter of the cheapest per-unit pool price, floors to milli-silver, caps a unit at 1 silver, and caps the duty at 128 silver. Unknown ordinary goods use 0.01/unit; recognized ordinary ingots use 0.10/unit. Dynamic container/enchantment trades are excluded. Material service bonus: **0**.

| Example request | Calculation from real delivered quantities | Payment |
| --- | --- | --- |
| Inexpensive builder: 32 cobblestone, 32 oak planks, 32 clay balls | 32 × 31 + 32 × 62 + 32 × 10 fallback = 3,296 milli-silver | 3 silver |
| Moderate smith: 32 coal, 32 iron ingots, 32 copper ingots | 32 × 125 + 32 × 750 + 32 × 750 = 52,000 milli-silver | 52 silver |
| Expensive goldsmith: 64 gold ingots, 64 gold nuggets, 64 copper, 64 coal | 64 × 1,000 + 64 × 500 + 64 × 750 + 64 × 125 = 152,000 milli-silver | 128 silver cap |

These are supported example compositions/calculations, not live assignment captures. Incremental payout is the newly reached cumulative whole-coin value minus the saved amount already reserved. Zero new deposits produce zero new reimbursement. The price discount/cap deliberately discourages buy-and-deliver arbitrage; fairness/effort and third-party markets need live testing.

## Existing systems reused and compatibility

Reused canonical `KOMEPlayerProgression`, Serf/Knight duty transitions, commission/Lordship records, current-Liege policy, native offer wrappers, inventory delivery plans, lost-item tokens/revisions, `KOMEProgressionItemDrops`, optional gift issuance, native `LOTRTradeEntry`/coin APIs, NPC occupation classification, native speech, hired follow controls, reversible role/home/teleport leases, native invasion/faction geography, verified shelter observations, tracker/book/map markers, server task queues, `/kome` permissions and central audit logging.

New infrastructure is confined to public identity projection and two native rendering adapters, a public identity packet, transition announcement transport, bounded history/context, conservative goods acquisition/pricing/recovery, curated speech/escort danger, authoritative eligibility presentation and administrative orchestration. It does not create a second quest/promotion engine or a global quest-history database.

NBT additions are inside existing progression records. The synchronized dev tactical IDs 48–51 and response 52 remain; new identity packet is 53. Eligibility packet payload is extended. **Client and server must use this same updated build**, even though the package version remains 1.0.8. Old-build interoperability is unverified. Other contributors' conflict, tactical, movement, population, conquest, hiring and character systems passed the full suite.

## Verification

Environment: cached Gradle 8.5; Java 17 Gradle runner; existing Java 8 compiler/toolchain; offline dependencies. No build configuration change.

- New focused tests: **34 passed / 0 failed / 0 skipped**.
- Expanded focused run: **911 passed / 0 failed / 3 skipped**, 914 total, 151 suites.
- Full project suite: **2,848 passed / 0 failed / 5 skipped**, 2,853 total, 287 suites.
- Ordinary `test build --offline --console=plain`: **BUILD SUCCESSFUL**, exit 0; 18 actionable tasks, 4 executed, 14 up-to-date. JAR/sources/reobfuscation completed.
- `git diff --check`: **passed**.

Full-suite group counts below are exact class-name filter summaries and overlap; do not add them together:

| Area | Passed | Failed | Skipped |
| --- | ---: | ---: | ---: |
| Master/Serf | 104 | 0 | 0 |
| Courier/Standing/Serf-Knight | 100 | 0 | 0 |
| Knight commissions/service | 48 | 0 | 0 |
| Lordship | 83 | 0 | 0 |
| NPC hierarchy/rulers | 60 | 0 | 0 |
| Character creation/UI/appearance | 214 | 0 | 2 |
| Rendering/presentation/packets/transformers | 131 | 0 | 3 |
| Geography/spawning | 39 | 0 | 0 |
| Administrative commands/diagnostics | 111 | 0 | 0 |

Existing skips: two platform symlink support/permission assumptions in custom-skin tests; three optional external original/configured production JAR fingerprint inputs in waypoint transformer tests. Shipped dependency-bytecode tests and new actual Minecraft renderer-hook tests executed. No tests were removed or newly disabled. Old expectations for Master-owned conferral, retained failed Liege state, numeric courier HUD and stack-group type counts were replaced with checks of the requested behavior and cleanup.

Verification logs/counts/source hashes are under ignored `outputs/progression-coherence/`. The native trade pricing test exercises native cost/quantity APIs with headless matching because a plain JUnit process cannot bootstrap Forge's ore dictionary. This is disclosed as an integration/live-testing limit.

## Changed files and independent audit

The complete 80-file source/test inventory is in [PROGRESSION_GAMEPLAY_IMPROVEMENTS_FILES_20261007.txt](PROGRESSION_GAMEPLAY_IMPROVEMENTS_FILES_20261007.txt). The reports add three documentation artifacts; no unrelated tracked file is included.

The independent post-verification audit is [PROGRESSION_COHERENCE_AUDIT.txt](PROGRESSION_COHERENCE_AUDIT.txt). It reconstructs all fifteen journey stages, distinguishes implemented from unfinished higher-rank content, records eighteen actionable findings, gives qualitative 1–5 assessments and lists the five next priorities. No new Lord/Prince features were implemented as a result of those findings.

Its highest-priority path blocker is Fangorn's absent native Liege host. Major supported-path concerns are Lord's content/UI handoff, legacy rank/permission overlap, technical trial activation penalties and unverified enemy visibility. Further Lord/Prince expansion needs those design decisions and a real Minecraft acceptance playthrough first.
