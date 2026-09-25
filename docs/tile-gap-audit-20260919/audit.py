"""Read-only real-resource gap audit. Requires Python, Pillow, NumPy; no downloads.
Run from repository root: python docs/tile-gap-audit-20260919/audit.py
Writes only beside this script. Four-neighbor components and axis-bounded seam
probes are measurements, not tile adjacency or geography decisions.
"""
from pathlib import Path
import io,zipfile,json,re,csv,hashlib,collections
import numpy as np
from PIL import Image,ImageDraw,ImageFont
OUT=Path(__file__).resolve().parent
ROOT=OUT.parent.parent
MASK=ROOT/'src/main/resources/assets/kome/map/reset_conquest_tile_ids.png'
MAPPING=ROOT/'src/main/resources/assets/kome/map/reset_conquest_tile_ids.txt'
JAR=ROOT/'libs/LOTRMod v36.15.jar'
def sha(b): return hashlib.sha256(b).hexdigest()
def world(x,y): return ((int(x)-810)*128,(int(y)-730)*128)
def writecsv(name,rows):
    if rows:
        with (OUT/name).open('w',newline='',encoding='utf-8') as f:
            w=csv.DictWriter(f,fieldnames=list(dict.fromkeys(k for row in rows for k in row)));w.writeheader();w.writerows(rows)
def load():
    # The checked transform and extracted biome legend belong to this exact bundled LOTR version.
    assert sha(JAR.read_bytes())=='4f296e749c0d4739ecf859217a526b4218a2a45a768c08d3d551af0d0d3d5635', 'Reverify LOTR transform and legend after a JAR change'
    mapping={}; seen=set()
    for line in MAPPING.read_text().splitlines():
        if not line.strip() or line.startswith('#'):continue
        rgb,tid=line.split('='); channels=tuple(map(int,rgb.split(',')))
        assert len(channels)==3 and all(0<=v<=255 for v in channels)
        assert re.fullmatch(r'T\d{3}',tid) and tid not in seen
        color=(channels[0]<<16)|(channels[1]<<8)|channels[2]
        assert color not in mapping
        mapping[color]=tid;seen.add(tid)
    src=(ROOT/'src/main/java/kome/common/data/KOMEConquestTileDefaults.java').read_text()
    retired=set(re.findall(r'RETIRED_TILE_IDS.add\("(T\d+)"\)',src))
    rgba=np.array(Image.open(MASK).convert('RGBA')); h,w=rgba.shape[:2]
    rgb=(rgba[:,:,0].astype(np.int32)<<16)|(rgba[:,:,1].astype(np.int32)<<8)|rgba[:,:,2]
    opaque=rgba[:,:,3]>24; unknown=set(np.unique(rgb[opaque]))-mapping.keys();assert not unknown,unknown
    ids=np.zeros((h,w),dtype=np.int16)
    # Palette lookup avoids a repeated whole-raster scan per tile.
    colors=np.array(sorted(mapping),dtype=np.int32); values=np.array([0 if mapping[c] in retired else int(mapping[c][1:]) for c in colors],dtype=np.int16)
    ids[opaque]=values[np.searchsorted(colors,rgb[opaque])]
    with zipfile.ZipFile(JAR) as z: b=z.read('assets/lotr/map/map.png')
    base=Image.open(io.BytesIO(b)).convert('RGB');assert base.size==(w,h)==(3200,4000)
    assert ids[58,2291]==0 and ids[58,2292]==1 and world(2291,58)==(189568,-86016)
    assert world(810,730)==(0,0) and world(0,0)==(-103680,-93440)
    assert world(w,h)==(305920,418560)
    return ids,base,dict(mask_sha256=sha(MASK.read_bytes()),mapping_sha256=sha(MAPPING.read_bytes()),background_sha256=sha(b),jar_sha256=sha(JAR.read_bytes()),width=w,height=h,mapped_ids=len(mapping),active_ids=len(seen-retired),retired_exclusions=len(retired),transparent_cells=int((~opaque).sum()),retired_cells=int((opaque & (ids==0)).sum()),unknown_colors=0,missing_coverage=sorted(seen-retired-{f'T{x:03}' for x in np.unique(ids) if x}))
