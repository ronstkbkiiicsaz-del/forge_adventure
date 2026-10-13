"""'In context' render: the port town placed at (35,25) in a 140x100 patch of a blue-biome island: the island's
coast continues the town's shoreline, open sea to the south and east, a small wild islet (no dock) to the south-east,
the north road and the two coast paths continue out of town. Writes port_context.tmx (illustration only).
Run: python context.py <OUTDIR>     (after town.py, which leaves _town_grids.json next to this script)"""
import os, sys, random, re, json, math
import xml.etree.ElementTree as ET
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

OUT = (sys.argv[1] if len(sys.argv) > 1 else 'A:/GameAssets/towns/port/').rstrip('/') + '/'
TOWN = OUT + 'port_town.tmx'
CW, CH = 140, 100
OX, OY = 35, 25
rnd = random.Random(11)

root = ET.parse(TOWN).getroot()
TW, TH = int(root.get('width')), int(root.get('height'))
TS = {}
for ts in root.findall('tileset'):
    src = ts.get('source')
    name = os.path.basename(src)[:-4]
    txt = open(os.path.join(OUT, src), encoding='utf-8').read()
    TS[name] = (int(ts.get('firstgid')), int(re.search(r'columns="(\d+)"', txt).group(1)))
KEY = {'F': 'ninja_PortFloor', 'W': 'ninja_PortWater', 'N': 'ninja_TilesetNature', 'H': 'ninja_TilesetHouse',
       'E': 'ninja_TilesetElement', 'D': 'ninja_TilesetFloorDetail', 'S': 'ninja_TilesetDesert', 'P': 'ninja_PortExtras'}
import port_extras
SPR = port_extras.build(OUT + 'tileset/ninja/')   # regenerates identical images; gives sprite coords
G = json.load(open(os.path.join(HERE, '_town_grids.json')))


def g(k, c, r):
    first, cols = TS[KEY[k]]
    return first + r * cols + c


LAYERS = ['Background', 'Ground', 'Foreground', 'AboveSprites']
L = {n: [[0] * CW for _ in range(CH)] for n in LAYERS}
INTOWN = lambda x, y: OX <= x < OX + TW and OY <= y < OY + TH


def put(layer, x, y, k, c, r):
    if 0 <= x < CW and 0 <= y < CH and not INTOWN(x, y):
        L[layer][y][x] = g(k, c, r)


def stamp(k, c0, r0, w, h, x, y, above=0):
    for dy in range(h):
        for dx in range(w):
            put('AboveSprites' if dy < above else 'Foreground', x + dx, y + dy, k, c0 + dx, r0 + dy)


# ---------------- land / sea on the vertex grid ----------------
def noise(x, y):
    return (math.sin(x * 0.21 + 1.3) * math.cos(y * 0.17 - 0.4) + 0.6 * math.sin(x * 0.07 - y * 0.11 + 2.0)
            + 0.35 * math.sin(x * 0.43 + y * 0.37))


def island(x, y):
    a = ((x - 46) / 50.0) ** 2 + ((y - 34) / 38.0) ** 2
    land = 1 - a + 0.16 * noise(x, y) > 0
    b = ((x - 121) / 8.5) ** 2 + ((y - 86) / 6.0) ** 2           # the wild islet
    return land or (1 - b + 0.22 * noise(x + 40, y) > 0)


WATER = [[0] * (CW + 1) for _ in range(CH + 1)]
SAND = [[0] * (CW + 1) for _ in range(CH + 1)]
for y in range(CH + 1):
    for x in range(CW + 1):
        tx, ty = x - OX, y - OY
        if 0 <= tx <= TW and 0 <= ty <= TH:
            WATER[y][x] = G['water'][ty][tx]
            SAND[y][x] = G['sand'][ty][tx]
            continue
        # outside the town: island shape, blended into the town's border within 8 tiles
        bx, by = min(max(tx, 0), TW), min(max(ty, 0), TH)
        d = math.hypot(tx - bx, ty - by)
        w = max(0.0, 1 - d / 10.0)
        land_town = 1 - G['water'][by][bx]
        land = w * land_town + (1 - w) * (1 if island(x, y) else 0)
        WATER[y][x] = 0 if land >= 0.5 else 1

# smooth the outside coastline (majority filter), the town's own vertices stay fixed
def inside_v(x, y):
    return 0 <= x - OX <= TW and 0 <= y - OY <= TH
for _ in range(5):
    nw = [row[:] for row in WATER]
    for y in range(1, CH):
        for x in range(1, CW):
            if inside_v(x, y):
                continue
            n = sum(WATER[y + dy][x + dx] for dy in (-1, 0, 1) for dx in (-1, 0, 1))
            nw[y][x] = 1 if n >= 5 else 0
    WATER = nw

FLOOR_SIG = {(0,0,0,1):(0,7),(0,0,1,1):(1,7),(0,0,1,0):(2,7),(0,1,0,1):(0,8),(1,1,1,1):(1,8),
             (1,0,1,0):(2,8),(0,1,0,0):(0,9),(1,1,0,0):(1,9),(1,0,0,0):(2,9),
             (0,1,1,1):(6,9),(1,0,1,1):(5,9),(1,1,0,1):(6,8),(1,1,1,0):(5,8),
             (0,1,1,0):(9,8),(1,0,0,1):(9,7)}
