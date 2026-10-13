from PIL import Image, ImageDraw
PACK = 'A:/GameAssets/NinjaAdventure/pack/Ninja Adventure - Asset Pack/'
OL = (20, 27, 27)
def draw_coach():
    im = Image.new('RGBA', (80, 48), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    bx0, by0, bx1, by1 = 34, 15, 72, 35     # body
    # luggage on the roof
    d.rectangle([bx0 + 4, by0 - 7, bx1 - 6, by0], fill=OL)
    d.rectangle([bx0 + 5, by0 - 6, bx0 + 16, by0 - 1], fill=(176, 120, 72))
    d.rectangle([bx0 + 18, by0 - 5, bx1 - 7, by0 - 1], fill=(120, 140, 160))
    d.line([bx0 + 10, by0 - 6, bx0 + 10, by0 - 1], fill=(120, 76, 44))
    # body
    d.rounded_rectangle([bx0, by0, bx1, by1], radius=4, fill=OL)
    d.rounded_rectangle([bx0 + 1, by0 + 1, bx1 - 1, by1 - 1], radius=3, fill=(158, 52, 46))
    d.rectangle([bx0 + 2, by0 + 1, bx1 - 2, by0 + 3], fill=(196, 84, 64))          # roof highlight
    d.rectangle([bx0 + 2, by1 - 5, bx1 - 2, by1 - 2], fill=(118, 36, 36))          # lower shade
    d.line([bx0 + 2, by0 + 4, bx1 - 2, by0 + 4], fill=(230, 180, 90))              # gold trim
    d.line([bx0 + 2, by1 - 6, bx1 - 2, by1 - 6], fill=(230, 180, 90))
    # windows + door
    for wx in (bx0 + 4, bx0 + 26):
        d.rectangle([wx, by0 + 7, wx + 7, by0 + 13], fill=OL)
        d.rectangle([wx + 1, by0 + 8, wx + 6, by0 + 12], fill=(250, 226, 170))
        d.line([wx + 1, by0 + 8, wx + 6, by0 + 8], fill=(255, 245, 210))
    d.rectangle([bx0 + 15, by0 + 6, bx0 + 22, by1 - 2], fill=OL)
    d.rectangle([bx0 + 16, by0 + 7, bx0 + 21, by1 - 3], fill=(140, 44, 40))
    d.rectangle([bx0 + 17, by0 + 8, bx0 + 20, by0 + 12], fill=(250, 226, 170))
    d.point((bx0 + 20, by0 + 16), fill=(230, 180, 90))
    # driver's bench + lamp
    d.rectangle([bx0 - 8, by0 + 6, bx0, by0 + 12], fill=OL)
    d.rectangle([bx0 - 7, by0 + 7, bx0 - 1, by0 + 11], fill=(150, 96, 56))
    d.rectangle([bx0 - 3, by0 + 1, bx0 - 1, by0 + 4], fill=(255, 220, 120)); d.rectangle([bx0 - 4, by0, bx0, by0 + 5], outline=OL)
    # wheels
    def wheel(cx, cy, r):
        d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=OL)
        d.ellipse([cx - r + 1, cy - r + 1, cx + r - 1, cy + r - 1], fill=(120, 76, 44))
        d.ellipse([cx - r + 3, cy - r + 3, cx + r - 3, cy + r - 3], fill=(178, 124, 72))
        d.line([cx - r + 2, cy, cx + r - 2, cy], fill=(120, 76, 44)); d.line([cx, cy - r + 2, cx, cy + r - 2], fill=(120, 76, 44))
        d.ellipse([cx - 1, cy - 1, cx + 1, cy + 1], fill=OL)
    wheel(bx1 - 9, by1 + 2, 8)
    wheel(bx0 + 6, by1 + 4, 6)
    # shafts to the horses
    d.line([bx0 - 8, by0 + 13, 20, by0 + 18], fill=OL, width=2)
    d.line([bx0 - 8, by0 + 12, 20, by0 + 17], fill=(150, 96, 56))
    # horses (side frames, facing left)
    for path, ox, oy in (('Horse/SpriteSheetBrownSide.png', 12, 25), ('Horse/SpriteSheetBlackSide.png', 1, 31)):
        h = Image.open(PACK + 'Actor/Animal/' + path).convert('RGBA').crop((0, 0, 23, 16)).transpose(Image.FLIP_LEFT_RIGHT)
        im.alpha_composite(h, (ox, oy))
    return im
if __name__ == '__main__':
    im = draw_coach()
    bg = Image.new('RGBA', im.size, (122, 202, 74, 255)); bg.alpha_composite(im)
    bg.resize((im.size[0] * 8, im.size[1] * 8), Image.NEAREST).save('coach_prev.png')
