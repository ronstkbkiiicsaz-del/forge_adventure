"""Generated tilesets for the port town (blue biome).

ninja_PortFloor  : ninja_TilesetFloor with the grass recoloured to the world-gen blue-biome greens (GRASS_PAL[2])
                   and the dirt autotile recoloured to the world-gen beach sand (SAND), so every lane is a sandy path.
ninja_PortWater  : ninja_TilesetWater with the world-gen blue-biome water (WATER_PAL[2]), sand (WATER_SAND) and grass.
ninja_PortExtras : composites made from Ninja Adventure art (CC0) plus a few newly drawn sprites (see draw_port.py):
                   roof recolours, whitewashed cottages, deep-water autotile, piers over water, quay wall, terrace
                   wall + steps, tavern, harbourmaster's office, warehouse, fishmonger, store, shipwright, chapel of
                   the sea, lighthouse, boats, fish market props, signs.
SPR maps a sprite name -> (col, row, w, h) inside ninja_PortExtras.
"""
import colorsys, random
from PIL import Image, ImageDraw

SRC = 'A:/forge_wave3/forge-gui/res/adventure/common/maps/tileset/ninja/'
PACK = 'A:/GameAssets/NinjaAdventure/pack/Ninja Adventure - Asset Pack/'

G1, G2 = (173, 188, 58), (168, 161, 41)
B1, B2 = (112, 198, 96), (88, 172, 88)          # world-gen blue-biome grass (GRASS_PAL[2])
W1, W2 = (113, 221, 238), (121, 184, 206)
BW1, BW2 = (96, 214, 232), (92, 178, 210)       # world-gen blue-biome water (WATER_PAL[2])
SAND = [(236, 212, 150), (214, 188, 128), (248, 230, 182)]
DIRT1, DIRT2 = (211, 134, 95), (189, 121, 89)
DEEP1, DEEP2, DEEP3 = (66, 168, 212), (58, 146, 196), (120, 200, 230)
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


