"""Generate round-3 planes: python -I run3.py [seeds...]  -> r3_world_<seed>.pkl + printed stats"""
import os, sys, pickle, time
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from planegen.generate import generate, load_profile

prof = load_profile(os.path.join(HERE, "profiles", "home_shandalar.json"))
for s in [int(a) for a in sys.argv[1:]] or [1, 2]:
    P = generate(s, prof)
    P.bmap = None
    if hasattr(P, "home") and "tmx" in P.home:
        P.home_tmx = P.home.pop("tmx")
    ir = [v for v in P.island_reach.values() if v is not None]
    print(f"seed {s}: {P.time}s  blocked {P.blocked_pct*100:.1f}% (+water {P.blocked_incl_water*100:.1f}%)  "
          f"mainland reach {P.reach*100:.2f}% (pre {P.pre_reach*100:.1f}%)  sites reach {P.site_reach*100:.1f}%  "
          f"stations reach {P.station_reach*100:.1f}%")
    print(f"  islands {len(P.island_ids)} min reach {min(ir)*100 if ir else 0:.1f}% docked {len(ir)} on-foot {P.islands_on_foot} "
          f"regions with station {P.regions_with_station}/{P.n_regions}  stations {len(P.stations)}  sites {len(P.sites)}")
    print("  island stats", P.island_stats)
    print(f"  POI distance mean {P.poi_dist_mean:.1f} p90 {P.poi_dist_p90:.1f} max {P.poi_dist_max:.0f}   carved {P.carved} filled {P.filled}")
    print("  timings", P.timings)
    print("  per biome blocked", {k: round(v * 100, 1) for k, v in P.per_biome_blocked.items()})
    for b, c in P.counts.items():
        print("   ", b, dict(sorted(c.items())))
    del P.home_tmx
    with open(os.path.join(HERE, f"r3_world_{s}.pkl"), "wb") as f:
        pickle.dump(P, f, protocol=4)
