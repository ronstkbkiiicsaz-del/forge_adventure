"""Tile-art close-ups (Ninja Adventure tiles) for a round-3 Plane. Reuses the round-2 autotile/sprite tables."""
import os, sys
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(1, os.path.dirname(HERE))
import numpy as np
from PIL import Image
from scipy import ndimage

import closeup2 as R2
from closeup2 import (SH, tile, blit, autotile, load, GRASS_PAL, WATER_PAL, RELIEF_V, SAND_ON_GRASS, WATER_SAND,
                      LAVA_SHEET, LAVA_CRATER, TILLED, TREES, BUSHES, ROCK_BIG, ROCK_SMALL, ROCK_PILE, DECOR, FLOWERS,
                      FERNS, BONES, spr, pick, draw_fence, NAT, DES, VA, HOU, ELE, TOW, G1, G2, W1, W2)
from planegen.core import *
from planegen.core import fbm

SH["tileset_camp"] = load("tileset_camp")
CAMP = "tileset_camp"

HOUSE_SPR = {
    "farm": [(HOU, 0, 0, 4, 3), (HOU, 4, 0, 4, 3), (HOU, 8, 0, 4, 3), (HOU, 12, 0, 4, 3)],
    "island": [(DES, 6, 0, 4, 3), (HOU, 4, 0, 4, 3)],
    "swamp": [(VA, 11, 0, 3, 3), (VA, 0, 0, 4, 3)],
    "mountain": [(HOU, 4, 0, 4, 3), (HOU, 12, 0, 4, 3), (HOU, 8, 0, 4, 3)],
    "forest": [(HOU, 0, 0, 4, 3), (HOU, 8, 0, 4, 3), (VA, 11, 0, 3, 3)],
}
SPRITE = {
    "barn": [(HOU, 25, 7, 4, 7)], "hay": [(ELE, 12, 4, 1, 2)], "crypt": [(VA, 8, 1, 3, 2)],
    "headstone": [(ELE, 4, 2, 1, 1), (ELE, 4, 2, 1, 1), (ELE, 3, 2, 1, 1)],
    "ruin_wall": [(VA, 17, 3, 3, 2)], "ruin_block": [(VA, 0, 3, 2, 2)], "standing_stone": [(HOU, 0, 19, 1, 3)],
    "tower": [(TOW, 22, 0, 2, 2), (TOW, 16, 0, 2, 2), (TOW, 4, 0, 2, 2)],
    "cave": [(HOU, 3, 7, 3, 3)], "dungeon": [(HOU, 0, 7, 3, 3)],
    "post_house": [(HOU, 0, 0, 4, 3)], "wagon": [(ELE, 2, 3, 2, 2), (ELE, 0, 3, 2, 2)], "signpost": [(ELE, 6, 3, 1, 2)],
    "tent": [(CAMP, 4, 0, 3, 3), (CAMP, 7, 0, 3, 3)], "campfire": [(CAMP, 12, 3, 2, 2)],
    "grove_tree": [(NAT, 3, 18, 3, 3)], "elf_tower": [(TOW, 12, 2, 2, 2)],
    "boathouse": [(HOU, 8, 0, 4, 3)], "skiff_hut": [(VA, 11, 0, 3, 3)], "well": [(DES, 0, 3, 2, 2)],
    "hall:farm": [(HOU, 25, 7, 4, 7)], "hall:island": [(DES, 6, 0, 4, 3)], "hall:swamp": [(VA, 11, 0, 3, 3)],
    "hall:mountain": [(HOU, 12, 0, 4, 3)], "hall:forest": [(HOU, 25, 7, 4, 7)],
    "lighthouse": [(TOW, 12, 4, 2, 2)], "shrine": [(HOU, 3, 15, 2, 2)], "rare_resource": [(TOW, 19, 4, 2, 2)],
    "sea_cave": [(HOU, 3, 7, 3, 3)], "chest": [(ELE, 3, 1, 1, 1)], "flag": [(ELE, 6, 3, 1, 2)],
    "planks": [("TilesetFloorDetail", 8, 0, 1, 1), ("TilesetFloorDetail", 7, 0, 1, 1)],
}


