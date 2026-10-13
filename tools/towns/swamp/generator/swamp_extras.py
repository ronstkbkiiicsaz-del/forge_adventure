"""Generated tilesets for the swamp town (black biome).

ninja_SwampFloor  : ninja_TilesetFloor with the grass recoloured to dark swamp olive and the dirt autotile to mud.
ninja_SwampWater  : ninja_TilesetWater, the sand-shore block recoloured to murky green-brown water with mud banks
                    and a pale scum line (the shore autotile), grass block likewise.
ninja_SwampExtras : composites made from Ninja Adventure art (CC0) plus newly drawn sprites (see draw_swamp.py):
                    deep-murk autotile, wading shallows, boardwalk planks over murk and mud, stilts and ladders,
                    rope bridges, roof recolours (moss/bark/plum/pine, patched), tavern, apothecary, trading post,
                    smokehouse, bog chapel, crypt, cistern, skiffs, cypress trees with hanging moss, reeds, lilies,
                    mushrooms, wisps, fireflies, mist banks, lanterns, iron fence, tombstones, battlefield remnants.
SPR maps a sprite name -> (col, row, w, h) inside ninja_SwampExtras.
"""
import colorsys, random
from PIL import Image, ImageDraw

SRC = 'A:/forge_wave3/forge-gui/res/adventure/common/maps/tileset/ninja/'
PACK = 'A:/GameAssets/NinjaAdventure/pack/Ninja Adventure - Asset Pack/'

G1, G2 = (173, 188, 58), (168, 161, 41)
B1, B2 = (120, 136, 70), (104, 120, 62)         # swamp olive grass
W1, W2 = (113, 221, 238), (121, 184, 206)
BW1, BW2 = (60, 84, 74), (52, 74, 66)           # murky teal-brown water
SAND = [(118, 96, 70), (100, 80, 60), (136, 114, 84)]    # mud banks
DIRT1, DIRT2 = (211, 134, 95), (189, 121, 89)
DEEP1, DEEP2, DEEP3 = (38, 56, 50), (34, 50, 46), (48, 68, 60)
SCUM = (124, 142, 104)
SHORE = (70, 56, 44)
STONE = [(176, 170, 160), (150, 144, 136), (112, 106, 102), (78, 74, 74)]
OL = (20, 27, 27)


def timg(name):
    return Image.open(SRC + name + '.png').convert('RGBA')


def pimg(path):
    return Image.open(PACK + path).convert('RGBA')


def swap(im, pairs):
    im = im.copy()
    px = im.load()
    m = {a: b for a, b in pairs}
    for y in range(im.size[1]):
        for x in range(im.size[0]):
            p = px[x, y]
            k = p[:3]
            if p[3] and k in m:
                px[x, y] = m[k] + (p[3],)
    return im


def hue_shift(im, hue, satmul=1.0, valmul=1.0, minsat=0.25, warm_only=True):
    im = im.copy()
    px = im.load()
    for y in range(im.size[1]):
        for x in range(im.size[0]):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            if s < minsat or (warm_only and not (h < 0.13 or h > 0.93)):
                continue
            r2, g2, b2 = colorsys.hsv_to_rgb(hue, min(1, s * satmul), min(1, v * valmul))
            px[x, y] = (int(r2 * 255), int(g2 * 255), int(b2 * 255), a)
    return im


def whiten(im, keep_dark=0.30, tint=(0.11, 0.10)):
    """whitewash: bright warm pixels become off-white/pale stone, dark outlines kept."""
    im = im.copy()
    px = im.load()
    for y in range(im.size[1]):
        for x in range(im.size[0]):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            if v < keep_dark:
                continue
            r2, g2, b2 = colorsys.hsv_to_rgb(tint[0], tint[1] * (1.4 - v), min(1, 0.55 + v * 0.47))
            px[x, y] = (int(r2 * 255), int(g2 * 255), int(b2 * 255), a)
    return im


def stretch_h(im, width, l, r):
    w, h = im.size
    out = Image.new('RGBA', (width, h))
    out.paste(im.crop((0, 0, l, h)), (0, 0))
    band = im.crop((l, 0, w - r, h))
    assert band.size[0] > 0
    x = l
    while x < width - r:
        out.paste(band, (x, 0))
        x += band.size[0]
    out.paste(im.crop((w - r, 0, w, h)), (width - r, 0))
    return out


