# Shandalar Ascendant: design agenda

Started 2026-10-11 by Steve and Vex. Design has to get ahead of the code. This is the list of what needs designing,
what's decided, and the order we're taking it in. Each topic gets its own design doc when we work it through.

## The north star

- **A whole RPG with story and quests**, not Forge Adventure with extras. Steve has wanted an MTG RPG for decades.
- **Keep from Forge:** the rules engine, the card database, and the Scryfall card/image pipeline (so Magic itself is
  never packaged with the game). **Everything else is up for replacement**, including the economy, shops, inventory,
  quest screen, deck builder and duel screen. Tiled stays for maps because it's good at pixel maps, not because it came
  with Forge.
- **Nothing a player touches should feel like leftover Forge UI.**

## Decided (2026-10-11)

- **Resources are built around the mana colors** (Steve): ash for red/fire; bone, mushrooms and grave moss for black;
  ore for colorless; and so on through the existing material families.
- **Every material has at least two sinks:** one in the base, and one in cards or gear (Vex, Steve agreed). Today
  111 materials exist but only 9 fortress structures use 4 of them; most gathering is a number that goes up.
- **Card crafting/upgrading uses that color's materials alongside dust** (agreed): your deck shapes where you gather.
- **Auto-salvage** for excess copies (already in the crafting brief as an option; make sure it ships and is visible).
- **Crafting must be explained** in-game, not discovered.

## New design topics (Steve, 2026-10-11)

### Merchant expeditions (another way to get cards)
- Send a merchant out seeking a **pool of cards** ("I want slivers"). Costs a lot of resources and takes time.
- A real source for **cards outside Standard**, for Commander and other decks.

### Shop overhaul (system, not just interface)
- Kill the "20 shops per town, check them all to see if anything's there" chore.
- The whole shop system gets redesigned, not reskinned.
- **Agreed shape (2026-10-11):**
  - **One market per town**, not 10 random shops. Every market is **diverse and predictable**.
  - **A stall per card type** (Steve): lands, creatures, instants, sorceries, artifacts, enchantments, and so on.
  - **A rotated-out stall** (Steve): cards that *were* in Standard and have rotated out.
  - **A rare shop** (Steve) with a **chance at older cards**: the random, lucky-find option.
  - **Stalls are run by townsfolk** (Vex): each stall has a person you can befriend, and their personality flavors
    the stall.
  - **Trade ledger** (Vex): once you know a merchant, their stock appears in a ledger at your fortress, searchable
    ("Sliver"), so you never walk town to town checking stalls.
  - **Merchant expeditions** are the answer when nobody has it (targeted, costly, slow), versus the rare shop's luck.
  - **Deck sellers** (Vex): townsfolk whose decks you've played sell copies of their decks.

### Time, days and travel (2026-10-11)
- **Today (SN1):** 15 real minutes of exploring = 1 in-game day; a duel costs a flat 2 hours; an inn rest skips to
  morning; seasons are 14 days (first two 7). The clock works but a day doesn't *feel* like anything.
- **Sleep is payday** (Vex; **confirmed by Steve**: "that feels right"):
  - **The day ends when you sleep**, in any bed (fortress, inn, camp). Overnight is when the world advances.
  - **Overnight payouts:** shipments arrive at stalls, workers finish jobs, crafting queues complete, crops grow,
    letters from friends arrive.
  - **The morning report:** the Watcher's briefing on waking: shipments and where, news, the Nothing's movement,
    what's on in town today. This is the "hints and news" feature, with a voice; it keeps the trade ledger fresh.
  - **Real day and night:** some things only happen at night (tavern crowd, nocturnal creatures, certain townsfolk,
    night markets).
  - **No punishment for staying up:** past 2am you doze off, the Watcher gets you home, you wake normally. The only
    cost is a missed full night's payout.
  - **Energy:** maybe later for gathering/crafting, **never for duels**. Start without it.
- **Restocking:** each stall restocks **once a week on its own day**, staggered so most mornings something somewhere
  is fresh; the morning report says what. Rotation day is the big festival restock.
- **Field fast travel costs time** (Steve): the Hall of Remembrance transition from the fortress stays quick, but
  out in the field there's a travel method that advances the clock.
  - Vex's draft: **road travel from signposts and town gates** to any known town; costs **in-game hours by
    distance**, not gold. Needs the **road to exist** (the Nothing cutting a road cuts the route).
  - Vex's draft: **recalling home** from the field also costs a few hours, so field → home → Hall → town isn't a free
    teleport everywhere; it rewards building up the Hall but still costs the trip home.
- **Road events: yes** (Steve). A trip can occasionally be interrupted by something worth stopping for: a traveler
  wanting a duel, a caravan with odd stock, a refugee from a lost town.

### A world that's populated, not infested (Steve, 2026-10-11)
- Steve: the world should be **filled with things that aren't just random things moving quickly to attack you.**
- Vex's draft:
  - **Most of what you meet is not hostile.** Travelers, pilgrims, merchants and caravans, wandering townsfolk,
    patrols, wildlife that grazes and flees, refugees, rival duelists who *ask* for a match.
  - **Hostility means something.** Chasing, aggressive enemies are reserved for things that are actually dangerous:
    the Nothing's minions, bandits, monsters in their own territory, at night.
  - **Places, not just encounters:** campsites, roadside shrines, ruins, lookouts, a lone house, a fishing spot,
    all things that are simply *there* and reward wandering.

### Fishing and cooking (Steve, 2026-10-11)
- **Fishing and cooking are in.** Food **heals you in the wilderness** and gives **duel buffs** (Steve's example:
  start a game able to play one extra land on turn 1).
