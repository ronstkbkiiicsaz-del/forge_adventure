"""Footprint builders: settlements (towns, hamlets, farmsteads), landmarks and travel stations.

Each builder stamps terrain codes into P.terrain at true size, appends render objects to P.objects
(sprite key + tile rect) and returns a site record. Builders never look outside their footprint except
for stations that need a coast (piers).
"""
import math

import numpy as np

from .core import (GRASS, ROAD, TREE, ROCK, RIDGE, WATER, OCEAN, TOWN, HOUSE, POI, FENCE, RUIN, DOCK, FIELD, GRAVE,
                   CLEARABLE, stamp)

HOUSE_W, HOUSE_H = 4, 3


def obj(P, key, y, x, h, w, **kw):
    o = dict(key=key, y=int(y), x=int(x), h=int(h), w=int(w))
    o.update(kw)
    P.objects.append(o)
    return o


def rect_ok(P, y, x, h, w, allowed=CLEARABLE, margin=0, check_reserved=True):
    y0, x0, y1, x1 = y - margin, x - margin, y + h + margin, x + w + margin
    if y0 < 2 or x0 < 2 or y1 > P.H - 2 or x1 > P.W - 2:
        return False
    if check_reserved and P.reserved[y0:y1, x0:x1].any():
        return False
    return np.isin(P.terrain[y0:y1, x0:x1], allowed).all()


def reserve(P, y, x, h, w, margin=2):
    P.reserved[max(0, y - margin):y + h + margin, max(0, x - margin):x + w + margin] = True


def put_house(P, y, x, style, rng, key=None):
    if not (P.terrain[y:y + HOUSE_H, x:x + HOUSE_W] == GRASS).all():
        return False
    P.terrain[y:y + HOUSE_H, x:x + HOUSE_W] = HOUSE
    obj(P, key or f"house:{style}", y, x, HOUSE_H, HOUSE_W, v=int(rng.integers(0, 1 << 16)))
    return True


def fence_ring(P, y, x, h, w, gaps=()):
    t = P.terrain
    for (yy, xx) in [(y, slice(x, x + w)), (y + h - 1, slice(x, x + w))]:
        seg = t[yy, xx]
        seg[seg == GRASS] = FENCE
    for (yy, xx) in [(slice(y, y + h), x), (slice(y, y + h), x + w - 1)]:
        seg = t[yy, xx]
        seg[seg == GRASS] = FENCE
    for gy, gx in gaps:
        if t[gy, gx] == FENCE:
            t[gy, gx] = GRASS


