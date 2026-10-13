"""Newly drawn / composed sprites for the swamp town (Ninja Adventure style: 1px dark outline (20,27,27), flat fills
with one highlight and one shade). Used by swamp_extras.build()."""
import math, random
from PIL import Image, ImageDraw

OL = (20, 27, 27)
WOOD = [(164, 136, 100), (128, 102, 74), (92, 72, 54), (60, 46, 36)]          # weathered swamp timber
STONE = [(164, 166, 152), (134, 138, 126), (102, 106, 98), (70, 74, 70)]       # grey-green stone
IRON = [(118, 128, 142), (74, 82, 96), (46, 52, 64)]
MOSS = [(150, 166, 108), (118, 136, 86), (88, 104, 66)]
GLOW = (255, 214, 120)
SCUM = (150, 162, 112)


def new(w, h):
    im = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    return im, ImageDraw.Draw(im)


def outline_rect(d, box, fill, ol=OL):
    d.rectangle(box, fill=ol)
    d.rectangle([box[0] + 1, box[1] + 1, box[2] - 1, box[3] - 1], fill=fill)


def halo(im, cx, cy, r, col, amax):
    """soft additive-looking glow (alpha falls off with distance), composited under what is already drawn."""
    g = Image.new('RGBA', im.size, (0, 0, 0, 0))
    px = g.load()
    for y in range(max(0, int(cy - r)), min(im.size[1], int(cy + r) + 1)):
        for x in range(max(0, int(cx - r)), min(im.size[0], int(cx + r) + 1)):
            dd = math.hypot(x + 0.5 - cx, y + 0.5 - cy) / r
            if dd < 1:
                px[x, y] = col + (int(amax * (1 - dd) ** 1.6),)
    g.alpha_composite(im)
    return g


# ------------------------------------------------------------------ stilts, ladders, bridges
def stilts(water, kind='mid'):
    """1x1 stilt tile drawn over water: the deck's underside in shadow, two posts with a cross brace, ripples at the
    feet. kind = mid | left | right (end tiles get a corner post at the outer edge)."""
    t = water.copy()
    d = ImageDraw.Draw(t)
    sh = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    ImageDraw.Draw(sh).rectangle([0, 0, 15, 6], fill=(10, 16, 12, 110))
    ImageDraw.Draw(sh).rectangle([0, 7, 15, 9], fill=(10, 16, 12, 55))
    t.alpha_composite(sh)
    d.rectangle([0, 0, 15, 2], fill=WOOD[3]); d.line([0, 2, 15, 2], fill=OL)      # deck beam
    posts = {'mid': (2, 10), 'left': (0, 9), 'right': (4, 13)}[kind]
    d.line([posts[0] + 2, 4, posts[1], 10], fill=WOOD[3])                          # brace
    for px_ in posts:
        d.ellipse([px_ - 2, 11, px_ + 4, 15], outline=SCUM)                         # ripple ring
        d.rectangle([px_, 3, px_ + 2, 13], fill=OL)
        d.line([px_ + 1, 3, px_ + 1, 12], fill=WOOD[1])
        d.point((px_ + 1, 13), fill=WOOD[2])
    return t


def ladder(water):
    """1x1: short plank steps from a stilt-house door down to the boardwalk (walkable)."""
    t = water.copy()
    d = ImageDraw.Draw(t)
    sh = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    ImageDraw.Draw(sh).rectangle([0, 0, 15, 6], fill=(10, 16, 12, 110))
    t.alpha_composite(sh)
    d.rectangle([0, 0, 15, 2], fill=WOOD[3]); d.line([0, 2, 15, 2], fill=OL)
    d.rectangle([2, 2, 13, 15], fill=OL)
    for i, y in enumerate(range(3, 16, 3)):
        d.rectangle([3, y, 12, y + 1], fill=WOOD[0] if i % 2 == 0 else WOOD[1])
        d.line([3, y + 2, 12, y + 2], fill=WOOD[3])
    d.line([2, 3, 2, 15], fill=WOOD[2]); d.line([13, 3, 13, 15], fill=WOOD[2])
    return t


def rope_bridge(water, vertical=False):
    """1x1 rope-bridge span over water: slats with gaps, rope rails, (walkable)."""
    t = water.copy()
    d = ImageDraw.Draw(t)
    sh = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    ImageDraw.Draw(sh).rectangle([2, 3, 14, 15], fill=(10, 16, 12, 70))
    t.alpha_composite(sh)
    for i, x in enumerate(range(0, 16, 4)):
        c = WOOD[0] if i % 2 else WOOD[1]
        d.rectangle([x, 3, x + 2, 12], fill=c)
        d.line([x, 12, x + 2, 12], fill=WOOD[3]); d.line([x + 2, 3, x + 2, 12], fill=WOOD[2])
    for y in (2, 13):
        d.line([0, y, 15, y], fill=(186, 160, 110))
        d.line([0, y + 1, 15, y + 1], fill=(110, 88, 60))
    if vertical:
        t = t.transpose(Image.TRANSPOSE)
    return t


