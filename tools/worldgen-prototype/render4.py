"""Round-4 (islands) renders: python -I render4.py [seed]"""
import os, sys, pickle
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import numpy as np
from planegen.core import *
from render_map import render_overview, render_mid
from render_tiles import render
from tmx import TMX

OUT = "A:/GameAssets/worldgen/r4/"
os.makedirs(OUT, exist_ok=True)
s = int(sys.argv[1]) if len(sys.argv) > 1 else 1
P = pickle.load(open(os.path.join(HERE, f"r3_world_{s}.pkl"), "rb"))
render_overview(P, OUT + f"overview_r4_seed{s}.png")
# archipelago mid zoom: window with the most island settlements + wild islands
cands = [m["settlement"] for m in P.island_meta.values() if m.get("settlement")]
def score(c):
    n = sum(1 for m in P.island_meta.values() for t in m.get("settlements", []) if abs(t["y"] - c["y"]) < 125 and abs(t["x"] - c["x"]) < 200)
    n += 0.5 * sum(1 for m in P.island_meta.values() if m["kind"] == "wild" and "c" in m and abs(m["c"][0] - c["y"]) < 125 and abs(m["c"][1] - c["x"]) < 200)
    return n
best = max(cands, key=score)
render_mid(P, best["y"], best["x"], OUT + f"mid_r4_seed{s}_archipelago.png", title=f"seed {s} archipelago")
tm = TMX(os.path.join(HERE, "hb", "havenbrook.tmx"), os.path.join(HERE, "hb"))
home_img = tm.render(["Background", "Ground", "Foreground", "AboveSprites"])[P.home["skip"] * 16:]
# island port town with its harbour: biggest port town; centre between town and its dock
ports = [(m["settlement"], (m.get("docks") or [None])[0]) for m in P.island_meta.values()
         if m.get("settlement") and m["settlement"]["island_kind"] == "port_town" and m.get("docks")]
t, d = max(ports, key=lambda td: td[0]["w"] * td[0]["h"])
# centre on the harbour, leaning into the town by up to 35 tiles so the pier, hut, boat and streets all show
vy, vx = t["y"] - d["y"], t["x"] - d["x"]
n = max(1.0, (vy * vy + vx * vx) ** 0.5)
k = 0.3
cy, cx = int(d["y"] + vy * k), int(d["x"] + vx * k)
render(P, cy, cx, path=OUT + f"closeup_r4_seed{s}_island_port_town.png", home_img=home_img)
# wild island: one that fits mostly in a 100x60 window and has its landmark
wild = []
for iid, m in P.island_meta.items():
    if m["kind"] != "wild" or not m["landmarks"]:
        continue
    ys, xs = (P.island_lab == iid).nonzero()
    hgt, wid = ys.max() - ys.min(), xs.max() - xs.min()
    if hgt < 55 and wid < 92:
        wild.append((m["size"], int(ys.mean()), int(xs.mean()), m))
wild.sort(key=lambda w: -w[0])
_, wy, wx, wm = wild[0]
render(P, wy, wx, path=OUT + f"closeup_r4_seed{s}_wild_island.png", home_img=home_img)
print("port town", t["name"], t["w"], t["h"], "dock", d["name"], "| wild island", wm["id"], wm["size"], [l["kind"] for l in wm["landmarks"]])
