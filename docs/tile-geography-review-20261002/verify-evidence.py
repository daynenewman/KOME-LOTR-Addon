"""Regenerate local evidence from JUnit/production JAR, benchmark capture, or preserved audit files.
Writes only this report directory. No gameplay, geometry, world or historical-source writes.
"""
from pathlib import Path
import argparse, collections, csv, hashlib, json, os, re, subprocess, xml.etree.ElementTree as ET, zipfile
ROOT = Path(__file__).resolve().parents[2]
OUT = Path(__file__).resolve().parent

def sha(path):
    with path.open('rb') as stream: return hashlib.file_digest(stream, 'sha256').hexdigest()

def save(name, value):
    (OUT / name).write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8')

def build():
    suites, skipped = [], []
    totals = dict(tests=0, failures=0, errors=0, skipped=0)
    xmls = sorted((ROOT / 'build/test-results/test').glob('TEST-*.xml'))
    assert xmls and 'BUILD SUCCESSFUL' in (OUT / 'clean-build.txt').read_text(encoding='utf-8')
    for path in xmls:
        suite = ET.parse(path).getroot()
        entry = {key: suite.attrib[key] for key in ('name', 'tests', 'failures', 'errors', 'skipped')}
        entry['xml_sha256'] = sha(path); suites.append(entry)
        for key in totals: totals[key] += int(entry[key])
        skipped.extend(c.attrib['classname'] + '.' + c.attrib['name'] for c in suite.findall('testcase') if c.find('skipped') is not None)
    assert totals['failures'] == totals['errors'] == 0
    jar = ROOT / 'build/libs/KOME-LOTR-Addon-1.0.8.jar'
    majors = {}
    with zipfile.ZipFile(jar) as archive:
        assert not any(n.startswith('lotr/') and n.endswith('.class') for n in archive.namelist())
        for resource in ('reset_conquest_tile_ids.png', 'tile_exclusions.tsv'):
            name = 'assets/kome/map/' + resource
            assert archive.read(name) == (ROOT / 'src/main/resources' / name).read_bytes()
        for name in archive.namelist():
            if name.endswith('.class') and any(name.startswith(prefix) for prefix in (
                'kome/common/data/KOMETileExclusions', 'kome/common/data/KOMETileRasterSnapshot',
                'kome/common/data/KOMETileWorldResolver', 'kome/common/data/KOMETileResolution',
                'kome/common/data/KOMEServerTileAwareness', 'kome/client/KOMECurrentTileHud')):
                majors[name] = int.from_bytes(archive.read(name)[6:8], 'big')
        assert majors and set(majors.values()) == {52}
    totals.update(passed=totals['tests'] - totals['skipped'], skipped_cases=skipped,
        artifact_sha256=sha(jar), artifact_bytes=jar.stat().st_size,
        packaged_mask_and_exclusions_match=True, no_base_lotr_classes=True, changed_class_major_versions=majors)
    result = dict(command='.\\gradlew.bat clean test build --no-daemon --console=plain',
        generator='verify-evidence.py build', transcript_sha256=sha(OUT / 'clean-build.txt'), totals=totals, suites=suites)
    save('build-results.json', result); print(json.dumps(totals, indent=2))

def fields(line):
    result = {}
    for key, value in re.findall(r'(\w+)=([^ ]+)', line):
        if value in ('true', 'false'): result[key] = value == 'true'
        else:
            try: result[key] = int(value)
            except ValueError:
                try: result[key] = float(value)
                except ValueError: result[key] = value
    return result

def measure():
    text = (OUT / 'measurements.txt').read_text(encoding='utf-8')
    assert 'BUILD SUCCESSFUL' in text
    measures = {}
    for label, trials, median, minimum, maximum in re.findall(
        r'^MEASURE (.*?) trials=(\[.*?\]) median=(\S+) min=(\S+) max=(\S+)$', text, re.M):
        values = json.loads(trials); ordered = sorted(values)
        assert len(values) == 5 and ordered[2] == float(median) and min(values) == float(minimum) and max(values) == float(maximum)
        measures[label] = dict(trials=values, median=float(median), min=float(minimum), max=float(maximum))
    java, vm = re.search(r'ENV java=(\S+) vm=(.*?) os=', text).groups()
    host = {}
    if os.name == 'nt':
        command = "$measurementCpu=Get-CimInstance Win32_Processor; $measurementSys=Get-CimInstance Win32_ComputerSystem; @{host_cpu=$measurementCpu.Name;host_ram_bytes=$measurementSys.TotalPhysicalMemory}|ConvertTo-Json -Compress"
        host = json.loads(subprocess.check_output(['powershell', '-NoProfile', '-Command', command], text=True))
    counters = [dict(record=line.split()[0], values=fields(line)) for line in text.splitlines()
        if line.startswith(('MEMORY ', 'POPULATED_', 'TRACK ', 'BORDER_CACHE ', 'DRAW ', 'LIMITS '))]
    result = dict(host, java=java + ' ' + vm, heap_limit_bytes=int(re.search(r'heapMax=(\d+)', text)[1]),
        trials=5, generator='verify-evidence.py measure', transcript_sha256=sha(OUT / 'measurements.txt'),
        measurements=measures, workload_counters=counters, limitations=[
            'Current host only; not representative low-spec or a performance threshold',
            'Inert players/hires; excludes AI, chunk generation, network and game scheduling',
            'Headless CPU geometry; excludes GL/GPU, actual FPS and cold first-opening',
            'Mixed lookup trials cycle through the same seeded 8192 spatial samples; JIT/cache effects apply',
            'Populated fixture classifies all canonical gaps only for measurement; no production geographic decision',
            'Populated post-GC heap includes both original and synthetic raster snapshots and runtime/class state',
            'Metadata parse trials include first populated-parser JIT; not a worst-case 4 MiB/many-zone bound'])
    save('measurement-summary.json', result); print('Regenerated', len(measures), 'measurement groups')

