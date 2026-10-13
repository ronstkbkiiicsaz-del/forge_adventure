# ===================== SWAMP TOWN LAYOUT (exec'd by town.py) =====================
# A bayou town built over and around murky water (black biome). Everything starts as water; land is carved as
# hummocks:
#   causeway      the one road to dry land, an earthen bank from the west edge (rows 23..27) to the landing green
#   landing green the main hummock (centre-west): trading post, apothecary, notice board + cistern on a plank square
#   NW hummock    two mossy cottages beside the causeway
#   the rise      the one firm ground (north-east, runs off the north edge): graveyard in an iron fence, the crypt,
#                 the bog chapel, the gravedigger's hut
#   the mudflat   east: smokehouse and eel racks, the hunter's house, remnants of an old battlefield
#   witch's knoll a little hummock at the south-west edge, reached by wading
# Stilt houses stand over the water along three boardwalk "streets" (north A y12, middle C y20, south B y41).
# The skiff landing (travel station) is a jetty off street B; the skiff channel runs south off the map.

import math

HT['va_a'] = ('V', 11, 0, 3, 3, (1, 2), None)
HT['va_b'] = ('V', 11, 3, 3, 3, (1, 2), None)

WALK = set()          # boardwalk planks (walkable, over water / shore / mud)
LADDERS = set()
DECK = set()          # plank deck under stilt-house walls (behind solid tiles)
STILTS = []           # (x, y, kind)
ROPE = {}             # (x, y) -> 'h' | 'v'  rope-bridge spans
WADE = set()          # wading shallows (walkable, slow later)
TILECLASS = {}        # (ts, tile id) -> 'water' | 'wade'   (written to the tsx as the tile's class)


def rect(x0, y0, x1, y1):
    return {(x, y) for y in range(y0, y1 + 1) for x in range(x0, x1 + 1)}


def land_ell(cx, cy, rx, ry):
    for y in range(H + 1):
        for x in range(W + 1):
            if ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 <= 1:
                WATER[y][x] = 0


def land_rect(x0, y0, x1, y1):
    vrect(WATER, x0, y0, x1 + 1, y1 + 1, 0)


def walk(x0, y0, x1, y1):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            WALK.add((x, y))


def rope(x0, y0, x1, y1, d):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            ROPE[(x, y)] = d
            WALK.discard((x, y))


def wade(name, x0, y0, x1, y1):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            WADE.add((x, y))
    tobj('wade', name, x0, y0, x1 - x0 + 1, y1 - y0 + 1, props=[('kind', 'shallows')])


def full_land(x, y):
    return not (WATER[y][x] or WATER[y][x + 1] or WATER[y + 1][x] or WATER[y + 1][x + 1])


def sp(name):
    return SPR[name][:2]


def pdeco(name, x, y, layer='Ground'):
    c, r = sp(name)
    put(layer, x, y, 'P', c, r)
    coll('P', c, r, [])


def pp(name, x, y, rects=((2, 4, 12, 12),), layer='Foreground'):
    c, r = sp(name)
    prop(x, y, 'P', c, r, rects)


def sprite(name, x, y, above, solid_rows=None, rects=None, layer='Foreground'):
    c0, r0, w, h = SPR[name]
    stamp('P', c0, r0, w, h, x, y, above, layer)
    rows = range(above, h) if solid_rows is None else solid_rows
    for dy in range(h):
        for dx in range(w):
            if dy in rows:
                coll('P', c0 + dx, r0 + dy, list(rects) if rects else FULL)
            elif dy >= above:
                coll('P', c0 + dx, r0 + dy, [])


def stilt_home(name, x, y, kind, interior='home', count=True):
    """a house standing on stilts over the water: the house, a stilt row under it with plank steps down from the
    door, and the boardwalk tile below the steps."""
    ts, c0, r0, w, h, d, dt = HT[kind]
    building(ts, c0, r0, w, h, x, y, 1, doors=[d], overlay=dt, count=count)
    door(interior, name, x + d[0], y + d[1])
    for yy in range(y + 1, y + h):
        for xx in range(x, x + w):
            DECK.add((xx, yy))
    for xx in range(x, x + w):
        if xx == x + d[0]:
            LADDERS.add((xx, y + h))
        else:
            STILTS.append((xx, y + h, 'stilt_l' if xx == x else 'stilt_r' if xx == x + w - 1 else 'stilt_m'))
    WALK.add((x + d[0], y + h + 1))
    return x + d[0], y + d[1]


def big_stilt(name, sprname, x, y, door_off, interior, above=1):
    """a PortExtras-style big building on stilts (tavern)."""
    c0, r0, w, h = SPR[sprname]
    building('P', c0, r0, w, h, x, y, above)
    door(interior, name, x + door_off[0], y + door_off[1])
    for yy in range(y + 1, y + h):
        for xx in range(x, x + w):
            DECK.add((xx, yy))
    for xx in range(x, x + w):
        if xx == x + door_off[0]:
            LADDERS.add((xx, y + h))
        else:
            STILTS.append((xx, y + h, 'stilt_l' if xx == x else 'stilt_r' if xx == x + w - 1 else 'stilt_m'))
    WALK.add((x + door_off[0], y + h + 1))


