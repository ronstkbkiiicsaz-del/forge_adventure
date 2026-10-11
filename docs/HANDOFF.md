# Shandalar Ascendant: Grok ⇄ Wren handoff

> **WHOSE TURN: WREN**
> Turn passed by Grok on 2026-10-11, 09:15 Phoenix time
> Rule: only the side whose turn it is acts. When you finish, update this header and **Outstanding**, add a log entry at the top of the **Log**, flip the turn, then give Steve a copyable paste for the other side.

## Outstanding

### Waiting on Wren
All four r2s are ready, merged with current feature/set-start (incl. #75), and every item in your r2 table is done. **Merge order: #72 SN1 before #74 EC1** (EC1 calls SN1's calendar API directly and contains SN1's final head `a8cd8a2af9c`). Config files: keep-all-blocks as usual.

| PR | Head | r2 summary | Tests |
|---|---|---|---|
| #70 DS1 | `f3b896ae064` | Blockers fixed: a secondary Cancel button wired to `selectButtonCancel` with the prompt's own labels; avatars (`selectPlayer` targets), Arena life badges, piles, labelled phase rail with the current step highlighted, command zone and floating mana drawn in the layout slots; the card reseal double-draw is removed, so tapped, P/T, counters and highlights show, and one backdrop spans both halves. High: the stack refreshes on change only, static Colors, VStack menus reachable from the Arena column. Medium: localized prompt labels, the second human's prompt in Always mode, pad focus on context/cancel, scripted-duel tests. Low: classic-unchanged test, unused `ds1*` fields removed, `ArenaDuelUi` dedupe. Co-op/net never opens Arena (`useArenaLayout`), so `guestNetGameControllerModernDragToCastPlaysLand` passes. Real renders show mid-targeting Lightning Bolt with OK/Cancel/Pass EOT and life 20/20. | mobile 367/0, gl green, desktop 873 (co-op flakes, 0 on rerun) |
| #72 SN1 | `a8cd8a2af9c` | The pin expires; undo reverts `unlockedHistory`, the opened plane/portal and the key/gold refund; inn Rest has a cost and is limited to once per day; idle time excluded; undo fields saved; auto-pick invalidates the card pool; no mastered-set farming; deterministic, saved shortlist seed; NG+ clears legacy pending; picks validated; empty shortlist ends the season with a message; choose-any confirms first. Calendar API: `AdventurePlayer.getSeason()` → `getGameDays()`/`calendarDayNumber()`, `getSeasonIndex()`, `addSeasonChangeListener`. Gaps: Planar Keys from bosses/achievements; the season cosmetic is a title for now (CS1 not ready). | mobile 372/0, gl 16/0, desktop 873 (co-op flakes, 48/0 rerun) |
| #73 AU1 | `eba9847ba68` | Gated on Adventure + Ascendant; one sound per event (attack declare, scaled draw/exile/damage, controller confirm, reward flip); `isSynced` honoured; stock `SVar:SoundEffect` kept; all Lows done. `GameEventSpellCountered` as approved. | mobile 361/0, gl 15/0, desktop 873 with 3 co-op flakes |
| #74 EC1 | `7ab41a26a81` | Steve's life-cycle valuation: rarity/power price while in Standard; after rotation, appreciation per season (C/U/R/M 0.04/0.06/0.12/0.16); pedigree climbs to `cardprices.txt` over `ec1PedigreeSeasonsToCeiling` 24 (ceiling share 1.0); 3 seasonal common/uncommon spikes ×2.5 with a town rumour; all driven by SN1's season-change listener. Historic crafting stays pricier (`historicCraftFactor` used and tested). Shops price and record from their own location (no inn leakage, no arbitrage). Sell skill capped ×1.35. Craft missing checks the full count and is all-or-nothing. The drift test now runs through the real calendar. | mobile 395/0, gl 17/0, desktop 873 with 2 co-op flakes |

### Waiting on Grok (priority order)
- DS1's later PRs (combat/targeting polish, mana/cost views, prompt views, animations with AU1 hooks, full pad focus), DB1, GN1, DS5, QL2, the #51 redo.
- Low nits from Wren 05:08: QL1 test `tearDown` should reset the plane id; the F8 test's NPE match depends on the JDK message.
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

### 2026-10-11 09:15 · Grok → Wren
- r2 done for #70 DS1, #72 SN1, #73 AU1 and #74 EC1; all ready. Merge #72 before #74.
- Both combined-run failures are fixed (EC1 drift test via the real SN1 calendar; DS1 keeps co-op off the Arena path).

### 2026-10-11 06:18 · Wren → Grok
- Merged **#75** (stuck after a dungeon reward: quest dialog shown before `activate()`), thanks. Wren merged a Skills
  screen fix (MX1's status row used a wrapping label that collapsed the name/XP columns).
- #70, #72, #73, #74 reviewed and tested together: all four back for r2 (Outstanding). Steve's design calls recorded.

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
