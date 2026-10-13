# ===================== PLAINS MARKET TOWN LAYOUT (exec'd by town.py) =====================
# Crossroads: N-S road x33-35, E-W road y24-26. Market square x22-47, y13-33, grain hall in the middle.
# Lanes: farm lane y11-12, back lane y18-19 (NW), lane 1 y34-35 (runs off both edges),
#        Orchard Lane (SW) y41-42 then y44-45 off the west edge, Wheat Lane (SE) y41-42.
SQ = (22, 13, 47, 33)

# ---------- water: duck pond behind the store ----------
vrect(WATER, 49, 15, 53, 18)
vrect(WATER, 50, 14, 52, 19)

# ---------- roads (dirt; autotiled at the end) ----------
road(33, 0, 35, 12)                   # north road
road(33, 34, 35, H - 1)               # south road
road(0, 24, 21, 26)                   # west road
road(48, 24, W - 1, 26)               # east road
road(*SQ)                             # square (cobbled on Ground)
road(6, 11, 57, 12)                   # farm lane (north)
road(0, 18, 21, 19)                   # back lane (north-west), runs off west as a field track
road(11, 20, 11, 23)                  # passage between coach office and inn
road(0, 23, 10, 29)                   # coach yard
road(0, 34, W - 1, 35)                # lane 1 (south), runs out into the fields both ways
road(18, 41, 32, 42); road(18, 41, 19, 45); road(0, 44, 19, 45)    # Orchard Lane
road(36, 41, 52, 42); road(52, 43, 53, 47)                           # Wheat Lane + chicken-run track
road(57, 36, 58, 36); road(65, 36, 65, 36)                           # pen gates
cobble(*SQ)

# ---------- the market square ----------
HX, HY = 28, 17                       # grain hall 13x6: roof rows 17-19, walls 20-21, plinth 22
gh = SPR['grain_hall']
stamp('P', gh[0], gh[1], 13, 6, HX, HY, above=2)
for dy in range(2, 5):
    for dx in range(13):
        coll('P', gh[0] + dx, gh[1] + dy, FULL)
for dx in range(13):                  # plinth row: walk-on, drawn on Ground under the eaves
    L['Foreground'][HY + 5][HX + dx] = None
    put('Ground', HX + dx, HY + 5, 'P', gh[0] + dx, gh[1] + 5)
gd = SPR['grain_hall_door']
HALL_DOOR = (HX + gd[0], HY + gd[1])
door('grain_hall', 'grain_hall', *HALL_DOOR)
sign('sign_grain', HX + 1, HY + 3)
sign('sign_market', HX + 9, HY + 3)

# grain trade on the west side of the hall: carts, sacks, crates
stamp('E', 0, 3, 2, 2, 23, 15, above=1)          # hand cart
coll('E', 0, 4, [(2, 0, 14, 12)]); coll('E', 1, 4, [(0, 0, 13, 12)])
for i, (x, y) in enumerate([(25, 14), (26, 14), (25, 15), (23, 19), (24, 19), (23, 20), (26, 21)]):
    sack(x, y, i)
for (x, y) in [(27, 14), (22, 21), (22, 22)]:
    crate(x, y)
hay(26, 18); hay(23, 22)
# produce on the east side
for i, (c, r) in enumerate([(16, 13), (17, 13), (18, 13)]):
    prop(42 + i, 15, 'H', c, r, [(1, 3, 14, 13)])
for i, (c, r) in enumerate([(16, 15), (17, 15)]):
    prop(45 + i, 19, 'H', c, r, [(1, 3, 14, 13)])
stamp('E', 2, 3, 2, 2, 44, 21, above=1)          # covered cart of vegetables
coll('E', 2, 4, [(4, 0, 12, 12)]); coll('E', 3, 4, [(0, 0, 15, 12)])
barrel(46, 15); barrel(46, 16); crate(42, 19)

