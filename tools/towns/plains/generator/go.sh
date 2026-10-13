# Rebuild the plains market town: TMX + tilesets, preview, debug, in-context render, reachability check.
set -e
cd "$(dirname "$0")"
OUT=${1:-A:/GameAssets/towns/plains/}
python town.py "$OUT"
python render_town.py "$OUT/plains_market_town.tmx" full.png "$OUT/plains_town_debug.png" 2
python -c "
from PIL import Image
im=Image.open('full.png'); w=2000; im.resize((w, round(im.size[1]*w/im.size[0])), Image.LANCZOS).save('$OUT/plains_town_preview.png')"
python check.py "$OUT/plains_market_town.tmx"
python context.py "$OUT"
python render_town.py "$OUT/plains_context.tmx" "$OUT/plains_town_in_context.png" ctx_debug.png 1
