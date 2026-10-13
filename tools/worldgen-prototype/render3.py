"""Render round-3 images for saved planes: python -I render3.py [seeds...]"""
import os, sys, pickle, time
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import numpy as np
from planegen.core import *
from render_map import render_overview, render_mid
from render_tiles import render
from tmx import TMX

OUT = "A:/GameAssets/worldgen/r3/"
os.makedirs(OUT, exist_ok=True)


def score_window(P, y, x, h, w, fn):
    y0, x0 = max(0, y - h // 2), max(0, x - w // 2)
    return fn(P.terrain[y0:y0 + h, x0:x0 + w], y0, x0)


def pick_views(P):
    V = {}
    hm = P.home
    V["havenbrook"] = (hm["top"] + hm["h"] - 16, hm["x"])
    # stagecoach post on farmland: white biome, most farmsteads/fields nearby
    best = None
    for st in P.stations:
        if st["type"] != "stagecoach" or P.biome[st["y"], st["x"]] != 1:
            continue
        sc = sum(1 for s in P.sites if s["kind"] in ("farmstead", "orchard") and abs(s["y"] - st["y"]) < 30 and abs(s["x"] - st["x"]) < 50)
        if best is None or sc > best[0]:
            best = (sc, st)
    if best:
        V["stagecoach_farmland"] = (best[1]["y"], best[1]["x"])
    # island dock village: island dock with a settlement on the same island close by
    best = None
    for st in P.stations:
        if st["type"] != "dock" or not st.get("island") or P.weights[2][st["y"], st["x"]] < 0.6:
            continue
        if not (40 < st["y"] < P.N - 40 and 60 < st["x"] < P.N - 60):
            continue
        for s in P.sites:
            if s.get("island") == st["island"] and s["kind"] in ("town", "hamlet"):
                d = abs(s["y"] - st["y"]) + abs(s["x"] - st["x"])
                if d < 70 and (best is None or (s["kind"] == "town", -d) > best[0]):
                    best = ((s["kind"] == "town", -d), st, s)
    if best:
        _, st, s = best
        V["island_dock_village"] = ((st["y"] + s["y"]) // 2, (st["x"] + s["x"]) // 2)
    # caravan camp in a mountain pass: caravan with the most ridge around and a road through
    best = None
    for st in P.stations:
        if st["type"] != "caravan" or not st.get("pass"):
            continue
        win = P.terrain[max(0, st["y"] - 30):st["y"] + 30, max(0, st["x"] - 50):st["x"] + 50]
        sc = (win == RIDGE).sum() + 3 * (win == ROAD).sum()
        if best is None or sc > best[0]:
            best = (sc, st)
    if best is None:
        best = (0, next(st for st in P.stations if st["type"] == "caravan"))
    V["caravan_mountain_pass"] = (best[1]["y"] + 6, best[1]["x"])
    return V


for s in [int(a) for a in sys.argv[1:]] or [1, 2]:
    t0 = time.time()
    P = pickle.load(open(os.path.join(HERE, f"r3_world_{s}.pkl"), "rb"))
    render_overview(P, OUT + f"overview_r3_seed{s}.png")
    t_over = time.time() - t0
    # mid zoom views
    hm = P.home
    mids = {"havenbrook": (hm["y"] + 40, hm["x"])}
    farms = [x for x in P.sites if x["kind"] == "farmstead" and P.biome[x["y"], x["x"]] == 1]
    if farms:
        f = max(farms, key=lambda f: sum(1 for g in farms if abs(g["y"] - f["y"]) < 125 and abs(g["x"] - f["x"]) < 200))
        mids["farmland"] = (f["y"], f["x"])
    if P.island_ids:
        isl_docks = [st for st in P.stations if st.get("island")]
        f = max(isl_docks, key=lambda d: sum(1 for g in isl_docks if abs(g["y"] - d["y"]) < 125 and abs(g["x"] - d["x"]) < 200))
        mids["archipelago"] = (f["y"], f["x"])
    if P.volcanoes:
        mids["mountains_volcano"] = P.volcanoes[0]
    blk = [x for x in P.sites if x["kind"] in ("graveyard", "battlefield") and P.biome[x["y"], x["x"]] == 3]
    if blk:
        f = max(blk, key=lambda f: sum(1 for g in blk if abs(g["y"] - f["y"]) < 125 and abs(g["x"] - f["x"]) < 200))
        mids["swamp"] = (f["y"], f["x"])
    for name, (y, x) in mids.items():
        render_mid(P, y, x, OUT + f"mid_r3_seed{s}_{name}.png", title=f"seed {s} {name}")
    t_mid = time.time() - t0 - t_over
    # tile-art close-ups
    tm = TMX(os.path.join(HERE, "hb", "havenbrook.tmx"), os.path.join(HERE, "hb"))
    home_img = tm.render(["Ground", "Foreground", "AboveSprites"])[P.home["skip"] * 16:]
    # Ground layer may be transparent where the Background backdrop drew grass; fill with Background
    bg = tm.render(["Background", "Ground", "Foreground", "AboveSprites"])[P.home["skip"] * 16:]
    home_img = bg
    for name, (y, x) in pick_views(P).items():
        render(P, y, x, path=OUT + f"closeup_r3_seed{s}_{name}.png", home_img=home_img)
        print(s, name, (y, x))
    print(f"seed {s}: overview {t_over:.1f}s mids {t_mid:.1f}s total {time.time() - t0:.1f}s")
