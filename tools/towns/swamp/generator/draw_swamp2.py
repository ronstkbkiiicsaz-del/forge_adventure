"""Swamp town improvement pass (2026-10-13 b): the fishing lift-net, the old battlefield, square furniture and
lamplight pools. Same style rules as draw_swamp.py."""
import math, random
from PIL import Image, ImageDraw
from draw_swamp import OL, WOOD, STONE, IRON, MOSS, new, outline_rect, halo


def _layer(size):
    lay = Image.new('RGBA', size, (0, 0, 0, 0))
    return lay, ImageDraw.Draw(lay)


def _pole(d, a, b, w=4, col=None, hi=None):
    col = col or WOOD[1]
    d.line([a, b], fill=OL, width=w + 2)
    d.line([a, b], fill=col, width=w)
    if hi:
        d.line([(a[0], a[1] - 1), (b[0], b[1] - 1)], fill=hi, width=1)


def _bez(p0, p1, p2, n=16):
    return [((1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * p1[0] + t * t * p2[0],
             (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * p1[1] + t * t * p2[1]) for t in [i / n for i in range(n + 1)]]


ROPE_D, ROPE_L = (70, 58, 42), (200, 180, 130)
BAMBOO = (186, 160, 100)

# geometry of the lift-net sprite (pixels inside the 240x224 sprite)
A = (80, 26)                    # apex of the A-frame (pivot)
T = (168, 56)                   # boom tip, above the net
C = (10, 2)                     # counterweight end
HUB = (168, 98)
FEET = [(68, 92), (92, 92)]
WINCH_PX = (26, 114)


def lift_net():
    """15x14 tiles: a counterweighted lift-net (carrelet) worked from a stilted platform. A-frame on the deck, long
    boom with a stone counterweight on the short end, the net raised dripping out of the water on four bowed bamboo
    spreaders, a haul rope to the winch, eel and crayfish traps hanging from the boom, a lantern at the pivot."""
    W_, H_ = 240, 224
    im = Image.new('RGBA', (W_, H_), (0, 0, 0, 0))
    corners_w = [(122, 122), (214, 122), (214, 182), (122, 182)]
    lift = 20
    corners = [(x, y - lift) for (x, y) in corners_w]
    # on the water: shadow, ripples at the corners, drips
    lay, dl = _layer((W_, H_))
    dl.ellipse([112, 112, 224, 200], fill=(8, 16, 12, 70))
    for (x, y) in corners_w:
        dl.ellipse([x - 8, y - 3, x + 8, y + 3], outline=(150, 170, 140, 170))
    for i in range(14):
        x = 132 + i * 6
        dl.line([x, 178 + (i % 3), x, 182 + (i % 3)], fill=(170, 200, 190, 160))
    im.alpha_composite(lay)

    def P(u, v):
        x0 = corners[0][0] + (corners[1][0] - corners[0][0]) * u
        y0 = corners[0][1] + (corners[3][1] - corners[0][1]) * v
        sag = 22 * (1 - (2 * u - 1) ** 2) * (1 - (2 * v - 1) ** 2)
        return (x0, y0 + sag)
    lay, dl = _layer((W_, H_))
    poly = [P(u / 12, 0) for u in range(13)] + [P(1, v / 12) for v in range(13)] + \
           [P(1 - u / 12, 1) for u in range(13)] + [P(0, 1 - v / 12) for v in range(13)]
    dl.polygon(poly, fill=(30, 44, 36, 100))
    for i in range(13):
        dl.line([P(i / 12, v / 24) for v in range(25)], fill=(204, 190, 146, 180))
        dl.line([P(u / 24, i / 12) for u in range(25)], fill=(204, 190, 146, 180))
    dl.line(poly + [poly[0]], fill=(150, 126, 84, 255), width=2)
    im.alpha_composite(lay)
    d = ImageDraw.Draw(im)
    for (x, y, f) in ((160, 116, 0), (172, 122, 1), (166, 128, 0), (178, 114, 1), (154, 124, 1), (184, 126, 0)):
        d.ellipse([x - 4, y - 2, x + 4, y + 2], fill=OL)
        d.ellipse([x - 3, y - 1, x + 3, y + 1], fill=(190, 204, 210))
        d.point((x + (4 if f else -4), y), fill=OL)
        d.point((x - 1, y - 1), fill=(240, 250, 255))
    pts = [(150 + i * 3, 132 + int(2 * math.sin(i))) for i in range(9)]
    d.line(pts, fill=OL, width=4); d.line(pts, fill=(96, 84, 60), width=2)
    # spreader arms
    for (x, y) in corners:
        mid = ((HUB[0] + x) / 2, (HUB[1] + y) / 2 - 16)
        pts = _bez(HUB, mid, (x, y))
        d.line(pts, fill=OL, width=5)
        d.line(pts, fill=BAMBOO, width=3)
        for k in range(3, len(pts) - 1, 4):
            d.point(pts[k], fill=(120, 100, 60))
    d.ellipse([HUB[0] - 4, HUB[1] - 4, HUB[0] + 4, HUB[1] + 4], fill=OL)
    d.ellipse([HUB[0] - 2, HUB[1] - 2, HUB[0] + 2, HUB[1] + 2], fill=WOOD[2])
    d.line([T, HUB], fill=ROPE_D); d.line([(T[0] + 1, T[1]), (HUB[0] + 1, HUB[1])], fill=ROPE_L)
    # shadow of the boom and frame on the water / deck
    lay, dl = _layer((W_, H_))
    dl.line([(C[0] + 8, C[1] + 70), (T[0] + 8, T[1] + 70)], fill=(8, 16, 12, 60), width=6)
    im.alpha_composite(lay)
    d = ImageDraw.Draw(im)
    # A-frame
    for f in FEET:
        _pole(d, f, A, 5, WOOD[1], WOOD[0])
    _pole(d, (71, 64), (89, 64), 2, WOOD[2])
    # boom
    _pole(d, C, T, 7, WOOD[1], WOOD[0])
    d.line([(C[0], C[1] + 2), (T[0], T[1] + 2)], fill=WOOD[2])
    d.ellipse([A[0] - 4, A[1] - 4, A[0] + 4, A[1] + 4], fill=OL)
    d.ellipse([A[0] - 2, A[1] - 2, A[0] + 2, A[1] + 2], fill=IRON[0])
    for t in (0.2, 0.62, 0.85):
        x, y = A[0] + (T[0] - A[0]) * t, A[1] + (T[1] - A[1]) * t
        d.line([x - 1, y - 4, x + 1, y + 4], fill=ROPE_L)
    # counterweight: stones in a rope sling
    d.line([C, (12, 34)], fill=ROPE_D); d.line([(C[0] + 1, C[1]), (13, 34)], fill=ROPE_L)
    stones = ((6, 40, 6), (16, 39, 6), (11, 46, 6), (4, 49, 4), (19, 48, 4))
    for (x, y, r) in stones:
        d.ellipse([x - r - 1, y - r - 1, x + r + 1, y + r + 1], fill=OL)
    for (x, y, r) in stones:
        d.ellipse([x - r, y - r, x + r, y + r], fill=STONE[1])
        d.ellipse([x - r + 1, y - r + 1, x, y], fill=STONE[0])
    for k in range(0, 24, 4):
        d.line([k, 34, 22 - k // 2, 54], fill=(170, 150, 104))
    lay, dl = _layer((W_, H_))
    dl.ellipse([0, 58, 26, 64], fill=(8, 16, 12, 80))
    im.alpha_composite(lay)
    d = ImageDraw.Draw(im)
    # haul rope to the winch
    B1 = (A[0] + (T[0] - A[0]) * 0.3, A[1] + (T[1] - A[1]) * 0.3)
    d.line([B1, WINCH_PX], fill=ROPE_D); d.line([(B1[0] + 1, B1[1]), (WINCH_PX[0] + 1, WINCH_PX[1])], fill=ROPE_L)
    # traps hanging from the boom
    for t, kind in ((0.45, 'eel'), (0.62, 'eel'), (0.78, 'cray'), (-0.35, 'cray')):
        x, y = A[0] + (T[0] - A[0]) * t, A[1] + (T[1] - A[1]) * t
        ln = 16 if kind == 'eel' else 12
        d.line([(x, y + 2), (x, y + ln)], fill=ROPE_L)
        if kind == 'eel':
            d.rectangle([x - 3, y + ln, x + 3, y + ln + 14], fill=OL)
            d.rectangle([x - 2, y + ln + 1, x + 2, y + ln + 13], fill=(162, 134, 86))
            for yy in range(int(y + ln + 3), int(y + ln + 13), 3):
                d.line([x - 2, yy, x + 2, yy], fill=(112, 88, 54))
            d.ellipse([x - 2, y + ln + 11, x + 2, y + ln + 15], fill=(50, 40, 30))
        else:
            d.ellipse([x - 5, y + ln, x + 5, y + ln + 9], fill=OL)
            d.ellipse([x - 4, y + ln + 1, x + 4, y + ln + 8], fill=(150, 126, 84))
            d.line([x - 4, y + ln + 4, x + 4, y + ln + 4], fill=(100, 80, 54))
    # lantern under the pivot
    d.line([(A[0], A[1] + 3), (A[0], A[1] + 12)], fill=OL)
    outline_rect(d, [A[0] - 3, A[1] + 12, A[0] + 3, A[1] + 20], (255, 196, 90))
    d.point((A[0], A[1] + 15), fill=(255, 252, 220))
    im = halo(im, A[0] + 0.5, A[1] + 16, 26, (255, 176, 80), 120)
    return im


def winch():
    im, d = new(16, 16)
    d.ellipse([1, 11, 15, 15], fill=(0, 0, 0, 60))
    for x in (1, 12):
        d.rectangle([x, 4, x + 3, 14], fill=OL); d.rectangle([x + 1, 5, x + 2, 13], fill=WOOD[1])
    d.rectangle([4, 6, 12, 12], fill=OL)
    d.rectangle([4, 7, 12, 11], fill=(186, 160, 110))
    for x in range(5, 12, 2):
        d.line([x, 7, x, 11], fill=(140, 116, 76))
    d.line([13, 9, 15, 6], fill=OL); d.line([15, 6, 15, 3], fill=OL); d.point((15, 3), fill=IRON[0])
    return im


def light_pool(r=24):
    """3x3 warm pool of lamplight on the ground or water (drawn on Ground, under sprites)."""
    im = Image.new('RGBA', (48, 48), (0, 0, 0, 0))
    px = im.load()
    for y in range(48):
        for x in range(48):
            dd = math.hypot(x + 0.5 - 24, (y + 0.5 - 24) * 1.25) / r
            if dd < 1:
                px[x, y] = (255, 184, 92, int(82 * (1 - dd) ** 1.7))
    return im


def scorch(seed):
    """2x2 scorched, ash-grey ground with charred sticks."""
    im = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    px = im.load()
    rnd = random.Random(seed)
    blobs = [(rnd.uniform(10, 22), rnd.uniform(10, 22), rnd.uniform(6, 11)) for _ in range(4)]
    for y in range(32):
        for x in range(32):
            v = max(1 - math.hypot(x - bx, y - by) / br for (bx, by, br) in blobs)
            v *= min(1.0, min(x, 31 - x, y, 31 - y) / 4.0)
            if v > 0:
                col = (38, 32, 28) if rnd.random() > 0.12 else (120, 116, 108)
                px[x, y] = col + (int(min(1, v * 1.8) * 215),)
    d = ImageDraw.Draw(im)
    for _ in range(3):
        x, y = rnd.randint(8, 22), rnd.randint(8, 22)
        d.line([x, y, x + rnd.randint(-5, 5), y + rnd.randint(2, 5)], fill=(20, 16, 14), width=2)
    return im


def stakes(broken=False, seed=0):
    """1x2 palisade section: three sharpened logs (broken: snapped and leaning)."""
    im, d = new(16, 32)
    rnd = random.Random(seed)
    d.ellipse([0, 27, 16, 31], fill=(0, 0, 0, 60))
    for x in (1, 6, 11):
        top = rnd.randint(12, 20) if broken else rnd.randint(2, 6)
        lean = rnd.choice((-1, 0, 1)) if broken else 0
        d.polygon([(x, 30), (x + lean, top + 3), (x + 2 + lean, top), (x + 4 + lean, top + 3), (x + 4, 30)], fill=OL)
        d.polygon([(x + 1, 29), (x + 1 + lean, top + 3), (x + 2 + lean, top + 1), (x + 3 + lean, top + 3), (x + 3, 29)],
                  fill=(120, 92, 62))
        d.line([x + 1, top + 4, x + 1, 29], fill=(150, 118, 80))
        if broken:
            d.line([x + lean, top + 1, x + 4 + lean, top + 2], fill=(190, 160, 110))
        d.line([x, 22, x + 4, 22], fill=(70, 58, 42))
    return im


def fallen_stakes():
    im, d = new(32, 16)
    for y, x0 in ((5, 1), (9, 4)):
        d.polygon([(x0, y), (x0 + 22, y - 1), (x0 + 27, y + 2), (x0 + 22, y + 5), (x0, y + 4)], fill=OL)
        d.polygon([(x0 + 1, y + 1), (x0 + 22, y), (x0 + 25, y + 2), (x0 + 22, y + 4), (x0 + 1, y + 3)], fill=(120, 92, 62))
        d.line([x0 + 2, y + 1, x0 + 21, y], fill=(150, 118, 80))
    return im


def siege_engine():
    """4x3 a toppled catapult: frame askew, a post snapped, the throwing arm broken, a wheel off, iron bands."""
    im = Image.new('RGBA', (64, 48), (0, 0, 0, 0))
    lay, dl = _layer((64, 48))
    dl.ellipse([2, 30, 62, 46], fill=(0, 0, 0, 70))
    im.alpha_composite(lay)
    d = ImageDraw.Draw(im)
    for a, b in (((6, 34), (46, 28)), ((10, 42), (50, 36)), ((6, 34), (10, 42)), ((46, 28), (50, 36)),
                 ((24, 31), (28, 39))):
        _pole(d, a, b, 4, WOOD[2], WOOD[1])
    _pole(d, (20, 32), (22, 14), 4, WOOD[2], WOOD[1])
    _pole(d, (36, 30), (38, 20), 4, WOOD[2], WOOD[1])
    d.polygon([(36, 20), (39, 16), (41, 21)], fill=(196, 166, 112))
    _pole(d, (22, 14), (38, 20), 3, WOOD[2])
    _pole(d, (8, 20), (40, 6), 4, WOOD[1], WOOD[0])
    d.polygon([(40, 6), (44, 2), (46, 7)], fill=(196, 166, 112))
    d.ellipse([2, 15, 13, 25], fill=OL); d.ellipse([3, 16, 12, 24], fill=WOOD[2]); d.ellipse([5, 18, 10, 22], fill=(48, 36, 28))
    d.ellipse([42, 22, 60, 40], fill=OL); d.ellipse([44, 24, 58, 38], fill=WOOD[1])
    for k in range(4):
        a = k * math.pi / 4
        d.line([51 + 7 * math.cos(a), 31 + 7 * math.sin(a), 51 - 7 * math.cos(a), 31 - 7 * math.sin(a)], fill=OL)
    d.ellipse([48, 28, 54, 34], fill=OL); d.ellipse([49, 29, 53, 33], fill=WOOD[2])
    d.ellipse([46, 40, 62, 47], fill=OL); d.ellipse([47, 41, 61, 46], fill=WOOD[2]); d.ellipse([52, 42, 56, 45], fill=OL)
    for (x, y) in ((14, 33), (34, 30), (28, 12)):
        d.line([x, y - 3, x + 1, y + 3], fill=(150, 92, 60), width=2)
    d.ellipse([24, 40, 34, 46], outline=(196, 172, 120))
    for (x, y) in ((8, 36), (12, 41), (44, 32), (30, 36), (26, 10)):
        d.point((x, y), fill=MOSS[0]); d.point((x + 1, y), fill=MOSS[1])
    return im


def broken_cart():
    """3x2 a cart tipped on its side, a wheel off, a spilled sack."""
    im = Image.new('RGBA', (48, 32), (0, 0, 0, 0))
    lay, dl = _layer((48, 32))
    dl.ellipse([2, 20, 46, 31], fill=(0, 0, 0, 70))
    im.alpha_composite(lay)
    d = ImageDraw.Draw(im)
    d.polygon([(4, 8), (34, 4), (38, 22), (8, 26)], fill=OL)
    d.polygon([(6, 9), (33, 6), (36, 21), (9, 24)], fill=WOOD[1])
    for k in range(1, 5):
        d.line([6 + k * 6, 8 - k, 9 + k * 6, 24 - k], fill=WOOD[2])
    d.line([6, 9, 33, 6], fill=WOOD[0])
    _pole(d, (34, 14), (46, 10), 2, WOOD[2])
    d.ellipse([2, 18, 16, 30], fill=OL); d.ellipse([4, 20, 14, 28], fill=WOOD[2]); d.ellipse([8, 23, 10, 25], fill=OL)
    d.ellipse([28, 24, 42, 31], fill=OL); d.ellipse([29, 25, 41, 30], fill=WOOD[1]); d.ellipse([34, 26, 36, 29], fill=OL)
    d.ellipse([38, 16, 46, 23], fill=OL); d.ellipse([39, 17, 45, 22], fill=(170, 150, 110))
    return im


def bones():
    im, d = new(16, 16)
    B = (226, 220, 196)
    for (a, b) in (((2, 12), (11, 8)), ((4, 6), (12, 13))):
        d.line([a, b], fill=OL, width=4); d.line([a, b], fill=B, width=2)
        for p in (a, b):
            d.ellipse([p[0] - 2, p[1] - 2, p[0] + 1, p[1] + 1], fill=OL); d.point(p, fill=B)
    return im


def skull(moss=False):
    im, d = new(16, 16)
    d.ellipse([3, 12, 13, 15], fill=(0, 0, 0, 60))
    d.ellipse([3, 3, 13, 12], fill=OL); d.ellipse([4, 4, 12, 11], fill=(226, 220, 196))
    d.rectangle([5, 10, 11, 14], fill=OL); d.rectangle([6, 10, 10, 13], fill=(206, 200, 176))
    d.ellipse([5, 6, 7, 8], fill=OL); d.ellipse([9, 6, 11, 8], fill=OL)
    d.point((8, 9), fill=OL); d.line([7, 12, 7, 13], fill=OL); d.line([9, 12, 9, 13], fill=OL)
    if moss:
        for (x, y) in ((5, 4), (6, 4), (7, 3), (11, 5)):
            d.point((x, y), fill=MOSS[1])
    return im


def armour():
    im, d = new(16, 16)
    d.ellipse([1, 11, 15, 15], fill=(0, 0, 0, 60))
    d.polygon([(3, 3), (13, 3), (14, 9), (11, 14), (5, 14), (2, 9)], fill=OL)
    d.polygon([(4, 4), (12, 4), (13, 9), (10, 13), (6, 13), (3, 9)], fill=(150, 104, 72))
    d.line([8, 4, 8, 13], fill=(110, 74, 52)); d.line([4, 5, 7, 5], fill=(196, 150, 110))
    d.point((10, 8), fill=(196, 120, 70)); d.point((5, 11), fill=(196, 120, 70)); d.point((11, 11), fill=MOSS[1])
    d.line([5, 3, 5, 1], fill=(90, 66, 44)); d.line([11, 3, 11, 1], fill=(90, 66, 44))
    return im


def shield(kind=0):
    im, d = new(16, 16)
    d.ellipse([1, 11, 15, 15], fill=(0, 0, 0, 60))
    if kind == 0:
        d.ellipse([1, 2, 15, 15], fill=OL); d.ellipse([2, 3, 14, 14], fill=(142, 104, 70))
        for x in (5, 8, 11):
            d.line([x, 3, x, 14], fill=(110, 80, 54))
        d.ellipse([6, 6, 10, 10], fill=OL); d.ellipse([7, 7, 9, 9], fill=(150, 110, 80))
        d.line([3, 7, 7, 9], fill=OL); d.line([10, 11, 13, 13], fill=OL)
    else:
        d.polygon([(3, 1), (13, 1), (13, 8), (8, 15), (3, 8)], fill=OL)
        d.polygon([(4, 2), (12, 2), (12, 8), (8, 13), (4, 8)], fill=(120, 110, 128))
        d.line([8, 2, 8, 13], fill=(160, 140, 96)); d.line([4, 6, 12, 6], fill=(160, 140, 96))
        d.point((10, 10), fill=(170, 104, 70)); d.point((5, 4), fill=(170, 104, 70))
    return im


def crow(flip=False, perched=False):
    im, d = new(16, 16)
    if not perched:
        d.ellipse([3, 12, 13, 15], fill=(0, 0, 0, 50))
    by = 6 if perched else 7
    d.ellipse([4, by, 12, by + 6], fill=(22, 22, 30))
    d.ellipse([9, by - 3, 14, by + 2], fill=(22, 22, 30))
    d.polygon([(4, by + 2), (0, by + 5), (4, by + 5)], fill=(22, 22, 30))
    d.polygon([(14, by - 1), (16, by), (14, by + 1)], fill=(150, 140, 110))
    d.point((12, by - 1), fill=(230, 230, 230))
    d.line([6, by + 1, 10, by + 1], fill=(60, 64, 84))
    d.line([7, by + 6, 7, by + 8], fill=(150, 140, 110)); d.line([10, by + 6, 10, by + 8], fill=(150, 140, 110))
    if flip:
        im = im.transpose(Image.FLIP_LEFT_RIGHT)
    return im


def tall_sword(lean=6, rust=0):
    """1x2 a sword driven into the ground at an angle: long bright blade, short guard, grip, pommel, mud lip."""
    im = Image.new('RGBA', (16, 32), (0, 0, 0, 0))
    tip = (8 - lean // 2, 28)
    guard = (8 + lean // 2, 9)
    lay, dl = _layer((16, 32))
    dl.line([tip, (min(15, tip[0] + 7), 31)], fill=(0, 0, 0, 70), width=2)
    im.alpha_composite(lay)
    d = ImageDraw.Draw(im)
    d.line([guard, tip], fill=OL, width=5)
    d.line([guard, tip], fill=(176, 182, 188), width=3)
    d.line([(guard[0] - 1, guard[1]), (tip[0] - 1, tip[1])], fill=(226, 232, 236), width=1)
    for k in (0.35, 0.6, 0.8)[:2 + rust]:
        x = guard[0] + (tip[0] - guard[0]) * k
        y = guard[1] + (tip[1] - guard[1]) * k
        d.point((x, y), fill=(160, 96, 60)); d.point((x + 1, y + 1), fill=(130, 76, 48))
    dx, dy = tip[0] - guard[0], tip[1] - guard[1]
    n = math.hypot(dx, dy)
    px_, py_ = -dy / n, dx / n
    g0 = (guard[0] + px_ * 5, guard[1] + py_ * 5)
    g1 = (guard[0] - px_ * 5, guard[1] - py_ * 5)
    d.line([g0, g1], fill=OL, width=4); d.line([g0, g1], fill=(120, 96, 64), width=2)
    hilt = (guard[0] - dx / n * 6, guard[1] - dy / n * 6)
    d.line([guard, hilt], fill=OL, width=4); d.line([guard, hilt], fill=(96, 66, 44), width=2)
    d.ellipse([hilt[0] - 2, hilt[1] - 2, hilt[0] + 2, hilt[1] + 2], fill=OL)
    d.point(hilt, fill=(200, 170, 100))
    d.ellipse([tip[0] - 4, 26, tip[0] + 4, 30], fill=(84, 66, 50))
    d.line([tip[0] - 3, 27, tip[0] + 3, 27], fill=(110, 88, 66))
    return im


def spear_banner():
    im, d = new(16, 32)
    d.line([12, 30, 4, 2], fill=OL, width=3); d.line([12, 30, 4, 2], fill=WOOD[1], width=1)
    d.polygon([(3, 0), (6, 3), (3, 5), (2, 2)], fill=(170, 176, 182))
    d.polygon([(5, 6), (13, 7), (10, 9), (13, 12), (6, 11)], fill=OL)
    d.polygon([(6, 7), (11, 8), (9, 9), (11, 11), (7, 10)], fill=(150, 84, 76))
    d.ellipse([9, 28, 15, 31], fill=(84, 66, 50))
    return im


def brazier():
    im, d = new(16, 16)
    for (a, b) in (((4, 15), (6, 9)), ((12, 15), (10, 9))):
        d.line([a, b], fill=OL, width=2)
    d.polygon([(2, 7), (14, 7), (11, 11), (5, 11)], fill=OL)
    d.polygon([(3, 8), (13, 8), (10, 10), (6, 10)], fill=IRON[1])
    d.polygon([(5, 7), (8, 1), (11, 7)], fill=(255, 140, 50)); d.polygon([(7, 7), (8, 3), (9, 7)], fill=(255, 230, 140))
    return halo(im, 8, 5, 8, (255, 170, 70), 120)


def stool():
    im, d = new(16, 16)
    d.ellipse([4, 11, 12, 15], fill=(0, 0, 0, 50))
    d.line([6, 10, 5, 14], fill=OL); d.line([10, 10, 11, 14], fill=OL)
    d.ellipse([3, 7, 13, 11], fill=OL); d.ellipse([4, 8, 12, 10], fill=WOOD[0])
    return im


def handcart():
    im, d = new(32, 16)
    d.ellipse([2, 12, 30, 15], fill=(0, 0, 0, 55))
    d.rectangle([4, 4, 24, 11], fill=OL); d.rectangle([5, 5, 23, 10], fill=WOOD[1])
    d.line([5, 5, 23, 5], fill=WOOD[0])
    d.line([24, 9, 31, 12], fill=OL, width=2)
    d.ellipse([14, 8, 22, 15], fill=OL); d.ellipse([15, 9, 21, 14], fill=WOOD[2]); d.point((18, 11), fill=OL)
    d.ellipse([5, 1, 12, 7], fill=OL); d.ellipse([6, 2, 11, 6], fill=(170, 150, 110))
    d.ellipse([12, 2, 19, 7], fill=OL); d.ellipse([13, 3, 18, 6], fill=(150, 126, 84))
    d.line([14, 4, 17, 4], fill=(96, 84, 60))
    return im


def crate_stack():
    im, d = new(16, 16)
    for (x, y) in ((1, 7), (8, 8), (4, 1)):
        outline_rect(d, [x, y, x + 7, y + 7], WOOD[1]); d.line([x + 1, y + 1, x + 6, y + 6], fill=WOOD[2])
        d.line([x + 1, y + 1, x + 6, y + 1], fill=WOOD[0])
    return im