def land_home(name, x, y, kind, lane_y=None, interior='home'):
    """a cottage on dry ground with a mud path from its door down to row lane_y."""
    ts, c0, r0, w, h, d, dt = HT[kind]
    building(ts, c0, r0, w, h, x, y, 1, doors=[d], overlay=dt, count=interior == 'home')
    door(interior, name, x + d[0], y + d[1])
    if lane_y is not None:
        road(x + d[0], y + d[1] + 1, x + d[0], lane_y)
    return x + d[0], y + d[1]


def tree_spr(name, x, y):
    """a cypress / dead tree sprite: all but the bottom row above sprites, the trunk tile solid."""
    c0, r0, w, h = SPR[name]
    stamp('P', c0, r0, w, h, x, y, above=h - 1)
    for dx in range(w):
        mid = dx == w // 2 or (w == 2 and dx == 0) or (w == 4 and dx in (1, 2))
        coll('P', c0 + dx, r0 + h - 1, [(3, 0, 10, 12)] if mid else [])


LIGHTS = []          # foot tiles of every lamp (a pool of warm light is laid under each)


def lantern(x, y):
    LIGHTS.append((x, y + 1))
    c, r = sp('lantern_post')
    put('AboveSprites', x, y, 'P', c, r)
    prop(x, y + 1, 'P', c, r + 1, [(3, 9, 6, 7)])


def deck_lantern(x, y):
    LIGHTS.append((x, y))
    pp('deck_lantern', x, y, [(6, 7, 4, 9)])


def ifence(x, y, k):
    c, r = sp('ifence_' + k)
    put('Foreground', x, y, 'P', c, r)
    coll('P', c, r, {'h': [(0, 4, 16, 10)], 'v': [(5, 0, 7, 16)], 'post': [(4, 1, 8, 14)]}[k])


def tomb(x, y, k):
    pp('tomb_' + k, x, y, [(3, 6, 10, 9)])


def mist(name, x, y):
    c0, r0, w, h = SPR[name]
    if all(0 <= x + dx < W and 0 <= y + dy < H and L['AboveSprites'][y + dy][x + dx] is None
           for dy in range(h) for dx in range(w)):
        stamp('P', c0, r0, w, h, x, y, above=h)


def sign_on_posts(name, x, y):
    sign(name, x, y)
    c0, r0, w, h = SPR['sign_posts']
    put('Foreground', x, y + 1, 'P', c0, r0)
    put('Foreground', x + 1, y + 1, 'P', c0 + 1, r0)
    coll('P', c0, r0, [(4, 0, 5, 14)])
    coll('P', c0 + 1, r0, [(8, 0, 5, 14)])


# =============================== terrain ===============================
vrect(WATER, 0, 0, W, H, 1)
land_rect(0, 23, 13, 27)                      # the causeway bank
land_ell(21, 23.5, 10.5, 8)                   # landing green
land_ell(6, 18.5, 5, 4)                       # NW hummock (merges with the causeway)
land_ell(55, 9, 13.5, 9.5)                    # the rise (graveyard)
land_ell(63.5, 28.5, 7.5, 7.5)                # the mudflat (old battlefield), bigger
land_ell(55, 32.5, 5, 3.5)                    # smokehouse hummock (merges with the mudflat)
land_ell(5, 44.5, 4.5, 3.5)                   # witch's knoll
land_rect(13, 28, 29, 31)                     # the green's south edge, flatter
land_rect(43, 13, 50, 16)                     # the rise's south-west shoulder (chapel path)
land_rect(5, 42, 8, 45)                       # the knoll's east side (where the wade comes ashore)
land_ell(9, 4, 2.2, 1.8)                      # a tree islet north-west

road(0, 25, 18, 26)                           # causeway road, mud, into the green
lantern(4, 23); lantern(10, 23); lantern(1, 26); lantern(7, 26)

# =============================== boardwalk streets ===============================
# Boardwalks jog, bend and change width; side jetties run out to houses standing alone on their own stilts.
# north exit boardwalk: 2 wide, jogs west, narrows, widens at the street A crossing, on down to the green
for r_ in [(21, 0, 22, 3), (20, 3, 21, 7), (20, 7, 20, 10), (20, 10, 22, 12), (21, 12, 22, 16)]:
    walk(*r_)
# street A west (houses at staggered heights, two side jetties)
for r_ in [(2, 11, 5, 11), (5, 11, 6, 12), (6, 13, 11, 13), (6, 12, 6, 15), (11, 11, 12, 13), (12, 11, 19, 11),
           (13, 9, 13, 10), (17, 7, 17, 10)]:
    walk(*r_)
# street A east
for r_ in [(23, 13, 27, 13), (27, 11, 28, 13), (29, 11, 32, 11), (32, 5, 32, 7), (32, 7, 33, 7), (33, 7, 33, 12),
           (33, 12, 34, 14), (35, 13, 36, 14)]:
    walk(*r_)
