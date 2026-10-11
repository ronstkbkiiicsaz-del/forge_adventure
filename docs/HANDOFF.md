# Shandalar Ascendant: Grok ⇄ Wren handoff

> **WHOSE TURN: GROK**
> Turn passed by Wren on 2026-10-10, 18:39 Phoenix time
> Rule: only the side whose turn it is acts. When you finish, update this header and **Outstanding**, add a log entry at the top of the **Log**, flip the turn, then give Steve a copyable paste for the other side.

## Outstanding

### Waiting on Wren
- Nothing. (#58 and #43: Wren will re-check and merge after this round.)

### Waiting on Grok (priority order; MX1 continues after these)
Tested together on the Linux runner: 870 desktop + 327 mobile, 0 failures. Code review found:

| PR | Round | Required |
|---|---|---|
| #67 QL1 | r2 | **(1)** `collectSideQuestNavigationPois` (`AdventureQuestStage.java` ~336-366) prefers stage-tag matches over the QuestSource/Sidequest list, then filters to discovered. Ascendant CompleteQuest stages are tagged `QuestSource` (23 POIs) so the 187 `Sidequest` POIs are never candidates, and with no QuestSource visited it shows "no known location" even when a discovered Sidequest exists. Build the **union** of QuestSource + Sidequest (+ tag matches), filter to discovered, pick nearest. This is Steve's actual complaint. **(2)** Controller: `QuestLogScene.buildList()` now calls `clearSelectable()` (~112), dropping Return/Status/Skills/Achievements from `selectActors` (re-add them like `AchievementsScene.build`); `loadDetailsPane` (~227) adds detail buttons without clearing, so hidden list buttons stay selectable. Low (later is fine): `isDiscovered` uses the creating `getPointOfInterestChanges` (adds empty entries to the save) and runs per frame via `drawNavigationArrow`; use a non-creating lookup + cache. HUD line only refreshes on `GameHUD.enter()`. `ensureTrackedQuest`/`trackNextAfterRemoval` not gated by `ql1QuestTracking`; uses `Current.player()` inside load. Unused `lblNoKnownLocation`/`lblTracking` (no-location text comes from config, not localized). |
| #66 SV1 | r2 | **(1) Co-op save path touched.** `InteriorMapSave.write/readPending` run inside `AdventurePlayer.save/load`, so co-op `.chr` saves (`CoopCharacterStore.savePlayer`, `stashGuestSave`) gain interior keys and co-op loads (`CoopCharacterStore.loadPlayer`, `CoopSession` restore ~797) set pending state that `GameScene.enter` (~82-86) consumes: guests could be teleported into a map. Write/read only from `WorldSave.save`/`load` (or skip when `CoopSession` is active) and drop the hasPending branch from `GameScene.enter`. **(2) Missing/renamed TMX:** `prepareRestoredMap` → `loadForRestore` has no try/catch; the loader throws inside the TransitionScreen. Catch, retry the root map at its entrance, else go to `GameScene`. Test it. **(3) Arena:** fee is taken (`ArenaScene` ~156) before every round's `initDuels` autosave, so loading after a loss leaves the fee paid and the arena gone. Autosave once before the fee, not per round. Low: overworld fights now autosave twice (`WorldStage` ~490 + `initDuels`), drop one. Clear pending if `WorldSave.load` fails after `player.load`. Persist `mustClearOnExit`. F8 quickload change (`GameStage` ~500) also alters stock mode and ignores `quickLoad()`'s result. `StartScene.Save` silently no-ops when a dialog blocks it; show a message. Quest tests should go through `sceneAfterLoad` → `TileMapScene.enter`. |

- Then continue **MX1**, **DS1**, EC1, DB1, GN1, DS5, QL2. **Add SN1** (seasons, roadmap "SN1") after DS1, ahead of EC1.
- #51: yes, redo its 4 single-player fixes as a small fresh PR (low priority).

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

### 2026-10-10 18:39 · Wren → Grok
- Merged **#68** (CS0 shop fallback; flaky test fixed for real). Closed #44, #47, #57, #39 per Grok's triage.
- #66 SV1 and #67 QL1 back for r2 (details in Outstanding). Tests were green; the issues are behaviour and co-op.
- Wren merged earlier this evening: Spawn always placed first (new Ascendant worlds could skip Spawn and leave the
  tutorial dialog stuck; Steve hit it), loot spread over the Standard window (`rw1NewestSetShare` 0.5), signature
  card from the played deck for unthemed enemies, `tools/make-release.sh` + GitHub releases for Tiny
  (v2026.10.10-2 is current).
- Roadmap: SN1 seasons written (4-set Standard, calendar: 15 min exploring = 1 day, duel = 2 in-game hours,
  season 14 days, opening seasons 7, player picks next set from a shortlist).

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
