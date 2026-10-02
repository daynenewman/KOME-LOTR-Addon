"""Reproduce a read-only coordinate atlas from committed concerns and current canonical mask.
Requires the existing Python/Pillow/NumPy tools and ignored stock LOTR JAR; writes only this report folder.
No biome inference, proposal generation, resource modification or world access.
"""
from pathlib import Path
import csv, json, hashlib, re, zipfile, io, collections
import numpy as np
from PIL import Image, ImageDraw
ROOT = Path(__file__).resolve().parents[2]
OUT = Path(__file__).resolve().parent
MASK = ROOT / "src/main/resources/assets/kome/map/reset_conquest_tile_ids.png"
MAPPING = ROOT / "src/main/resources/assets/kome/map/reset_conquest_tile_ids.txt"
JAR = ROOT / "libs/LOTRMod v36.15.jar"
def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
assert sha(MASK) == "ab792277f61882d415963bf5af1b8d2705458c68de80f5b3cbb9102e30d1b4a7"
assert sha(JAR) == "4f296e749c0d4739ecf859217a526b4218a2a45a768c08d3d551af0d0d3d5635"
with zipfile.ZipFile(JAR) as z: background_bytes = z.read("assets/lotr/map/map.png")
base = Image.open(io.BytesIO(background_bytes)).convert("RGB")
rgba = np.array(Image.open(MASK).convert("RGBA")); h,w = rgba.shape[:2]
assert (w,h) == base.size == (3200,4000)
mapping = {}
for line in MAPPING.read_text().splitlines():
    if line.strip() and not line.startswith("#"):
        rgb,tid = line.split("="); c = tuple(map(int,rgb.split(",")))
        mapping[(c[0]<<16)|(c[1]<<8)|c[2]] = tid
retired = set(re.findall(r'RETIRED_TILE_IDS.add\("(T\d+)"\)', (ROOT/"src/main/java/kome/common/data/KOMEConquestTileDefaults.java").read_text()))
colors = np.array(sorted(mapping)); values = np.array([0 if mapping[c] in retired else int(mapping[c][1:]) for c in colors],dtype=np.int16)
rgb = (rgba[:,:,0].astype(np.int32)<<16)|(rgba[:,:,1].astype(np.int32)<<8)|rgba[:,:,2]
active = rgba[:,:,3]>24
assert not (set(np.unique(rgb[active])) - set(mapping))
ids = np.zeros((h,w),dtype=np.int16); ids[active] = values[np.searchsorted(colors,rgb[active])]
assert int((ids==0).sum()) == 8732021
assert ids[58,2291] == 0 and ids[58,2292] == 1 and ids[727,974] == 149
with (OUT/"review-inputs.csv").open(encoding="utf-8",newline="") as f: evidence = list(csv.DictReader(f))
cells = {}
for e in evidence:
    x,y = int(e["mask_x"]),int(e["mask_y"]); assert 0<=x<w and 0<=y<h
    c = cells.setdefault((x,y), {"x":x,"y":y,"groups":[],"notes":[]})
    if e["review_group"] not in c["groups"]: c["groups"].append(e["review_group"])
    c["notes"].append(e["review_note"])
regions = collections.defaultdict(list)
for c in cells.values():
    x,y=c["x"],c["y"]; bx,by=x//400*400,y//400*400
    c["region"] = f"{bx}-{by}"
    c["tile"] = f"T{ids[y,x]:03}" if ids[y,x] else "GAP"
    c["world"] = [(x-810)*128,(y-730)*128,(x+1-810)*128,(y+1-730)*128]
    c["disposition"] = "protected control" if c["groups"]==["protected_control"] else "pending explicit design; preserve current cell"
    regions[c["region"]].append(c)
# Thin canonical tile edges + marked concerns over the real packaged LOTR map.
edge = np.zeros((h,w),dtype=bool)
edge[:,:-1] |= ids[:,:-1] != ids[:,1:]; edge[:-1,:] |= ids[:-1,:] != ids[1:,:]
canvas = np.array(base).copy(); canvas[edge] = [25,25,25]
for c in cells.values():
    x,y=c["x"],c["y"]
    canvas[y,x] = [230,40,70] if any(g in c["groups"] for g in ["blocked55","withheld_transfer","optional_shape"]) else [0,190,230]
    if "protected_control" in c["groups"]: canvas[y,x]=[255,210,0]
