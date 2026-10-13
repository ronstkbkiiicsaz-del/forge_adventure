"""Preview mood pass (render only, the tiles are unchanged): a light green-grey murk tint and a soft vignette, then
resize. Run: python mood.py <in.png> <out.png> [width]  (width 0 = keep size)"""
import sys
from PIL import Image, ImageDraw, ImageFilter, ImageChops
src, out = sys.argv[1], sys.argv[2]
width = int(sys.argv[3]) if len(sys.argv) > 3 else 2000
im = Image.open(src).convert('RGB')
if width and im.size[0] != width:
    im = im.resize((width, round(im.size[1] * width / im.size[0])), Image.LANCZOS)
w, h = im.size
tint = Image.new('RGB', (w, h), (34, 58, 50))
im = Image.blend(im, tint, 0.10)
# vignette: darken toward the corners by up to ~28%
m = Image.new('L', (w // 8, h // 8), 0)
d = ImageDraw.Draw(m)
d.ellipse([-w // 40, -h // 40, w // 8 + w // 40, h // 8 + h // 40], fill=255)
m = m.filter(ImageFilter.GaussianBlur(min(w, h) // 60)).resize((w, h), Image.BILINEAR)
dark = Image.blend(im, Image.new('RGB', (w, h), (8, 16, 14)), 0.28)
im = Image.composite(im, dark, m)
im.save(out)
