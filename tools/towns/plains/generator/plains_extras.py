"""Generated tilesets for the plains market town.

ninja_PlainsFloor  : ninja_TilesetFloor with the grass recoloured to the white-biome (plains) greens used by the
                     world-gen prototype (GRASS_PAL[1]), and the dirt slightly warmer. Same layout as TilesetFloor.
ninja_PlainsExtras : composites made only from Ninja Adventure art (CC0): roof recolours, wide buildings (grain hall,
                     inn, stable, granary barn), a stone tower windmill + sails, animals (single frames), painted signs,
                     a grassy rise (recoloured Relief), tilled-soil autotile and hedges.
SPR maps a sprite name -> (col, row, w, h) inside ninja_PlainsExtras.
"""
import colorsys
from PIL import Image, ImageDraw

SRC = 'A:/forge_wave3/forge-gui/res/adventure/common/maps/tileset/ninja/'
PACK = 'A:/GameAssets/NinjaAdventure/pack/Ninja Adventure - Asset Pack/'

G1, G2 = (173, 188, 58), (168, 161, 41)
P1, P2 = (122, 202, 74), (98, 174, 66)          # world-gen white-biome grass
DIRT1, DIRT2 = (211, 134, 95), (189, 121, 89)


def timg(name):
    return Image.open(SRC + name + '.png').convert('RGBA')


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


def stretch_h(im, width, l, r):
    """repeat the pixel band [l, im.w - r) horizontally to reach `width`."""
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
    'F': ['111', '100', '110', '100', '100'], 'W': ['101', '101', '111', '111', '101'],
    ' ': ['000', '000', '000', '000', '000'],
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


