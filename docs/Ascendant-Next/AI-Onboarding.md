# Shandalar Ascendant: onboarding for a new AI collaborator

*Written by Wren (Claude Code) for Steve, 2026-10-13. Hand this whole file to a new AI (ChatGPT, Gemini, etc.) so
it can give useful input without re-learning the project. Everything linked is in the public repo.*

**Repo:** <https://github.com/sjones321/forge_adventure> (public). **Working branch:** `feature/set-start` (the
integration branch; `master` is stale upstream). Links below point at `feature/set-start`.

---

## 1. What the project is

**Shandalar Ascendant** is a single-player (later partner co-op) Magic: The Gathering adventure RPG, made by Steve
for the "OG Magic" crowd who loved MicroProse's *Shandalar* (1997). It is built on **Forge**, the open-source MTG
rules engine (GPL-3.0, ~30,000 scripted cards, a decent AI).

Steve's brief for how it should feel: **Shandalar's card duels inside a Core Keeper / Stardew Valley style
world**:
- a big walkable world with dig, build, gather and craft;
- seasons;
- towns with real interiors;
- planes to walk between.

There is a separate sister project, **Bellwarden** (a Godot game for a wider audience, with its own card game).
Keep the two apart: this file is only about Shandalar Ascendant.

## 2. People and AIs (the "swarm")