# ------------------------------------------------------------------------------------------- settlements
def build_town(P, cy, cx, h, w, style, rng, name):
    y, x = cy - h // 2, cx - w // 2
    t = P.terrain
    t[y:y + h, x:x + w] = GRASS
    # main cross streets (3 wide) to the four gates, plaza in the middle
    t[cy - 1:cy + 2, x:x + w] = TOWN
    t[y:y + h, cx - 1:cx + 2] = TOWN
    ph, pw = max(5, h // 5), max(7, w // 5)
    t[cy - ph // 2:cy + ph // 2 + 1, cx - pw // 2:cx + pw // 2 + 1] = TOWN
    streets_h = [cy]
    if h >= 34:
        for sy in (y + h // 4, y + 3 * h // 4):
            t[sy:sy + 2, x + 3:x + w - 3] = TOWN
            streets_h.append(sy)
    obj(P, "well", cy - 1, cx - 1, 2, 2) if rng.random() < 0.7 else None
    # houses face the streets, one tile of front yard, gaps between
    for sy in streets_h:
        top = sy - (1 if sy == cy else 0)
        bot = sy + (2 if sy == cy else 2)
        for hx in range(x + 2, x + w - HOUSE_W - 1, HOUSE_W + 2):
            if abs(hx + HOUSE_W / 2 - cx) < pw / 2 + 3:
                continue
            put_house(P, top - 1 - HOUSE_H, hx + int(rng.integers(0, 2)), style, rng)
            put_house(P, bot + 1, hx + int(rng.integers(0, 2)), style, rng)
    # houses along the north-south street too
    for hy in range(y + 2, y + h - HOUSE_H - 1, HOUSE_H + 2):
        if abs(hy + 1 - cy) < ph / 2 + 3:
            continue
        put_house(P, hy, cx - 2 - 1 - HOUSE_W, style, rng)
        put_house(P, hy, cx + 2 + 1, style, rng)
    # a landmark building (inn / hall) on the plaza edge
    hy, hx = cy - ph // 2 - 1 - 4, cx + pw // 2 - 4
    if (t[hy:hy + 4, hx:hx + 4] == GRASS).all():
        t[hy:hy + 4, hx:hx + 4] = HOUSE
        obj(P, f"hall:{style}", hy, hx, 4, 4)
    # yard trees and gardens
    ys, xs = np.nonzero(t[y + 1:y + h - 1, x + 1:x + w - 1] == GRASS)
    for k in rng.permutation(len(ys))[: max(2, len(ys) // 60)]:
        t[y + 1 + ys[k], x + 1 + xs[k]] = TREE
    # town fence with gates where the streets leave
    fence_ring(P, y, x, h, w, gaps=[])
    for g in [(y, cx - 1), (y, cx), (y, cx + 1), (y + h - 1, cx - 1), (y + h - 1, cx), (y + h - 1, cx + 1),
              (cy - 1, x), (cy, x), (cy + 1, x), (cy - 1, x + w - 1), (cy, x + w - 1), (cy + 1, x + w - 1)]:
        t[g] = TOWN
    reserve(P, y, x, h, w, 3)
    gates = [(y - 1, cx), (y + h, cx), (cy, x - 1), (cy, x + w)]
    return dict(kind="town", name=name, y=cy, x=cx, h=h, w=w, top=y, left=x, style=style, gates=gates)


def build_hamlet(P, cy, cx, h, w, style, rng, name):
    y, x = cy - h // 2, cx - w // 2
    t = P.terrain
    t[y:y + h, x:x + w] = GRASS
    t[cy - 2:cy + 2, cx - 3:cx + 3] = TOWN
    t[cy - 1:cy + 1, x:x + w] = TOWN       # lane through
    spots = [(y, x + 1), (y, x + w - 5), (y + h - 3, x + 1), (y + h - 3, x + w - 5)]
    if w >= 20:
        spots += [(y, x + w // 2 - 2 + 5), (y + h - 3, x + w // 2 - 2 - 5)]
    n = int(rng.integers(3, 5)) + (2 if w >= 20 else 0)
    for (hy, hx) in [spots[i] for i in rng.permutation(len(spots))[:n]]:
        put_house(P, hy, hx, style, rng)
    ys, xs = np.nonzero(t[y:y + h, x:x + w] == GRASS)
    for k in rng.permutation(len(ys))[:2]:
        t[y + ys[k], x + xs[k]] = TREE
    reserve(P, y, x, h, w, 3)
    return dict(kind="hamlet", name=name, y=cy, x=cx, h=h, w=w, top=y, left=x, style=style,
                gates=[(cy, x - 1), (cy, x + w)])


def build_farmstead(P, cy, cx, h, w, style, rng, name):
    y, x = cy - h // 2, cx - w // 2
    t = P.terrain
    t[y:y + h, x:x + w] = GRASS
    put_house(P, y, x, "farm" if style in ("farm", "forest", "mountain") else style, rng)
    if w >= 14 and h >= 10:
        # barn + fenced field
        if (t[y:y + 3, x + 5:x + 9] == GRASS).all():
            t[y:y + 3, x + 5:x + 9] = HOUSE
            obj(P, "barn", y, x + 5, 3, 4)
        fy, fx, fh, fw = y + 4, x, h - 4, w
    else:
        fy, fx, fh, fw = y + 4, x, h - 4, w
    if fh >= 4 and fw >= 5:
        t[fy + 1:fy + fh - 1, fx + 1:fx + fw - 1] = FIELD
        fence_ring(P, fy, fx, fh, fw, gaps=[(fy, fx + fw // 2), (fy, fx + fw // 2 + 1)])
        obj(P, "field", fy + 1, fx + 1, fh - 2, fw - 2, crop=int(rng.integers(0, 2)))
    for _ in range(int(rng.integers(1, 3))):
        hy, hx = y + int(rng.integers(0, 3)), x + 4 + int(rng.integers(0, 2))
        if t[hy, hx] == GRASS:
            t[hy, hx] = ROCK
            obj(P, "hay", hy, hx, 1, 1)
    reserve(P, y, x, h, w, 2)
    return dict(kind="farmstead", name=name, y=cy, x=cx, h=h, w=w, top=y, left=x, style=style, gates=[(y + h, cx)])


# ------------------------------------------------------------------------------------------- landmarks
def build_landmark(P, kind, cy, cx, rng, name):
    t = P.terrain
    if kind == "graveyard":
        h, w = int(rng.integers(10, 13)), int(rng.integers(12, 17))
        y, x = cy - h // 2, cx - w // 2
        t[y:y + h, x:x + w] = GRAVE
        # iron fence around the yard (set explicitly: the floor is GRAVE, not GRASS)
        t[y, x:x + w] = FENCE; t[y + h - 1, x:x + w] = FENCE; t[y:y + h, x] = FENCE; t[y:y + h, x + w - 1] = FENCE
        t[y + h - 1, x + w // 2 - 1:x + w // 2 + 1] = GRASS
        obj(P, "fence:iron", y, x, h, w)
        c = x + w // 2 - 1
        t[y + 1:y + 3, c:c + 3] = RUIN
        obj(P, "crypt", y + 1, c, 2, 3)
        for gy in range(y + 4, y + h - 1, 2):
            for gx in range(x + 1, x + w - 1):
                if gx in (x + w // 2 - 1, x + w // 2) or rng.random() > 0.8:
                    continue
                obj(P, "headstone", gy, gx, 1, 1)
        reserve(P, y, x, h, w, 3)
        return dict(kind=kind, name=name, y=cy, x=cx, h=h, w=w, top=y, left=x, entry=(y + h, x + w // 2))
    if kind == "battlefield":
        h, w = 16, 20
        y, x = cy - h // 2, cx - w // 2
        # open killing ground in the middle (dead trees may stand around the edges)
        c = t[cy - 4:cy + 5, cx - 6:cx + 7]
        c[np.isin(c, (TREE, ROCK))] = GRASS
        for _ in range(int(rng.integers(5, 8))):
            ry, rx = y + int(rng.integers(1, h - 3)), x + int(rng.integers(1, w - 4))
            if rng.random() < 0.5 and (t[ry, rx:rx + 3] == GRASS).all():
                t[ry, rx:rx + 3] = RUIN
                obj(P, "ruin_wall", ry, rx, 1, 3)
            elif (t[ry:ry + 2, rx:rx + 2] == GRASS).all():
                t[ry:ry + 2, rx:rx + 2] = RUIN
                obj(P, "ruin_block", ry, rx, 2, 2)
        for _ in range(int(rng.integers(30, 50))):
            by, bx = y + int(rng.integers(0, h)), x + int(rng.integers(0, w))
            if t[by, bx] == GRASS:
                obj(P, "bones", by, bx, 1, 1)
        reserve(P, y, x, h, w, 2)
        return dict(kind=kind, name=name, y=cy, x=cx, h=h, w=w, top=y, left=x, entry=(cy, cx))
    if kind == "ruin":
        h, w = 8, 9
        y, x = cy - h // 2, cx - w // 2
        t[y:y + h, x:x + w][np.isin(t[y:y + h, x:x + w], (TREE, ROCK))] = GRASS
        t[y + 1, x + 1:x + 7] = RUIN; obj(P, "ruin_wall", y + 1, x + 1, 1, 3); obj(P, "ruin_wall", y + 1, x + 4, 1, 3)
        t[y + 2:y + 6, x + 1] = RUIN; obj(P, "ruin_wall_v", y + 2, x + 1, 4, 1)
        t[y + 5:y + 7, x + 6:x + 8] = RUIN; obj(P, "ruin_block", y + 5, x + 6, 2, 2)
        reserve(P, y, x, h, w, 2)
        return dict(kind=kind, name=name, y=cy, x=cx, h=h, w=w, top=y, left=x, entry=(cy + 1, cx))
    if kind == "standing_stones":
        h = w = 9
        y, x = cy - 4, cx - 4
        t[y:y + h, x:x + w][np.isin(t[y:y + h, x:x + w], (TREE, ROCK))] = GRASS
        for k in range(7):
            a = 2 * math.pi * k / 7
            sy, sx = int(round(cy + 3.6 * math.sin(a))), int(round(cx + 3.6 * math.cos(a)))
            t[sy, sx] = RUIN
            obj(P, "standing_stone", sy, sx, 1, 1)
        reserve(P, y, x, h, w, 2)
        return dict(kind=kind, name=name, y=cy, x=cx, h=h, w=w, top=y, left=x, entry=(cy, cx))
    if kind == "lone_tower":
        y, x = cy - 1, cx - 1
        t[y - 1:y + 4, x - 1:x + 3][np.isin(t[y - 1:y + 4, x - 1:x + 3], (TREE, ROCK))] = GRASS
        t[y:y + 2, x:x + 2] = RUIN
        obj(P, "tower", y, x, 2, 2, v=int(rng.integers(0, 3)))
        reserve(P, y, x, 2, 2, 3)
        return dict(kind=kind, name=name, y=cy, x=cx, h=2, w=2, top=y, left=x, entry=(y + 2, cx))
    if kind in ("cave", "dungeon"):
        y, x = cy, cx
        stamp(t, y + 1, x, 1.6, GRASS, where=np.isin(t, (TREE, ROCK, RIDGE)))
        t[y - 2:y, x - 1:x + 2] = HOUSE
        t[y, x] = POI
        obj(P, kind, y - 3, x - 1, 3, 3)
        reserve(P, y - 2, x - 1, 3, 3, 3)
        return dict(kind=kind, name=name, y=cy, x=cx, h=3, w=3, top=y - 2, left=x - 1, entry=(y, x))
    if kind in ("lighthouse", "shrine", "rare_resource"):
        y, x = cy - 1, cx - 1
        a = t[y - 1:y + 4, x - 1:x + 3]
        a[np.isin(a, (TREE, ROCK))] = GRASS
        t[y:y + 2, x:x + 2] = HOUSE if kind != "rare_resource" else RUIN
        obj(P, kind, y, x, 2, 2)
        reserve(P, y, x, 2, 2, 3)
        return dict(kind=kind, name=name, y=cy, x=cx, h=2, w=2, top=y, left=x, entry=(y + 2, cx))
    if kind in ("shipwreck", "treasure_cove"):
        h, w = (3, 4) if kind == "shipwreck" else (3, 3)
        y, x = cy - 1, cx - w // 2
        a = t[y:y + h, x:x + w]
        a[np.isin(a, (TREE, ROCK))] = GRASS
        if kind == "shipwreck":
            t[y + 1, x:x + 3] = RUIN
            obj(P, "shipwreck", y + 1, x, 1, 3)
            for _ in range(4):
                obj(P, "planks", y + int(rng.integers(0, 3)), x + int(rng.integers(0, 4)), 1, 1)
        else:
            t[y + 1, x + 1] = RUIN
            obj(P, "chest", y + 1, x + 1, 1, 1)
            obj(P, "bones", y + 2, x, 1, 1); obj(P, "bones", y, x + 2, 1, 1)
        reserve(P, y, x, h, w, 2)
        return dict(kind=kind, name=name, y=cy, x=cx, h=h, w=w, top=y, left=x, entry=(y + h, cx))
    if kind == "sea_cave":
        site = build_landmark(P, "cave", cy, cx, rng, name)
        P.objects[-1]["key"] = "sea_cave"
        site["kind"] = "sea_cave"
        return site
    if kind == "pirate_cove":
        h, w = 5, 7
        y, x = cy - 2, cx - 3
        a = t[y:y + h, x:x + w]
        a[np.isin(a, (TREE, ROCK))] = GRASS
        t[y:y + 3, x:x + 3] = HOUSE; obj(P, "tent", y, x, 3, 3, v=1)
        t[y + 3:y + 5, x + 4:x + 6] = HOUSE; obj(P, "campfire", y + 3, x + 4, 2, 2)
        t[y + 1, x + 5] = RUIN; obj(P, "chest", y + 1, x + 5, 1, 1)
        obj(P, "flag", y, x + 4, 1, 1)
        reserve(P, y, x, h, w, 2)
        return dict(kind=kind, name=name, y=cy, x=cx, h=h, w=w, top=y, left=x, entry=(y + h, x + 3))
    if kind == "orchard":
        h, w = 9, 12
        y, x = cy - h // 2, cx - w // 2
        t[y:y + h, x:x + w] = GRASS
        for oy in range(y + 1, y + h - 1, 3):
            for ox in range(x + 1, x + w - 1, 3):
                t[oy, ox] = TREE
                obj(P, "fruit_tree", oy, ox, 1, 1, v=(oy // 3) % 2)
        reserve(P, y, x, h, w, 2)
        return dict(kind=kind, name=name, y=cy, x=cx, h=h, w=w, top=y, left=x, entry=(y + h, cx))
    raise ValueError(kind)


LANDMARK_SIZE = {"graveyard": (13, 17), "battlefield": (16, 20), "ruin": (8, 9), "standing_stones": (9, 9),
                 "lone_tower": (5, 5), "cave": (5, 5), "dungeon": (5, 5), "orchard": (9, 12),
                 "lighthouse": (5, 5), "shrine": (5, 5), "shipwreck": (4, 5), "sea_cave": (5, 5), "pirate_cove": (6, 8),
                 "treasure_cove": (4, 4), "rare_resource": (5, 5)}


# ------------------------------------------------------------------------------------------- stations
STATION_SIZE = {"stagecoach": (5, 7), "caravan": (6, 8), "elfgrove": (7, 7), "skiff": (4, 5), "dock": (4, 5)}


def build_station(P, kind, y, x, rng, name, pier=None):
    """y,x = top-left of the footprint. pier = list of cells (sea/bog) for dock/skiff."""
    t = P.terrain
    h, w = STATION_SIZE[kind]
    t[y:y + h, x:x + w][np.isin(t[y:y + h, x:x + w], (TREE, ROCK))] = GRASS
    access = (y + h - 1, x + w // 2)
    if kind == "stagecoach":
        t[y:y + 3, x:x + 4] = HOUSE; obj(P, "post_house", y, x, 3, 4)
        t[y + 3:y + 5, x:x + 7] = TOWN
        t[y:y + 2, x + 5:x + 7] = HOUSE; obj(P, "wagon", y, x + 5, 2, 2)
        obj(P, "signpost", y + 2, x + 4, 1, 1)
    elif kind == "caravan":
        t[y:y + 3, x:x + 3] = HOUSE; obj(P, "tent", y, x, 3, 3)
        t[y:y + 3, x + 4:x + 7] = HOUSE; obj(P, "tent", y, x + 4, 3, 3, v=1)
        t[y + 3:y + 6, x:x + 8] = TOWN
        t[y + 4:y + 6, x + 3:x + 5] = HOUSE; obj(P, "campfire", y + 4, x + 3, 2, 2)
        obj(P, "wagon", y + 3, x + 6, 2, 2, v=1)
        t[y + 3:y + 5, x + 6:x + 8] = HOUSE
        access = (y + 5, x + 1)
    elif kind == "elfgrove":
        t[y:y + h, x:x + w] = GRASS
        for k in range(8):
            a = 2 * math.pi * k / 8
            ty, tx = int(round(y + 3 + 3 * math.sin(a))), int(round(x + 3 + 3 * math.cos(a)))
            if (ty, tx) != (y + 6, x + 3):
                t[ty, tx] = TREE
                obj(P, "grove_tree", ty, tx, 1, 1)
        t[y + 2:y + 4, x + 2:x + 4] = HOUSE; obj(P, "elf_tower", y + 2, x + 2, 2, 2)
        t[y + 6, x + 3] = TOWN
        t[y + 4:y + 6, x + 3] = TOWN
        access = (y + 6, x + 3)
    elif kind in ("dock", "skiff"):
        t[y:y + 3, x:x + 4] = HOUSE; obj(P, "boathouse" if kind == "dock" else "skiff_hut", y, x, 3, 4)
        t[y + 3, x:x + w] = TOWN
        access = (y + 3, x + 1)
    if pier:
        for c in pier:
            t[c] = DOCK
        obj(P, "boat" if kind == "dock" else "skiff", pier[-1][0], pier[-1][1], 1, 2, dir=pier_dir(pier))
    reserve(P, y, x, h, w, 2)
    return dict(kind="station", type=kind, name=name, y=access[0], x=access[1], top=y, left=x, h=h, w=w,
                pier=pier or [])


def pier_dir(pier):
    if len(pier) < 2:
        return (1, 0)
    return (int(np.sign(pier[1][0] - pier[0][0])), int(np.sign(pier[1][1] - pier[0][1])))
