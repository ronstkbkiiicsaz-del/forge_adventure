"""Reachability check: tile blocked if any per-tile collision rect (any layer but AboveSprites) covers its centre.
BFS from the north road exit. A door is reached when the tile below it is reached; any other object when a tile
inside its rectangle, or touching it, is reached (boards, stations and the like are solid props you walk up to)."""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import xml.etree.ElementTree as ET
from render_town import load_tsx, layer_data
from collections import deque
tmx = sys.argv[1]
root = ET.parse(tmx).getroot()
W, H = int(root.get('width')), int(root.get('height'))
ts = sorted([load_tsx(os.path.join(os.path.dirname(tmx), t.get('source')), int(t.get('firstgid'))) for t in root.findall('tileset')], key=lambda t: t['first'])
B = [[False] * W for _ in range(H)]
for layer in root.findall('layer'):
    if layer.get('name') == 'AboveSprites':
        continue
    for i, g in enumerate(layer_data(layer, W, H)):
        if not g: continue
        t = [t for t in ts if g >= t['first']][-1]
        for (x, y, w, h) in t['coll'].get(g - t['first'], []):
            if x <= 8 <= x + w and y <= 8 <= y + h:
                B[i // W][i % W] = True
objs = [(o.get('type'), o.get('name'), int(float(o.get('x')) // 16), int(float(o.get('y')) // 16),
         int(float(o.get('width', 16)) // 16), int(float(o.get('height', 16)) // 16)) for o in root.iter('object')]
start = (31, 0)          # the north road exit
seen = {start}; q = deque([start])
while q:
    x, y = q.popleft()
    for dx, dy in ((1,0),(-1,0),(0,1),(0,-1)):
        nx, ny = x + dx, y + dy
        if 0 <= nx < W and 0 <= ny < H and (nx, ny) not in seen and not B[ny][nx]:
            seen.add((nx, ny)); q.append((nx, ny))
bad = 0
for (t, n, x, y, w, h) in objs:
    if t == 'door':
        ok = (x, y + 1) in seen
    else:
        ok = any((xx, yy) in seen for yy in range(y - 1, y + h + 1) for xx in range(x - 1, x + w + 1)
                 if not ((xx < x or xx >= x + w) and (yy < y or yy >= y + h)))
    if not ok:
        print('UNREACHABLE', t, n, x, y); bad += 1
print('reachable tiles', len(seen), 'objects', len(objs), 'unreachable', bad)
