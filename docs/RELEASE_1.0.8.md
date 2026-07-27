# KOME 1.0.8

KOME 1.0.8 delivers the schema-7 alliance, Build, population, Tile Command, and server-record redesign developed after 1.0.7.

## Highlights

- Adds persistent Builds with contribution review, population ownership, succession, deletion safeguards, map markers, and audit history.
- Separates Base and Build population pools while preserving controller access rules and existing save compatibility.
- Rebuilds Tile Command around responsive Builds, Population, and Allocations tabs.
- Replaces the legacy alliance screens with one directional four-stage alliance workflow and an authoritative shared ledger.
- Adds typed server records, controlled-tile drilldown, readable waypoint-first tile labels, and corrected scrolling.
- Adds destructive-action preflights and confirmation flows across Build, alliance, war, and tile actions.
- Allows ordinary players to use the read-only `/build` and `/war` inspection commands while retaining operator permission checks for every mutation.

## Compatibility

- Minecraft 1.7.10
- Forge 10.13.4.1614
- LOTR Mod v36.15
- Alliance schema 7
- Build schema 1
- Population schema 2

Install the KOME jar alongside the untouched LOTR v36.15 production jar. Every client and the server must use the same KOME version. Back up an existing world before upgrading.

## Verification

- Clean Gradle test/build: 155 tests, 0 failures, 0 errors, 0 skipped.
- Deterministic GUI matrix: Small, Normal, Large, Auto, and 854x480.
- Production LOTR jar remains external and unmodified.

Automated and screenshot checks do not replace the live multiplayer checklist in `KOME_TEST_PLAN.md`, especially the authoritative ledger container slots and hitboxes.
