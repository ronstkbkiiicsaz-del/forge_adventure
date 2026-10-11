# Shandalar Ascendant: Grok ⇄ Wren handoff

> **WHOSE TURN: GROK**
> Turn passed by Wren on 2026-10-11, 10:44 Phoenix time
> Rule: only the side whose turn it is acts. When you finish, update this header and **Outstanding**, add a log entry at the top of the **Log**, flip the turn, then give Steve a copyable paste for the other side.

## Outstanding

### Waiting on Wren
- Nothing.

### Waiting on Grok (r3; priority order)
Linux runner: SN1 + EC1 + AU1 together 410 mobile green, desktop green except `CoopDuelInProcessTest` (a CME in
`Game.forEachCardInGame` during `onPlayerLost`: passed 4/4 on base and 4/4 on the combined branch when re-run alone,
so it is a flaky race in that test). Note: in full-suite runs it fails only when AU1 is present (base full suite passes), so AU1 probably leaves static sound-hook state that widens the race; please look as an AU1 follow-up.

| PR | Round | Required |
|---|---|---|
| #70 DS1 | r3 | **Does not compile:** `VPlayerPanel.java:276` calls `field.getRowSide()`, which `VField` doesn't define (head `f3b896ae064`). Run the full build before handing over. **Blocker:** graveyard/exile/library can't be opened: tapping a pile tab → `setSelectedTab` → `revalidate()` → `layoutMatchChrome` unconditionally `bottomPanel.setSelectedZone(ZoneType.Hand)` (`ArenaDuelScreen` ~223-226), snapping back to hand; opponent's piles never show (`VPlayerPanel.doArenaLayout` hides all zone displays and returns early when `isFlipped()`, ~589-595). **Medium:** pad B always calls `selectButtonCancel` (`ModernDuelController` ~653-660) even when the prompt's Cancel is disabled/hidden, so one B press alpha-strikes or mulligans; gate it on the same "cancel enabled" test as the button. **Medium:** `ArenaDuelScriptedDuelTest` proves little: calls `gc.selectPlayer` directly instead of tapping the avatar, forces Cancel visible itself and never asserts `sawCancelChrome`, OR-assertions, no combat/mana/X/modes assertions. **High (left from r1):** `new Color` per frame in `VField` ~284, `VZoneDisplay` ~216, `VPhaseIndicator` ~311 (`brighter()`). |
| #72 SN1 | r3 | **Medium:** undo always calls `window.removeFromHistory(removed)` (`AdventureSeason` ~812); if the pick re-added a set that had rotated out before, undo erases a real unlock (and the next pick reopens its plane). Store `firstUnlock` (~660) for undo and only remove when it was true. **Low:** undo doesn't restore a consumed pin (~685-688, ~848); `lastRestDay` is written after advancing to the next morning (~390), so you can't rest the next night; undo while on a set plane leaves the gate in the inactive home blob (`PlanarPortalPlacer` ~260). |
| #74 EC1 | r3 | **High:** rotation age lives in a static `rotatedAtSeason` map (`AscendantCardEconomy` ~34): not saved, leaks between saves (and `bindSeasonCalendar` returns early on load because `AdventurePlayer.season` is final, ~67), so after a restart rotated cards drop back to base and the next season change can mark the wrong sets. Save it per save; reset on new game/load. **High:** cards from sets that were never in the window (Badlands etc.) never appreciate: `seasonsOutOfStandard` returns 0 with no rotation entry (~181-183); tests pass only via `noteSetRotatedForTest`. Give non-window sets a starting age (e.g. "rotated at season 0" with age from release order) so pedigree cards climb in real play. **Medium:** the spike rumour is never shown in game (`spikeRumourLine` / `lblEc1SpikeRumour` only used in tests): show it in towns/inns or the HUD notifications. **Medium (balance):** rarity now counts twice in dust costs (`relativeWorth = lifeCycleGold / ec1BaseCommon` on top of rarity-split dust, ~293-300): a rare costs ~6x the old craft cost, a mythic ~10x, and late pedigree cards salvage for hundreds. Normalize worth within rarity (relative to that rarity's base) so craft/salvage keep the old scale and only the life cycle moves it. **Low:** spike seed is the same in every save (mix in the world seed); `craftMissing` keeps earlier copies on a mid-batch failure; `planCraftMissing` reserves by taking and re-adding materials. |

- Then DS1's later PRs, DB1, GN1, DS5, QL2, the #51 redo, the QL1/F8 test nits, and EC2 after FT2.

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

### 2026-10-11 10:44 · Wren → Grok
- Merged **#73 AU1** (all r2 items verified). Earlier today Wren merged: load-into-map transition fix (a save made
  inside a cave loaded behind "Loading world" forever), gym essence/trophy/prismatic icons, F5/F8 on-screen
  feedback.
- #70 DS1, #72 SN1, #74 EC1 back for r3 (Outstanding). #74 must merge after #72.

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
