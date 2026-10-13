SWAMP TOWN  (Shandalar Ascendant, black/swamp biome template town)                        2026-10-13, Wren
==========================================================================================================

Files
  swamp_town.tmx              70x50 tiles, 16px. Layers: Background, Ground, Foreground (spriteLayer=true, collision
                              via per-tile collision in the tilesets), AboveSprites (roofs, canopies, moss, lantern
                              heads, wisps, mist), Objects. Map properties: biome=swamp, townKind=swamp_town, seamless=true.
  swamp_town_preview.png      clean render, 2000px wide, with a light "mood" pass (10% green-grey murk tint + soft
                              vignette, generator/mood.py). The tiles themselves are not tinted.
  swamp_town_debug.png        2x render, no mood pass: collision rects (red), object rects + labels, tile grid numbers
  swamp_town_in_context.png   the town placed at (35,25) in a 140x100 swamp patch (bog pools and hummocks, reed
                              beds, cypress and dead trees, firmer ground west where the causeway goes, the north
                              boardwalk running on over the water, the skiff channel continuing south, a distant
                              ruin on a hummock in the north-east). Mood pass applied.
  swamp_context.tmx           the TMX behind that render (illustration only, no collision/objects authored)
  tileset/ninja/              tilesets the TMX uses (sources "tileset/ninja/<name>.tsx", relative to the TMX)
  generator/                  python 3 + Pillow. generator/go.sh [OUTDIR] rebuilds everything:
                                town.py          generator (the port town.py: same helpers/writers; deep water now
                                                 from euclidean distance with a wobbly threshold; tile classes written)
                                swamp_layout.py  the hand layout (exec'd by town.py)
                                swamp_extras.py  builds ninja_SwampFloor / SwampWater / SwampExtras
                                draw_swamp.py    the newly drawn sprites (see below)
                                draw_swamp2.py   revision 2 drawings (lift-net, battlefield, square furniture,
                                                 lamplight pools)
                                draw_port.py     unchanged from the port (bench, table, net pile, cat frame, ...)
                                context.py       the 140x100 swamp patch
                                render_town.py   renderer (unchanged)
                                mood.py          preview tint + vignette (render only)
                                check.py         reachability: on foot from the west causeway exit, and by skiff
                                                 from the station to the skiff-channel exit

Tilesets
  Copied from the repo (A:/forge_wave3 maps/tileset/ninja/), collision included:
    ninja_TilesetNature, ninja_TilesetHouse, ninja_TilesetElement, ninja_TilesetFloorDetail,
    ninja_TilesetVillageAbandoned (mossy cottages, the witch's A-frame, ruined wall pieces)
  ADDED per-tile collision (merge into the repo .tsx when moving the map in):
    VillageAbandoned +24 tiles (it had none), Nature +1 (stump), Element +1.
  Generated (CC0-derived from Ninja Adventure art plus new drawings):
    ninja_SwampFloor   = TilesetFloor, grass -> swamp olive, dirt autotile -> mud
    ninja_SwampWater   = TilesetWater, water -> murky teal-brown, shore -> mud bank with a pale scum line
    ninja_SwampExtras  = everything else (all collision in its .tsx)
  Tile classes (Tiled "type" on the tile): full-water tiles = "water", wading tiles = "wade". The later stage can use
  them for skiff navigation and slow wading. Full water is solid on foot.
  To put the map in the repo at maps/map/ascendant/: change each source to ../../tileset/ninja/<name>.tsx, copy the
  three generated tilesets there, and merge the added <tile> entries into VillageAbandoned/Nature/Element.

Layout (tile coords x,y; origin top-left)          Revision 2 (2026-10-13): fishing platform, zigzag
                                                    boardwalks, bigger battlefield, furnished square, warmer light.
  Terrain       All water except hummocks: causeway bank (x0..13, y23..27), the landing green (centre-west, about
                x11..31 y16..31), NW hummock (x1..11 y15..22), the rise (north-east, x42..68 y0..18, runs off the north
                edge), the mudflat + smokehouse hummock (east, x50..70 y21..36), the witch's knoll (x0..9 y41..48).
                Shallow murk near land, deep murk further out.
  Boardwalks    No long straight runs: every walk jogs a tile or two, narrows to one plank and widens again
                (rects listed in swamp_layout.py, "boardwalk streets"). North boardwalk x20..22 y0..16 (jogs west
                at y3..10); street A west y11..13, east y11..14; street C y19..22 with a rope bridge x35..39 y21;
                street B y40..42 down to the wharf x39..47 y41..42; the green -> B link jogs x18..20 y31..42.
                Side jetties to houses standing alone on their own stilts: (17,7..10) to h_l1, (32..33, 5..12) to
                h_l2, and an L-jetty (36,43..48) to h_b5 south of street B.
  FISHING       The centrepiece in the middle of the water: plank deck x33..40 y25..29 on stilts (row 30), reached
  PLATFORM      from the rope bridge by a jetty (37,22..24) and from street B by a zigzag walk (x38..39, y30..41).
                A counterweighted lift-net (carrelet): A-frame on the deck (feet at (38,26),(39,26), solid), a long
                boom with a stone counterweight on the short end, the net raised dripping out of the water on four
                bowed bamboo spreaders over x42..47 y28..32, a haul rope to the winch (35,28), eel and crayfish traps
                hanging from the boom, a lantern at the pivot. One 15x14 sprite, origin (34,21), on AboveSprites
                (the feet on Foreground). On the deck: baskets, traps, crates, net pile, barrel, two deck lanterns.
                Moored skiffs at (32,25..27), (34..36,31) and (41..43,34). npc_spot eel_fisher at (36,27), (39,28).
  Wade rects    wade_witch x9..11 y41..43 (B to the witch's knoll), wade_rise x37..42 y13..14 (end of A to the
                rise), wade_mudflat x61..63 y17..21 (rise to the mudflat). Objects type "wade", kind=shallows.
  Exits         exit_west_causeway (0, 25..26) dir W kind=causeway (the road to dry land; BFS start)
                exit_north_boardwalk (21..22, 0) dir N kind=boardwalk
                exit_south_skiff_channel (44..47, 49) dir S kind=skiff_channel (water exit, checked by skiff)
  STATION       Skiff landing: jetty x45..46 y43..47 off the wharf, three skiffs moored (42..44,43), (42..44,45),
                (47..49,44). Object 'station' kind=skiff label "Skiff landing", rect x42..49 y42..47. SKIFFS sign on
                posts (43..44, 41..42). Poleman's hut on legs x48..50 y36..40, door (49,40).
  Inn           tavern on stilts x22..27 y37..39, stilts y40, door (24,39); plank terrace y42..43, tables, bench.
  Trading post  x13..16 y17..20, door (14,20).   Apothecary x25..27 y17..19, door (26,19), herb garden x26..30 y23..26.
  Plank square  x16..24 y22..27 (+ x19..22 y28..30): notice board (quest_board) x18..20 y22, rain cistern
                (22..23, 22..23), two market stalls (16..17, 22..23) and (23..24, 25..26), a table with two stools,
                a brazier, a handcart, crates, barrels, an eel basket, a bench, lanterns at both sides.
  Graveyard     on the rise: iron fence x48..62 y5..14, gate y14 x54..56; crypt x53..57 y2..5, door (55,5)
                (interior=crypt); 16 graves, a fresh grave, dead trees, candles, lanterns at the gate.
  Chapel        bog chapel x44..46 y9..13, door (45,13), candles each side.  Gravedigger's hut door (44,6).
  Smokehouse    x51..53 y30..32, door (52,32); eel racks (54,30) and (57,31), eel baskets, crayfish traps.
  Battlefield   the whole east of the mudflat, x57..69 y24..35 (mudflat enlarged): scorched ground patches, a
                broken palisade of sharpened stakes along y26..27 with gaps and a fallen section, a ruined stone
                wall (66..69, 32..34) and a low ruin (62..64, 34..35), a toppled catapult (59..62, 30..32), a cart
                on its side (64..66, 29..30), five torn war banners on poles (two with a crow perched on top),
                six swords driven into the mud at angles and a spear, breastplates, shields, a helmet, bones and
                skulls, crows on the ground, dead trees. Hunter's house door (63,24).
  Witch         mossy A-frame x2..5 y39..43, door (3,43); cauldron, warding skull post, bottles, glowing
                mushrooms, black cat.
  Homes         20 doors interior=home: street A 5, lone stilt houses 3, street C 2, street B 4, the green 2,
                NW hummock 2, gravedigger, hunter. Plus poleman.
  Light         every lamp (boardwalk lantern posts about every five tiles, deck lanterns, the brazier, the pivot
                lantern) has a stronger, warmer glow and a 3x3 pool of warm light on the ground or water under it.
Objects: 28 doors (inn, store, apothecary, witch, smokehouse, chapel, crypt, poleman, 20 home), 1 station, 1 quest
  board, 13 npc_spots (innkeeper, apothecary, shopkeep, witch, poleman, gravedigger, priest, eel_fisher x4, child,
  hunter), 3 exits, 3 wade rects.
  Reachability check: 49 objects, 0 unreachable on foot; station -> skiff-channel exit OK by skiff.

Newly drawn or composed (Steve to approve)
  Drawn: stilt rows (posts, braces, ripples, deck shadow) and plank steps for every stilt house, rope-bridge span,
  wading shallows, bald cypress trees (3x4 and 2x3) with Spanish moss and knees, moss on dead trees, reeds/cattails,
  lily pads, mushrooms (incl. glowing), wisps, fireflies, mist banks, lantern posts with glow, deck lanterns,
  candles, skiffs (flat punts, three variants), crypt, bog chapel, smokehouse, eel racks, eel basket, crayfish
  trap, rain cistern, cauldron, bottles, skull warding post, herb bunches, iron fence, six grave types, war banners,
  rusted swords, helmet/shield, swamp signs (SKIFFS, INN, APOTHECARY, TRADING POST, SMOKEHOUSE).
  Revision 2, drawn: the lift-net apparatus (A-frame, boom, stone counterweight, bamboo spreaders, net with the
  catch, haul rope, hanging eel and crayfish traps, pivot lantern), winch, lamplight pool, scorched ground, palisade
  stakes (whole, broken, fallen), toppled catapult, overturned cart, war banners, swords driven in at angles, spear
  with pennant, breastplate, two shields, bones, skulls, crows (ground and perched), brazier, stools, handcart,
  crate stack; market stalls recoloured from the Ninja desert stall.
  Composed/recoloured: house roofs in moss/bark/plum/pine with nailed patches and weathered walls, tavern (port
  tavern with a mossy roof), apothecary (Ninja shopfront, plum, potions on the board), trading post (porch shop,
  pine roof), poleman's hut (VillageAbandoned stilt hut, weathered), boardwalk planks (Ninja planks, grey timber).

Known weak spots
  - Houses all face south, so every ladder comes down on the south side; lone houses are reached by straight or
    L-shaped side jetties.
  - Stilts are one tile tall: readable in the preview, modest up close.
  - Wade patches are rectangles with a hard edge against the murk.
  - The lift-net is one big static sprite on AboveSprites: the player walks under the boom, and the net cannot be
    raised or lowered yet (an animated actor later). The counterweight stones read a little like a grey cloud.
  - Scorch patches are subtle on the swamp grass.
  - The landing green's south yard is still a plain mud patch.
  - Skiffs, wisps, fireflies, frogs and the cat are static frames; they should become animated actors later.
  - The context patch is a little island-heavy, and its hummock edges are blocky from the smoothing.
