# WG1 spec: walk the home plane, then fight on it (stages 1 and 2)

*Wren for Reed, 2026-10-13. Read `Architecture-Brief.md` first. Goal: Steve can run around a real-scale generated
Shandalar and get a feel for it as soon as possible; then touch an enemy and play a duel.*

## Reference implementation

`tools/worldgen-prototype/` is the approved Python prototype (four rounds of renders reviewed by Steve; images in
`A:\GameAssets\worldgen\r3\` and `r4\`). Port its algorithms and **keep its knob names and default values**.
- `profiles/home_shandalar.json`: the plane profile (every knob). Load it as data in Java; don't hard-code numbers.
- `planegen/generate.py`: the 14-step pipeline, one function per step, each with its own seeded random stream
  (seed + step name). Keep that structure so steps can be tuned independently.
- `planegen/sites.py`: footprints for towns, hamlets, farmsteads, landmarks, stations.
- `README-r3.txt` / `README-r4.txt`: knobs, measured stats, and the split between **global** steps (run once per
  plane: coastline/islands, rivers, volcanoes, sites, roads, stations, fords, reachability fix-up) and **per-chunk**
  steps (biome layout, clumps and lanes, lakes and bogs, ridges, ash: pure functions of seed and coordinates).

## Stage 1: walk the plane

**Generation**
- `PlaneGenerator.generateSkeleton(seed, profile)` runs the global steps once and returns a small `PlaneSkeleton`
  (site list with footprints, road and river polylines, stations, fords/bridges, carved paths, island labels). Save it
  with the plane; regenerate deterministically if missing.
- `ChunkGenerator.generate(skeleton, layer=SURFACE, cx, cy)` builds one 64x64 chunk from per-chunk noise (use
  coordinate-hashed noise, not whole-plane arrays) plus the skeleton features that intersect it.
- Determinism test: the same seed gives byte-identical skeletons and chunks.
- Must match the prototype's guarantees: mainland 100% reachable from Havenbrook; every docked island reachable from
  its dock; no island reachable on foot; blocked land about 20-22%. Add these as tests.

**Plane size and scale**: 2000x2000 tiles, 16 px tiles, Havenbrook (hand-made `havenbrook.tmx`, footprint 90x52 plus
its backdrop strip) at the centre. For stage 1 Havenbrook is **stamped into the plane as terrain only** (houses are
obstacles, doors do nothing yet; interiors come in a later stage).

**Rendering**
- Tiles from the Ninja Adventure tilesets (`forge-gui/res/adventure/common/maps/tileset/ninja/`), using the same
  tile choices and autotiling as `render_tiles.py` (shorelines, roads, biome-tinted grass, tree and rock sprites with
  jitter and overlapping canopies).
- Chunk streaming: keep chunks within ~2 screens of the camera loaded; generate on a background thread, build render
  caches on the GL thread; no hitch when crossing chunk borders (target 60 fps on Steve's PC).
- Draw order: ground, then sprites sorted by Y (player walks behind tree canopies and roofs).

**Movement**
- Smooth 8-direction movement with keyboard, controller and click-to-move; collision against blocked tiles and
  sprite bases (small foot box, not the whole sprite). Use the player sprites from `sprites/heroes/` for now.
- Camera follows with slight easing.

**Minimap and map**
- Minimap corner widget; full map (M key) showing explored area (fog of war), roads, towns, stations you've seen.

**Saves**
- Save = seed + profile id + player position + explored-chunk bitmap. Versioned JSON. Load returns to the same spot.

**Done when**: Steve launches it from a `play-ascendant-next.cmd`, starts a new plane from a seed (or a random one),
walks from Havenbrook to the coast, across biomes and over bridges, opens the map, saves, quits and reloads.

## Stage 2: enemies and duels

- Enemies roam per biome from a small table in the profile (reuse existing Forge Adventure enemy sprites and decks as
  data for now); spawn near the player outside view, despawn far away; density tunable per biome.
- Touching an enemy calls `DuelBridge.start(DuelRequest)` (see the brief): builds `RegisteredPlayer`s and runs a
  `HostedMatch` with **Forge's existing match screen** for now. Returns `DuelResult`.
- Win: a reward screen (gold, cards from the current set); lose: lose some gold, return to the last safe spot.
- Starting deck: a sealed pool from a chosen set (as today) or a test deck; the deck editor can be Forge's for now.

**Done when**: Steve walks out of Havenbrook, gets attacked, plays a full duel, gets rewards and keeps walking.

## Not in these stages

Towns as living places, interiors, gathering, travel stations working, underground and sky, set planes, our systems
(seasons, economy, skills). Those follow in the order in the brief.

## Process

- Small playable slices; show Steve each one. If something feels wrong to him, that wins over this spec.
- Keep the Linux test runner green (`ssh root@192.168.1.110 'forge-test <label> <branch>'`).