def build(out_dir):
    house = timg('ninja_TilesetHouse')
    ele = timg('ninja_TilesetElement')
    nat = timg('ninja_TilesetNature')
    floor = timg('ninja_TilesetFloor')
    relief = timg('ninja_TilesetRelief')
    rdet = timg('ninja_TilesetReliefDetail')
    water = timg('ninja_TilesetWater')
    des = timg('ninja_TilesetDesert')

    def H(c, r, w, h, src=house):
        return src.crop((c * 16, r * 16, (c + w) * 16, (r + h) * 16))

    # ---------------- PortFloor / PortWater ----------------
    pf = swap(floor, [(G1, B1), (G2, B2), (DIRT1, SAND[0]), (DIRT2, SAND[1])])
    pf.save(out_dir + 'ninja_PortFloor.png')
    pw = swap(water, [(W1, BW1), (W2, BW2), (G1, B1), (G2, B2), ((255, 173, 93), SAND[0]),
                      ((215, 139, 74), SAND[1]), ((255, 203, 141), SAND[2])])
    # keep the plank/boat tiles unrecoloured (rows 12+ and the boat at 25..27,0..1)
    pw.paste(water.crop((0, 12 * 16, water.size[0], water.size[1])), (0, 12 * 16))
    pw.paste(water.crop((25 * 16, 0, 28 * 16, 32)), (25 * 16, 0))
    pw.save(out_dir + 'ninja_PortWater.png')
    shallow = pw.crop((16, 7 * 16, 32, 8 * 16))          # full shallow water (grass-shore block centre)

    S = Sheet(32, 64)

    # ---- 1. roof recolours of the 4x3 houses (rows 0..11) + whitewashed cottages (rows 12..14) ----
    roofs = H(0, 0, 16, 2)
    walls = H(0, 2, 16, 1)
    hues = [('blue', 0.58, 0.60, 0.95), ('teal', 0.47, 0.55, 0.80), ('slate', 0.60, 0.30, 0.78),
            ('navy', 0.63, 0.70, 0.62)]
    for i, (nm, hue, sm, vm) in enumerate(hues):
        blk = Image.new('RGBA', (256, 48))
        blk.alpha_composite(hue_shift(roofs, hue, sm, vm), (0, 0))
        blk.alpha_composite(walls, (0, 32))
        S.put('roofs_' + nm, blk, 0, i * 3)
        for j, base in enumerate(('orange', 'beige', 'orange2', 'red')):
            S.spr['%s_%s' % (base, nm)] = (j * 4, i * 3, 4, 3)
    blk = Image.new('RGBA', (256, 48))
    ww = whiten(walls)
    for j, (base, hue, sm, vm) in enumerate([(0, 0.58, 0.65, 0.92), (8, 0.47, 0.6, 0.85), (12, 0.63, 0.7, 0.66),
                                             (4, 0.55, 0.35, 0.80)]):
        blk.alpha_composite(hue_shift(roofs.crop((base * 16, 0, base * 16 + 64, 32)), hue, sm, vm), (j * 64, 0))
        blk.alpha_composite(ww.crop((base * 16, 0, base * 16 + 64, 16)), (j * 64, 32))
    S.put('white_cottages', blk, 0, 12)
    for j, nm in enumerate(('white_blue', 'white_teal', 'white_navy', 'white_slate')):
        S.spr[nm] = (j * 4, 12, 4, 3)
    # small 3x3 houses: gable (wood, slate roof) and round (whitewashed, blue roof)
    gab = H(26, 0, 3, 3)
    S.put('gable_slate', hue_shift(gab, 0.60, 0.35, 0.85, minsat=0.3))
    S.put('gable', gab)
    rnd_h = H(23, 0, 3, 3)
    S.put('round_white', whiten(rnd_h))

    # ---- 2. deep-water autotile (4x4) + ripple variant ----
    dt = deep_tiles(shallow)
    blk = Image.new('RGBA', (64, 64))
    keys = sorted(dt)
    for i, k in enumerate(keys):
        blk.alpha_composite(dt[k], ((i % 4) * 16, (i // 4) * 16))
    blk.alpha_composite(ripple(dt[(1, 1, 1, 1)]), (3 * 16, 3 * 16))
    c0, r0 = S.put('deep', blk)
    S.spr['deep_sig'] = {k: (c0 + i % 4, r0 + i // 4) for i, k in enumerate(keys)}
    S.spr['deep_ripple'] = (c0 + 3, r0 + 3, 1, 1)
    S.spr['deep_full'] = S.spr['deep_sig'][(1, 1, 1, 1)]
    shallow_r = shallow.copy()
    d = ImageDraw.Draw(shallow_r)
    d.line([4, 6, 8, 6], fill=(170, 236, 246)); d.line([9, 12, 11, 12], fill=(170, 236, 246))
    S.put('shallow_ripple', shallow_r)

    # ---- 3. piers over water: Water planks (0..3, 12..15) composited on shallow and deep water ----
    planks = H(0, 12, 4, 4, water)
    for nm, base in (('pier_s', shallow), ('pier_d', dt[(1, 1, 1, 1)])):
        blk = Image.new('RGBA', (64, 64))
        for y in range(4):
            for x in range(4):
                blk.alpha_composite(base, (x * 16, y * 16))
        blk.alpha_composite(planks)
        S.put(nm, blk)

    # ---- 4. quay wall (stone face over water) and terrace wall (grass bank, stone face) + steps ----
    rel_src = [(255, 203, 141), (242, 173, 125), (189, 121, 89), (150, 83, 64), (84, 60, 82)]
    face = relief.crop((16, 7 * 16, 64, 8 * 16))
    quay = swap(face, list(zip(rel_src, [STONE[0], STONE[0], STONE[1], STONE[2], STONE[3]])))
    q = Image.new('RGBA', (48, 16))
    for x in range(3):
        q.alpha_composite(shallow, (x * 16, 0))
    q.alpha_composite(quay)
    d = ImageDraw.Draw(q)
    d.line([0, 15, 47, 15], fill=(200, 240, 248))         # foam at the foot of the wall
    S.put('quay_face', q)
    S.put('quay_face_sand', quay)                  # same wall standing on sand (beach ends)
    terr = swap(relief.crop((0, 5 * 16, 112, 10 * 16)), list(zip(rel_src, [B1, B2, STONE[1], STONE[2], STONE[3]])))
    S.put('terrace', terr)                          # 7x5 block, same layout as Relief cols 0..6 rows 5..9
    stairs = swap(rdet.crop((5 * 16, 3 * 16, 6 * 16, 4 * 16)),
                  [((255, 203, 141), STONE[0]), ((242, 173, 125), STONE[1]), ((189, 121, 89), STONE[1]),
                   ((150, 83, 64), STONE[2]), ((84, 60, 82), STONE[3]), ((230, 106, 58), STONE[1]),
                   ((255, 173, 93), STONE[0]), ((125, 62, 36), STONE[3])])
    S.put('stairs', hue_shift(stairs, 0.08, 0.15, 1.0, minsat=0.2, warm_only=False))
    # paving for the quay (InteriorFloor dark cobble 11..13 x 12..14, lightened to pale stone)
    inf = timg('ninja_TilesetInteriorFloor').crop((11 * 16, 12 * 16, 14 * 16, 15 * 16))
    S.put('paving', hue_shift(inf, 0.09, 0.22, 1.62, minsat=0.0, warm_only=False))

    # ---- 5. buildings ----
    import draw_port as DP
    # tavern 6x3: the lantern-lit shopfront (House 19..22, 0..2) widened, slate-blue roof
    t4 = H(19, 0, 4, 3)
    tav = Image.new('RGBA', (96, 48))
    tav.alpha_composite(stretch_h(t4.crop((0, 0, 64, 32)), 96, 24, 24), (0, 0))
    for i, c in enumerate([0, 1, 2, 1, 2, 3]):
        tav.alpha_composite(t4.crop((c * 16, 32, c * 16 + 16, 48)), (i * 16, 32))
    tav_roof = hue_shift(tav.crop((0, 0, 96, 26)), 0.60, 1.6, 0.95, minsat=0.05, warm_only=False)
    tav.paste(tav_roof, (0, 0))
    S.put('tavern', tav)
    S.spr['tavern_doors'] = [(1, 2), (2, 2), (3, 2), (4, 2)]
    # harbourmaster's office 5x4: two storeys, whitewashed, blue roof, flag
    S.put('harbour', DP.harbour_office(house, hue_shift, whiten, stretch_h, pimg))
    S.spr['harbour_door'] = (2, 3)
    # warehouse 8x4: dark slate roof, plank walls, two big loading doors
    S.put('warehouse', DP.warehouse(house, hue_shift, stretch_h, stretch_v))
    S.spr['warehouse_door'] = (3, 3)
    # fishmonger 3x3: the House shopfront (16..18, 0..2) with a fish on the roof board
    fm = H(16, 0, 3, 3)
    fm = hue_shift(fm, 0.55, 1.0, 1.0, minsat=0.25, warm_only=False) if False else fm
    fish = pimg('Items/Food/Fish.png')
    fm2 = fm.copy()
    dd = ImageDraw.Draw(fm2)
    dd.rectangle([13, 6, 34, 17], fill=(236, 214, 170))
    fm2.alpha_composite(fish.crop((0, 2, 16, 14)), (16, 6))
    S.put('fishmonger', fm2)
    # general store 4x4: the porch shop (House 29..32, 0..3), slate roof
    st = H(29, 0, 4, 4)
    S.put('store', hue_shift(st, 0.58, 0.45, 0.85, minsat=0.25, warm_only=True))
    S.spr['store_door'] = (1, 3)
    # shipwright 5x3: wooden gable workshop + open lean-to
    S.put('shipwright', DP.shipwright(house, hue_shift, stretch_h))
    S.spr['shipwright_door'] = (1, 2)
    # chapel of the sea 3x5: whitewashed gable, blue roof, anchor on the gable
    S.put('chapel', DP.sea_chapel(house, hue_shift, whiten, stretch_v))
    S.spr['chapel_door'] = (1, 4)
    # lighthouse 3x8 (drawn) and keeper's cottage uses a whitewashed house
    S.put('lighthouse', DP.lighthouse())
    S.spr['lighthouse_door'] = (1, 7)

    # ---- 6. boats ----
    S.put('ferry', DP.ferry(pimg))                        # 6x4, hull rows 2..3
    S.put('smack', DP.smack())                            # 4x3 fishing boat with a red sail
    rb = water.crop((25 * 16, 0, 28 * 16, 16))
    S.put('rowboat', water.crop((26 * 16, 0, 28 * 16, 16)))
    S.put('rowboat_blue', hue_shift(water.crop((26 * 16, 0, 28 * 16, 16)), 0.57, 0.8, 0.85, minsat=0.3))
    S.put('rowboat_up', DP.upturned(water.crop((26 * 16, 0, 28 * 16, 16))))
    S.put('hull', DP.hull_on_slip(pimg, house))            # 6x3 hull on the cradle (bow to the right, east)
    S.put('slipway', DP.slipway())                        # 1x1 rail tile (planks on sand, rails)

    # ---- 7. props ----
    S.put('drying_rack', DP.drying_rack(pimg))            # 2x2
    S.put('net_rack', DP.net_rack(pimg))                  # 2x2
    S.put('net_pile', DP.net_pile(pimg))                  # 2x1
    S.put('basket_fish', DP.basket(pimg, 'Items/Food/Fish.png'))
    S.put('basket_shrimp', DP.basket(pimg, 'Items/Food/Shrimp.png'))
    S.put('lobster_pot', DP.lobster_pot())                # 1x1
    S.put('lobster_pots', DP.lobster_pots())              # 1x1 stack
    S.put('fish_crate', DP.fish_crate(ele, pimg, 'Items/Food/Fish.png'))
    S.put('squid_crate', DP.fish_crate(ele, pimg, 'Items/Food/Calamari.png'))
    S.put('shrimp_crate', DP.fish_crate(ele, pimg, 'Items/Food/Shrimp.png'))
    S.put('octo_basket', DP.fish_basket(ele, pimg, 'Items/Food/Octopus.png'))
    S.put('bollard', DP.bollard())
    S.put('anchor', DP.anchor())
    S.put('crane', DP.crane(pimg))                        # 3x3
    S.put('flagpole', DP.flagpole(pimg))                  # 1x2
    S.put('rope', ele.crop((8 * 16, 3 * 16, 9 * 16, 4 * 16)))
    st_r = des.crop((2 * 16, 10 * 16, 4 * 16, 12 * 16))
    S.put('stall_blue', hue_shift(st_r, 0.58, 0.8, 0.9, warm_only=False, minsat=0.3))
    S.put('stall_teal', hue_shift(st_r, 0.47, 0.8, 0.85, warm_only=False, minsat=0.3))
    S.put('fish_stall', DP.fish_stall(hue_shift(st_r, 0.58, 0.8, 0.9, warm_only=False, minsat=0.3), pimg))
    S.put('fish_stall2', DP.fish_stall(hue_shift(st_r, 0.47, 0.8, 0.85, warm_only=False, minsat=0.3), pimg, alt=True))
    S.put('cat', DP.animal_frame(pimg, 'Actor/Animal/Cat/SpriteSheet.png', 16))
    S.put('cat2', DP.animal_frame(pimg, 'Actor/Animal/CatOrange/SpriteSheet.png', 16))
    S.put('gull', DP.gull())
    S.put('gull2', DP.gull(flip=True))
    S.put('statue', DP.sea_statue(house, whiten))         # 1x2
    S.put('bench', DP.bench())                            # 2x1
    S.put('table', DP.table())                            # 1x1 tavern table with mugs
    S.put('buoy', DP.buoy())                              # 1x1 in water

    # ---- 8. signs ----
    for nm, w, txt in (('sign_harbour', 3, 'HARBOUR'), ('sign_ferry', 2, 'FERRY'), ('sign_inn', 2, 'INN'),
                       ('sign_store', 2, 'STORE'), ('sign_fish', 3, 'FISH MARKET'), ('sign_boats', 3, 'BOATYARD'),
                       ('sign_wh', 3, 'WAREHOUSE')):
        S.put(nm, blue_sign(w, txt) if nm in ('sign_harbour', 'sign_ferry') else sign_board(w, txt))
    post = Image.new('RGBA', (32, 16))
    d = ImageDraw.Draw(post)
    for x in (5, 25):
        d.rectangle([x, 0, x + 2, 13], fill=(110, 66, 40)); d.line([x + 2, 0, x + 2, 13], fill=(70, 40, 26))
    d.rectangle([3, 13, 29, 14], fill=(0, 0, 0, 60))
    S.put('sign_posts', post)

    # ---- 9. palms (Desert 10..13, 10..11) kept as-is; sand-tone garden soil tilled block ----
    til = floor.crop((0, 112, 176, 208))
    til = swap(til, [(DIRT1, (150, 104, 70)), (DIRT2, (124, 84, 58)), (G1, B1), (G2, B2)])
    S.put('tilled', til)

    used_rows = max(r + h for (c, r, w, h) in [v for k, v in S.spr.items() if isinstance(v, tuple) and len(v) == 4])
    im = S.im.crop((0, 0, 32 * 16, used_rows * 16))
    im.save(out_dir + 'ninja_PortExtras.png')
    return S.spr


if __name__ == '__main__':
    import sys
    out = sys.argv[1] if len(sys.argv) > 1 else './'
    s = build(out)
    for k, v in s.items():
        print(k, v)