# market cross: grey-paved ring round the well, benches, trees and tubs
for y in range(27, 32):
    for x in range(30, 39):
        cc = 0 if x == 30 else 2 if x == 38 else 1
        rr = 0 if y == 27 else 2 if y == 31 else 1
        put('Ground', x, y, 'P', SPR['paving'][0] + cc, SPR['paving'][1] + rr)
stamp('E', 7, 1, 1, 2, 34, 28, above=1)          # roofed well
coll('E', 7, 2, [(2, 0, 12, 13)])
for x0 in (30, 36):
    for i in range(3):
        prop(x0 + i, 30, 'E', 2 + i, 10, [(0, 4, 16, 10)])
tree(27, 28, (16, 0)); tree(40, 28, (18, 0))


def tub(x, y):
    stamp('E', 0, 7, 1, 2, x, y - 1, above=1)
    coll('E', 0, 8, [(3, 4, 10, 12)])


for (x, y) in [(31, 28), (37, 28), (29, 23), (41, 23), (30, 14), (38, 14)]:
    tub(x, y)
# stalls in loose clusters, not rows
for (nm, x, y) in [('stall_red', 23, 27), ('stall_yel', 26, 29), ('stall_grn', 23, 31),
                   ('stall_blu', 42, 27), ('stall_red', 45, 29), ('stall_yel', 42, 31)]:
    stall(nm, x, y)
for (x, y) in [(25, 28), (28, 31), (44, 28), (47, 31), (22, 29)]:
    sack(x, y, x)
for (x, y) in [(29, 32), (40, 32), (46, 27)]:
    crate(x, y)
# worn spots and litter break up the cobbles (Foreground, no collision)
for (x, y, c, r) in [(24, 25, 4, 0), (39, 25, 0, 0), (31, 33, 4, 0), (44, 33, 1, 0), (27, 16, 0, 0), (41, 14, 4, 0),
                     (36, 32, 1, 0), (25, 24, 3, 0)]:
    put('Foreground', x, y, 'D', c, r); coll('D', c, r, [])
stamp('E', 11, 1, 3, 1, 37, 23)                  # notice board by the hall door
for c in (11, 12, 13):
    coll('E', c, 1, [(0, 4, 16, 9)])
tobj('quest_board', 'notice_board', 37, 23, 3, 1)
for (x, y) in [(32, 22), (36, 22), (21, 12), (48, 12), (21, 32), (48, 32)]:
    lamp(x, y)

# ---------- north-west: windmill on a rise, granary + silos ----------
RC, RR = SPR['rise'][0], SPR['rise'][1]
RX0, RY0, RX1, RY1 = 2, 1, 13, 10     # plateau incl. edges; face row RY1
for y in range(RY0, RY1 + 1):
    for x in range(RX0, RX1 + 1):
        cc = 1 if x == RX0 else 3 if x == RX1 else 2
        rr = 0 if y == RY0 else 2 if y == RY1 else 1
        put('Ground', x, y, 'P', RC + cc, RR + rr)
for cc in (1, 2, 3):
    for rr in (0, 1, 2):
        if (cc, rr) != (2, 1):
            coll('P', RC + cc, RR + rr, FULL)
STAIR_X = 8
put('Ground', STAIR_X, RY1, 'R', 5, 3)
coll('R', 5, 3, [])
MX, MY = 6, 2                          # 5x7 windmill (sails baked in); body cols 7-9, rows 4-8
wmc = SPR['windmill']
stamp('P', wmc[0], wmc[1], 5, 7, MX, MY, above=4)
for dy in range(4, 7):
    for dx in range(5):
        coll('P', wmc[0] + dx, wmc[1] + dy, FULL if 1 <= dx <= 3 else [])
door('windmill', 'windmill', MX + 2, MY + 6)
for (x, y) in [(4, 8), (5, 8)]:
    sack(x, y, x)
