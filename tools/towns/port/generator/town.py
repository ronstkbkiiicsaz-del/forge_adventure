"""Port town (70x50) for Shandalar Ascendant: a harbour town on an island's sheltered bay (blue biome).

Built on the plains market town generator (same stamp/collision helpers and writers), with a sea autotile
(sand shore + deep water), piers, a quay wall and terrace walls added.
Writes:  <OUT>/port_town.tmx
         <OUT>/tileset/ninja/*.tsx|png   (copies of the repo Ninja tilesets + per-tile collision added here,
                                           plus the generated ninja_PortFloor / ninja_PortWater / ninja_PortExtras)
Run:     python town.py [OUT]     (default A:/GameAssets/towns/port/)
"""
import re, os, sys, random, shutil
import xml.etree.ElementTree as ET

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import port_extras

REPO_TS = 'A:/forge_wave3/forge-gui/res/adventure/common/maps/tileset/ninja/'   # read-only source
OUTDIR = (sys.argv[1] if len(sys.argv) > 1 else 'A:/GameAssets/towns/port/').rstrip('/') + '/'
TSDIR = OUTDIR + 'tileset/ninja/'
OUT = OUTDIR + 'port_town.tmx'
W, H = 70, 50
random.seed(41)
os.makedirs(TSDIR, exist_ok=True)


