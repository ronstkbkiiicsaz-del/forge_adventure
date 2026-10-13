"""PlaneGenerator prototype, WG1 round 3: generate(seed, profile) -> Plane.

Pipeline (each step is a function, run in this order; GLOBAL = needs whole-plane data, LOCAL = could be
computed per 64x64 chunk from (seed, chunk coords) if the noise were coordinate-hashed):
  1 layout        LOCAL   biome wedges/rings from warped polar coords + noise
  2 sea           GLOBAL  coastline, archipelago, island labelling (connected components)
  3 water         LOCAL   lakes / bogs (noise thresholds)            + GLOBAL clean-up of enclosed sea
  4 rivers        GLOBAL  priority-flood flow field, traced from sources
  5 volcanoes     GLOBAL  site choice (spacing); cones/flows are local stamps
  6 obstacles     LOCAL   clustered trees/rocks + lanes, ridges (noise)
  7 home town     LOCAL   Havenbrook stamped from its TMX at the centre
  8 settlements   GLOBAL  Poisson-style site choice with spacing; footprints are local stamps
  9 landmarks     GLOBAL  as settlements
 10 roads         GLOBAL  MST + extra links over settlements, least-cost paths
 11 stations      GLOBAL  one per town + every island dock + gap filling by distance transform
 12 fords         GLOBAL  along rivers
 13 reachability  GLOBAL  flood fill per walkable region (mainland / each island), fill pockets, carve paths
 14 stats
"""
import heapq
import json
import math
import os
import time

import numpy as np
from scipy import ndimage
from skimage.graph import MCP_Geometric

from .core import *
from . import sites as S


class Plane:
    pass


def load_profile(path):
    with open(path) as f:
        p = json.load(f)
    p["_dir"] = os.path.dirname(os.path.abspath(path))
    return p