img=Image.fromarray(canvas); (OUT/"atlas").mkdir(exist_ok=True)
region_records=[]
for key,items in sorted(regions.items(),key=lambda kv:-len(kv[1])):
    bx,by=map(int,key.split("-")); img.crop((bx,by,bx+400,by+400)).save(OUT/"atlas"/f"{key}.png")
    region_records.append({"id":key,"x":bx,"y":by,"cells":len(items),"world":[(bx-810)*128,(by-730)*128,(bx+400-810)*128,(by+400-730)*128]})
img.resize((800,1000),Image.Resampling.NEAREST).save(OUT/"overview.png")
with (OUT/"review-cells.csv").open("w",encoding="utf-8",newline="") as f:
    writer=csv.writer(f);writer.writerow(["mask_x","mask_y","world_x_min","world_z_min","world_x_max_exclusive","world_z_max_exclusive","current_tile","review_groups","disposition","evidence_note"])
    for c in sorted(cells.values(),key=lambda c:(c["y"],c["x"])):writer.writerow([c["x"],c["y"],*c["world"],c["tile"],"|".join(c["groups"]),c["disposition"],"; ".join(c["notes"])])
summary={"mask_sha256":sha(MASK),"mapping_sha256":sha(MAPPING),"jar_sha256":sha(JAR),"background_sha256":hashlib.sha256(background_bytes).hexdigest(),"inputs_sha256":sha(OUT/"review-inputs.csv"),"concern_rows":len(evidence),"unique_review_cells":len(cells),"regions":len(regions),"groups":dict(collections.Counter(e["review_group"] for e in evidence)),"assigned_cells":int((ids>0).sum()),"unknown_gap_cells":int((ids==0).sum()),"classified_exclusions":0,"geometry_changed":False}
(OUT/"atlas-summary.json").write_text(json.dumps(summary,indent=2),encoding="utf-8")
# Inline data lets the atlas work from file:// without a server, fetch or external dependencies.
data=json.dumps({"regions":region_records,"cells":list(cells.values())},separators=(",",":"))
html = r'''<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>KOM-59 coordinate review</title><style>
body{margin:24px auto;max-width:1260px;padding:0 20px;font:16px/1.5 system-ui;color:#232522;background:#f5f3ea}h1{font:600 30px/1.2 Georgia}label{display:inline-block;margin:0 18px 14px 0}select,input,button{font:inherit;padding:5px}main{display:grid;grid-template-columns:minmax(300px,650px) 1fr;gap:28px}canvas{width:100%;max-width:650px;border:1px solid #444;image-rendering:pixelated;background:#ddd}pre{white-space:pre-wrap;font:14px/1.5 monospace}#list{height:250px;overflow:auto;border-top:1px solid #999}#list button{display:block;text-align:left;width:100%;border:0;border-bottom:1px solid #ddd;background:transparent;font:13px/1.5 monospace;cursor:pointer}a{color:#245765}@media(max-width:850px){main{grid-template-columns:1fr}}
</style><h1>KOM-59 / pending coordinate decisions</h1>
<p>Current approved raster, 26,664 review cells. Historical concerns are <b>not confirmed errors or geographic classifications</b>. Preserve current cells pending an explicit decision. No proposal is applied.</p>
<p>Black: canonical tile edges. Cyan: unresolved V2/gap/junction review. Red: withheld/blocked transfer or optional shape. Yellow: protected control. Background: actual bundled LOTR map; its colors supply visual context only.</p>
<label>Region <select id="region"></select></label><label>Mask X <input id="x" type="number" min="0" max="3199" value="974"></label><label>Y <input id="y" type="number" min="0" max="3999" value="727"></label><button id="find">Locate</button>
<main><section><canvas id="map" width="800" height="800" aria-label="Regional raster atlas; click a marked cell"></canvas><p id="bounds"></p><a href="overview.png">Full overview</a>  -  <a href="review-cells.csv">All cells and world coordinates (CSV)</a>  -  <a href="README.md">Contract / acceptance handoff</a></section>
<section><h2 style="font-size:20px">Selected cell</h2><pre id="detail">Select a listed cell or click the atlas.</pre><p>Decision needed: retain; explicitly classify an existing gap with zone/type/reason and exact approved cells; or approve a geometry correction after topology and persisted-reference review. None is inferred here.</p><div id="list"></div><button id="more">Next 200 cells</button></section></main>
<script>const DATA=__DATA__;
const regions=DATA.regions, cells=DATA.cells, byCell=new Map(cells.map(c=>[c.x+','+c.y,c]));
const sel=document.getElementById('region'), cv=document.getElementById('map'), ctx=cv.getContext('2d'), image=new Image();let current,point=null,offset=0;
for(const r of regions){const o=document.createElement('option');o.value=r.id;o.textContent=r.id+' ('+r.cells+' cells)';sel.append(o)}
function paint(){ctx.imageSmoothingEnabled=false;ctx.drawImage(image,0,0,800,800);if(point){const px=(point.x-current.x)*2,py=(point.y-current.y)*2;ctx.strokeStyle='#fff';ctx.lineWidth=2;ctx.strokeRect(px-6,py-6,14,14);ctx.strokeStyle='#000';ctx.strokeRect(px-8,py-8,18,18)}}
image.onload=paint;
function rows(){const items=cells.filter(c=>c.region===current.id), list=document.getElementById('list');list.replaceChildren();for(const c of items.slice(offset,offset+200)){const b=document.createElement('button');b.textContent=c.x+','+c.y+' '+c.tile+' '+c.groups.join(',');b.onclick=()=>show(c.x,c.y);list.append(b)}document.getElementById('more').textContent='Next 200 ('+(offset+1)+' - '+Math.min(offset+200,items.length)+' of '+items.length+')'}
function region(id){current=regions.find(r=>r.id===id);sel.value=id;point=null;offset=0;image.src='atlas/'+id+'.png';document.getElementById('bounds').textContent='Mask ['+current.x+','+(current.x+400)+')  -  ['+current.y+','+(current.y+400)+'); world X/Z bounds: '+current.world.join(', ');rows()}
function show(x,y){const c=byCell.get(x+','+y);document.getElementById('x').value=x;document.getElementById('y').value=y;const id=(Math.floor(x/400)*400)+'-'+(Math.floor(y/400)*400);if(!regions.some(r=>r.id===id)){document.getElementById('detail').textContent='No review input for this region. This does not classify or approve the cell.';return}if(current.id!==id)region(id);point={x,y};if(image.complete)paint();document.getElementById('detail').textContent=c?['Mask '+x+','+y,'Current identity: '+c.tile,'World X ['+c.world[0]+','+c.world[2]+')','World Z ['+c.world[1]+','+c.world[3]+')','Groups: '+c.groups.join(', '),'Disposition: '+c.disposition,'','Evidence:',...c.notes].join(String.fromCharCode(10)):'Mask '+x+','+y+' has no recorded concern. No inferred classification.'}
sel.onchange=()=>region(sel.value);document.getElementById('find').onclick=()=>{const x=Number(document.getElementById('x').value),y=Number(document.getElementById('y').value);if(Number.isInteger(x)&&Number.isInteger(y)&&x>=0&&x<3200&&y>=0&&y<4000)show(x,y)};
cv.onclick=e=>{const b=cv.getBoundingClientRect();show(current.x+Math.min(399,Math.floor((e.clientX-b.left)/b.width*400)),current.y+Math.min(399,Math.floor((e.clientY-b.top)/b.height*400)))};
document.getElementById('more').onclick=()=>{const n=cells.filter(c=>c.region===current.id).length;offset=(offset+200)>=n?0:offset+200;rows()};region(regions[0].id);show(974,727);
</script></html>'''
(OUT/"review.html").write_text(html.replace("__DATA__",data),encoding="utf-8")
print(json.dumps(summary,indent=2))
