"""Newly drawn / composed sprites for the port town (Ninja Adventure style: 1px dark outline (20,27,27),
flat fills with one highlight and one shade). Used by port_extras.build()."""
from PIL import Image, ImageDraw

OL = (20, 27, 27)
WOOD = [(226, 170, 110), (176, 120, 72), (120, 76, 44), (82, 50, 30)]
WHITE = [(244, 240, 228), (214, 210, 200), (170, 166, 160)]
BLUE = [(96, 150, 210), (62, 108, 170), (40, 70, 120)]
RED = [(214, 72, 60), (164, 46, 44)]
STONE = [(176, 170, 160), (150, 144, 136), (112, 106, 102), (78, 74, 74)]
FISH = [(178, 196, 210), (120, 140, 160), (70, 86, 104)]


def new(w, h):
    im = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    return im, ImageDraw.Draw(im)


def outline_rect(d, box, fill, ol=OL):
    d.rectangle(box, fill=ol)
    d.rectangle([box[0] + 1, box[1] + 1, box[2] - 1, box[3] - 1], fill=fill)


# ------------------------------------------------------------------ buildings
def harbour_office(house, hue_shift, whiten, stretch_h, pimg):
    """5x4: blue roof (2 rows), whitewashed upper storey with windows, lower storey with the door, a flag."""
    im, d = new(80, 64)
    roof = house.crop((4 * 16, 0, 8 * 16, 32))
    roof = hue_shift(roof, 0.58, 2.2, 0.92, minsat=0.03, warm_only=False)
    im.alpha_composite(stretch_h(roof, 80, 16, 16), (0, 0))
    wall = whiten(house.crop((4 * 16, 32, 8 * 16, 48)))
    col = lambda c: wall.crop((c * 16, 0, c * 16 + 16, 16))
    # upper storey: plain wall with three windows (drawn), lower: windows + door
    for i, c in enumerate([0, 2, 2, 2, 3]):
        im.alpha_composite(col(c), (i * 16, 32))
    for i, c in enumerate([0, 2, 1, 2, 3]):
        im.alpha_composite(col(c), (i * 16, 48))
    for wx in (6, 36, 66):
        if wx == 36:
            continue
        outline_rect(d, [wx - 2, 36, wx + 8, 44], (120, 170, 220))
        d.line([wx + 3, 37, wx + 3, 43], fill=OL)
        d.point((wx, 38), fill=(220, 240, 255))
    d.rectangle([1, 46, 78, 48], fill=BLUE[2])                 # beam between storeys
    d.line([1, 46, 78, 46], fill=BLUE[1])
    # flag on the ridge
    d.line([70, 0, 70, 12], fill=OL)
    d.rectangle([71, 1, 78, 5], fill=BLUE[1]); d.line([71, 1, 78, 1], fill=BLUE[0])
    d.rectangle([71, 6, 78, 6], fill=OL)
    return im


def warehouse(house, hue_shift, stretch_h, stretch_v):
    """8x4: dark slate roof, tarred plank walls, hoist door upstairs, two big double doors."""
    im, d = new(128, 64)
    roof = house.crop((12 * 16, 0, 16 * 16, 32))
    roof = hue_shift(roof, 0.60, 0.30, 0.62, minsat=0.03, warm_only=False)
    im.alpha_composite(stretch_h(roof, 128, 16, 16), (0, 0))
    # plank walls
    outline_rect(d, [0, 30, 127, 63], WOOD[2])
    for x in range(3, 126, 5):
        d.line([x, 32, x, 62], fill=WOOD[3])
    for x in range(5, 126, 10):
        d.line([x, 32, x, 62], fill=(136, 88, 52))
    d.rectangle([1, 46, 126, 48], fill=WOOD[3]); d.line([1, 46, 126, 46], fill=(150, 100, 60))
    # hoist door + beam
    outline_rect(d, [56, 32, 71, 45], (60, 36, 24))
    d.line([57, 33, 70, 44], fill=WOOD[1]); d.line([70, 33, 57, 44], fill=WOOD[1])
    d.rectangle([60, 26, 67, 29], fill=OL); d.line([63, 29, 63, 38], fill=(60, 60, 60))
    # two pairs of double doors on the ground floor
    for x0 in (32, 80):
        outline_rect(d, [x0, 49, x0 + 31, 63], (96, 58, 34))
        d.line([x0 + 15, 50, x0 + 15, 62], fill=OL); d.line([x0 + 16, 50, x0 + 16, 62], fill=OL)
        d.line([x0 + 1, 50, x0 + 14, 62], fill=WOOD[1]); d.line([x0 + 14, 50, x0 + 1, 62], fill=WOOD[1])
        d.line([x0 + 17, 50, x0 + 30, 62], fill=WOOD[1]); d.line([x0 + 30, 50, x0 + 17, 62], fill=WOOD[1])
        d.point((x0 + 13, 56), fill=(230, 200, 120)); d.point((x0 + 18, 56), fill=(230, 200, 120))
    # small windows upstairs
    for wx in (12, 100):
        outline_rect(d, [wx, 34, wx + 9, 42], (70, 90, 110))
        d.line([wx + 1, 35, wx + 8, 35], fill=(140, 170, 200))
    return im