def stretch_v(im, height, t, b):
    w, h = im.size
    out = Image.new('RGBA', (w, height))
    out.paste(im.crop((0, 0, w, t)), (0, 0))
    band = im.crop((0, t, w, h - b))
    assert band.size[1] > 0
    y = t
    while y < height - b:
        out.paste(band, (0, y))
        y += band.size[1]
    out.paste(im.crop((0, h - b, w, h)), (0, height - b))
    return out


FONT = {  # 3x5 pixel font
    'A': ['010', '101', '111', '101', '101'], 'C': ['011', '100', '100', '100', '011'],
    'E': ['111', '100', '110', '100', '111'], 'G': ['011', '100', '101', '101', '011'],
    'H': ['101', '101', '111', '101', '101'], 'I': ['111', '010', '010', '010', '111'],
    'L': ['100', '100', '100', '100', '111'], 'M': ['10001', '11011', '10101', '10001', '10001'],
    'N': ['1001', '1101', '1011', '1001', '1001'], 'O': ['010', '101', '101', '101', '010'],
    'R': ['110', '101', '110', '101', '101'], 'S': ['011', '100', '010', '001', '110'],
    'T': ['111', '010', '010', '010', '010'], 'Y': ['101', '101', '010', '010', '010'],
    'B': ['110', '101', '110', '101', '110'], 'K': ['101', '101', '110', '101', '101'],
    'F': ['111', '100', '110', '100', '100'], 'W': ['10001', '10001', '10101', '10101', '01010'],
    'U': ['101', '101', '101', '101', '111'], 'P': ['110', '101', '110', '100', '100'],
    'D': ['110', '101', '101', '101', '110'], 'V': ['101', '101', '101', '101', '010'],
    ' ': ['00', '00', '00', '00', '00'],
}


def paint_text(im, text, cx, cy, col):
    d = ImageDraw.Draw(im)
    w = sum(len(FONT[ch][0]) + 1 for ch in text) - 1
    x = cx - w // 2
    for ch in text:
        for yy, row in enumerate(FONT[ch]):
            for xx, bit in enumerate(row):
                if bit == '1':
                    d.point((x + xx, cy + yy), fill=col)
        x += len(FONT[ch][0]) + 1