WATER_SIG = {(0,0,0,1):(0,6),(0,0,1,1):(1,6),(0,0,1,0):(2,6),(0,1,0,1):(0,7),(1,1,1,1):(1,7),
             (1,0,1,0):(2,7),(0,1,0,0):(0,8),(1,1,0,0):(1,8),(1,0,0,0):(2,8),
             (0,1,1,1):(6,8),(1,0,1,1):(5,8),(1,1,0,1):(6,7),(1,1,1,0):(5,7),
             (0,1,1,0):(9,7),(1,0,0,1):(9,6)}

# beaches: sand within 1-2 vertices of the sea (2 on the island's outer coast)
for y in range(CH + 1):
    for x in range(CW + 1):
        if WATER[y][x]:
            continue
        r = 2 if (x * 7 + y * 3) % 5 else 1
        for dy in range(-r, r + 1):
            for dx in range(-r, r + 1):
                if 0 <= y + dy <= CH and 0 <= x + dx <= CW and WATER[y + dy][x + dx]:
                    if not (0 <= x - OX <= TW and 0 <= y - OY <= TH):
                        SAND[y][x] = 1


def road(x0, y0, x1, y1):
    for y in range(max(0, y0), min(CH, y1 + 1) + 1):
        for x in range(max(0, x0), min(CW, x1 + 1) + 1):
            if not (0 <= x - OX <= TW and 0 <= y - OY <= TH):
                SAND[y][x] = 1


USED = [[False] * CW for _ in range(CH)]


def mark(x0, y0, x1, y1):
    for y in range(max(0, y0), min(CH, y1 + 1)):
        for x in range(max(0, x0), min(CW, x1 + 1)):
            USED[y][x] = True


mark(OX - 1, OY - 1, OX + TW, OY + TH)


def wander_v(x, y0, y1, w, step):
    y = y0
    while y != y1 + step:
        if rnd.random() < 0.08:
            x += rnd.choice((-1, 1))
        road(x, y, x + w - 1, y)
        mark(x - 1, y, x + w, y)
        y += step


wander_v(OX + 30, OY - 1, 0, 3, -1)                  # north road, inland
wander_v(OX + 61, OY - 1, 0, 2, -1)                  # coast path north
wander_v(OX + 4, OY + TH, CH - 1, 2, 1)              # coast path south (fades into the beach)

# ---------------- background autotile (outside the town) ----------------
FULLW = lambda x, y: WATER[y][x] and WATER[y][x + 1] and WATER[y + 1][x] and WATER[y + 1][x + 1]
dist = [[0 if not FULLW(x, y) else 99 for x in range(CW)] for y in range(CH)]
for _ in range(2):
    for rng in ((range(CH), range(CW)), (range(CH - 1, -1, -1), range(CW - 1, -1, -1))):
        for y in rng[0]:
            for x in rng[1]:
                if dist[y][x]:
                    m = dist[y][x]
                    for dy in (-1, 0, 1):
                        for dx in (-1, 0, 1):
                            if 0 <= y + dy < CH and 0 <= x + dx < CW:
                                m = min(m, dist[y + dy][x + dx] + 1)
                    dist[y][x] = m
DEEP_SIG = SPR['deep_sig']
for y in range(CH):
    for x in range(CW):
        if INTOWN(x, y):
            continue
        s = (WATER[y][x], WATER[y][x + 1], WATER[y + 1][x], WATER[y + 1][x + 1])
        if s == (1, 1, 1, 1):
            dv = []
            for (vx, vy) in ((x, y), (x + 1, y), (x, y + 1), (x + 1, y + 1)):
                ok = all(dist[min(max(ty, 0), CH - 1)][min(max(tx, 0), CW - 1)] >= 3
                         for (tx, ty) in ((vx - 1, vy - 1), (vx, vy - 1), (vx - 1, vy), (vx, vy)))
                dv.append(1 if ok else 0)
            dv = tuple(dv)
            if dv == (0, 0, 0, 0):
                put('Background', x, y, 'W', 1, 1)
            elif dv == (1, 1, 1, 1) and rnd.random() < 0.05:
                put('Background', x, y, 'P', *SPR['deep_ripple'][:2])
            else:
                put('Background', x, y, 'P', *DEEP_SIG[dv])
            USED[y][x] = True
        elif s != (0, 0, 0, 0):
            c, r = WATER_SIG[s]
            put('Background', x, y, 'W', c, r - 6)
            USED[y][x] = True
        else:
            s = (SAND[y][x], SAND[y][x + 1], SAND[y + 1][x], SAND[y + 1][x + 1])
            if s != (0, 0, 0, 0):
                c, r = FLOOR_SIG[s]
                put('Background', x, y, 'F', c, r)
                if s == (1, 1, 1, 1) and rnd.random() < 0.04 and not USED[y][x]:
                    put('Ground', x, y, 'D', rnd.choice((10, 15, 4)), 0)
            else:
                put('Background', x, y, 'F', rnd.choice((1, 2, 3, 4)) if rnd.random() < 0.1 else 0, 12)


