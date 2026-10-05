"""Read-only KOM-80 geometry audit. Requires existing Python, NumPy, Pillow and stock LOTR JAR.
Writes this report directory only; stock map appearance is context, never geographic authority.
"""
from pathlib import Path
import collections, csv, hashlib, io, json, re, subprocess, zipfile
import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
OUT = Path(__file__).resolve().parent
BASE = '41f145773601ff84f754421ee7534d7265d42f74'
MASK_REL = 'src/main/resources/assets/kome/map/reset_conquest_tile_ids.png'
MASK = ROOT / MASK_REL
CONFIG = ROOT / 'src/main/resources/assets/kome/config'
PAIRS = [(435,654),(455,654),(400,420),(329,355),(352,356),(218,220)]

def sha(data): return hashlib.sha256(data).hexdigest()
def tid(value): return 'T%03d' % value if value else 'GAP'
def rows(path):
    return list(csv.DictReader(line for line in path.read_text(encoding='utf-8-sig').splitlines() if not line.startswith('#')))
def writecsv(name, data):
    with (OUT/name).open('w',encoding='utf-8',newline='') as stream:
        writer=csv.DictWriter(stream,fieldnames=list(data[0])); writer.writeheader(); writer.writerows(data)

jar=(ROOT/'libs/LOTRMod v36.15.jar').read_bytes()
assert sha(jar)=='4f296e749c0d4739ecf859217a526b4218a2a45a768c08d3d551af0d0d3d5635'
with zipfile.ZipFile(io.BytesIO(jar)) as archive: background_bytes=archive.read('assets/lotr/map/map.png')
background=np.array(Image.open(io.BytesIO(background_bytes)).convert('RGB'))
mapping={}
mapping_path=ROOT/'src/main/resources/assets/kome/map/reset_conquest_tile_ids.txt'
for line in mapping_path.read_text().splitlines():
    if line and not line.startswith('#'):
        rgb,tile=line.split('='); r,g,b=map(int,rgb.split(',')); mapping[(r<<16)|(g<<8)|b]=int(tile[1:])
retired=set(map(int,re.findall(r'RETIRED_TILE_IDS.add\("T(\d+)"\)',(ROOT/'src/main/java/kome/common/data/KOMEConquestTileDefaults.java').read_text())))
colors=np.array(sorted(mapping)); values=np.array([0 if mapping[c] in retired else mapping[c] for c in colors],dtype=np.int16)
def labels(rgba):
    assert rgba.shape==(4000,3200,4)
    rgb=(rgba[:,:,0].astype(np.int32)<<16)|(rgba[:,:,1].astype(np.int32)<<8)|rgba[:,:,2]
    active=rgba[:,:,3]>24; assert not (set(np.unique(rgb[active]))-set(mapping))
    result=np.zeros(rgb.shape,dtype=np.int16); result[active]=values[np.searchsorted(colors,rgb[active])]; return result
mask_bytes=MASK.read_bytes(); rgba=np.array(Image.open(io.BytesIO(mask_bytes)).convert('RGBA')); ids=labels(rgba)
historical_path=ROOT/'src/test/resources/kome/tile/gameplay-baseline/mask.png'
historical=labels(np.array(Image.open(historical_path).convert('RGBA')))
base_bytes=subprocess.check_output(['git','show',BASE+':'+MASK_REL],cwd=ROOT)
base_rgba=np.array(Image.open(io.BytesIO(base_bytes)).convert('RGBA'))
changed=np.any(base_rgba!=rgba,axis=2); ys,xs=np.nonzero(changed)
changed_cells=[dict(x=int(x),y=int(y),before_rgba=base_rgba[y,x].tolist(),after_rgba=rgba[y,x].tolist()) for y,x in zip(ys,xs)]
assert not changed_cells, 'Raster changed: separately review exact cells and gameplay effects'
(OUT/'changed-cells.json').write_text(json.dumps(changed_cells)+'\n',encoding='utf-8')
routes={tuple(sorted((int(r['from'][1:]),int(r['to'][1:])))):r['type'] for r in rows(CONFIG/'kome_tile_route_defaults.csv')}
ownership={int(r['tile_id'][1:]):r for r in rows(CONFIG/'kome_tile_ownership_defaults.csv')}
names={key:r['reason'].removeprefix('Waypoint default: ') if r['source']=='WAYPOINT_DEFAULT' else tid(key) for key,r in ownership.items()}
phases={}
manifest_paths=[('pilot',ROOT/'docs/weathertop-seam-candidate-20260919/edit-manifest.csv'),('v2',ROOT/'docs/land-seam-candidate-v2-20260919/edit-manifest-buffer0.csv'),('final',ROOT/'docs/final-combined-geography-20260920/edit-manifest.csv')]
for phase,path in manifest_paths:
    for row in rows(path): phases[(int(row['mask_x']),int(row['mask_y']))]=phase

