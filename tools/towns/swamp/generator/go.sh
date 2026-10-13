# Rebuild the swamp town: TMX + tilesets, preview, debug, in-context render, reachability check.
set -e
cd "$(dirname "$0")"
OUT=${1:-A:/GameAssets/towns/swamp/}
python town.py "$OUT"
python render_town.py "$OUT/swamp_town.tmx" full.png "$OUT/swamp_town_debug.png" 2
python mood.py full.png "$OUT/swamp_town_preview.png" 2000
python check.py "$OUT/swamp_town.tmx"
python context.py "$OUT"
python render_town.py "$OUT/swamp_context.tmx" ctx.png ctx_debug.png 1
python mood.py ctx.png "$OUT/swamp_town_in_context.png" 0
rm -f full.png ctx.png ctx_debug.png
