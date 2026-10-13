# ===================== PORT TOWN LAYOUT (exec'd by town.py) =====================
# A harbour town on an island's sheltered bay. Sea to the south and east; the town climbs a hill to the north in
# three levels separated by two CURVED, stepped terrace walls (W1 lower, W2 upper):
#   lower town   below W1 (sand; stone quay broken by boardwalk, quay wall y33, harbour basin below)
#   middle tier  between W1 and W2, narrow winding lane LM, cottages in staggered clusters
#   upper tier   above W2, lane LU, chapel of the sea
# Main road (2 wide) runs inland off the north edge, with stone steps through both walls; alleys with steps
# between the cottage clusters (x9, x18, x48) and steps at the coast path (x4-5), boatyard (x13), headland (x56).

def pp(name, x, y, rects=((2, 4, 12, 12),), layer='Foreground'):
    c, r = SPR[name][:2]
    prop(x, y, 'P', c, r, rects)


def pdeco(name, x, y, layer='Foreground'):
    c, r = SPR[name][:2]
    put(layer, x, y, 'P', c, r)
    coll('P', c, r, [])


def sprite(name, x, y, above, solid_rows=None, rects=None, layer='Foreground'):
    """stamp a PortExtras sprite: rows < above go to AboveSprites; solid_rows (default all the rest) get FULL
    collision (or `rects`)."""
    c0, r0, w, h = SPR[name]
    stamp('P', c0, r0, w, h, x, y, above, layer)
    rows = range(above, h) if solid_rows is None else solid_rows
    for dy in range(h):
        for dx in range(w):
            if dy in rows:
                coll('P', c0 + dx, r0 + dy, list(rects) if rects else FULL)
            elif dy >= above:
                coll('P', c0 + dx, r0 + dy, [])


TERR = SPR['terrace']
STAIRS = SPR['stairs'][:2]


def terrace_wall(x0, x1, y, stairs=()):
    """retaining wall: a row of grass-bank/stone-face tiles (solid) with stone steps (walkable) at `stairs`."""
    for x in range(x0, x1 + 1):
        if x in stairs:
            put('Ground', x, y, 'P', *STAIRS)
            continue
        cc = 1 if x == x0 else 3 if x == x1 else 2
        put('Ground', x, y, 'P', TERR[0] + cc, TERR[1] + 2)
        coll('P', TERR[0] + cc, TERR[1] + 2, FULL)
    coll('P', *STAIRS, [])


def paving(x0, y0, x1, y1):
    pc, pr = SPR['paving'][:2]
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            cc = 0 if x == x0 else 2 if x == x1 else 1
            rr = 0 if y == y0 else 2 if y == y1 else 1
            put('Ground', x, y, 'P', pc + cc, pr + rr)


PIERS = []          # (x, y, col, row) inside the 4x4 plank block, drawn after the autotile


def pier_v(x0, x1, y0, y1, open_top=True):
    """vertical pier / jetty x0..x1 (1-3 wide), rows y0..y1, posts on the last row."""
    for y in range(y0, y1 + 1):
        rr = 2 if y == y1 else (0 if (y == y0 and not open_top) else 1)
        for x in range(x0, x1 + 1):
            if x0 == x1:
                cc = 3
            else:
                cc = 0 if x == x0 else 2 if x == x1 else 1
            PIERS.append((x, y, cc, rr))


def draw_piers():
    for (x, y, cc, rr) in PIERS:
        base = 'pier_d' if DEEPT[y][x] else 'pier_s'
        c0, r0 = SPR[base][:2]
        put('Background', x, y, 'P', c0 + cc, r0 + rr)
        coll('P', c0 + cc, r0 + rr, [])


def palm(x, y, kind=0):
    c = 10 if kind == 0 else 12
    stamp('S', c, 10, 2, 2, x, y, above=1)
    coll('S', c, 11, [(8, 0, 8, 12)])
    coll('S', c + 1, 11, [(0, 0, 8, 12)])


