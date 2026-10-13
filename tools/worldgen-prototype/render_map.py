"""Overview (whole plane) and mid-zoom (per-tile colour + icons) renders for a round-3 Plane."""
import numpy as np
from PIL import Image, ImageDraw
from scipy import ndimage
from planegen.core import *

PALC = np.array([(176, 168, 140), (150, 205, 110), (120, 200, 140), (95, 105, 80), (150, 138, 122),
                 (100, 165, 75), (40, 170, 70), (80, 76, 78)], np.float32)
TREEC = np.array([(110, 130, 80), (70, 140, 60), (40, 130, 80), (55, 62, 45), (100, 92, 80),
                  (35, 100, 45), (10, 110, 40), (60, 58, 58)], np.float32)
STATION_STYLE = {  # colour, shape
    "dock": ((60, 120, 255), "square"), "stagecoach": ((255, 220, 40), "circle"), "caravan": ((255, 130, 30), "triangle"),
    "elfgrove": ((60, 230, 90), "diamond"), "skiff": ((40, 200, 190), "hex")}
SITE_STYLE = {"cave": (255, 140, 0), "dungeon": (240, 40, 200), "graveyard": (225, 225, 255), "battlefield": (200, 40, 40),
              "ruin": (200, 190, 170), "standing_stones": (160, 160, 255), "lone_tower": (140, 110, 200),
              "orchard": (255, 160, 190), "farmstead": (250, 230, 120), "hamlet": (255, 255, 255),
              "lighthouse": (255, 255, 160), "shrine": (120, 220, 255), "shipwreck": (150, 100, 60), "sea_cave": (255, 170, 60),
              "pirate_cove": (20, 20, 20), "treasure_cove": (255, 215, 0), "rare_resource": (0, 255, 200)}


def colour_tiles(P, y0, x0, h, w):
    sl = (slice(y0, y0 + h), slice(x0, x0 + w))
    T = P.terrain[sl]
    w6 = P.weights[(slice(None),) + sl].astype(np.float32)
    t2 = P.tier2_soft[sl].astype(np.float32)
    ash = np.clip(P.ash[sl].astype(np.float32) * 1.4, 0, 1)
    pc = np.zeros((8,) + T.shape, np.float32)
    pc[0:5] = w6[0:5]; pc[5] = w6[5] * (1 - t2); pc[6] = w6[5] * t2
    pc[:7] *= (1 - ash); pc[7] = ash
    pw = pc.transpose(1, 2, 0)
    img = np.tensordot(pw, PALC, axes=1)
    img *= (1 - 0.1 * (P.tier[sl] == 2))[..., None]
    beach = (P.dist_sea[sl] <= 2.5) & (T != OCEAN) & ((w6[2] > 0.2) | (P.island_lab[sl] > 0))
    img[beach & (T == GRASS)] = (235, 215, 160)
    tc = np.tensordot(pw, TREEC, axes=1)
    img[T == TREE] = tc[T == TREE]
    img[T == ROCK] = (150, 145, 140)
    img[T == RIDGE] = (100, 96, 98)
    img[P.cone[sl]] = (55, 48, 50)
    img[T == WATER] = (70, 140, 210)
    img[P.river[sl] & (T == WATER)] = (80, 155, 225)
    img[T == OCEAN] = (40, 85, 160)
    yy, xx = np.mgrid[y0:y0 + h, x0:x0 + w]
    sea_zone = (T == OCEAN) & (ndimage.distance_transform_edt(T == OCEAN) > 3) & (w6[2] > 0.3)
    img[sea_zone & (((yy + xx) % 10) == 0)] = (70, 120, 190)
    img[T == LAVA] = (255, 100, 20)
    img[T == ROAD] = (225, 195, 130)
    img[T == BRIDGE] = (140, 90, 50)
    img[T == FORD] = (200, 220, 235)
    img[T == TOWN] = (215, 175, 120)
    img[T == HOUSE] = (150, 60, 45)
    img[T == FIELD] = np.where((yy[T == FIELD] % 2 == 0)[:, None], (180, 160, 60), (225, 205, 90))
    img[T == FENCE] = (120, 80, 40)
    img[T == GRAVE] = (120, 110, 130)
    img[T == RUIN] = (175, 165, 155)
    img[T == DOCK] = (250, 250, 250)
    return img