def components(ids):
    # Scanline run-length union-find; equal labels overlap vertically, never diagonally.
    parent=[];runs=[];prev=[]
    def find(a):
        while parent[a]!=a:parent[a]=parent[parent[a]];a=parent[a]
        return a
    for y,row in enumerate(ids):
        starts=np.r_[0,np.flatnonzero(row[1:]!=row[:-1])+1]; ends=np.r_[starts[1:],len(row)]
        curr=[];j=0
        for a,b in zip(starts.tolist(),ends.tolist()):
            k=len(parent);parent.append(k);v=int(row[a]);curr.append((a,b,v,k));runs.append((y,a,b,v))
            while j<len(prev) and prev[j][1]<=a:j+=1
            q=j
            while q<len(prev) and prev[q][0]<b:
                if prev[q][2]==v:
                    ra,rb=find(k),find(prev[q][3]);parent[ra]=rb
                q+=1
        prev=curr
    stats={};rootgrid=np.zeros(ids.shape,dtype=np.int32)
    for k,(y,a,b,v) in enumerate(runs):
        root=find(k);rootgrid[y,a:b]=root
        if root not in stats:stats[root]=dict(component=root,tile='GAP' if v==0 else f'T{v:03}',cells=0,min_x=a,min_y=y,max_x=b-1,max_y=y,first_x=a,first_y=y)
        s=stats[root];s['cells']+=b-a;s['min_x']=min(s['min_x'],a);s['max_x']=max(s['max_x'],b-1);s['max_y']=y
    for s in stats.values():
        s['touches_edge']=s['min_x']==0 or s['min_y']==0 or s['max_x']==ids.shape[1]-1 or s['max_y']==ids.shape[0]-1
        s['world_x'],s['world_z']=world(s['first_x'],s['first_y'])
    return rootgrid,stats