def contacts(grid):
    result=collections.defaultdict(list)
    # Each unordered physical cell pair emitted once. Corner count is not edge count.
    for dx,dy,kind in [(1,0,'edge'),(0,1,'edge'),(1,1,'corner'),(-1,1,'corner')]:
        x0,x1=max(0,-dx),min(3200,3200-dx); y1=4000-dy
        first=grid[:y1,x0:x1]; second=grid[dy:y1+dy,x0+dx:x1+dx]
        ys,xs=np.nonzero((first>0)&(second>0)&(first!=second))
        for y,x in zip(ys,xs):
            xx,yy=int(x)+x0,int(y); a,b=int(grid[yy,xx]),int(grid[yy+dy,xx+dx])
            result[tuple(sorted((a,b)))].append((kind,xx,yy,xx+dx,yy+dy,a,b))
    return result
current_contacts=contacts(ids); old_contacts=contacts(historical)
requested=[]; pair_summaries=[]; (OUT/'contacts').mkdir(exist_ok=True)
for a,b in PAIRS:
    records=current_contacts.get((a,b),[]); old=old_contacts.get((a,b),[])
    for kind,x,y,nx,ny,first,second in records:
        requested.append(dict(pair=tid(a)+'-'+tid(b),kind=kind,first_tile=tid(first),first_x=x,first_y=y,second_tile=tid(second),second_x=nx,second_y=ny,first_historical=tid(historical[y,x]),second_historical=tid(historical[ny,nx]),first_edit_phase=phases.get((x,y),'unchanged'),second_edit_phase=phases.get((nx,ny),'unchanged'),first_world_x_min=(x-810)*128,first_world_z_min=(y-730)*128,second_world_x_min=(nx-810)*128,second_world_z_min=(ny-730)*128))
    counts=collections.Counter(r[0] for r in records); old_counts=collections.Counter(r[0] for r in old)
    cells={(x,y) for _,x,y,_,_,_,_ in records}|{(x,y) for _,_,_,x,y,_,_ in records}
    changed_old=[(x,y) for x,y in cells if ids[y,x]!=historical[y,x]]
    item=dict(pair=tid(a)+'-'+tid(b),first_name=names[a],second_name=names[b],shared_grid_edges=counts['edge'],diagonal_cell_pairs=counts['corner'],historical_shared_grid_edges=old_counts['edge'],historical_diagonal_cell_pairs=old_counts['corner'],unique_contact_cells=len(cells),contact_cells_changed_since_gameplay_baseline=len(changed_old),changed_contact_cell_phases=dict(collections.Counter(phases.get(c,'unmanifested') for c in changed_old)),packaged_route_type=routes.get((a,b),'ABSENT'))
    crop_cells=cells
    if not cells:
        # Exact closest boundary-cell centers, including every tied pair; no terrain inference.
        boundaries=[]
        for tile in (a,b):
            selected=ids==tile; interior=selected.copy()
            interior[1:,:] &= selected[:-1,:]; interior[:-1,:] &= selected[1:,:]
            interior[:,1:] &= selected[:,:-1]; interior[:,:-1] &= selected[:,1:]
            yy,xx=np.nonzero(selected & ~interior)
            boundaries.append(np.column_stack((xx,yy)))
        delta=boundaries[0][:,None,:]-boundaries[1][None,:,:]
        distances=np.sum(delta.astype(np.int64)**2,axis=2); minimum=int(distances.min())
        aa,bb=np.nonzero(distances==minimum)
        closest=[dict(first_cell=boundaries[0][i].tolist(),second_cell=boundaries[1][j].tolist()) for i,j in zip(aa,bb)]
        item['minimum_cell_center_distance_squared']=minimum
        item['nearest_boundary_cell_pairs']=closest
        crop_cells={tuple(c['first_cell']) for c in closest}|{tuple(c['second_cell']) for c in closest}
    if crop_cells:
        xs,ys=zip(*crop_cells)
        if cells: item['contact_bbox_inclusive']=[min(xs),min(ys),max(xs),max(ys)]
        left,top,right,bottom=max(0,min(xs)-45),max(0,min(ys)-45),min(3200,max(xs)+46),min(4000,max(ys)+46)
        item['crop_bbox_half_open']=[left,top,right,bottom]
        crop=background[top:bottom,left:right].copy(); local=ids[top:bottom,left:right]
        for tile,tint in [(a,[50,150,245]),(b,[250,160,40])]:
            selected=local==tile; crop[selected]=(crop[selected]*.75+np.array(tint)*.25).astype(np.uint8)
        scale=3; image=Image.fromarray(crop).resize(((right-left)*scale,(bottom-top)*scale),Image.Resampling.NEAREST); draw=ImageDraw.Draw(image)
        # Source-matched canonical grid edges; no straight-line diagonal shortcut.
        for dy,dx in [(0,1),(1,0)]:
            first=local[:local.shape[0]-dy,:local.shape[1]-dx]; second=local[dy:,dx:]; ey,ex=np.nonzero(first!=second)
            for y,x in zip(ey,ex):
                x,y=int(x),int(y)
                line=((x+1)*scale,y*scale,(x+1)*scale,(y+1)*scale) if dx else (x*scale,(y+1)*scale,(x+1)*scale,(y+1)*scale)
                draw.line(line,fill=(50,35,20),width=1)
        for kind,x,y,nx,ny,_,_ in records:
            if kind=='edge':
                line=((max(x,nx)-left)*scale,(y-top)*scale,(max(x,nx)-left)*scale,(y-top+1)*scale) if nx!=x else ((x-left)*scale,(max(y,ny)-top)*scale,(x-left+1)*scale,(max(y,ny)-top)*scale)
                draw.line(line,fill=(255,0,90),width=3)
        if not cells:
            for x,y in crop_cells:
                px,py=(x-left)*scale,(y-top)*scale
                draw.rectangle((px-3,py-3,px+scale+3,py+scale+3),outline='red',width=2)
        unlabeled_image=image.copy()
        for tile in sorted(set(map(int,np.unique(local)))-{0}):
            yy,xx=np.nonzero(local==tile)
            if len(xx)>=15: draw.text((int(np.median(xx))*scale,int(np.median(yy))*scale),tid(tile),fill='white',stroke_width=2,stroke_fill='black',anchor='mm')
        framed=Image.new('RGB',(max(660,image.width+320),max(450,image.height+64)),'white'); framed.paste(image,(0,64)); caption=ImageDraw.Draw(framed)
        caption.text((8,5),names[a]+' '+tid(a)+' / '+names[b]+' '+tid(b),fill='black')
        caption.text((8,23),'Mask [%d,%d)-[%d,%d); edges=%d; corner pairs=%d'%(left,top,right,bottom,counts['edge'],counts['corner']),fill='black')
        caption.text((8,41),('Magenta=shared edges.' if cells else 'Red boxes=nearest cells, no contact.')+' Stock map is context only. 3x.',fill='black')
        panel=image.width+12
        caption.text((panel,80),'Packaged direct route: ABSENT',fill='black')
        caption.text((panel,100),'Before V2: no edge/corner contact',fill='black')
        if cells:
            caption.text((panel,130),'Exact contact inset (12x):',fill='black')
            x0,y0,x1,y1=item['contact_bbox_inclusive']
            inset=unlabeled_image.crop(((x0-left-3)*scale,(y0-top-3)*scale,(x1-left+4)*scale,(y1-top+4)*scale))
            inset=inset.resize((inset.width*4,inset.height*4),Image.Resampling.NEAREST)
            framed.paste(inset,(panel,150))
        else:
            caption.text((panel,130),'Nearest cell centers:',fill='black')
            caption.text((panel,150),str(item['nearest_boundary_cell_pairs'][0]['first_cell'])+' / '+str(item['nearest_boundary_cell_pairs'][0]['second_cell']),fill='black')
            caption.text((panel,170),'Squared distance: '+str(minimum)+' map cells^2',fill='black')
            caption.text((panel,200),'T444 lies between the named tiles.',fill='black')
        framed.save(OUT/'contacts'/(item['pair']+'.png'))
    pair_summaries.append(item)