# street C (middle): the green -> rope bridge over the channel -> the rise
for r_ in [(29, 20, 34, 20), (33, 20, 34, 21), (40, 21, 44, 22), (44, 19, 45, 22), (45, 19, 50, 19), (50, 18, 52, 19)]:
    walk(*r_)
rope(35, 21, 39, 21, 'h')
walk(37, 22, 37, 24)                           # down from the bridge to the fishing platform
# the fishing platform (deck) and its zigzag link south to street B
walk(33, 25, 40, 29)
for r_ in [(38, 30, 38, 33), (38, 33, 39, 33), (39, 33, 39, 36), (38, 36, 39, 37), (38, 37, 38, 41)]:
    walk(*r_)
# street B (south): from the green, jogging past the houses to the wharf, the skiff landing and the smokehouse
for r_ in [(19, 31, 20, 34), (18, 34, 19, 37), (19, 37, 20, 40), (18, 41, 21, 42)]:
    walk(*r_)
for r_ in [(10, 40, 13, 40), (13, 41, 14, 42), (14, 42, 18, 42), (21, 41, 26, 41), (26, 40, 31, 40), (31, 40, 32, 42),
           (32, 42, 37, 42), (37, 41, 39, 42), (39, 41, 47, 42), (47, 41, 51, 41), (51, 40, 53, 41), (53, 40, 56, 40),
           (55, 36, 56, 40)]:
    walk(*r_)
walk(22, 42, 28, 43)                           # tavern terrace
walk(45, 43, 46, 47)                           # the skiff jetty
for r_ in [(36, 43, 36, 48), (36, 48, 37, 48)]:   # L-jetty to the lone house south of the street
    walk(*r_)

wade('wade_witch', 9, 41, 11, 43)             # wading out to the witch's knoll
wade('wade_rise', 37, 13, 42, 14)             # from the end of street A to the rise
wade('wade_mudflat', 61, 17, 63, 21)          # from the rise down to the mudflat

# =============================== stilt houses ===============================
# street A (staggered)
stilt_home('h_a1', 2, 7, 'orange_moss')
stilt_home('h_a2', 7, 9, 'gable_plum')
stilt_home('h_a3', 12, 5, 'beige_bark')
stilt_home('h_a4', 24, 9, 'round_pine')
stilt_home('h_a5', 27, 6, 'red_plum')
# houses standing alone on their own stilts, reached by side jetties
stilt_home('h_l1', 16, 3, 'gable_moss')
stilt_home('h_l2', 31, 1, 'beige_moss')
stilt_home('h_b5', 37, 44, 'red_moss')
# street C
stilt_home('h_c1', 31, 16, 'orange2_plum')
stilt_home('h_c2', 40, 16, 'gable_moss')
# street B
stilt_home('h_b1', 11, 36, 'beige_moss')
stilt_home('h_b2', 16, 38, 'round_bark')
big_stilt('inn', 'tavern', 22, 37, (2, 2), 'inn')
stilt_home('h_b3', 29, 36, 'orange2_pine')
stilt_home('h_b4', 34, 38, 'thatch_moss')
# the poleman's hut: the mossy hut on legs, its ladder comes down at (49,40)
c0, r0, w, h = SPR['pole_hut']
stamp('P', c0, r0, 3, 5, 48, 36, above=2)
for dy in range(2, 5):
    for dx in range(3):
        coll('P', c0 + dx, r0 + dy, FULL)
door('poleman', 'poleman_hut', 49, 40)

# =============================== the fishing platform and its lift-net ===============================
# deck x33..40 y25..29 on stilts (row 30), the A-frame, boom, counterweight and net drawn as one 15x14 sprite whose
# origin is (34,21); the net hangs over the channel east of the deck (x42..47, y28..32).
for x in range(33, 41):
    if x != 38:
        STILTS.append((x, 30, 'stilt_l' if x == 33 else 'stilt_r' if x == 40 else 'stilt_m'))
NX, NY = 34, 21
c0, r0, w, h = SPR['liftnet']
for dy in range(h):
    for dx in range(w):
        if (dx, dy) in ((4, 5), (5, 5)):
            put('Foreground', NX + dx, NY + dy, 'P', c0 + dx, r0 + dy)
            coll('P', c0 + dx, r0 + dy, [(1, 8, 8, 8)] if dx == 4 else [(8, 8, 8, 8)])
        elif L['AboveSprites'][NY + dy][NX + dx] is None:
            put('AboveSprites', NX + dx, NY + dy, 'P', c0 + dx, r0 + dy)
pp('winch', 35, 28, [(1, 5, 14, 10)])
LIGHTS.append((39, 24))
for (nm, x, y) in [('eel_basket', 33, 26), ('eel_basket', 34, 26), ('cray_trap', 40, 26), ('cray_trap', 40, 27),
                   ('eel_basket', 36, 29), ('crates', 33, 29)]:
    pp(nm, x, y)
