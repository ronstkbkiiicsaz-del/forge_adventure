"""Shared constants and numeric helpers for the PlaneGenerator prototype (WG1 round 3).

Everything here is deterministic: all randomness flows from numpy Generators seeded from the plane seed.
"""
import math

import numpy as np
from PIL import Image

# ---- terrain codes (one int8 per tile) -------------------------------------------------------------
GRASS, ROAD, TREE, ROCK, RIDGE, WATER, OCEAN, BRIDGE, FORD, TOWN, HOUSE, POI, LAVA, FENCE, RUIN, DOCK, FIELD, GRAVE = range(18)
TERRAIN_NAMES = ["grass", "road", "tree", "rock", "ridge", "water", "ocean", "bridge", "ford", "town_floor", "building",
                 "poi_entrance", "lava", "fence", "ruin", "dock", "field", "grave_floor"]
WALKABLE = (GRASS, ROAD, BRIDGE, FORD, TOWN, POI, DOCK, FIELD, GRAVE)
BLOCKED_LAND = (TREE, ROCK, RIDGE, HOUSE, LAVA, FENCE, RUIN)
CLEARABLE = (GRASS, TREE, ROCK)          # what a settlement/landmark footprint may be stamped over

BIOMES = ["wastes", "white", "blue", "black", "red", "green"]
# renderer palette channels: the six biomes with green split into forest / jungle, plus volcanic ash
PALS = ["wastes", "white", "blue", "black", "red", "forest", "jungle", "ash"]


def fbm(rng, H, W, scale, octaves=4, persistence=0.5):
    """Fractal value noise (bicubic-interpolated random lattices), zero mean, unit std."""
    total = np.zeros((H, W), np.float32)
    amp, s = 1.0, float(scale)
    for _ in range(octaves):
        if s < 1.5:
            break
        gh, gw = int(H / s) + 4, int(W / s) + 4
        grid = rng.standard_normal((gh, gw)).astype(np.float32)
        a = np.asarray(Image.fromarray(grid, "F").resize((int(gw * s), int(gh * s)), Image.BICUBIC))
        oy, ox = rng.integers(0, int(s) + 1), rng.integers(0, int(s) + 1)
        total += amp * a[oy:oy + H, ox:ox + W]
        amp *= persistence
        s /= 2
    total -= total.mean()
    total /= total.std() + 1e-9
    return total


def to_uniform(a):
    """Rank-transform to uniform [0,1) so thresholds mean 'fraction of tiles'."""
    flat = a.ravel()
    ranks = np.empty(flat.size, np.float32)
    ranks[np.argsort(flat, kind="stable")] = np.arange(flat.size, dtype=np.float32)
    return (ranks / flat.size).reshape(a.shape)


def disk(r):
    k = int(math.ceil(r))
    y, x = np.mgrid[-k:k + 1, -k:k + 1]
    return (x * x + y * y) <= r * r + 0.01


def stamp(arr, cy, cx, r, value=True, where=None):
    H, W = arr.shape
    d = disk(r)
    k = d.shape[0] // 2
    y0, y1, x0, x1 = cy - k, cy + k + 1, cx - k, cx + k + 1
    sy0, sx0 = max(0, -y0), max(0, -x0)
    y0, x0, y1, x1 = max(0, y0), max(0, x0), min(H, y1), min(W, x1)
    if y0 >= y1 or x0 >= x1:
        return
    dd = d[sy0:sy0 + (y1 - y0), sx0:sx0 + (x1 - x0)]
    if where is not None:
        dd = dd & where[y0:y1, x0:x1]
    arr[y0:y1, x0:x1][dd] = value


def sub_rng(seed, *keys):
    """Independent deterministic stream per (seed, step name, ...) so steps don't perturb each other."""
    h = np.random.SeedSequence([seed] + [abs(hash_str(k)) for k in keys])
    return np.random.default_rng(h)


def hash_str(s):
    # stable across runs (python's hash() is salted)
    v = 2166136261
    for ch in str(s).encode():
        v = ((v ^ ch) * 16777619) & 0xFFFFFFFF
    return v
