# KOM-58 / KOM-59 exact proposals — unapproved

No production resource was changed. Current mask SHA-256 is
`ab792277f61882d415963bf5af1b8d2705458c68de80f5b3cbb9102e30d1b4a7`;
production exclusions still contain zero zones/cells.

[Region proposals](region-proposals.csv) identify all 43 existing atlas regions,
world bounds, groups and exact concern counts. The default proposal is to preserve
current cells and unknown gaps. [Unknown-gap decisions](unknown-gap-decisions.csv)
lists the 693 existing atlas gap cells with blank approved type/reason. It does not
classify all 8,732,021 raster gaps, infer biome semantics or convert a gap to an exclusion.

[Mountain alternatives](mountain-alternatives.csv) and
[exact candidate cells](mountain-candidate-cells.csv) supply two reviewable alternatives
for each of T435/T654, T400/T420, T329/T355, T352/T356 and T218/T220:

1. A removes only participating contact cells belonging to the first tile.
2. B removes only participating contact cells belonging to the second tile.

The union is exactly 99 unique cells. Each alternative removes an endpoint of every
recorded direct/diagonal contact for its pair. Proposed `mountain` type/reason is
**unapproved**, cannot overlap the current active tile, and can only become valid
metadata after an approved KOM-59 raster change with a new bound mask hash.
These sparse alternatives are precise minimum contact-removal candidates, not
certification of a wider barrier, tile connectivity, all neighboring contacts or
persistent-reference migration. Those checks precede application after a choice.

Recommend choosing the intended mountain side and width on the existing
[contact atlas](../../kom80-mountain-barriers/README.md) before approving either sparse
alternative. No authority presently selects a side automatically. Keep the authored
T239–T232–T220–T223–T233 corridor, six bridge permissions, protected junction/controls,
T149 optional shape cell and seven nearby geometry-only pairs unchanged.
T455/T654 has no direct current contact; do not substitute the observed T444/T654
contact for that Harnen report. Obtain exact live tile IDs, build/hash and location.

Reproduction: run `prepare-geography.py` from the parent report using Python with
the existing NumPy/Pillow dependencies. It validates current cell identities and
checks every production map resource hash before/after. Output hashes and counts
are in [proposal-summary.json](proposal-summary.json). This is a proposal generator,
not a tile editor or an approval.