barrel(40, 25)
pp('net_pile', 34, 25, [(1, 4, 30, 10)]); put('Foreground', 35, 25, 'P', sp('net_pile')[0] + 1, sp('net_pile')[1])
coll('P', sp('net_pile')[0] + 1, sp('net_pile')[1], [(0, 4, 15, 10)])
deck_lantern(33, 28); deck_lantern(40, 29)
sprite('skiffv1', 32, 25, above=0, solid_rows=())
sprite('skiff0', 34, 31, above=0, solid_rows=())
sprite('skiff2', 41, 34, above=0, solid_rows=())

# =============================== the landing green ===============================
c0, r0, w, h = SPR['store']
building('P', c0, r0, w, h, 13, 17, 1)
door('store', 'trading_post', 14, 20)
sign('sign_store', 13, 21, layer='AboveSprites')
road(14, 21, 14, 24)
c0, r0, w, h = SPR['apothecary']
building('P', c0, r0, w, h, 25, 17, 1)
door('apothecary', 'apothecary', 26, 19)
sign('sign_apoth', 24, 20)
road(26, 20, 26, 22)
crops(26, 23, 30, 26, [(10, 9), (9, 9)])
pdeco('herbs', 27, 19, 'AboveSprites')
pp('bottles', 28, 19); pp('bottles', 24, 19)
# plank square: notice board, rain cistern, two stalls, a table with stools, a brazier, a handcart, crates
PLAZA = rect(16, 22, 24, 27) | rect(19, 28, 22, 30)
for (x, y) in PLAZA:
    WALK.add((x, y))
stamp('E', 11, 1, 3, 1, 18, 22)
for c in (11, 12, 13):
    coll('E', c, 1, [(0, 4, 16, 9)])
tobj('quest_board', 'notice_board', 18, 22, 3, 1)
sprite('cistern', 22, 22, above=1)
sprite('stall_plum', 16, 22, above=1, rects=[(1, 2, 15, 12)])
sprite('stall_moss', 23, 25, above=1, rects=[(1, 2, 15, 12)])
pp('table', 21, 25, [(2, 4, 12, 8)])
pp('stool', 20, 25, [(4, 8, 8, 6)]); pp('stool', 22, 25, [(4, 8, 8, 6)])
pp('brazier', 18, 25, [(3, 7, 10, 8)])
LIGHTS.append((18, 25))
put('Foreground', 16, 27, 'P', *sp('handcart')); put('Foreground', 17, 27, 'P', sp('handcart')[0] + 1, sp('handcart')[1])
coll('P', *sp('handcart'), [(3, 4, 13, 10)]); coll('P', sp('handcart')[0] + 1, sp('handcart')[1], [(0, 4, 10, 10)])
pp('crates', 24, 23); barrel(24, 22); pp('eel_basket', 21, 27); sack(22, 27, 0)
pp('bench', 23, 27, [(0, 6, 16, 6)]); put('Foreground', 24, 27, 'P', sp('bench')[0] + 1, sp('bench')[1])
coll('P', sp('bench')[0] + 1, sp('bench')[1], [(0, 6, 16, 6)])
lantern(15, 22); lantern(25, 24)
barrel(17, 21); crate(18, 21); sack(19, 21, 1)
# cottages on the green
land_home('h_g1', 27, 27, 'va_b', lane_y=30)
road(23, 30, 28, 30)
land_home('h_g2', 12, 27, 'hut_bark', lane_y=29)
road(13, 29, 18, 29)
pdeco('rowboat_up', 14, 30, 'Foreground'); put('Foreground', 15, 30, 'P', sp('rowboat_up')[0] + 1, sp('rowboat_up')[1])
coll('P', sp('rowboat_up')[0] + 1, sp('rowboat_up')[1], [])
pp('net_pile', 25, 30, [(1, 4, 30, 10)]); put('Foreground', 26, 30, 'P', sp('net_pile')[0] + 1, sp('net_pile')[1])
coll('P', sp('net_pile')[0] + 1, sp('net_pile')[1], [(0, 4, 15, 10)])
barrel(29, 28); barrel(12, 22); crate(11, 22)
pp('eel_basket', 17, 30)

# NW hummock
land_home('h_nw1', 2, 16, 'va_a', lane_y=24)
land_home('h_nw2', 7, 17, 'orange_bark', lane_y=24)

# =============================== the tavern ===============================
sign('sign_inn', 23, 38)
for (x, y) in [(23, 43), (27, 43)]:
    pp('table', x, y, [(2, 4, 12, 8)])
pp('bench', 24, 43, [(0, 6, 16, 6)]); put('Foreground', 25, 43, 'P', sp('bench')[0] + 1, sp('bench')[1])
deck_lantern(22, 43); deck_lantern(28, 43)

# =============================== the skiff landing ===============================
sprite('skiff1', 42, 43, above=0, solid_rows=())
sprite('skiff0', 42, 45, above=0, solid_rows=())
sprite('skiff2', 47, 44, above=0, solid_rows=())
tobj('station', 'skiff_landing', 42, 42, 8, 6, props=[('kind', 'skiff'), ('label', 'Skiff landing')])
sign_on_posts('sign_skiffs', 43, 41)
deck_lantern(45, 47); deck_lantern(46, 47)
pp('rope', 46, 43, [(3, 5, 10, 8)])
pp('cray_trap', 52, 42); pp('eel_basket', 53, 42)