def shipwright(house, hue_shift, stretch_h):
    """5x3: wooden gable workshop (House 26..28,0..2) + an open lean-to on posts with timber inside."""
    im, d = new(80, 48)
    gab = house.crop((26 * 16, 0, 29 * 16, 48))
    im.alpha_composite(gab, (0, 0))
    # lean-to: sloping plank roof from the gable's side, open front on posts
    d.polygon([(46, 14), (79, 20), (79, 31), (46, 27)], fill=OL)
    d.polygon([(47, 15), (78, 21), (78, 29), (47, 25)], fill=WOOD[1])
    for x in range(50, 78, 5):
        d.line([x, 16 + (x - 47) * 6 // 31, x, 25 + (x - 47) * 4 // 31], fill=WOOD[2])
    d.rectangle([47, 28, 78, 47], fill=(58, 40, 30))           # shadowed interior
    for y, c in ((36, WOOD[0]), (39, WOOD[1]), (42, WOOD[0])):  # stacked planks
        d.rectangle([52, y, 74, y + 2], fill=c); d.line([52, y + 2, 74, y + 2], fill=WOOD[3])
    for x in (47, 77):
        d.rectangle([x, 28, x + 2, 47], fill=OL); d.line([x + 1, 29, x + 1, 46], fill=WOOD[1])
    return im


def sea_chapel(house, hue_shift, whiten, stretch_v):
    """3x5 whitewashed chapel, blue pitched roof (front gable), bell cote with a bell, anchor in the gable."""
    im, d = new(48, 80)
    # bell cote
    d.polygon([(24, 0), (16, 9), (32, 9)], fill=OL)
    d.polygon([(24, 2), (18, 8), (30, 8)], fill=BLUE[1])
    outline_rect(d, [17, 8, 31, 24], WHITE[0])
    d.rectangle([20, 11, 28, 20], fill=OL)
    d.ellipse([21, 12, 27, 19], fill=(232, 190, 80)); d.point((24, 19), fill=(150, 110, 40))
    d.line([29, 9, 29, 23], fill=WHITE[1])
    # gable front: walls
    outline_rect(d, [3, 38, 44, 79], WHITE[0])
    d.rectangle([38, 39, 43, 78], fill=WHITE[1])
    # roof: thick inverted-V band from the apex
    d.polygon([(24, 18), (0, 44), (8, 44), (24, 28), (40, 44), (47, 44)], fill=OL)
    d.polygon([(24, 20), (2, 43), (8, 43), (24, 26), (40, 43), (45, 43)], fill=BLUE[1])
    d.line([(24, 21), (4, 42)], fill=BLUE[0])
    d.polygon([(24, 27), (9, 43), (39, 43)], fill=WHITE[0])
    d.line([(24, 28), (38, 43)], fill=WHITE[1])
    # anchor emblem in the gable
    a = BLUE[2]
    d.ellipse([22, 29, 26, 33], outline=a)
    d.line([24, 33, 24, 42], fill=a); d.line([21, 35, 27, 35], fill=a)
    d.line([19, 39, 24, 42], fill=a); d.line([29, 39, 24, 42], fill=a)
    # round windows
    for wx in (10, 34):
        d.ellipse([wx - 4, 50, wx + 4, 58], fill=OL)
        d.ellipse([wx - 3, 51, wx + 3, 57], fill=(120, 170, 220))
        d.point((wx - 1, 52), fill=(230, 245, 255))
    # arched door (row 4)
    d.rectangle([17, 64, 31, 79], fill=OL)
    d.ellipse([17, 58, 31, 72], fill=OL)
    d.rectangle([19, 66, 29, 79], fill=WOOD[2]); d.ellipse([19, 60, 29, 70], fill=WOOD[2])
    d.line([24, 61, 24, 79], fill=WOOD[3]); d.point((22, 72), fill=(230, 200, 120)); d.point((26, 72), fill=(230, 200, 120))
    d.rectangle([4, 77, 43, 78], fill=WHITE[2])
    return im


def lighthouse():
    """3x8 lighthouse: lantern room, gallery, white tower with red bands, stone base with a door."""
    im, d = new(48, 128)
    # cap + lantern
    d.polygon([(24, 1), (13, 12), (35, 12)], fill=OL)
    d.polygon([(24, 3), (15, 11), (33, 11)], fill=RED[1])
    d.line([(24, 3), (16, 11)], fill=RED[0])
    d.rectangle([22, 0, 26, 2], fill=OL)
    outline_rect(d, [14, 12, 34, 26], (255, 226, 120))
    d.rectangle([15, 13, 33, 16], fill=(255, 244, 190))
    for x in (19, 24, 29):
        d.line([x, 13, x, 25], fill=(70, 60, 50))
    d.ellipse([20, 16, 28, 24], fill=(255, 250, 220))
    # gallery
    outline_rect(d, [8, 26, 40, 31], (60, 64, 72))
    for x in range(10, 40, 3):
        d.line([x, 27, x, 29], fill=(140, 146, 156))
    d.line([9, 30, 39, 30], fill=(36, 40, 46))
    # tapering tower
    top, bot = 32, 108
    for y in range(top, bot):
        f = (y - top) / (bot - top)
        hw = 10 + 6 * f
        x0, x1 = int(24 - hw), int(24 + hw)
        band = (46 <= y < 58) or (76 <= y < 88)
        c, sh = (RED[0], RED[1]) if band else (WHITE[0], WHITE[1])
        d.line([x0, y, x1, y], fill=OL)
        d.line([x0 + 1, y, x1 - 1, y], fill=c)
        d.line([x1 - 5, y, x1 - 1, y], fill=sh)
    for (wy, ) in ((38,), (66,), (94,)):
        outline_rect(d, [21, wy, 27, wy + 6], (60, 80, 110))
        d.point((23, wy + 2), fill=(200, 230, 255))
    # stone base + door
    outline_rect(d, [4, 106, 44, 127], STONE[1])
    for y in (111, 116, 121):
        d.line([5, y, 43, y], fill=STONE[2])
    for i, y in enumerate((107, 112, 117, 122)):
        for x in range(8 + (i % 2) * 5, 44, 10):
            d.line([x, y, x, y + 3], fill=STONE[2])
    d.line([5, 107, 43, 107], fill=STONE[0])
    d.rectangle([18, 114, 30, 127], fill=OL); d.ellipse([18, 109, 30, 121], fill=OL)
    d.rectangle([20, 116, 28, 127], fill=BLUE[2]); d.ellipse([20, 111, 28, 119], fill=BLUE[2])
    d.line([24, 112, 24, 127], fill=OL)
    return im


# ------------------------------------------------------------------ boats
def ferry(pimg):
    """6x4: the Ninja Boat hull (navy band) with two sails; hull occupies rows 2..3."""
    im, d = new(96, 64)
    boat = pimg('Backgrounds/Vehicles/Boat.png')
    px = boat.load()
    for y in range(boat.size[1]):
        for x in range(boat.size[0]):
            p = px[x, y]
            if p[3] and p[:3] == (230, 106, 58):
                px[x, y] = (54, 88, 146, p[3])
            elif p[3] and p[:3] == (242, 234, 241):
                px[x, y] = (200, 230, 250, p[3])
    im.alpha_composite(boat, (8, 31))
    sail = pimg('Backgrounds/Vehicles/Sail.png')
    im.alpha_composite(sail, (30, 4))
    im.alpha_composite(sail, (56, 6))
    # pennant on the fore mast
    d.polygon([(58, 6), (66, 8), (58, 10)], fill=BLUE[1])
    return im


def smack():
    """4x3 small fishing boat, bow east, red-brown sail, nets and a crate on deck."""
    im, d = new(64, 48)
    hull = [(3, 30), (8, 25), (44, 24), (56, 28), (62, 33), (56, 38), (44, 42), (8, 41), (3, 36)]
    d.polygon(hull, fill=OL)
    inner = [(5, 31), (9, 27), (44, 26), (55, 29), (59, 33), (55, 37), (44, 40), (9, 39), (5, 35)]
    d.polygon(inner, fill=(64, 112, 150))
    deck = [(8, 30), (11, 29), (44, 28), (52, 31), (54, 33), (52, 35), (44, 37), (11, 37), (8, 35)]
    d.polygon(deck, fill=WOOD[0])
    for x in range(14, 50, 6):
        d.line([x, 29, x, 37], fill=WOOD[1])
    d.line([9, 38, 52, 38], fill=(44, 80, 114))
    # nets heap + crate at the stern
    d.ellipse([12, 30, 22, 36], fill=(150, 160, 120)); d.point((15, 32), fill=(110, 120, 90)); d.point((18, 34), fill=(110, 120, 90))
    outline_rect(d, [40, 30, 46, 35], WOOD[1])
    # mast + sail
    d.line([28, 2, 28, 33], fill=OL); d.line([29, 3, 29, 33], fill=WOOD[2])
    d.polygon([(30, 4), (30, 28), (50, 27)], fill=OL)
    d.polygon([(31, 6), (31, 27), (47, 26)], fill=(196, 92, 60))
    d.polygon([(31, 6), (31, 16), (37, 16)], fill=(222, 128, 84))
    d.line([(20, 30), (30, 27)], fill=WOOD[2])                 # boom
    return im


def upturned(rowboat):
    """an upturned rowboat on the sand (rowboat flipped, darkened, keel line, shadow)."""
    im, d = new(32, 16)
    d.ellipse([2, 9, 30, 15], fill=(0, 0, 0, 50))
    b = rowboat.transpose(Image.FLIP_TOP_BOTTOM)
    px = b.load()
    for y in range(16):
        for x in range(32):
            p = px[x, y]
            if p[3] and p[:3] != OL:
                px[x, y] = (int(p[0] * 0.72), int(p[1] * 0.62), int(p[2] * 0.55), p[3])
    im.alpha_composite(b)
    d.line([6, 8, 26, 8], fill=(90, 50, 30))
    return im


def hull_on_slip(pimg, house):
    """6x3: an unfinished hull on a cradle (bare ribs at the stern half), bow east."""
    im, d = new(96, 48)
    boat = pimg('Backgrounds/Vehicles/Boat.png')
    px = boat.load()
    for y in range(boat.size[1]):
        for x in range(boat.size[0]):
            p = px[x, y]
            if not p[3]:
                continue
            if p[:3] in ((20, 27, 27),):
                continue
            r, g, b = p[:3]
            px[x, y] = (min(255, int(r * 0.92 + 20)), min(255, int(g * 0.95 + 28)), min(255, int(b * 0.9 + 30)), p[3])
    # no cabin portholes: paint over the porthole row
    bd = ImageDraw.Draw(boat)
    bd.rectangle([9, 4, 44, 7], fill=(238, 196, 130))
    # bare ribs on the stern half: dark gaps between the ribs
    for x in range(6, 40, 4):
        bd.line([x, 9, x, 24], fill=(70, 42, 28))
    bd.line([5, 16, 40, 16], fill=(120, 76, 44))
    im.alpha_composite(boat, (8, 4))
    # cradle trestles and a ladder
    for x in (18, 40, 62, 82):
        d.rectangle([x, 30, x + 3, 45], fill=OL); d.line([x + 1, 31, x + 1, 44], fill=WOOD[1])
    d.rectangle([12, 42, 90, 44], fill=WOOD[3])
    d.line([4, 10, 9, 44], fill=OL); d.line([9, 8, 14, 42], fill=OL)
    for y in range(14, 42, 6):
        d.line([5 + (y - 10) * 5 // 34, y, 10 + (y - 10) * 5 // 34, y], fill=WOOD[1])
    return im


def slipway():
    """1x1 rail tile for a slipway running east-west (drawn on Ground over sand / shore)."""
    im, d = new(16, 16)
    for x in (1, 6, 11):
        d.rectangle([x, 2, x + 2, 13], fill=WOOD[2]); d.line([x, 2, x, 13], fill=WOOD[1])
    for y in (4, 10):
        d.line([0, y, 15, y], fill=(70, 70, 76)); d.line([0, y + 1, 15, y + 1], fill=(40, 40, 46))
    return im


# ------------------------------------------------------------------ props
def _fish(d, x, y, col=FISH):
    d.ellipse([x, y, x + 3, y + 7], fill=col[2])
    d.ellipse([x + 1, y + 1, x + 2, y + 6], fill=col[0])
    d.polygon([(x + 1, y + 7), (x - 1, y + 10), (x + 4, y + 10), (x + 2, y + 7)], fill=col[1])


def drying_rack(pimg):
    """2x2: two posts, two cross-bars, split fish hung to dry."""
    im, d = new(32, 32)
    d.ellipse([1, 26, 31, 31], fill=(0, 0, 0, 45))
    for x in (2, 27):
        d.rectangle([x, 4, x + 3, 29], fill=OL); d.line([x + 1, 5, x + 1, 28], fill=WOOD[1])
    for y in (5, 16):
        d.rectangle([2, y, 30, y + 2], fill=OL); d.line([3, y + 1, 29, y + 1], fill=WOOD[1])
    for i, x in enumerate(range(7, 26, 5)):
        _fish(d, x, 8, FISH if i % 2 else [(200, 170, 130), (160, 120, 90), (100, 70, 50)])
        _fish(d, x + 1, 19, FISH if (i + 1) % 2 else [(200, 170, 130), (160, 120, 90), (100, 70, 50)])
    return im


def net_rack(pimg):
    """2x2: a fishing net hung between two posts to dry."""
    im, d = new(32, 32)
    d.ellipse([1, 26, 31, 31], fill=(0, 0, 0, 45))
    net = pimg('Backgrounds/Vehicles/FishNet.png')
    net = net.resize((26, 20), Image.NEAREST)
    im.alpha_composite(net, (3, 6))
    for x in (1, 27):
        d.rectangle([x, 2, x + 3, 29], fill=OL); d.line([x + 1, 3, x + 1, 28], fill=WOOD[1])
    d.rectangle([1, 3, 30, 5], fill=OL); d.line([2, 4, 29, 4], fill=WOOD[1])
    for x in (8, 15, 22):
        d.ellipse([x, 24, x + 2, 26], fill=(230, 230, 220))   # floats
    return im


def lobster_pot():
    im, d = new(16, 16)
    d.ellipse([1, 11, 15, 15], fill=(0, 0, 0, 50))
    d.rounded_rectangle([1, 4, 14, 13], radius=4, fill=OL)
    d.rounded_rectangle([2, 5, 13, 12], radius=3, fill=(150, 104, 62))
    for x in (4, 7, 10):
        d.line([x, 5, x, 12], fill=(96, 62, 38))
    d.line([2, 9, 13, 9], fill=(96, 62, 38))
    d.ellipse([11, 6, 14, 10], fill=(60, 40, 30))
    return im


def lobster_pots():
    im, d = new(16, 16)
    d.ellipse([0, 11, 16, 16], fill=(0, 0, 0, 50))
    for (x0, y0) in ((0, 7), (6, 8), (3, 1)):
        d.rounded_rectangle([x0, y0, x0 + 10, y0 + 7], radius=3, fill=OL)
        d.rounded_rectangle([x0 + 1, y0 + 1, x0 + 9, y0 + 6], radius=2, fill=(150, 104, 62))
        for x in (x0 + 3, x0 + 6):
            d.line([x, y0 + 1, x, y0 + 6], fill=(96, 62, 38))
    d.point((10, 3), fill=(220, 80, 50)); d.point((11, 3), fill=(220, 80, 50))
    return im


def _icon(pimg, path, w):
    ic = pimg(path)
    bb = ic.getbbox()
    ic = ic.crop(bb)
    h = max(1, round(ic.size[1] * w / ic.size[0]))
    return ic.resize((w, h), Image.NEAREST)


def fish_crate(ele, pimg, path):
    im = ele.crop((0, 0, 16, 16)).copy()
    ic = _icon(pimg, path, 11)
    im.alpha_composite(ic, ((16 - ic.size[0]) // 2, max(0, 7 - ic.size[1] // 2)))
    return im


def fish_basket(ele, pimg, path):
    im = ele.crop((4 * 16, 0, 5 * 16, 16)).copy()
    ic = _icon(pimg, path, 10)
    im.alpha_composite(ic, (3, 0))
    return im


def bollard():
    im, d = new(16, 16)
    d.ellipse([3, 11, 13, 15], fill=(0, 0, 0, 50))
    d.rectangle([5, 6, 10, 13], fill=OL); d.rectangle([6, 7, 9, 13], fill=(70, 74, 82))
    d.ellipse([4, 3, 11, 8], fill=OL); d.ellipse([5, 4, 10, 7], fill=(110, 116, 126))
    d.line([5, 10, 10, 10], fill=(176, 140, 90))
    return im


def anchor():
    im, d = new(16, 16)
    c = (60, 64, 72)
    d.ellipse([6, 0, 10, 4], outline=OL)
    d.line([8, 4, 8, 14], fill=OL, width=2); d.line([4, 6, 12, 6], fill=OL, width=2)
    d.arc([2, 7, 14, 15], 20, 160, fill=OL, width=2)
    d.line([8, 5, 8, 13], fill=c)
    d.polygon([(1, 10), (3, 9), (3, 12)], fill=OL); d.polygon([(15, 10), (13, 9), (13, 12)], fill=OL)
    return im


def crane(pimg):
    im, d = new(48, 48)
    d.ellipse([10, 40, 40, 47], fill=(0, 0, 0, 45))
    cr = pimg('Backgrounds/Vehicles/Crane.png')
    im.alpha_composite(cr, (8, 10))
    return im


def flagpole(pimg):
    im, d = new(16, 32)
    flag = pimg('Backgrounds/Animated/Flag/FlagBlue16x16.png').crop((0, 0, 16, 16))
    im.alpha_composite(flag, (0, 0))
    bb = flag.getbbox()
    # extend the pole down to a small stone foot
    d.rectangle([bb[0], 14, bb[0] + 1, 29], fill=OL)
    d.line([bb[0] + 1, 14, bb[0] + 1, 29], fill=(150, 150, 160))
    d.rectangle([bb[0] - 3, 28, bb[0] + 4, 31], fill=OL); d.rectangle([bb[0] - 2, 29, bb[0] + 3, 30], fill=STONE[1])
    return im


def fish_stall(stall, pimg, alt=False):
    im = stall.copy()
    icons = ['Items/Food/Fish.png', 'Items/Food/Shrimp.png', 'Items/Food/Fish.png'] if not alt else \
        ['Items/Food/Calamari.png', 'Items/Food/Octopus.png', 'Items/Food/Fish.png']
    for i, p in enumerate(icons):
        ic = _icon(pimg, p, 8)
        im.alpha_composite(ic, (3 + i * 9, 17))
    return im


def animal_frame(pimg, path, fw):
    fr = pimg(path).crop((0, 0, fw, 16))
    im = Image.new('RGBA', (16, 16))
    im.alpha_composite(fr, ((16 - fw) // 2, 0))
    return im


def gull(flip=False):
    im, d = new(16, 16)
    d.polygon([(2, 6), (7, 8), (8, 10), (9, 8), (14, 5), (12, 8), (8, 12), (4, 8)], fill=OL)
    d.polygon([(3, 7), (7, 9), (8, 10), (9, 9), (13, 6), (11, 8), (8, 11), (5, 8)], fill=(246, 246, 240))
    d.point((3, 7), fill=(120, 120, 130)); d.point((13, 6), fill=(120, 120, 130))
    d.point((8, 9), fill=(240, 180, 60))
    return im.transpose(Image.FLIP_LEFT_RIGHT) if flip else im


def sea_statue(house, whiten):
    """1x2: a bronze anchor on a stone plinth (shrine of the sea)."""
    im, d = new(16, 32)
    d.ellipse([0, 27, 16, 32], fill=(0, 0, 0, 50))
    outline_rect(d, [1, 20, 14, 30], STONE[1])
    d.line([2, 21, 13, 21], fill=STONE[0]); d.line([2, 26, 13, 26], fill=STONE[2])
    c, c2 = (70, 150, 140), (40, 100, 96)
    d.ellipse([5, 1, 10, 6], outline=OL, width=2)
    d.rectangle([6, 5, 9, 19], fill=OL); d.line([7, 6, 7, 18], fill=c); d.line([8, 6, 8, 18], fill=c2)
    d.rectangle([2, 8, 13, 10], fill=OL); d.line([3, 9, 12, 9], fill=c)
    d.arc([1, 10, 14, 21], 10, 170, fill=OL, width=3)
    d.arc([2, 10, 13, 20], 15, 165, fill=c)
    return im


def bench():
    im, d = new(32, 16)
    d.rectangle([2, 12, 30, 14], fill=(0, 0, 0, 40))
    outline_rect(d, [1, 5, 30, 10], WOOD[1])
    d.line([2, 6, 29, 6], fill=WOOD[0])
    for x in (4, 26):
        d.rectangle([x, 10, x + 2, 14], fill=OL)
    return im


def table():
    im, d = new(16, 16)
    d.ellipse([1, 11, 15, 15], fill=(0, 0, 0, 45))
    d.ellipse([1, 3, 14, 11], fill=OL); d.ellipse([2, 4, 13, 10], fill=WOOD[1])
    d.ellipse([3, 4, 12, 8], fill=WOOD[0])
    for x in (4, 9):
        d.rectangle([x, 3, x + 2, 6], fill=OL); d.rectangle([x + 1, 4, x + 1, 5], fill=(240, 220, 160))
    return im


def buoy():
    im, d = new(16, 16)
    d.ellipse([2, 9, 14, 15], outline=(220, 246, 252))
    d.ellipse([5, 3, 11, 12], fill=OL)
    d.ellipse([6, 4, 10, 11], fill=RED[0])
    d.rectangle([6, 7, 10, 8], fill=WHITE[0])
    d.line([8, 0, 8, 3], fill=OL)
    return im


def net_pile(pimg):
    """2x1: a fishing net heaped on the ground, a few floats."""
    im, d = new(32, 16)
    d.ellipse([1, 9, 31, 15], fill=(0, 0, 0, 45))
    net = pimg('Backgrounds/Vehicles/FishNet.png').resize((30, 14), Image.NEAREST)
    im.alpha_composite(net, (1, 0))
    for x, y in ((6, 9), (15, 11), (24, 8)):
        d.ellipse([x, y, x + 3, y + 3], fill=OL); d.ellipse([x + 1, y + 1, x + 2, y + 2], fill=(236, 236, 226))
    return im


def basket(pimg, path):
    """1x1 wicker fish basket with a catch on top."""
    im, d = new(16, 16)
    d.ellipse([1, 11, 15, 15], fill=(0, 0, 0, 45))
    d.rounded_rectangle([2, 6, 13, 14], radius=3, fill=OL)
    d.rounded_rectangle([3, 7, 12, 13], radius=2, fill=(198, 150, 92))
    for y in (9, 11):
        d.line([3, y, 12, y], fill=(150, 104, 62))
    ic = pimg(path); ic = ic.crop(ic.getbbox())
    h = max(1, round(ic.size[1] * 10 / ic.size[0]))
    im.alpha_composite(ic.resize((10, h), Image.NEAREST), (3, max(0, 7 - h // 2)))
    return im