def rock_big(x, y, grey=True):
    r = 14 if grey else 10
    stamp('N', 15, r, 2, 2, x, y, above=1)
    coll('N', 15, r + 1, [(1, 0, 15, 12)]); coll('N', 16, r + 1, [(0, 0, 15, 12)])


def rock_small(x, y, k=0, grey=True):
    prop(x, y, 'N', 17 + k % 3, 17 if grey else 13, [(2, 4, 12, 10)])




def interp(points, x):
    """piecewise-linear y(x) through (x, y) control points, rounded to a tile row."""
    if x <= points[0][0]:
        return points[0][1]
    for (xa, ya), (xb, yb) in zip(points, points[1:]):
        if xa <= x <= xb:
            return int(round(ya + (yb - ya) * (x - xa) / float(xb - xa) + 1e-6))
    return points[-1][1]


def wall_curve(points, x0, x1, stairs=()):
    """a terrace wall following a curve: runs of the same row become wall segments (end caps where the row
    steps), stone steps at `stairs`. Returns {x: row}."""
    rows = {x: interp(points, x) for x in range(x0, x1 + 1)}
    for x in range(x0, x1):
        assert abs(rows[x + 1] - rows[x]) <= 1, ('wall step too steep', x)
    xs = list(range(x0, x1 + 1))
    i = 0
    while i < len(xs):
        j = i
        while j + 1 < len(xs) and rows[xs[j + 1]] == rows[xs[i]]:
            j += 1
        terrace_wall(xs[i], xs[j], rows[xs[i]], stairs=[s for s in stairs if xs[i] <= s <= xs[j]])
        i = j + 1
    return rows


def lane(points, x0, x1, w=1):
    """a narrow sandy lane following a curve; where the row changes it bends with a short vertical joint."""
    prev = None
    for x in range(x0, x1 + 1):
        y = interp(points, x)
        road(x, y, x, y + w - 1)
        if prev is not None and prev != y:
            road(x, min(prev, y), x, max(prev, y) + w - 1)
        prev = y
    return {x: interp(points, x) for x in range(x0, x1 + 1)}


def mask_frame(tiles, place):
    """draw a 3x3-frame tile set over an arbitrary tile mask: edges where the neighbour is outside the mask."""
    for (x, y) in tiles:
        cc = 0 if (x - 1, y) not in tiles else 2 if (x + 1, y) not in tiles else 1
        rr = 0 if (x, y - 1) not in tiles else 2 if (x, y + 1) not in tiles else 1
        place(x, y, cc, rr)


def paving_mask(tiles):
    pc, pr = SPR['paving'][:2]
    mask_frame(tiles, lambda x, y, cc, rr: put('Ground', x, y, 'P', pc + cc, pr + rr))


def deck_mask(tiles):
    """wooden boardwalk on land (TilesetWater planks, walkable)."""
    mask_frame(tiles, lambda x, y, cc, rr: put('Ground', x, y, 'W', cc, 12 + rr))


def rect(x0, y0, x1, y1):
    return {(x, y) for y in range(y0, y1 + 1) for x in range(x0, x1 + 1)}


def house_to_lane(name, x, y, kind, lanes, fence=False, flowers=True):
    """a cottage whose front path runs down to the lane row below its door."""
    ts, c0, r0, w, h, d, dt = HT[kind]
    ly = lanes[x + d[0]]
    return cottage(name, x, y, kind, ly, fence=fence, flowers=flowers)


def alley(x, y0, y1):
    road(x, y0, x, y1)


# =============================== terrain ===============================
# ---------- sea: harbour basin with an irregular west shore, open sea S and E, a rocky point ----------
water(17, 34, 55, 35)                                   # under the quay wall
for y, (xw, xe) in {36: (17, 55), 37: (16, 54), 38: (15, 55), 39: (16, 55), 40: (16, 54), 41: (15, 55),
                    42: (14, 55), 43: (12, 55)}.items():
    water(xw, y, xe, y)