# =============================== the rise: graveyard, crypt, chapel ===============================
FX0, FX1, FY0, FY1 = 48, 62, 5, 14
c0, r0, w, h = SPR['crypt']
building('P', c0, r0, w, h, 53, 2, 2)
door('crypt', 'crypt', 55, 5)
for x in range(FX0, FX1 + 1):
    if 53 <= x <= 57:
        continue
    ifence(x, FY0, 'post' if x in (FX0, FX1, 52, 58) else 'h')
for x in range(FX0, FX1 + 1):
    if 54 <= x <= 56:
        continue
    ifence(x, FY1, 'post' if x in (FX0, FX1, 53, 57) else 'h')
for y in range(FY0 + 1, FY1):
    ifence(FX0, y, 'v'); ifence(FX1, y, 'v')
road(55, 6, 55, 18); road(54, 14, 56, 15); road(51, 18, 55, 18)
lantern(53, 13); lantern(57, 13)
GRAVES = [(50, 7, 'round'), (52, 7, 'cross'), (59, 7, 'slab'), (61, 7, 'old'), (50, 9, 'old'), (52, 9, 'round'),
          (58, 9, 'cross'), (60, 9, 'round'), (50, 11, 'cross'), (52, 11, 'slab'), (58, 11, 'old'),
          (60, 11, 'mound'), (49, 13, 'mound'), (51, 12, 'fresh'), (59, 13, 'round'), (61, 13, 'cross')]
for (x, y, k) in GRAVES:
    tomb(x, y, k)
pp('candles', 57, 7, [(2, 8, 12, 8)])
tree_spr('dead_moss', 53, 9)
tree_spr('dead_moss', 56, 10)
# gravedigger's hut and the bog chapel, west of the fence
land_home('h_dig', 43, 4, 'hut_bark', lane_y=8)
road(44, 8, 47, 8)
c0, r0, w, h = SPR['chapel']
building('P', c0, r0, w, h, 44, 9, 2)
door('chapel', 'chapel', 45, 13)
road(45, 14, 45, 16); road(46, 16, 54, 16)
pp('candles', 44, 14, [(2, 8, 12, 8)]); pp('candles', 46, 14, [(2, 8, 12, 8)])
tree_spr('dead_big', 63, 2)
tree_spr('dead_moss', 64, 8)
tree_spr('dead_moss', 47, 1)
tomb(66, 12, 'old'); tomb(43, 2, 'old')

# =============================== the mudflat: smokehouse, hunter, old battlefield ===============================
c0, r0, w, h = SPR['smokehouse']
building('P', c0, r0, w, h, 51, 30, 1)
door('smokehouse', 'smokehouse', 52, 32)
sign('sign_smoke', 51, 33)
road(52, 33, 52, 35); road(52, 35, 56, 35)
sprite('eel_rack', 54, 30, above=1, rects=[(2, 0, 28, 14)])
sprite('eel_rack', 57, 31, above=1, rects=[(2, 0, 28, 14)])
pp('eel_basket', 54, 33); pp('eel_basket', 57, 34); pp('cray_trap', 50, 34)
barrel(58, 34)
land_home('h_hunter', 62, 22, 'beige_pine', lane_y=26)
road(63, 26, 66, 26)
# the old battlefield across the east of the mudflat: scorched ground, a broken palisade, a ruined wall, a toppled
# catapult and a cart, war banners, swords and a spear stuck in the mud, armour, shields, bones and crows.
BF_SKIP = []


def bf(name, x, y, above=0, rects=((3, 6, 10, 9),)):
    c0, r0, w, h = SPR[name]
    cells = [(x + dx, y + dy) for dy in range(h) for dx in range(w)]
    if any(L['Foreground'][yy][xx] or L['AboveSprites'][yy][xx] for (xx, yy) in cells):
        BF_SKIP.append((name, x, y))
        return
    stamp('P', c0, r0, w, h, x, y, above)
    for dy in range(h):
        for dx in range(w):
            coll('P', c0 + dx, r0 + dy, list(rects) if dy >= above and dy == h - 1 else [])


for (k, x, y) in [(0, 60, 28), (1, 65, 30), (2, 61, 33), (0, 67, 26), (1, 58, 31), (2, 64, 23)]:
    c0, r0, w, h = SPR['scorch%d' % k]
    for dy in range(2):
        for dx in range(2):
            put('Ground', x + dx, y + dy, 'P', c0 + dx, r0 + dy)
            coll('P', c0 + dx, r0 + dy, [])
for (nm, x) in [('stakes0', 59), ('stakes_b0', 60), ('stakes1', 62), ('stakes_b1', 64), ('stakes_b0', 67),
                ('stakes0', 68)]:
    bf(nm, x, 26, above=1, rects=[(0, 8, 16, 8)])
bf('stakes_fallen', 65, 28, rects=[(1, 5, 30, 9)])
c0, r0, w, h = SPR['ruin']
stamp('P', c0, r0, 4, 3, 66, 32, above=1)
for dx in range(4):
    for dy in (1, 2):
        coll('P', c0 + dx, r0 + dy, [(0, 2, 16, 12)] if dy == 2 else [])
