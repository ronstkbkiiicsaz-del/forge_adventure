"""'In context' render: the swamp town placed at (35,25) in a 140x100 patch of black-biome swamp: bog pools and
hummocks, reed beds, cypress and dead trees, firmer ground to the west where the causeway goes, the north boardwalk
running on over the water, the skiff channel continuing south, and a distant ruin on a hummock to the north-east.
Writes swamp_context.tmx (illustration only).
Run: python context.py <OUTDIR>     (after town.py, which leaves _town_grids.json next to this script)"""
import os, sys, random, re, json, math
import xml.etree.ElementTree as ET
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

OUT = (sys.argv[1] if len(sys.argv) > 1 else 'A:/GameAssets/towns/swamp/').rstrip('/') + '/'
TOWN = OUT + 'swamp_town.tmx'
CW, CH = 140, 100
OX, OY = 35, 25
rnd = random.Random(17)

root = ET.parse(TOWN).getroot()
TW, TH = int(root.get('width')), int(root.get('height'))
TS = {}
for ts in root.findall('tileset'):
    src = ts.get('source')
    name = os.path.basename(src)[:-4]
    txt = open(os.path.join(OUT, src), encoding='utf-8').read()
    TS[name] = (int(ts.get('firstgid')), int(re.search(r'columns="(\d+)"', txt).group(1)))
KEY = {'F': 'ninja_SwampFloor', 'W': 'ninja_SwampWater', 'N': 'ninja_TilesetNature', 'H': 'ninja_TilesetHouse',
       'E': 'ninja_TilesetElement', 'D': 'ninja_TilesetFloorDetail', 'V': 'ninja_TilesetVillageAbandoned',
       'P': 'ninja_SwampExtras'}
import swamp_extras
SPR = swamp_extras.build(OUT + 'tileset/ninja/')   # regenerates identical images; gives sprite coords
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


def noise(x, y):
    return (math.sin(x * 0.23 + 1.3) * math.cos(y * 0.19 - 0.4) + 0.6 * math.sin(x * 0.09 - y * 0.13 + 2.0)
            + 0.45 * math.sin(x * 0.47 + y * 0.41) + 0.3 * math.cos(x * 0.71 - y * 0.66))


def wild_land(x, y):
    v = noise(x, y) - 0.68
    v += max(0.0, (16 - x) / 10.0)                         # firmer ground to the west, where the causeway goes
    if ((x - 122) / 9.0) ** 2 + ((y - 12) / 6.5) ** 2 < 1:  # the ruin hummock
        v = 1
    return v > 0


WATER = [[0] * (CW + 1) for _ in range(CH + 1)]
SAND = [[0] * (CW + 1) for _ in range(CH + 1)]
for y in range(CH + 1):
    for x in range(CW + 1):
        tx, ty = x - OX, y - OY
        if 0 <= tx <= TW and 0 <= ty <= TH:
            WATER[y][x] = G['water'][ty][tx]
            SAND[y][x] = G['sand'][ty][tx]
            continue
        bx, by = min(max(tx, 0), TW), min(max(ty, 0), TH)
        d = math.hypot(tx - bx, ty - by)
        w = max(0.0, 1 - d / 7.0)
        land_town = 1 - G['water'][by][bx]
        land = w * land_town + (1 - w) * (1 if wild_land(x, y) else 0)
        WATER[y][x] = 0 if land >= 0.5 else 1


def inside_v(x, y):
    return 0 <= x - OX <= TW and 0 <= y - OY <= TH


for _ in range(4):
    nw = [row[:] for row in WATER]
    for y in range(1, CH):
        for x in range(1, CW):
            if inside_v(x, y):
                continue
            n = sum(WATER[y + dy][x + dx] for dy in (-1, 0, 1) for dx in (-1, 0, 1))
            nw[y][x] = 1 if n >= 5 else 0
    WATER = nw


def setv(grid, x0, y0, x1, y1, v):
    for y in range(max(0, y0), min(CH, y1) + 1):
        for x in range(max(0, x0), min(CW, x1) + 1):
            if not inside_v(x, y):
                grid[y][x] = v


