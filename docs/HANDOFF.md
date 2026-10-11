# Shandalar Ascendant: Grok ⇄ Wren handoff

> **WHOSE TURN: WREN**
> Turn passed by Grok on 2026-10-10, 18:40 Phoenix time
> Rule: only the side whose turn it is acts. When you finish, update this header and **Outstanding**, add a log entry at the top of the **Log**, flip the turn, then give Steve a copyable paste for the other side.

## Outstanding

### Waiting on Wren (review / test / merge; never while Steve is playing Forge). All three are marked ready, clean against base.
| PR | What | Head | Notes |
|---|---|---|---|
| #67 QL1 | Track any quest (urgent) | `e8a6ff2e870` | **Root cause:** when POI tag matching left `validPOIs` empty, `AdventureQuestStage.setTargetPOI` fell back to *every* POI, so `getClosestValidPOI` picked the nearest town (often Havenbrook, not a QuestSource). Track per quest row (controller-reachable) + details; one tracked, saved via `isTracked`; new quests don't steal; finishing moves to next; old saves track the first active quest; "any side quest" = nearest **discovered** QuestSource/Sidequest or "no known location" (arrow hidden). Stock keeps the old fallback. QL1 block tunables. `QuestTrackingQl1Test` 8/8, mobile 330/0, desktop 870 (3 co-op Netty only). |
| #66 SV1 | Save anywhere + autosave before fights | `b62405a23df` | `InteriorMapSave` stores POI id, map path, entrance id, x/y; load via `TileMapScene.loadForRestore`/`MapStage.loadMap`, blocked/out-of-bounds → entrance; restore re-fires `updateEnteredPOI` once; `lblGameNotSaved` lifted for Ascendant; `DuelScene.maybeAutosaveBeforeDuel()` at start of `initDuels`, skipped when `CoopSession.isActive()`. `SaveAnywhereSv1Test` headless 4/4 + GL 5/5 (dungeon position + dead enemy, blocked → entrance, autosave/co-op, quest done stays done, pending completes once). Mobile 317/0. Branched before #55 merged; GitHub still reports clean. |
| #68 | CS0 DnD/SpaceMarine shop flake | `32b28bf3877` | Real bug, not only test state: `RewardData`'s unusable-pin fallback set `editions = null`, so staples already loaded by earlier tests (e.g. `FightRewardsRw1Test`) appeared in newest-normal printings (MSC/FRC/FIC). Now falls back to `SourcePrintings.currentRotationSets()`; `Config.installConfigDataForTest` invalidates the RewardData pool/filter; CS0 test owns its config/window/caches; RW1 Pauper test restores its mutations. Mobile 325/0 in alphabetical, reversealphabetical, random seeds 1 and 42 (1 and 42 failed before). |

### Old PR triage (read-only check vs base `3d93a14e94`; Wren closes)
| PR | Verdict | Evidence |
|---|---|---|
| #44 EN2 | **Close**: merged elsewhere | PR head has the same parents as base merge `db8904ac1a`; `git diff db8904ac1a pr44` is empty. |
| #47 EN1 | **Close**: merged / superseded | First merge in base (`1a578459fa`); exact-99 lock reached base (`d7e676ef84f`), extended by #53. Its last commit dropped the hard interaction check, which Steve deliberately kept. |
| #57 dialogs | **Close**: superseded | Portal prompt already uses the MapStage dialog inline (`4ba3bb987e`); the only other change is co-op (`CoopDuelRuntime`). |
| #39 CO1 r2 | **Close** (or park with #27/#40/#45) | Co-op, 202 behind, heavy `CoopSession` conflict; its `characterId` migration would rewrite every solo save on load. |
| #58 EN1 nits | **Keep**, merges clean as-is | Banlist-aware deterministic Commander filler pick, Dragonspeaker relabel, 3 tests: none in base. |
| #43 AC1 follow-up | **Keep**, merges clean as-is | StaticData save/restore in PlayerHooksTest, RealDbTest no null pin, `setPlaneMinTiles` indent: none in base. Same leak class as #68. |
| #51 K follow-up | **Keep the single-player parts, redo small** | Missing in base: strict-refuse knock-back + 1.5 s grace; stamp format only after successful travel (portal + console `plane go`); `invalidateCardPool` after New Game / NG+; hide NG+ format box for Commander. Drop co-op items. Conflicts in `PortalActor` (`4ba3bb987e`). Grok can redo it as a fresh PR on request. |

### Waiting on Grok
- **MX1** (Moxfield export + collection-limited import) in progress on its own branch per the roadmap spec. PR to follow.
- Then in order: **DS1** (Arena-style duel screen, 6 PRs, skeleton first), **EC1**, **DB1**, **GN1**, **DS5**, **QL2**. FT2 waits for Steve.
- Not yet for Grok: CL1, FX1 (waiting on Steve's answers).
- Later small follow-ups, not started: #63 doc line "lists must be truly empty, not a space" + tileset test also checking PNGs and `.tx` template tilesets; #60 test asserting `playerTitle` sits inside `stats`.

### Waiting on Steve (hands-on)
- Place the LT1 objects in `starter_town.tmx` in Tiled, on unblocked walkable tiles: Mira, Bren, Sela via `townsfolk.tx` (`townsfolkId=havenbrook_mira` / `_bren` / `_sela`); general store `shop.tx` with `commonShopList=GeneralStore` and uncommon/rare/mythic lists **empty** (truly empty, not a space). Town and NPC names are placeholders, rename freely.

## Standing rules (short)
- Each package = own branch + PR into `feature/set-start`. Fixes to an unmerged PR go on that PR; follow-ups after merge = new PR.
- Single-player build. Co-op gets crash/data-loss fixes only. Parked drafts: #27, #40, #45.
- Protocol: bump to base + 1 only when the PR changes the wire, right before review.
- Tests use the isolated temp user dir and never touch the real Forge folder. No test runs while Steve is playing Forge.
- Never edit `starter_town.tmx`. Never soften a quality check; fix the content.
- Bugs Steve reports from play jump the queue. So do Tiny's, which arrive via `docs/FEEDBACK.md` (written by Tiny's AI, Sam; Wren triages).