for y, xw in {44: 11, 45: 10, 46: 10, 47: 9, 48: 10, 49: 10}.items():   # open sea along the south edge
    water(xw, y, 69, y)
for y, (hl, hr) in {38: (55, 64), 39: (56, 63), 40: (57, 63), 41: (58, 62), 42: (64, 64), 43: (64, 64)}.items():
    water(56, y, hl, y)                                 # the point narrows to its tip
    water(hr, y, 64, y)
water(56, 42, 64, 43)
water(65, 0, 69, 49)                                    # open sea along the east edge
water(64, 11, 64, 18); water(63, 13, 63, 16)            # a small cove on the east beach
water(64, 27, 64, 37); water(63, 30, 63, 31); water(63, 35, 63, 37)

# ---------- the slope: two curved terrace walls (the hill rises north, bulging back around the basin) ----------
W1 = [(0, 26), (6, 25), (12, 23), (17, 21), (29, 20), (34, 20), (45, 21), (52, 21), (58, 22)]
W2 = [(0, 13), (8, 12), (16, 10), (24, 9), (30, 8), (34, 8), (42, 9), (50, 11), (58, 12)]
for x in range(0, 59):                                  # lower town is sand below the lower wall
    sand(x, interp(W1, x) + 1, x, 33)
sand(6, 34, 16, 43)
sand(59, 0, 64, 21)
road(4, 22, 5, H - 1)                                   # coast path south (west arm)
road(61, 0, 62, 21)                                     # coast path north (east beach)

# main road: 2 wide, bends at the well square, steps through both walls, down to the quay
road(30, 0, 31, 13); road(31, 13, 32, 28)
R1 = wall_curve(W1, 0, 58, stairs=(4, 5, 13, 31, 32, 56))
R2 = wall_curve(W2, 0, 58, stairs=(9, 19, 30, 31, 41))


def terrace_walk(R, x0, x1):
    """a 1-tile sandy walk along the top of a terrace wall; it bends where the wall steps."""
    lanes = {x: R[min(x, 58)] - 1 for x in range(x0, x1 + 1)}
    for x in range(x0, x1 + 1):
        road(x, lanes[x], x, lanes[x])
        if x < x1 and lanes[x + 1] < lanes[x]:
            road(x, lanes[x + 1], x, lanes[x + 1])
        if x < x1 and lanes[x + 1] > lanes[x]:
            road(x + 1, lanes[x], x + 1, lanes[x])
    return lanes


MID = terrace_walk(R1, 1, 60)
UP = terrace_walk(R2, 1, 60)
PLAZA = rect(32, 9, 36, 14) | rect(32, 15, 34, 15)
paving_mask(PLAZA)                                      # the well square is a small flagged square


def steps(x, y0, y1):
    """a stepped alley: stone steps laid on the grass between the cottages (walkable)."""
    for y in range(y0, y1 + 1):
        put('Ground', x, y, 'P', *STAIRS)
    coll('P', *STAIRS, [])


for x in (4, 5, 13, 56):                                # steps down to the lower town
    road(x, R1[x] + 1, x, R1[x] + 1)
for x in (9, 19, 41):                                   # alleys between the cottages, up through the upper wall
    steps(x, R2[x] + 1, MID[x] - 1)
    steps(x, UP[x] + 1, R2[x] - 1)

# ---------- the quay: stone slab broken by wooden boardwalk, narrower at its ends ----------
QFACE = 33
STONE = rect(17, 30, 55, 32) | rect(20, 29, 52, 29) | rect(25, 27, 39, 28) | rect(48, 27, 55, 28)
DECK = rect(17, 27, 23, 28) | rect(40, 27, 47, 28)
STONE -= DECK
paving_mask(STONE)
deck_mask(DECK)

