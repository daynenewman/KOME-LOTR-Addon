"""Generate review proposals only; never modify production geography or worlds."""
from pathlib import Path
import csv, hashlib, json, collections
import numpy as np
from PIL import Image

OUT = Path(__file__).resolve().parent / "geography"
ROOT = Path(__file__).resolve().parents[2]
MAP = ROOT / "src/main/resources/assets/kome/map"
ATLAS = ROOT / "docs/tile-geography-review-20261002"
CONTACTS = ROOT / "docs/kom80-mountain-barriers"
OUT.mkdir(exist_ok=True)
def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
resources = {p.name: sha(p) for p in MAP.iterdir() if p.is_file()}
assert resources["reset_conquest_tile_ids.png"] == "ab792277f61882d415963bf5af1b8d2705458c68de80f5b3cbb9102e30d1b4a7"
def read(p):
    with p.open(encoding="utf-8-sig", newline="") as f: return list(csv.DictReader(f))
def write(name, rows):
    with (OUT/name).open("w", encoding="utf-8", newline="") as f:
        w = csv.DictWriter(f, fieldnames=list(rows[0])); w.writeheader(); w.writerows(rows)
regions = collections.defaultdict(list)
for c in read(ATLAS/"review-cells.csv"):
    x,y=int(c["mask_x"]),int(c["mask_y"])
    regions[(x//400*400,y//400*400)].append(c)
region_rows=[]
for (x,y),cells in sorted(regions.items()):
    groups=collections.Counter(g for c in cells for g in c["review_groups"].split("|"))
    region_rows.append(dict(region=f"{x}-{y}",mask_x_min=x,mask_y_min=y,mask_x_max_exclusive=x+400,
        mask_y_max_exclusive=y+400,world_x_min=(x-810)*128,world_z_min=(y-730)*128,
        world_x_max_exclusive=(x+400-810)*128,world_z_max_exclusive=(y+400-730)*128,
        exact_review_cells=len(cells),groups=json.dumps(dict(groups),sort_keys=True),
        proposed_disposition="Preserve current assignments and unknown gaps; no geographic reason inferred",
        decision_required="Exact approved assignment or exclusion type/reason for each changed cell",
        atlas_crop=f"../../tile-geography-review-20261002/atlas/{x}-{y}.png"))
write("region-proposals.csv",region_rows)

rgba=np.array(Image.open(MAP/"reset_conquest_tile_ids.png").convert("RGBA"))
mapping={}
for line in (MAP/"reset_conquest_tile_ids.txt").read_text().splitlines():
    if line.strip() and not line.startswith("#"):
        rgb,tile=line.split("="); mapping[tuple(map(int,rgb.split(",")))]=tile
def tile_at(x,y): return mapping.get(tuple(map(int,rgba[y,x,:3]))) if rgba[y,x,3]>24 else "GAP"
pair_cells=collections.defaultdict(dict)
for c in read(CONTACTS/"requested-contact-cells.csv"):
    for side in ("first","second"):
        x,y=int(c[side+"_x"]),int(c[side+"_y"]); tile=c[side+"_tile"]
        assert tile_at(x,y)==tile
        pair_cells[c["pair"]][(x,y)]=(tile,c[side+"_historical"],c[side+"_edit_phase"])
choices=[]; exact=[]
for pair,cells in pair_cells.items():
    for side,tile in zip(("A","B"),pair.split("-")):
        chosen={p:v for p,v in cells.items() if v[0]==tile}
        assert chosen
        # Every recorded cardinal/diagonal contact has one removed endpoint.
        contacts=read(CONTACTS/"requested-contact-cells.csv")
        for c in contacts:
            if c["pair"]==pair:
                assert (int(c["first_x"]),int(c["first_y"])) in chosen or (int(c["second_x"]),int(c["second_y"])) in chosen
        proposal=f"{pair}-{side}"
        xs=[p[0] for p in chosen]; ys=[p[1] for p in chosen]
        choices.append(dict(proposal=proposal,pair=pair,alternative=side,remove_from_tile=tile,
            exact_cells=len(chosen),mask_bbox_inclusive=f"{min(xs)},{min(ys)},{max(xs)},{max(ys)}",
            proposed_assignment="GAP",proposed_exclusion_type="mountain",approved=False,
            effect="Removes every recorded direct/diagonal contact for this pair only",
            limitations="Not a wider barrier design; adjacent topology, references and approved width require review"))
        for (x,y),(tid,historical,phase) in sorted(chosen.items(),key=lambda v:(v[0][1],v[0][0])):
            exact.append(dict(proposal=proposal,pair=pair,mask_x=x,mask_y=y,current_tile=tid,
                historical_tile=historical,last_edit_phase=phase,world_x_min=(x-810)*128,
                world_z_min=(y-730)*128,world_x_max_exclusive=(x+1-810)*128,
                world_z_max_exclusive=(y+1-730)*128,proposed_tile="GAP",
                proposed_zone_id=pair.lower()+"-barrier",proposed_type="mountain",
                proposed_reason="Proposed separation of recorded mountain contact; awaiting explicit approval",
                status="UNAPPROVED_ALTERNATIVE"))
write("mountain-alternatives.csv",choices); write("mountain-candidate-cells.csv",exact)
unknown=[]
for c in read(ATLAS/"review-cells.csv"):
    if c["current_tile"]=="GAP":
        unknown.append({**c,"proposed_disposition":"PRESERVE_UNKNOWN","approved_type":"","approved_reason":""})
write("unknown-gap-decisions.csv",unknown)
assert resources == {p.name:sha(p) for p in MAP.iterdir() if p.is_file()}
summary=dict(resource_hashes=resources,regions=len(region_rows),review_cells=sum(len(v) for v in regions.values()),
    alternative_cell_rows=len(exact),unique_mountain_contact_cells=len({(r["mask_x"],r["mask_y"]) for r in exact}),
    gap_cells_in_atlas=len(unknown),production_changed=False,exclusion_zones_added=0,
    alternatives=choices,output_hashes={p.name:sha(p) for p in OUT.glob("*.csv")})
(OUT/"proposal-summary.json").write_text(json.dumps(summary,indent=2),encoding="utf-8")
print(json.dumps({k:v for k,v in summary.items() if k not in ("resource_hashes","alternatives","output_hashes")},indent=2))
