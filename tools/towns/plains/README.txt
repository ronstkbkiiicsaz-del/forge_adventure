PLAINS MARKET TOWN  (Shandalar Ascendant, white/plains biome template town)            2026-10-13, Wren
==========================================================================================================

Files
  plains_market_town.tmx      70x50 tiles, 16px. Layers: Background, Ground, Foreground (spriteLayer=true,
                              collision via per-tile collision in the tilesets), AboveSprites (roofs, canopies,
                              sails, awnings), Objects (object layer). Map properties: biome=plains,
                              townKind=market_town, seamless=true.
  plains_town_preview.png     clean render, 2000px wide
  plains_town_debug.png       2x render with collision rects (red), object rects + labels, tile grid numbers
  plains_town_in_context.png  the town placed at (35,25) in a 140x100 patch of generated plains farmland
  plains_context.tmx          the TMX behind that render (illustration only, no collision/objects authored)
  tileset/ninja/              tilesets the TMX uses (sources are "tileset/ninja/<name>.tsx", relative to the TMX)
  generator/                  scripts (python 3 + Pillow). generator/go.sh rebuilds everything:
                                town.py          generator (based on Havenbrook's village.py)
                                town_layout.py   the hand layout (exec'd by town.py)
                                plains_extras.py builds ninja_PlainsFloor/PlainsWater/PlainsExtras
                                coach_draw.py    the stagecoach sprite
                                context.py       the 140x100 farmland patch
                                render_town.py   renderer (from scratchpad render.py, labels by object type)
                                check.py         reachability check (BFS from the west exit to every object)

Tilesets
  Copied unchanged from the repo (A:/forge_wave3, feature/set-start, maps/tileset/ninja/), collision included
  (Havenbrook's per-tile collision is kept wherever it already existed):
    ninja_TilesetNature, ninja_TilesetHouse, ninja_TilesetElement, ninja_TilesetFloorDetail,
    ninja_TilesetInteriorFloor (market cobbles), ninja_TilesetReliefDetail (steps up the windmill rise)
  ADDED per-tile collision for this town (merge into the repo .tsx when moving the map in):
    TilesetHouse +6 tiles, TilesetElement +11 tiles (smithy furnace/anvil, coach-office door, pots, trough...).
  Generated (new, CC0-derived from Ninja Adventure art only):
    ninja_PlainsFloor   = TilesetFloor with the grass recoloured to the world-gen white-biome greens
    ninja_PlainsWater   = TilesetWater with the same grass recolour (+ water collision quarters)
    ninja_PlainsExtras  = roof recolours (straw/teal/slate/moss), grain hall 13x6, inn 7x4, coach house 8x3,
                          granary 6x3, tower windmill 5x7 with sails, pale silos, chapel 3x5, animals (single
                          frames: cow, horse, pig, chicken, donkey), stagecoach 5x3, painted signs (COACH, INN,
                          STORE, GRAIN HALL, SMITH, MARKET), grassy rise (recoloured Relief), tilled-soil
                          autotile, hedgerows, market stalls, flagstone paving. All collision is in its .tsx.
  To put the map in the repo at maps/map/ascendant/: change each source to ../../tileset/ninja/<name>.tsx, copy
  the three generated tilesets there, and merge the added <tile> collision entries into House/Element.

Layout (tile coords x,y; origin top-left)
  Crossroads    N-S road x33-35 (exits N y0, S y49); E-W road y24-26 (exits W x0, E x69).
                Field tracks also run off the edges: back lane y18-19 (W), lane 1 y34-35 (W and E),
                Orchard Lane y44-45 (W). All exits are 'exit' objects (dir N/S/E/W, kind=field_track for tracks).
                No boundary walls: the town is meant to sit seamlessly in the plane.
  Market square x22-47, y13-33 (cobbled). Grain hall x28-40, y17-22, door (34,21).
                Market cross: flagstone ring x30-38 y27-31, roofed well (34,28-29), benches, trees, tubs.
                Notice board (37-39,23). Six stalls in two clusters, grain sacks/carts W of the hall, produce E.
  Stagecoach post (WEST GATE): coach house x0-7 y20-22 (door 3,22, COACH sign on the roof), booking office
                x8-10 y20-22 (door 9,22), coach yard x0-10 y23-29 with the stagecoach (2-6,26-28), hitching rail
                + horse, trough, COACH signpost (8-9,27-28), luggage. Object 'station' kind=stagecoach x0-10 y20-29.
  Inn           x12-18 y20-23, door (15,23), on the west road.
  General store x49-51 y21-23, door (50,23), on the east road; duck pond behind it.
  Smithy        x14-16 y31-33, door (15,33), furnace x18-20 y30-33, anvil bench (15-16,28-29).
  Chapel        x39-41 y6-10, door (40,10); churchyard x43-49 y4-10.
  Windmill      on a grassy rise x2-13 y1-10 (steps at 8,10); windmill door (8,8).
  Granary       x16-21 y8-10, door (18,10); three silos x23-29 y5-10.
  Livestock     cattle pen x54-61 y37-43, pig pen x63-68 y37-41, chicken run + coop x54-61 y44-49.
  Homes         24 cottages with doors marked (interior=home): NW back lane (4), SW by the coach yard (2),
                Orchard Lane cluster (6), NE by the chapel (2), east road (3) with back gardens, SE lane 1 (4),
                Wheat Lane (3). Staggered setbacks, front yards, paths to the door, picket fences, hedges.
  Edges         vegetable fields N and SW, NE orchard and field, SW orchard, SE wheat field, hedgerows on field
                boundaries, field trees; roads and tracks continue off-map.

Objects (layer "Objects", all type + string properties)
  door        interior = grain_hall | inn | store | smithy | chapel | windmill | granary |
              coach_house | coach_office | home   (33 doors; rect = the door tile; stand on the tile below)
  station     kind=stagecoach, label="Plains stagecoach"
  quest_board the notice board
  npc_spot    role = innkeeper, shopkeep, smith, priest, child, coachman, stablehand, miller, elder,
              farmer (x3), merchant (x3)
  exit        dir = N/S/E/W (+ kind=field_track on the minor tracks)

Known weak spots
  - Every building faces south (the Ninja house art only has front views); orientation variety is faked with
    setbacks and yards. Buildings south of a lane have their backs to it.
  - The windmill rise is a rectangular plateau (one cliff row), it reads as a raised lawn more than a hill.
  - Animals are static single-frame tiles; they should become animated actors later.
  - The grain hall, inn, coach house and chapel are composites stretched from 4x3 houses; fine at game zoom,
    a little repetitive in the window rows up close. The stagecoach is a new drawn sprite (Steve to approve).
  - Collision is per tile type and shared with Havenbrook: two tiles (store door, grain sack) keep Havenbrook's
    collision instead of what this town asked for; harmless.