# =============================== the harbour ===============================
pier_v(34, 36, QFACE, 41)
for x in range(30, 41):
    cc = 0 if x == 30 else 2 if x == 40 else 1
    PIERS.append((x, 42, cc, 1 if 34 <= x <= 36 else 0))
    PIERS.append((x, 43, cc, 2))
PIERS[:] = [p for p in PIERS if not (p[1] == 41 and 34 <= p[0] <= 36)]
for x in (34, 35, 36):
    PIERS.append((x, 41, 0 if x == 34 else 2 if x == 36 else 1, 1))
pier_v(35, 35, 44, 45)                                  # gangway
sprite('ferry', 32, 44, above=2, solid_rows=())
pp('bollard', 30, 43, [(5, 4, 6, 10)]); pp('bollard', 40, 43, [(5, 4, 6, 10)])
crate(39, 42); pp('rope', 31, 42, [(3, 5, 10, 8)]); pp('basket_fish', 33, 43)
tobj('station', 'ferry_pier', 30, 42, 11, 6, props=[('kind', 'dock'), ('label', 'Port ferry')])
sign_on_posts('sign_ferry', 37, 31)

pier_v(22, 23, QFACE, 41)
pdeco('rowboat', 20, 36); put('Foreground', 21, 36, 'P', SPR['rowboat'][0] + 1, SPR['rowboat'][1])
pdeco('rowboat_blue', 24, 39); put('Foreground', 25, 39, 'P', SPR['rowboat_blue'][0] + 1, SPR['rowboat_blue'][1])
coll('P', SPR['rowboat'][0] + 1, SPR['rowboat'][1], []); coll('P', SPR['rowboat_blue'][0] + 1, SPR['rowboat_blue'][1], [])
pp('lobster_pots', 22, 40); pp('basket_shrimp', 23, 35)

pier_v(46, 47, QFACE, 40)
sprite('smack', 48, 35, above=1, solid_rows=())
pp('net_pile', 46, 38, [(1, 4, 30, 10)]); put('Foreground', 47, 38, 'P', SPR['net_pile'][0] + 1, SPR['net_pile'][1])
coll('P', SPR['net_pile'][0] + 1, SPR['net_pile'][1], [(0, 4, 15, 10)])

for x in (19, 26, 29, 41, 44, 50):
    pp('bollard', x, 32, [(5, 4, 6, 10)])
pdeco('buoy', 28, 41, layer='Ground'); pdeco('buoy', 44, 45, layer='Ground'); pdeco('buoy', 15, 46, layer='Ground')

# ---------- lower town along the quay ----------
c0, r0, w, h = SPR['tavern']
building('P', c0, r0, w, h, 18, 24, 1)
door('inn', 'inn', 20, 26)
sign('sign_inn', 20, 25)
for (x, y) in [(18, 28), (23, 28), (17, 31)]:
    pp('table', x, y, [(2, 4, 12, 8)])
pp('bench', 21, 28, [(0, 6, 16, 6)]); put('Foreground', 22, 28, 'P', SPR['bench'][0] + 1, SPR['bench'][1])
coll('P', SPR['bench'][0] + 1, SPR['bench'][1], [(0, 6, 16, 6)])
barrel(24, 25); barrel(25, 25); barrel(24, 26); crate(25, 24)

c0, r0, w, h = SPR['store']
building('P', c0, r0, w, h, 26, 23, 1)
door('store', 'general_store', 27, 26)
sign('sign_store', 28, 23)
crate(29, 27); sack(28, 27, 1); barrel(26, 28)