def pal_window(P, y0, x0, h, w):
    """8 renderer palette channels for a window, padded with ocean/wastes outside the plane."""
    out = np.zeros((8, h, w), np.float32)
    sy0, sx0, sy1, sx1 = max(0, y0), max(0, x0), min(P.N, y0 + h), min(P.N, x0 + w)
    w6 = P.weights[:, sy0:sy1, sx0:sx1].astype(np.float32)
    t2 = P.tier2_soft[sy0:sy1, sx0:sx1].astype(np.float32)
    ash = np.clip(P.ash[sy0:sy1, sx0:sx1].astype(np.float32) * 1.4, 0, 1)
    pc = np.zeros((8,) + w6.shape[1:], np.float32)
    pc[0:5] = w6[0:5]; pc[5] = w6[5] * (1 - t2); pc[6] = w6[5] * t2
    pc[:7] *= (1 - ash); pc[7] = ash
    out[:, sy0 - y0:sy1 - y0, sx0 - x0:sx1 - x0] = pc
    out[0][out.sum(0) == 0] = 1
    return out


def crop(a, y0, x0, h, w, fill):
    out = np.full((h, w), fill, a.dtype)
    sy0, sx0, sy1, sx1 = max(0, y0), max(0, x0), min(a.shape[0], y0 + h), min(a.shape[1], x0 + w)
    out[sy0 - y0:sy1 - y0, sx0 - x0:sx1 - x0] = a[sy0:sy1, sx0:sx1]
    return out