def sign_board(w_tiles, text, board=(226, 170, 110), edge=(96, 58, 32), ink=(60, 34, 22), line=(196, 140, 90)):
    W = w_tiles * 16
    im = Image.new('RGBA', (W, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rectangle([1, 3, W - 2, 12], fill=edge)
    d.rectangle([2, 4, W - 3, 11], fill=board)
    d.line([3, 11, W - 4, 11], fill=line)
    paint_text(im, text, W // 2, 5, ink)
    return im


def blue_sign(w_tiles, text):
    return sign_board(w_tiles, text, board=(70, 118, 170), edge=(30, 46, 72), ink=(240, 236, 220), line=(52, 92, 140))


class Sheet:
    """shelf-free first-fit packer on a tile grid."""
    def __init__(self, cols, rows):
        self.cols, self.rows = cols, rows
        self.im = Image.new('RGBA', (cols * 16, rows * 16), (0, 0, 0, 0))
        self.used = [[False] * cols for _ in range(rows)]
        self.spr = {}

    def put(self, name, im, c=None, r=None):
        w, h = (im.size[0] + 15) // 16, (im.size[1] + 15) // 16
        if c is None:
            c, r = self.find(w, h)
        for y in range(r, r + h):
            for x in range(c, c + w):
                assert not self.used[y][x], (name, x, y)
                self.used[y][x] = True
        self.im.alpha_composite(im, (c * 16, r * 16))
        self.spr[name] = (c, r, w, h)
        return c, r

    def find(self, w, h):
        for r in range(self.rows - h + 1):
            for c in range(self.cols - w + 1):
                if all(not self.used[y][x] for y in range(r, r + h) for x in range(c, c + w)):
                    return c, r
        raise Exception('sheet full')


def deep_tiles(shallow_tile):
    """16 transition tiles shallow->deep for a corner signature (tl, tr, bl, br), deep = 1."""
    out = {}
    rnd = random.Random(5)
    noise = [[rnd.random() for _ in range(17)] for _ in range(17)]
    for sig in [(a, b, c, d) for a in (0, 1) for b in (0, 1) for c in (0, 1) for d in (0, 1)]:
        if sig == (0, 0, 0, 0):
            continue
        t = Image.new('RGBA', (16, 16))
        px = t.load()
        for y in range(16):
            for x in range(16):
                fx, fy = (x + 0.5) / 16, (y + 0.5) / 16
                v = (sig[0] * (1 - fx) * (1 - fy) + sig[1] * fx * (1 - fy) + sig[2] * (1 - fx) * fy + sig[3] * fx * fy)
                # wobble the edge a little, tile-seamless at the borders (noise is 0 at x/y = 0 and 16)
                wob = 0.10 * (noise[y][x] - 0.5) * min(x, 16 - x, y, 16 - y) / 8
                v += wob
                if v > 0.56:
                    col = DEEP1 if (x * 7 + y * 3) % 23 else DEEP2
                elif v > 0.46:
                    col = DEEP3
                else:
                    col = shallow_tile.getpixel((x, y))[:3]
                px[x, y] = col + (255,)
        out[sig] = t
    return out


def ripple(t):
    """a couple of soft wave dashes on a deep tile."""
    t = t.copy()
    d = ImageDraw.Draw(t)
    d.line([3, 5, 7, 5], fill=DEEP3)
    d.line([9, 11, 12, 11], fill=DEEP3)
    return t




def weather(im, val=0.84, sat=0.8):
    """darken and desaturate (old, damp timber and plaster)."""
    im = im.copy()
    px = im.load()
    for y in range(im.size[1]):
        for x in range(im.size[0]):
            r, g, b, a = px[x, y]
            if a == 0 or (r, g, b) == OL:
                continue
            h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            r2, g2, b2 = colorsys.hsv_to_rgb(h, s * sat, v * val)
            px[x, y] = (int(r2 * 255), int(g2 * 255), int(b2 * 255), a)
    return im


def patch_roof(im, seed, rows=2, shade=(0.8, 1.15)):
    """nail a few mismatched patches onto a roof (top `rows` tile rows)."""
    im = im.copy()
    d = ImageDraw.Draw(im)
    rnd = random.Random(seed)
    w = im.size[0]
    for _ in range(max(2, w // 24)):
        pw, ph = rnd.randint(5, 9), rnd.randint(4, 6)
        for _try in range(30):
            x, y = rnd.randint(3, w - pw - 4), rnd.randint(4, rows * 16 - ph - 4)
            p = im.getpixel((x + pw // 2, y + ph // 2))
            if p[3] and p[:3] != OL:
                break
        p = im.getpixel((x + pw // 2, y + ph // 2))
        f = rnd.choice(shade)
        col = tuple(min(255, int(c * f)) for c in p[:3])
        d.rectangle([x, y, x + pw, y + ph], fill=(int(col[0] * .6), int(col[1] * .6), int(col[2] * .6)))
        d.rectangle([x + 1, y + 1, x + pw - 1, y + ph - 1], fill=col)
        for (nx, ny) in ((x + 1, y + 1), (x + pw - 1, y + 1), (x + 1, y + ph - 1), (x + pw - 1, y + ph - 1)):
            d.point((nx, ny), fill=(40, 40, 40))
    return im


def tint_low_sat(im, col, amt, maxsat=0.35):
    im = im.copy()
    px = im.load()
    for y in range(im.size[1]):
        for x in range(im.size[0]):
            r, g, b, a = px[x, y]
            if not a or (r, g, b) == OL:
                continue
            h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            if s > maxsat:
                continue
            k = amt * v * 1.6
            px[x, y] = (int(r * (1 - amt) + col[0] * k), int(g * (1 - amt) + col[1] * k), int(b * (1 - amt) + col[2] * k), a)
    return im


def build(out_dir):
    import draw_swamp as DS
    import draw_port as DP
    house = timg('ninja_TilesetHouse')
    ele = timg('ninja_TilesetElement')
    nat = timg('ninja_TilesetNature')
    floor = timg('ninja_TilesetFloor')
    water = timg('ninja_TilesetWater')

    def H(c, r, w, h, src=house):
        return src.crop((c * 16, r * 16, (c + w) * 16, (r + h) * 16))

    # ---------------- SwampFloor / SwampWater ----------------
    pf = swap(floor, [(G1, B1), (G2, B2), (DIRT1, SAND[0]), (DIRT2, SAND[1])])
    pf.save(out_dir + 'ninja_SwampFloor.png')
    pw = swap(water, [(W1, BW1), (W2, BW2), (G1, B1), (G2, B2), ((255, 173, 93), SAND[0]),
                      ((215, 139, 74), SAND[1]), ((255, 203, 141), SAND[2]), ((255, 255, 255), SCUM),
                      ((150, 83, 64), SHORE), ((116, 163, 52), (74, 110, 56))])
    pw.paste(water.crop((0, 12 * 16, water.size[0], water.size[1])), (0, 12 * 16))
    pw.save(out_dir + 'ninja_SwampWater.png')
    shallow = pw.crop((16, 1 * 16, 32, 2 * 16))          # full murky water (centre of the shore block)
    mud = pf.crop((16, 8 * 16, 32, 9 * 16))             # full mud (centre of the dirt autotile)

    S = Sheet(32, 72)

    # ---- 1. roof recolours of the 4x3 houses: moss / bark / plum / pine, weathered walls, patched roofs ----
    roofs = H(0, 0, 16, 2)
    walls = weather(H(0, 2, 16, 1), 0.86, 0.85)
    hues = [('moss', 0.22, 0.75, 0.70), ('bark', 0.07, 0.55, 0.62), ('plum', 0.80, 0.45, 0.62),
            ('pine', 0.42, 0.55, 0.52)]
    for i, (nm, hue, sm, vm) in enumerate(hues):
        blk = Image.new('RGBA', (256, 48))
        rr = hue_shift(roofs, hue, sm, vm, minsat=0.03, warm_only=False)
        for j in range(4):
            blk.alpha_composite(patch_roof(rr.crop((j * 64, 0, j * 64 + 64, 32)), seed=i * 7 + j), (j * 64, 0))
        blk.alpha_composite(walls, (0, 32))
        S.put('roofs_' + nm, blk, 0, i * 3)
        for j, base in enumerate(('orange', 'beige', 'orange2', 'red')):
            S.spr['%s_%s' % (base, nm)] = (j * 4, i * 3, 4, 3)
    # small 3x3 houses: gable / round / smallwood / hut, recoloured and patched
    for nm, (c, r), hue in (('gable_moss', (26, 0), 0.22), ('gable_plum', (26, 0), 0.80), ('round_bark', (23, 0), 0.07),
                            ('round_pine', (23, 0), 0.42), ('smallwood_moss', (19, 19), 0.24),
                            ('hut_bark', (0, 7), 0.08), ('thatch_moss', (3, 7), 0.20)):
        im = H(c, r, 3, 3)
        top = hue_shift(im.crop((0, 0, 48, 32)), hue, 0.6, 0.66, minsat=0.03, warm_only=False)
        im2 = weather(im, 0.86, 0.85)
        im2.paste(patch_roof(top, seed=len(nm)), (0, 0))
        S.put(nm, im2)

    # ---- 2. deep-murk autotile (4x4) + ripple, shallow ripple, wading shallows ----
    dt = deep_tiles(shallow)
    blk = Image.new('RGBA', (64, 64))
    keys = sorted(dt)
    for i, k in enumerate(keys):
        blk.alpha_composite(dt[k], ((i % 4) * 16, (i // 4) * 16))
    blk.alpha_composite(ripple(dt[(1, 1, 1, 1)]), (3 * 16, 3 * 16))
    c0, r0 = S.put('deep', blk)
    S.spr['deep_sig'] = {k: (c0 + i % 4, r0 + i // 4) for i, k in enumerate(keys)}
    S.spr['deep_ripple'] = (c0 + 3, r0 + 3, 1, 1)
    deep_full = dt[(1, 1, 1, 1)]
    shallow_r = shallow.copy()
    d = ImageDraw.Draw(shallow_r)
    d.line([4, 6, 8, 6], fill=(84, 108, 92)); d.line([9, 12, 11, 12], fill=(84, 108, 92))
    S.put('shallow_ripple', shallow_r)
    S.put('wade', DS.wade(shallow, SAND[0]))
    S.put('wade2', DS.wade(shallow, SAND[1]).transpose(Image.FLIP_LEFT_RIGHT))

    # ---- 3. boardwalk planks (Water 0..3, 12..15 = a 4x4 blob set) over murk, deep murk and mud ----
    planks = swap(H(0, 12, 4, 4, water), [((255, 173, 93), DS.WOOD[0]), ((230, 106, 58), DS.WOOD[1]),
                                          ((125, 62, 36), (78, 60, 44))])
    for nm, base in (('walk_s', shallow), ('walk_d', deep_full), ('walk_m', mud)):
        blk = Image.new('RGBA', (64, 64))
        for y in range(4):
            for x in range(4):
                blk.alpha_composite(base, (x * 16, y * 16))
        blk.alpha_composite(planks)
        S.put(nm, blk)
    # ---- 4. stilts, ladders, rope bridges (over shallow and deep murk) ----
    for sfx, base in (('s', shallow), ('d', deep_full)):
        blk = Image.new('RGBA', (96, 16))
        for i, t in enumerate([DS.stilts(base, 'left'), DS.stilts(base, 'mid'), DS.stilts(base, 'right'),
                               DS.ladder(base), DS.rope_bridge(base), DS.rope_bridge(base, True)]):
            blk.alpha_composite(t, (i * 16, 0))
        c0, r0 = S.put('stilts_' + sfx, blk)
        for i, k in enumerate(('stilt_l', 'stilt_m', 'stilt_r', 'ladder', 'rope_h', 'rope_v')):
            S.spr['%s_%s' % (k, sfx)] = (c0 + i, r0, 1, 1)

    # ---- 5. buildings ----
    # tavern 6x3: the lantern-lit shopfront (House 19..22, 0..2) widened, moss roof
    t4 = H(19, 0, 4, 3)
    tav = Image.new('RGBA', (96, 48))
    tav.alpha_composite(stretch_h(t4.crop((0, 0, 64, 32)), 96, 24, 24), (0, 0))
    for i, c in enumerate([0, 1, 2, 1, 2, 3]):
        tav.alpha_composite(t4.crop((c * 16, 32, c * 16 + 16, 48)), (i * 16, 32))
    tav_roof = tint_low_sat(tav.crop((0, 0, 96, 26)), (96, 116, 70), 0.45)
    tav.paste(patch_roof(tav_roof, 3), (0, 0))
    S.put('tavern', weather(tav, 0.92, 0.9))
    # apothecary 3x3: the House shopfront (16..18, 0..2), plum roof, potions on the board, herbs at the eaves
    ap = hue_shift(H(16, 0, 3, 3), 0.80, 0.9, 0.75, minsat=0.15, warm_only=False)
    dd = ImageDraw.Draw(ap)
    dd.rectangle([13, 6, 34, 17], fill=(206, 196, 160))
    dd.rectangle([13, 6, 34, 6], fill=OL); dd.rectangle([13, 17, 34, 17], fill=OL)
    pot = pimg('Items/Potion/LifePot.png')
    pot2 = pimg('Items/Potion/WaterPot.png')
    ap.alpha_composite(pot, (14, 7)); ap.alpha_composite(pot2, (25, 7))
    hb = DS.herbs()
    ap.alpha_composite(hb.crop((0, 0, 16, 12)), (0, 31)); ap.alpha_composite(hb.crop((0, 0, 16, 12)), (32, 31))
    S.put('apothecary', ap)
    # trading post 4x4: the porch shop (House 29..32, 0..3), pine roof
    st = weather(hue_shift(H(29, 0, 4, 4), 0.40, 0.45, 0.70, minsat=0.25, warm_only=True), 0.9, 0.9)
    S.put('store', st)
    S.put('chapel', DS.bog_chapel())                      # 3x5, door (1,4)
    S.put('crypt', DS.crypt())                            # 5x4, door (2,3)
    S.put('smokehouse', DS.smokehouse())                  # 3x3, door (1,2)
    S.put('cistern', DS.cistern())                        # 2x2
    va = timg('ninja_TilesetVillageAbandoned')
    S.put('pole_hut', weather(hue_shift(va.crop((14 * 16, 0, 17 * 16, 5 * 16)), 0.07, 0.55, 0.8, minsat=0.3,
                                        warm_only=True), 0.85, 0.85))   # 3x5 hut on legs

    # ---- 6. boats ----
    for i in range(3):
        S.put('skiff%d' % i, DS.skiff(i))                 # 3x1, bow east
        S.put('skiffv%d' % i, DS.skiff(i).transpose(Image.ROTATE_90))   # 1x3, bow north

    # ---- 7. vegetation ----
    def recol_tree(t):
        return hue_shift(t, 0.20, 0.75, 0.62, minsat=0.15, warm_only=False)
    S.put('cypress', DS.bald_cypress(3))                            # 3x4
    S.put('cypress2', DS.bald_cypress(9).transpose(Image.FLIP_LEFT_RIGHT))
    S.put('cypress_s', DS.small_cypress(5))                         # 2x3
    S.put('dead_moss', DS.dead_mossy(weather(nat.crop((4 * 16, 0, 6 * 16, 32)), 0.78, 0.55), 5))   # 2x2
    S.put('dead_big', DS.dead_mossy(weather(nat.crop((0, 5 * 16, 4 * 16, 8 * 16)), 0.78, 0.55), 6))   # 4x3
    S.put('ruin', weather(va.crop((0, 0, 4 * 16, 3 * 16)), 0.85, 0.8))                       # 4x3 ruined wall
    S.put('ruin_low', weather(va.crop((17 * 16, 3 * 16, 20 * 16, 5 * 16)), 0.85, 0.8))       # 3x2
    S.put('stump', weather(nat.crop((0, 8 * 16, 2 * 16, 10 * 16)), 0.8, 0.6))                # 2x2 big stump
    S.put('logs', weather(va.crop((6 * 16, 6 * 16, 7 * 16, 10 * 16)), 0.85, 0.8))           # 1x4 log
    for i in range(3):
        S.put('reeds%d' % i, DS.reeds(i))
    S.put('lily', DS.lilies())
    S.put('lily_f', DS.lilies(True))
    for i in range(3):
        S.put('shroom%d' % i, DS.mushrooms(i))
    S.put('wisp', DS.wisp())
    S.put('wisp2', DS.wisp((180, 220, 255)))
    S.put('fireflies', DS.fireflies())
    S.put('mist_a', DS.mist(3, 2, 1))
    S.put('mist_b', DS.mist(4, 2, 2))
    S.put('mist_c', DS.mist(2, 1, 3))

    # ---- 8. props ----
    S.put('lantern_post', DS.lantern_post())              # 1x2
    S.put('deck_lantern', DS.deck_lantern())
    S.put('candles', DS.candles())
    for k in ('round', 'cross', 'old', 'slab', 'mound', 'fresh'):
        S.put('tomb_' + k, DS.tombstone(k))
    for k in ('h', 'v', 'post'):
        S.put('ifence_' + k, DS.iron_fence(k))
    S.put('eel_rack', DS.eel_rack2())                     # 2x2
    S.put('eel_basket', DS.eel_basket())
    S.put('cray_trap', DS.crayfish_trap())
    S.put('cauldron', DS.cauldron())
    S.put('bottles', DS.bottles())
    S.put('skull_post', DS.skull_post())                  # 1x2
    S.put('herbs', DS.herbs())
    S.put('sword', DS.sword_upright(0))
    S.put('sword2', DS.sword_upright(1))
    S.put('helmet', DS.helmet_shield())
    for i, cl in enumerate((((150, 84, 76), (110, 60, 56)), ((92, 104, 136), (66, 74, 100)), ((176, 156, 96), (130, 112, 70)))):
        S.put('banner%d' % i, DS.war_banner(cl, i))
    S.put('frog', DS.frog(pimg))
    S.put('cat', DP.animal_frame(pimg, 'Actor/Animal/CatBlack/SpriteSheet.png', 16))
    S.put('bench', DP.bench())
    S.put('table', DP.table())
    S.put('net_pile', DP.net_pile(pimg))
    S.put('rope', ele.crop((8 * 16, 3 * 16, 9 * 16, 4 * 16)))
    S.put('rowboat_up', DP.upturned(water.crop((26 * 16, 0, 28 * 16, 16))))

    # ---- 8b. improvement pass: fishing lift-net, battlefield, square furniture, light pools (draw_swamp2) ----
    import draw_swamp2 as D2
    S.put('liftnet', D2.lift_net())                       # 15x14
    S.put('winch', D2.winch())
    S.put('light_pool', D2.light_pool())                  # 3x3
    for i in range(3):
        S.put('scorch%d' % i, D2.scorch(i))               # 2x2
    for i in range(2):
        S.put('stakes%d' % i, D2.stakes(False, i))        # 1x2
        S.put('stakes_b%d' % i, D2.stakes(True, i + 5))   # 1x2
    S.put('stakes_fallen', D2.fallen_stakes())            # 2x1
    S.put('siege', D2.siege_engine())                     # 4x3
    S.put('cart', D2.broken_cart())                       # 3x2
    S.put('bones', D2.bones())
    S.put('skull', D2.skull())
    S.put('skull_m', D2.skull(True))
    S.put('armour', D2.armour())
    S.put('shield0', D2.shield(0))
    S.put('shield1', D2.shield(1))
    S.put('crow', D2.crow())
    S.put('crow2', D2.crow(True))
    S.put('crow_p', D2.crow(False, True))
    for i, (lean, rust) in enumerate(((6, 1), (-7, 0), (3, 1), (-4, 1))):
        S.put('tsword%d' % i, D2.tall_sword(lean, rust))  # 1x2
    S.put('spear', D2.spear_banner())                     # 1x2
    S.put('brazier', D2.brazier())
    S.put('stool', D2.stool())
    S.put('handcart', D2.handcart())                      # 2x1
    S.put('crates', D2.crate_stack())
    des = timg('ninja_TilesetDesert')
    st_r = des.crop((2 * 16, 10 * 16, 4 * 16, 12 * 16))
    for nm, hue in (('stall_plum', 0.80), ('stall_moss', 0.22)):
        stl = weather(hue_shift(st_r, hue, 0.7, 0.75, warm_only=False, minsat=0.3), 0.92, 0.9)
        dd = ImageDraw.Draw(stl)
        for x, col in ((6, (150, 96, 160)), (12, (176, 120, 76)), (19, (120, 200, 120)), (25, (170, 110, 190))):
            dd.ellipse([x - 2, 19, x + 2, 22], fill=OL)
            dd.ellipse([x - 1, 20, x + 1, 21], fill=col)
        S.put(nm, stl)                                    # 2x2

    # ---- 9. signs ----
    for nm, w, txt in (('sign_skiffs', 2, 'SKIFFS'), ('sign_inn', 2, 'INN'), ('sign_apoth', 3, 'APOTHECARY'),
                       ('sign_store', 4, 'TRADING POST'), ('sign_smoke', 3, 'SMOKEHOUSE')):
        S.put(nm, DS.swamp_sign(txt, w, paint_text))
    post = Image.new('RGBA', (32, 16))
    d = ImageDraw.Draw(post)
    for x in (5, 25):
        d.rectangle([x, 0, x + 2, 13], fill=(92, 72, 54)); d.line([x + 2, 0, x + 2, 13], fill=(60, 46, 36))
    d.rectangle([3, 13, 29, 14], fill=(0, 0, 0, 60))
    S.put('sign_posts', post)

    # ---- 10. herb garden soil (tilled autotile on swamp grass) ----
    til = floor.crop((0, 112, 176, 208))
    til = swap(til, [(DIRT1, (96, 74, 54)), (DIRT2, (80, 62, 46)), (G1, B1), (G2, B2)])
    S.put('tilled', til)

    used_rows = max(r + h for (c, r, w, h) in [v for k, v in S.spr.items() if isinstance(v, tuple) and len(v) == 4])
    im = S.im.crop((0, 0, 32 * 16, used_rows * 16))
    im.save(out_dir + 'ninja_SwampExtras.png')
    return S.spr


if __name__ == '__main__':
    import sys
    out = sys.argv[1] if len(sys.argv) > 1 else './'
    s = build(out)
    for k, v in s.items():
        print(k, v)
