WG1 round 3 - PlaneGenerator prototype at real scale (home plane, 2000x2000 tiles)
=================================================================================
Reference for the Java PlaneGenerator (forge-ascendant). Same algorithms, knob names and numbers.

IMAGES (2 seeds)
  overview_r3_seed{1,2}.png   whole plane, 1 px per tile (2000 px), legend top-left
  mid_r3_seed{1,2}_*.png      400x250 tiles at 5 px/tile: havenbrook, farmland, archipelago,
                              mountains_volcano, swamp (buildings in dark red, icons for stations/sites)
  closeup_r3_seed{1,2}_*.png  100x60 tiles of Ninja Adventure art at 2x:
                              havenbrook (true-size, rendered from havenbrook.tmx, road out south),
                              stagecoach_farmland, island_dock_village, caravan_mountain_pass

RUN (src\)
  python -I run3.py 1 2      generate planes -> r3_world_<seed>.pkl + stats   (~11 s per plane)
  python -I render3.py 1 2   overview + mids + close-ups                       (~2 s per plane)
  Code: planegen\core.py (codes, noise), planegen\generate.py (pipeline), planegen\sites.py (footprint
  builders), profiles\home_shandalar.json (THE PLANE PROFILE - every knob below lives there).
  Each pipeline step gets its own RNG stream sub_rng(seed, step) -> deterministic, and changing one
  step's knobs does not reshuffle the others.

PLANE PROFILE (profiles\home_shandalar.json) - change by name
  size 2000, chunk 64
  layout: wedges, core_biome wastes, order white/blue/black/red/green,
          R_CORE 200, R_INNER 550, R_OUTER 835, R_COAST 925, BORDER_WARP_ANGLE 0.20,
          BORDER_WARP_RADIUS 100, TRANSITION_BAND 12
  home_town: Havenbrook from havenbrook.tmx (90x60; top 8 backdrop rows skipped -> 90x52 in-world)
  terrain: CLUSTER_SCALE 13, LANE_WIDTH 0.10, LANE_SCALE 90, OUTER_BONUS 0.06, JUNGLE_BONUS 0.06,
           LAKE_SCALE 34, BOG_SCALE 10, RIDGE_SCALE 80, RIDGE_RED 0.88, RIDGE_OTHER_OUTER 0.975,
           RIDGE_GAP_EVERY 0.35, RIVER_COUNT 22 (sources tried; ~11-12 survive spacing/length),
           RIVER_SPACING 230, RIVER_MIN_LEN 140, RIVER_WIDTH 1.6-2.8, FORD_SPACING 40
  sea: SEA_START 330, ISLAND_SHARE 0.30, ISLAND_SCALE 48, MIN_ISLAND 400, PIER_LEN 4
  volcanoes: COUNT 3-4, SPACING 260, CONE_RADIUS 7.5, CONE_TOP_RADIUS 4.3, CRATER_RADIUS 1.8,
             LAVA_FLOWS 2-3, ASH_RADIUS 40
  biomes (density = tree/rock share; lake; station type; house style; per100k = sites per 100k tiles):
    wastes  0.07  lake 0.006  stagecoach  hamlet 5, farmstead 4, stones 2, ruin 2, tower 0.5, cave 0.5, dungeon 0.5
    white   0.06  lake 0.012  stagecoach  town 1.5, hamlet 4, farmstead 16, orchard 3, stones 0.8, ruin 0.8,
                                          tower 0.4, cave 1.2, dungeon 1.5, graveyard 0.4, battlefield 0.3
    blue    0.18  (sea)       dock        town 1.4, hamlet 4, stones 0.8, ruin 1.2, tower 0.8, cave 2, dungeon 2, graveyard 0.3
    black   0.15  bogs 0.14   skiff       town 1.2, hamlet 3, graveyard 3, battlefield 2, ruin 2, tower 1.2, cave 1.5,
                                          dungeon 3, stones 0.8
    red     0.14  lake 0.008  caravan     town 1.2, hamlet 3, farmstead 0.5, battlefield 1, ruin 1.2, tower 1, cave 4,
                                          dungeon 1.5, stones 0.5
    green   0.22  lake 0.02   elfgrove    town 1.2, hamlet 3.5, farmstead 1, stones 1.5, ruin 1.5, tower 0.6, cave 2,
                                          dungeon 2.5, graveyard 0.3
  settlements: town 40-70 x 30-50 (min_dist 150; island towns capped 44x34), hamlet 15x12 (min 60),
               farmstead 8-20 x 8-16 (min 30)
  roads: EXTRA_LINKS_PER_100_NODES 12, WIDTH 2, MAX_LINK 420
  stations: MAX_GAP 200 (no walkable mainland tile further from a station), MIN_SPACING 60,
            SPUR_MAX 90 (road spur to nearest road/town), REMOTE_MAX 80, PASS_CAMPS 8, PASS_SPACING 150
  reachability: MIN_POCKET 40

TRAVEL STATIONS (dial-a-ride, no routes drawn)
  dock (blue: every island + mainland archipelago coast), stagecoach post (white/meadow), caravan camp
  (red, incl. camps at mountain passes), elf-guide grove (green), skiff landing (black, pier into a bog).
  One per town (outside its gate, with a road spur), one dock per island, mainland coast docks every
  ~160 tiles along the archipelago, then gap-filling so no walkable mainland tile is > 200 tiles away.

MEASURED                                   seed 1          seed 2
  generation time                          10.4 s          10.5 s   (+ ~2 s rendering)
  blocked land                             21.6%           21.5%
  blocked incl. inland water               25.9%           26.7%
  mainland reachable from Havenbrook       100% (99.2% before fix-up)   100% (98.0%)
  islands / docked / reachable on foot     32 / 32 / 0     37 / 37 / 0
  island walkable reach from its dock      100% (all)      100% (all)
  sites + stations reachable               100%            100%
  walkable regions with a station          33/33           38/38
  stations dock/stage/caravan/elf/skiff    41/13/17/11/7   47/14/15/10/10
  towns (incl. Havenbrook) / hamlets / farmsteads   29/84/98    29/81/88
  graveyards/battlefields/ruins/stones/towers       16/15/32/23/17   20/18/33/22/20
  caves/dungeons/orchards                  48/46/16        52/48/14
  volcanoes / rivers                       3 / 11          4 / 12
  avg distance to nearest POI (walkable)   36 tiles (90% within 64, max 216)   36 (90% within 64, max 182)
  per-biome blocked %: wastes 9, white 12-14, blue 20-23, black 17-20, red 24-27, green 32-33

GLOBAL vs PER-CHUNK
  Per-chunk-able (pure functions of seed + world coords if noise is coordinate-hashed, as it should be in
  Java): biome layout and blends, lakes/bogs, tree/rock clusters and lanes, ridges, ash, tile art choice.
  The prototype computes these as whole-plane numpy arrays (value-noise lattices), so in Java swap fbm()
  for a hashed lattice noise and they become chunk-local.
  Global passes (run once per plane at creation, results stored as a small "plane skeleton": coastline +
  island ids, river polylines, volcano sites, site list with footprints, road polylines, station list,
  carved paths): sea/islands (connected components), rivers (flow field), volcano/site placement
  (spacing), roads (least-cost paths), stations (distance transform), fords, reachability fix-up.
  A chunk = local noise layers for its 64x64 + stamps of every skeleton feature overlapping it; so chunks
  can still be generated lazily once the skeleton exists (skeleton is ~kilobytes, generated in ~10 s here).
