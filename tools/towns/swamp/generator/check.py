"""Reachability check for the swamp town.

On foot: a tile is blocked if any per-tile collision rect (any layer but AboveSprites) covers its centre. BFS from
the west causeway exit. A door is reached when the tile below it is reached; any other object when a tile inside its
rectangle, or touching it, is reached. Wade rects count as walkable (they have no collision; a later stage makes them
slow). The skiff-channel exit is a water exit and is checked by boat instead.

By skiff: tiles whose Background tile has class "water" or "wade" (set in the tsx) and that nothing solid stands on.
BFS from every water tile touching the skiff station; the skiff-channel exit must be reached."""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import xml.etree.ElementTree as ET
from render_town import load_tsx, layer_data
from collections import deque
tmx = sys.argv[1]
root = ET.parse(tmx).getroot()
W, H = int(root.get('width')), int(root.get('height'))
ts = []
for t in root.findall('tileset'):
    path = os.path.join(os.path.dirname(tmx), t.get('source'))
    d = load_tsx(path, int(t.get('firstgid')))
    d['cls'] = {int(e.get('id')): e.get('type') for e in ET.parse(path).getroot().findall('tile') if e.get('type')}
    ts.append(d)
ts.sort(key=lambda t: t['first'])
B = [[False] * W for _ in range(H)]
WET = [[False] * W for _ in range(H)]
for layer in root.findall('layer'):
    name = layer.get('name')
    if name == 'AboveSprites':
        continue
    for i, g in enumerate(layer_data(layer, W, H)):
        if not g:
            continue
        t = [t for t in ts if g >= t['first']][-1]
        lid = g - t['first']
        if name == 'Background' and t['cls'].get(lid) in ('water', 'wade'):
            WET[i // W][i % W] = True
        for (x, y, w, h) in t['coll'].get(lid, []):
            if x <= 8 <= x + w and y <= 8 <= y + h:
                B[i // W][i % W] = True
objs = []
for o in root.iter('object'):
    props = {p.get('name'): p.get('value') for p in o.iter('property')}
    objs.append((o.get('type'), o.get('name'), int(float(o.get('x')) // 16), int(float(o.get('y')) // 16),
                 int(float(o.get('width', 16)) // 16), int(float(o.get('height', 16)) // 16), props))


def bfs(starts, ok):
    seen = set(starts)
    q = deque(starts)
    while q:
        x, y = q.popleft()
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, ny = x + dx, y + dy
            if 0 <= nx < W and 0 <= ny < H and (nx, ny) not in seen and ok(nx, ny):
                seen.add((nx, ny))
                q.append((nx, ny))
    return seen


def touches(seen, x, y, w, h):
    return any((xx, yy) in seen for yy in range(y - 1, y + h + 1) for xx in range(x - 1, x + w + 1)
               if not ((xx < x or xx >= x + w) and (yy < y or yy >= y + h)))


start = [(x, y + dy) for (t, n, x, y, w, h, p) in objs if n == 'exit_west_causeway' for dy in range(h)]
seen = bfs(start, lambda x, y: not B[y][x])
bad = 0
for (t, n, x, y, w, h, p) in objs:
    if t == 'exit' and p.get('kind') == 'skiff_channel':
        continue
    ok = (x, y + 1) in seen if t == 'door' else touches(seen, x, y, w, h)
    if not ok:
        print('UNREACHABLE', t, n, x, y)
        bad += 1
print('on foot: reachable tiles', len(seen), 'objects', len(objs), 'unreachable', bad)

st = [o for o in objs if o[0] == 'station'][0]
_, _, sx, sy, sw, sh, _ = st
boat = lambda x, y: WET[y][x] and not B_solid_above[y][x]
B_solid_above = [[False] * W for _ in range(H)]
for layer in root.findall('layer'):
    if layer.get('name') in ('Ground', 'Foreground'):
        for i, g in enumerate(layer_data(layer, W, H)):
            if g:
                t = [t for t in ts if g >= t['first']][-1]
                if any(x <= 8 <= x + w and y <= 8 <= y + h for (x, y, w, h) in t['coll'].get(g - t['first'], [])):
                    B_solid_above[i // W][i % W] = True
starts = [(x, y) for y in range(sy, sy + sh) for x in range(sx, sx + sw) if boat(x, y)]
wet = bfs(starts, boat)
for (t, n, x, y, w, h, p) in objs:
    if t == 'exit' and p.get('kind') == 'skiff_channel':
        ok = any((xx, yy) in wet for yy in range(y, y + h) for xx in range(x, x + w))
        print('by skiff: station -> %s %s (%d water tiles reachable)' % (n, 'OK' if ok else 'NOT REACHED', len(wet)))
        bad += 0 if ok else 1
print('unreachable total', bad)