writecsv('requested-contact-cells.csv',requested)
# Bounded nearby audit: seed tiles plus one whole geometric/strategic neighbor ring.
seeds={v for pair in PAIRS for v in pair}; near=set(seeds)
for a,b in set(current_contacts)|set(routes):
    if a in seeds or b in seeds: near.update((a,b))
nearby=[]
nearby_ambiguous_cells=[]
for a,b in sorted(set(current_contacts)|set(routes)):
    if a not in near or b not in near: continue
    counts=collections.Counter(r[0] for r in current_contacts.get((a,b),[])); old=collections.Counter(r[0] for r in old_contacts.get((a,b),[]))
    records=current_contacts.get((a,b),[])
    cells={(x,y) for _,x,y,_,_,_,_ in records}|{(x,y) for _,_,_,x,y,_,_ in records}
    bbox=''
    if cells:
        xx,yy=zip(*cells); bbox='%d;%d;%d;%d'%(min(xx),min(yy),max(xx),max(yy))
    nearby.append(dict(first=tid(a),first_name=names[a],second=tid(b),second_name=names[b],seed_endpoint=a in seeds or b in seeds,shared_grid_edges=counts['edge'],diagonal_cell_pairs=counts['corner'],historical_shared_grid_edges=old['edge'],historical_diagonal_cell_pairs=old['corner'],contact_bbox_inclusive=bbox,packaged_route_type=routes.get((a,b),'ABSENT'),disposition='existing explicit gameplay edge' if (a,b) in routes else 'geometric contact only; no default gameplay edge'))
    if (a,b) not in routes:
        for x,y in sorted(cells,key=lambda c:(c[1],c[0])):
            nearby_ambiguous_cells.append(dict(pair=tid(a)+'-'+tid(b),x=x,y=y,tile=tid(ids[y,x]),historical_tile=tid(historical[y,x]),last_edit_phase=phases.get((x,y),'unchanged'),world_x_min=(x-810)*128,world_z_min=(y-730)*128))