def provenance(historical):
    manifest = json.loads((OUT / 'historical-provenance.json').read_text(encoding='utf-8'))
    historical = historical or Path(manifest['source_docs_root_absolute'])
    for entry in manifest['sources']:
        path = historical / entry['source']
        assert path.stat().st_size == entry['bytes'] and sha(path) == entry['sha256'], path
    for entry in manifest['authority_sources']: assert sha(ROOT / entry['source']) == entry['sha256']
    expected = []
    def rows(name):
        with (historical / name).open(encoding='utf-8-sig', newline='') as f: yield from csv.DictReader(f)
    def add(x, y, group, note, source): expected.append((str(x), str(y), group, note, source))
    source = 'v2-geography-review-20260920/cell-review.csv'
    for row in rows(source):
        if row['category'] in ('river_bank_uncertainty', 'unanchored_or_conflicting_transition', 'context_sensitive_uncertainty'):
            add(row['mask_x'], row['mask_y'], 'uncertain_v2', row['category'] + '; historical review concern, not a geographic classification', source)
    source = 'v2-geography-review-20260920/withheld-manifest.csv'
    for row in rows(source):
        add(row['mask_x'], row['mask_y'], 'withheld_transfer', row['disposition'] + '; historical candidate ' + row['current_tile'] + ' -> ' + row['supported_tile'] + '; no approval', source)
    source = 'tile-cleanup-geography-20260920/cell-audit.csv'
    for row in rows(source):
        if not row['final_tile']:
            add(row['mask_x'], row['mask_y'], 'withheld_gap', ('topology unsafe; ' if row['withheld_topology'] == 'True' else '') + row['reason'], source)
    source = 'tile-cleanup-geography-20260920/assigned-transfer-proposal.csv'
    for row in rows(source):
        add(row['mask_x'], row['mask_y'], 'blocked55', row['from_tile'] + ' -> ' + row['to_tile'] + ' proposal remains blocked; existing territory decision required', source)
    authority = 'docs/tile-milestone-checkpoint-20260920/README.md'
    add(974, 727, 'optional_shape', 'T149 -> T132 one-cell transfer remains unapplied; explicit decision required', authority)
    pilot = 'src/test/resources/kome/tile/weathertop-pilot/excluded-junction-cells.csv'
    with (ROOT / pilot).open(encoding='utf-8', newline='') as f:
        for row in csv.DictReader(f):
            add(row['mask_x'], row['mask_y'], 'protected_junction', 'Excluded pilot junction; remain unknown until explicit approval', pilot.replace('/', chr(92)))
    for x, y, note in [(2291, 58, 'Protected unclassified gap'), (2292, 58, 'Protected T001 control'), (1083, 735, 'R1 protected unclassified gap')]:
        add(x, y, 'protected_control', note, authority)
    with (OUT / 'review-inputs.csv').open(encoding='utf-8', newline='') as f:
        actual = [tuple(row[k] for k in ('mask_x', 'mask_y', 'review_group', 'review_note', 'source')) for row in csv.DictReader(f)]
    assert collections.Counter(expected) == collections.Counter(actual), 'Compact extraction differs from source predicates/notes'
    assert sha(OUT / 'review-inputs.csv') == json.loads((OUT / 'atlas-summary.json').read_text(encoding='utf-8'))['inputs_sha256']
    result = dict(source_docs_root=str(historical), source_hashes_verified=True, exact_compact_rows_verified=True,
        inputs_sha256=sha(OUT / 'review-inputs.csv'), rows=len(actual), groups=dict(collections.Counter(row[2] for row in actual)))
    save('provenance-verification.json', result); print(json.dumps(result, indent=2))

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mode', choices=('build', 'measure', 'provenance'))
    parser.add_argument('--historical-root', type=Path, help='Preserved audit docs root (defaults to recorded absolute root)')
    args = parser.parse_args()
    if args.mode == 'build': build()
    elif args.mode == 'measure': measure()
    else: provenance(args.historical_root)