# the causeway runs on west to firm ground; the skiff channel stays open water south of the town
cy = OY + 25
for x in range(OX - 1, -1, -1):
    if rnd.random() < 0.06:
        cy += rnd.choice((-1, 1))
    setv(WATER, x, cy - 2, x + 1, cy + 4, 0)
    setv(SAND, x, cy, x + 1, cy + 2, 1)
CAUSE_Y = cy
setv(WATER, OX + 42, OY + TH, OX + 50, CH, 1)
setv(SAND, OX + 42, OY + TH, OX + 50, CH, 0)
# the north boardwalk continues over the water (planks drawn later), keep water under it
NORTH = [(OX + 21, y) for y in range(0, OY)] + [(OX + 22, y) for y in range(0, OY)]

FLOOR_SIG = {(0,0,0,1):(0,7),(0,0,1,1):(1,7),(0,0,1,0):(2,7),(0,1,0,1):(0,8),(1,1,1,1):(1,8),
             (1,0,1,0):(2,8),(0,1,0,0):(0,9),(1,1,0,0):(1,9),(1,0,0,0):(2,9),
             (0,1,1,1):(6,9),(1,0,1,1):(5,9),(1,1,0,1):(6,8),(1,1,1,0):(5,8),
             (0,1,1,0):(9,8),(1,0,0,1):(9,7)}
WATER_SIG = {(0,0,0,1):(0,6),(0,0,1,1):(1,6),(0,0,1,0):(2,6),(0,1,0,1):(0,7),(1,1,1,1):(1,7),
             (1,0,1,0):(2,7),(0,1,0,0):(0,8),(1,1,0,0):(1,8),(1,0,0,0):(2,8),
             (0,1,1,1):(6,8),(1,0,1,1):(5,8),(1,1,0,1):(6,7),(1,1,1,0):(5,7),
             (0,1,1,0):(9,7),(1,0,0,1):(9,6)}

# mud banks around every pool
for y in range(CH + 1):
    for x in range(CW + 1):
        if WATER[y][x] or inside_v(x, y):
            continue
        if any(0 <= y + dy <= CH and 0 <= x + dx <= CW and WATER[y + dy][x + dx]
               for dy in (-1, 0, 1) for dx in (-1, 0, 1)):
            SAND[y][x] = 1

USED = [[False] * CW for _ in range(CH)]


def mark(x0, y0, x1, y1):
    for y in range(max(0, y0), min(CH, y1 + 1)):
        for x in range(max(0, x0), min(CW, x1 + 1)):
            USED[y][x] = True


mark(OX - 1, OY - 1, OX + TW, OY + TH)
for (x, y) in NORTH:
    mark(x - 1, y, x + 1, y)
mark(0, CAUSE_Y - 1, OX, CAUSE_Y + 3)
mark(OX + 41, OY + TH, OX + 51, CH)