c0, r0, w, h = SPR['ruin_low']
stamp('P', c0, r0, 3, 2, 62, 34, above=1)
for dx in range(3):
    coll('P', c0 + dx, r0 + 1, [(0, 2, 16, 12)])
bf('siege', 59, 30, above=1, rects=[(0, 0, 16, 12)])
bf('cart', 64, 29, above=0, rects=[(2, 2, 14, 12)])
for i, (x, y) in enumerate([(58, 27), (66, 24), (69, 27), (61, 34), (57, 33)]):
    c, r = sp('banner%d' % (i % 3))
    if L['AboveSprites'][y][x] is None and L['Foreground'][y + 1][x] is None:
        put('AboveSprites', x, y, 'P', c, r)
        prop(x, y + 1, 'P', c, r + 1, [(4, 6, 8, 10)])
        if i in (1, 3):
            put('AboveSprites', x, y - 1, 'P', *sp('crow_p'))
for i, (x, y) in enumerate([(62, 28), (61, 27), (60, 33), (65, 31), (63, 30), (67, 28)]):
    bf('tsword%d' % (i % 4), x, y, above=1, rects=[(5, 10, 6, 6)])
bf('spear', 69, 30, above=1, rects=[(8, 10, 6, 6)])
for (nm, x, y) in [('armour', 58, 29), ('shield0', 58, 33), ('shield1', 69, 29), ('helmet', 63, 32),
                   ('skull_m', 64, 31), ('bones', 62, 33), ('skull', 65, 34), ('bones', 59, 35), ('armour', 65, 35),
                   ('shield0', 68, 30)]:
    bf(nm, x, y)
for (nm, x, y) in [('crow', 61, 29), ('crow2', 65, 33), ('crow', 63, 33)]:
    if L['Foreground'][y][x] is None:
        pdeco(nm, x, y, 'Foreground')
tree_spr('dead_moss', 59, 24)
tree_spr('dead_big', 66, 21)
if BF_SKIP:
    print('battlefield: skipped', BF_SKIP)

# =============================== the witch's knoll ===============================
stamp('V', 12, 6, 4, 5, 2, 39, above=2)
for dy in range(2, 5):
    for dx in range(4):
        coll('V', 12 + dx, 6 + dy, FULL)
door('witch', 'witch_hut', 3, 43)
road(3, 44, 3, 45); road(4, 45, 8, 45)
pp('cauldron', 6, 43, [(2, 6, 12, 9)])
c, r = sp('skull_post')
put('AboveSprites', 7, 41, 'P', c, r); prop(7, 42, 'P', c, r + 1, [(6, 0, 4, 16)])
pp('bottles', 1, 44); pdeco('shroom2', 6, 46); pdeco('shroom0', 1, 46)
pdeco('cat', 5, 44, 'Foreground')

# =============================== trees in and by the water ===============================
for (name, x, y) in [('cypress2', 26, 1), ('cypress', 0, 0), ('cypress_s', 36, 3),
                     ('cypress2', 8, 2), ('cypress', 13, 44), ('cypress2', 30, 45), ('cypress_s', 55, 45),
                     ('cypress', 60, 39), ('cypress_s', 3, 31),
                     ('cypress', 66, 41), ('cypress_s', 18, 46), ('cypress2', 0, 33), ('cypress_s', 39, 4),
                     ('cypress_s', 9, 21), ('cypress', 2, 19)]:
    tree_spr(name, x, y)
for (x, y) in [(66, 16), (8, 29), (34, 46), (59, 46)]:
    tree_spr('dead_moss', x, y)

autotile()

# =============================== drawn over the autotile ===============================
def blob(x, y, S_, extra=()):
    n = (x, y - 1) in S_ or (x, y - 1) in extra
    s = (x, y + 1) in S_
    w_ = (x - 1, y) in S_
    e = (x + 1, y) in S_
    cc = 3 if not (w_ or e) else 0 if (e and not w_) else 2 if (w_ and not e) else 1
    rr = 3 if not (n or s) else 0 if (s and not n) else 2 if (n and not s) else 1
    return cc, rr


def base_of(x, y):
    if full_land(x, y):
        return 'm'
    return 'd' if DEEPT[y][x] else 's'


for (x, y) in WALK:
    if not (0 <= x < W and 0 <= y < H):
        continue
    cc, rr = blob(x, y, WALK | set(ROPE), extra=LADDERS)
    c0, r0 = sp('walk_' + base_of(x, y))
    put('Background', x, y, 'P', c0 + cc, r0 + rr)
    coll('P', c0 + cc, r0 + rr, [])
    if L['Ground'][y][x] and L['Ground'][y][x][0] == 'F':
        L['Ground'][y][x] = None
for (x, y) in DECK:
    c0, r0 = sp('walk_' + base_of(x, y))
    put('Background', x, y, 'P', c0 + 1, r0 + 1)
for (x, y), d_ in ROPE.items():
    c, r = sp('rope_%s_%s' % (d_, 'd' if DEEPT[y][x] else 's'))
    put('Background', x, y, 'P', c, r)
    coll('P', c, r, [])
