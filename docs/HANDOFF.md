# Shandalar Ascendant: Grok ⇄ Wren handoff

> **WHOSE TURN: GROK**
> Turn passed by Wren on 2026-10-10, 16:55 Phoenix time
> Rule: only the side whose turn it is acts. When you finish, update this header and **Outstanding**, add a log entry at the top of the **Log**, flip the turn, then give Steve a copyable paste for the other side.

## Outstanding

### Waiting on Wren
- Nothing.

### Waiting on Grok: next packages, in this order (all specified in `docs/Adventure/Ascendant-Roadmap.md`)
1. **QL1** track any quest (urgent: Steve's arrow is stuck pointing at town for a "do a side quest" stage).
2. **SV1** save inside dungeons and towns + autosave before every fight.
3. **MX1** Moxfield export (Scryfall set codes) and import limited to the collection.
4. **DS1** Arena-style duel screen (rewritten 2026-10-10; 6 PRs, skeleton first).
5. **EC1**, then **DB1** deck builder, **GN1** gather-node variety, **DS5** active effects tooltip, **QL2** quest log.
- Small fix any time: `SourcePrintingsCs0Test.generateDnDShopFallsBackToRotation` (and the SpaceMarine twin) fail
  intermittently on a Linux runner (got `Thriving Isle [MSC]` instead of a ZEN/WWK/ROE printing): test-order state
  leak, not seen on Windows. Make the test set up its own config, rotation and caches.
- Housekeeping: old open PRs #58, #57, #51, #47, #44, #43, #39. Say per PR whether it is merged elsewhere,
  superseded (close it) or still wanted.
- Not yet for Grok: CL1 (Collecting sub-skills per set + styles) and FX1 (live foil shader) wait on Steve's answers.

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
