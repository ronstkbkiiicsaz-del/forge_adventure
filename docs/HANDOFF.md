# Shandalar Ascendant: Grok ⇄ Wren handoff

> **WHOSE TURN: WREN**
> Turn passed by Grok on 2026-10-11, 05:45 Phoenix time
> Rule: only the side whose turn it is acts. When you finish, update this header and **Outstanding**, add a log entry at the top of the **Log**, flip the turn, then give Steve a copyable paste for the other side.

## Outstanding

### Waiting on Wren
Four PRs are ready to test and merge. Each was merged with the current feature/set-start (after #69 and #71), conflicts were resolved by keeping every labelled ConfigData/config.json block, and each is MERGEABLE/CLEAN. They touch the same config files, so later ones will need the usual keep-all-blocks resolve as earlier ones merge.

| PR | Head | What's in it | Tests |
|---|---|---|---|
| #70 DS1 (PR 1 of the roadmap plan) | `5b8331ddef8` | `ArenaDuelScreen`: opponent on top, you at the bottom, centre stack column, fanned hand, auto-arranged battlefield (lands, creatures, others; small boards centred), context button plus Pass until EOT, DS4 take back kept, DS3 right-click scope unchanged. Gated by `ModernDuelScreen` + `ds1ArenaDuelScreen`. Classic chrome hidden on Arena (the stray "2/3" bar was the classic VStack dropdown). One backdrop spans the whole play area. Real xvfb renders in the PR (landscape + portrait). Tests: pairwise overlap in both orientations, and hand cards keep card aspect and stay on screen. Later DS1 PRs: targeting/combat, mana/cost views, prompt views, animations with AU1, full pad focus. | mobile 362/0, gl 14/0, desktop 873 with 2 co-op flakes |
| #72 SN1 | `d61cfad2695` | Seasons: 4-set Standard (`sn1WindowSize`), `AdventureSeason` calendar (15 min exploring = 1 day, duel = 2h, inn Rest skips to morning), 7-day opening seasons ×2 then 14-day seasons, mastery ends a season early, shortlist pick with grace auto-pick and undo. HUD, Status, Skills and Inn UI. Old saves default from their rotation. Wired through `StandardWindow`/RW1/CS0. Real renders in the PR; the HUD season line clears the QL1 line and the minimap. Gaps: town criers are HUD notices only, Planar Keys are a counter, mastery cosmetics wait on CS1/AC1. | mobile 361/0, gl 14/0, desktop 873 (co-op flakes, 48/0 on rerun) |
| #73 AU1 | `2d57c42855f` | Sound overhaul: Kenney CC0 `au1_*.ogg` (pack table in the PR and in CREDITS.md), `Au1Sounds` hooks for DS1 animations, counter sound from the engine's `GameEventSpellCountered` (no name heuristics), chest/pickup/UI sounds wired with fire-once tests, UI volume plus muted overworld ambience. Stock sounds unchanged. | mobile 355/0, gl 13/0, desktop 873 (co-op flakes, 13/0 alone) |
| #74 EC1 | `4c513b90e3a` | Card economy: base gold 50/150/300/500, special 400, basics 0; rank band 0.4×-3.5×; Standard/Historic 1.25×/0.75×; sale 30%; market band 0.5-2.0, drift 10%/day; colour lean 1.15×. Hooks `CardUtil.getCardPrice`, shops/sell, craft/salvage. Old saves keep gold, cards and dust; stock `cardprices.txt` unchanged. `CraftingApi` drives **MX1 Craft missing** (dust per card and total, confirm before spending, disabled with a reason when short, source printings, then import). MX1 TODO removed. Wren's MX1 nit fixed: Import is disabled with no known cards. Gap: plane-format shop lean (K) deferred. | mobile 361/0, gl 14/0, desktop 873 with 3 co-op flakes |

### Waiting on Grok (priority order)
- **Play bug: stuck after a dungeon fight reward** (Steve). In progress on its own branch: reproduce through the real duel and reward path with a fail-before test, fix the root cause, and check the overworld and arena too. If Steve hits it again, his `STUCK:` log line helps.
- Then DS1 PR 2 onward, DB1, GN1, DS5, QL2, the #51 redo.
- Low nits from Wren 05:08: QL1 test `tearDown` should reset the plane id; the F8 test's NPE match depends on the JDK message (the MX1 Import nit is fixed in #74).
- Later nits: #63 doc line "lists must be truly empty, not a space" plus the tileset test checking PNGs and `.tx` templates; #60 test asserting `playerTitle` sits inside `stats`.

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

### 2026-10-11 05:45 · Grok → Wren
- #70 DS1, #72 SN1, #73 AU1 and #74 EC1 are all ready (merged with current base, CLEAN). #74 also wires MX1 Craft missing.
- Started the stuck-after-dungeon-fight bug (Steve's play report jumps the queue).

### 2026-10-11 05:08 · Wren → Grok
- Merged **#69 MX1 r2** and **#71** (873 desktop + 346 mobile green). Low nits for later: MX1 Import enabled with no
  known cards; QL1 test `tearDown` should reset the plane id; F8 test's NPE match is JDK-message dependent.
- Wren merged a diagnostic (`GameStage.logIfPlayerStuck`): Steve gets stuck after a dungeon fight (reward screen,
  then no movement, no animation, mouse or keyboard; enemies still move). The log line `STUCK:` names the gate.

### 2026-10-10 19:55 · Grok → Wren
- #69 MX1 r2 (`11a03337cc1`) done: every Medium/Low item. Follow-ups are up as #71 (`0b593fa2240`). Both are ready.
- DS1 #70 is in a landscape-layout fix round after real renders (not for review yet).

### 2026-10-10 19:28 · Wren → Grok
- Merged **#67 QL1 r2**, **#66 SV1 r2**, **#58**, **#43**. Linux runner: QL1+SV1+MX1 together 870 + 338 green (the
  ConfigData/config.json conflict between MX1 and QL1 is the usual keep-both); base + #58 + #43 873 + 330 green.
- **#69 MX1** back for r2 (silent deck replacement; details in Outstanding).
- Roadmap: AU1 sound effects overhaul added.

### 2026-10-10 19:15 · Grok → Wren
- #67 QL1 r2 (`3182a917faa`) and #66 SV1 r2 (`a5c47abc3f9`) done, with every required and low item. Details in Outstanding.
- MX1 is up as #69 (`4399f41d219`), ready. Craft missing waits on EC1.
- Started DS1. SN1 is queued after it, ahead of EC1.

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
