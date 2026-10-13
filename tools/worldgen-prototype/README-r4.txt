WG1 round 4 - islands only (mainland logic unchanged; seed 1)
=============================================================
Rule: docks only where people are. Islands are settled biggest-first until INHABITED_AREA_SHARE of all
island area is inhabited; every inhabited island gets harbour settlements (each with its own dock: pier +
hut + boat at the settlement's shore) and 1-2 island-only landmarks. Everything else is a wild island:
no dock (needs own boat / glider / flier / swim), one rarer landmark.

IMAGES
  overview_r4_seed1.png                 whole plane; "W" ring = wild island
  mid_r4_seed1_archipelago.png          400x250 tiles around the main inhabited island
  closeup_r4_seed1_island_port_town.png port town (51x45) with its harbour dock across the bay
  closeup_r4_seed1_wild_island.png      wild island (1815 tiles) with a ruin, no dock

KNOBS (profiles\home_shandalar.json -> "islands")
  INHABITED_AREA_SHARE 0.65 | TOWN_MIN_AREA 5000 (port town possible) | VILLAGE_MIN_AREA 900
  port_town 40-60 x 30-45 | village 20x14 (falls back to hamlet 15x12)
  AREA_PER_EXTRA_SETTLEMENT 6000 | MAX_SETTLEMENTS_PER_ISLAND 5 | SETTLEMENT_SPACING 70
  HARBOUR_MAX_SHORE_DIST 8 (settlement footprint must reach within 8 tiles of the sea)
  inhabited_landmarks lighthouse/shrine/shipwreck/sea_cave/pirate_cove, 1-2 per inhabited island
  wild_landmarks ruin/treasure_cove/rare_resource, 1 per wild island

MEASURED (seed 1)
  islands 32: inhabited + docked 6, wild 26; inhabited share of island area 65.2% (151,713 island tiles)
  island settlements 13: port towns 5, villages/hamlets 8; island harbour docks 13
  every inhabited island walkable 100% from its dock; no island reachable on foot from the mainland
  wild islands: landmark reachable within the island (fix-up pass)
  mainland: blocked 21.7%, reachable 100%; generation 11.7 s
