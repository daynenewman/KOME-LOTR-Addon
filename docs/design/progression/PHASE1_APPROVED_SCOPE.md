# Player Progression and Advancement System - approved Phase 1 scope

Authority: the owner's Phase 1 assignment in this session, approved 2026-10-17.
The separate full redesign document was not found in the checkout; its location
has been requested. This file records the supplied Phase 1 specification only.
It does not invent or approve later-phase mechanics.

## Required foundation

1. Faction allegiance and permanent rank are independent. Pledging establishes
   native allegiance and its rules; it never promotes the player. Pledged players
   may remain Commoner. Preserve alignment and legitimate historical progress.
2. The normal ranks are Commoner -> Knight -> Lord -> Prince. Commoner replaces
   obsolete starting-rank terminology. King is a political office, not a rank.
3. Choosing a birth faction in character creation establishes a pledge and awards
   +100 alignment once per character. Later pledges award no birth bonus. Relog,
   changed allegiance and reopened creation must not grant the award again.
4. Inspect existing formats before migration. Preserve earned higher ranks,
   completed duties, relationships, alignment and advancements wherever possible.
   Do not erase records or grant a retroactive birth bonus without reliable evidence.
5. Update rank/book status concisely, preserving navigation, Show Me and parchment
   appearance. Do not rewrite the GUI.

## Explicitly deferred

Revised Master quest pools and letter handoff; new Knight commissions; royal
objectives; conquest permissions; equipment/crafting restrictions; specialty nodes.
Only minimal compatibility work for existing service may cross these boundaries.

## Validation and delivery

Preserve branch, inherited edits, stashes, saves and runtimes. Use verified backups,
focused tests, disposable saved-data fixtures, then Full test/build/reobfuscation.
Create verified local checkpoints; no push or jar installation. Update current
status, relevant routes/design documentation and 10-15 practical Yes/No playtests.
Automated checks are separate from Minecraft acceptance.

## Open clarification

Existing birth finalization raises alignment to the native pledge minimum. The
owner has been asked whether +100 replaces that grant or is added afterward.
Bonus implementation must wait for that choice or an authoritative full-spec rule.