hay(11, 8)
tree(3, 3, (16, 0)); tree(10, 2, (18, 0))
# granary (6x3) + three silos
GX, GY = 16, 8
c0, r0, w, h = SPR['granary']
building('P', c0, r0, w, h, GX, GY, 1)
gdd = SPR['granary_door']
door('granary', 'granary', GX + gdd[0], GY + gdd[1])
pstamp('silo_big', 23, 5, above=2)
pstamp('silo_small', 26, 6, above=2)
pstamp('silo_small', 28, 6, above=2)
for i, (x, y) in enumerate([(22, 9), (22, 10), (30, 9), (30, 10), (31, 10)]):
    sack(x, y, i)
crate(15, 10); barrel(15, 9)
stamp('E', 0, 3, 2, 2, 30, 6, above=1)           # grain cart by the silos
crops(15, 1, 22, 6, [(9, 9), (10, 9)])            # vegetable field + scarecrow
prop(19, 3, 'E', 15, 0, [(5, 8, 6, 8)])
hedge_v(0, 0, 9)

# ---------- north-west lower: cottages with front yards on the back lane ----------
cottage('h_nw1', 1, 13, 'orange_straw', 18)
cottage('h_nw2', 6, 14, 'red_moss', 18)
hedge_v(11, 13, 16)
cottage('h_nw3', 13, 13, 'beige_teal', 18)
cottage('h_nw4', 17, 14, 'orange2', 18)

# ---------- the stagecoach post: coach house + booking office, coach, horse, rail, trough, sign ----------
c0, r0, w, h = SPR['stable']
building('P', c0, r0, w, h, 0, 20, 1)                       # coach house x0-7, rows 20-22
door('coach_house', 'coach_house', 3, 22)
sign('sign_coach', 3, 21)
building('H', 19, 19, 3, 3, 8, 20, 1, doors=[(1, 2)], overlay=(26, 19))   # booking office x8-10
door('coach_office', 'coach_office', 9, 22)
c0, r0, w, h = SPR['coach']
CX, CY = 2, 26
stamp('P', c0, r0, w, h, CX, CY, above=1)
for dx in range(w):
    coll('P', c0 + dx, r0 + 1, FULL if dx >= 2 else [])
    coll('P', c0 + dx, r0 + 2, [(0, 0, 16, 12)])
stamp('E', 11, 2, 3, 2, 7, 23, above=1)                     # hitching rail
for c in (11, 12, 13):
    coll('E', c, 3, [(0, 4, 16, 10)])
animal('horse_l', 7, 25)
put('Foreground', 10, 25, 'E', 6, 10); coll('E', 6, 10, [(1, 4, 14, 10)])   # water trough
sign_on_posts('sign_coach', 8, 27)
hay(10, 29); prop(1, 29, 'E', 9, 0, [(2, 4, 12, 12)]); crate(0, 29)      # luggage
tobj('station', 'stagecoach_post', 0, 20, 11, 10, props=[('kind', 'stagecoach'), ('label', 'Plains stagecoach')])

# ---------- the inn ----------
IX, IY = 12, 20
c0, r0, w, h = SPR['inn']
building('P', c0, r0, w, h, IX, IY, 1)
idd = SPR['inn_door']
door('inn', 'inn', IX + idd[0], IY + idd[1])
sign('sign_inn', IX + 1, IY + 2)
barrel(19, 22); barrel(19, 23)

# ---------- south-west: smithy, homes ----------
cottage('h_sw1', 1, 31, 'orange_slate', 34, fence=False)
cottage('h_sw2', 6, 30, 'gable', 34)
SMX, SMY = 14, 31
building('H', 23, 0, 3, 3, SMX, SMY, 1)
door('smithy', 'smithy', SMX + 1, SMY + 2)
stamp('H', 29, 4, 3, 4, 18, 30, above=2)          # forge furnace
for dx in range(3):
    for dy in (2, 3):
        coll('H', 29 + dx, 4 + dy, FULL)