for (x, y, k) in STILTS:
    c, r = sp('%s_%s' % (k, 'd' if DEEPT[y][x] else 's'))
    put('Background', x, y, 'P', c, r)
    coll('P', c, r, FULL)
for (x, y) in LADDERS:
    c, r = sp('ladder_%s' % ('d' if DEEPT[y][x] else 's'))
    put('Background', x, y, 'P', c, r)
    coll('P', c, r, [])
for i, (x, y) in enumerate(sorted(WADE)):
    c, r = sp('wade' if (x * 3 + y) % 3 else 'wade2')
    put('Background', x, y, 'P', c, r)
    coll('P', c, r, [])
    TILECLASS[('P', tid('P', c, r))] = 'wade'
TILECLASS[('W', tid('W', 1, 1))] = 'water'
for k in ('shallow_ripple', 'deep_ripple'):
    TILECLASS[('P', tid('P', *sp(k)))] = 'water'
for (c, r) in SPR['deep_sig'].values():
    TILECLASS[('P', tid('P', c, r))] = 'water'


# =============================== groves: more cypresses standing in the open water ===============================
BUSY = WALK | set(ROPE) | WADE | LADDERS | DECK | {(a, b) for (a, b, k) in STILTS}


def spot_free(x, y, w, h, margin=1):
    for yy in range(y - margin, y + h + margin):
        for xx in range(x - margin, x + w + margin):
            if (xx, yy) in BUSY:
                return False
    for yy in range(y, y + h):
        for xx in range(x, x + w):
            if not (0 <= xx < W and 0 <= yy < H):
                return False
            if L['Ground'][yy][xx] or L['Foreground'][yy][xx] or L['AboveSprites'][yy][xx]:
                return False
    if 40 <= x + w and x <= 52 and y + h >= 43:          # keep the skiff channel open
        return False
    return True