def seams(ids,rootgrid,base):
    flags=np.zeros(ids.shape,dtype=np.uint8);segments=[];b=np.array(base)
    for axis,arr in [('H',ids),('V',ids.T)]:
        for n,row in enumerate(arr):
            zero=row==0;changes=np.diff(np.r_[False,zero,False].astype(np.int8));starts=np.flatnonzero(changes==1);ends=np.flatnonzero(changes==-1)
            for a,e in zip(starts.tolist(),ends.tolist()):
                if a==0 or e==len(row) or e-a>8 or row[a-1]==row[e]:continue
                x,y=(a,n) if axis=='H' else (n,a);cx,cy=((a+e-1)//2,n) if axis=='H' else (n,(a+e-1)//2)
                if axis=='H':flags[n,a:e]=1
                else:flags[a:e,n]=1
                color=b[cy,cx].tolist()
                segments.append(dict(axis=axis,x=x,y=y,width_cells=e-a,width_blocks=(e-a)*128,tile_a=f'T{row[a-1]:03}',tile_b=f'T{row[e]:03}',component=int(rootgrid[y,x]),sample_x=cx,sample_y=cy,world_x=world(cx,cy)[0],world_z=world(cx,cy)[1],base_rgb='/'.join(map(str,color))))
    return flags,segments

def font(size):return ImageFont.truetype('C:/Windows/Fonts/consola.ttf',size)
def layer_images(ids,rootgrid,stats,flags):
    h,w=ids.shape
    # Neutral coverage, categorical transparency, and no invented faction colors.
    cover=np.zeros((h,w,4),dtype=np.uint8);cover[ids>0]=[240,237,205,60]
    gaps=np.zeros((h,w,4),dtype=np.uint8);gaps[ids==0]=[177,103,247,55]
    enclosed_roots=[k for k,s in stats.items() if s['tile']=='GAP' and not s['touches_edge']]
    enclosed=np.isin(rootgrid,enclosed_roots);gaps[enclosed]=[255,143,36,90];gaps[flags>0]=[255,40,161,120]
    return Image.fromarray(cover),Image.fromarray(gaps),enclosed

def main():
    ids,base,summary=load();rootgrid,stats=components(ids);flags,segments=seams(ids,rootgrid,base)
    gapstats=[s for s in stats.values() if s['tile']=='GAP'];tilecount=collections.Counter(s['tile'] for s in stats.values() if s['tile']!='GAP')
    cover,gaps,enclosed=layer_images(ids,rootgrid,stats,flags)
    summary.update(assigned_cells=int((ids>0).sum()),gap_cells=int((ids==0).sum()),gap_components=len(gapstats),enclosed_components=sum(not s['touches_edge'] for s in gapstats),enclosed_cells=int(enclosed.sum()),exterior_connected_cells=sum(s['cells'] for s in gapstats if s['touches_edge']),narrow_probe_unique_cells=int(flags.sum()),narrow_probe_exterior_cells=int(((flags>0)&~enclosed).sum()),narrow_probe_segments=len(segments),disconnected_tiles={k:v for k,v in tilecount.items() if v>1})
    active_totals=collections.Counter()
    for s in stats.values():
        if s['tile']!='GAP':active_totals[s['tile']]+=s['cells']
    summary['one_cell_tiles']=sum(v==1 for v in active_totals.values())
    summary['large_enclosed_components_100plus_cells']=sum(s['cells']>=100 and not s['touches_edge'] for s in gapstats)
    summary['largest_enclosed']=max((s for s in gapstats if not s['touches_edge']),key=lambda s:s['cells'])
    assert summary['gap_components']==791 and summary['enclosed_components']==790
    assert summary['one_cell_tiles']==46 and summary['disconnected_tiles']=={'T423':2}
    assert summary['assigned_cells']==3973869 and summary['retired_cells']==44
    # Compare independently reconstructed component geometry with historical Java output.
    historical=ROOT/'build/reports/tile-resource-audit/components.csv'
    if historical.exists():
        old=list(csv.DictReader(historical.open()))
        norm=lambda s:(s['tile'],int(s['cells']),int(s['min_x']),int(s['min_y']),int(s['max_x']),int(s['max_y']))
        assert sorted(map(norm,old))==sorted(map(norm,stats.values()))
        summary['historical_components_match']=True
    writecsv('components.csv',sorted(stats.values(),key=lambda s:(s['first_y'],s['first_x'])))
    writecsv('narrow-probes.csv',segments)
    (OUT/'summary.json').write_text(json.dumps(summary,indent=2))
    base.save(OUT/'background.png');cover.save(OUT/'coverage.png');gaps.save(OUT/'gaps.png')
    np.savez_compressed(OUT/'analysis.npz',ids=ids,components=rootgrid,narrow=flags)
    composite=Image.alpha_composite(Image.alpha_composite(base.convert('RGBA'),cover),gaps)
    overview=Image.new('RGB',(1640,2180),'#171c24');d=ImageDraw.Draw(overview)
    d.text((20,12),'KOME TILE GAP AUDIT | real mask + LOTR base map',font=font(27),fill='white')
    d.text((20,52),'1 mask cell = 128 x 128 blocks | north up | +X east, +Z south',font=font(21),fill='#dedede')
    d.text((20,84),'Pale = assigned | purple = exterior-connected gap | orange = enclosed gap',font=font(19),fill='#dedede')
    d.text((20,112),'Pink = <=8-cell straight gap between DIFFERENT IDs; candidate, not a river rule',font=font(18),fill='#ff8cc5')
    overview.paste(composite.resize((1600,2000),Image.Resampling.NEAREST).convert('RGB'),(20,148));overview.save(OUT/'overview.png')
    # Exploratory contact sheet selects spatially separated probes by underlying map color only.
    blue=[s for s in segments if (lambda c:c[2]>c[0]+25 and c[2]>c[1])(list(map(int,s['base_rgb'].split('/'))))]
    land=[s for s in segments if (lambda c:c[1]>c[2]+20 and c[0]>40)(list(map(int,s['base_rgb'].split('/'))))]
    picks=[]
    for kind,pool in [('possible_water',blue),('land_color',land)]:
        for anchor in [(1000,730),(1400,1000),(1200,1500),(2000,1800),(1000,2200),(2100,2800)]:
            choices=sorted(pool,key=lambda s:(s['sample_x']-anchor[0])**2+(s['sample_y']-anchor[1])**2)
            if choices:
                s=choices[0].copy();s['candidate']=kind;picks.append(s)
    sheet=Image.new('RGB',(1800,1450),'#171c24');draw=ImageDraw.Draw(sheet)
    for i,s in enumerate(picks):
        x,y=s['sample_x'],s['sample_y'];bounds=(x-40,y-40,x+40,y+40);ox=(i%4)*450;oy=(i//4)*480
        panel=Image.alpha_composite(base.crop(bounds).convert('RGBA'),gaps.crop(bounds)).resize((400,400),Image.Resampling.NEAREST)
        sheet.paste(panel.convert('RGB'),(ox+20,oy+55));draw.text((ox+12,oy+4),f'{i+1}: {x},{y} {s["candidate"]}',font=font(16),fill='white');draw.text((ox+12,oy+26),s['tile_a']+' / '+s['tile_b']+' width='+str(s['width_cells']),font=font(16),fill='white')
        draw.rectangle((ox+218,oy+253,ox+222,oy+257),outline='white',width=2)
    sheet.save(OUT/'candidate-contact.png');(OUT/'candidate-picks.json').write_text(json.dumps(picks,indent=2))
    print(json.dumps(summary,indent=2))
if __name__=='__main__':main()