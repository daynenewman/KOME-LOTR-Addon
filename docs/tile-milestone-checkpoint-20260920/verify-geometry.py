"""Read-only replay of accepted geometry manifests. No proposal generation or installation."""
from pathlib import Path
import csv
import hashlib
import json
import re
import sys
import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
sys.dont_write_bytecode = True
sys.path.insert(0, str(ROOT / "docs/tile-gap-audit-20260919"))
from audit import components


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def rows(path):
    with path.open(encoding="utf-8-sig", newline="") as stream:
        return list(csv.DictReader(stream))


def load(path):
    image = np.array(Image.open(path).convert("RGBA"))
    assert image.shape == (4000, 3200, 4)
    return image


mapping = ROOT / "src/main/resources/assets/kome/map/reset_conquest_tile_ids.txt"
palette = {}
for line in mapping.read_text().splitlines():
    if line.strip() and not line.startswith("#"):
        color, tile = line.split("=")
        palette[tile] = tuple(map(int, color.split(","))) + (255,)
retired = set(re.findall(r'RETIRED_TILE_IDS.add\("(T\d+)"\)',
    (ROOT / "src/main/java/kome/common/data/KOMEConquestTileDefaults.java").read_text()))


def labels(rgba):
    rgb = (rgba[:, :, 0].astype(np.int32) << 16) | (rgba[:, :, 1].astype(np.int32) << 8) | rgba[:, :, 2]
    pairs = sorted(((v[0] << 16) | (v[1] << 8) | v[2], 0 if k in retired else int(k[1:]))
                   for k, v in palette.items())
    keys = np.array([k for k, v in pairs]); values = np.array([v for k, v in pairs], dtype=np.int16)
    active = rgba[:, :, 3] > 24
    assert not (set(np.unique(rgb[active])) - set(keys))
    result = np.zeros(rgb.shape, dtype=np.int16)
    result[active] = values[np.searchsorted(keys, rgb[active])]
    return result


before = ROOT / "src/test/resources/kome/tile/weathertop-pilot/before.png"
assert sha(before) == "3ee80b95947f99ca4bdc355c0acf3dc832d41e964b5fe54e880f35665068e6d1"
current = load(before)
pilot = rows(ROOT / "docs/weathertop-seam-candidate-20260919/edit-manifest.csv")
v2 = rows(ROOT / "docs/land-seam-candidate-v2-20260919/edit-manifest-buffer0.csv")
final_path = ROOT / "docs/final-combined-geography-20260920/edit-manifest.csv"
assert sha(final_path) == "72a21e60160994b9054e37eb3db4ce7d30ecb55152c7c0c61d9a365779dfa6f4"
combined = rows(final_path)
assert (len(pilot), len(v2), len(combined)) == (95, 93103, 4915)


def apply(edits, separator, target):
    seen = set()
    for row in edits:
        x, y = int(row["mask_x"]), int(row["mask_y"])
        assert 0 <= x < 3200 and 0 <= y < 4000 and (x, y) not in seen
        seen.add((x, y))
        assert tuple(current[y, x]) == tuple(map(int, row["previous_rgba"].split(separator)))
        tile = row[target]
        assert tile in palette and tile not in retired
        assert int(row["world_x_min"]) == (x - 810) * 128
        assert int(row["world_z_min"]) == (y - 730) * 128
        current[y, x] = palette[tile]


apply(pilot, ",", "proposed_tile_id")
pinned = ROOT / "src/test/resources/kome/tile/gameplay-baseline/mask.png"
assert sha(pinned) == "a5cd6cf91b3fc1b662cffffde36a250a6e6687bcb8b2b7cd9aa06809b944b866"
assert np.array_equal(current, load(pinned))
pre_v2 = labels(current)
apply(v2, "/", "proposed_tile_id")
baseline = labels(current)
inputs = [(ROOT / "docs/v2-geography-review-20260920/edit-manifest.csv", 4003,
           "d41787a87c5da0e6844eac3beeba21f2e71fd0a103db147a962f642f0ded9bcb"),
          (ROOT / "docs/tile-cleanup-geography-20260920/edit-manifest.csv", 912,
           "ae2ec37230ba10bb735bc45feb2680c54592906117fd7ecb408b7208c068551f")]
coords = set()
for path, count, digest in inputs:
    assert sha(path) == digest
    data = rows(path); assert len(data) == count
    for row in data:
        coord = (int(row["mask_x"]), int(row["mask_y"]))
        assert coord not in coords; coords.add(coord)
assert coords == {(int(r["mask_x"]), int(r["mask_y"])) for r in combined}
apply(combined, "/", "to_tile")
installed = ROOT / "src/main/resources/assets/kome/map/reset_conquest_tile_ids.png"
assert sha(installed) == "ab792277f61882d415963bf5af1b8d2705458c68de80f5b3cbb9102e30d1b4a7"
assert np.array_equal(current, load(installed)), "Unmanifested geometry change"
final = labels(current)
assert np.array_equal(final[pre_v2 > 0], pre_v2[pre_v2 > 0])
assert [int(final[y, x]) for x, y in [(2291, 58), (2292, 58), (1083, 735), (974, 727)]] == [0, 1, 0, 149]
before_grid, before_stats = components(baseline)
after_grid, after_stats = components(final)
assert len(np.unique(final)) - 1 == 621
assert sum(v["tile"] != "GAP" for v in before_stats.values()) == 622
assert sum(v["tile"] != "GAP" for v in after_stats.values()) == 622
targets = set()
for key, stat in before_stats.items():
    if stat["tile"] == "GAP":
        continue
    box = np.s_[stat["min_y"]:stat["max_y"] + 1, stat["min_x"]:stat["max_x"] + 1]
    found = np.unique(after_grid[box][(before_grid[box] == key) & (pre_v2[box] > 0)])
    assert len(found) == 1 and int(found[0]) not in targets
    targets.add(int(found[0]))
assert targets == {k for k, v in after_stats.items() if v["tile"] != "GAP"}
print(json.dumps(dict(pilot=95, v2=93103, combined=4915, ids=621, components=622,
    component_bijection=True, exact_manifest_replay=True, protected_controls=True), indent=2))