def sign_board(w_tiles, text, board=(226, 170, 110), edge=(96, 58, 32), ink=(60, 34, 22)):
    W = w_tiles * 16
    im = Image.new('RGBA', (W, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rectangle([1, 3, W - 2, 12], fill=edge)
    d.rectangle([2, 4, W - 3, 11], fill=board)
    d.line([3, 11, W - 4, 11], fill=(196, 140, 90))
    paint_text(im, text, W // 2, 5, ink)
    return im


def build(out_dir):
    house = timg('ninja_TilesetHouse')
    ele = timg('ninja_TilesetElement')
    nat = timg('ninja_TilesetNature')
    floor = timg('ninja_TilesetFloor')
    relief = timg('ninja_TilesetRelief')
    field = timg('ninja_TilesetField')

    def H(c, r, w, h):
        return house.crop((c * 16, r * 16, (c + w) * 16, (r + h) * 16))

    # ---------------- PlainsFloor ----------------
    pf = swap(floor, [(G1, P1), (G2, P2)])
    pf.save(out_dir + 'ninja_PlainsFloor.png')
    swap(timg('ninja_TilesetWater'), [(G1, P1), (G2, P2)]).save(out_dir + 'ninja_PlainsWater.png')

    COLS, ROWS = 32, 40
    ex = Image.new('RGBA', (COLS * 16, ROWS * 16), (0, 0, 0, 0))
    SPR = {}

    def place(name, im, c, r):
        ex.alpha_composite(im, (c * 16, r * 16))
        SPR[name] = (c, r, im.size[0] // 16, im.size[1] // 16)

    # ---- 1. roof recolours of the 4x3 houses (rows 0..11) ----
    roofs = H(0, 0, 16, 2)
    walls = H(0, 2, 16, 1)
    hues = [('straw', 0.125, 0.55, 1.12), ('teal', 0.47, 0.55, 0.80), ('slate', 0.60, 0.45, 0.82),
            ('moss', 0.25, 0.65, 0.78)]
    for i, (nm, hue, sm, vm) in enumerate(hues):
        blk = Image.new('RGBA', (256, 48))
        blk.alpha_composite(hue_shift(roofs, hue, sm, vm), (0, 0))
        blk.alpha_composite(walls, (0, 32))
        ex.alpha_composite(blk, (0, i * 48))
        for j, base in enumerate(('orange', 'beige', 'orange2', 'red')):
            SPR['%s_%s' % (base, nm)] = (j * 4, i * 3, 4, 3)

    # ---- 2. the grain hall (13 x 6) ----  red-tile roof, timber walls, central gabled porch
    roof = H(12, 0, 4, 2)
    roof = hue_shift(roof, 0.035, 0.85, 0.92)                        # deeper terracotta
    hw = 13
    big_roof = stretch_v(stretch_h(roof, hw * 16, 16, 16), 3 * 16, 10, 6)
    wall_src = H(12, 2, 4, 1)
    def wcol(c):
        return wall_src.crop((c * 16, 0, c * 16 + 16, 16))
    lower = Image.new('RGBA', (hw * 16, 16))
    seq = [0] + [2] * (hw - 2) + [3]
    for i, c in enumerate(seq):
        lower.alpha_composite(wcol(c), (i * 16, 0))
    upper = lower.copy()
    # upper storey: same wall but windows lifted; cover the roof-shadow line of the lower row
    hall = Image.new('RGBA', (hw * 16, 6 * 16))
    hall.alpha_composite(big_roof, (0, 0))
    hall.alpha_composite(upper, (0, 48))
    lw = lower.copy()
    hall.alpha_composite(lw, (0, 64))
    # gabled porch in the centre: the gable house front (26..28, rows 0..2), recoloured to match
    gable = hue_shift(H(26, 0, 3, 3), 0.035, 0.85, 0.92)
    hall.alpha_composite(gable, (5 * 16, 3 * 16 - 16 + 16 - 16))   # rows 2..4 of the hall
    # ground row: arcade posts under the eaves (walkable plinth) - plinth line + grain sacks drawn on the tile row 5
    plinth = Image.new('RGBA', (hw * 16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(plinth)
    d.rectangle([2, 0, hw * 16 - 3, 3], fill=(120, 84, 66))
    d.rectangle([2, 4, hw * 16 - 3, 5], fill=(84, 56, 46))
    hall.alpha_composite(plinth, (0, 80))
    place('grain_hall', hall, 16, 0)
    SPR['grain_hall_door'] = (6, 4)            # door tile offset inside the sprite (gable door)

    # ---- 3. inn (7 x 4, two storeys, straw thatch) ----
    o = H(0, 0, 4, 3)
    o_roof = hue_shift(o.crop((0, 0, 64, 32)), 0.10, 0.75, 1.05)
    iw = 7
    r2 = stretch_h(o_roof, iw * 16, 16, 16)
    owall = o.crop((0, 32, 64, 48))
    def ocol(c):
        return owall.crop((c * 16, 0, c * 16 + 16, 16))
    inn = Image.new('RGBA', (iw * 16, 4 * 16))
    inn.alpha_composite(r2, (0, 0))
    up = [0, 2, 2, 2, 2, 2, 3]
    lo = [0, 2, 2, 1, 2, 2, 3]
    for i, c in enumerate(up):
        inn.alpha_composite(ocol(c), (i * 16, 32))
    for i, c in enumerate(lo):
        t = ocol(c).copy()
        inn.alpha_composite(t, (i * 16, 48))
    # beam between storeys
    d = ImageDraw.Draw(inn)
    d.rectangle([2, 47, iw * 16 - 3, 49], fill=(120, 70, 44))
    place('inn', inn, 16, 6)
    SPR['inn_door'] = (3, 3)

    # ---- 4. stable (8 x 3): thatched barn roof (weathered brown), open stall arches ----
    o3 = H(0, 0, 4, 3)
    sroof = hue_shift(o3.crop((0, 0, 64, 32)), 0.075, 0.70, 0.72)
    sw = 8
    st = Image.new('RGBA', (sw * 16, 48))
    st.alpha_composite(stretch_h(sroof, sw * 16, 16, 16), (0, 0))
    owall3 = hue_shift(o3.crop((0, 32, 64, 48)), 0.07, 0.8, 0.85, minsat=0.05, warm_only=False)
    door_t = owall3.crop((16, 0, 32, 16))
    wall_t = owall3.crop((32, 0, 48, 16))
    st.alpha_composite(owall3.crop((0, 0, 16, 16)), (0, 32))
    st.alpha_composite(owall3.crop((48, 0, 64, 16)), ((sw - 1) * 16, 32))
    for i in range(1, sw - 1):
        st.alpha_composite(door_t if i % 2 == 1 else wall_t, (i * 16, 32))
    place('stable', st, 0, 12)

    # ---- 5. granary barn (6 x 3): dark red-tile roof, plank walls, double door ----
    rr = H(12, 0, 4, 3)
    gw = 6
    gr = Image.new('RGBA', (gw * 16, 48))
    gr.alpha_composite(stretch_h(hue_shift(rr.crop((0, 0, 64, 32)), 0.02, 0.7, 0.70), gw * 16, 16, 16), (0, 0))
    gwall = hue_shift(rr.crop((0, 32, 64, 48)), 0.07, 0.6, 0.80, minsat=0.05, warm_only=False)
    seq = [0, 2, 1, 1, 2, 3]
    for i, c in enumerate(seq):
        gr.alpha_composite(gwall.crop((c * 16, 0, c * 16 + 16, 16)), (i * 16, 32))
    place('granary', gr, 8, 12)
    SPR['granary_door'] = (2, 2)

    # ---- 6. tower windmill: stone-white silo body + door + cap; sails separate ----
    silo = H(19, 3, 3, 5)
    body = hue_shift(silo, 0.10, 0.18, 1.25, minsat=0.0, warm_only=False)
    capc = hue_shift(silo.crop((0, 0, 48, 24)), 0.03, 0.9, 0.75, minsat=0.0, warm_only=False)
    body.alpha_composite(capc, (0, 0))
    door = H(8, 3, 1, 1)
    body.alpha_composite(door, (16, 64))
    sails = Image.open(PACK + 'Backgrounds/Animated/MillPropeller/MillPropeller_A_64x64.png').convert('RGBA').crop((0, 0, 64, 64))
    wm = Image.new('RGBA', (80, 112))
    wm.alpha_composite(body, (16, 32))
    wm.alpha_composite(sails, (8, 6))
    place('windmill', wm, 27, 6)          # 5 x 7, body in cols 1-3 rows 2-6, door at (2, 6)

    # ---- 7. silos recoloured pale tin for the granary yard ----
    place('silo_big', hue_shift(silo, 0.09, 0.35, 1.05, minsat=0.0, warm_only=False), 22, 12)
    small = H(19, 9, 2, 4)
    place('silo_small', hue_shift(small, 0.09, 0.35, 1.05, minsat=0.0, warm_only=False), 25, 12)

    # ---- 8. animals (one frame each, bottom-aligned) ----
    A = PACK + 'Actor/Animal/'
    def frame(path, fw, flip=False):
        im = Image.open(A + path).convert('RGBA').crop((0, 0, fw, 16))
        return im.transpose(Image.FLIP_LEFT_RIGHT) if flip else im
    def cell(im, wt):
        c = Image.new('RGBA', (wt * 16, 16))
        c.alpha_composite(im, ((wt * 16 - im.size[0]) // 2, 0))
        return c
    place('cow', cell(frame('Cow/SpriteSheetWhiteSide.png', 24), 2), 0, 18)
    place('cow_l', cell(frame('Cow/SpriteSheetWhiteSide.png', 24, True), 2), 2, 18)
    place('horse', cell(frame('Horse/SpriteSheetBrownSide.png', 23), 2), 4, 18)
    place('horse_l', cell(frame('Horse/SpriteSheetBrownSide.png', 23, True), 2), 6, 18)
    place('horse_black', cell(frame('Horse/SpriteSheetBlackSide.png', 23), 2), 8, 18)
    place('pig', cell(frame('Pig/SpriteSheetPink.png', 16), 1), 10, 18)
    place('pig2', cell(frame('Pig/SpriteSheetBlack.png', 16), 1), 11, 18)
    place('chicken', cell(frame('Chicken/SpriteSheetWhite.png', 16), 1), 12, 18)
    place('chicken2', cell(frame('Chicken/SpriteSheetBrown.png', 16), 1), 13, 18)
    place('donkey', cell(frame('Donkey/SpriteSheetGrey.png', 16), 1), 14, 18)

    # ---- 9. stagecoach: drawn coach + two horses (5 x 3), see coach_draw.py ----
    import coach_draw
    place('coach', coach_draw.draw_coach(), 16, 18)

    # ---- 10. signs ----
    place('sign_coach', sign_board(2, 'COACH'), 0, 20)
    place('sign_inn', sign_board(2, 'INN'), 2, 20)
    place('sign_store', sign_board(2, 'STORE'), 4, 20)
    place('sign_grain', sign_board(3, 'GRAIN HALL'), 6, 20)
    place('sign_smith', sign_board(2, 'SMITH'), 9, 20)
    place('sign_market', sign_board(3, 'MARKET'), 11, 20)
    # sign post (two posts) under a board: 1 tile tall
    post = Image.new('RGBA', (32, 16))
    d = ImageDraw.Draw(post)
    for x in (5, 25):
        d.rectangle([x, 0, x + 2, 13], fill=(110, 66, 40)); d.line([x + 2, 0, x + 2, 13], fill=(70, 40, 26))
    d.rectangle([3, 13, 29, 14], fill=(0, 0, 0, 60))
    place('sign_posts', post, 14, 20)

    # ---- 11. grassy rise: Relief recoloured to plains greens (cols 0..6, rows 5..9) ----
    rel = relief.crop((0, 80, 112, 160))
    rel = swap(rel, [((255, 203, 141), P1), ((242, 173, 125), P2), ((189, 121, 89), (112, 150, 72)),
                     ((150, 83, 64), (82, 112, 58)), ((84, 60, 82), (52, 72, 50))])
    place('rise', rel, 0, 21)

    # ---- 12. tilled soil autotile (Floor cols 0..10 rows 7..12 with darker dirt, plains grass) ----
    til = floor.crop((0, 112, 176, 208))
    til = swap(til, [(DIRT1, (150, 104, 70)), (DIRT2, (124, 84, 58)), (G1, P1), (G2, P2)])
    place('tilled', til, 8, 21)

    # ---- 13. hedges: Field dark-green block (cols 0..4 rows 6..8) ----
    place('hedge', field.crop((0, 96, 80, 144)), 20, 21)

    # ---- 14. market stall awnings (Desert cloth stalls), striped recolour ----
    des = timg('ninja_TilesetDesert')
    stall_r = des.crop((2 * 16, 10 * 16, 4 * 16, 12 * 16))
    place('stall_red', stall_r, 0, 27)
    place('stall_yel', des.crop((4 * 16, 10 * 16, 6 * 16, 12 * 16)), 2, 27)
    place('stall_grn', hue_shift(stall_r, 0.30, 0.8, 0.9, warm_only=False, minsat=0.3), 4, 27)
    place('stall_blu', hue_shift(stall_r, 0.58, 0.8, 0.9, warm_only=False, minsat=0.3), 6, 27)

    # ---- 15. chapel: whitewashed gable, taller (3 x 4), cross on the ridge ----
    gab = H(26, 0, 3, 3)
    gab = hue_shift(gab, 0.09, 0.32, 1.06, minsat=0.0, warm_only=False)
    tall = stretch_v(gab, 64, 18, 22)
    cross = ele.crop((3 * 16, 2 * 16, 4 * 16, 3 * 16))
    ch = Image.new('RGBA', (48, 80))
    ch.alpha_composite(tall, (0, 16))
    ch.alpha_composite(cross, (16, 2))
    place('chapel', ch, 8, 27)
    SPR['chapel_door'] = (1, 4)

    # ---- 16. pale flagstone paving (InteriorFloor dark cobble 11..13 x 12..14, lightened) ----
    inf = timg('ninja_TilesetInteriorFloor').crop((11 * 16, 12 * 16, 14 * 16, 15 * 16))
    place('paving', hue_shift(inf, 0.10, 0.35, 2.1, minsat=0.0, warm_only=False), 12, 27)

    ex.save(out_dir + 'ninja_PlainsExtras.png')
    return SPR


if __name__ == '__main__':
    import sys
    out = sys.argv[1] if len(sys.argv) > 1 else './'
    s = build(out)
    for k, v in s.items():
        print(k, v)
