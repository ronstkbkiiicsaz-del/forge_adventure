PORT TOWN  (Shandalar Ascendant, blue/islands biome template town)                         2026-10-13, Wren
==========================================================================================================

Files
  port_town.tmx               70x50 tiles, 16px. Layers: Background, Ground, Foreground (spriteLayer=true,
                              collision via per-tile collision in the tilesets), AboveSprites (roofs, sails, canopies),
                              Objects (object layer). Map properties: biome=islands, townKind=port_town, seamless=true.
  port_town_preview.png       clean render, 2000px wide
  port_town_debug.png         2x render with collision rects (red), object rects + labels, tile grid numbers
  port_town_in_context.png    the town placed at (35,25) in a 140x100 island patch: open sea S and E, a wild islet
                              (no dock) to the south-east, the road and both coast paths continuing out of town
  port_context.tmx            the TMX behind that render (illustration only, no collision/objects authored)
  tileset/ninja/              tilesets the TMX uses (sources are "tileset/ninja/<name>.tsx", relative to the TMX)
  generator/                  scripts (python 3 + Pillow). generator/go.sh [OUTDIR] rebuilds everything:
                                town.py          generator (the plains town.py with a sea autotile, piers, quay wall
                                                 and terrace walls added)
                                port_layout.py   the hand layout (exec'd by town.py)
                                port_extras.py   builds ninja_PortFloor / PortWater / PortExtras
                                draw_port.py     the newly drawn / composed sprites (see below)
                                context.py       the 140x100 island patch
                                render_town.py   renderer (unchanged from plains)
                                check.py         reachability check (BFS from the north road exit; a door counts when
                                                 the tile below it is reached, other objects when a tile in or
                                                 touching their rect is reached)

Tilesets
  Copied unchanged from the repo (A:/forge_wave3 maps/tileset/ninja/), collision included:
    ninja_TilesetNature, ninja_TilesetHouse, ninja_TilesetElement, ninja_TilesetFloorDetail,
    ninja_TilesetInteriorFloor, ninja_TilesetDesert (palms)
  ADDED per-tile collision for this town (merge into the repo .tsx when moving the map in):
    TilesetNature +6 tiles (grey rocks, trees, a bush), TilesetElement +3, TilesetDesert +4 (palm trunks).
    Element (8,2) and Nature bushes (0..2,10) keep the repo's existing collision.
  Generated (CC0-derived from Ninja Adventure art, plus a few new drawings):
    ninja_PortFloor   = TilesetFloor, grass -> world-gen blue-biome greens, dirt autotile -> world-gen beach sand
                        (every lane in the port is a sandy path; no dirt/sand seams)
    ninja_PortWater   = TilesetWater with world-gen blue-biome water, sand shore and grass (+ shore collision by
                        quarter, full water solid). Plank and rowboat tiles kept in their original colours.
    ninja_PortExtras  = deep-water autotile (16 transitions + ripple), pier planks composited over shallow and deep
                        water (walkable, no collision), stone quay wall over water, terrace wall (grass bank, stone
                        face) + stone steps, pale quay paving, roof recolours (blue/teal/slate/navy) of the 4x3
                        houses, whitewashed cottages, slate gable house, whitewashed round house, and the buildings,
                        boats, props and signs listed below. All collision is in its .tsx.
  To put the map in the repo at maps/map/ascendant/: change each source to ../../tileset/ninja/<name>.tsx, copy the
  three generated tilesets there, and merge the added <tile> collision entries into Nature/Element/Desert.

Layout (tile coords x,y; origin top-left)          Revision 2 (2026-10-13): hill village pass, see below.
  Levels        The town climbs a hill north from the harbour in three levels, split by two CURVED terrace walls
                (grass bank, stone face, solid) that follow the slope and step a row at a time, bulging back
                around the basin:
                  W1 (lower wall)  x0..58, row 26 at the west edge -> 20 behind the quay -> 22 at the east end
                  W2 (upper wall)  x0..58, row 13 at the west edge -> 8 at the main road -> 12 at the east end
                  (control points: W1 and W2 in port_layout.py; rows at any x come from interp())
                lower town   below W1: sand, the quay, quay wall y33, harbour basin below
                middle tier  between W1 and W2: a 1-tile sandy walk along the top of W1, cottages in staggered
                             clusters at different heights, stepped stone alleys between them
                upper tier   above W2: a walk along the top of W2, chapel, four cottages, two crop fields
  Steps         W1: x4-5 (coast path), x13 (boatyard), x31-32 (main road), x56 (headland).
                W2: x9, x19, x41 (alleys of stone steps that climb between the cottages), x30-31 (main road).
                Further stepped alleys to the upper cottages of each cluster at x4, x24, x46.
  Sea           Harbour basin from the quay wall (y33) with an irregular west shore (x12..17) and rocks; open
                sea along the south edge (x9..69, y44..49, ragged) and the east edge (x65..69); a cove at
                x63..64 y11..18. The lighthouse point narrows to its tip (rows 38..41), with rocks on it and in
                the sea around it. Shallow water near every shore, deep water further out.
  Exits         exit_north_road (30-31, 0) dir N: the one road inland (2 wide), it bends at the well square and
                steps through both walls to the quay. coast_path_south (4-5, 49) dir S kind=coast_path.
                coast_path_north (61-62, 0) dir N kind=coast_path.
  STATION       Ferry pier: stem x34..36 y33..41, T-head x30..40 y42..43, gangway (35,44-45), the ferry moored
                at x32..37 y44..47. Object 'station' kind=dock label "Port ferry", rect x30..40 y42..47.
                FERRY sign on the quay (37-38,31-32); the harbourmaster's office stands at the head of the pier.
  Quay          Stone slab broken up: stone x17..55 y30..32 (narrow at both ends), y29 x20..52, y27..28 only
                in front of the store/harbour (x25..39) and the warehouse (x48..55); wooden boardwalks at
                x17..23 y27..28 (tavern terrace) and x40..47 y27..28 (fish market). Nets, crates, barrels,
                fish baskets, rope coils, lobster pots, bollards, lamps, a crane, cats, gulls.
  Harbourmaster x33..37 y23..26 (5x4, two storeys, blue roof, flag, HARBOUR sign), door (35,26).
  Jetties       fishing jetty x22..23 y33..41 (two rowboats moored bow-in); east jetty x46..47 y33..40 with the
                fishing smack alongside x48..51 y35..37.
  Inn/tavern    x18..23 y24..26, door (20,26); tables and a bench on its boardwalk terrace.
  General store x26..29 y23..26, door (27,26).
  Fish market   fishmonger x39..41 y24..26 door (40,26); drying racks (42-45,22-23), net rack (46-47,22-23),
                FISH MARKET sign (43-45,25-26), stalls on the boardwalk (41-42, 44-45, y27-28) and the quay (37-38,29-30).
  Warehouse     x48..55 y23..26 (8x4), door (51,26); crates and barrels; crane on the quay edge (52-54,30-32).
  Boatyard      shipwright's workshop + lean-to x6..10 y27..29, door (7,29); BOATYARD sign; hull on a cradle
                x9..14 y33..35 on slipway rails (x8..18, y35..36) running into the basin; crane, net rack, timber.
  Lighthouse    on the rocky point x59..61 y30..37 (3x8), door (60,37); keeper's cottage x57..60 y24..26.
  Chapel        chapel of the sea x35..37 y0..4, door (36,4); bronze-anchor shrine statues at (33,3) and (39,3).
  Well square   small flagged square x32..36 y9..14 on the middle tier: roofed well (34,12-13), notice board
                (33-35,10) = quest_board, bench.
  Homes         22 cottages, doors marked interior=home: middle tier 14 in three clusters (west x0..13,
                centre-west x15..28, east x37..50), each a lower row on the walk plus an upper row reached by
                stepped alleys; upper tier 4; west arm 3 (on the coast path); lighthouse keeper 1.
                Fenced vegetable gardens, washing lines, lobster pots, trees and bushes fill the gaps.

Objects (layer "Objects", all type + string properties)
  door        interior = inn | store | harbourmaster | fishmonger | warehouse | shipwright | lighthouse | chapel |
              home   (30 doors; rect = the door tile; stand on the tile below)
  station     kind=dock, label="Port ferry"
  quest_board the notice board on the well square
  npc_spot    role = harbourmaster, fishmonger, sailor (x2), shipwright, innkeeper, shopkeep, lighthouse_keeper,
              child, fisher (x3), dockhand (x2), priest, merchant   (16)
  exit        dir N/S (+ kind=coast_path on the two coast paths)
  Reachability check: 51 objects, 0 unreachable (BFS from the north road exit).

Newly drawn or composed (Steve to approve)
  Drawn from scratch: lighthouse, chapel of the sea, fishing smack, hull on the cradle (Ninja Boat recoloured with
  bare ribs drawn), slipway rails, fish drying rack, lobster pots, bollard, anchor, bronze-anchor shrine, buoy,
  gulls, tavern table, bench, net piles, wicker fish baskets, harbourmaster's office details (windows, beam, flag), warehouse walls and doors,
  shipwright's lean-to, deep-water transitions.
  Composed from Ninja art: ferry (Ninja Boat + two Ninja Sails), net rack (Ninja FishNet on posts), crane (Ninja
  Crane), fish stalls (Desert stalls recoloured + Ninja fish/squid/shrimp/octopus items), fish crates, the tavern
  (the lantern shopfront widened, slate roof), fishmonger (shopfront with a fish board), store (porch shop, slate
  roof), flagpole (Ninja blue flag), cats (single frames), upturned rowboat.

Known weak spots
  - Every building still faces south (Ninja front views only). The slope is shown with the curved, stepped
    terrace walls and staggered cottages; where a wall steps a row the two runs meet diagonally (end caps).
  - Stepped alleys are a repeated stone-step tile; long ones read a little like ladders.
  - The lower town just under W1 is still an open sandy street (behind the waterfront buildings).
  - The quay wall itself is still one straight line (y33); the softening is in the basin's sides, the point
    and the slab above it.
  - The ferry and smack are drawn top-down while their sails are in perspective (matches the Ninja Boat/Sail art).
  - Boats, gulls and cats are static single frames; they should become animated actors later.
  - Context render: deep/shallow shading can step slightly at the town's border where the two scripts meet.