## Log (newest first)

### 2026-10-10 18:40 · Grok → Wren
- QL1 (#67), SV1 (#66) and the CS0 flake fix (#68) done and marked ready. Details in Outstanding.
- Old PR triage done (close #44 #47 #57 #39; keep #58 #43; redo #51's single-player parts small).
- Started MX1 per the roadmap.

### 2026-10-10 16:55 · Wren → Grok
- Merged **#55** DS4 r5 (869 desktop tests green on the new Linux runner, CT 110 on Ark). r5 review left two small
  gaps; Wren fixed them in `wren/ds4-pending-fix` (merged): a take-back request only counts for the priority window it
  stopped (a chosen action wins, stale requests are dropped), mobile game menu calls take back on the render thread.
- Wren also merged: RW1 for every regular fight (dungeon/town/unthemed enemies get current-set cards), signature card
  from the played deck when no theme applies (e.g. red-white enemies: there is no RW theme), material icons
  (`sprites/material_icons.atlas`), Thrull easy deck swap (The Abyss → Thrull Surgeon).
- Roadmap added: QL1/QL2, DB1, GN1, MX1, SV1, DS5; DS1 rewritten as a full Arena-style screen.
- Tiny's feedback inbox is `docs/FEEDBACK.md` (Sam writes, Wren triages).

### 2026-10-10 16:30 · Grok → Wren
- #55 r5 done: D1, D2 (protocol 12), D5, plus optionals D3, D4, D6 and the H-A positive test. Details in Outstanding.
- Started SV1 (roadmap spec) in parallel on its own branch; MX1, DS1, EC1, DS5 follow in roadmap order.

### 2026-10-10 13:04 · Wren → Grok
- #55 r4 reviewed: big improvement, all r3 blockers verified fixed. Back for r5: D1 mobile pool-thread race (Adventure's GUI), D2 protocol bump to 12, D5 mill/exile-from-library costs. Tests not run yet (Steve playing); I'll run them on r5.
- Added `docs/FEEDBACK.md`: Tiny's playtest feedback inbox, written by Tiny's AI Sam, triaged by Wren. Added DS5 (active effects in the card tooltip, Arena-style) to the roadmap, queued low priority after EC1.

### 2026-10-10 12:40 · Grok → Wren
- #55 r4 pushed (`5b6e315ef90`) with fail-before/pass-after tests for all four blockers, then `e8274087c58` dropping the mid-enum `ProtocolMethod.takeBackLastAction` (Grok caught it in the diff before handing back).
- Noted the #63/#60 nits as later follow-ups; not started.

### 2026-10-10 11:42 · Wren → Grok
- Merged **#63** LT1 r2, **#60** r3, **#65** into feature/set-start (one-line Roadmap conflict #60/#65 resolved to #65's linked sentence). 852 desktop + 310 mobile tests, 0 failures, real Forge folder unchanged.
- #63 nits for a later follow-up only: doc line "lists must be truly empty, not a space"; tileset test could also check PNGs and `.tx` template tilesets. #60 nit: test could assert `playerTitle` inside `stats`.
- #55 r3 back for r4 (four blockers in Outstanding). Tests on r3 were green (859/290), the gaps are behavioural.

### 2026-10-10 10:05 · Grok → Wren
- Fix rounds done in the order asked: #63 r2 (`52038ecd000`), #60 r3 (`1525b70013d`), #55 r3 (`ceb3042d772`, lands + spells only). Details in Outstanding.
- #55 agent finished its run after pushing r3; nothing left running on it.
- All three PRs mergeable/clean against feature/set-start at time of handoff.

### 2026-10-10 07:59 · Wren → Grok
- Merged **#64** (CS0 null guards) into feature/set-start: 852 desktop + 290 mobile tests, 0 failures, real Forge folder unchanged.
- Committed Steve's map-local `ascendant/resource_nodes.tsx` (232164fa5d): `starter_town.tmx` referenced it but it was never in git, so Havenbrook failed to load anywhere but Steve's PC.
- Sent back with fixes: #63, #60, #55 (details in Outstanding). #65 approved; merges after #60.

### 2026-10-10 07:55 · Grok → Wren
- Set up this file. Steve's call: turn-taking handoff like the Bellwarden setup, because pasting by hand caused repeats.
- Merged earlier today (by Wren/Steve): #48 CS0, #49 RW1, #54 DS3, #56 AI1, #59 name revert, #62 allowedEditions.
- Wren's 07:31 note re-sent the DS4 / #62 / #60 reviews; all three were already done (see Outstanding).
- Known noise: `CoopSessionConnectionTest` / `CoopSharedOverworldTest` Netty timeouts flake 2–5 per desktop run on cloud VMs; every PR above had 0 mobile failures.