# ---------------- background autotile (outside the town) ----------------
FULLW = lambda x, y: WATER[y][x] and WATER[y][x + 1] and WATER[y + 1][x] and WATER[y + 1][x + 1]
dry = [(x, y) for y in range(CH + 1) for x in range(CW + 1) if not WATER[y][x]]
grid = {}
for (x, y) in dry:
    grid.setdefault((x // 6, y // 6), []).append((x, y))
deepv = [[0] * (CW + 1) for _ in range(CH + 1)]
for y in range(CH + 1):
    for x in range(CW + 1):
        if not WATER[y][x]:
            continue
        thr = 3.4 + 0.7 * math.sin(x * 0.45 + 1.3) * math.cos(y * 0.38 - 0.7) + 0.35 * math.sin(x * 1.1 + y * 0.9)
        best = 99.0
        for cy_ in range(y // 6 - 1, y // 6 + 2):
            for cx_ in range(x // 6 - 1, x // 6 + 2):
                for (dx, dy) in grid.get((cx_, cy_), ()):
                    best = min(best, (dx - x) ** 2 + (dy - y) ** 2)
        deepv[y][x] = 1 if best ** 0.5 >= thr else 0
DEEP_SIG = SPR['deep_sig']
DEEPT = [[False] * CW for _ in range(CH)]
for y in range(CH):
    for x in range(CW):
        if INTOWN(x, y):
            continue
        s = (WATER[y][x], WATER[y][x + 1], WATER[y + 1][x], WATER[y + 1][x + 1])
        if s == (1, 1, 1, 1):
            dv = (deepv[y][x], deepv[y][x + 1], deepv[y + 1][x], deepv[y + 1][x + 1])
            DEEPT[y][x] = dv == (1, 1, 1, 1)
            if dv == (0, 0, 0, 0):
                put('Background', x, y, 'W', 1, 1)
            elif dv == (1, 1, 1, 1) and rnd.random() < 0.05:
                put('Background', x, y, 'P', *SPR['deep_ripple'][:2])
            else:
                put('Background', x, y, 'P', *DEEP_SIG[dv])
        elif s != (0, 0, 0, 0):
            c, r = WATER_SIG[s]
            put('Background', x, y, 'W', c, r - 6)
            USED[y][x] = True
        else:
            s = (SAND[y][x], SAND[y][x + 1], SAND[y + 1][x], SAND[y + 1][x + 1])
            if s != (0, 0, 0, 0):
                c, r = FLOOR_SIG[s]
                put('Background', x, y, 'F', c, r)
            else:
                put('Background', x, y, 'F', rnd.choice((1, 2, 3, 4)) if rnd.random() < 0.1 else 0, 12)

# the north boardwalk planks (2 wide, running off the top edge)
wc, wr = SPR['walk_s'][:2]
dc, dr = SPR['walk_d'][:2]
for (x, y) in NORTH:
    c, r = (dc, dr) if DEEPT[y][x] else (wc, wr)
    put('Background', x, y, 'P', c + (0 if x == OX + 21 else 2), r + 1)
for y in range(3, OY, 7):
    put('AboveSprites', OX + 20, y - 1, 'P', *SPR['lantern_post'][:2])
    put('Foreground', OX + 20, y, 'P', SPR['lantern_post'][0], SPR['lantern_post'][1] + 1)
# lanterns along the causeway
for x in range(3, OX - 1, 8):
    put('AboveSprites', x, CAUSE_Y - 2, 'P', *SPR['lantern_post'][:2])
    put('Foreground', x, CAUSE_Y - 1, 'P', SPR['lantern_post'][0], SPR['lantern_post'][1] + 1)


def is_land(x, y):
    return 0 <= x < CW and 0 <= y < CH and not INTOWN(x, y) and \
        all(not WATER[yy][xx] for yy in (y, y + 1) for xx in (x, x + 1))


def is_grass(x, y):
    return is_land(x, y) and all(not SAND[yy][xx] for yy in (y, y + 1) for xx in (x, x + 1))


def is_wet(x, y):
    return 0 <= x < CW and 0 <= y < CH and not INTOWN(x, y) and FULLW(x, y)


def free(x0, y0, x1, y1, test):
    return all(0 <= x < CW and 0 <= y < CH and not USED[y][x] and test(x, y)
               for y in range(y0, y1 + 1) for x in range(x0, x1 + 1))


def sprite(name, x, y, above):
    c0, r0, w, h = SPR[name]
    stamp('P', c0, r0, w, h, x, y, above)
    mark(x, y, x + w - 1, y + h - 1)


# ---------------- the distant ruin on its hummock (north-east) ----------------
sprite('ruin', 118, 9, 1)
sprite('ruin_low', 123, 11, 1)
stamp('V', 0, 3, 2, 3, 115, 11, above=1); mark(115, 11, 116, 13)      # a mossy statue
sprite('dead_big', 125, 6, 2)
for (x, y) in [(120, 14), (126, 15), (114, 9)]:
    put('Foreground', x, y, 'P', *SPR['tomb_old'][:2]); mark(x, y, x, y)
for i, (x, y) in enumerate([(122, 15), (117, 15)]):
    c, r = SPR['banner%d' % i][:2]
    put('AboveSprites', x, y, 'P', c, r); put('Foreground', x, y + 1, 'P', c, r + 1); mark(x, y, x, y + 1)
put('AboveSprites', 121, 7, 'P', *SPR['wisp'][:2])

# ---------------- vegetation ----------------
TREES = [('cypress', 3), ('cypress2', 3), ('cypress_s', 2), ('dead_moss', 1), ('dead_big', 2)]
for _ in range(5000):
    x, y = rnd.randint(0, CW - 4), rnd.randint(0, CH - 4)
    nm, above = rnd.choice(TREES)
    c0, r0, w, h = SPR[nm]
    dens = 0.35 + 0.35 * math.sin(x * 0.08 + 1) * math.cos(y * 0.1)
    if rnd.random() > dens:
        continue
    base_ok = lambda xx, yy: True
    if free(x - 1, y, x + w, y + h - 1, lambda a, b: not INTOWN(a, b)) and \
            (is_wet(x + w // 2, y + h - 1) or is_land(x + w // 2, y + h - 1)):
        sprite(nm, x, y, above)
        mark(x - 1, y - 1, x + w, y + h)
# reed beds: dense where a slow noise is high, on shallow water near banks; lilies on open shallows
for y in range(CH):
    for x in range(CW):
        if USED[y][x] or not is_wet(x, y) or DEEPT[y][x]:
            continue
        bed = math.sin(x * 0.17 + 0.5) * math.cos(y * 0.21 + 1.1) + 0.4 * math.sin(x * 0.5 + y * 0.3)
        near = any(is_land(x + dx, y + dy) for dy in (-1, 0, 1) for dx in (-1, 0, 1))
        r_ = rnd.random()
        if (bed > 0.55 and r_ < 0.75) or (near and r_ < 0.3):
            put('Ground', x, y, 'P', *SPR['reeds%d' % rnd.randint(0, 2)][:2])
        elif r_ < 0.07:
            put('Ground', x, y, 'P', *SPR[rnd.choice(('lily', 'lily', 'lily_f'))][:2])
# land: tufts, mushrooms, stumps
for y in range(CH):
    for x in range(CW):
        if USED[y][x] or not is_grass(x, y):
            continue
        r_ = rnd.random()
        if r_ < 0.07:
            put('Ground', x, y, *rnd.choice([('D', 0, 2), ('D', 1, 2), ('D', 3, 2), ('D', 6, 2)]))
        elif r_ < 0.10:
            put('Ground', x, y, 'P', *SPR[rnd.choice(('shroom0', 'shroom1', 'shroom1', 'shroom2'))][:2])
        elif r_ < 0.11:
            put('Foreground', x, y, 'N', 4, 8)
# wisps, fireflies, mist
for _ in range(60):
    x, y = rnd.randint(0, CW - 1), rnd.randint(0, CH - 1)
    if not INTOWN(x, y) and not L['AboveSprites'][y][x]:
        put('AboveSprites', x, y, 'P', *SPR[rnd.choice(('wisp', 'wisp2', 'fireflies', 'fireflies'))][:2])
for _ in range(70):
    nm = rnd.choice(('mist_a', 'mist_b', 'mist_c'))
    c0, r0, w, h = SPR[nm]
    x, y = rnd.randint(0, CW - w), rnd.randint(0, CH - h)
    if all(not INTOWN(x + dx, y + dy) and not L['AboveSprites'][y + dy][x + dx] for dy in range(h) for dx in range(w)):
        for dy in range(h):
            for dx in range(w):
                put('AboveSprites', x + dx, y + dy, 'P', c0 + dx, r0 + dy)

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
open(OUT + 'swamp_context.tmx', 'w', encoding='utf-8', newline='\n').write('\n'.join(lines))
print('context written')
