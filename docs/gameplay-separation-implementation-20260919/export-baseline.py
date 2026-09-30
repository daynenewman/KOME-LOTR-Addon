"""Manual export of pinned ORIGINAL production outputs, never run by Gradle.
The original inference source and raw capture are kept alongside this script.
Use --output DIR to reproduce tables without replacing packaged resources.
"""
from pathlib import Path
import csv,json,hashlib,argparse
O=Path(__file__).resolve().parent;R=O.parent.parent
p=argparse.ArgumentParser();p.add_argument('--output',type=Path,required=True);args=p.parse_args();args.output.mkdir(parents=True,exist_ok=True)
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
mask=R/'src/test/resources/kome/tile/gameplay-baseline/mask.png'
assert sha(mask)=='a5cd6cf91b3fc1b662cffffde36a250a6e6687bcb8b2b7cd9aa06809b944b866'
assert sha(O/'original-derived.tsv')=='97f31e11e33705df4eb7b4c10c355d3741b042401225c7b00edde78a8fb88efb'
assert sha(O/'original-KOMEConquestTileDefaults.java.txt')=='f77920023fdf14d0fb8690b4d6ef9b3b4cdcb00f097f6cd3ea3f401e9bc1f682'
records=[]
for line in (O/'original-derived.tsv').read_text().splitlines():
 kind,key,value=line.split('\t',2); records.append((kind,key,value))
def fields(v):return dict(p.split('=',1) for p in v.strip(';').split(';'))
centers={key:fields(v) for k,key,v in records if k=='center'}
exceptions={r['tile']:r['baseline_center_in_baseline'] for r in csv.DictReader((R/'docs/tile-gameplay-separation-20260919/legacy-center-exceptions.tsv').open(),delimiter='\t')}
outputs={}
def write(name,header,rows):
 target=args.output/name
 with target.open('w',newline='',encoding='utf-8') as f:
  f.write('# KOME gameplay defaults 1\n');w=csv.writer(f,lineterminator='\n');w.writerow(header);w.writerows(rows)
 outputs[name]={'rows':len(rows),'sha256':sha(target)}
write('kome_tile_route_defaults.csv',['from','to','type'],[key.split('/')+[v.split(';')[0]] for k,key,v in records if k=='route'])
write('kome_tile_gameplay_points.csv',['tile','routeX','routeZ','arrivalX','arrivalY','arrivalZ','legacyOutside'],[[t,c['x'],c['z'],c['x'],c['y'],c['z'],exceptions.get(t,'')] for t,c in sorted(centers.items())])
markers=[]
for k,key,v in records:
 if k in ('bridgeMarker','riverMarker'):
  f=fields(v);markers.append([f['fromTile'],f['toTile'],'bridge' if k=='bridgeMarker' else 'river',f['x'],f['y'],f['z'],f['imageX'],f['imageY']])
write('kome_tile_route_markers.csv',['from','to','type','x','y','z','mapX','mapY'],markers)
choices={}
waypoints=R/'docs/tile-gameplay-separation-20260919/waypoint-containment.tsv'
for ordinal,w in enumerate(csv.DictReader(waypoints.open(),delimiter='\t')):
 t=w['baseline_tile']
 if not t or w['hidden']=='true':continue
 c=centers[t];dx=float(w['world_x'])-float(c['x']);dz=float(w['world_z'])-float(c['z']);choices.setdefault(t,[]).append((dx*dx+dz*dz,ordinal,w['waypoint']))
rows=[]
for t,cs in sorted(choices.items()):
 for priority,(_,_,key) in enumerate(sorted(cs)):rows.append([t,priority,key])
write('kome_tile_waypoint_candidates.csv',['tile','priority','waypoint'],rows)
provenance={'version':1,'baseline_mask_sha256':sha(mask),'inputs':{str(f.relative_to(R)):sha(f) for f in [mask,O/'original-derived.tsv',O/'original-KOMEConquestTileDefaults.java.txt',waypoints]},'outputs':outputs,'note':'Original production including 95-cell pilot; no V1/V2 defaults. Logical Middle-earth dimension is configured at runtime. Not a build-generation step.'}
(args.output/'kome_tile_gameplay_provenance.json').write_text(json.dumps(provenance,indent=2)+'\n');print(json.dumps(outputs,indent=2))

(args.output/'kome_tile_gameplay_manifest.properties').write_text('version=1\nrevision=baseline-pilot-20260919\n'+''.join(name+'='+v['sha256']+'\n' for name,v in outputs.items()))
