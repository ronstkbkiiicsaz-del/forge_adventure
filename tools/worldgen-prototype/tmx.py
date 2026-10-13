"""Minimal Tiled TMX reader/renderer (CSV layers, external tsx tilesets, per-tile collision boxes)."""
import os
import xml.etree.ElementTree as ET
import numpy as np
from PIL import Image

FLIP_MASK = 0x1FFFFFFF


class Tileset:
    def __init__(self, path, firstgid):
        root = ET.parse(path).getroot()
        self.firstgid = firstgid
        self.cols = int(root.get("columns"))
        img = root.find("image")
        self.img = np.array(Image.open(os.path.join(os.path.dirname(path), img.get("source"))).convert("RGBA"))
        self.count = int(root.get("tilecount"))
        self.collide = {}
        for t in root.findall("tile"):
            og = t.find("objectgroup")
            if og is None:
                continue
            area = 0.0
            for o in og.findall("object"):
                area += float(o.get("width", 0)) * float(o.get("height", 0))
            self.collide[int(t.get("id"))] = area / 256.0

    def tile(self, lid):
        r, c = divmod(lid, self.cols)
        return self.img[r * 16:(r + 1) * 16, c * 16:(c + 1) * 16]


class TMX:
    def __init__(self, path, tileset_dir):
        root = ET.parse(path).getroot()
        self.w, self.h = int(root.get("width")), int(root.get("height"))
        self.tilesets = []
        for ts in root.findall("tileset"):
            src = os.path.basename(ts.get("source"))
            self.tilesets.append(Tileset(os.path.join(tileset_dir, src), int(ts.get("firstgid"))))
        self.tilesets.sort(key=lambda t: t.firstgid)
        self.layers = {}
        for ly in root.findall("layer"):
            data = ly.find("data").text
            arr = np.array([int(v) for v in data.replace("\n", "").split(",") if v.strip()], np.int64)
            self.layers[ly.get("name")] = (arr & FLIP_MASK).reshape(self.h, self.w)
        self.objects = []
        for og in root.findall("objectgroup"):
            for o in og.findall("object"):
                self.objects.append(dict(template=os.path.basename(o.get("template", "")), x=float(o.get("x")),
                                         y=float(o.get("y")), w=float(o.get("width", 0)), h=float(o.get("height", 0))))

    def lookup(self, gid):
        ts = None
        for t in self.tilesets:
            if gid >= t.firstgid:
                ts = t
        return ts, gid - ts.firstgid

    def render(self, layers):
        out = np.zeros((self.h * 16, self.w * 16, 4), np.uint8)
        for name in layers:
            L = self.layers.get(name)
            if L is None:
                continue
            for y in range(self.h):
                for x in range(self.w):
                    g = int(L[y, x])
                    if g == 0:
                        continue
                    ts, lid = self.lookup(g)
                    t = ts.tile(lid)
                    m = t[..., 3] > 0
                    out[y * 16:(y + 1) * 16, x * 16:(x + 1) * 16][m] = t[m]
        return out

    def blocked(self, layers=("Ground", "Foreground"), thresh=0.4):
        b = np.zeros((self.h, self.w), bool)
        for name in layers:
            L = self.layers.get(name)
            if L is None:
                continue
            for y in range(self.h):
                for x in range(self.w):
                    g = int(L[y, x])
                    if g:
                        ts, lid = self.lookup(g)
                        if ts.collide.get(lid, 0) >= thresh:
                            b[y, x] = True
        return b