- Vex's draft:
  - **Meals give a "well fed" buff** that lasts until you next sleep (or a set number of duels). One meal buff at a
    time, so choosing a meal is a small pre-adventure decision.
  - **Buffs lean on color identity**, like materials: a fish dish (blue) might draw an extra card in your opening
    hand; a hearty stew (green) more starting life; something spicy (red) a first-turn edge; the extra-land breakfast
    for ramp decks.
  - **Ingredients come from gathering:** fish, plus berries, roots, herbs, mushrooms (the plant and swamp rows).
    This gives the plant families a second sink, per the two-sinks rule.
  - **Cook at a campfire** in the field (simple recipes) or a **kitchen** at the fortress or inn (full recipes).
  - **Fishing must be tremor-friendly:** no precise timing or fast clicking (Steve and Tiny both have hand tremors).
    Patience and choosing the right spot, bait and time of day, not reflexes.
  - Some fish only bite at night, in a season, or in a luminous pool tier. Collecting every fish is its own goal.
- Note for Grok: an "extra land play on turn 1" buff likely needs a new item effect; today's `EffectData` has life,
  starting cards and start-with-card, but not extra land plays.

### Deck builder
- **No reason to use an external deck builder.** The best tools of sites like Moxfield, inside the game.
- Designed around **our Standard rotation**.
- A **collection browser** so you know what you can go for.
- **Hints and news.**
- **People sell completed decks.**

### Base building
- Steve played the game and **couldn't figure out how to build a base**. That's a design failure: the fortress must
  be introduced by the game, not discovered.
- **The first hour (confirmed by Steve, 2026-10-11):** the fortress is a **story beat**. Early on, the Supervisor's
  first real order: *"You can't run an investigation out of an inn. Find defensible ground and plant your banner."*
  The Watcher marks a few good sites on the map; you walk there and plant it; the **first structure the game walks
  you through is your bed**, so from day one the fortress is where your day ends and overnight payouts land.
- **Gather yields must go up (Steve):** today a gather gives **1** unit, far too little for building. Yields need
  sizing against structure costs. Vex's draft: one interaction harvests a **batch** (e.g. 3-5 at base, more with
  skill and tier), never rapid repeated clicking (tremor-friendly). The world is being rebuilt from scratch, so node
  placement and yields can be designed together.
- **The base produces, not just consumes (Steve):** production structures for each material family. Steve's examples:
  a **mushroom farm**, an **old battlefield for bones**.
  - **Producer list (decided 2026-10-11; Steve's list, gaps filled by Vex and approved):**

    | Color | Any base | Needs the land |
    |---|---|---|
    | Green | berry patch, herb patch | hardwood grove (real forest); elder glade, an ancient grove for rare herbs and sap |
    | White | aviary | marble quarry |
    | Red | charcoal kiln | ash vent tap |
    | Black | mushroom farm, poison herb bed | old battlefield, old graveyard |
    | Blue | fish pond | spring tap on a luminous pool; the pool's tier sets the water's purity |
    | Colorless | foundry: processes scrap into raw materials | mine (an ore vein) |
  - Vex's draft rules:
    - **Production pays out overnight** (sleep is payday).
    - **Production accumulates while you're away (Steve, decided):** spending a few nights out in the wilderness is
      never punished. Your base keeps producing and the output waits for you; no missed days, no spoilage.
    - **Slower than gathering by hand, but steady.** Gathering stays the fast way to get a lot; production is the
      reliable trickle that keeps the base fed while you're out adventuring.
    - **A producer needs its own material to build (Steve, decided):** the mushroom farm costs mushrooms, the ossuary
      costs bones. So you must find and gather a material in the wild before you can produce it.
    - **A mix of anywhere and the right ground (Steve, decided):** small, cultivated producers can be built in any
      fortress; big, land-bound ones need the real thing nearby.
      - Which is which: see the producer table above.
      - So where you plant your banner shapes your *big* producers, and **forward fortresses** in other biomes are
        how you get the other colors' land-bound producers.
- Open questions in `Fortress-Design-Brief.md` (feel, decor, interiors, people, farming, Archive/Hall, raids,
  progression beats, co-op space, forward fortresses).

### Art direction (decided 2026-10-11)
- **The world stays 16px**, like Stardew Valley (also 16px tiles). Going to 32px would be 4x the pixels per tile for
  Steve's draft-and-mold workflow and would orphan the 16px packs (Kenney, Ninja Adventure, Havenbrook, the townsfolk).
- **16px is not a ceiling:** taller characters (16x32), lighting, weather and day/night that change the mood.
- **Buildings stay small (Steve):** roughly **2x3 tiles with one door**, not Stardew-size buildings, which would mean
  far more map work. If the door is done right (you enter an interior), that's enough. Save big set pieces for a few
  landmarks (town hall, church, the fortress).
- **The UI doesn't have to be pixel art.** Menus, deck builder, inventory and duel screen can be crisp and modern with
  pixel accents (cards are already high-res). The UI is where "not leftover Forge" shows most.

### Inventory and quest screens
- Full redesign, built around what the economy and base need to show.

### Duel screen
- Being rebuilt from the ground up (Grok's HP1 hand prototype is the first piece). Acknowledge that **MTG Arena does
  it well, not perfectly**, and far better than Forge's default. Design what we take from Arena and what we fix.

## Order

1. **Economy map**: what every resource is for (sinks for all families, color identity, card crafting costs).
2. **Getting cards**: shops overhaul, merchant expeditions, deck sellers, crafting explained.
3. **Base building**: onboarding first, then the open fortress questions.
4. **Deck builder and collection browser.**
5. **Inventory and quest screens.**
6. **Duel screen.**
