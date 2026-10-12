# Production tile raster audit

Mask SHA-256: `89bd8ccc9ceff3e7d3f9271d61125b07f5f9b1122fb51726ab2f2f5a95364a12`
Mapping SHA-256: `d8674747229c7a9cbfdebcd217f1423d722ff4cdacb882d55607491cea36d9e5`
LOTR map SHA-256: `1c79d0610dfbcfa970aa0dd929ce80cb19b1feb89fe57777b293b166282230f3`

Dimensions: 3200x4000; cells: 12800000; mapping IDs: 645; active: 621; retired exclusions: 25.
Assigned cells: 4067934; transparent cells (alpha <= 24): 8732022; retired-colored cells: 44; nontransparent partial-alpha cells: 0.
Approved mountain exclusion cells: 45; all other gaps remain unclassified (independent pinned manifest).
Every cell matched the integer resolver against independent source-image identity. Unknown opaque colors / invalid active IDs: zero (asserted).
Ownership metadata rows: 645; duplicate/unknown IDs: zero (asserted); active IDs without metadata: []. SHA-256: `51a325a18fc52652d3f0dd2b9c6fec3693dd108da65941458d6843557e3bd1bc`.
Active IDs without coverage: [].
Disconnected active IDs under four-neighbor connectivity: 1; [T423 (2)].
Active one-pixel islands with no same-ID eight-neighbor: 39.
Gap components: 425; enclosed four-neighbor gap components: 424.
Differing horizontal/vertical cell pairs: 182195; involving gaps: 107332.

Representative horizontal assigned/assigned boundaries (right cell's inclusive lower world corner): [T033 -> T030 at mask (668,453) / world (-18176,-35456), T033 -> T030 at mask (669,454) / world (-18048,-35328), T033 -> T030 at mask (670,455) / world (-17920,-35200), T033 -> T030 at mask (671,456) / world (-17792,-35072), T033 -> T030 at mask (672,457) / world (-17664,-34944), T033 -> T030 at mask (673,458) / world (-17536,-34816), T033 -> T030 at mask (675,459) / world (-17280,-34688), T033 -> T030 at mask (676,460) / world (-17152,-34560)]

See tiles.csv and components.csv for every count, bounding box and representative world coordinate. Connectivity is an audit measurement, not gameplay adjacency. Disconnection, islands, missing coverage and enclosed gaps are geographic observations, not automatic errors; no asset is modified.
