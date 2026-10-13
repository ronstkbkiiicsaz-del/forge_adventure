# Rebuild the port town: TMX + tilesets, preview, debug, in-context render, reachability check.
set -e
cd "$(dirname "$0")"
OUT=${1:-A:/GameAssets/towns/port/}
python town.py "$OUT"
python render_town.py "$OUT/port_town.tmx" full.png "$OUT/port_town_debug.png" 2
python -c "
from PIL import Image
im=Image.open('full.png'); w=2000; im.resize((w, round(im.size[1]*w/im.size[0])), Image.LANCZOS).save('$OUT/port_town_preview.png')"
python check.py "$OUT/port_town.tmx"
python context.py "$OUT"
python render_town.py "$OUT/port_context.tmx" "$OUT/port_town_in_context.png" ctx_debug.png 1