stamp('H', 29, 8, 2, 2, 15, 28, above=1)          # anvil bench
coll('H', 29, 9, [(0, 4, 16, 10)]); coll('H', 30, 9, [(0, 4, 16, 10)])
sign('sign_smith', SMX, SMY - 1)
barrel(17, 29); crate(14, 29)
# Orchard Lane cluster: staggered cottages, gardens, a washing line
cottage('h_ol1', 1, 38, 'red_straw', 44)
cottage('h_ol2', 6, 39, 'orange_teal', 44)
cottage('h_ol3', 11, 37, 'beige_moss', 44)
cottage('h_ol4', 15, 36, 'thatchhut', 41, fence=False)
cottage('h_ol5', 21, 36, 'orange2_slate', 41)
cottage('h_ol6', 26, 37, 'beige_straw', 41)
stamp('E', 14, 2, 2, 2, 8, 36, above=1)
coll('E', 14, 3, [(3, 6, 2, 10)]); coll('E', 15, 3, [(11, 6, 2, 10)])
tree(30, 37, (16, 0))
for i, x in enumerate((0, 4, 8, 12)):
    big_tree(x, 46, (3, 0, 9)[i % 3])
big_tree(16, 46, 9)
crops(23, 44, 31, 47, [(9, 9), (10, 9), (6, 10)])
prop(27, 45, 'E', 15, 0, [(5, 8, 6, 8)])         # scarecrow
hedge_v(21, 43, 49)
hedge_h(23, 31, 48)

# ---------- north-east: chapel + churchyard, homes, orchard, field ----------
CHX, CHY = 39, 6
c0, r0, w, h = SPR['chapel']
building('P', c0, r0, w, h, CHX, CHY, 2)
cdd = SPR['chapel_door']
door('chapel', 'chapel', CHX + cdd[0], CHY + cdd[1])
fence_box(43, 4, 49, 10, bgaps=(46,))
for (x, y, c, r) in [(44, 5, 4, 2), (46, 5, 3, 2), (48, 5, 5, 2), (45, 8, 5, 2), (47, 8, 4, 2)]:
    prop(x, y, 'E', c, r, [(3, 6, 10, 10)])
big_tree(36, 6, 0)                                 # blossom tree by the chapel
cottage('h_ne1', 51, 6, 'red_teal', 11)
cottage('h_ne2', 56, 8, 'smallwood', 11, fence=False)
crops(50, 0, 58, 4, [(10, 9), (6, 10)])
for i, (x, y) in enumerate([(60, 0), (64, 0), (60, 4), (64, 4), (62, 8)]):
    big_tree(x, y, (3, 9)[i % 2])
hedge_v(68, 0, 10)

# ---------- east road north side: general store + homes, back gardens, duck pond ----------
SX, SY = 49, 21
building('H', 16, 0, 3, 3, SX, SY, 1)
door('store', 'general_store', SX + 1, SY + 2)
sign('sign_store', SX, SY - 1)
crate(52, 23); barrel(52, 22)
cottage('h_e1', 54, 20, 'orange', 24, fence=False)
cottage('h_e2', 59, 21, 'beige_slate', 24)
cottage('h_e3', 64, 19, 'red', 24)
for (gx, gy0, gy1) in [(54, 15, 19), (59, 16, 20), (64, 13, 18)]:
    fence_box(gx, gy0, gx + 3, gy1)
    for x in (gx + 1, gx + 2):
        put('Foreground', x, gy0 + 1, 'N', 9, 9); put('Foreground', x, gy0 + 2, 'N', 10, 9)
tree(58, 13, (16, 0))