def icon(d, x, y, kind, r):
    col, shape = STATION_STYLE[kind]
    if shape == "square":
        d.rectangle([x - r, y - r, x + r, y + r], fill=col, outline=(0, 0, 0), width=2)
        d.line([(x, y - r + 2), (x, y + r - 2)], fill=(255, 255, 255), width=2)
    elif shape == "circle":
        d.ellipse([x - r, y - r, x + r, y + r], fill=col, outline=(0, 0, 0), width=2)
        d.ellipse([x - r // 3, y - r // 3, x + r // 3, y + r // 3], fill=(90, 60, 20))
    elif shape == "triangle":
        d.polygon([(x, y - r - 2), (x + r + 2, y + r), (x - r - 2, y + r)], fill=col, outline=(0, 0, 0))
    elif shape == "diamond":
        d.polygon([(x, y - r - 2), (x + r + 2, y), (x, y + r + 2), (x - r - 2, y)], fill=col, outline=(0, 0, 0))
    elif shape == "hex":
        d.polygon([(x - r, y), (x - r // 2, y - r), (x + r // 2, y - r), (x + r, y), (x + r // 2, y + r), (x - r // 2, y + r)],
                  fill=col, outline=(0, 0, 0))


def wild_marks(d, P, y0, x0, sc, r, win=None):
    """wild islands (no dock): brown ring with a W at the island's centre of mass"""
    for iid, m in getattr(P, "island_meta", {}).items():
        if m["kind"] != "wild":
            continue
        if "c" not in m:
            ys, xs = (P.island_lab == iid).nonzero()
            m["c"] = (int(ys.mean()), int(xs.mean()))
        cy, cx = m["c"]
        if win and not (y0 <= cy < y0 + win[0] and x0 <= cx < x0 + win[1]):
            continue
        X, Y = (cx - x0) * sc, (cy - y0) * sc
        d.ellipse([X - r, Y - r, X + r, Y + r], outline=(120, 60, 20), width=3, fill=(240, 220, 170))
        d.text((X - 3, Y - 6), "W", fill=(120, 60, 20))


def legend(d, lines, x=10, y=10, wpx=470):
    d.rectangle([x, y, x + wpx, y + 10 + 18 * len(lines)], fill=(18, 18, 18))
    for i, (s, ic) in enumerate(lines):
        if ic:
            if ic[0] == "station":
                icon(d, x + 14, y + 14 + 18 * i, ic[1], 6)
            else:
                d.ellipse([x + 9, y + 9 + 18 * i, x + 19, y + 19 + 18 * i], fill=ic[1], outline=(0, 0, 0))
        d.text((x + 28, y + 8 + 18 * i), s, fill=(255, 255, 255))


def legend_lines(P, title):
    ir = [v for v in P.island_reach.values() if v is not None]
    L = [(title, None),
         (f"{P.N}x{P.N} tiles; blocked land {P.blocked_pct*100:.1f}% (+water {P.blocked_incl_water*100:.1f}%)", None),
         (f"mainland reachable {P.reach*100:.2f}%  islands {P.island_stats['islands']}: {P.island_stats['inhabited']} inhabited+docked ({P.island_stats['island_docks']} harbour docks) "
          f"({P.island_stats['port_towns']} port towns, {P.island_stats['villages']} villages), {P.island_stats['wild']} wild", None),
         (f"inhabited share of island area {P.island_stats['inhabited_area_share']*100:.0f}%; no island reachable on foot", None),
         (f"avg distance to nearest POI {P.poi_dist_mean:.0f} tiles (90% within {P.poi_dist_p90:.0f})", None)]
    for k, nm in [("dock", "dock (islands + coast)"), ("stagecoach", "stagecoach post (plains/meadow)"),
                  ("caravan", "caravan camp (mountains)"), ("elfgrove", "elf-guide grove (forest)"),
                  ("skiff", "skiff landing (swamp)")]:
        L.append((f"station: {nm}", ("station", k)))
    for k in ["hamlet", "farmstead", "cave", "dungeon", "graveyard", "battlefield", "ruin", "standing_stones", "lone_tower", "orchard",
              "lighthouse", "shrine", "shipwreck", "sea_cave", "pirate_cove", "treasure_cove", "rare_resource"]:
        L.append((k.replace("_", " "), ("site", SITE_STYLE[k])))
    L.append(("W ring = wild island: no dock, needs own boat / glider / flier / swim", ("site", (240, 220, 170))))
    L.append(("towns = true-size footprints (dark red = buildings); hatched sea = sea encounter zone", None))
    L.append(("orange = lava, dark cone = volcano, tan = road, pale = ford", None))
    return L


def render_overview(P, path, out_px=2000):
    img = colour_tiles(P, 0, 0, P.N, P.N)
    im = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8))
    sc = out_px / P.N
    if sc != 1:
        im = im.resize((out_px, out_px), Image.NEAREST if sc >= 1 else Image.BOX)
    d = ImageDraw.Draw(im)
    for s in P.sites:
        y, x = s["y"] * sc, s["x"] * sc
        if s["kind"] == "town":
            d.rectangle([s["left"] * sc, s["top"] * sc, (s["left"] + s["w"]) * sc, (s["top"] + s["h"]) * sc],
                        outline=(255, 255, 255) if not s.get("home") else (255, 230, 0), width=2)
        elif s["kind"] in SITE_STYLE and s["kind"] not in ("farmstead", "orchard"):
            r = 3
            d.ellipse([x - r, y - r, x + r, y + r], fill=SITE_STYLE[s["kind"]], outline=(0, 0, 0))
    for st in P.stations:
        icon(d, st["x"] * sc, st["y"] * sc, st["type"], 5)
    wild_marks(d, P, 0, 0, sc, 7)
    cy, cx = P.center[0] * sc, P.center[1] * sc
    d.polygon([(cx, cy - 16), (cx + 5, cy - 5), (cx + 16, cy), (cx + 5, cy + 5), (cx, cy + 16), (cx - 5, cy + 5), (cx - 16, cy), (cx - 5, cy - 5)],
              outline=(0, 0, 0), fill=(255, 230, 0))
    legend(d, legend_lines(P, f"WG1 round 3 - home plane, seed {P.seed}  (yellow star = Havenbrook)"))
    im.save(path)


def render_mid(P, cy, cx, path, h=250, w=400, px=5, title=""):
    y0, x0 = max(0, min(P.N - h, cy - h // 2)), max(0, min(P.N - w, cx - w // 2))
    img = colour_tiles(P, y0, x0, h, w)
    im = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8)).resize((w * px, h * px), Image.NEAREST)
    d = ImageDraw.Draw(im)
    # outlines for buildings
    T = P.terrain[y0:y0 + h, x0:x0 + w]
    for o in P.objects:
        if not (y0 <= o["y"] < y0 + h and x0 <= o["x"] < x0 + w):
            continue
        if o["key"].startswith("house") or o["key"].startswith("hall") or o["key"] in ("barn", "post_house", "boathouse", "skiff_hut", "tent"):
            Y, X = (o["y"] - y0) * px, (o["x"] - x0) * px
            d.rectangle([X, Y, X + o["w"] * px - 1, Y + o["h"] * px - 1], outline=(70, 25, 20))
            d.rectangle([X, Y, X + o["w"] * px - 1, Y + px], fill=(200, 80, 50))
    for s in P.sites:
        if not (y0 <= s["y"] < y0 + h and x0 <= s["x"] < x0 + w):
            continue
        Y, X = (s["y"] - y0) * px, (s["x"] - x0) * px
        if s["kind"] in SITE_STYLE:
            r = 6
            d.ellipse([X - r, Y - r, X + r, Y + r], fill=SITE_STYLE[s["kind"]], outline=(0, 0, 0), width=2)
        if s["kind"] in ("town", "hamlet"):
            d.text((X - 20, (s["top"] - y0) * px - 14), s["name"], fill=(255, 255, 255), stroke_width=2, stroke_fill=(0, 0, 0))
    for st in P.stations:
        if y0 <= st["y"] < y0 + h and x0 <= st["x"] < x0 + w:
            icon(d, (st["x"] - x0) * px, (st["y"] - y0) * px, st["type"], 9)
    wild_marks(d, P, y0, x0, px, 14, (h, w))
    d.rectangle([0, 0, w * px, 22], fill=(18, 18, 18))
    d.text((8, 5), f"{title}  -  tiles y {y0}..{y0 + h}, x {x0}..{x0 + w}  ({px} px per tile)", fill=(255, 255, 255))
    im.save(path)
    return (y0, x0)