def render(P, cy, cx, wt=100, ht=60, path=None, home_img=None):
    rng = np.random.default_rng(P.seed * 7919 + cy * 2003 + cx)
    y0, x0 = cy - ht // 2, cx - wt // 2
    pad = 4
    Y0, X0, Hp, Wp = y0 - pad, x0 - pad, ht + 2 * pad, wt + 2 * pad
    Tp = crop(P.terrain, Y0, X0, Hp, Wp, OCEAN)
    pal = pal_window(P, Y0, X0, Hp, Wp)
    dom = pal.argmax(0)
    dsea = crop(P.dist_sea, Y0, X0, Hp, Wp, 0.0)
    isl = crop(P.island_lab, Y0, X0, Hp, Wp, 0)
    wblue = crop(P.weights[2].astype(np.float32), Y0, X0, Hp, Wp, 0.0)
    cone = crop(P.cone, Y0, X0, Hp, Wp, False)
    cone2 = crop(P.cone2, Y0, X0, Hp, Wp, False)
    hr = np.random.default_rng(P.seed + 99)
    hill = fbm(hr, Hp, Wp, 40, 2)
    flw = fbm(hr, Hp, Wp, 6, 2)
    inner1 = (slice(pad - 1, pad + ht + 1), slice(pad - 1, pad + wt + 1))
    inner = (slice(pad, pad + ht), slice(pad, pad + wt))
    up = lambda a: np.repeat(np.repeat(a, 16, 0), 16, 1)
    canvas = np.zeros((ht * 16, wt * 16, 3), np.uint8)
    base = tile("TilesetFloor", 0, 12)
    details = [tile("TilesetFloor", c, 12) for c in (1, 2, 3, 4)]
    for y in range(ht):
        for x in range(wt):
            t = base if rng.random() > 0.07 else details[rng.integers(0, 4)]
            canvas[y * 16:(y + 1) * 16, x * 16:(x + 1) * 16] = t[..., :3]
    land = Tp != OCEAN
    sand = land & (dsea <= 2.6) & ((wblue > 0.15) | (isl > 0)) & ~np.isin(Tp, (WATER, LAVA, RIDGE, HOUSE, TOWN))
    autotile(canvas, (sand | (Tp == OCEAN) | (Tp == DOCK))[inner1], SAND_ON_GRASS, 0, 7)
    autotile(canvas, np.isin(Tp, (ROAD, TOWN, POI, BRIDGE))[inner1], SH["TilesetFloor"], 0, 7)
    autotile(canvas, (Tp == FIELD)[inner1], TILLED, 0, 7)
    water = np.isin(Tp, (WATER, OCEAN, BRIDGE, FORD, DOCK))
    autotile(canvas, water[inner1], SH["TilesetWater"], 0, 6)
    wsand = water & ndimage.binary_dilation(sand, np.ones((3, 3)))
    tmp = canvas.copy()
    autotile(tmp, water[inner1], WATER_SAND, 0, 0)
    mm = up(wsand[inner]); canvas[mm] = tmp[mm]
    lava = Tp == LAVA
    autotile(canvas, (lava & ~cone)[inner1], LAVA_SHEET, 13, 6)
    # palette swap + hill shading (farmland)
    wts = pal[:, pad:pad + ht, pad:pad + wt].transpose(1, 2, 0)
    gp = np.tensordot(wts, GRASS_PAL, axes=([2], [0]))
    hh = hill[pad:pad + ht, pad:pad + wt]; hp = hill[pad - 1:pad + ht - 1, pad - 1:pad + wt - 1]
    q = np.clip(np.round(ndimage.uniform_filter(hp - hh, 3) * 22), -1, 1)
    shade = 1 + (0.05 * q + 0.025 * np.clip(np.round(hh), -1, 1)) * wts[..., 1]
    t2 = crop(P.tier2_soft.astype(np.float32), y0, x0, ht, wt, 0.0)
    shade *= 1 - 0.07 * t2 * (1 - wts[..., 6])
    gp = gp * shade[..., None, None]
    wp = np.tensordot(wts, WATER_PAL, axes=([2], [0]))
    for i, src in enumerate((G1, G2)):
        m = (canvas[..., 0] == src[0]) & (canvas[..., 1] == src[1]) & (canvas[..., 2] == src[2])
        canvas[m] = np.clip(up(gp[:, :, i, :])[m], 0, 255).astype(np.uint8)
    for i, src in enumerate((W1, W2)):
        m = (canvas[..., 0] == src[0]) & (canvas[..., 1] == src[1]) & (canvas[..., 2] == src[2])
        canvas[m] = np.clip(up(wp[:, :, i, :])[m], 0, 255).astype(np.uint8)
    # ridges and volcano cones
    ridge = Tp == RIDGE
    rlab, rn = ndimage.label(ridge)
    domr = dom.copy(); domr[cone] = 7
    dom_r = (np.array([0] + [np.bincount(domr[rlab == i], minlength=8).argmax() for i in range(1, rn + 1)])[rlab]
             if rn else dom)
    for b in range(8):
        sel = ridge & (dom_r == b)
        if sel[inner].any():
            tmp = canvas.copy()
            autotile(tmp, ridge[inner1], RELIEF_V[b], 1, 5, notch=False, strips=False)
            mm = up(sel[inner]); canvas[mm] = tmp[mm]
    if cone2[inner].any():
        tmp = canvas.copy(); autotile(tmp, cone2[inner1], RELIEF_V[7], 1, 5, notch=False, strips=False)
        mm = up(cone2[inner]); canvas[mm] = tmp[mm]
        crater = lava & cone
        tmp = canvas.copy(); autotile(tmp, crater[inner1], LAVA_CRATER, 13, 6)
        mm = up(crater[inner]); canvas[mm] = tmp[mm]
    # Havenbrook from its TMX
    hm = P.home
    if home_img is not None:
        py, px = (hm["top"] - y0) * 16, (hm["left"] - x0) * 16
        hy0, hx0 = max(0, -py), max(0, -px)
        hy1, hx1 = min(home_img.shape[0], canvas.shape[0] - py), min(home_img.shape[1], canvas.shape[1] - px)
        if hy1 > hy0 and hx1 > hx0:
            sub = home_img[hy0:hy1, hx0:hx1]
            m = sub[..., 3] > 0
            canvas[py + hy0:py + hy1, px + hx0:px + hx1][m] = sub[..., :3][m]
    in_home = np.zeros_like(Tp, bool)
    hy, hx = hm["top"] - Y0, hm["left"] - X0
    in_home[max(0, hy):max(0, hy + hm["h"]), max(0, hx):max(0, hx + hm["w"])] = True
    # bridges, docks, fords, fences, crops
    plank = tile("TilesetWater", 5, 13); stone = tile("TilesetFloorDetail", 15, 0)
    blab, _ = ndimage.label(Tp == BRIDGE, np.ones((3, 3)))
    bvert = {i: (sl[0].stop - sl[0].start) >= (sl[1].stop - sl[1].start) for i, sl in enumerate(ndimage.find_objects(blab), 1)}
    dk = Tp == DOCK
    fence = Tp == FENCE
    iron = np.zeros_like(Tp, bool)
    crop_kind = np.full(Tp.shape, -1, np.int8)
    objs_in = [o for o in P.objects if y0 - 12 <= o["y"] < y0 + ht + 4 and x0 - 12 <= o["x"] < x0 + wt + 4]
    for o in objs_in:
        oy, ox = o["y"] - Y0, o["x"] - X0
        if o["key"] == "fence:iron":
            iron[max(0, oy):max(0, oy + o["h"]), max(0, ox):max(0, ox + o["w"])] = True
        if o["key"] == "field":
            crop_kind[max(0, oy):max(0, oy + o["h"]), max(0, ox):max(0, ox + o["w"])] = o.get("crop", 0)
    wheat = tile(ELE, 14, 5); sprout = [tile(NAT, c, 10) for c in (6, 7)]
    for y in range(ht):
        for x in range(wt):
            Y, X = y + pad, x + pad
            v = Tp[Y, X]
            if in_home[Y, X]:
                continue
            if v in (BRIDGE, DOCK):
                vertical = bvert[int(blab[Y, X])] if v == BRIDGE else bool(dk[Y - 1, X] or dk[Y + 1, X])
                p = plank if vertical else np.rot90(plank).copy()
                blit(canvas, p, y * 16, x * 16)
                same = Tp == v; dark = (92, 56, 40)
                if vertical:
                    if not same[Y, X - 1]: canvas[y * 16:(y + 1) * 16, x * 16:x * 16 + 2] = dark
                    if not same[Y, X + 1]: canvas[y * 16:(y + 1) * 16, x * 16 + 14:x * 16 + 16] = dark
                else:
                    if not same[Y - 1, X]: canvas[y * 16:y * 16 + 2, x * 16:(x + 1) * 16] = dark
                    if not same[Y + 1, X]: canvas[y * 16 + 14:y * 16 + 16, x * 16:(x + 1) * 16] = dark
            elif v == FORD:
                blit(canvas, stone, y * 16 + int(rng.integers(-2, 3)), x * 16 + int(rng.integers(-2, 3)))
            elif v == FENCE:
                ir = iron[Y, X]
                draw_fence(canvas, fence, y, x, Y, X, (70, 70, 80) if ir else (170, 112, 60), (36, 34, 40) if ir else (96, 58, 32))
            elif v == FIELD:
                if crop_kind[Y, X] == 1:
                    if Y % 2 == 0: blit(canvas, wheat, y * 16 + 2, x * 16)
                elif Y % 2 == 0:
                    blit(canvas, sprout[(X + Y // 2) % 2], y * 16 + 1, x * 16)
    # objects: trees and rocks (jittered, overlapping), then structures
    sprites = []
    covered = np.zeros_like(Tp, bool)
    struct_cells = np.zeros_like(Tp, bool)
    for o in objs_in:
        if o["key"] in ("fruit_tree", "grove_tree", "hay"):
            struct_cells[o["y"] - Y0, o["x"] - X0] = True
    cells = np.argwhere(np.isin(Tp, (TREE, ROCK)) & ~in_home & ~struct_cells)
    cells = cells[rng.permutation(len(cells))]
    for Y, X in cells:
        if covered[Y, X]:
            continue
        v = Tp[Y, X]; ch = int(dom[Y, X])
        if v == TREE:
            spec = pick(rng, TREES[ch])
            jy, jx = int(rng.integers(-5, 4)), int(rng.integers(-7, 8))
            if rng.random() < 0.06:
                spec = BUSHES[int(rng.integers(0, len(BUSHES)))]
        else:
            tone = "grey" if ch in (2, 3, 4, 5, 6, 7) else "brown"
            rr = rng.random()
            spec = ROCK_PILE[tone][0] if rr < 0.15 else (ROCK_BIG[tone][int(rng.integers(0, 2))] if rr < 0.55 else ROCK_SMALL[tone][int(rng.integers(0, 3))])
            jy, jx = int(rng.integers(-2, 3)), int(rng.integers(-3, 4))
        sh_, c_, r_, sw, shh = spec
        img = spr(spec, ch if v == TREE or ch == 7 else None)
        py = (Y - pad + 1) * 16 - shh * 16 + jy
        px = (X - pad) * 16 + 8 - sw * 8 + jx
        sprites.append((py + shh * 16, py, px, img))
        ry = max(1, shh - 1); rx = max(1, sw // 2 + (1 if sw >= 3 else 0))
        for dy in range(-ry + 1, 1):
            for dx in range(-(rx // 2), rx - rx // 2):
                if rng.random() < 0.75 or (dy == 0 and dx == 0):
                    yy, xx = Y + dy, X + dx
                    if 0 <= yy < Tp.shape[0] and 0 <= xx < Tp.shape[1] and Tp[yy, xx] == v:
                        covered[yy, xx] = True
    for Y in range(1, Tp.shape[0] - 1):
        for X in range(1, Tp.shape[1] - 1):
            if Tp[Y, X] == RIDGE and Tp[Y + 1, X] == RIDGE and rng.random() < 0.28:
                ch = int(dom_r[Y, X])
                img = spr(ROCK_SMALL["brown" if ch == 0 else "grey"][int(rng.integers(0, 3))], 7 if ch == 7 else None)
                sprites.append(((Y + 1) * 16 - 2, (Y - pad) * 16 - 2, (X - pad) * 16 + int(rng.integers(-3, 4)), img))
    for o in objs_in:
        k = o["key"]
        oy, ox = (o["y"] - y0) * 16, (o["x"] - x0) * 16
        bottom = (o["y"] + o["h"] - y0) * 16
        if k.startswith("house:"):
            lst = HOUSE_SPR[k.split(":")[1]]
            spec = lst[o.get("v", 0) % len(lst)]
        elif k == "fruit_tree":
            spec = (NAT, 0, 0, 2, 2) if o.get("v", 0) else (NAT, 14, 0, 2, 2)
        elif k == "shipwreck":
            img = tile("TilesetWater", 26, 0, 2, 1).copy()
            img[..., :3] = (img[..., :3] * 0.55).astype(np.uint8)
            img = np.rot90(img).copy() if (o["x"] % 2) else img
            sprites.append((bottom, oy, ox + 4, img))
            continue
        elif k in ("boat", "skiff"):
            img = tile("TilesetWater", 26, 0, 2, 1)
            if k == "skiff":
                img = img.copy(); img[..., :3] = (img[..., :3] * 0.7).astype(np.uint8)
            dy_, dx_ = o.get("dir", (1, 0))
            by, bx = (o["y"], o["x"] + 1) if dy_ != 0 else (o["y"] + 1, o["x"] - 1)
            sprites.append(((by - y0 + 1) * 16, (by - y0) * 16 + 2, (bx - x0) * 16, img))
            continue
        elif k in SPRITE:
            lst = SPRITE[k]
            spec = lst[o.get("v", 0) % len(lst)]
        elif k == "bones":
            sh_, c_, r_ = BONES[(o["y"] * 7 + o["x"]) % len(BONES)]
            sprites.append((oy + 1, oy, ox, tile(sh_, c_, r_)))
            continue
        elif k == "ruin_wall_v":
            for i in range(o["h"]):
                sprites.append((oy + (i + 1) * 16, oy + i * 16 - 16, ox, spr((VA, 2, 3, 1, 2))))
            continue
        else:
            continue
        img = spr(spec)
        w_px = img.shape[1]
        px = ox + (o["w"] * 16 - w_px) // 2
        if k in ("headstone",):
            sprites.append((bottom, bottom - img.shape[0] - 2, px, img)); continue
        if k == "grove_tree":
            px = ox + 8 - w_px // 2
        sprites.append((bottom, bottom - img.shape[0], px, img))
    # ground decoration
    for Y in range(pad, pad + ht):
        for X in range(pad, pad + wt):
            if Tp[Y, X] != GRASS or covered[Y, X] or in_home[Y, X]:
                continue
            ch = int(dom[Y, X]); rr = rng.random(); img = None
            if ch == 0 and flw[Y, X] > 0.9 and rr < 0.45:
                img = tile(*FLOWERS[int(rng.integers(0, len(FLOWERS)))])
            elif ch == 6 and rr < 0.16:
                img = tile(*FERNS[int(rng.integers(0, len(FERNS)))])
            elif ch == 1 and flw[Y, X] > 1.1 and rr < 0.3:
                img = tile(*FLOWERS[int(rng.integers(0, 3))])
            elif sand[Y, X] and rr < 0.03:
                img = tile(DES, 14, 10 + int(rng.integers(0, 2)))
            elif rr < 0.04:
                c, r = DECOR[ch][int(rng.integers(0, len(DECOR[ch])))]
                img = tile("TilesetFloorDetail", c, r)
            if img is not None:
                sprites.append(((Y + 1) * 16 - 15, (Y - pad) * 16 + int(rng.integers(-2, 3)), (X - pad) * 16 + int(rng.integers(-3, 4)), img))
    sprites.sort(key=lambda o: o[0])
    for _, py, px, img in sprites:
        blit(canvas, img, py, px)
    im = Image.fromarray(canvas).resize((wt * 32, ht * 32), Image.NEAREST)
    if path:
        im.save(path)
    return im