c0, r0, w, h = SPR['harbour']
building('P', c0, r0, w, h, 33, 23, 1)
door('harbourmaster', 'harbourmaster', 35, 26)
sign('sign_harbour', 34, 25)
pp('flagpole', 37, 26, [(4, 10, 8, 6)]); put('AboveSprites', 37, 25, 'P', SPR['flagpole'][0], SPR['flagpole'][1])
L['Foreground'][26][37] = ('P', SPR['flagpole'][0], SPR['flagpole'][1] + 1)
coll('P', SPR['flagpole'][0], SPR['flagpole'][1] + 1, [(4, 10, 8, 6)])
pp('anchor', 24, 30, [(3, 6, 10, 9)])

c0, r0, w, h = SPR['fishmonger']
building('P', c0, r0, w, h, 39, 24, 1)
door('fishmonger', 'fishmonger', 40, 26)
sprite('drying_rack', 42, 22, above=1, rects=[(2, 0, 28, 14)])
sprite('drying_rack', 44, 22, above=1, rects=[(2, 0, 28, 14)])
sprite('net_rack', 46, 22, above=1, rects=[(1, 0, 30, 14)])
sign_on_posts('sign_fish', 43, 25)
stall('fish_stall', 41, 27); stall('fish_stall2', 44, 27); stall('stall_teal', 37, 29)
pp('basket_fish', 43, 28); pp('octo_basket', 46, 27); pp('squid_crate', 47, 28); pp('shrimp_crate', 39, 30)
pp('fish_crate', 40, 29); pp('basket_shrimp', 46, 30); barrel(36, 29)
pp('net_pile', 42, 31, [(1, 4, 30, 10)]); put('Foreground', 43, 31, 'P', SPR['net_pile'][0] + 1, SPR['net_pile'][1])
pp('rope', 45, 31, [(3, 5, 10, 8)])
pp('lobster_pots', 48, 31)

c0, r0, w, h = SPR['warehouse']
building('P', c0, r0, w, h, 48, 23, 1)
door('warehouse', 'warehouse', 51, 26)
sign('sign_wh', 54, 24)
for (x, y) in [(49, 28), (49, 29), (50, 29), (53, 27), (54, 27), (54, 28), (48, 28)]:
    crate(x, y)
barrel(55, 28); barrel(55, 29); sack(53, 28, 0)
sprite('crane', 52, 30, above=1)
pp('rope', 51, 31, [(3, 5, 10, 8)])

pp('rope', 25, 31, [(3, 5, 10, 8)]); pp('rope', 33, 31, [(3, 5, 10, 8)])
pp('net_pile', 20, 30, [(1, 4, 30, 10)]); put('Foreground', 21, 30, 'P', SPR['net_pile'][0] + 1, SPR['net_pile'][1])
pp('lobster_pot', 27, 30); pp('basket_fish', 30, 29); crate(55, 31); barrel(17, 30)
pdeco('cat', 36, 28); pdeco('cat2', 23, 31)
pdeco('gull', 27, 32, 'AboveSprites'); pdeco('gull2', 41, 31); pdeco('gull', 56, 30)
lamp(25, 26); lamp(38, 26)

# ---------- the boatyard on the west arm ----------
c0, r0, w, h = SPR['shipwright']
building('P', c0, r0, w, h, 6, 27, 1)
door('shipwright', 'shipwright', 7, 29)
sign_on_posts('sign_boats', 11, 28)
for x in range(8, 19):
    for y in (35, 36):
        pdeco('slipway', x, y, 'Ground')
sprite('hull', 9, 33, above=1, solid_rows=(1, 2))
sprite('crane', 14, 29, above=1)
sprite('net_rack', 11, 31, above=1, rects=[(1, 0, 30, 14)])
prop(8, 31, 'E', 9, 2, [(1, 4, 14, 12)]); prop(9, 31, 'E', 10, 2, [(1, 4, 14, 12)])
crate(10, 30); barrel(6, 31)
pdeco('rowboat_up', 11, 40); put('Foreground', 12, 40, 'P', SPR['rowboat_up'][0] + 1, SPR['rowboat_up'][1])
coll('P', SPR['rowboat_up'][0] + 1, SPR['rowboat_up'][1], [])
pp('anchor', 8, 39, [(3, 6, 10, 9)])
pdeco('gull2', 13, 37)