writecsv('nearby-contacts.csv',nearby)
writecsv('nearby-geometry-only-cells.csv',nearby_ambiguous_cells)
exclusion_path=ROOT/'src/main/resources/assets/kome/map/tile_exclusions.tsv'
exclusions=[line for line in exclusion_path.read_text().splitlines() if line.startswith('zone\t')]
summary=dict(base_commit=BASE,mask_sha256=sha(mask_bytes),base_mask_sha256=sha(base_bytes),mapping_sha256=sha(mapping_path.read_bytes()),historical_mask_sha256=sha(historical_path.read_bytes()),exclusions_sha256=sha(exclusion_path.read_bytes()),stock_jar_sha256=sha(jar),stock_map_sha256=sha(background_bytes),manifest_sha256={phase:sha(path.read_bytes()) for phase,path in manifest_paths},full_raster_cells_compared=int(changed.size),exact_changed_cells=changed_cells,assigned_cells=int((ids>0).sum()),unclassified_gap_cells=int((ids==0).sum()),active_tile_ids=len(np.unique(ids))-1,explicit_exclusion_zones=len(exclusions),requested_pairs=pair_summaries,nearby_scope='Both endpoints in the union of the six pairs and their direct geometric or strategic neighbors',nearby_tile_ids=[tid(v) for v in sorted(near)],nearby_pairs=len(nearby),nearby_geometry_only_pairs=sum(r['packaged_route_type']=='ABSENT' for r in nearby),nearby_routes_by_type=dict(collections.Counter(r['packaged_route_type'] for r in nearby)),render_evidence='Source-matched grid-edge reconstruction; actual client/GL rendering remains a live check',geographic_ambiguity='No approved mountain exclusion zones or cell-removal manifest exists; contact and stock map appearance alone do not authorize deleting tile cells or blocking existing strategic routes')
(OUT/'geometry-summary.json').write_text(json.dumps(summary,indent=2)+'\n',encoding='utf-8')
print(json.dumps(summary,indent=2))
