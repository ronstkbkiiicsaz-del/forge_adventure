# Towns: one hand-made template per biome

*Steve + Wren, 2026-10-13. Towns are seamless parts of the plane (walked into on the road); building interiors are
instanced (see `Architecture-Brief.md`). Each biome gets one hand-made template town; other towns of that biome
reuse its pieces with variations. Each template needs Steve's approval of a preview render before it is used.*

## Conventions (all towns)

- Ninja Adventure 16px tiles. Layers: `Background`, `Ground`, `Foreground` (sprite layer; collision comes from
  per-tile collision in the tilesets), `AboveSprites` (roofs, canopies), and an `Objects` layer.
- Map properties: `biome`, `townKind`, `seamless=true`.
- Objects, for later stages:
  - `door` with `interior` = inn / store / smithy / chapel / home / granary / ... (each loads an instanced interior).
  - `station` with `kind` = stagecoach / dock / caravan / elf_guide / skiff (the travel station; see the brief).
  - `quest_board`.
  - `npc_spot` with `role` = innkeeper / shopkeep / smith / priest / farmer / child / ...
  - `exit` where a road leaves the town.
- No boundary walls. A town fades into its biome (fields, hedges, docks, stilts...), and roads continue out.
- A reachability check (search from one exit to every object) must pass.
- Generator scripts live with the town so it can be rebuilt and changed.

## Approved

### Plains market town (white) — APPROVED by Steve 2026-10-13 ("perfect")

`tools/towns/plains/`: `plains_market_town.tmx` (70x50), previews, generator (`generator/go.sh`), README.

- A farmland market town on a crossroads with exits N, S, E and W, plus four field tracks.
- Grain hall in a cobbled market square, with a roofed well, stalls and the notice board.
- Stagecoach post (coach house plus booking office and yard) at the west gate.
- Inn, general store, smithy, chapel and churchyard, a windmill on a rise, granary and silos.
- 24 cottages with gardens; livestock pens; orchards and fields at the edges.
- 33 doors, 1 station, 1 quest board, 15 NPC spots, 8 exits.

Known limits:
- All houses face south (the Ninja art has front views only).
- The windmill rise is a flat plateau.
- Animals are single frames (they become animated actors later).
- The stagecoach is a newly drawn sprite.

To move it into the game, follow the steps in its `README.txt`.

### Island port town (blue) — APPROVED by Steve 2026-10-13 ("lovely"), with the fix pass

`tools/towns/port/`: `port_town.tmx` (70x50), previews, generator (`generator/go.sh`), README.

- A harbour town climbing three curved terraces from a cobbled quay, joined by stepped alleys.
- Three piers; the T-head ferry pier is the travel station (`kind=dock`). Rowboats and a fishing smack.
- Inn with a boardwalk terrace, store, harbourmaster, fishmonger and fish-market boardwalk, warehouse.
- Boatyard with a hull on slipway rails; lighthouse on a rocky point; chapel of the sea at the top.
- 22 cottages in staggered clusters; a well square with the notice board.
- 30 doors, 1 station, 1 quest board, 16 NPC spots, 3 exits (inland road and two coast paths).

Known limits:
- Long stepped alleys repeat one tile.
- The quay's front edge is straight.
- Houses face south.
- Boats and animals are static.
- The lighthouse, chapel, boats and hull are newly drawn.

Small "couple of houses and a dock" islands reuse its pieces.

### Swamp town (black) — APPROVED by Steve 2026-10-13 ("i like it"), with the fix pass

`tools/towns/swamp/`: `swamp_town.tmx` (70x50), previews, generator (`generator/go.sh`), README.

- A bayou town of stilt houses on zigzag boardwalks and a rope bridge, over murky water.
- Centrepiece: a stilted fishing platform with a big counterweighted lift-net, eel traps, a winch and skiffs.
- Skiff landing with a poleman's hut is the travel station (`kind=skiff`).
- Inn on stilts, apothecary, trading post with a furnished plank square, smokehouse and eel racks.
- Witch's hut on its own knoll.
- Graveyard on the firm rise with a crypt (`interior=crypt`, a future dungeon entrance) and a bog chapel.
- Old battlefield on the east mudflat: palisade, ruined wall, toppled catapult, banners, swords, bones, crows.
- Moss-hung cypress trees, reeds, wisps, mist, and warm lanterns with light pools.
- `wade` rects mark shallow channels (slow without a skiff).
- 28 doors, 1 station, 1 quest board, 13 NPC spots, 3 exits (causeway, boardwalk, skiff channel).
- The previews have a mood tint; the tiles don't.

Known limits:
- The lift-net is static.
- One-tile stilts.
- Wade patches are rectangles.
- Houses face south.

## Next templates (proposed order)

1. Mining town (red): terraced on a slope, mine entrance, caravan station, forge.
2. Forest treehouse town (green): built in and around big trees, elf-guide station.