# ---------- south-east: homes, livestock pens, wheat ----------
cottage('h_se1', 49, 28, 'orange2_teal', 34)
cottage('h_se2', 54, 30, 'red_straw', 34, fence=False)
cottage('h_se3', 59, 29, 'orange_slate', 34)
cottage('h_se4', 64, 30, 'beige', 34, fence=False)
hedge_v(68, 27, 33)
cottage('h_ws1', 37, 37, 'orange2_moss', 41)
cottage('h_ws2', 42, 36, 'gable', 41)
cottage('h_ws3', 47, 38, 'red_slate', 41, fence=False)
fence_box(54, 37, 61, 43, gaps=(57, 58))         # cattle pen
animal('cow', 55, 39); animal('cow_l', 58, 41); animal('donkey', 60, 39)
put('Foreground', 59, 38, 'E', 6, 10)
fence_box(63, 37, 68, 41, gaps=(65,))            # pig pen
animal('pig', 64, 39); animal('pig2', 66, 40); animal('pig', 67, 38)
fence_box(54, 44, 61, 49, lgaps=(46, 47))        # chicken run + coop
building('H', 0, 7, 3, 3, 58, 45, 1)
for (x, y) in [(55, 47), (56, 48), (57, 46)]:
    animal('chicken' if x % 2 else 'chicken2', x, y)
hay(63, 44); hay(64, 45)
wheat(37, 44, 50, 47)
hedge_h(37, 50, 48)
for (x, y) in [(39, 46), (44, 45), (48, 46)]:
    hay(x, y)

# ---------- soft edges: field trees ----------
for (x, y) in [(66, 11), (66, 44), (36, 44)]:
    tree(x, y, (0, 0))

autotile()

# ---------- decorations ----------
FLOWERS = [('N', 0, 11), ('N', 1, 11), ('N', 3, 11), ('N', 6, 11), ('N', 2, 11), ('D', 5, 2), ('D', 2, 0)]
for (xx, yy) in YARD:
    bg = L['Background'][yy][xx]
    if bg and bg[0] == 'F' and bg[2] == 12 and not L['Foreground'][yy][xx] and not L['Ground'][yy][xx]:
        deco(xx, yy, *random.choice(FLOWERS))
TUFTS = [('D', 0, 2), ('D', 1, 2), ('D', 2, 2), ('D', 3, 2), ('D', 3, 0), ('D', 6, 2)]
for y in range(H):
    for x in range(W):
        bg = L['Background'][y][x]
        if bg and bg[0] == 'F' and bg[2] == 12 and not L['Foreground'][y][x] and not L['Ground'][y][x] \
                and not L['AboveSprites'][y][x] and random.random() < 0.07:
            deco(x, y, *random.choice(TUFTS + FLOWERS[:3]))
for t in FLOWERS + TUFTS:
    coll(t[0], t[1], t[2], [])

# ---------- objects: exits, npc spots ----------
tobj('exit', 'exit_north', 33, 0, 3, 1, props=[('dir', 'N')])
tobj('exit', 'exit_south', 33, H - 1, 3, 1, props=[('dir', 'S')])
tobj('exit', 'exit_west', 0, 24, 1, 3, props=[('dir', 'W')])
tobj('exit', 'exit_east', W - 1, 24, 1, 3, props=[('dir', 'E')])
for nm, (x, y, d) in {'track_west_backlane': (0, 18, 'W'), 'track_west_lane1': (0, 34, 'W'),
                      'track_east_lane1': (W - 1, 34, 'E'), 'track_west_orchard': (0, 44, 'W')}.items():
    tobj('exit', nm, x, y, 1, 2, props=[('dir', d), ('kind', 'field_track')])
for role, (x, y) in [('innkeeper', (15, 24)), ('shopkeep', (50, 24)), ('smith', (16, 30)), ('farmer', (53, 44)),
                     ('priest', (40, 11)), ('child', (33, 29)), ('coachman', (7, 28)), ('farmer', (24, 17)),
                     ('merchant', (24, 29)), ('merchant', (43, 29)), ('merchant', (27, 31)), ('elder', (32, 29)),
                     ('miller', (9, 9)), ('stablehand', (6, 24)), ('farmer', (20, 2))]:
    tobj('npc_spot', 'npc_' + role, x, y, props=[('role', role)])