cottage('h_w1', 0, 28, 'white_teal', 32, fence=False)
road(1, 31, 3, 31)
cottage('h_w2', 0, 34, 'orange_blue', 38, fence=False)
road(1, 37, 3, 37)
home('h_w3', 6, 45, 'gable', step=False)
road(5, 48, 7, 48)
sprite('drying_rack', 0, 32, above=1, rects=[(2, 0, 28, 14)])
pp('lobster_pots', 2, 38); pp('lobster_pot', 3, 39); pp('lobster_pot', 8, 48)
stamp('E', 14, 2, 2, 2, 0, 41, above=1)
coll('E', 14, 3, [(3, 6, 2, 10)]); coll('E', 15, 3, [(11, 6, 2, 10)])
palm(1, 45, 0); palm(7, 41, 1); palm(1, 24, 1); palm(15, 24, 0)
pp('bench', 2, 40, [(0, 6, 16, 6)]); put('Foreground', 3, 40, 'P', SPR['bench'][0] + 1, SPR['bench'][1])
pdeco('cat', 3, 33)
rock_big(13, 41); rock_small(16, 33, 0); rock_small(15, 34, 1)

# ---------- the lighthouse point ----------
home('h_keeper', 57, 24, 'beige_blue', step=False)
road(57, 27, 58, 33); road(59, 38, 61, 38)
sprite('lighthouse', 59, 30, above=5)
door('lighthouse', 'lighthouse', 60, 37)
for (x, y) in [(56, 36), (62, 38), (57, 39)]:
    rock_big(x, y)
for (x, y, k) in [(63, 34, 0), (57, 35, 1), (63, 32, 2), (56, 34, 0), (62, 37, 1), (58, 40, 2)]:
    rock_small(x, y, k)
WATER_ROCKS = [(58, 42), (61, 43), (64, 39), (66, 34), (54, 40), (66, 21), (13, 44), (17, 38), (55, 37),
               (53, 42), (59, 42), (62, 41)]
pdeco('gull2', 62, 31, 'AboveSprites')
tree(62, 25, (16, 0)); tree(56, 31, (0, 0))
pp('lobster_pots', 57, 30); sprite('net_rack', 61, 27, above=1, rects=[(1, 0, 30, 14)])
rock_small(56, 33, 2); rock_small(16, 37, 0)

# ---------- east beach ----------
for (x, y, k) in [(59, 2, 0), (62, 5, 1), (59, 12, 1), (61, 16, 0), (63, 22, 0), (59, 27, 1)]:
    palm(x, y, k)
pdeco('rowboat_up', 60, 8); put('Foreground', 61, 8, 'P', SPR['rowboat_up'][0] + 1, SPR['rowboat_up'][1])
pp('lobster_pots', 63, 8); pp('lobster_pot', 60, 15); pp('lobster_pot', 63, 19)
sprite('net_rack', 59, 20, above=1, rects=[(1, 0, 30, 14)])
pdeco('gull', 63, 3)

# =============================== the slope: cottages in staggered clusters ===============================
# Lower row: front paths straight down to the walk on top of the lower wall. Upper row: reached by the alleys.
def cot(name, x, y, kind, to_y=None, fence=False):
    ts, c0, r0, w, h, d, dt = HT[kind]
    ly = to_y if to_y is not None else MID[x + d[0]]
    return cottage(name, x, y, kind, ly, fence=fence)