def tsx_header(name, w, h):
    return ('<?xml version="1.0" encoding="UTF-8"?>\n'
            '<tileset version="1.10" tiledversion="1.10.1" name="%s" tilewidth="16" tileheight="16" tilecount="%d" columns="%d">\n'
            ' <image source="%s.png" width="%d" height="%d"/>\n</tileset>\n' % (name, (w // 16) * (h // 16), w // 16, name, w, h))


# ---- tilesets: copy repo ones, generate ours ----------------------------------
TS_ORDER = [('F', 'ninja_PortFloor'), ('W', 'ninja_PortWater'), ('N', 'ninja_TilesetNature'),
            ('H', 'ninja_TilesetHouse'), ('E', 'ninja_TilesetElement'), ('D', 'ninja_TilesetFloorDetail'),
            ('I', 'ninja_TilesetInteriorFloor'), ('S', 'ninja_TilesetDesert'), ('P', 'ninja_PortExtras')]
GENERATED = ('ninja_PortFloor', 'ninja_PortWater', 'ninja_PortExtras')
SPR = port_extras.build(TSDIR)
from PIL import Image
for name in GENERATED:
    im = Image.open(TSDIR + name + '.png')
    open(TSDIR + name + '.tsx', 'w', encoding='utf-8', newline='\n').write(tsx_header(name, *im.size))
for key, name in TS_ORDER:
    if name not in GENERATED:
        shutil.copyfile(REPO_TS + name + '.png', TSDIR + name + '.png')
        shutil.copyfile(REPO_TS + name + '.tsx', TSDIR + name + '.tsx')
shutil.copyfile(REPO_TS + 'LICENSE-ninja-adventure.txt', TSDIR + 'LICENSE-ninja-adventure.txt')

TS = {}
gid = 1
BASECOLL = {}
for key, name in TS_ORDER:
    txt = open(TSDIR + name + '.tsx', encoding='utf-8').read()
    cnt = int(re.search(r'tilecount="(\d+)"', txt).group(1))
    cols = int(re.search(r'columns="(\d+)"', txt).group(1))
    TS[key] = dict(name=name, first=gid, count=cnt, cols=cols)
    gid += cnt
    root = ET.fromstring(txt)
    for t in root.findall('tile'):
        og = t.find('objectgroup')
        if og is not None:
            BASECOLL[(key, int(t.get('id')))] = [tuple(float(o.get(k, 0)) for k in ('x', 'y', 'width', 'height'))
                                                 for o in og.findall('object')]


def tid(ts, c, r):
    return r * TS[ts]['cols'] + c


LAYERS = ['Background', 'Ground', 'Foreground', 'AboveSprites']
L = {n: [[None] * W for _ in range(H)] for n in LAYERS}
COLL = {}
CONFLICTS = []
FULL = [(0, 0, 16, 16)]


def coll(ts, c, r, rects):
    k = (ts, tid(ts, c, r))
    rects = [tuple(float(v) for v in x) for x in rects]
    if k in COLL and COLL[k] != rects:
        raise Exception('collision conflict %s %s vs %s' % (k, rects, COLL[k]))
    if k in BASECOLL and BASECOLL[k] != rects:
        CONFLICTS.append((ts, c, r))     # the shared tileset already says otherwise (Havenbrook): keep theirs
        return
    COLL[k] = rects


def put(layer, x, y, ts, c, r):
    if 0 <= x < W and 0 <= y < H:
        L[layer][y][x] = (ts, c, r)


def stamp(ts, c0, r0, w, h, x, y, above=0, layer='Foreground'):
    for dy in range(h):
        for dx in range(w):
            put('AboveSprites' if dy < above else layer, x + dx, y + dy, ts, c0 + dx, r0 + dy)


def pstamp(name, x, y, above=0, layer='Foreground', solid=True, skip=()):
    """stamp a PlainsExtras sprite; rows >= above are solid (unless solid=False)."""
    c0, r0, w, h = SPR[name]
    stamp('P', c0, r0, w, h, x, y, above, layer)
    if solid:
        for dy in range(above, h):
            for dx in range(w):
                if (dx, dy) not in skip:
                    coll('P', c0 + dx, r0 + dy, FULL)


# ---- autotile -------------------------------------------------------------
FLOOR_SIG = {(0,0,0,1):(0,7),(0,0,1,1):(1,7),(0,0,1,0):(2,7),(0,1,0,1):(0,8),(1,1,1,1):(1,8),
             (1,0,1,0):(2,8),(0,1,0,0):(0,9),(1,1,0,0):(1,9),(1,0,0,0):(2,9),
             (0,1,1,1):(6,9),(1,0,1,1):(5,9),(1,1,0,1):(6,8),(1,1,1,0):(5,8),
             (0,1,1,0):(9,8),(1,0,0,1):(9,7)}
WATER_SIG = {(0,0,0,1):(0,6),(0,0,1,1):(1,6),(0,0,1,0):(2,6),(0,1,0,1):(0,7),(1,1,1,1):(1,7),
             (1,0,1,0):(2,7),(0,1,0,0):(0,8),(1,1,0,0):(1,8),(1,0,0,0):(2,8),
             (0,1,1,1):(6,8),(1,0,1,1):(5,8),(1,1,0,1):(6,7),(1,1,1,0):(5,7),
             (0,1,1,0):(9,7),(1,0,0,1):(9,6)}
WATER = [[0] * (W + 1) for _ in range(H + 1)]     # vertex grids
SAND = [[0] * (W + 1) for _ in range(H + 1)]
TILL = [[0] * (W + 1) for _ in range(H + 1)]
BG_OVERRIDE = {}                                     # (x, y) -> tile drawn on Background instead of the autotile


def vrect(grid, x0, y0, x1, y1, v=1):
    for y in range(max(0, y0), min(H, y1) + 1):
        for x in range(max(0, x0), min(W, x1) + 1):
            grid[y][x] = v


def water(x0, y0, x1, y1):
    """tiles x0..x1, y0..y1 become full water (the shore autotile forms around them)."""
    vrect(WATER, x0, y0, x1 + 1, y1 + 1)


def road(x0, y0, x1, y1):
    """sand path covering full tiles x0..x1, y0..y1 (inclusive); clipped at the map edge it runs off-map."""
    vrect(SAND, x0, y0, x1 + 1, y1 + 1)


sand = road


def tilled(x0, y0, x1, y1):
    vrect(TILL, x0, y0, x1 + 1, y1 + 1)


GRASS_PLAIN = ('F', 0, 12)
GRASS_TUFT = [('F', 1, 12), ('F', 2, 12), ('F', 3, 12), ('F', 4, 12)]
SAND_VARIANT = [('F', 0, 11), ('F', 1, 11)]
TC, TR = SPR['tilled'][0], SPR['tilled'][1]
DEEP_SIG = SPR['deep_sig']
FULLW = [[False] * W for _ in range(H)]
DEEPT = [[False] * W for _ in range(H)]


def autotile():
    # beaches: every vertex within 1 of water is sand, so no grass ever touches the sea
    wv = [row[:] for row in WATER]
    for y in range(H + 1):
        for x in range(W + 1):
            if wv[y][x]:
                for dy in (-1, 0, 1):
                    for dx in (-1, 0, 1):
                        if 0 <= y + dy <= H and 0 <= x + dx <= W:
                            SAND[y + dy][x + dx] = 1
    for y in range(H):
        for x in range(W):
            FULLW[y][x] = WATER[y][x] and WATER[y][x + 1] and WATER[y + 1][x] and WATER[y + 1][x + 1]
    # distance (in tiles, chebyshev) from each full-water tile to the nearest tile that is not full water
    INF = 99
    dist = [[0 if not FULLW[y][x] else INF for x in range(W)] for y in range(H)]
    for _ in range(6):
        for y in range(H):
            for x in range(W):
                if dist[y][x]:
                    m = dist[y][x]
                    for dy in (-1, 0, 1):
                        for dx in (-1, 0, 1):
                            yy, xx = y + dy, x + dx
                            if 0 <= yy < H and 0 <= xx < W:
                                m = min(m, dist[yy][xx] + 1)
                    dist[y][x] = m
    deepv = [[0] * (W + 1) for _ in range(H + 1)]
    for y in range(H + 1):
        for x in range(W + 1):
            ts = [(x - 1, y - 1), (x, y - 1), (x - 1, y), (x, y)]
            ok = True
            for (tx, ty) in ts:
                tx2, ty2 = min(max(tx, 0), W - 1), min(max(ty, 0), H - 1)
                if dist[ty2][tx2] < DEEP_AT:
                    ok = False
            deepv[y][x] = 1 if ok else 0
    for y in range(H):
        for x in range(W):
            if (x, y) in BG_OVERRIDE:
                put('Background', x, y, *BG_OVERRIDE[(x, y)])
                continue
            s = (WATER[y][x], WATER[y][x + 1], WATER[y + 1][x], WATER[y + 1][x + 1])
            if s != (0, 0, 0, 0):
                if s == (1, 1, 1, 1):
                    d = (deepv[y][x], deepv[y][x + 1], deepv[y + 1][x], deepv[y + 1][x + 1])
                    if d == (0, 0, 0, 0):
                        if random.random() < 0.04:
                            put('Background', x, y, 'P', *SPR['shallow_ripple'][:2])
                        else:
                            put('Background', x, y, 'W', 1, 1)
                    else:
                        if d == (1, 1, 1, 1):
                            DEEPT[y][x] = True
                        if d == (1, 1, 1, 1) and random.random() < 0.06:
                            put('Background', x, y, 'P', *SPR['deep_ripple'][:2])
                        else:
                            put('Background', x, y, 'P', *DEEP_SIG[d])
                else:
                    c, r = WATER_SIG[s]
                    put('Background', x, y, 'W', c, r - 6)
                continue
            s = (SAND[y][x], SAND[y][x + 1], SAND[y + 1][x], SAND[y + 1][x + 1])
            t = (TILL[y][x], TILL[y][x + 1], TILL[y + 1][x], TILL[y + 1][x + 1])
            if t != (0, 0, 0, 0) and s == (0, 0, 0, 0):
                c, r = FLOOR_SIG[t]
                put('Background', x, y, 'P', TC + c, TR + r - 7)
            elif s != (0, 0, 0, 0):
                c, r = FLOOR_SIG[s]
                if s == (1, 1, 1, 1) and random.random() < 0.05:
                    put('Background', x, y, *random.choice(SAND_VARIANT))
                else:
                    put('Background', x, y, 'F', c, r)
            else:
                g = random.choice(GRASS_TUFT) if random.random() < 0.10 else GRASS_PLAIN
                put('Background', x, y, *g)
    # water collision: shore tiles by quarter, full water solid
    for s, (c, r) in WATER_SIG.items():
        q = [(0, 0), (8, 0), (0, 8), (8, 8)]
        coll('W', c, r - 6, [(q[i][0], q[i][1], 8, 8) for i, on in enumerate(s) if on])
    coll('P', *SPR['shallow_ripple'][:2], FULL)
    coll('P', *SPR['deep_ripple'][:2], FULL)
    for k, (c, r) in DEEP_SIG.items():
        coll('P', c, r, FULL)


DEEP_AT = 3


# ---- objects ----------------------------------------------------------------
OBJECTS = []


def obj(otype, name, x, y, w, h, props=()):
    """pixel-space rectangle object with a type and string properties."""
    s = '<object id="ID" name="%s" type="%s" x="%g" y="%g" width="%g" height="%g"' % (name, otype, x, y, w, h)
    if props:
        s += '>\n   <properties>\n' + '\n'.join(
            '    <property name="%s" value="%s"/>' % kv for kv in props) + '\n   </properties>\n  </object>'
    else:
        s += '/>'
    OBJECTS.append(s)


def tobj(otype, name, tx, ty, tw=1, th=1, props=()):
    obj(otype, name, tx * 16, ty * 16, tw * 16, th * 16, props)


DOORS = []


def door(interior, name, x, y):
    DOORS.append((interior, name, x, y))
    tobj('door', name, x, y, props=[('interior', interior)])


# ---- prefabs --------------------------------------------------------------
HOUSE_COUNT = [0]
HT = {   # name -> (tileset, c0, r0, w, h, door offset, closed-door overlay tile or None)
    'orange': ('H', 0, 0, 4, 3, (1, 2), (2, 3)),
    'beige': ('H', 4, 0, 4, 3, (1, 2), (8, 3)),
    'orange2': ('H', 8, 0, 4, 3, (1, 2), (2, 3)),
    'red': ('H', 12, 0, 4, 3, (1, 2), (2, 3)),
    'gable': ('H', 26, 0, 3, 3, (1, 2), (9, 3)),
    'round': ('H', 23, 0, 3, 3, (1, 2), (8, 3)),
    'smallwood': ('H', 19, 19, 3, 3, (1, 2), (26, 19)),
    'hut': ('H', 0, 7, 3, 3, (1, 2), None),
    'thatchhut': ('H', 3, 7, 3, 3, (1, 2), None),
}
for base, dt in (('orange', (2, 3)), ('beige', (8, 3)), ('orange2', (2, 3)), ('red', (2, 3))):
    for hue in ('blue', 'teal', 'slate', 'navy'):
        c0, r0, w, h = SPR['%s_%s' % (base, hue)]
        HT['%s_%s' % (base, hue)] = ('P', c0, r0, w, h, (1, 2), dt)
for nm, dt in (('white_blue', (2, 3)), ('white_teal', (2, 3)), ('white_navy', (2, 3)), ('white_slate', (8, 3)),
               ('gable_slate', (9, 3)), ('round_white', (8, 3))):
    c0, r0, w, h = SPR[nm]
    HT[nm] = ('P', c0, r0, w, h, (1, 2), dt)


def building(ts, c0, r0, w, h, x, y, above, doors=(), overlay=None, count=False):
    """stamp a building: rows < above are roof (AboveSprites), the rest solid. doors = offsets that stay
    dark doorways (solid). overlay = closed-door tile from House drawn over the first door."""
    if count:
        HOUSE_COUNT[0] += 1
    stamp(ts, c0, r0, w, h, x, y, above)
    for dy in range(above, h):
        for dx in range(w):
            coll(ts, c0 + dx, r0 + dy, FULL)
    if overlay and doors:
        dx, dy = doors[0]
        put('Ground', x + dx, y + dy, ts, c0 + dx, r0 + dy)
        put('Foreground', x + dx, y + dy, 'H', *overlay)
        coll('H', overlay[0], overlay[1], FULL)


def home(name, x, y, kind, step=True, closed=True):
    ts, c0, r0, w, h, d, dt = HT[kind]
    building(ts, c0, r0, w, h, x, y, 1, doors=[d], overlay=dt if closed else None, count=True)
    door('home', name, x + d[0], y + d[1])
    if step:
        road(x + d[0], y + d[1] + 1, x + d[0], y + d[1] + 1)
    return (x + d[0], y + d[1])


def cottage(name, x, y, kind, lane_y, fence=True, flowers=True, closed=True):
    """home with a front yard down to a lane at row lane_y: dirt path from the door, picket fence with a gate,
    flowers in the yard. The fence is skipped when the yard is less than 2 rows deep."""
    ts, c0, r0, w, h, d, dt = HT[kind]
    dx_, dy_ = home(name, x, y, kind, step=False, closed=closed)
    road(dx_, dy_ + 1, dx_, lane_y - 1)
    y0, yf = y + h, lane_y - 2              # yard rows y0..lane_y-2; picket fence on row yf
    fenced = fence and yf - y0 >= 1
    if fenced:
        xs = [xx for xx in range(x - 1, x + w + 1) if 0 <= xx < W]
        for xx in xs:
            if xx == dx_:
                continue
            left_end = xx == xs[0] or xx - 1 == dx_
            right_end = xx == xs[-1] or xx + 1 == dx_
            if left_end and right_end:
                continue
            fpiece(xx, yf, 'ER' if left_end else 'EL' if right_end else 'H')
    y1 = yf if fenced else lane_y - 1
    if flowers:
        for yy in range(y0, y1):
            for xx in range(x, x + w):
                if xx != dx_ and random.random() < 0.55:
                    YARD.append((xx, yy))
    return dx_, dy_


YARD = []


def tree(x, y, kind=(0, 0)):
    c, r = kind
    stamp('N', c, r, 2, 2, x, y, above=1)
    coll('N', c, r + 1, [(0, 0, 16, 12)])
    coll('N', c + 1, r + 1, [(0, 0, 16, 12)])


def big_tree(x, y, c):  # 3x3 fruit/blossom tree, Nature rows 18-20
    stamp('N', c, 18, 3, 3, x, y, above=2)
    coll('N', c + 1, 20, [(3, 0, 10, 12)])
    coll('N', c, 20, [])
    coll('N', c + 2, 20, [])


def prop(x, y, ts, c, r, rects=((2, 4, 12, 12),), layer='Foreground'):
    put(layer, x, y, ts, c, r)
    coll(ts, c, r, list(rects))


def deco(x, y, ts, c, r):
    put('Ground', x, y, ts, c, r)


FENCE = dict(TL=(8, 6), TR=(9, 6), BL=(8, 7), BR=(9, 7), H=(11, 7), V=(11, 6), EL=(10, 7), ER=(10, 6))
FENCE_COLL = dict(TL=[(5, 3, 11, 13)], TR=[(0, 3, 11, 13)], BL=[(5, 0, 11, 12)], BR=[(0, 0, 11, 12)],
                  H=[(0, 3, 16, 9)], V=[(5, 0, 6, 16)], EL=[(0, 3, 9, 9)], ER=[(7, 3, 9, 9)])


def fpiece(x, y, k):
    c, r = FENCE[k]
    put('Foreground', x, y, 'H', c, r)
    coll('H', c, r, FENCE_COLL[k])


def fence_box(x0, y0, x1, y1, gaps=(), bgaps=(), lgaps=(), rgaps=()):
    for x in range(x0, x1 + 1):
        for y, gs in ((y0, gaps), (y1, bgaps)):
            if x == x0:
                k = 'TL' if y == y0 else 'BL'
            elif x == x1:
                k = 'TR' if y == y0 else 'BR'
            else:
                k = 'H'
            if x in gs:
                continue
            if k == 'H' and (x + 1) in gs:
                k = 'EL'
            if k == 'H' and (x - 1) in gs:
                k = 'ER'
            fpiece(x, y, k)
    for y in range(y0 + 1, y1):
        if y not in lgaps:
            fpiece(x0, y, 'V')
        if y not in rgaps:
            fpiece(x1, y, 'V')


def fence_row(x0, x1, y):
    for x in range(x0, x1 + 1):
        fpiece(x, y, 'EL' if x == x1 else 'ER' if x == x0 else 'H')


IC = [(1, 13)]


def cobble(x0, y0, x1, y1):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            cc = 0 if x == x0 else 2 if x == x1 else 1
            rr = 12 if y == y0 else 14 if y == y1 else 13
            t = (cc, rr) if (cc, rr) != (1, 13) else random.choice(IC)
            put('Ground', x, y, 'I', *t)


def wheat(x0, y0, x1, y1):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            c = 13 if x == x0 else 15 if x == x1 else 14
            r = 4 if y == y0 else 6 if y == y1 else 5
            put('Ground', x, y, 'E', c, r)


def crops(x0, y0, x1, y1, kinds):
    """tilled patch with crop rows on alternate rows."""
    tilled(x0, y0, x1, y1)
    for i, y in enumerate(range(y0 + 1, y1, 2)):
        k = kinds[i % len(kinds)]
        for x in range(x0 + 1, x1):
            put('Ground', x, y, 'N', *k)


def hay(x, y):
    put('AboveSprites', x, y - 1, 'E', 12, 4)
    prop(x, y, 'E', 12, 5, [(1, 2, 14, 12)])


def animal(name, x, y):
    c0, r0, w, h = SPR[name]
    for dx in range(w):
        put('Foreground', x + dx, y, 'P', c0 + dx, r0)
        coll('P', c0 + dx, r0, [(0, 6, 16, 10)] if w == 2 else [(3, 6, 10, 10)])


def lamp(x, y):
    put('AboveSprites', x, y, 'E', 6, 3)
    prop(x, y + 1, 'E', 6, 4, [(5, 2, 6, 14)])


def stall(name, x, y):
    c0, r0, w, h = SPR[name]
    stamp('P', c0, r0, 2, 2, x, y, above=1)
    coll('P', c0, r0 + 1, [(1, 2, 15, 12)])
    coll('P', c0 + 1, r0 + 1, [(0, 2, 15, 12)])


def sign(name, x, y, layer='AboveSprites'):
    c0, r0, w, h = SPR[name]
    for dx in range(w):
        put(layer, x + dx, y, 'P', c0 + dx, r0)


def sign_on_posts(name, x, y):
    """board on row y (above), two posts on row y+1 (solid at the posts)."""
    sign(name, x, y)
    c0, r0, w, h = SPR['sign_posts']
    put('Foreground', x, y + 1, 'P', c0, r0)
    put('Foreground', x + 1, y + 1, 'P', c0 + 1, r0)
    coll('P', c0, r0, [(4, 0, 5, 14)])
    coll('P', c0 + 1, r0, [(8, 0, 5, 14)])


def barrel(x, y):
    prop(x, y, 'E', 1, 0, [(2, 3, 12, 13)])


def crate(x, y):
    prop(x, y, 'E', 0, 0, [(2, 3, 12, 13)])


def sack(x, y, k=0):
    c, r = [(8, 2), (1, 1), (2, 1)][k % 3]
    prop(x, y, 'E', c, r, [(2, 4, 12, 12)])


exec(open(os.path.join(HERE, 'port_layout.py'), encoding='utf-8').read())


# ---- writers ---------------------------------------------------------------
def write_tsx():
    allc = dict(COLL)
    allc.update(BASECOLL)             # repo collision wins on any conflict
    byts = {}
    for (ts, i), rects in allc.items():
        byts.setdefault(ts, {})[i] = rects
    for ts in TS:
        tiles = byts.get(ts, {})
        path = TSDIR + TS[ts]['name'] + '.tsx'
        txt = open(path, encoding='utf-8').read()
        head = txt[:txt.index('/>', txt.index('<image')) + 2]
        out = [head]
        for i in sorted(tiles):
            rects = tiles[i]
            if not rects:
                continue
            out.append(' <tile id="%d">' % i)
            out.append('  <objectgroup draworder="index" id="2">')
            for n, (x, y, w, h) in enumerate(rects, 1):
                out.append('   <object id="%d" x="%g" y="%g" width="%g" height="%g"/>' % (n, x, y, w, h))
            out.append('  </objectgroup>')
            out.append(' </tile>')
        out.append('</tileset>\n')
        open(path, 'w', encoding='utf-8', newline='\n').write('\n'.join(out))


def write_tmx():
    lines = ['<?xml version="1.0" encoding="UTF-8"?>',
             '<map version="1.10" tiledversion="1.10.1" orientation="orthogonal" renderorder="right-down" '
             'width="%d" height="%d" tilewidth="16" tileheight="16" infinite="0" nextlayerid="6" nextobjectid="%d">'
             % (W, H, len(OBJECTS) + 1),
             ' <properties>',
             '  <property name="biome" value="islands"/>',
             '  <property name="townKind" value="port_town"/>',
             '  <property name="seamless" type="bool" value="true"/>',
             ' </properties>']
    for key, name in TS_ORDER:
        lines.append(' <tileset firstgid="%d" source="tileset/ninja/%s.tsx"/>' % (TS[key]['first'], name))
    for n, lname in enumerate(LAYERS, 1):
        lines.append(' <layer id="%d" name="%s" width="%d" height="%d">' % (n, lname, W, H))
        if lname == 'Foreground':
            lines += ['  <properties>', '   <property name="spriteLayer" type="bool" value="true"/>', '  </properties>']
        lines.append('  <data encoding="csv">')
        rows = []
        for y in range(H):
            vals = []
            for x in range(W):
                t = L[lname][y][x]
                vals.append(str(TS[t[0]]['first'] + tid(*t)) if t else '0')
            rows.append(','.join(vals))
        lines.append(',\n'.join(rows))
        lines.append('</data>')
        lines.append(' </layer>')
    lines.append(' <objectgroup id="5" name="Objects">')
    for i, o in enumerate(OBJECTS, 1):
        lines.append('  ' + o.replace('ID', str(i), 1))
    lines.append(' </objectgroup>')
    lines.append('</map>\n')
    open(OUT, 'w', encoding='utf-8', newline='\n').write('\n'.join(lines))


write_tsx()
write_tmx()
import json
json.dump({'water': WATER, 'sand': SAND}, open(os.path.join(HERE, '_town_grids.json'), 'w'))
print('ok homes=%d doors=%d objects=%d' % (HOUSE_COUNT[0], len(DOORS), len(OBJECTS)))
if CONFLICTS:
    print('kept repo collision for', sorted(set(CONFLICTS)))