grove_rnd = random.Random(21)
placed = 0
for _ in range(4000):
    nm = grove_rnd.choice(('cypress', 'cypress2', 'cypress_s', 'cypress_s', 'dead_moss'))
    c0, r0, w, h = SPR[nm]
    x, y = grove_rnd.randint(0, W - w), grove_rnd.randint(0, H - h)
    if spot_free(x, y, w, h) and FULLW[y + h - 1][x + w // 2]:
        tree_spr(nm, x, y)
        for yy in range(y, y + h):
            for xx in range(x, x + w):
                BUSY.add((xx, yy))
        placed += 1
        if placed >= 16:
            break

# =============================== decorations ===============================
def is_water(x, y):
    return 0 <= x < W and 0 <= y < H and FULLW[y][x] and (x, y) not in WALK and (x, y) not in ROPE and \
        (x, y) not in WADE and not any(s[0] == x and s[1] == y for s in STILTS) and (x, y) not in LADDERS and \
        (x, y) not in DECK


def near_land(x, y, r=1):
    return any(0 <= x + dx < W and 0 <= y + dy < H and not FULLW[y + dy][x + dx]
               for dy in range(-r, r + 1) for dx in range(-r, r + 1))


def free3(x, y):
    return not L['Ground'][y][x] and not L['Foreground'][y][x]


def near_walk(x, y):
    return any((x + dx, y + dy) in WALK for dy in (-1, 0, 1) for dx in (-1, 0, 1))


rnd = random.Random(5)
for y in range(H):
    for x in range(W):
        if not is_water(x, y) or not free3(x, y):
            continue
        r_ = rnd.random()
        if near_land(x, y) and not near_walk(x, y):
            if r_ < 0.30:
                put('Ground', x, y, 'P', *sp('reeds%d' % rnd.randint(0, 2)))
            elif r_ < 0.40:
                put('Ground', x, y, 'P', *sp(rnd.choice(('lily', 'lily_f'))))
        elif not DEEPT[y][x]:
            if r_ < 0.06:
                put('Ground', x, y, 'P', *sp(rnd.choice(('lily', 'lily', 'lily_f'))))
            elif r_ < 0.08 and not near_walk(x, y):
                put('Ground', x, y, 'P', *sp('reeds2'))
for k in ('reeds0', 'reeds1', 'reeds2', 'lily', 'lily_f'):
    coll('P', *sp(k), [])
# land: tufts, mushrooms, stumps
TUFTS = [('D', 0, 2), ('D', 1, 2), ('D', 2, 2), ('D', 3, 2), ('D', 6, 2)]
for y in range(H):
    for x in range(W):
        bg = L['Background'][y][x]
        if not bg or not free3(x, y) or L['AboveSprites'][y][x]:
            continue
        if bg[0] == 'F' and bg[2] == 12:
            r_ = rnd.random()
            if r_ < 0.08:
                deco(x, y, *rnd.choice(TUFTS))
            elif r_ < 0.11:
                put('Ground', x, y, 'P', *sp(rnd.choice(('shroom0', 'shroom1', 'shroom1'))))
for t in TUFTS:
    coll(*t, [])
for k in ('shroom0', 'shroom1', 'shroom2'):
    coll('P', *sp(k), [])
for (x, y) in [(10, 22), (33, 22), (40, 33), (12, 31)]:
    prop(x, y, 'N', 2, 13, [(2, 4, 12, 10)]) if False else None
# stumps and a frog or two
for (x, y) in [(11, 19), (29, 29), (46, 3), (60, 27)]:
    if free3(x, y):
        prop(x, y, 'N', 4, 8, [(2, 4, 12, 10)])
for (x, y) in [(15, 33), (33, 43), (40, 28), (8, 37)]:
    if is_water(x, y):
        put('Ground', x, y, 'P', *sp('lily'))
        put('Foreground', x, y, 'P', *sp('frog'))
coll('P', *sp('frog'), [])

# lanterns along the boardwalks (standing in the water beside them), about every five tiles
def lamp_ok(x, y):
    return is_water(x, y) and y > 0 and free3(x, y) and L['AboveSprites'][y - 1][x] is None and \
        L['AboveSprites'][y][x] is None and not (41 <= x <= 52 and y >= 43) and (x, y) not in BUSY and \
        all(max(abs(x - a), abs(y - b)) >= 5 for (a, b) in LIGHTS)


for (x, y) in sorted(WALK, key=lambda t: (t[1] * 7 + t[0] * 3) % 11):
    for (ax, ay) in ((x, y + 1), (x, y - 1), (x + 1, y), (x - 1, y)):
        if lamp_ok(ax, ay):
            put('AboveSprites', ax, ay - 1, 'P', *sp('lantern_post'))
            prop(ax, ay, 'P', sp('lantern_post')[0], sp('lantern_post')[1] + 1, [(3, 9, 6, 7)])
            LIGHTS.append((ax, ay))
            break
# eel traps, a rowboat, nets by the streets
for (x, y) in [(9, 42), (36, 43), (41, 13), (26, 21)]:
    if is_water(x, y):
        put('Ground', x, y, 'P', *sp('cray_trap'))
coll('P', *sp('cray_trap'), [(2, 4, 12, 12)])
# wisps and fireflies over the water and the graveyard
for (x, y) in [(39, 24), (5, 36), (63, 14), (33, 34), (12, 46), (46, 11), (1, 39), (66, 36), (28, 47)]:
    if L['AboveSprites'][y][x] is None:
        put('AboveSprites', x, y, 'P', *sp('wisp' if (x + y) % 3 else 'wisp2'))
for (x, y) in [(10, 15), (26, 31), (44, 33), (62, 20), (3, 27), (58, 17), (34, 25), (16, 47), (8, 10)]:
    if L['AboveSprites'][y][x] is None:
        put('AboveSprites', x, y, 'P', *sp('fireflies'))
# mist banks over open water
for (nm, x, y) in [('mist_a', 2, 34), ('mist_b', 58, 43), ('mist_a', 38, 46), ('mist_c', 10, 47),
                   ('mist_a', 64, 18), ('mist_b', 0, 5), ('mist_c', 40, 0), ('mist_a', 26, 32), ('mist_c', 59, 37),
                   ('mist_c', 14, 14), ('mist_a', 6, 27)]:
    mist(nm, x, y)
# pools of warm lamplight under every lamp (Ground layer, under sprites; never over a solid prop)
pc, pr = sp('light_pool')
SOFT = {('P',) + sp(k) for k in ('reeds0', 'reeds1', 'reeds2', 'lily', 'lily_f', 'shroom0', 'shroom1')} | \
    {('D', 0, 2), ('D', 1, 2), ('D', 2, 2), ('D', 3, 2), ('D', 6, 2)}
for (lx, ly) in LIGHTS:
    for dy in range(3):
        for dx in range(3):
            x, y = lx - 1 + dx, ly - 1 + dy
            if 0 <= x < W and 0 <= y < H and (L['Ground'][y][x] is None or L['Ground'][y][x] in SOFT):
                put('Ground', x, y, 'P', pc + dx, pr + dy)
for dy in range(3):
    for dx in range(3):
        coll('P', pc + dx, pr + dy, [])

# =============================== objects: exits, npc spots ===============================
tobj('exit', 'exit_west_causeway', 0, 25, 1, 2, props=[('dir', 'W'), ('kind', 'causeway')])
tobj('exit', 'exit_north_boardwalk', 21, 0, 2, 1, props=[('dir', 'N'), ('kind', 'boardwalk')])
tobj('exit', 'exit_south_skiff_channel', 44, H - 1, 4, 1, props=[('dir', 'S'), ('kind', 'skiff_channel')])
for role, (x, y) in [('innkeeper', (26, 42)), ('apothecary', (27, 21)), ('shopkeep', (15, 21)),
                     ('witch', (5, 45)), ('poleman', (46, 42)), ('gravedigger', (51, 13)), ('priest', (47, 15)),
                     ('eel_fisher', (55, 34)), ('eel_fisher', (52, 41)), ('child', (20, 24)),
                     ('eel_fisher', (36, 27)), ('eel_fisher', (39, 28)),
                     ('hunter', (64, 26))]:
    tobj('npc_spot', 'npc_' + role, x, y, props=[('role', role)])