# west cluster (alleys x4, x9)
cot('h_m1', 0, 21, 'white_blue')
cot('h_m2', 5, 19, 'orange_slate')
cot('h_m3', 10, 18, 'red_teal')
cot('h_m4', 1, 15, 'white_teal', to_y=19); road(2, 18, 3, 18); steps(4, 18, MID[4] - 1)
cot('h_m5', 6, 14, 'gable_slate', to_y=18); road(7, 17, 8, 17)
stamp('E', 14, 2, 2, 2, 10, 15, above=1)
coll('E', 14, 3, [(3, 6, 2, 10)]); coll('E', 15, 3, [(11, 6, 2, 10)])
tree(12, 12, (16, 0))
pp('lobster_pots', 3, 19); pp('lobster_pot', 8, 22)
# centre-west cluster (alley x19 to the upper wall, x24 to the upper cottage)
cot('h_m6', 15, 17, 'beige_navy')
cot('h_m7', 20, 16, 'white_blue')
cot('h_m8', 25, 15, 'red_slate')
cot('h_m9', 16, 12, 'round_white', to_y=16); road(17, 15, 18, 15)
cot('h_m10', 22, 11, 'orange2_blue', to_y=15); road(23, 14, 23, 14); steps(24, 14, MID[24] - 1)
fence_box(26, 10, 29, 13)
for x in (27, 28):
    put('Foreground', x, 11, 'N', 6, 10); put('Foreground', x, 12, 'N', 9, 9)
pp('lobster_pots', 29, 18); tree(13, 15, (0, 0))
# well square on the main road
stamp('E', 7, 1, 1, 2, 34, 12, above=1)
coll('E', 7, 2, [(2, 0, 12, 13)])
stamp('E', 11, 1, 3, 1, 33, 10)
for c in (11, 12, 13):
    coll('E', c, 1, [(0, 4, 16, 9)])
tobj('quest_board', 'notice_board', 33, 10, 3, 1)
pp('bench', 35, 14, [(0, 6, 16, 6)]); put('Foreground', 36, 14, 'P', SPR['bench'][0] + 1, SPR['bench'][1])
tree(33, 16, (16, 0))
# east cluster (alley x41 up through the wall, x46 to the upper cottage)
cot('h_m11', 37, 15, 'orange_teal')
cot('h_m12', 42, 16, 'white_navy')
cot('h_m13', 47, 15, 'red_blue')
cot('h_m14', 44, 11, 'beige_teal', to_y=15); road(45, 14, 45, 14); steps(46, 14, MID[46] - 1)
fence_box(37, 10, 40, 13, rgaps=(12,))
for x in (38, 39):
    put('Foreground', x, 11, 'N', 9, 9); put('Foreground', x, 12, 'N', 10, 9)
stamp('E', 14, 2, 2, 2, 52, 16, above=1)
coll('E', 14, 3, [(3, 6, 2, 10)]); coll('E', 15, 3, [(11, 6, 2, 10)])
fence_box(49, 12, 52, 14)
for x in (50, 51):
    put('Foreground', x, 13, 'N', 6, 10)
tree(55, 15, (0, 0)); tree(53, 13, (16, 0)); pp('lobster_pots', 51, 18); pp('lobster_pot', 41, 19)

# upper tier
cot('h_u1', 6, 7, 'white_slate', to_y=UP[7])
cot('h_u2', 15, 5, 'red_navy', to_y=UP[16])
cot('h_u3', 44, 4, 'white_blue', to_y=UP[45])
cot('h_u4', 50, 6, 'orange_navy', to_y=UP[51])
c0, r0, w, h = SPR['chapel']
building('P', c0, r0, w, h, 35, 0, 2)
door('chapel', 'chapel', 36, 4)
road(36, 5, 36, UP[36])
sprite('statue', 33, 3, above=1)
sprite('statue', 39, 3, above=1)
crops(1, 1, 10, 4, [(9, 9), (10, 9)])
crops(19, 1, 27, 5, [(10, 9), (6, 10)])
stamp('E', 14, 2, 2, 2, 11, 7, above=1)
coll('E', 14, 3, [(3, 6, 2, 10)]); coll('E', 15, 3, [(11, 6, 2, 10)])
fence_box(54, 5, 57, 8)
for (x, y, k) in [(12, 3, (0, 0)), (15, 1, (16, 0)), (2, 8, (18, 0)), (41, 3, (0, 0)), (48, 1, (16, 0)),
                  (55, 1, (0, 0)), (40, 6, (0, 0)), (52, 2, (18, 0))]:
    tree(x, y, k)

