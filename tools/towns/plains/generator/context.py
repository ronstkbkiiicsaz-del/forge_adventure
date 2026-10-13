"""'In context' render: the plains market town placed in a 140x100 patch of generated plains farmland
(patchwork fields, hedgerows, orchards, farmsteads, roads leaving the town). Uses the same Ninja tiles as the
world-gen prototype (white-biome greens, tilled soil, wheat, hedges). Writes plains_context.tmx + PNG.
Run: python context.py <OUTDIR>"""
import os, sys, random, re
import xml.etree.ElementTree as ET
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

OUT = (sys.argv[1] if len(sys.argv) > 1 else 'A:/GameAssets/towns/plains/').rstrip('/') + '/'
TOWN = OUT + 'plains_market_town.tmx'
CW, CH = 140, 100
OX, OY = 35, 25
rnd = random.Random(7)

root = ET.parse(TOWN).getroot()
TW, TH = int(root.get('width')), int(root.get('height'))
TS = {}
for ts in root.findall('tileset'):
    src = ts.get('source')
    name = os.path.basename(src)[:-4]
    txt = open(os.path.join(OUT, src), encoding='utf-8').read()
    TS[name] = (int(ts.get('firstgid')), int(re.search(r'columns="(\d+)"', txt).group(1)))
KEY = {'F': 'ninja_PlainsFloor', 'W': 'ninja_PlainsWater', 'N': 'ninja_TilesetNature', 'H': 'ninja_TilesetHouse',
       'E': 'ninja_TilesetElement', 'D': 'ninja_TilesetFloorDetail', 'P': 'ninja_PlainsExtras'}
import plains_extras
SPR = plains_extras.build(OUT + 'tileset/ninja/')   # regenerates identical images; gives sprite coords


def g(k, c, r):
    first, cols = TS[KEY[k]]
    return first + r * cols + c


LAYERS = ['Background', 'Ground', 'Foreground', 'AboveSprites']
L = {n: [[0] * CW for _ in range(CH)] for n in LAYERS}
TOWNRECT = (OX, OY, OX + TW - 1, OY + TH - 1)
USED = [[False] * CW for _ in range(CH)]
for y in range(OY - 1, OY + TH + 1):
    for x in range(OX - 1, OX + TW + 1):
        if 0 <= x < CW and 0 <= y < CH:
            USED[y][x] = True


def put(layer, x, y, k, c, r):
    if 0 <= x < CW and 0 <= y < CH and not (TOWNRECT[0] <= x <= TOWNRECT[2] and TOWNRECT[1] <= y <= TOWNRECT[3]):
        L[layer][y][x] = g(k, c, r)


def stamp(k, c0, r0, w, h, x, y, above=0):
    for dy in range(h):
        for dx in range(w):
            put('AboveSprites' if dy < above else 'Foreground', x + dx, y + dy, k, c0 + dx, r0 + dy)


def free(x0, y0, x1, y1):
    if x0 < 0 or y0 < 0 or x1 >= CW or y1 >= CH:
        return False
    return not any(USED[y][x] for y in range(y0, y1 + 1) for x in range(x0, x1 + 1))


def mark(x0, y0, x1, y1):
    for y in range(max(0, y0), min(CH, y1 + 1)):
        for x in range(max(0, x0), min(CW, x1 + 1)):
            USED[y][x] = True


FLOOR_SIG = {(0,0,0,1):(0,7),(0,0,1,1):(1,7),(0,0,1,0):(2,7),(0,1,0,1):(0,8),(1,1,1,1):(1,8),
             (1,0,1,0):(2,8),(0,1,0,0):(0,9),(1,1,0,0):(1,9),(1,0,0,0):(2,9),
             (0,1,1,1):(6,9),(1,0,1,1):(5,9),(1,1,0,1):(6,8),(1,1,1,0):(5,8),
             (0,1,1,0):(9,8),(1,0,0,1):(9,7)}
DIRT = [[0] * (CW + 1) for _ in range(CH + 1)]
TILL = [[0] * (CW + 1) for _ in range(CH + 1)]


def vrect(grid, x0, y0, x1, y1):
    for y in range(max(0, y0), min(CH, y1) + 1):
        for x in range(max(0, x0), min(CW, x1) + 1):
            grid[y][x] = 1


def road(x0, y0, x1, y1):
    vrect(DIRT, x0, y0, x1 + 1, y1 + 1)
    mark(x0, y0 - 1, x1, y1 + 1)