def generate(seed, profile, log=print):
    P = Plane()
    P.seed, P.profile = seed, profile
    N = profile["size"]
    P.N = P.H = P.W = N
    P.center = (N // 2, N // 2)
    P.objects, P.sites, P.stations = [], [], []
    P.timings = {}
    P.reserved = np.zeros((N, N), bool)
    steps = [step_layout, step_sea, step_water, step_rivers, step_volcanoes, step_obstacles, step_home_town,
             step_settlements, step_landmarks, step_islands, step_roads, step_stations, step_fords, step_reachability, step_stats]
    t_all = time.time()
    for fn in steps:
        t = time.time()
        fn(P, sub_rng(seed, fn.__name__))
        P.timings[fn.__name__] = round(time.time() - t, 2)
    P.time = round(time.time() - t_all, 1)
    return P


# ------------------------------------------------------------------------------------------- 1 layout
def step_layout(P, rng):
    L = P.profile["layout"]
    N = P.N
    cy, cx = P.center
    yy, xx = np.mgrid[0:N, 0:N].astype(np.float32)
    dy, dx = yy - cy, xx - cx
    r = np.hypot(dx, dy)
    theta = np.arctan2(dy, dx)
    s = N / 600.0
    warp_a, warp_r, warp_s = fbm(rng, N, N, 140 * s, 4), fbm(rng, N, N, 90 * s, 4), fbm(rng, N, N, 28 * s, 3)
    rot = rng.uniform(0, 2 * math.pi)
    tw = theta + L["BORDER_WARP_ANGLE"] * (warp_a + 0.45 * warp_s) * np.clip(90 * s / (r + 30 * s), 0.35, 1.0) + rot
    rw = r + L["BORDER_WARP_RADIUS"] * warp_r
    order = [BIOMES.index(b) for b in L["order"]]
    sector = np.floor((tw % (2 * math.pi)) / (2 * math.pi / len(order))).astype(np.int64) % len(order)
    biome = np.array(order, np.int8)[sector]
    biome[rw < L["R_CORE"]] = BIOMES.index(L["core_biome"])
    tier = np.where(rw < L["R_CORE"], 0, np.where(rw < L["R_INNER"], 1, 2)).astype(np.int8)
    w6 = np.stack([ndimage.gaussian_filter((biome == b).astype(np.float32), L["TRANSITION_BAND"]) for b in range(6)])
    w6 /= w6.sum(0, keepdims=True)
    P.r, P.rw, P.biome, P.tier = r, rw, biome, tier
    P.weights = w6.astype(np.float16)
    P.tier2_soft = ndimage.gaussian_filter((tier == 2).astype(np.float32), 10).astype(np.float16)
    P.terrain = np.full((N, N), GRASS, np.int8)
    P.bmap = lambda d: np.tensordot(np.array([d[b] for b in BIOMES], np.float32), P.weights.astype(np.float32), axes=1)


# ------------------------------------------------------------------------------------------- 2 sea / islands
def step_sea(P, rng):
    L, SEA = P.profile["layout"], P.profile["sea"]
    N, rw, w6 = P.N, P.rw, P.weights
    s = N / 600.0
    coast_n = fbm(rng, N, N, 45 * s, 5)
    ocean = rw > L["R_COAST"] + 10 * s * coast_n
    sea_n = fbm(rng, N, N, 30 * s, 3)
    sb = BIOMES.index(SEA["biome"])
    blue_sea = (w6[sb] > 0.5 + 0.12 * sea_n) & (rw > SEA["SEA_START"] + 12 * s * sea_n)
    isl = to_uniform(np.where(blue_sea, fbm(rng, N, N, SEA["ISLAND_SCALE"], 4, 0.5), -9))
    island_land = blue_sea & (isl > 1 - SEA["ISLAND_SHARE"] * blue_sea.sum() / (N * N))
    island_land = ndimage.binary_opening(island_land, disk(2.5))
    yy, xx = np.ogrid[0:N, 0:N]
    ocean |= blue_sea & ~island_land
    ocean[:4, :] = ocean[-4:, :] = True
    ocean[:, :4] = ocean[:, -4:] = True
    # islands that touch the mainland get a strait so they really are islands
    mainland0 = ~ocean & ~island_land
    touching = island_land & ndimage.binary_dilation(mainland0 & ~blue_sea, np.ones((3, 3)))
    if touching.any():
        tl, _ = ndimage.label(island_land)
        bad = np.unique(tl[touching]); bad = bad[bad > 0]
        ocean |= np.isin(tl, bad) & ndimage.binary_dilation(mainland0 & ~blue_sea, disk(12))
    lab, nl = ndimage.label(~ocean)
    sizes = ndimage.sum(np.ones(lab.shape, np.int32), lab, index=np.arange(nl + 1))
    main = lab[P.center]
    keep = sizes >= SEA["MIN_ISLAND"]
    keep[0] = False; keep[main] = True
    ocean |= ~keep[lab]
    olab, _ = ndimage.label(ocean)
    edge = set(np.unique(np.concatenate([olab[0], olab[-1], olab[:, 0], olab[:, -1]]))) - {0}
    ocean_c = np.isin(olab, list(edge)) | (ocean & blue_sea)
    P.enclosed = ocean & ~ocean_c
    ocean = ocean_c
    lab, nl = ndimage.label(~ocean)
    main = lab[P.center]
    P.island_lab = np.where((lab > 0) & (lab != main), lab, 0).astype(np.int32)
    P.island_ids = [i for i in range(1, nl + 1) if i != main]
    P.mainland = lab == main
    P.ocean = ocean
    P.blue_sea = blue_sea
    P.terrain[ocean] = OCEAN
    P.dist_sea = ndimage.distance_transform_edt(~ocean).astype(np.float32)


# ------------------------------------------------------------------------------------------- 3 lakes/bogs
def step_water(P, rng):
    T = P.profile["terrain"]
    B = P.profile["biomes"]
    N = P.N
    lake_d = P.bmap({b: B[b]["lake"] for b in BIOMES})
    black = P.weights[BIOMES.index("black")].astype(np.float32)
    lake = (to_uniform(fbm(rng, N, N, T["LAKE_SCALE"], 4)) > 1 - lake_d * (1 - black)) & ~P.ocean & (P.r > 60)
    lake |= (to_uniform(fbm(rng, N, N, T["BOG_SCALE"], 3)) > 1 - lake_d * black) & ~P.ocean & (P.r > 60)
    lake |= P.enclosed
    lake = ndimage.binary_opening(lake, np.ones((3, 3)))
    lake = ndimage.binary_closing(lake, np.ones((3, 3))) & ~P.ocean & (P.island_lab == 0)
    # meadow pond near spawn (outside Havenbrook's footprint)
    cy, cx = P.center
    a = rng.uniform(0, 2 * math.pi)
    py, px = int(cy + 80 * math.sin(a)), int(cx + 80 * math.cos(a))
    stamp(lake, py, px, 4.5); stamp(lake, py + 1, px + 3, 3.2)
    P.lake = lake
    P.terrain[lake] = WATER


# ------------------------------------------------------------------------------------------- 4 rivers
def step_rivers(P, rng):
    T = P.profile["terrain"]
    N, s = P.N, P.N / 600.0
    w6 = P.weights.astype(np.float32)
    elev = (0.55 * fbm(rng, N, N, 110 * s, 5) + 1.2 * (1 - P.r / (N / 2)) + 1.0 * w6[4] - 0.6 * w6[2]
            + 0.25 * fbm(rng, N, N, 18 * s, 2))
    P.elev = elev.astype(np.float16)
    hs = 4                                     # flow field on a quarter-resolution grid
    e2 = elev[::hs, ::hs]
    h2, w2 = e2.shape
    parent = np.full(h2 * w2, -1, np.int64)
    seen = np.zeros(h2 * w2, bool)
    ef = e2.ravel()
    heap = [(float(ef[i]), int(i)) for i in np.flatnonzero(P.ocean[::hs, ::hs].ravel())]
    for _, i in heap:
        seen[i] = True
    heapq.heapify(heap)
    nb = [(-1, 0), (1, 0), (0, -1), (0, 1), (-1, -1), (-1, 1), (1, -1), (1, 1)]
    while heap:
        e, i = heapq.heappop(heap)
        y, x = divmod(i, w2)
        for dy, dx in nb:
            ny, nx = y + dy, x + dx
            if 0 <= ny < h2 and 0 <= nx < w2:
                j = ny * w2 + nx
                if not seen[j]:
                    seen[j] = True
                    parent[j] = i
                    heapq.heappush(heap, (max(e, float(ef[j])), j))
    L = P.profile["layout"]
    cand = (P.r > L["R_CORE"] + 60) & (P.r < L["R_OUTER"] - 30) & P.mainland & ~P.lake
    score = np.where(cand, elev + 0.8 * w6[4] + 0.3 * rng.random((N, N), dtype=np.float32), -1e9)
    sub = score[::hs, ::hs]
    order = np.argsort(sub.ravel())[::-1][:60000]
    sources = []
    for idx in order:
        y, x = divmod(int(idx), w2)
        y, x = y * hs, x * hs
        if all((y - a) ** 2 + (x - b) ** 2 > T["RIVER_SPACING"] ** 2 for a, b in sources):
            sources.append((y, x))
        if len(sources) >= T["RIVER_COUNT"]:
            break
    river = np.zeros((N, N), bool)
    paths = []
    r0, r1 = T["RIVER_WIDTH"]
    for sy, sx in sources:
        j = (sy // hs) * w2 + sx // hs
        pts = []
        while j >= 0:
            y, x = divmod(j, w2)
            Y, X = y * hs + 1, x * hs + 1
            pts.append((Y, X))
            if P.ocean[Y, X] or ((river[Y, X] or P.lake[Y, X]) and len(pts) > 5):
                break
            j = parent[j]
        if len(pts) * hs < T["RIVER_MIN_LEN"]:
            continue
        full = []
        for (y0, x0), (y1, x1) in zip(pts[:-1], pts[1:]):
            for t in range(hs):
                full.append((int(round(y0 + (y1 - y0) * t / hs)), int(round(x0 + (x1 - x0) * t / hs))))
        # meander: smooth lateral wobble so quarter-res steps don't show
        fy = np.array([p[0] for p in full], np.float32); fx = np.array([p[1] for p in full], np.float32)
        k = np.ones(9) / 9
        fy = np.convolve(np.pad(fy, 4, mode="edge"), k, "valid"); fx = np.convolve(np.pad(fx, 4, mode="edge"), k, "valid")
        full = [(int(round(a)), int(round(b))) for a, b in zip(fy, fx)]
        paths.append(full)
        n = len(full)
        for i, (y, x) in enumerate(full):
            stamp(river, y, x, r0 + (r1 - r0) * min(1.0, i / max(1, n * 0.5)))
    river &= ~P.ocean & (P.island_lab == 0)
    P.river, P.river_paths = river, paths
    P.terrain[river] = WATER


# ------------------------------------------------------------------------------------------- 5 volcanoes
def step_volcanoes(P, rng):
    V = P.profile["volcanoes"]
    N = P.N
    vb = BIOMES.index(V["biome"])
    w = P.weights[vb].astype(np.float32)
    cand = (w > 0.9) & (P.tier == 2) & (P.dist_sea > 40) & (P.terrain == GRASS) & P.mainland
    score = np.where(cand, P.elev.astype(np.float32) + 0.2 * rng.random((N, N), dtype=np.float32), -1e9)
    want = int(rng.integers(V["COUNT"][0], V["COUNT"][1] + 1))
    vols = []
    for idx in np.argsort(score[::4, ::4].ravel())[::-1][:20000]:
        y, x = divmod(int(idx), N // 4 + (1 if N % 4 else 0))
        y, x = y * 4, x * 4
        if score[y, x] < -1e8:
            break
        if all((y - a) ** 2 + (x - b) ** 2 > V["SPACING"] ** 2 for a, b in vols):
            vols.append((y, x))
        if len(vols) >= want:
            break
    lava = np.zeros((N, N), bool); cone = np.zeros((N, N), bool); cone2 = np.zeros((N, N), bool)
    ash = np.zeros((N, N), np.float32)
    for vy, vx in vols:
        stamp(cone, vy, vx, V["CONE_RADIUS"]); stamp(cone2, vy, vx, V["CONE_TOP_RADIUS"])
        stamp(lava, vy, vx, V["CRATER_RADIUS"])
        R = int(V["ASH_RADIUS"] * 1.4)
        y0, y1, x0, x1 = max(0, vy - R), min(N, vy + R), max(0, vx - R), min(N, vx + R)
        yy, xx = np.mgrid[y0:y1, x0:x1]
        ash[y0:y1, x0:x1] = np.maximum(ash[y0:y1, x0:x1], np.clip(1.3 - np.hypot(yy - vy, xx - vx) / V["ASH_RADIUS"], 0, 1))
        nf = int(rng.integers(V["LAVA_FLOWS"][0], V["LAVA_FLOWS"][1] + 1))
        for f in range(nf):
            a0 = 2 * math.pi * (f + rng.uniform(0.1, 0.9)) / nf
            a = a0
            y, x = vy + (V["CONE_RADIUS"] - 1.5) * math.sin(a), vx + (V["CONE_RADIUS"] - 1.5) * math.cos(a)
            for st in range(int(rng.integers(18, 32))):
                a = a0 + 0.8 * (a - a0) + rng.normal(0, 0.25)
                y += math.sin(a); x += math.cos(a)
                iy, ix = int(round(y)), int(round(x))
                if P.ocean[iy, ix]:
                    break
                stamp(lava, iy, ix, 1.5)
            stamp(lava, iy, ix, 2.8)
    ash = ndimage.gaussian_filter(ash, 3) * w
    P.volcanoes, P.cone, P.cone2, P.ash = vols, cone, cone2, ash.astype(np.float16)
    P.lava_mask = lava & ~P.ocean


# ------------------------------------------------------------------------------------------- 6 obstacles + ridges
def step_obstacles(P, rng):
    T, B = P.profile["terrain"], P.profile["biomes"]
    N = P.N
    w6 = P.weights.astype(np.float32)
    t2 = P.tier2_soft.astype(np.float32)
    jungle = w6[5] * t2
    clus = to_uniform(fbm(rng, N, N, T["CLUSTER_SCALE"] * 2, 3, 0.55))
    lane = np.abs(to_uniform(fbm(rng, N, N, T["LANE_SCALE"], 3)) - 0.5) < T["LANE_WIDTH"] / 2
    lane |= np.abs(to_uniform(fbm(rng, N, N, T["LANE_SCALE"] * 1.5, 3)) - 0.5) < T["LANE_WIDTH"] / 2
    dens = P.bmap({b: B[b]["density"] for b in BIOMES}) + T["OUTER_BONUS"] * t2 + T["JUNGLE_BONUS"] * jungle
    obst = (clus > 1 - dens * 1.25) & ~lane
    obst |= rng.random((N, N), dtype=np.float32) < 0.010 * (1 + 2 * w6[5])
    obst &= P.terrain == GRASS
    kind = fbm(rng, N, N, 9, 2)
    ash = P.ash.astype(np.float32)
    rock = ((w6[4] > 0.5) & (kind > -0.5)) | ((w6[0] > 0.5) & (kind > 0.6)) | ((P.tier == 2) & (kind > 1.1)) | (ash > 0.3)
    P.terrain[obst & ~rock] = TREE
    P.terrain[obst & rock] = ROCK
    # meadow groves around the core
    L = P.profile["layout"]
    cy, cx = P.center
    for _ in range(40):
        a, rr = rng.uniform(0, 2 * math.pi), rng.uniform(70, L["R_CORE"] - 15)
        gy, gx = int(cy + rr * math.sin(a)), int(cx + rr * math.cos(a))
        for _k in range(int(rng.integers(3, 8))):
            stamp(P.terrain, gy + int(rng.integers(-4, 5)), gx + int(rng.integers(-4, 5)), 1.3, TREE, where=(P.terrain == GRASS))
    # ridges
    rn = 1 - np.abs(fbm(rng, N, N, T["RIDGE_SCALE"], 4))
    rn /= rn.max()
    thr = np.where(w6[4] > 0.5, T["RIDGE_RED"], np.where(P.tier == 2, T["RIDGE_OTHER_OUTER"], 2.0))
    near_volc = np.zeros((N, N), bool)
    for vy, vx in P.volcanoes:
        stamp(near_volc, vy, vx, 18)
    ridge = (rn > thr) & (P.r > L["R_CORE"] + 40) & (w6[1] < 0.5) & (P.island_lab == 0) & ~near_volc
    ridge &= ~((w6[2] > 0.5) & (P.dist_sea < 14))
    ridge &= ~(to_uniform(fbm(rng, N, N, 18, 2)) < T["RIDGE_GAP_EVERY"])
    ridge = ndimage.binary_dilation(ridge, disk(1.6))
    ridge = ndimage.gaussian_filter(ridge.astype(np.float32), 1.6) > 0.5
    ridge = ndimage.binary_opening(ridge, disk(1.5))
    ridge |= P.cone
    ridge &= ~P.ocean & (P.terrain != WATER)
    P.terrain[ridge] = RIDGE
    P.terrain[P.lava_mask] = LAVA


# ------------------------------------------------------------------------------------------- 7 home town
def step_home_town(P, rng):
    H = P.profile.get("home_town")
    if not H:
        return
    import sys
    sys.path.insert(0, P.profile["_dir"] + "/..")
    from tmx import TMX
    tm = TMX(os.path.join(P.profile["_dir"], "..", H["tmx"]), os.path.join(P.profile["_dir"], "..", H["tileset_dir"]))
    skip = H.get("skip_rows_top", 0)
    blocked = tm.blocked()[skip:]
    h, w = blocked.shape
    cy, cx = P.center
    y0, x0 = cy - h // 2, cx - w // 2
    P.terrain[y0:y0 + h, x0:x0 + w] = np.where(blocked, HOUSE, TOWN)
    P.reserved[y0 - 6:y0 + h + 6, x0 - 6:x0 + w + 6] = True
    P.home = dict(kind="town", name=H["name"], y=cy, x=cx, h=h, w=w, top=y0, left=x0, style="farm", home=True,
                  gates=[(y0 + h, x0 + w // 2)], tmx=tm, skip=skip)
    # the town's south gate opens onto a road stub
    gx = x0 + w // 2
    P.terrain[y0 + h - 1:y0 + h + 8, gx - 1:gx + 2] = ROAD
    P.sites.append(P.home)


# ------------------------------------------------------------------------------------------- 8/9 settlements + landmarks
def _land_area(P):
    land = ~P.ocean
    return {b: int((land & (P.biome == b)).sum()) for b in range(6)}


def _candidates(P, rng, b, k):
    sel = (P.biome == b) & (P.weights[b] > 0.75) & ~P.ocean
    idx = np.flatnonzero(sel[::2, ::2].ravel())
    if len(idx) == 0:
        return []
    w2 = (P.N + 1) // 2
    pick = rng.choice(idx, size=min(len(idx), k), replace=False)
    return [(int(i // w2) * 2, int(i % w2) * 2) for i in pick]


def _far_enough(points, y, x, d):
    gy, gx = y // 128, x // 128
    for yy in range(gy - 2, gy + 3):
        for xx in range(gx - 2, gx + 3):
            for (a, b) in points.get((yy, xx), ()):
                if (a - y) ** 2 + (b - x) ** 2 < d * d:
                    return False
    return True


def _add_point(points, y, x):
    points.setdefault((y // 128, x // 128), []).append((y, x))


def step_settlements(P, rng):
    B, ST = P.profile["biomes"], P.profile["settlements"]
    area = _land_area(P)
    P.area = area
    pts = {k: {} for k in ST}
    for s in P.sites:
        _add_point(pts["town"], s["y"], s["x"])
    for kind in ("town", "hamlet", "farmstead"):
        cfg = ST[kind]
        for b in range(6):
            bname = BIOMES[b]
            want = int(round(B[bname]["per100k"].get(kind, 0) * area[b] / 1e5))
            if want == 0:
                continue
            got = 0
            for (y, x) in _candidates(P, rng, b, want * 60):
                if got >= want:
                    break
                h = int(rng.integers(cfg["h"][0], cfg["h"][1] + 1)); w = int(rng.integers(cfg["w"][0], cfg["w"][1] + 1))
                if P.island_lab[y, x]:
                    continue          # islands are settled by step_islands (docks only where people are)
                ty, tx = y - h // 2, x - w // 2
                if not _far_enough(pts[kind], y, x, cfg["min_dist"]):
                    continue
                # settlements may drain small bog pools / lake edges, never rivers or sea
                if not S.rect_ok(P, ty, tx, h, w, allowed=(GRASS, TREE, ROCK, WATER), margin=3):
                    continue
                if P.river[ty:ty + h, tx:tx + w].any() or (P.terrain[ty:ty + h, tx:tx + w] == WATER).mean() > 0.25:
                    continue
                style = B[bname]["houses"]
                name = f"{bname}-{kind}-{got + 1}"
                fn = {"town": S.build_town, "hamlet": S.build_hamlet, "farmstead": S.build_farmstead}[kind]
                site = fn(P, y, x, h, w, style, rng, name)
                site["biome"] = b
                site["island"] = int(P.island_lab[y, x])
                P.sites.append(site)
                _add_point(pts[kind], y, x)
                got += 1


def step_landmarks(P, rng):
    B = P.profile["biomes"]
    area = P.area
    kinds = ["graveyard", "battlefield", "ruin", "standing_stones", "lone_tower", "cave", "dungeon", "orchard"]
    near_rock = ndimage.binary_dilation(np.isin(P.terrain, (RIDGE, ROCK)), disk(4))
    pts = {}
    for s in P.sites:
        _add_point(pts, s["y"], s["x"])
    for kind in kinds:
        hh, ww = S.LANDMARK_SIZE[kind]
        for b in range(6):
            want = int(round(B[BIOMES[b]]["per100k"].get(kind, 0) * area[b] / 1e5))
            got = 0
            for (y, x) in _candidates(P, rng, b, want * 80):
                if got >= want:
                    break
                if kind == "cave" and not near_rock[y, x]:
                    continue
                if P.island_lab[y, x]:
                    continue          # island landmarks come from step_islands
                if not _far_enough(pts, y, x, 28):
                    continue
                if not S.rect_ok(P, y - hh // 2, x - ww // 2, hh, ww, margin=2):
                    continue
                site = S.build_landmark(P, kind, y, x, rng, f"{BIOMES[b]}-{kind}-{got + 1}")
                site["biome"] = b
                site["island"] = int(P.island_lab[y, x])
                P.sites.append(site)
                _add_point(pts, y, x)
                got += 1


# ------------------------------------------------------------------------------------------- 9b islands
def step_islands(P, rng):
    """Inhabit the biggest islands (port towns / villages with a harbour dock + 1-2 island landmarks) until
    INHABITED_AREA_SHARE of island area is settled; the rest are wild (no dock, one rarer landmark)."""
    I = P.profile["islands"]
    sizes = np.bincount(P.island_lab.ravel())
    isl = sorted(P.island_ids, key=lambda i: -sizes[i])
    total = sum(int(sizes[i]) for i in isl)
    P.island_meta = {}
    settled = 0
    for iid in isl:
        size = int(sizes[iid])
        meta = dict(id=iid, size=size, kind="wild", landmarks=[])
        P.island_meta[iid] = meta
        if size < I["VILLAGE_MIN_AREA"] or settled >= I["INHABITED_AREA_SHARE"] * total:
            continue
        ys, xs = np.nonzero(P.island_lab == iid)
        n_set = 1 + min(I["MAX_SETTLEMENTS_PER_ISLAND"] - 1, max(0, (size - I["TOWN_MIN_AREA"]) // I["AREA_PER_EXTRA_SETTLEMENT"]))
        placed = []
        for si in range(n_set):
            kinds = (["port_town"] if si == 0 and size >= I["TOWN_MIN_AREA"] else []) + ["village", "hamlet"]
            site = None
            for kind in kinds:
                cfg = I.get(kind, P.profile["settlements"]["hamlet"])
                for k in rng.permutation(len(ys))[:600]:
                    y, x = int(ys[k]), int(xs[k])
                    if any((y - a_["y"]) ** 2 + (x - a_["x"]) ** 2 < I["SETTLEMENT_SPACING"] ** 2 for a_ in placed):
                        continue
                    h = int(rng.integers(cfg["h"][0], cfg["h"][1] + 1)); w = int(rng.integers(cfg["w"][0], cfg["w"][1] + 1))
                    ty, tx = y - h // 2, x - w // 2
                    if not S.rect_ok(P, ty, tx, h, w, margin=1):
                        continue
                    if P.dist_sea[ty:ty + h, tx:tx + w].min() > I["HARBOUR_MAX_SHORE_DIST"]:
                        continue        # every island settlement is a harbour settlement
                    name = f"isle-{iid} {kind.replace('_', ' ')} {si + 1}"
                    fn = S.build_town if kind == "port_town" else S.build_hamlet
                    site = fn(P, y, x, h, w, "island", rng, name)
                    site.update(kind="town" if kind == "port_town" else "hamlet", island_kind=kind, island=iid, biome=2)
                    P.sites.append(site)
                    break
                if site:
                    break
            if site is None:
                continue
            placed.append(site)
            # dock IN the harbour: pier from the footprint's point closest to the sea
            sub = P.dist_sea[site["top"]:site["top"] + site["h"], site["left"]:site["left"] + site["w"]]
            a_, b_ = np.unravel_index(int(np.argmin(sub)), sub.shape)
            st = _place_station(P, rng, "dock", site["top"] + int(a_), site["left"] + int(b_), f"{site['name']} harbour", iid,
                                harbour=True)
            if st:
                st["town"] = site["name"]
                meta.setdefault("docks", []).append(st)
        if not placed:
            continue
        meta.update(kind=placed[0]["island_kind"], settlement=placed[0], settlements=placed,
                    dock=(meta.get("docks") or [None])[0])
        settled += size
        n = int(rng.integers(I["inhabited_landmark_count"][0], I["inhabited_landmark_count"][1] + 1))
        for kind in rng.permutation(I["inhabited_landmarks"])[:n]:
            lm = _island_landmark(P, rng, iid, str(kind))
            if lm:
                meta["landmarks"].append(lm)
    for iid, meta in P.island_meta.items():
        if meta["kind"] == "wild":
            kind = str(I["wild_landmarks"][int(rng.integers(0, len(I["wild_landmarks"])))])
            lm = _island_landmark(P, rng, iid, kind)
            if lm:
                lm["wild"] = True
                meta["landmarks"].append(lm)
    P.island_stats = dict(
        islands=len(isl), inhabited=sum(1 for m in P.island_meta.values() if m["kind"] != "wild"),
        docked=sum(1 for m in P.island_meta.values() if m.get("dock")),
        wild=sum(1 for m in P.island_meta.values() if m["kind"] == "wild"),
        port_towns=sum(1 for m in P.island_meta.values() for t in m.get("settlements", []) if t["island_kind"] == "port_town"),
        villages=sum(1 for m in P.island_meta.values() for t in m.get("settlements", []) if t["island_kind"] != "port_town"),
        island_docks=sum(len(m.get("docks", [])) for m in P.island_meta.values()),
        inhabited_area_share=round(settled / max(1, total), 3), island_area=total)


COASTAL = {"lighthouse", "shipwreck", "sea_cave", "pirate_cove", "treasure_cove"}


def _island_landmark(P, rng, iid, kind):
    hh, ww = S.LANDMARK_SIZE[kind]
    m = P.island_lab == iid
    if kind in COASTAL:
        m = m & (P.dist_sea <= (2 if kind in ("shipwreck", "treasure_cove") else 4))
    ys, xs = np.nonzero(m)
    for k in rng.permutation(len(ys))[:400]:
        y, x = int(ys[k]), int(xs[k])
        if not S.rect_ok(P, y - hh // 2, x - ww // 2, hh, ww, margin=1):
            continue
        site = S.build_landmark(P, kind, y, x, rng, f"isle-{iid} {kind.replace('_', ' ')}")
        site.update(island=iid, biome=2)
        P.sites.append(site)
        return site
    return None


# ------------------------------------------------------------------------------------------- 10 roads
def _base_cost(P):
    t = P.terrain
    c = np.ones(t.shape, np.float32)
    c[t == TREE] = 4; c[t == ROCK] = 6; c[t == RIDGE] = 14
    c[np.isin(t, (HOUSE, FENCE, RUIN))] = 600
    c[t == WATER] = 22; c[P.river] = 16
    c[t == LAVA] = 400; c[P.cone] = 400
    c[t == OCEAN] = 1e5
    c[np.isin(t, (TOWN, FIELD, GRAVE))] = 0.6
    c += 0.6 * ndimage.binary_dilation(np.isin(t, (WATER, OCEAN)), disk(1.5))
    return c


def step_roads(P, rng):
    R = P.profile["roads"]
    cost = _base_cost(P)
    road = np.zeros(P.terrain.shape, bool)
    P.road_paths = []

    def route(a, b, margin=60):
        (ya, xa), (yb, xb) = a, b
        y0, y1 = max(0, min(ya, yb) - margin), min(P.N, max(ya, yb) + margin + 1)
        x0, x1 = max(0, min(xa, xb) - margin), min(P.N, max(xa, xb) + margin + 1)
        m = MCP_Geometric(cost[y0:y1, x0:x1])
        m.find_costs([(ya - y0, xa - x0)], [(yb - y0, xb - x0)])
        path = [(py + y0, px + x0) for py, px in m.traceback((yb - y0, xb - x0))]
        for py, px in path:
            stamp(road, py, px, R["WIDTH"] / 2)
            cost[max(0, py - 1):py + 2, max(0, px - 1):px + 2] = np.minimum(cost[max(0, py - 1):py + 2, max(0, px - 1):px + 2], 0.35)
        P.road_paths.append(path)

    # per walkable region (mainland, each island): MST + extra links over towns and hamlets
    groups = {}
    for s in P.sites:
        if s["kind"] in ("town", "hamlet"):
            groups.setdefault(s.get("island", 0), []).append(s)
    for gid, nodes in groups.items():
        if len(nodes) < 2:
            continue
        pts = np.array([[n["y"], n["x"]] for n in nodes], np.float32)
        n = len(pts)
        D = np.hypot(pts[:, None, 0] - pts[None, :, 0], pts[:, None, 1] - pts[None, :, 1])
        edges, best, bp, used = [], D[0].copy(), np.zeros(n, int), np.zeros(n, bool)
        used[0] = True
        for _ in range(n - 1):
            j = int(np.argmin(np.where(used, np.inf, best)))
            edges.append((int(bp[j]), j)); used[j] = True
            u = D[j] < best; best[u] = D[j][u]; bp[u] = j
        adj = {i: set() for i in range(n)}
        for a, b in edges:
            adj[a].add(b); adj[b].add(a)

        def gdist(a, b):
            dist = {a: 0.0}; pq = [(0.0, a)]
            while pq:
                d, u = heapq.heappop(pq)
                if u == b:
                    return d
                if d > dist[u]:
                    continue
                for v in adj[u]:
                    nd = d + D[u, v]
                    if nd < dist.get(v, 1e18):
                        dist[v] = nd; heapq.heappush(pq, (nd, v))
            return 1e18
        extra = int(round(R["EXTRA_LINKS_PER_100_NODES"] * n / 100))
        cands = []
        for i in range(n):
            if nodes[i]["kind"] != "town":
                continue
            for j in np.argsort(D[i])[1:5]:
                j = int(j)
                if j not in adj[i] and D[i, j] < R["MAX_LINK"] and nodes[j]["kind"] == "town":
                    cands.append((gdist(i, j) / D[i, j], i, j))
        cands.sort(reverse=True)
        for ratio, i, j in cands[:extra]:
            if ratio > 1.6 and j not in adj[i]:
                edges.append((i, j)); adj[i].add(j); adj[j].add(i)

        def gate(s, other):
            gs = s.get("gates") or [(s["y"], s["x"])]
            return min(gs, key=lambda g: (g[0] - other[0]) ** 2 + (g[1] - other[1]) ** 2)
        for a, b in sorted(edges, key=lambda e: D[e[0], e[1]]):
            A, Bn = nodes[a], nodes[b]
            ga, gb = gate(A, (Bn["y"], Bn["x"])), gate(Bn, (A["y"], A["x"]))
            ga = (min(max(ga[0], 1), P.N - 2), min(max(ga[1], 1), P.N - 2))
            gb = (min(max(gb[0], 1), P.N - 2), min(max(gb[1], 1), P.N - 2))
            route(ga, gb)
    t = P.terrain
    road &= ~np.isin(t, (OCEAN, TOWN, HOUSE, FENCE, RUIN, POI, FIELD, GRAVE, DOCK))
    clear = ndimage.binary_dilation(road, disk(1.5)) & np.isin(t, (TREE, ROCK)) & ~P.reserved
    t[clear] = GRASS
    t[ndimage.binary_dilation(road, disk(3.2)) & (t == RIDGE) & ~P.cone] = GRASS
    on_water = road & (t == WATER)
    t[road & np.isin(t, (GRASS, TREE, ROCK, RIDGE, LAVA))] = ROAD
    t[on_water] = BRIDGE
    _straighten_bridges(P)
    P.road = road


def _straighten_bridges(P, width=2):
    t = P.terrain
    lab, n = ndimage.label(t == BRIDGE, np.ones((3, 3)))
    for i, sl in enumerate(ndimage.find_objects(lab), 1):
        y0, y1, x0, x1 = max(0, sl[0].start - 2), sl[0].stop + 2, max(0, sl[1].start - 2), sl[1].stop + 2
        sub = t[y0:y1, x0:x1]
        comp = lab[y0:y1, x0:x1] == i
        ring = ndimage.binary_dilation(comp, np.ones((3, 3))) & ~comp & (sub == ROAD)
        ry, rx = np.nonzero(ring)
        sub[comp] = WATER
        if len(ry) < 2:
            continue
        d2 = (ry[:, None] - ry[None]) ** 2 + (rx[:, None] - rx[None]) ** 2
        a, b = np.unravel_index(np.argmax(d2), d2.shape)
        cy_, cx_ = np.nonzero(comp)
        my, mx = int(round(cy_.mean())) + y0, int(round(cx_.mean())) + x0
        vertical = abs(ry[a] - ry[b]) >= abs(rx[a] - rx[b])
        cell = (lambda k, o: (my + k, mx + o)) if vertical else (lambda k, o: (my + o, mx + k))
        lo = hi = 0
        for sgn in (1, -1):
            k = 0
            while abs(k) < 18 and any(t[cell(k, o)] in (WATER, BRIDGE) for o in range(width)):
                k += sgn
            if sgn > 0: hi = k
            else: lo = k
        for k in range(lo, hi + 1):
            for o in range(width):
                if t[cell(k, o)] != OCEAN:
                    t[cell(k, o)] = BRIDGE
        for k, sgn in ((lo - 1, -1), (hi + 1, 1)):
            ey, ex = cell(k, 0)
            for kk in range(0, 8):
                y, x = cell(k + sgn * kk, 0)
                if t[y, x] == ROAD and kk > 0:
                    break
                for o in range(width):
                    yy, xx = cell(k + sgn * kk, o)
                    if t[yy, xx] in (GRASS, TREE, ROCK):
                        t[yy, xx] = ROAD


# ------------------------------------------------------------------------------------------- 11 stations
def _find_pier(P, y, x, target_water, maxr, length, island=0):
    """coast cell near (y,x) next to target water with a straight run of `length` water tiles."""
    t = P.terrain
    y0, y1, x0, x1 = max(4, y - maxr), min(P.N - 4, y + maxr), max(4, x - maxr), min(P.N - 4, x + maxr)
    sub = t[y0:y1, x0:x1]
    wat = np.isin(sub, target_water)
    coast = np.isin(sub, (GRASS, ROAD, TOWN)) & ndimage.binary_dilation(wat, np.ones((3, 3))) & ~P.reserved[y0:y1, x0:x1]
    # the pier must start in the station's own walkable region (an island dock on that island, etc.)
    coast &= (P.island_lab[y0:y1, x0:x1] == island) if island else P.mainland[y0:y1, x0:x1]
    cys, cxs = np.nonzero(coast)
    if len(cys) == 0:
        return None
    order = np.argsort((cys + y0 - y) ** 2 + (cxs + x0 - x) ** 2)
    for k in order[:300]:
        cy, cx = int(cys[k]) + y0, int(cxs[k]) + x0
        for dy, dx in ((-1, 0), (1, 0), (0, -1), (0, 1)):
            cells = [(cy + dy * i, cx + dx * i) for i in range(1, length + 1)]
            if all(t[c] in target_water for c in cells):
                return (cy, cx), cells, (dy, dx)
    return None


def _station_spot(P, kind, y, x, maxr=40):
    h, w = S.STATION_SIZE[kind]
    for r in range(4, maxr, 3):
        for k in range(16):
            a = 2 * math.pi * k / 16
            ty, tx = int(y + r * math.sin(a)) - h // 2, int(x + r * math.cos(a)) - w // 2
            if S.rect_ok(P, ty, tx, h, w, margin=1):
                return ty, tx
    return None


def _place_station(P, rng, kind, y, x, name, island=0, harbour=False):
    pier = None
    if kind in ("dock", "skiff"):
        target = (OCEAN,) if kind == "dock" else (WATER,)
        fp = _find_pier(P, y, x, target, 60, P.profile["sea"]["PIER_LEN"] if kind == "dock" else 3, island)
        if fp is None:
            if kind == "dock":
                return None
            kind = "skiff"
        else:
            (cy, cx), cells, (dy, dx) = fp
            h, w = S.STATION_SIZE[kind]
            # hut on land behind the pier root
            for (ty, tx) in [(cy - dy * h - (h // 2 if dy == 0 else 0), cx - dx * w - (w // 2 if dx == 0 else 0)),
                             (cy - h + 1, cx - w // 2), (cy, cx - w // 2), (cy - h // 2, cx - w + 1), (cy - h // 2, cx)]:
                # a harbour hut may stand right against its settlement (inside the reserved margin)
                if S.rect_ok(P, ty, tx, h, w, check_reserved=not harbour):
                    st = S.build_station(P, kind, ty, tx, rng, name, pier=[(cy, cx)] + cells)
                    P.terrain[cy, cx] = DOCK
                    st["island"] = island
                    st["y"], st["x"] = cy, cx
                    P.stations.append(st)
                    return st
            # no room for a hut: pier alone
            for c in [(cy, cx)] + cells:
                P.terrain[c] = DOCK
            P.objects.append(dict(key="boat" if kind == "dock" else "skiff", y=cells[-1][0], x=cells[-1][1], h=1, w=2, dir=(dy, dx)))
            st = dict(kind="station", type=kind, name=name, y=cy, x=cx, top=cy, left=cx, h=1, w=1, pier=[(cy, cx)] + cells,
                      island=island)
            P.stations.append(st)
            return st
    spot = _station_spot(P, kind, y, x)
    if spot is None:
        return None
    st = S.build_station(P, kind, spot[0], spot[1], rng, name)
    st["island"] = island
    P.stations.append(st)
    return st


def step_stations(P, rng):
    B, ST = P.profile["biomes"], P.profile["stations"]
    n = 0
    for s in list(P.sites):
        if s["kind"] != "town" or s.get("island"):
            continue
        b = int(P.biome[s["y"], s["x"]])
        kind = B[BIOMES[b]]["station"]
        isl = s.get("island", 0)
        if isl or (kind == "dock" and P.dist_sea[s["y"], s["x"]] > 70):
            kind = "dock" if isl else "stagecoach"
        gy, gx = s["gates"][-1] if s.get("home") else (s["y"] + s["h"] // 2 + 4, s["x"])
        st = _place_station(P, rng, kind, gy, gx, f"{s['name']} {kind}", isl)
        if st is None and kind in ("dock", "skiff"):
            st = _place_station(P, rng, "stagecoach", gy, gx, f"{s['name']} stagecoach", isl)
        if st:
            st["town"] = s["name"]
            n += 1
    # docks only where people are: an inhabited island whose harbour dock failed gets one near its settlement
    for iid, meta in P.island_meta.items():
        if meta["kind"] == "wild" or meta.get("dock"):
            continue
        s_ = meta["settlement"]
        meta["dock"] = _place_station(P, rng, "dock", s_["y"], s_["x"], f"{s_['name']} harbour", iid)
    # mainland docks along the archipelago coast so boats can reach the islands
    sb = BIOMES.index(P.profile["sea"]["biome"])
    coast = P.mainland & (P.dist_sea < 2) & (P.weights[sb] > 0.3)
    ys, xs = np.nonzero(coast[::8, ::8])
    pts = []
    for k in rng.permutation(len(ys)):
        y, x = int(ys[k]) * 8, int(xs[k]) * 8
        if all((y - a) ** 2 + (x - b) ** 2 > 160 ** 2 for a, b in pts) and \
           all((y - s["y"]) ** 2 + (x - s["x"]) ** 2 > 100 ** 2 for s in P.stations if s["type"] == "dock" and not s.get("island")):
            if _place_station(P, rng, "dock", y, x, f"coast dock {len(pts) + 1}", 0):
                pts.append((y, x))
    # caravan camps at mountain passes: road tiles with dense ridge on both sides, in the caravan biome
    cb = [i for i, b in enumerate(BIOMES) if B[b]["station"] == "caravan"]
    if cb:
        rd = ndimage.uniform_filter((P.terrain == RIDGE).astype(np.float32), 13)
        cand = (P.terrain == ROAD) & (rd > 0.22) & (P.weights[cb[0]] > 0.6)
        ys, xs = np.nonzero(cand)
        order = np.argsort(-rd[ys, xs])
        placed = []
        for k in order:
            y, x = int(ys[k]), int(xs[k])
            if len(placed) >= ST["PASS_CAMPS"]:
                break
            if any((y - a) ** 2 + (x - b) ** 2 < ST["PASS_SPACING"] ** 2 for a, b in placed):
                continue
            if any((y - s_["y"]) ** 2 + (x - s_["x"]) ** 2 < ST["MIN_SPACING"] ** 2 for s_ in P.stations):
                continue
            st = _place_station(P, rng, "caravan", y, x, f"pass camp {len(placed) + 1}", 0)
            if st:
                st["pass"] = True
                placed.append((y, x))
    # fill gaps: no walkable mainland tile further than MAX_GAP from a station
    walk_main = (np.isin(P.terrain, WALKABLE) & P.mainland)[::4, ::4]
    dummies = []
    for i in range(ST["REMOTE_MAX"]):
        seedm = np.ones(walk_main.shape, bool)
        for (yy, xx) in [(st["y"], st["x"]) for st in P.stations] + dummies:
            seedm[min(yy // 4, seedm.shape[0] - 1), min(xx // 4, seedm.shape[1] - 1)] = False
        d = ndimage.distance_transform_edt(seedm) * 4
        d[~walk_main] = 0
        k = int(np.argmax(d))
        if d.ravel()[k] <= ST["MAX_GAP"]:
            break
        y, x = divmod(k, d.shape[1]); y, x = y * 4, x * 4
        b = int(P.biome[y, x])
        kind = B[BIOMES[b]]["station"]
        if kind == "dock":
            kind = "stagecoach"
        st = _place_station(P, rng, kind, y, x, f"{BIOMES[b]} {kind} {i + 1}", 0)
        if st is None:
            st = _place_station(P, rng, "stagecoach", y, x, f"{BIOMES[b]} post {i + 1}", 0)
        if st is None:
            dummies.append((y, x))     # nowhere to build here; don't retry this spot
    _station_spurs(P)


def _station_spurs(P):
    """Short road from each station's access tile to the nearest road / settlement floor in its region."""
    SPUR = P.profile["stations"]["SPUR_MAX"]
    t = P.terrain
    for st in P.stations:
        y, x = int(st["y"]), int(st["x"])
        y0, y1, x0, x1 = max(1, y - SPUR), min(P.N - 1, y + SPUR), max(1, x - SPUR), min(P.N - 1, x + SPUR)
        sub = t[y0:y1, x0:x1]
        target = np.isin(sub, (ROAD, TOWN))
        own = np.zeros_like(target)
        own[max(0, st["top"] - 1 - y0):max(0, st["top"] + st["h"] + 1 - y0), max(0, st["left"] - 1 - x0):max(0, st["left"] + st["w"] + 1 - x0)] = True
        target &= ~own
        if st.get("island"):
            target &= P.island_lab[y0:y1, x0:x1] == st["island"]
        else:
            target &= P.mainland[y0:y1, x0:x1]
        ty, tx = np.nonzero(target)
        if len(ty) == 0:
            continue
        k = int(np.argmin((ty + y0 - y) ** 2 + (tx + x0 - x) ** 2))
        c = np.ones(sub.shape, np.float32)
        c[np.isin(sub, (TREE, ROCK))] = 4; c[sub == RIDGE] = 14; c[sub == WATER] = 25
        c[np.isin(sub, (HOUSE, FENCE, RUIN, LAVA, OCEAN, DOCK))] = 1e5
        c[np.isin(sub, (ROAD, TOWN))] = 0.3
        sy, sx = y - y0, x - x0
        if st.get("pier"):  # start on land next to the pier root
            sy, sx = sy, sx
        m = MCP_Geometric(c)
        m.find_costs([(sy, sx)], [(int(ty[k]), int(tx[k]))])
        for py, px in m.traceback((int(ty[k]), int(tx[k]))):
            for (u, v) in ((py, px), (py + 1, px), (py, px + 1)):
                if u < sub.shape[0] and v < sub.shape[1]:
                    if sub[u, v] in (GRASS, TREE, ROCK, RIDGE):
                        sub[u, v] = ROAD
                    elif sub[u, v] == WATER:
                        sub[u, v] = BRIDGE


# ------------------------------------------------------------------------------------------- 12 fords
def step_fords(P, rng):
    sp = P.profile["terrain"]["FORD_SPACING"]
    t = P.terrain
    bridges = t == BRIDGE
    P.fords = []
    for path in P.river_paths:
        L = len(path)
        i = sp // 2
        while i < L - 4:
            y, x = path[i]
            if P.ocean[y, x] or P.lake[y, x] or bridges[max(0, y - 12):y + 13, max(0, x - 12):x + 13].any():
                i += 6; continue
            (ya, xa), (yb, xb) = path[max(0, i - 4)], path[min(L - 1, i + 4)]
            ty, tx = yb - ya, xb - xa
            nrm = math.hypot(ty, tx) or 1
            ny, nx = -tx / nrm, ty / nrm
            for s in np.arange(-5, 5.5, 0.5):
                for tt in (0, 1):
                    fy = int(round(y + ny * s + ty / nrm * tt)); fx = int(round(x + nx * s + tx / nrm * tt))
                    if P.river[fy, fx] and t[fy, fx] == WATER:
                        t[fy, fx] = FORD
            P.fords.append((y, x))
            i += sp


# ------------------------------------------------------------------------------------------- 13 reachability
def _fix_region(P, region, sy, sx, important, min_pocket):
    t = P.terrain
    ys_, xs_ = np.nonzero(region)
    y0, y1, x0, x1 = ys_.min(), ys_.max() + 1, xs_.min(), xs_.max() + 1
    reg = region[y0:y1, x0:x1]
    sub = t[y0:y1, x0:x1]
    imp = [(y - y0, x - x0) for (y, x) in important if y0 <= y < y1 and x0 <= x < x1 and reg[y - y0, x - x0]]
    sy, sx = sy - y0, sx - x0
    carved = filled = 0
    for it in range(30):
        walk = np.isin(sub, WALKABLE) & reg
        lab, n = ndimage.label(walk)
        main = lab[sy, sx]
        if main == 0:
            return 0.0, carved, filled
        sizes = np.bincount(lab.ravel(), minlength=n + 1)
        imp_l = set(int(lab[y, x]) for y, x in imp) - {0, main}
        small = sizes < min_pocket
        small[0] = False; small[main] = False
        for l in imp_l:
            small[l] = False
        sm = small[lab]
        if sm.any():
            sub[sm] = TREE
            filled += int(sm.sum())
        todo = [l for l in range(1, n + 1) if l != main and not small[l]]
        if not todo:
            break
        c = np.ones(sub.shape, np.float32)
        c[sub == TREE] = 3; c[sub == ROCK] = 4; c[sub == RIDGE] = 8; c[sub == WATER] = 10; c[sub == LAVA] = 40
        c[np.isin(sub, (HOUSE, FENCE, RUIN))] = 2000; c[~reg] = 1e6
        mys, mxs = np.nonzero(lab == main)
        objs = ndimage.find_objects(lab)
        for l in todo[:80]:
            sl = objs[l - 1]
            cy_, cx_ = np.nonzero(lab[sl] == l)
            py, px = int(cy_[len(cy_) // 2]) + sl[0].start, int(cx_[len(cx_) // 2]) + sl[1].start
            q = int(np.argmin((mys - py) ** 2 + (mxs - px) ** 2))
            qy, qx = int(mys[q]), int(mxs[q])
            m = 30
            a0, a1 = max(0, min(py, qy) - m), min(sub.shape[0], max(py, qy) + m + 1)
            b0, b1 = max(0, min(px, qx) - m), min(sub.shape[1], max(px, qx) + m + 1)
            mc = MCP_Geometric(c[a0:a1, b0:b1], fully_connected=False)
            mc.find_costs([(py - a0, px - b0)], [(qy - a0, qx - b0)])
            for a, b in mc.traceback((qy - a0, qx - b0)):
                for (u, v) in ((a + a0, b + b0), (a + a0 + 1, b + b0), (a + a0, b + b0 + 1), (a + a0 + 1, b + b0 + 1)):
                    if u < sub.shape[0] and v < sub.shape[1] and reg[u, v]:
                        tv = sub[u, v]
                        if tv in (TREE, ROCK, RIDGE):
                            sub[u, v] = GRASS; carved += 1
                        elif tv == LAVA:
                            sub[u, v] = ROAD; carved += 1
                        elif tv == WATER:
                            sub[u, v] = FORD; carved += 1
    walk = np.isin(sub, WALKABLE) & reg
    lab, _ = ndimage.label(walk)
    main = lab[sy, sx]
    return float((lab == main).sum() / max(1, walk.sum())), carved, filled


def step_reachability(P, rng):
    mp = P.profile["reachability"]["MIN_POCKET"]
    important = [(s["y"], s["x"]) for s in P.sites] + [s.get("entry", (s["y"], s["x"])) for s in P.sites] + \
                [(st["y"], st["x"]) for st in P.stations]
    important = [(int(y), int(x)) for y, x in important]
    piers = np.zeros(P.terrain.shape, bool)
    pier_of = {}
    for st in P.stations:
        for c in st.get("pier", []):
            piers[c] = True
            pier_of[c] = st.get("island", 0)
    walk0 = np.isin(P.terrain, WALKABLE) & P.mainland
    l0, _ = ndimage.label(walk0)
    P.pre_reach = float((l0 == l0[P.center[0] + P.home["h"] // 2 + 3, P.center[1]]).sum() / walk0.sum())
    main_piers = piers.copy()
    for c, isl in pier_of.items():
        if isl:
            main_piers[c] = False
    sy, sx = P.home["gates"][0]
    P.reach, P.carved, P.filled = _fix_region(P, P.mainland | main_piers, sy, sx, important, mp)
    P.island_reach = {}
    for iid, meta in P.island_meta.items():
        if meta["kind"] == "wild" and meta["landmarks"]:
            lm = meta["landmarks"][0]
            e = tuple(lm.get("entry", (lm["y"], lm["x"])))
            reg = P.island_lab == iid
            if reg[e] and P.terrain[e] in WALKABLE:
                r, c_, f_ = _fix_region(P, reg, e[0], e[1], important, mp)
                meta["reach_from_landmark"] = r
                P.carved += c_; P.filled += f_
    for iid in P.island_ids:
        docks = [st for st in P.stations if st.get("island") == iid and st["type"] == "dock"]
        if not docks:
            P.island_reach[iid] = None
            continue
        reg = P.island_lab == iid
        for st in docks:
            for c in st["pier"]:
                reg[c] = True
        r, c_, f_ = _fix_region(P, reg, docks[0]["y"], docks[0]["x"], important, mp)
        P.island_reach[iid] = r
        P.carved += c_; P.filled += f_


# ------------------------------------------------------------------------------------------- 14 stats
def step_stats(P, rng):
    t = P.terrain
    land = (t != OCEAN) & (t != DOCK)
    blocked = np.isin(t, BLOCKED_LAND)
    P.blocked_pct = float(blocked.sum() / land.sum())
    P.blocked_incl_water = float((blocked | (t == WATER)).sum() / land.sum())
    P.per_biome_blocked = {BIOMES[b]: float((blocked & (P.biome == b)).sum() / max(1, (land & (P.biome == b)).sum()))
                           for b in range(6)}
    walk = np.isin(t, WALKABLE)
    lab, _ = ndimage.label(walk)
    home_l = lab[P.home["gates"][0]]
    P.islands_on_foot = 0
    for iid in P.island_ids:
        docks = [st for st in P.stations if st.get("island") == iid and st["type"] == "dock"]
        if docks and lab[docks[0]["y"], docks[0]["x"]] == home_l:
            P.islands_on_foot += 1

    def reachable(y, x):
        isl = P.island_lab[y, x]
        if isl == 0:
            return lab[y, x] == home_l
        d = [st for st in P.stations if st.get("island") == isl and st["type"] == "dock"]
        if not d:   # wild island: only its landmark exists; it must be walkable from where you land
            return True
        return lab[y, x] == lab[d[0]["y"], d[0]["x"]]
    ent =[(s, s.get("entry", s.get("gates", [(s["y"], s["x"])])[0])) for s in P.sites]
    def walk_near(y, x):
        y, x = min(max(int(y), 0), P.N - 1), min(max(int(x), 0), P.N - 1)
        if walk[y, x]:
            return y, x
        sub = walk[max(0, y - 3):y + 4, max(0, x - 3):x + 4]
        ys, xs = np.nonzero(sub)
        return (y, x) if len(ys) == 0 else (int(ys[0]) + max(0, y - 3), int(xs[0]) + max(0, x - 3))
    ok = [reachable(*walk_near(*e)) for s, e in ent]
    P.site_reach = sum(ok) / max(1, len(ok))
    P.station_reach = sum(reachable(*walk_near(st["y"], st["x"])) for st in P.stations) / max(1, len(P.stations))
    # regions with a station
    regions = [0] + list(P.island_ids)
    P.regions_with_station = sum(1 for rg in regions if any(st.get("island", 0) == rg for st in P.stations))
    P.n_regions = len(regions)
    # counts per biome per kind
    cnt = {}
    for s in P.sites:
        b = BIOMES[int(P.biome[s["y"], s["x"]])]
        cnt.setdefault(b, {}).setdefault(s["kind"], 0)
        cnt[b][s["kind"]] += 1
    for st in P.stations:
        b = BIOMES[int(P.biome[min(st["y"], P.N - 1), min(st["x"], P.N - 1)])]
        cnt.setdefault(b, {}).setdefault("station:" + st["type"], 0)
        cnt[b]["station:" + st["type"]] += 1
    P.counts = cnt
    # average distance from walkable land to the nearest point of interest (sites + stations)
    m = np.ones(t.shape, bool)
    for s in P.sites:
        m[s["y"], s["x"]] = False
    for st in P.stations:
        m[st["y"], st["x"]] = False
    d = ndimage.distance_transform_edt(m[::2, ::2]) * 2
    wl = walk[::2, ::2]
    P.poi_dist_mean = float(d[wl].mean())
    P.poi_dist_p90 = float(np.percentile(d[wl], 90))
    P.poi_dist_max = float(d[wl].max())