def is_grass(x, y):
    return 0 <= x < CW and 0 <= y < CH and not INTOWN(x, y) and \
        all(not WATER[yy][xx] and not SAND[yy][xx] for yy in (y, y + 1) for xx in (x, x + 1))


def is_sand(x, y):
    return 0 <= x < CW and 0 <= y < CH and not INTOWN(x, y) and \
        all(not WATER[yy][xx] for yy in (y, y + 1) for xx in (x, x + 1))


def free(x0, y0, x1, y1, test):
    return all(0 <= x < CW and 0 <= y < CH and not USED[y][x] and test(x, y)
               for y in range(y0, y1 + 1) for x in range(x0, x1 + 1))


# ---------------- vegetation: woods inland, palms on the beaches, rocks, meadow flowers ----------------
TREES = [((N, c, r, w, h, a)) for (N, c, r, w, h, a) in
         [('N', 0, 0, 2, 2, 1), ('N', 16, 0, 2, 2, 1), ('N', 18, 0, 2, 2, 1), ('N', 6, 0, 2, 2, 1),
          ('N', 3, 18, 3, 3, 2), ('S', 10, 4, 4, 3, 2)]]
for _ in range(2600):
    x, y = rnd.randint(0, CW - 3), rnd.randint(0, CH - 3)
    dens = 0.5 + 0.5 * math.sin(x * 0.09 + 1) * math.cos(y * 0.11)
    if rnd.random() > dens:
        continue
    k, c, r, w, h, a = rnd.choice(TREES)
    if free(x, y, x + w - 1, y + h - 1, is_grass):
        stamp(k, c, r, w, h, x, y, above=a)
        mark(x, y, x + w - 1, y + h - 1)
for _ in range(900):
    x, y = rnd.randint(0, CW - 2), rnd.randint(0, CH - 2)
    if free(x, y, x + 1, y + 1, is_sand) and not free(x, y, x + 1, y + 1, is_grass):
        if rnd.random() < 0.5:
            stamp('S', rnd.choice((10, 12)), 10, 2, 2, x, y, above=1)
        else:
            stamp('N', 15, rnd.choice((14, 16)), 2, 2, x, y, above=1)
        mark(x - 1, y - 1, x + 2, y + 2)
for _ in range(400):
    x, y = rnd.randint(0, CW - 1), rnd.randint(0, CH - 1)
    if free(x, y, x, y, is_grass) and rnd.random() < 0.6:
        put('Ground', x, y, *rnd.choice([('N', 0, 11), ('N', 1, 11), ('N', 3, 11), ('D', 0, 2), ('D', 3, 2),
                                         ('N', 0, 10), ('N', 1, 10)]))
        mark(x, y, x, y)
# rocks standing in the sea near the coasts
for _ in range(500):
    x, y = rnd.randint(0, CW - 1), rnd.randint(0, CH - 1)
    if not INTOWN(x, y) and FULLW(x, y) and 1 <= dist[y][x] <= 2 and rnd.random() < 0.12:
        put('Ground', x, y, 'N', rnd.choice((17, 18, 19)), 17)
# a wreck on the wild islet's beach and a gull or two
for (x, y) in [(118, 89), (124, 81), (12, 92)]:
    if is_sand(x, y) and is_sand(x + 1, y):
        put('Foreground', x, y, 'P', SPR['rowboat_up'][0], SPR['rowboat_up'][1])
        put('Foreground', x + 1, y, 'P', SPR['rowboat_up'][0] + 1, SPR['rowboat_up'][1])
for (x, y) in [(110, 70), (128, 40), (20, 85), (90, 92)]:
    put('Foreground', x, y, 'P', *SPR['gull'][:2])

# ---------------- paste the town ----------------
for layer in root.findall('layer'):
    n = layer.get('name')
    vals = [int(v) for v in layer.find('data').text.replace('\n', '').split(',') if v.strip()]
    for i, v in enumerate(vals):
        L[n][OY + i // TW][OX + i % TW] = v

lines = ['<?xml version="1.0" encoding="UTF-8"?>',
         '<map version="1.10" tiledversion="1.10.1" orientation="orthogonal" renderorder="right-down" width="%d" '
         'height="%d" tilewidth="16" tileheight="16" infinite="0" nextlayerid="5" nextobjectid="1">' % (CW, CH)]
for ts in root.findall('tileset'):
    lines.append(' <tileset firstgid="%s" source="%s"/>' % (ts.get('firstgid'), ts.get('source')))
for i, n in enumerate(LAYERS, 1):
    lines.append(' <layer id="%d" name="%s" width="%d" height="%d">' % (i, n, CW, CH))
    lines.append('  <data encoding="csv">')
    lines.append(',\n'.join(','.join(str(v) for v in row) for row in L[n]))
    lines.append('</data>')
    lines.append(' </layer>')
lines.append('</map>\n')
open(OUT + 'port_context.tmx', 'w', encoding='utf-8', newline='\n').write('\n'.join(lines))
print('context written')