# ---- roads leaving the town (town coords + offset), wandering a little ----
def wander_v(x, y0, y1, w=3):
    for y in range(y0, y1 + 1):
        if rnd.random() < 0.06:
            x += rnd.choice((-1, 1))
        road(x, y, x + w - 1, y)
def wander_h(y, x0, x1, w=3, step=1):
    xs = range(x0, x1 + step, step)
    for x in xs:
        if rnd.random() < 0.06:
            y += rnd.choice((-1, 1))
        road(x, y, x, y + w - 1)
road(OX + 33, 0, OX + 35, OY); road(OX + 33, OY + TH - 1, OX + 35, CH - 1)
road(0, OY + 24, OX, OY + 26); road(OX + TW - 1, OY + 24, CW - 1, OY + 26)
wander_v(OX + 33, 0, 4); wander_v(OX + 33, CH - 5, CH - 1)
road(10, OY + 18, OX, OY + 19)                          # back-lane field track
road(14, OY + 34, OX, OY + 35); road(OX + TW - 1, OY + 34, CW - 12, OY + 35)
road(20, OY + 44, OX, OY + 45)
road(14, OY + 18, 15, OY + 35)                          # tracks joining up west
road(CW - 13, OY + 26, CW - 12, OY + 35)

# ---- farmsteads ----
HOUSES = ['orange_straw', 'red_teal', 'beige_moss', 'orange2_slate', 'red_straw', 'orange']


def farmstead(x, y, hk, flip=False):
    """farmhouse + barn + fenced yard with animals and hay; ~16x12, track to the nearest road row below."""
    c0, r0, w, h = SPR[hk]
    stamp('P', c0, r0, w, h, x, y, above=1)
    bx = x + 6
    stamp('H', 25, 7, 4, 7, bx, y - 4, above=4)          # big thatched barn (world-gen 'barn')
    c0, r0, w2, h2 = SPR['granary']
    stamp('P', c0, r0, w2, h2, x + 11, y, above=1)
    road(x + 1, y + 3, x + 1, y + 4); road(bx + 1, y + 3, bx + 2, y + 4)
    road(x, y + 5, x + 16, y + 5)
    # paddock
    px0, py0 = x + 1, y + 7
    for xx in range(px0, px0 + 10):
        put('Foreground', xx, py0, 'H', 11, 7); put('Foreground', xx, py0 + 4, 'H', 11, 7)
    for yy in range(py0, py0 + 5):
        put('Foreground', px0 - 1, yy, 'H', 11, 6); put('Foreground', px0 + 10, yy, 'H', 11, 6)
    for nm, (ax, ay) in (('cow', (px0 + 1, py0 + 2)), ('cow_l', (px0 + 5, py0 + 1)), ('horse', (px0 + 7, py0 + 3))):
        c0, r0, w3, _ = SPR[nm]
        for d in range(w3):
            put('Foreground', ax + d, ay, 'P', c0 + d, r0)
    for hx in (x + 12, x + 14, x + 15):
        put('AboveSprites', hx, y + 7, 'E', 12, 4); put('Foreground', hx, y + 8, 'E', 12, 5)
    mark(x - 1, y - 5, x + 17, y + 12)


FARMS = [(6, 4, 0), (112, 6, 1), (10, 82, 2), (114, 80, 3), (90, 84, 4)]
for (fx, fy, i) in FARMS:
    farmstead(fx, fy + 4, HOUSES[i % len(HOUSES)])
for (fx, fy, i) in FARMS:                                # farm tracks to the nearest main road
    ty = fy + 9
    if fx < OX:
        road(fx + 16, ty, OX + 32, ty) if ty < OY else road(fx + 16, ty, OX + 32, ty)
    else:
        road(OX + 36, ty, fx, ty)

