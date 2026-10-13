"""Composite a TMX (CSV or base64[/zlib|gzip]) from its external tilesets; write clean + debug PNGs."""
import sys, os, base64, zlib, gzip, struct
import xml.etree.ElementTree as ET
from PIL import Image, ImageDraw

FH, FV, FD = 0x80000000, 0x40000000, 0x20000000

def load_tsx(path, first):
    root = ET.parse(path).getroot()
    tw, th = int(root.get('tilewidth')), int(root.get('tileheight'))
    cols = int(root.get('columns'))
    img = root.find('image')
    im = Image.open(os.path.join(os.path.dirname(path), img.get('source'))).convert('RGBA')
    coll = {}
    for t in root.findall('tile'):
        og = t.find('objectgroup')
        if og is not None:
            coll[int(t.get('id'))] = [tuple(float(o.get(k, 0)) for k in ('x', 'y', 'width', 'height'))
                                      for o in og.findall('object')]
    return dict(first=first, tw=tw, th=th, cols=cols, im=im, count=int(root.get('tilecount')), coll=coll)

def layer_data(layer, w, h):
    d = layer.find('data')
    enc = d.get('encoding')
    if enc == 'csv':
        vals = [int(v) for v in d.text.replace('\n', '').split(',') if v.strip()]
    else:
        raw = base64.b64decode(d.text.strip())
        c = d.get('compression')
        if c == 'zlib':
            raw = zlib.decompress(raw)
        elif c == 'gzip':
            raw = gzip.decompress(raw)
        vals = list(struct.unpack('<%dI' % (w * h), raw))
    return vals

def render(tmx, out_clean, out_debug, scale=3):
    root = ET.parse(tmx).getroot()
    W, H = int(root.get('width')), int(root.get('height'))
    tsets = []
    for ts in root.findall('tileset'):
        tsets.append(load_tsx(os.path.normpath(os.path.join(os.path.dirname(tmx), ts.get('source'))),
                              int(ts.get('firstgid'))))
    tsets.sort(key=lambda t: t['first'])

    def lookup(g):
        g &= ~(FH | FV | FD)
        for t in reversed(tsets):
            if g >= t['first']:
                return t, g - t['first']
    canvas = Image.new('RGBA', (W * 16, H * 16), (40, 40, 40, 255))
    collrects = []
    for layer in root.findall('layer'):
        vals = layer_data(layer, W, H)
        for i, raw in enumerate(vals):
            if raw == 0:
                continue
            t, lid = lookup(raw)
            x, y = i % W, i // W
            c, r = lid % t['cols'], lid // t['cols']
            tile = t['im'].crop((c * 16, r * 16, c * 16 + 16, r * 16 + 16))
            if raw & FD:
                tile = tile.transpose(Image.TRANSPOSE)
            if raw & FH:
                tile = tile.transpose(Image.FLIP_LEFT_RIGHT)
            if raw & FV:
                tile = tile.transpose(Image.FLIP_TOP_BOTTOM)
            canvas.alpha_composite(tile, (x * 16, y * 16))
            for (rx, ry, rw, rh) in t['coll'].get(lid, []):
                collrects.append((x * 16 + rx, y * 16 + ry, rw, rh))
    big = canvas.resize((W * 16 * scale, H * 16 * scale), Image.NEAREST)
    big.save(out_clean)
    dbg = big.copy()
    ov = Image.new('RGBA', dbg.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(ov)
    for (x, y, w, h) in collrects:
        d.rectangle([x * scale, y * scale, (x + w) * scale - 1, (y + h) * scale - 1], fill=(255, 0, 0, 70),
                    outline=(255, 0, 0, 200))
    colors = dict(door=(255, 255, 0), station=(0, 255, 255), quest_board=(0, 255, 0), npc_spot=(255, 0, 255),
                  exit=(80, 160, 255), collision=(255, 128, 0))
    for og in root.findall('objectgroup'):
        for o in og.findall('object'):
            kind = o.get('type') or os.path.basename(o.get('template', '')).replace('.tx', '')
            x, y = float(o.get('x')), float(o.get('y'))
            w, h = float(o.get('width', 16)), float(o.get('height', 16))
            col = colors.get(kind, (255, 255, 255))
            d.rectangle([x * scale, y * scale, (x + w) * scale - 1, (y + h) * scale - 1], outline=col + (255,), width=2)
            props = {p.get('name'): p.get('value') for p in o.iter('property')}
            label = props.get('interior') or props.get('role') or props.get('kind') or props.get('dir') or kind
            d.rectangle([x * scale + 1, y * scale + 1, x * scale + 6 * len(label) + 3, y * scale + 11], fill=(0, 0, 0, 170))
            d.text((x * scale + 2, y * scale + 1), label, fill=col + (255,))
    for gx in range(W):
        d.text((gx * 16 * scale + 2, 0), str(gx), fill=(255, 255, 255, 160))
    for gy in range(H):
        d.text((0, gy * 16 * scale + 2), str(gy), fill=(255, 255, 255, 160))
    dbg.alpha_composite(ov)
    dbg.save(out_debug)
    print('rendered', out_clean, out_debug, len(collrects), 'collision rects')

if __name__ == '__main__':
    render(sys.argv[1], sys.argv[2], sys.argv[3], int(sys.argv[4]) if len(sys.argv) > 4 else 3)