def wade(water, mud):
    """1x1 shallow wading water: the mud bottom shows through in pale patches, a few stepping stones."""
    t = water.copy()
    px = t.load()
    rnd = random.Random(7)
    for y in range(16):
        for x in range(16):
            v = math.sin(x * 0.9 + y * 0.4) + math.cos(y * 0.8 - x * 0.3)
            if v > 0.6:
                r, g, b, a = px[x, y]
                px[x, y] = ((r + mud[0]) // 2, (g + mud[1]) // 2, (b + mud[2]) // 2, 255)
    d = ImageDraw.Draw(t)
    for (x, y) in [(3, 4), (10, 10)]:
        d.ellipse([x, y, x + 3, y + 2], fill=(120, 116, 96)); d.point((x + 1, y), fill=(160, 156, 132))
    d.line([6, 13, 8, 13], fill=SCUM)
    return t


# ------------------------------------------------------------------ boats
def skiff(alt=0):
    """3x1 flat-bottomed punt (bow east): square ends, plank floor, a thwart, the pole laid along it."""
    im, d = new(48, 16)
    d.ellipse([2, 11, 46, 15], fill=(10, 20, 14, 90))
    d.polygon([(2, 3), (40, 3), (46, 6), (46, 10), (40, 13), (2, 13)], fill=OL)
    d.polygon([(3, 4), (40, 4), (45, 7), (45, 9), (40, 12), (3, 12)], fill=WOOD[2])
    d.rectangle([5, 6, 40, 10], fill=WOOD[1])
    for x in range(9, 40, 6):
        d.line([x, 6, x, 10], fill=WOOD[2])
    d.line([3, 4, 40, 4], fill=WOOD[0])
    d.rectangle([22, 5, 24, 11], fill=WOOD[0]); d.line([24, 5, 24, 11], fill=WOOD[3])     # thwart
    d.line([0, 9, 47, 6], fill=(198, 170, 120))                                            # pole
    d.point((47, 6), fill=OL); d.point((0, 9), fill=OL)
    if alt == 1:                                     # a lantern on the bow, a sack in the stern
        d.rectangle([39, 3, 41, 6], fill=OL); d.point((40, 4), fill=GLOW)
        d.ellipse([6, 6, 12, 10], fill=(150, 126, 90)); d.point((8, 7), fill=(176, 150, 110))
        im = halo(im, 40.5, 4.5, 6, (255, 200, 100), 70)
    elif alt == 2:                                   # an eel trap and a coil of rope
        d.rectangle([28, 6, 35, 10], fill=OL); d.rectangle([29, 7, 34, 9], fill=(140, 120, 70))
        d.line([31, 7, 31, 9], fill=OL)
        d.ellipse([8, 6, 13, 10], outline=(190, 160, 110))
    return im


# ------------------------------------------------------------------ vegetation
def mossy(tree, seed=1, density=0.55, maxlen=9):
    """add hanging moss strands under a canopy (tree sprite, transparent bg)."""
    im = tree.copy()
    px = im.load()
    w, h = im.size
    rnd = random.Random(seed)
    d = ImageDraw.Draw(im)
    for x in range(1, w - 1):
        if rnd.random() > density:
            continue
        # lowest canopy pixel (green-ish, not trunk) in this column
        low = None
        for y in range(h - 1, -1, -1):
            r, g, b, a = px[x, y]
            if a and g > r and g > 70 and y < h - 6:
                low = y
                break
        if low is None:
            continue
        n = rnd.randint(3, maxlen)
        col = MOSS[rnd.randint(0, 2)]
        for yy in range(low - 1, min(h, low + n)):
            xx = x + (1 if (yy - low) % 5 == 4 and rnd.random() < 0.5 else 0)
            if xx < w:
                d.point((xx, yy), fill=col)
        if low + n < h:
            d.point((x, low + n), fill=MOSS[2])
    return im


def cypress(nature, recol, big=True, seed=3):
    """swamp cypress: Ninja round tree recoloured dark olive-teal, buttress roots, hanging moss."""
    if big:
        t = nature.crop((4 * 16, 2 * 16, 7 * 16, 5 * 16))
    else:
        t = nature.crop((0, 0, 32, 32))
    t = recol(t)
    w, h = t.size
    d = ImageDraw.Draw(t)
    # buttress roots flaring at the base
    cx = w // 2
    for dx, ln in ((-7, 5), (-4, 3), (4, 3), (7, 5)):
        x0 = cx + (dx // 3)
        d.line([x0, h - 5, cx + dx, h - 1], fill=OL)
        d.line([x0, h - 6, cx + dx, h - 2], fill=(110, 84, 60))
    return mossy(t, seed=seed)


def dead_mossy(tree, seed=5):
    """bare dead tree with grey moss strands."""
    im = tree.copy()
    px = im.load()
    w, h = im.size
    rnd = random.Random(seed)
    d = ImageDraw.Draw(im)
    for x in range(2, w - 2, 2):
        for y in range(h - 8):
            if px[x, y][3] and not px[x, min(h - 1, y + 1)][3] and rnd.random() < 0.45:
                n = rnd.randint(3, 7)
                for yy in range(y + 1, min(h, y + 1 + n)):
                    d.point((x, yy), fill=MOSS[rnd.randint(0, 1)] if yy < y + n else MOSS[2])
                break
    return im


def reeds(kind=0):
    """1x1 clump of reeds / cattails standing in the shallows (transparent)."""
    im, d = new(16, 16)
    rnd = random.Random(11 + kind)
    blades = [(2, 4), (4, 1), (6, 3), (8, 0), (10, 2), (12, 5), (13, 3)]
    if kind == 2:
        blades = blades[1:5]
    for i, (x, top) in enumerate(blades):
        lean = rnd.choice((-1, 0, 1))
        col = [(118, 150, 70), (94, 124, 58), (140, 168, 84)][i % 3]
        d.line([x, 15, x + lean, top + 2], fill=OL)
        d.line([x + 1, 15, x + 1 + lean, top + 3], fill=col)
    if kind in (0, 1):
        for (x, y) in ([(4, 2), (10, 4)] if kind == 0 else [(7, 1)]):
            d.rectangle([x, y, x + 2, y + 5], fill=OL)
            d.line([x + 1, y + 1, x + 1, y + 4], fill=(126, 78, 46))
            d.line([x + 1, y - 2, x + 1, y], fill=(94, 124, 58))
    d.line([1, 15, 14, 15], fill=(60, 80, 50))
    return im


def lilies(flower=False):
    im, d = new(16, 16)
    for (x, y, r) in ((2, 3, 4), (9, 8, 5), (3, 10, 3)):
        d.ellipse([x, y, x + r * 2, y + r * 2 - 2], fill=(56, 92, 48))
        d.ellipse([x, y, x + r * 2 - 1, y + r * 2 - 3], fill=(110, 150, 70))
        d.line([x + r, y + r - 1, x + r * 2 - 1, y + r - 2], fill=(56, 92, 48))
    if flower:
        d.point((13, 9), fill=(255, 220, 236)); d.point((12, 10), fill=(232, 150, 190)); d.point((14, 10), fill=(232, 150, 190))
        d.point((13, 10), fill=(255, 236, 140)); d.point((13, 11), fill=(210, 120, 170))
    return im


def mushrooms(kind=0):
    im, d = new(16, 16)
    caps = {0: [(150, 96, 160), (110, 66, 120)], 1: [(176, 120, 76), (130, 84, 52)], 2: [(120, 220, 196), (70, 160, 150)]}[kind]
    for (x, y, s) in ((3, 8, 3), (9, 6, 4), (7, 11, 2)):
        d.rectangle([x + s - 1, y + s // 2, x + s, y + s + 3], fill=(226, 220, 196))
        d.ellipse([x - 1, y - 1, x + 2 * s, y + s + 1], fill=OL)
        d.ellipse([x, y, x + 2 * s - 1, y + s], fill=caps[0])
        d.line([x + 1, y + s, x + 2 * s - 2, y + s], fill=caps[1])
        d.point((x + 1, y + 1), fill=(240, 236, 220))
    if kind == 2:
        im = halo(im, 9, 9, 9, (120, 255, 210), 70)
    return im


def wisp(col=(150, 255, 210)):
    im, d = new(16, 16)
    im = halo(im, 8, 8, 8, col, 120)
    d = ImageDraw.Draw(im)
    d.ellipse([6, 6, 9, 9], fill=(230, 255, 245)); d.point((7, 7), fill=(255, 255, 255))
    d.line([8, 10, 7, 13], fill=col + (140,))
    return im


def fireflies():
    im, d = new(16, 16)
    for (x, y) in ((3, 4), (11, 2), (8, 10), (13, 12)):
        im = halo(im, x + 0.5, y + 0.5, 3, (255, 240, 120), 110)
    d = ImageDraw.Draw(im)
    for (x, y) in ((3, 4), (11, 2), (8, 10), (13, 12)):
        d.point((x, y), fill=(255, 250, 190))
    return im


def mist(w, h, seed):
    """soft, low fog bank (alpha at most ~60, zero at the sprite edges so it can sit anywhere)."""
    im = Image.new('RGBA', (w * 16, h * 16), (0, 0, 0, 0))
    px = im.load()
    rnd = random.Random(seed)
    blobs = [(rnd.uniform(0.25, 0.75) * w * 16, rnd.uniform(0.35, 0.65) * h * 16, rnd.uniform(0.18, 0.32) * w * 16)
             for _ in range(5)]
    for y in range(h * 16):
        for x in range(w * 16):
            v = 0
            for (bx, by, br) in blobs:
                dd = ((x - bx) / br) ** 2 + ((y - by) / (br * 0.45)) ** 2
                v = max(v, 1 - dd)
            ex = min(x, w * 16 - 1 - x, y, h * 16 - 1 - y) / 6.0
            v = max(0.0, v) * min(1.0, ex)
            if v > 0:
                px[x, y] = (214, 228, 214, int(92 * v ** 0.8))
    return im


# ------------------------------------------------------------------ lights
def lantern_post():
    """1x2: a crooked post with an arm and a hanging lantern, warm glow (top tile above sprites)."""
    im, d = new(16, 32)
    d.rectangle([4, 4, 6, 30], fill=OL); d.line([5, 5, 5, 29], fill=WOOD[1])
    d.rectangle([4, 4, 13, 6], fill=OL); d.line([5, 5, 12, 5], fill=WOOD[1])
    d.line([11, 6, 11, 8], fill=OL)
    outline_rect(d, [9, 8, 13, 14], (255, 200, 90))
    d.point((11, 10), fill=(255, 250, 210)); d.point((11, 11), fill=(255, 236, 160))
    d.line([9, 8, 13, 8], fill=OL)
    d.ellipse([2, 28, 9, 31], fill=(0, 0, 0, 60))
    im = halo(im, 11.5, 11.5, 16, (255, 176, 80), 150)
    return im


def deck_lantern():
    """1x1: a lantern on a stub post at a boardwalk corner."""
    im, d = new(16, 16)
    d.rectangle([7, 7, 9, 15], fill=OL); d.line([8, 8, 8, 15], fill=WOOD[1])
    outline_rect(d, [6, 2, 10, 8], (255, 200, 90))
    d.point((8, 4), fill=(255, 250, 210)); d.line([6, 2, 10, 2], fill=OL)
    return halo(im, 8.5, 5.5, 11, (255, 176, 80), 150)


def candles():
    im, d = new(16, 16)
    for (x, hgt) in ((3, 6), (7, 9), (11, 5)):
        d.rectangle([x, 15 - hgt, x + 2, 14], fill=OL)
        d.rectangle([x + 1, 16 - hgt, x + 1, 14], fill=(236, 226, 196))
        d.point((x + 1, 13 - hgt), fill=(255, 220, 110)); d.point((x + 1, 14 - hgt), fill=(255, 160, 60))
    im2 = im
    for (x, hgt) in ((3, 6), (7, 9), (11, 5)):
        im2 = halo(im2, x + 1.5, 13.5 - hgt, 5, (255, 200, 100), 90)
    return im2


# ------------------------------------------------------------------ graveyard
def tombstone(kind):
    im, d = new(16, 16)
    d.ellipse([2, 12, 14, 15], fill=(0, 0, 0, 55))
    s = STONE
    if kind == 'round':
        d.rectangle([4, 6, 12, 14], fill=OL); d.ellipse([4, 2, 12, 10], fill=OL)
        d.rectangle([5, 7, 11, 13], fill=s[1]); d.ellipse([5, 3, 11, 9], fill=s[1])
        d.line([5, 6, 5, 13], fill=s[0]); d.line([11, 6, 11, 13], fill=s[2])
        d.line([7, 7, 9, 7], fill=s[3]); d.line([7, 9, 9, 9], fill=s[3])
    elif kind == 'cross':
        d.rectangle([6, 1, 10, 14], fill=OL); d.rectangle([3, 4, 13, 8], fill=OL)
        d.rectangle([7, 2, 9, 13], fill=s[1]); d.rectangle([4, 5, 12, 7], fill=s[1])
        d.line([7, 2, 7, 13], fill=s[0]); d.line([4, 5, 12, 5], fill=s[0])
        d.rectangle([5, 12, 11, 14], fill=OL); d.line([6, 13, 10, 13], fill=s[2])
    elif kind == 'old':                     # leaning, cracked, mossy
        d.polygon([(4, 14), (5, 5), (8, 2), (12, 4), (12, 14)], fill=OL)
        d.polygon([(5, 13), (6, 5), (8, 3), (11, 5), (11, 13)], fill=s[2])
        d.line([6, 6, 6, 13], fill=s[1]); d.line([8, 4, 9, 8], fill=s[3]); d.line([9, 8, 8, 11], fill=s[3])
        for (x, y) in ((6, 12), (7, 12), (10, 11), (10, 12), (7, 5)):
            d.point((x, y), fill=MOSS[1])
    elif kind == 'slab':                    # flat slab grave with a small headstone
        d.rectangle([3, 7, 13, 15], fill=OL); d.rectangle([4, 8, 12, 14], fill=s[1])
        d.line([4, 8, 12, 8], fill=s[0]); d.line([4, 14, 12, 14], fill=s[2])
        d.rectangle([5, 2, 11, 8], fill=OL); d.rectangle([6, 3, 10, 7], fill=s[0])
        d.point((8, 11), fill=MOSS[0]); d.point((5, 13), fill=MOSS[1])
    elif kind == 'mound':                   # earth mound with a little wooden cross
        d.ellipse([2, 7, 14, 15], fill=OL); d.ellipse([3, 8, 13, 14], fill=(104, 82, 60))
        d.line([4, 10, 11, 9], fill=(130, 104, 76))
        d.rectangle([7, 1, 9, 10], fill=OL); d.rectangle([5, 3, 11, 5], fill=OL)
        d.line([8, 2, 8, 9], fill=WOOD[1]); d.line([6, 4, 10, 4], fill=WOOD[1])
    elif kind == 'fresh':                   # an open grave with a spade stuck in the heap
        d.rectangle([3, 6, 12, 15], fill=OL); d.rectangle([4, 7, 11, 14], fill=(40, 30, 24))
        d.ellipse([9, 2, 15, 9], fill=(104, 82, 60)); d.ellipse([10, 3, 14, 7], fill=(124, 98, 70))
        d.line([13, 0, 12, 6], fill=OL); d.rectangle([11, 6, 13, 9], fill=IRON[1])
    return im


def iron_fence(kind):
    """1x1 wrought-iron fence pieces: h (railing), v (side run), post (corner post with finial)."""
    im, d = new(16, 16)
    I = IRON
    if kind == 'h':
        d.line([0, 5, 15, 5], fill=OL); d.line([0, 11, 15, 11], fill=OL)
        for x in range(1, 16, 3):
            d.line([x, 2, x, 13], fill=OL)
            d.line([x, 3, x, 12], fill=I[1])
            d.point((x, 1), fill=I[0])
        d.line([0, 6, 15, 6], fill=I[2]); d.line([0, 12, 15, 12], fill=I[2])
        d.line([0, 14, 15, 14], fill=(0, 0, 0, 50))
    elif kind == 'v':
        d.line([7, 0, 7, 15], fill=OL); d.line([9, 0, 9, 15], fill=OL)
        d.line([8, 0, 8, 15], fill=I[1])
        for y in range(1, 16, 4):
            d.rectangle([6, y, 10, y + 1], fill=OL); d.point((8, y), fill=I[0])
    elif kind == 'post':
        d.rectangle([5, 3, 10, 14], fill=OL); d.rectangle([6, 4, 9, 13], fill=I[1])
        d.line([6, 4, 6, 13], fill=I[0])
        d.ellipse([5, 0, 10, 4], fill=OL); d.point((7, 1), fill=I[0])
        d.line([0, 5, 4, 5], fill=OL); d.line([11, 5, 15, 5], fill=OL)
        d.line([0, 11, 4, 11], fill=OL); d.line([11, 11, 15, 11], fill=OL)
    return im


def crypt():
    """5x4 stone mausoleum: steep stone roof (2 rows), pediment with a carved lamp, two columns, iron door at the
    centre of the bottom row, moss, a lantern each side."""
    im, d = new(80, 64)
    s = STONE
    # roof: a stepped stone pitch
    d.polygon([(40, 1), (2, 26), (78, 26)], fill=OL)
    d.polygon([(40, 3), (5, 25), (75, 25)], fill=(96, 92, 108))
    for i, y in enumerate(range(8, 25, 4)):
        hw = int((y - 3) * 35 / 22)
        d.line([40 - hw, y, 40 + hw, y], fill=(70, 66, 82))
    d.line([(40, 3), (5, 25)], fill=(128, 124, 140))
    d.rectangle([36, 0, 44, 3], fill=OL); d.rectangle([37, 0, 43, 2], fill=s[1])
    # body
    outline_rect(d, [4, 26, 75, 63], s[1])
    d.rectangle([5, 27, 74, 30], fill=s[0]); d.line([5, 31, 74, 31], fill=s[3])          # cornice
    for y in (38, 45, 52):
        d.line([5, y, 74, y], fill=s[2])
    for i, y in enumerate((32, 39, 46, 53)):
        for x in range(10 + (i % 2) * 7, 74, 14):
            d.line([x, y, x, y + 5], fill=s[2])
    # pediment emblem: carved lamp
    d.polygon([(40, 10), (34, 22), (46, 22)], fill=s[2])
    d.rectangle([38, 14, 42, 20], fill=OL); d.point((40, 17), fill=GLOW)
    # columns
    for x in (20, 54):
        d.rectangle([x, 31, x + 6, 63], fill=OL)
        d.rectangle([x + 1, 32, x + 5, 62], fill=s[0])
        d.line([x + 4, 32, x + 4, 62], fill=s[1]); d.line([x + 5, 32, x + 5, 62], fill=s[2])
        d.rectangle([x - 1, 31, x + 7, 33], fill=s[1]); d.rectangle([x - 1, 60, x + 7, 63], fill=s[2])
    # iron door, arched
    d.rectangle([31, 44, 49, 63], fill=OL); d.ellipse([31, 36, 49, 54], fill=OL)
    d.rectangle([33, 46, 47, 63], fill=IRON[2]); d.ellipse([33, 38, 47, 52], fill=IRON[2])
    d.line([40, 39, 40, 63], fill=OL)
    for y in (46, 52, 58):
        d.line([34, y, 46, y], fill=IRON[1])
    d.point((38, 54), fill=(200, 180, 120)); d.point((42, 54), fill=(200, 180, 120))
    # moss
    rnd = random.Random(4)
    for _ in range(70):
        x, y = rnd.randint(5, 74), rnd.choice([rnd.randint(27, 33), rnd.randint(54, 62), rnd.randint(20, 26)])
        if im.getpixel((x, y))[:3] not in (OL, IRON[2]) and im.getpixel((x, y))[3]:
            d.point((x, y), fill=MOSS[rnd.randint(0, 2)])
    # lanterns on the wall
    for x in (11, 66):
        outline_rect(d, [x, 40, x + 4, 46], (255, 200, 90)); d.point((x + 2, 42), fill=(255, 250, 210))
    im = halo(im, 13.5, 43.5, 9, (255, 196, 96), 80)
    im = halo(im, 68.5, 43.5, 9, (255, 196, 96), 80)
    return im


def bog_chapel():
    """3x5 shrine chapel: grey stone, dark plum roof, bell cote, a warm round window, arched door."""
    im, d = new(48, 80)
    s = STONE
    ROOF = [(130, 96, 140), (96, 66, 108), (66, 44, 76)]
    d.polygon([(24, 0), (16, 9), (32, 9)], fill=OL)
    d.polygon([(24, 2), (18, 8), (30, 8)], fill=ROOF[1])
    outline_rect(d, [17, 8, 31, 24], s[0])
    d.rectangle([20, 11, 28, 20], fill=OL)
    d.ellipse([21, 12, 27, 19], fill=(196, 160, 80)); d.point((24, 19), fill=(130, 100, 40))
    d.line([29, 9, 29, 23], fill=s[1])
    outline_rect(d, [3, 38, 44, 79], s[1])
    d.rectangle([38, 39, 43, 78], fill=s[2])
    for y in (46, 54, 62, 70):
        d.line([4, y, 37, y], fill=s[2])
    d.polygon([(24, 18), (0, 44), (8, 44), (24, 28), (40, 44), (47, 44)], fill=OL)
    d.polygon([(24, 20), (2, 43), (8, 43), (24, 26), (40, 43), (45, 43)], fill=ROOF[1])
    d.line([(24, 21), (4, 42)], fill=ROOF[0])
    d.polygon([(24, 27), (9, 43), (39, 43)], fill=s[0])
    d.line([(24, 28), (38, 43)], fill=s[1])
    # round window glowing warm
    d.ellipse([18, 31, 30, 43], fill=OL); d.ellipse([19, 32, 29, 42], fill=(255, 206, 110))
    d.line([24, 32, 24, 42], fill=OL); d.line([19, 37, 29, 37], fill=OL)
    for wx in (10, 34):
        d.rectangle([wx - 3, 50, wx + 3, 60], fill=OL); d.ellipse([wx - 3, 47, wx + 3, 53], fill=OL)
        d.rectangle([wx - 2, 51, wx + 2, 59], fill=(255, 206, 110)); d.ellipse([wx - 2, 48, wx + 2, 52], fill=(255, 206, 110))
    d.rectangle([17, 64, 31, 79], fill=OL); d.ellipse([17, 58, 31, 72], fill=OL)
    d.rectangle([19, 66, 29, 79], fill=WOOD[2]); d.ellipse([19, 60, 29, 70], fill=WOOD[2])
    d.line([24, 61, 24, 79], fill=WOOD[3])
    rnd = random.Random(8)
    for _ in range(40):
        x, y = rnd.randint(4, 43), rnd.randint(70, 78)
        if im.getpixel((x, y))[:3] in (s[1], s[2]):
            d.point((x, y), fill=MOSS[rnd.randint(0, 2)])
    im = halo(im, 24, 37, 12, (255, 196, 96), 60)
    return im


# ------------------------------------------------------------------ buildings
def smokehouse():
    """3x3 smokehouse: low dark-plank shed, roof vent with smoke, small door, a fish board."""
    im, d = new(48, 48)
    # smoke puffs above the vent (top row)
    for (x, y, r, a) in ((28, 6, 5, 120), (32, 2, 4, 90), (24, 10, 4, 140)):
        d.ellipse([x - r, y - r, x + r, y + r], fill=(196, 196, 186, a))
    d.rectangle([22, 10, 30, 18], fill=OL); d.rectangle([23, 11, 29, 17], fill=WOOD[3])
    d.polygon([(0, 30), (8, 14), (40, 14), (47, 30)], fill=OL)
    d.polygon([(2, 29), (9, 15), (39, 15), (45, 29)], fill=(84, 70, 62))
    for x in range(6, 44, 5):
        d.line([x, 16, x - 3, 28], fill=(64, 52, 46))
    d.line([9, 15, 39, 15], fill=(110, 94, 84))
    outline_rect(d, [3, 29, 44, 47], WOOD[2])
    for x in range(6, 44, 4):
        d.line([x, 30, x, 46], fill=WOOD[3])
    d.rectangle([18, 34, 29, 47], fill=OL); d.rectangle([19, 35, 28, 47], fill=(48, 34, 26))
    d.line([23, 35, 23, 47], fill=OL)
    # smoke-blackened eaves, a fish board
    d.rectangle([4, 30, 43, 31], fill=(50, 40, 34))
    outline_rect(d, [32, 34, 41, 40], (196, 170, 120))
    d.line([34, 37, 39, 37], fill=(80, 70, 60)); d.point((40, 36), fill=(80, 70, 60)); d.point((40, 38), fill=(80, 70, 60))
    return im


def eel_rack():
    """2x2 drying rack: two posts, two bars, eels hung to dry."""
    im, d = new(32, 32)
    for x in (2, 28):
        d.rectangle([x, 4, x + 2, 30], fill=OL); d.line([x + 1, 5, x + 1, 29], fill=WOOD[1])
    for y in (5, 17):
        d.line([2, y, 30, y], fill=OL); d.line([3, y + 1, 29, y + 1], fill=WOOD[2])
    rnd = random.Random(2)
    for y0 in (6, 18):
        for x in range(6, 27, 4):
            L = rnd.randint(7, 10)
            pts = [(x + (1 if (i // 2) % 2 else 0), y0 + 1 + i) for i in range(L)]
            for (px_, py) in pts:
                d.point((px_ - 1, py), fill=OL); d.point((px_ + 1, py), fill=OL)
                d.point((px_, py), fill=(96, 84, 70))
            d.point(pts[-1], fill=OL)
    d.ellipse([3, 28, 31, 31], fill=(0, 0, 0, 50))
    return im


def eel_basket():
    im, d = new(16, 16)
    d.ellipse([2, 6, 14, 15], fill=OL); d.ellipse([3, 7, 13, 14], fill=(170, 140, 90))
    for x in range(4, 13, 3):
        d.line([x, 8, x, 13], fill=(130, 100, 60))
    d.ellipse([4, 5, 12, 9], fill=OL); d.ellipse([5, 6, 11, 8], fill=(60, 52, 44))
    for (x, y) in ((6, 3), (9, 2)):
        d.line([x, y, x + 1, y + 4], fill=OL); d.point((x, y + 1), fill=(110, 98, 80))
    return im


def crayfish_trap():
    im, d = new(16, 16)
    d.ellipse([1, 4, 14, 14], fill=OL); d.ellipse([2, 5, 13, 13], fill=(150, 126, 84))
    for x in range(4, 13, 3):
        d.line([x, 5, x, 13], fill=(100, 80, 54))
    d.line([2, 9, 13, 9], fill=(100, 80, 54))
    d.ellipse([11, 7, 15, 11], fill=OL); d.ellipse([12, 8, 14, 10], fill=(40, 34, 30))
    return im


def cistern():
    """2x2 rain cistern: a big hooped vat on a stone footing, a funnel lid and a spout with a bucket."""
    im, d = new(32, 32)
    d.ellipse([1, 25, 31, 31], fill=(0, 0, 0, 60))
    outline_rect(d, [2, 22, 29, 29], STONE[1]); d.line([3, 23, 28, 23], fill=STONE[0])
    d.rectangle([4, 6, 27, 25], fill=OL)
    d.rectangle([5, 7, 26, 24], fill=WOOD[1])
    for x in range(8, 26, 4):
        d.line([x, 8, x, 24], fill=WOOD[2])
    d.line([6, 8, 6, 23], fill=WOOD[0])
    for y in (10, 19):
        d.rectangle([4, y, 27, y + 1], fill=IRON[2]); d.line([5, y, 26, y], fill=IRON[1])
    d.ellipse([3, 2, 28, 10], fill=OL); d.ellipse([4, 3, 27, 9], fill=WOOD[2])
    d.ellipse([10, 4, 21, 8], fill=(60, 80, 60)); d.point((14, 5), fill=(120, 150, 110))
    d.rectangle([24, 18, 30, 20], fill=OL); d.point((29, 21), fill=(140, 180, 190))
    d.rectangle([26, 23, 31, 29], fill=OL); d.rectangle([27, 24, 30, 28], fill=WOOD[1])
    return im


def apothecary_sign(pimg):
    """1x1 hanging sign with a potion bottle."""
    im, d = new(16, 16)
    d.line([1, 1, 14, 1], fill=OL); d.line([4, 1, 4, 4], fill=OL); d.line([11, 1, 11, 4], fill=OL)
    outline_rect(d, [2, 4, 13, 15], (88, 120, 96))
    p = pimg('Items/Potion/LifePot.png')
    im.alpha_composite(p.crop((0, 0, 9, 11)).resize((8, 10), Image.NEAREST) if False else p.crop((0, 0, 9, 11)), (3, 4))
    return im


def herbs():
    """1x1 bunches of drying herbs hung from an eave (transparent; drawn on AboveSprites over a wall)."""
    im, d = new(16, 16)
    d.line([0, 1, 15, 1], fill=(110, 88, 60))
    for i, x in enumerate((2, 7, 12)):
        col = [(120, 160, 80), (150, 120, 170), (170, 160, 80)][i]
        d.line([x, 1, x, 4], fill=(90, 70, 50))
        d.polygon([(x - 2, 4), (x + 2, 4), (x + 1, 11), (x - 1, 11)], fill=OL)
        d.polygon([(x - 1, 5), (x + 1, 5), (x, 10)], fill=col)
    return im


def cauldron():
    im, d = new(16, 16)
    d.ellipse([1, 12, 15, 15], fill=(0, 0, 0, 60))
    for x in (3, 12):
        d.line([x, 10, x - 1, 15], fill=OL)
    d.ellipse([1, 4, 14, 14], fill=OL); d.ellipse([2, 5, 13, 13], fill=(56, 60, 66))
    d.line([3, 7, 3, 11], fill=(90, 96, 104))
    d.ellipse([2, 3, 13, 7], fill=OL); d.ellipse([3, 4, 12, 6], fill=(120, 220, 120))
    d.point((6, 4), fill=(200, 255, 190)); d.point((9, 5), fill=(80, 170, 90))
    d.rectangle([5, 14, 10, 15], fill=(255, 140, 60)); d.point((7, 13), fill=(255, 210, 110))
    im = halo(im, 7.5, 5, 7, (140, 255, 150), 70)
    return im


def bottles():
    im, d = new(16, 16)
    for (x, c, h) in ((2, (120, 200, 120), 8), (7, (170, 110, 190), 10), (11, (110, 170, 210), 7)):
        d.rectangle([x, 15 - h, x + 3, 15], fill=OL); d.rectangle([x + 1, 16 - h, x + 2, 14], fill=c)
        d.rectangle([x + 1, 13 - h, x + 2, 15 - h], fill=OL)
        d.point((x + 1, 17 - h), fill=(240, 250, 240))
    return im


def skull_post():
    """1x2 the witch's warding post: a stick with an animal skull and charms (not gory)."""
    im, d = new(16, 32)
    d.rectangle([7, 8, 9, 31], fill=OL); d.line([8, 9, 8, 30], fill=WOOD[1])
    d.ellipse([3, 1, 13, 10], fill=OL); d.ellipse([4, 2, 12, 9], fill=(226, 220, 200))
    d.point((6, 5), fill=OL); d.point((10, 5), fill=OL); d.line([7, 8, 9, 8], fill=(170, 160, 140))
    d.line([2, 2, 4, 0], fill=(226, 220, 200)); d.line([14, 2, 12, 0], fill=(226, 220, 200))
    for (x, y, c) in ((5, 14, (170, 110, 190)), (11, 16, (120, 200, 160)), (6, 19, (220, 190, 90))):
        d.line([8, y - 3, x, y], fill=(150, 130, 100)); d.ellipse([x - 1, y, x + 1, y + 2], fill=c)
    return im


# ------------------------------------------------------------------ old battlefield
def rusted_sword(flip=False):
    im, d = new(16, 16)
    d.ellipse([3, 12, 13, 15], fill=(0, 0, 0, 50))
    d.line([5, 2, 9, 13], fill=OL); d.line([6, 2, 10, 13], fill=OL); d.line([4, 2, 8, 13], fill=OL)
    d.line([5, 3, 9, 13], fill=(160, 104, 70)); d.point((6, 6), fill=(196, 140, 96)); d.point((7, 9), fill=(120, 74, 50))
    d.line([2, 4, 8, 1], fill=OL); d.line([3, 4, 7, 2], fill=(110, 90, 60))
    d.line([4, 0, 5, 3], fill=OL)
    d.rectangle([4, 12, 11, 14], fill=(96, 78, 56))
    if flip:
        im = im.transpose(Image.FLIP_LEFT_RIGHT)
    return im


def helmet_shield():
    im, d = new(16, 16)
    d.ellipse([1, 4, 11, 15], fill=OL); d.ellipse([2, 5, 10, 14], fill=(150, 96, 64))
    d.line([6, 5, 6, 14], fill=(110, 70, 50)); d.line([2, 9, 10, 9], fill=(110, 70, 50))
    d.point((4, 7), fill=MOSS[0]); d.point((8, 12), fill=MOSS[1])
    d.ellipse([8, 8, 15, 14], fill=OL); d.ellipse([9, 9, 14, 13], fill=(130, 120, 110))
    d.rectangle([9, 12, 14, 14], fill=OL); d.line([10, 11, 13, 11], fill=(60, 56, 54))
    return im


def torn_banner(src):
    """1x2 faded, torn war banner from a Ninja banner: desaturated, ragged bottom, pole leaning."""
    im = src.copy()
    px = im.load()
    for y in range(im.size[1]):
        for x in range(im.size[0]):
            r, g, b, a = px[x, y]
            if a:
                m = (r + g + b) // 3
                px[x, y] = ((r + m * 2) // 3 * 85 // 100, (g + m * 2) // 3 * 85 // 100, (b + m * 2) // 3 * 82 // 100, a)
    d = ImageDraw.Draw(im)
    rnd = random.Random(3)
    for x in range(im.size[0]):
        cut = rnd.randint(18, 27)
        for y in range(cut, 28):
            if px[x, y][3] and px[x, y][:3] != OL:
                px[x, y] = (0, 0, 0, 0)
    for (x, y) in ((5, 12), (9, 15), (6, 18)):
        d.rectangle([x, y, x + 1, y + 1], fill=(0, 0, 0, 0))
    return im


# ------------------------------------------------------------------ signs
def swamp_sign(text, w_tiles, paint_text, board=(108, 92, 70), edge=(46, 36, 28), ink=(236, 222, 180),
               line=(84, 70, 54)):
    W = w_tiles * 16
    im, d = new(W, 16)
    d.rectangle([1, 3, W - 2, 12], fill=edge)
    d.rectangle([2, 4, W - 3, 11], fill=board)
    d.line([3, 11, W - 4, 11], fill=line)
    paint_text(im, text, W // 2, 5, ink)
    return im


def frog(pimg):
    sh = pimg('Actor/Animal/Frog/SpriteSheet.png')
    return sh.crop((0, 0, 16, 16))


# ------------------------------------------------------------------ bald cypress (drawn)
LEAF = [(98, 128, 76), (70, 100, 62), (50, 74, 50), (36, 54, 40)]
BARK = [(122, 100, 80), (92, 74, 60), (64, 52, 44)]
MOSSH = [(176, 184, 146), (146, 158, 120), (114, 128, 96)]


def _canopy(d, clumps):
    for (x, y, rx, ry) in clumps:
        d.ellipse([x - rx - 1, y - ry - 1, x + rx + 1, y + ry + 1], fill=OL)
    for (x, y, rx, ry) in clumps:
        d.ellipse([x - rx, y - ry, x + rx, y + ry], fill=LEAF[2])
    for (x, y, rx, ry) in clumps:
        d.ellipse([x - rx, y - ry, x + rx - 2, y + ry - 3], fill=LEAF[1])
        d.ellipse([x - rx + 2, y - ry + 1, x + rx // 3, y - ry // 3], fill=LEAF[0])


def _trunk(d, cx, top, bot, wtop, wbot):
    for y in range(top, bot + 1):
        f = (y - top) / max(1, bot - top)
        hw = wtop + (wbot - wtop) * f ** 2.2
        x0, x1 = int(cx - hw), int(cx + hw)
        d.line([x0, y, x1, y], fill=OL)
        d.line([x0 + 1, y, x1 - 1, y], fill=BARK[1])
        d.line([x0 + 1, y, x0 + 2, y], fill=BARK[0])
        d.line([x1 - 2, y, x1 - 1, y], fill=BARK[2])
    # cypress knees beside the trunk
    for kx in (cx - wbot - 4, cx + wbot + 3):
        d.polygon([(kx - 2, bot), (kx, bot - 5), (kx + 2, bot)], fill=OL)
        d.line([kx, bot - 3, kx, bot - 1], fill=BARK[0])


def _moss(im, seed, ymin, ymax):
    """hang strands of Spanish moss from the underside of the canopy."""
    px = im.load()
    w, h = im.size
    rnd = random.Random(seed)
    d = ImageDraw.Draw(im)
    for x in range(2, w - 2):
        if rnd.random() > 0.42:
            continue
        low = None
        for y in range(min(h - 1, ymax), ymin, -1):
            p = px[x, y]
            if p[3] and p[:3] in (LEAF[2], LEAF[1], LEAF[0]):
                low = y
                break
        if low is None:
            continue
        n = rnd.randint(5, 13)
        for i in range(n):
            yy = low + i
            if yy >= h:
                break
            xx = x + (1 if (i // 4) % 2 and rnd.random() < 0.6 else 0)
            col = MOSSH[0] if i < 2 else MOSSH[1] if i < n - 2 else MOSSH[2]
            d.point((xx, yy), fill=col)


def bald_cypress(seed=3):
    """3x4 swamp cypress: layered dark canopy, flared trunk with knees, Spanish moss hanging."""
    im, d = new(48, 64)
    d.ellipse([6, 56, 42, 63], fill=(10, 20, 14, 80))
    _trunk(d, 24, 30, 61, 3, 9)
    rnd = random.Random(seed)
    clumps = [(24, 9, 10, 8), (13, 18, 9, 7), (35, 17, 9, 7), (24, 24, 13, 8), (11, 31, 8, 6), (37, 32, 8, 6)]
    clumps = [(x + rnd.randint(-2, 2), y + rnd.randint(-1, 1), rx, ry) for (x, y, rx, ry) in clumps]
    _canopy(d, clumps)
    # a few branches showing between the lower clumps
    d.line([24, 34, 15, 37], fill=OL); d.line([24, 35, 16, 38], fill=BARK[1])
    d.line([24, 34, 33, 37], fill=OL); d.line([24, 35, 32, 38], fill=BARK[1])
    _moss(im, seed, 4, 44)
    return im


def small_cypress(seed=5):
    """2x3 young cypress with moss."""
    im, d = new(32, 48)
    d.ellipse([4, 41, 28, 47], fill=(10, 20, 14, 80))
    _trunk(d, 16, 24, 45, 2, 6)
    _canopy(d, [(16, 9, 9, 8), (9, 19, 7, 6), (23, 19, 7, 6), (16, 24, 8, 5)])
    _moss(im, seed, 3, 34)
    return im


def war_banner(cloth, seed):
    """1x2 an old war banner: leaning pole with a crossbar, faded cloth torn at the bottom, a hole or two."""
    im, d = new(16, 32)
    rnd = random.Random(seed)
    d.ellipse([3, 28, 13, 31], fill=(0, 0, 0, 60))
    d.line([6, 1, 8, 30], fill=OL); d.line([7, 1, 9, 30], fill=OL); d.line([7, 2, 8, 29], fill=WOOD[1])
    d.line([1, 3, 14, 2], fill=OL); d.line([2, 3, 13, 2], fill=WOOD[2])
    c0, c1 = cloth
    for x in range(2, 14):
        bot = 18 + rnd.randint(-3, 3) + (2 if x % 3 == 0 else 0)
        d.line([x, 4, x, bot], fill=c0 if 3 < x < 13 else c1)
        d.point((x, bot + 1), fill=OL)
    d.line([2, 4, 2, 16], fill=OL); d.line([13, 4, 13, 15], fill=OL)
    d.line([3, 9, 12, 9], fill=c1); d.ellipse([6, 10, 9, 13], fill=(196, 176, 120))
    for (x, y) in ((4, 14), (10, 6)):
        d.rectangle([x, y, x + 1, y + 1], fill=(0, 0, 0, 0))
    return im


def sword_upright(seed=0):
    """1x1 a rusted sword stuck point-down in the mud."""
    im, d = new(16, 16)
    d.ellipse([4, 12, 12, 15], fill=(0, 0, 0, 60))
    lean = 1 if seed % 2 else -1
    d.polygon([(7, 4), (9, 4), (9 + lean, 13), (7 + lean, 13)], fill=OL)
    d.line([8, 5, 8 + lean, 12], fill=(170, 112, 76)); d.point((8, 8), fill=(206, 150, 104)); d.point((8 + lean, 11), fill=(120, 74, 50))
    d.rectangle([4, 3, 12, 5], fill=OL); d.line([5, 4, 11, 4], fill=(120, 96, 64))
    d.rectangle([7, 0, 9, 3], fill=OL); d.point((8, 1), fill=(110, 80, 56))
    d.rectangle([6, 12, 11, 14], fill=(96, 78, 56))
    return im


def eel_rack2():
    """2x2 drying rack with plump smoked eels."""
    im, d = new(32, 32)
    d.ellipse([3, 27, 31, 31], fill=(0, 0, 0, 55))
    for x in (2, 28):
        d.rectangle([x, 3, x + 2, 30], fill=OL); d.line([x + 1, 4, x + 1, 29], fill=WOOD[1])
    for y in (4, 16):
        d.line([2, y, 30, y], fill=OL); d.line([3, y + 1, 29, y + 1], fill=WOOD[2])
    for y0 in (6, 18):
        for i, x in enumerate(range(6, 27, 5)):
            L = 8 + (i * 3) % 3
            d.line([x + 1, y0 - 1, x + 1, y0], fill=OL)
            for k in range(L):
                xx = x + (1 if (k // 3) % 2 else 0)
                d.line([xx - 1, y0 + k, xx + 2, y0 + k], fill=OL)
                d.line([xx, y0 + k, xx + 1, y0 + k], fill=(118, 96, 66) if k % 4 else (150, 124, 84))
            d.point((x + 1, y0 + L), fill=OL)
    return im