# ---- patchwork fields with hedgerows ----
CROPS = [(9, 9), (10, 9), (6, 10)]
def field(x0, y0, x1, y1):
    kind = rnd.choice(['wheat', 'wheat', 'tilled', 'pasture', 'orchard', 'tilled', 'meadow'])
    if kind == 'wheat':
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                c = 13 if x == x0 else 15 if x == x1 else 14
                r = 4 if y == y0 else 6 if y == y1 else 5
                put('Ground', x, y, 'E', c, r)
        for _ in range(rnd.randint(0, 3)):
            hx, hy = rnd.randint(x0 + 1, x1 - 1), rnd.randint(y0 + 1, y1 - 1)
            put('AboveSprites', hx, hy - 1, 'E', 12, 4); put('Foreground', hx, hy, 'E', 12, 5)
    elif kind == 'tilled':
        vrect(TILL, x0, y0, x1 + 1, y1 + 1)
        k = rnd.choice(CROPS)
        for y in range(y0 + 1, y1, 2):
            for x in range(x0 + 1, x1):
                put('Ground', x, y, 'N', *k)
    elif kind == 'orchard':
        c = rnd.choice((3, 9, 0, 3))
        for y in range(y0, y1 - 2, 4):
            for x in range(x0, x1 - 2, 4):
                stamp('N', c, 18, 3, 3, x, y, above=2)
    elif kind == 'pasture':
        for _ in range(rnd.randint(2, 4)):
            nm = rnd.choice(['cow', 'cow_l', 'horse', 'horse_l', 'pig'])
            c0, r0, w, _ = SPR[nm]
            ax, ay = rnd.randint(x0 + 1, x1 - 3), rnd.randint(y0 + 1, y1 - 1)
            for d in range(w):
                put('Foreground', ax + d, ay, 'P', c0 + d, r0)
    else:
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                if rnd.random() < 0.18:
                    put('Ground', x, y, 'N', rnd.choice((0, 1, 3, 6)), 11)


HC, HR = SPR['hedge'][0], SPR['hedge'][1]
def hedge_h(x0, x1, y):
    for x in range(x0, x1 + 1):
        c = 0 if x == x0 else 2 if x == x1 else 1
        put('Foreground', x, y, 'P', HC + c, HR); put('Foreground', x, y + 1, 'P', HC + c, HR + 2)
def hedge_v(x, y0, y1):
    for y in range(y0, y1 + 1):
        r = 0 if y == y0 else 2 if y == y1 else 1
        put('Foreground', x, y, 'P', HC, HR + r); put('Foreground', x + 1, y, 'P', HC + 2, HR + r)


for by in range(0, CH, 11):
    for bx in range(0, CW, 13):
        x0, y0 = bx + rnd.randint(0, 2), by + rnd.randint(0, 2)
        x1, y1 = bx + 12, by + 10
        x1, y1 = min(x1, CW - 1), min(y1, CH - 1)
        if x1 - x0 < 6 or y1 - y0 < 5:
            continue
        # shrink until free
        if not free(x0, y0, x1, y1):
            ok = False
            for sx in range(0, 4):
                for sy in range(0, 4):
                    if free(x0 + sx, y0 + sy, x1 - sx, y1 - sy) and x1 - x0 - 2 * sx >= 6 and y1 - y0 - 2 * sy >= 5:
                        x0, y0, x1, y1 = x0 + sx, y0 + sy, x1 - sx, y1 - sy
                        ok = True
                        break
                if ok:
                    break
            if not ok:
                continue
        field(x0, y0, x1 - 2, y1 - 2)
        if rnd.random() < 0.8:
            hedge_h(x0, x1 - 2, y1 - 1)
        if rnd.random() < 0.7:
            hedge_v(x1 - 1, y0, y1 - 2)
        if rnd.random() < 0.4:
            stamp('N', rnd.choice((0, 16, 18)), 0, 2, 2, x1 - 1, y1 - 1, above=1)
        mark(x0, y0, x1, y1)

# lone trees in the gaps
for _ in range(220):
    x, y = rnd.randint(0, CW - 2), rnd.randint(0, CH - 2)
    if free(x, y, x + 1, y + 1):
        stamp('N', rnd.choice((0, 16, 18, 0)), 0, 2, 2, x, y, above=1)
        mark(x, y, x + 1, y + 1)

# ---- background autotile (outside the town) ----
for y in range(CH):
    for x in range(CW):
        done = False
        for grid, base in ((DIRT, ('F', 0)), (TILL, ('P', SPR['tilled'][0]))):
            s = (grid[y][x], grid[y][x + 1], grid[y + 1][x], grid[y + 1][x + 1])
            if s != (0, 0, 0, 0):
                c, r = FLOOR_SIG[s]
                if base[0] == 'F':
                    put('Background', x, y, 'F', c, r)
                else:
                    put('Background', x, y, 'P', base[1] + c, SPR['tilled'][1] + r - 7)
                done = True
                break
        if not done:
            put('Background', x, y, 'F', rnd.choice((1, 2, 3, 4)) if rnd.random() < 0.1 else 0, 12)
            if not L['Ground'][y][x] and not L['Foreground'][y][x] and rnd.random() < 0.05:
                put('Ground', x, y, 'D', rnd.choice((0, 1, 2, 3)), 2)

# ---- paste the town ----
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
open(OUT + 'plains_context.tmx', 'w', encoding='utf-8', newline='\n').write('\n'.join(lines))
print('context written')