| Who | Role |
|---|---|
| **Steve** | Owner, designer and final say. Plays everything and approves all visuals. Direct; wants the answer first. |
| **Tiny** | Steve's co-op partner and playtester. His feedback arrives via `docs/FEEDBACK.md`. |
| **Wren** (Claude Code, on Steve's PC) | Design, specs, map/town building, reviews, integration merges. |
| **Reed** (Cursor agent) | Main coder for the new game (`forge-ascendant`). Iterates live with Steve on feel (hand, duel screen, walking). |
| **Grok** | Coder on a second machine. Takes turns with Cursor through `docs/HANDOFF.md`. |
| **Vex** (claude.ai chat) | Design discussions with Steve (wrote the design agenda). |
| **You** (new AI) | Second opinions, design critique, ideas, research, specs. See §10 for what Steve wants from you. |

Steve's budget is limited, so be efficient: give useful answers, not long ones.

## 3. How to work with Steve

- **Lead with the answer**, then the detail. Use complete sentences, plain words and no hype.
- Say what you know versus what you're guessing.
- **Push back with reasons** when a big idea has a real cost; he values a clear verdict and the cost. Don't argue
  him out of something he has decided without a strong reason.
- **Visual things need a picture first.** He approves a mockup or render before anything is coded. He has
  aphantasia, so drafts he can react to work much better than descriptions or blank canvases. Hand him a shape and
  he molds it.
- He **won't use Tiled** (the map editor). AIs build the maps from code and show him renders.
- **Accessibility matters:** forgiving input (drag targets that snap, clicks as a backup, no precision timing).
- Never ask for, or print, passwords, API keys or secrets.

## 4. The big decision (2026-10-13)

Forge already ships an "Adventure mode" (a small overworld RPG). We forked it and spent weeks renovating it as
Shandalar Ascendant, which is still playable. On 2026-10-13 Steve and Wren decided to **stop renovating Forge
Adventure and build our own game on Forge's engine**:

- **Keep from Forge:**
  - the rules engine (`forge-game`);
  - the cards and card database (`forge-core`, `forge-gui/res/cardsfolder`);
  - the AI (`forge-ai`, plus our LLM opponent);
  - the duel machinery (`HostedMatch`, `RegisteredPlayer`).
- **Ours, new:** world, movement, layers, gathering, building, travel, saves, UI and all our systems. Forge
  Adventure contributes **concepts only**.
- New Maven module **`forge-ascendant`**, package `forge.ascendant`. No static singletons; one `AscendantSession`
  holds the save, plane and player.
- **DuelBridge:** the world sends a `DuelRequest` and gets back a `DuelResult`. Stage 1 uses Forge's existing match
  screen. The new duel screen replaces it later behind the same bridge.

Source of truth: [Architecture-Brief.md](https://github.com/sjones321/forge_adventure/blob/feature/set-start/docs/Ascendant-Next/Architecture-Brief.md).

## 5. World design (decided)

### Planes
- A **plane** is one generated world, about **2000x2000 tiles** of 16px, in 64x64 chunks. Player changes are stored
  as **chunk deltas**.
- The **home plane** is Shandalar, with the hand-made village **Havenbrook** at the centre.
- **Set planes** (Innistrad, Zendikar, Kaladesh…) arrive as their card sets rotate into the seasonal Standard.
  You **planeswalk** to them through gates.
- Each plane is a **PlaneProfile** (JSON: biome mix, landmarks, enemies, resources, music) plus **hand-made set
  pieces** designed with Steve. The generator fills the wilderness between them and guarantees reachability.

### Biomes of the home plane (the five MTG colours)
| Colour | Biome |
|---|---|
| White | Lush rolling hills and farms |
| Blue | Real islands |
| Black | Swamp, graveyards and old battlefields |
| Red | Mountains and volcanoes |
| Green | Forests and jungles |

### Towns
- Towns are **seamless**: you walk in on the road.
- **Building interiors are instanced** (a door loads a small map), like Stardew Valley.
- Each biome gets **one hand-made template town**. Other towns of that biome reuse its pieces.

### Islands
- **Most islands are wild:** dungeons, resources and monsters.
- A few inhabited islands have a real port town; a few have a couple of houses and a dock.
- **Docks only exist where people live.**

### Layers
- Each plane has a **surface** layer, an **underground** layer aligned 1:1 with it (enter by placing a ladder; dig
  tunnels; hard stops at ocean, rivers and bedrock; build factories), and a **sky** layer (platforms, defences).
- Steve leans toward many vertical levels for big bases. There will be a high cap, tuned later.
- The underground has **pre-carved caves and dungeon mouths** as well as what you dig yourself.

### Travel ("dial-a-ride")
- Each biome has its own travel station:
  - docks and ferries (islands)
  - stagecoaches (plains)
  - caravans (mountains)
  - elf guides (forest)
  - skiffs (swamp)
- Use a station once to activate it. After that, a travel map lists every activated station (search, quest
  highlights); you pay a fare and spend in-game time.

### Earn your way out from the centre
- You start in the plains at the centre. Each outer biome has a **traversal unlock**. You *can* go in on foot
  before you have it; it's just slow and painful.

| Biome | Unlock | What it does |
|---|---|---|
| Plains | A regular mount | Faster travel on open ground. |
| Islands | A basic boat, upgraded through play | Reach islands without a ferry. |
| Swamp | A skiff | Without it you wade slowly. |
| Mountains | A cliff-jumping mount (e.g. a mountain goat) | Jump up ledges instead of taking long switchbacks. |
| Forest | **Choose one of three mounts** | **Stag:** bounds over logs and gaps. **Panther:** leaps into the **canopy**, the forest's upper layer, and runs the branches. **Bear:** bulls through brush. |

### Resources
Draft: [Resources-Redesign-Draft.md](https://github.com/sjones321/forge_adventure/blob/feature/set-start/docs/Ascendant-Next/Resources-Redesign-Draft.md)
- Resources **belong to places**; nothing spawns around the player.
- Every tree is gatherable and **regrows over a season**.
- **Most ore is underground** in veins that depend on the biome above. There are some surface outcrops.
- **Fishing is in.** Each set plane has its own resource table.

### Co-op
- **Partner co-op:** the partner lives in the host's world and can build their own base or live with you. The
  current Forge build is single-player; co-op returns in the new game.

## 6. Card-game and economy systems (decided earlier, to be ported)

- **SN1 seasons calendar:**
  - Time: 15 minutes of exploring is 1 day, and a duel is 2 in-game hours.
  - Seasons: 14-day seasons, with 7-day opening seasons.
  - Format: a rotating **4-set Standard**.
  - Deck size: the minimum is 40 cards until the first season adds a set, then 60.
- **EC1 economy:** life-cycle pricing.
- **EC2 economy:** no card selling in towns; a fortress stall instead. Collectors trade, they don't buy. Old sets
  that aren't valid starting or season sets stay locked.
- **Skills:** RuneScape-style gathering and crafting skills.
- **Collecting.**
- **Fortress:** your base.
- **LLM opponent:** an LLM can drive the AI opponent (`forge-ai/.../llm/LlmOpponent.java`).
- **Planned roadmap items:**
  - **MC1:** AI Commander opponents for multiplayer.
  - **AD1:** an in-game advisor chat.

Full roadmap with codes (SN1, EC1, DS1, MC1, ...): [docs/Adventure/Ascendant-Roadmap.md](https://github.com/sjones321/forge_adventure/blob/feature/set-start/docs/Adventure/Ascendant-Roadmap.md).

## 7. What exists right now

| Thing | State | Where |
|---|---|---|
| Old Forge-Adventure-based build | Playable; Steve and Tiny play it. Gets bug fixes only. | `forge-gui-mobile`, `forge.adventure.*` |
| World generator (Python prototype) | Approved after four render rounds; reference for the Java port. | [`tools/worldgen-prototype/`](https://github.com/sjones321/forge_adventure/tree/feature/set-start/tools/worldgen-prototype), spec [WG1-Spec-Stage1.md](https://github.com/sjones321/forge_adventure/blob/feature/set-start/docs/Ascendant-Next/WG1-Spec-Stage1.md) |
| Havenbrook (home village, 90x60) | Done | `forge-gui/res/adventure/common/maps/map/ascendant/havenbrook.tmx` |
| Plains market town template | **Approved** | [`tools/towns/plains/`](https://github.com/sjones321/forge_adventure/tree/feature/set-start/tools/towns/plains) |
| Island port town template | **Approved** | [`tools/towns/port/`](https://github.com/sjones321/forge_adventure/tree/feature/set-start/tools/towns/port) |
| Swamp town template | **Approved** | [`tools/towns/swamp/`](https://github.com/sjones321/forge_adventure/tree/feature/set-start/tools/towns/swamp) |
| Mining town (red) and forest treehouse town (green) | Next | — |
| `forge-ascendant` stage 1 (walk the plane) | Reed's first playable is in progress (draft PR #84) | branch `cursor/ascendant-next-walk-c0e8` |
| Hand and battlefield prototypes (the new duel feel, Arena / Slay the Spire style) | Steve iterating live with Reed | [HandPrototype-Spec.md](https://github.com/sjones321/forge_adventure/blob/feature/set-start/docs/Adventure/HandPrototype-Spec.md), [BattlefieldPrototype-Spec.md](https://github.com/sjones321/forge_adventure/blob/feature/set-start/docs/Adventure/BattlefieldPrototype-Spec.md) |

Town conventions and the approval record: [Towns.md](https://github.com/sjones321/forge_adventure/blob/feature/set-start/docs/Ascendant-Next/Towns.md).

Each town folder has `*_preview.png`, `*_in_context.png` and `*_debug.png`. Look at them; they show the art
direction better than words.

**Art:**
- Ninja Adventure 16px tiles (CC0). All other assets must be CC0 or permissive and credited in `CREDITS.md`.
- New props are drawn to match (pixel art, top-down, south-facing buildings).

### Build stages (each one playable)
1. Walk a plane.
2. Fight (enemies and duels through the DuelBridge).
3. Gather and travel.
4. Seamless towns.
5. Layers.
6. Planes.
7. Port our systems (seasons, economy, skills, collecting, fortress).

Reed's "feel order" for the near term:
1. Real scale.
2. Malleable terrain.
3. Base building.
4. Layers with caves.

## 8. Tech facts

- **Stack:** Java 17, libGDX (the stack Slay the Spire uses) and Maven.
- **Modules:** `forge-core`, `forge-game`, `forge-ai`, `forge-gui`, `forge-gui-mobile`, `forge-gui-mobile-dev` and
  the new `forge-ascendant`.
- **Maps:** Tiled `.tmx` files, built by Python generators in `tools/`. Renders come from those generators.
- **Tests:** Maven, run on a Linux test box. Tests never touch the real Forge user folder.

## 9. Process rules

- Each package of work gets its own branch and a PR into `feature/set-start`.
- Coders take turns through [docs/HANDOFF.md](https://github.com/sjones321/forge_adventure/blob/feature/set-start/docs/HANDOFF.md):
  - Only the side whose turn it is acts.
  - When it's done, that side updates the file and flips the turn.
- **Pictures before code** for anything visual. Feel-based things are iterated live with Steve.
- **Small playable slices.** No big multi-PR epics without something Steve can play in between.
- Bugs from play (Steve's or Tiny's) jump the queue.
- Never edit `starter_town.tmx`. Never weaken a quality check to make it pass; fix the content.

## 10. Where Steve wants input from you

Good uses of a fresh AI:
1. **Design critique** of the world plan (§5): what will be boring, grindy or confusing? How do Core Keeper,
   Stardew, Terraria, Valheim and Zelda solve the same problems (travel gating, base layers, seasons)?
2. **The progression loop:** how card collecting, duels, gathering, crafting and the traversal unlocks feed each
   other. What does the first 2 hours look like? The first 20?
3. **Set-plane concepts:** for a given MTG set, propose the PlaneProfile (biomes, landmarks, towns, enemies,
   resources, music mood) and the hand-made set pieces.
4. **The next two town templates** (the mountain mining town with a caravan station, and the forest treehouse town
   with a canopy level for the panther): layouts, key buildings, what makes each one memorable.
5. **Duel-side ideas** that fit an RPG: start-of-duel effects from the world, enemy deck themes by biome, rewards.
6. **Risk spotting:** scope, performance at 2000x2000 with layers, save size, AI cost.

When you answer:
- Be concrete (numbers, examples, named references).
- Mark what you're unsure of.
- Keep it short enough to act on.
- If you propose code, say which module or stage it belongs to. Wren and Reed will turn agreed ideas into specs.

## 11. Don'ts

- Don't propose dropping Forge or switching engines. That was weighed and decided.
- Don't plan around buying expensive hardware or assets.
- Don't treat Forge Adventure code as something to extend. It's concept reference only.
- Don't ask Steve to hand-edit maps in Tiled.