autotile()

# ---------- drawn over the autotile: piers, quay wall, rocks in the sea ----------
draw_piers()
qc, qr = SPR['quay_face'][:2]
for x in range(17, 56):
    if any(p[0] == x and p[1] == QFACE for p in PIERS):
        continue
    cc = 0 if x == 17 else 2 if x == 55 else 1
    put('Background', x, QFACE, 'P', qc + cc, qr)
    coll('P', qc + cc, qr, FULL)
for i, (x, y) in enumerate(WATER_ROCKS):
    put('Ground', x, y, 'N', 17 + i % 3, 17)

# ---------- decorations ----------
FLOWERS = [('N', 0, 11), ('N', 1, 11), ('N', 3, 11), ('N', 6, 11), ('N', 2, 11), ('D', 5, 2), ('D', 2, 0)]
for (xx, yy) in YARD:
    bg = L['Background'][yy][xx]
    if bg and bg[0] == 'F' and bg[2] == 12 and not L['Foreground'][yy][xx] and not L['Ground'][yy][xx]:
        deco(xx, yy, *random.choice(FLOWERS))
TUFTS = [('D', 0, 2), ('D', 1, 2), ('D', 2, 2), ('D', 3, 2), ('D', 3, 0), ('D', 6, 2)]
SHORE = [('D', 10, 0), ('D', 15, 0), ('D', 4, 0)]
BUSH = [('N', 0, 10), ('N', 1, 10), ('N', 2, 10), ('N', 6, 10)]
for y in range(H):
    for x in range(W):
        bg = L['Background'][y][x]
        free_ = not L['Foreground'][y][x] and not L['Ground'][y][x] and not L['AboveSprites'][y][x]
        if not bg or not free_:
            continue
        if bg[0] == 'F' and bg[2] == 12:
            r_ = random.random()
            if r_ < 0.08:
                deco(x, y, *random.choice(TUFTS + FLOWERS[:3]))
            elif r_ < 0.11 and y > 0 and L['Ground'][y - 1][x] is None:
                prop(x, y, *random.choice(BUSH), [(2, 6, 12, 8)])
        elif bg[0] == 'F' and (bg[1], bg[2]) == (1, 8) and y >= 22 and random.random() < 0.035:
            deco(x, y, *random.choice(SHORE))
for t in FLOWERS + TUFTS + SHORE:
    coll(t[0], t[1], t[2], [])

# ---------- objects: exits, npc spots ----------
tobj('exit', 'exit_north_road', 30, 0, 2, 1, props=[('dir', 'N')])
tobj('exit', 'coast_path_south', 4, H - 1, 2, 1, props=[('dir', 'S'), ('kind', 'coast_path')])
tobj('exit', 'coast_path_north', 61, 0, 2, 1, props=[('dir', 'N'), ('kind', 'coast_path')])
for role, (x, y) in [('harbourmaster', (34, 28)), ('fishmonger', (42, 29)), ('sailor', (38, 42)),
                     ('shipwright', (10, 37)), ('innkeeper', (21, 27)), ('shopkeep', (28, 28)),
                     ('lighthouse_keeper', (59, 38)), ('child', (33, 14)), ('fisher', (23, 38)),
                     ('fisher', (46, 36)), ('sailor', (31, 31)), ('priest', (37, 5)), ('merchant', (52, 28)),
                     ('dockhand', (49, 30)), ('dockhand', (27, 29)), ('fisher', (44, 30))]:
    tobj('npc_spot', 'npc_' + role, x, y, props=[('role', role)])
